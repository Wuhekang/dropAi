package com.dropai.rewrite.mechanicalpaper;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.dropai.rewrite.entity.DocumentJobRecord;
import com.dropai.rewrite.mapper.DocumentJobMapper;
import com.dropai.rewrite.service.MatrixDesignService;
import com.dropai.rewrite.service.PointService;
import com.dropai.rewrite.service.image.*;
import com.fasterxml.jackson.databind.*;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;
import jakarta.annotation.PreDestroy;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Supplier;

@Service
public class MechanicalPaperService {
    static final String FEATURE = "MECHANICAL_PAPER";
    private final DocumentJobMapper jobs;
    private final ObjectMapper mapper;
    private final MatrixDesignService text;
    private final DoubaoImageProvider images;
    private final MechanicalPaperPhotoService photos;
    private final MechanicalPaperExporter exporter;
    private final PointService points;
    private final MechanicalPaperBilling billing;
    private final Environment env;
    private final ExecutorService coordinators = Executors.newFixedThreadPool(2);
    private final ExecutorService sections = Executors.newFixedThreadPool(14);
    private final ExecutorService figures = Executors.newFixedThreadPool(4);
    private final ConcurrentMap<String, MechanicalPaperState> active = new ConcurrentHashMap<>();

    public MechanicalPaperService(DocumentJobMapper jobs, ObjectMapper mapper, MatrixDesignService text,
                                  DoubaoImageProvider images, MechanicalPaperPhotoService photos,
                                  MechanicalPaperExporter exporter, PointService points, MechanicalPaperBilling billing, Environment env) {
        this.jobs=jobs; this.mapper=mapper; this.text=text; this.images=images; this.photos=photos;
        this.exporter=exporter; this.points=points; this.env=env;
        this.billing=billing;
    }
    public Map<String,Object> readiness() {
        var health = images.health();
        boolean imageReady = health.isEnabled() && health.isApiKeyConfigured() && !health.getEndpoint().isBlank()
            && !health.getModel().isBlank() && !health.getModel().equals(text.modelName());
        boolean searchReady = env.getProperty("ai.doubao.web-search-enabled", Boolean.class, false);
        return Map.of("ready", imageReady && searchReady && text.apiKeyConfigured(), "imageReady",imageReady,
            "searchReady",searchReady,"textReady",text.apiKeyConfigured(),"message",
            !imageReady ? "Doki 图片服务尚未配置，请联系管理员启用" : !searchReady ? "Doki 实物图检索尚未启用" : "Doki 生成服务已就绪",
            "costPoints",points.featureCostPoints(PointService.DOCX_GENERATE));
    }
    public synchronized Map<String,Object> start(Long userId, String title) {
        String normalized = title == null ? "" : title.trim();
        if (normalized.isBlank() || normalized.length() > 120) throw new IllegalArgumentException("请输入 1—120 字的论文题目");
        // Return an existing live task after a lost start response; never double bill or double submit.
        var existing = jobs.selectList(new LambdaQueryWrapper<DocumentJobRecord>().eq(DocumentJobRecord::getUserId,userId)
            .eq(DocumentJobRecord::getSourceFeature,FEATURE).eq(DocumentJobRecord::getStatus,"RUNNING")
            .orderByDesc(DocumentJobRecord::getCreatedAt).last("LIMIT 1"));
        if (!existing.isEmpty()) {
            if(!existing.get(0).getFileName().equals(normalized+".docx")) throw new IllegalStateException("Doki 已有一个机械论文任务，请先完成或继续该任务");
            return view(load(existing.get(0)),userId);
        }
        if (!Boolean.TRUE.equals(readiness().get("ready"))) throw new IllegalStateException(String.valueOf(readiness().get("message")));
        points.checkPoints(PointService.DOCX_GENERATE);
        MechanicalPaperState state = new MechanicalPaperState();
        state.id="mp_"+UUID.randomUUID().toString().replace("-", ""); state.title=normalized;
        state.tasks.put("plan",new MechanicalPaperState.Task("统一结构与参数规划"));
        for (var section : MechanicalPaperPlan.SECTIONS) state.tasks.put("text:"+section.id(),new MechanicalPaperState.Task(section.id()+" "+section.title()));
        for (var figure : MechanicalPaperPlan.FIGURES) state.tasks.put("image:"+figure.id(),new MechanicalPaperState.Task("图"+figure.id()+" "+figure.caption()));
        state.tasks.put("export",new MechanicalPaperState.Task("Word 排版与交付检查"));
        DocumentJobRecord job = new DocumentJobRecord();
        job.setJobId(state.id); job.setUserId(userId); job.setFileName(normalized+".docx"); job.setSourceFeature(FEATURE);
        job.setMode("mechanical_thesis"); job.setModeName("机械论文"); job.setPlatform("DOKI"); job.setPlatformName("Doki");
        job.setStatus("RUNNING"); job.setTotalParagraphs(state.tasks.size()); job.setProcessedParagraphs(0); job.setRewrittenParagraphs(0);
        job.setCharCount(0); job.setPointsCharged(false); job.setCostPoints(points.featureCostPoints(PointService.DOCX_GENERATE));
        job.setMessage("Doki 正在规划设计参数"); job.setParagraphsJson(json(state));
        job.setCreatedAt(LocalDateTime.now()); job.setUpdatedAt(LocalDateTime.now()); jobs.insert(job);
        active.put(state.id,state); coordinators.submit(()->run(state,userId));
        return view(state,userId);
    }
    public Map<String,Object> get(Long userId, String id) { return view(load(owned(userId,id)),userId); }
    public List<Map<String,Object>> recent(Long userId) {
        return jobs.selectList(new LambdaQueryWrapper<DocumentJobRecord>().eq(DocumentJobRecord::getUserId,userId)
            .eq(DocumentJobRecord::getSourceFeature,FEATURE).orderByDesc(DocumentJobRecord::getCreatedAt).last("LIMIT 10"))
            .stream().map(job->view(load(job),userId)).toList();
    }
    public synchronized Map<String,Object> retry(Long userId,String id) {
        var state = load(owned(userId,id));
        if (active.containsKey(id) || "SUCCESS".equals(state.status)) return view(state,userId);
        if (!Boolean.TRUE.equals(readiness().get("ready"))) throw new IllegalStateException("Doki 生成服务尚未就绪");
        points.checkPoints(PointService.DOCX_GENERATE);
        state.status="RUNNING"; state.error="";
        state.tasks.values().stream().filter(task->!"completed".equals(task.status)).forEach(task->{task.status="waiting";task.error="";task.startedAt=0;});
        save(state); active.put(id,state); coordinators.submit(()->run(state,userId));
        return view(state,userId);
    }
    public byte[] download(Long userId,String id) throws Exception {
        var record = owned(userId,id);
        if (!"SUCCESS".equals(record.getStatus())) throw new IllegalStateException("论文尚未完成");
        return Files.readAllBytes(directory(id).resolve("paper.docx"));
    }
    public byte[] image(Long userId,String id,String figureId) throws Exception {
        var state=load(owned(userId,id));
        if (MechanicalPaperPlan.FIGURES.stream().noneMatch(f->f.id().equals(figureId))) throw new IllegalArgumentException("图片不存在");
        var task=state.tasks.get("image:"+figureId);
        if (!"completed".equals(task.status)) throw new IllegalStateException("图片尚未完成");
        return Files.readAllBytes(directory(id).resolve(task.file));
    }
    private void run(MechanicalPaperState state,Long userId) {
        try {
            Path dir=directory(state.id); Files.createDirectories(dir);
            task(state,"plan",()-> {
                var design=parse(text.generate("你是机械设计论文规划者，只返回严格 JSON。",planPrompt(state.title))); validateDesign(design);
                synchronized(state) {
                    state.design=design;
                    for(var section:MechanicalPaperPlan.SECTIONS) state.tasks.get("text:"+section.id()).label=section.id()+" "+find(design,"sections",section.id()).path("title").asText();
                    for(var figure:MechanicalPaperPlan.FIGURES) if(!figure.kind().equals("photo"))state.tasks.get("image:"+figure.id()).label="图"+figure.id()+" "+find(design,"figures",figure.id()).path("caption").asText();
                }
                return design;
            });
            stage(state,"正文与配图并行生成");
            List<CompletableFuture<Void>> pending = new ArrayList<>();
            for (var section:MechanicalPaperPlan.SECTIONS) pending.add(CompletableFuture.runAsync(()->task(state,"text:"+section.id(),()->writeSection(state,section)),sections));
            for (var figure:MechanicalPaperPlan.FIGURES) pending.add(CompletableFuture.runAsync(()->task(state,"image:"+figure.id(),()-> {
                try {
                    var t=state.tasks.get("image:"+figure.id());
                    if (figure.kind().equals("photo")) {
                        var photo=photos.find(state.title,dir); t.file=photo.file();t.pageUrl=photo.pageUrl();t.imageUrl=photo.imageUrl();
                    } else {
                        ImageGenerationRequest request=new ImageGenerationRequest(); request.setPrompt(figurePrompt(state.design,figure));
                        var result=images.generate(request);
                        if (!"success".equals(result.getStatus())) throw new IllegalStateException("Doki 图片生成未完成，请重试此任务");
                        var original=Path.of(result.getFilePath());
                        String filename="figure-"+figure.id()+ (original.toString().endsWith(".jpg")?".jpg":".png");
                        Files.copy(original,dir.resolve(filename),StandardCopyOption.REPLACE_EXISTING); t.file=filename;
                    }
                    return mapper.createObjectNode().put("figureId",figure.id());
                } catch (Exception e) { throw new IllegalStateException(e.getMessage(),e); }
            }),figures));
            CompletableFuture.allOf(pending.toArray(CompletableFuture[]::new)).join();
            stage(state,"字数与结构检查");
            state.wordCount=MechanicalPaperPlan.SECTIONS.stream().mapToInt(s->bodyWords(state.tasks.get("text:"+s.id()).content)).sum();
            if (state.wordCount < 6000 || state.wordCount > 9000) throw new IllegalStateException("Doki 正文字数检查未通过，请重试调整");
            stage(state,"Word 排版与导出");
            task(state,"export",()-> {
                try { exporter.export(state,dir); return mapper.createObjectNode().put("file","paper.docx"); }
                catch (Exception e) { throw new IllegalStateException("Doki 文档排版失败，请重试",e); }
            });
            billing.complete(userId,state);
            state.status="SUCCESS";stage(state,"生成完成");
        } catch(Exception e) {
            state.status="FAILED";state.error=friendly(e); save(state);
        } finally { active.remove(state.id); }
    }
    private JsonNode writeSection(MechanicalPaperState state,MechanicalPaperPlan.Section section) {
        String prompt="题目："+state.title+"。共享设计参数与计算依据："+state.design+"\n只写二级小节 "+section.id()+"，标题使用规划中的对应标题。"
            +"正文目标"+section.words()+"字，按自然段组织，不写一级二级标题，不写来源说明、编辑说明、示意或待补充，不编造文献、实验、真实仿真操作。"
            +"按设计假定和解析计算展开具体结构与参数分析，不把生成云图当作实际有限元求解证据。只返回 JSON："
            +"{\"paragraphs\":[\"自然段\"],\"formulas\":[\"可编辑线性数学表达，含代入计算及单位\"],\"tables\":[{\"caption\":\"表题\",\"headers\":[\"名称\",\"值\"],\"rows\":[[\"项\",\"值\"]]}]}。"
            +"第三章每节至少3条公式；仅2.1、4.1、5.3各一个表格，其他节tables为空；公式仅第三章。正文准确提及该节图号与对象。"
            +"不要增加三级标题，避免套话和重复段落。";
        for(int attempt=0;attempt<3;attempt++) {
            JsonNode result=parse(text.generate("你是机械论文作者，以材料力学解析计算和一致的设计假定撰写。严格返回 JSON。",prompt));
            validateSection(result,section);
            int count=bodyWords(result);
            if(count >= Math.ceil(section.words()*.90) && count <= Math.floor(section.words()*1.06)) return result;
            prompt += "\n上次正文实测为"+count+"字；重新输出整个小节，补充或精简具体结构分析至"+section.words()+"字，不重复凑字。";
        }
        throw new IllegalStateException("Doki 小节字数未达到要求，请重试");
    }
    static int bodyWords(JsonNode content) {
        if(content==null) return 0;
        int count=0;for(JsonNode p:content.path("paragraphs")) count+=MechanicalPaperPlan.wordCount(p.asText());return count;
    }
    static void validateSection(JsonNode node,MechanicalPaperPlan.Section section) {
        if(!node.path("paragraphs").isArray() || node.path("paragraphs").isEmpty()) throw new IllegalStateException("Doki 正文结构不完整");
        if(!node.path("formulas").isArray() || !node.path("tables").isArray()) throw new IllegalStateException("Doki 公式或表格结构不完整");
        if(section.chapter()==3 && node.path("formulas").size()<3) throw new IllegalStateException("Doki 计算公式数量不足");
        boolean needsTable=List.of("2.1","4.1","5.3").contains(section.id());
        if(node.path("tables").size()!=(needsTable?1:0)) throw new IllegalStateException("Doki 表格位置不符合稿件结构");
        for(var p:node.path("paragraphs")) if(!p.isTextual() || p.asText().matches("(?s).*?(资料来源|此处插入|待补充|待核验|图.*?示意|\\[[0-9]+]).*")) throw new IllegalStateException("Doki 正文内容检查未通过");
        for(var table:node.path("tables")) {
            int columns=table.path("headers").size();
            if(columns<2 || columns>6 || table.path("rows").isEmpty() || table.path("rows").size()>15) throw new IllegalStateException("Doki 表格结构检查未通过");
            for(var row:table.path("rows")) if(!row.isArray() || row.size()!=columns) throw new IllegalStateException("Doki 表格列数不一致");
        }
    }
    static void validateDesign(JsonNode design) {
        if(design.path("parts").size()!=4 || design.path("sections").size()!=MechanicalPaperPlan.SECTIONS.size()
           || design.path("figures").size()!=14 || design.path("inputs").asText().isBlank() || design.path("calculations").asText().isBlank()
           || design.path("abstract").asText().isBlank() || design.path("englishAbstract").asText().isBlank() || design.path("keywords").isEmpty()) throw new IllegalStateException("Doki 设计规划不完整");
        Set<String> partNames=new HashSet<>();
        for(var part:design.path("parts")) if(part.path("name").asText().isBlank() || part.path("geometry").asText().isBlank() || !partNames.add(part.path("name").asText())) throw new IllegalStateException("Doki 零件规划不完整或存在重复");
        for(var s:MechanicalPaperPlan.SECTIONS) if(find(design,"sections",s.id()).path("title").asText().isBlank()) throw new IllegalStateException("Doki 章节规划不完整");
        for(var f:MechanicalPaperPlan.FIGURES) if(!f.kind().equals("photo") && (find(design,"figures",f.id()).path("description").asText().isBlank() || find(design,"figures",f.id()).path("caption").asText().isBlank())) throw new IllegalStateException("Doki 配图规划不完整");
    }
    public static JsonNode find(JsonNode root,String field,String id) {
        for(JsonNode item:root.path(field)) if(id.equals(item.path("id").asText())) return item;
        return com.fasterxml.jackson.databind.node.MissingNode.getInstance();
    }
    static String planPrompt(String title) {
        return "为题目【"+title+"】规划完整机械设计论文，不改变题目、不要把非夹具题目写成夹具。制定一组明确且自洽的方案级设计假定：用途、尺寸、材料、载荷、结构、工况、单位，计算依据使用解析公式而非编造仿真或实验。"
            +"正文六章，一级标题："+MechanicalPaperPlan.CHAPTERS+"；二级章节槽："+MechanicalPaperPlan.SECTIONS
            +"。配图位置固定："+MechanicalPaperPlan.FIGURES+"。第一章实物图在程序中单独真实检索，不在规划中生成图1-1。四个零件必须不同且属于同一总装。图5-4/5是同一承载件、5-6/7是同一功能件。"
            +"输出 JSON：{\"abstract\":\"中文摘要300字\",\"englishAbstract\":\"英文摘要\",\"keywords\":[\"关键词\"],\"inputs\":\"统一参数、结构与设计工况假定\",\"calculations\":\"第三章解析公式、代入数据、单位与结果，至少6项\","
            +"\"parts\":[{\"name\":\"实际零件名\",\"geometry\":\"统一尺寸材料孔位\"}],\"sections\":[{\"id\":\"1.1\",\"title\":\"适配题目的二级标题\"}],\"figures\":[{\"id\":\"2-1\",\"caption\":\"准确零件/图名，不含示意\",\"description\":\"画面要求、对应零件、结构、尺寸、载荷与约束\"}]}。sections完整14项，figures完整14项，parts恰好4项。不给参考文献，不生成封面、诚信声明、流程图。";
    }
    static String figurePrompt(JsonNode design,MechanicalPaperPlan.Figure figure) {
        var specification=find(design,"figures",figure.id());
        String common="仅生成一张机械论文插图，不拼贴不同图。统一设计依据："+design.path("inputs").asText()+"；零件尺寸："+design.path("parts")
            +"。本图："+specification.path("caption").asText()+"。内容："+specification.path("description").asText()
            +"。清晰横向构图，文字简洁准确且大字号，结构必须与总装一致。图内不得出现示意、AI、Doki、水印、资料来源、论文图号或编辑说明。";
        return common+switch(figure.kind()) {
            case "stress","displacement" -> "严格采用工程分析视口视觉：浅灰到白色背景，单一三维零件等轴视角，细网格，紫红色载荷箭头，蓝色约束符号，左下角XYZ坐标，右侧竖直彩虹色标从蓝色低值到红色高值。"
                +"左上短标题为"+specification.path("caption").asText()+"，小字定性分析。色标仅标相对分布、高、低；不得编造仿真数值、单位量程、软件名、软件界面、求解成功标志或真实截图。分析类型："+figure.kind()+"。";
            case "part","drawing" -> "白底黑线机械工程图，正投影视图和必要轴测图，尺寸线箭头规范清楚，尺寸只使用共享参数，不添加学校标题栏，零件材料与标注准确。";
            case "mesh" -> "浅灰白工程视口背景，三维零件表面细密三角网格，孔位及边缘适当加密，坐标轴和约束符号清楚，无软件品牌和虚构节点数。";
            case "force" -> "白底黑线受力简图，箭头、尺寸、支承和符号清晰，与第三章解析计算的结构与载荷一致。";
            default -> "白底，金属材料三维装配渲染，实际零件清楚完整，总装几何、数量和孔位必须与共享设计参数一致。";
        };
    }
    private JsonNode parse(String response) {
        try { int start=response.indexOf('{'),end=response.lastIndexOf('}'); if(start<0 || end<start) throw new IllegalArgumentException(); return mapper.readTree(response.substring(start,end+1)); }
        catch(Exception e) { throw new IllegalStateException("Doki 返回的论文数据格式不完整，请重试",e); }
    }
    private void task(MechanicalPaperState state,String key,Supplier<JsonNode> action) {
        var task=state.tasks.get(key);
        synchronized(state) { if("completed".equals(task.status)) return; task.status="running";task.startedAt=System.currentTimeMillis();save(state); }
        try {
            JsonNode result=action.get();
            synchronized(state) { task.content=result;task.status="completed";task.error="";task.finishedAt=System.currentTimeMillis();save(state); }
        } catch(Exception e) {
            synchronized(state) { task.status="failed";task.error=friendly(e);task.finishedAt=System.currentTimeMillis();save(state); }
            throw new CompletionException(e);
        }
    }
    private void stage(MechanicalPaperState state,String stage) { synchronized(state) {state.stage=stage;save(state);} }
    private void save(MechanicalPaperState state) {
        synchronized(state) {
            state.updatedAt=System.currentTimeMillis(); var record=new DocumentJobRecord();record.setJobId(state.id);
            record.setStatus(state.status);record.setMessage(state.error.isBlank()?state.stage:state.error);record.setParagraphsJson(json(state));
            record.setProcessedParagraphs((int)state.tasks.values().stream().filter(t->"completed".equals(t.status)).count());
            record.setCharCount(state.wordCount);record.setUpdatedAt(LocalDateTime.now());jobs.updateById(record);
        }
    }
    private String json(MechanicalPaperState state) { try {return mapper.writeValueAsString(state);} catch(Exception e) {throw new IllegalStateException("任务保存失败",e);} }
    private DocumentJobRecord owned(Long userId,String id) {
        var job=jobs.selectById(id);
        if(job==null || !FEATURE.equals(job.getSourceFeature()) || !Objects.equals(userId,job.getUserId())) throw new IllegalArgumentException("任务不存在或无权访问");
        return job;
    }
    private MechanicalPaperState load(DocumentJobRecord job) {
        var running=active.get(job.getJobId()); if(running!=null) return running;
        try {
            var state=mapper.readValue(job.getParagraphsJson(),MechanicalPaperState.class);
            if("RUNNING".equals(state.status)) {
                state.status="INTERRUPTED";state.error="服务已重启，已完成内容已保存，可继续生成";
            }
            return state;
        } catch(Exception e) {throw new IllegalStateException("Doki 任务状态读取失败",e);}
    }
    private Map<String,Object> view(MechanicalPaperState state,Long userId) {
        synchronized(state) {
            List<Map<String,Object>> taskViews=state.tasks.entrySet().stream().map(entry->{
                var t=entry.getValue();Map<String,Object> out=new LinkedHashMap<>();out.put("id",entry.getKey());out.put("label",t.label);out.put("status",t.status);
                out.put("error",t.error);out.put("startedAt",t.startedAt);out.put("finishedAt",t.finishedAt);return out;
            }).toList();
            int completed=(int)state.tasks.values().stream().filter(t->"completed".equals(t.status)).count();
            Map<String,Object> result=new LinkedHashMap<>();result.put("id",state.id);result.put("title",state.title);result.put("status",state.status);
            result.put("stage",state.stage);result.put("error",state.error);result.put("wordCount",state.wordCount);result.put("tasks",taskViews);
            result.put("progress","SUCCESS".equals(state.status)?100:Math.min(99,100*completed/state.tasks.size()));
            result.put("startedAt",state.startedAt);result.put("updatedAt",state.updatedAt);result.put("chapters",MechanicalPaperPlan.CHAPTERS);
            result.put("sections",state.design==null?List.of():state.design.path("sections"));
            result.put("figures",MechanicalPaperPlan.FIGURES.stream().map(f->{ var imageTask=state.tasks.get("image:"+f.id());
                return Map.of("id",f.id(),"section",f.section(),"caption",state.design==null?f.caption():f.kind().equals("photo")?"应用实物图":find(state.design,"figures",f.id()).path("caption").asText(),"status",imageTask.status);
            }).toList());
            return result;
        }
    }
    Path directory(String id) { if(!id.matches("mp_[a-f0-9]{32}")) throw new IllegalArgumentException("任务编号无效"); return Path.of(env.getProperty("mechanical.paper.output-dir","data/mechanical-papers")).toAbsolutePath().resolve(id); }
    private static String friendly(Exception exception) {
        Throwable root=exception;while(root.getCause()!=null) root=root.getCause();
        String message=root.getMessage();
        if(message!=null && (message.startsWith("Doki") || message.startsWith("请输入"))) return message;
        return "Doki 生成遇到问题，已完成内容已保存，请重试；持续失败请联系管理员";
    }
    @PreDestroy public void close() { coordinators.shutdownNow(); sections.shutdownNow(); figures.shutdownNow(); }
}
