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
    private static final org.slf4j.Logger log=org.slf4j.LoggerFactory.getLogger(MechanicalPaperService.class);
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
            "costPoints",points.featureCostPoints(PointService.MECHANICAL_PAPER));
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
        points.checkPoints(PointService.MECHANICAL_PAPER);
        MechanicalPaperState state = new MechanicalPaperState();
        state.id="mp_"+UUID.randomUUID().toString().replace("-", ""); state.title=normalized;
        state.tasks.put("plan",new MechanicalPaperState.Task("统一结构与参数规划"));
        for (var section : MechanicalPaperPlan.SECTIONS) state.tasks.put("text:"+section.id(),new MechanicalPaperState.Task(section.id()+" "+section.title()));
        for (var figure : MechanicalPaperPlan.FIGURES) state.tasks.put("image:"+figure.id(),new MechanicalPaperState.Task("图"+figure.id()+" "+figure.caption()));
        state.tasks.put("export",new MechanicalPaperState.Task("Word 排版与交付检查"));
        state.tasks.put("summary",new MechanicalPaperState.Task("摘要与正文一致性检查"));
        DocumentJobRecord job = new DocumentJobRecord();
        job.setJobId(state.id); job.setUserId(userId); job.setFileName(normalized+".docx"); job.setSourceFeature(FEATURE);
        job.setMode("mechanical_thesis"); job.setModeName("机械说明书"); job.setPlatform("DOKI"); job.setPlatformName("Doki");
        job.setStatus("RUNNING"); job.setTotalParagraphs(state.tasks.size()); job.setProcessedParagraphs(0); job.setRewrittenParagraphs(0);
        job.setCharCount(0); job.setPointsCharged(false); job.setCostPoints(points.featureCostPoints(PointService.MECHANICAL_PAPER));
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
        points.checkPoints(PointService.MECHANICAL_PAPER);
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
            normalizeCaptions(state.design);
            task(state,"plan",()-> {
                JsonNode design=state.design;
                try{validateDesign(design);}catch(Exception invalid){design=null;}
                if(design==null)design=parse(text.generateJson("你是机械设计论文规划者，只返回严格 JSON。",planPrompt(state.title)));
                normalizeCaptions(design);
                synchronized(state){state.design=design;save(state);}
                validateDesign(design);
                synchronized(state) {
                    state.design=design;
                    for(var section:MechanicalPaperPlan.SECTIONS) state.tasks.get("text:"+section.id()).label=section.id()+" "+find(design,"sections",section.id()).path("title").asText();
                    for(var figure:MechanicalPaperPlan.FIGURES) if(!figure.kind().equals("photo"))state.tasks.get("image:"+figure.id()).label="图"+figure.id()+" "+find(design,"figures",figure.id()).path("caption").asText();
                }
                return design;
            });
            stage(state,"正文与配图并行生成");
            for(var section:MechanicalPaperPlan.SECTIONS) {
                var completed=state.tasks.get("text:"+section.id());
                if("completed".equals(completed.status))try{validateSection(completed.content,section);}catch(IllegalStateException invalid){
                    synchronized(state){completed.status="waiting";state.tasks.get("export").status="waiting";var summary=state.tasks.get("summary");if(summary!=null)summary.status="waiting";save(state);}
                }
            }
            List<CompletableFuture<Void>> pending = new ArrayList<>();
            Map<String,CompletableFuture<Void>> imageBranches=new HashMap<>();
            for (var section:MechanicalPaperPlan.SECTIONS) pending.add(CompletableFuture.runAsync(()->task(state,"text:"+section.id(),()->writeSection(state,section)),sections));
            for (var figure:MechanicalPaperPlan.FIGURES) {
                String pairedStress=figure.id().equals("5-5")?"5-4":figure.id().equals("5-7")?"5-6":"";
                var geometryReady=imageBranches.getOrDefault(pairedStress,CompletableFuture.completedFuture(null));
                var branch=geometryReady.thenRunAsync(()->task(state,"image:"+figure.id(),()-> {
                try {
                    var t=state.tasks.get("image:"+figure.id());
                    if (figure.kind().equals("photo")) {
                        var photo=photos.find(state.title,dir); t.file=photo.file();t.pageUrl=photo.pageUrl();t.imageUrl=photo.imageUrl();
                    } else {
                        ImageGenerationRequest request=new ImageGenerationRequest(); request.setPrompt(figurePrompt(state.design,figure));
                        if(List.of("stress","displacement").contains(figure.kind())) {
                            List<String> references=new ArrayList<>();
                            try(var style=MechanicalPaperService.class.getResourceAsStream("/templates/mechanical-cloud-style.png")) {
                                if(style!=null) references.add("data:image/png;base64,"+Base64.getEncoder().encodeToString(style.readAllBytes()));
                            }
                            String geometryId=!pairedStress.isBlank()?pairedStress:figure.id().equals("5-4")?"5-3":"";
                            var geometryTask=state.tasks.get("image:"+geometryId);
                            if(geometryTask!=null && "completed".equals(geometryTask.status) && geometryTask.file!=null && !geometryTask.file.isBlank()) {
                                Path geometryImage=dir.resolve(geometryTask.file);
                                if(Files.isRegularFile(geometryImage)) {
                                    references.add("data:image/"+(geometryTask.file.endsWith(".jpg")?"jpeg":"png")+";base64,"+Base64.getEncoder().encodeToString(Files.readAllBytes(geometryImage)));
                                    request.setPrompt(request.getPrompt()+"\n最后一张参考图是本零件几何依据，必须保持其外轮廓、厚度比例、孔数孔位及观察角度。其他参考图只用于版式。不得添加螺钉、螺杆、第二块板或其他零件。"
                                        +(!pairedStress.isBlank()?"这是对最后一张应力图的精准局部编辑：保留对象外形、构图、载荷箭头、约束符号的位置，改变表面颜色为位移趋势，并将短标题改为位移分布云图。颜色必须彻底重绘，禁止保留应力图的孔缘/根部暖色环。所有固定约束面及紧邻区域必须深蓝色。"
                                            +(figure.id().equals("5-7")?"定位销最上面的自由端面及紧邻顶端侧面必须橙红色，沿高度从底部蓝、下段青、中段绿、上段黄、顶端橙红连续过渡；顶端绝不能蓝色或青色。禁止侧面中部孤立热点。":"板的四个角部固定孔及孔边必须深蓝，暖色仅保留在板上表面中部的连续局部区域，不画任何孔边暖色环。")
                                            +"坐标轴三条分支各仅标一个字母X、Y、Z，不得写XYZ合在一起或重复X。":"只改表面分布与载荷约束标记，不改几何。"));
                                }
                            }
                            if(!references.isEmpty())request.setReferenceImageUrls(references);
                        }
                        Path original=null;
                        for(int attempt=0;attempt<2;attempt++) {
                            var result=images.generate(request);
                            if (!"success".equals(result.getStatus())) throw new IllegalStateException("Doki 图片生成未完成，请重试此任务");
                            original=Path.of(result.getFilePath());
                            var verdict=parse(text.inspectImage("你是工程配图可见内容核验员，只返回JSON。",
                                "核验图内可见文字。若含有示意、AI生成、水印、伪称实际求解成功等字样，accepted为false。图类为"+figure.kind()+"；网格图不得出现声称实际计算出的节点/单元总数，云图不得出现具体应力MPa/位移mm结果或真实求解完成状态；其他图中的设计尺寸和材料标注允许。"
                                    +(List.of("stress","displacement").contains(figure.kind())?"另检查是否能清楚看到紫红色载荷箭头及蓝色固定约束符号，缺少任一种则不通过。允许载荷、固定约束标签。应力图若只有横贯整件的等宽彩虹条带而无局部热点，不通过。只核验可见特征，不判断数值或工程精度。":"不要评价绘图精度，只核验实际可见文字。")
                                    +"返回{\"accepted\":true或false}。",
                                "data:image/"+(original.toString().endsWith(".jpg")?"jpeg":"png")+";base64,"+Base64.getEncoder().encodeToString(Files.readAllBytes(original))));
                            if(verdict.path("accepted").asBoolean(false))break;
                            original=null;request.setPrompt(request.getPrompt()+"\n上一张图片检查未通过。重新生成，载荷箭头和固定约束必须醒目可见；只允许短图名和必要标签，不写示意、AI、水印、软件名、节点数、单元数或求解结果值。");
                        }
                        if(original==null)throw new IllegalStateException("Doki 配图文字检查未通过，请重试此任务");
                        String filename="figure-"+figure.id()+ (original.toString().endsWith(".jpg")?".jpg":".png");
                        Files.copy(original,dir.resolve(filename),StandardCopyOption.REPLACE_EXISTING); t.file=filename;
                    }
                    return mapper.createObjectNode().put("figureId",figure.id());
                } catch (Exception e) { throw new IllegalStateException(e.getMessage(),e); }
            }),figures);
                imageBranches.put(figure.id(),branch);pending.add(branch);
            }
            CompletableFuture.allOf(pending.toArray(CompletableFuture[]::new)).join();
            stage(state,"字数与结构检查");
            repairBodyLength(state);
            if (state.wordCount < 6000 || state.wordCount > 9000) throw new IllegalStateException("Doki 正文字数检查未通过，请重试调整");
            synchronized(state){state.tasks.computeIfAbsent("summary",key->new MechanicalPaperState.Task("摘要与正文一致性检查"));save(state);}
            task(state,"summary",()-> {
                var summary=parse(text.generateJson("你是机械说明书编辑，只返回JSON。",
                    "根据实际正文写中英文摘要，中文约300字。本文只做结构方案绘制、解析强度刚度计算和定性分布分析；没有实际CAD建模或有限元求解。不得声称完成三维模型建模、进行了仿真验证、真实网格求解或实验；不写编辑说明、示意或虚假等词。返回{\"abstract\":\"中文\",\"englishAbstract\":\"English\"}。题目："+state.title+"。实际正文："+
                    MechanicalPaperPlan.SECTIONS.stream().map(s->state.tasks.get("text:"+s.id()).content.path("paragraphs").toString()).toList()));
                if(summary.path("abstract").asText().isBlank()||summary.path("englishAbstract").asText().isBlank())throw new IllegalStateException("Doki 摘要检查未通过");
                return summary;
            });
            var summary=state.tasks.get("summary").content;
            synchronized(state){((com.fasterxml.jackson.databind.node.ObjectNode)state.design).put("abstract",summary.path("abstract").asText()).put("englishAbstract",summary.path("englishAbstract").asText());save(state);}
            stage(state,"Word 排版与导出");
            task(state,"export",()-> {
                try { exporter.export(state,dir); return mapper.createObjectNode().put("file","paper.docx"); }
                catch (Exception e) { throw new IllegalStateException("Doki 文档排版失败，请重试",e); }
            });
            billing.complete(userId,state);
            state.status="SUCCESS";stage(state,"生成完成");
        } catch(Exception e) {
            Throwable cause=e;while(cause.getCause()!=null)cause=cause.getCause();
            log.warn("Mechanical paper task failed id={} causeType={}",state.id,cause.getClass().getSimpleName());
            state.status="FAILED";state.error=friendly(e); save(state);
        } finally { active.remove(state.id); }
    }
    private void repairBodyLength(MechanicalPaperState state) {
        for(int attempt=0;attempt<=4;attempt++) {
            state.wordCount=MechanicalPaperPlan.SECTIONS.stream().mapToInt(s->bodyWords(state.tasks.get("text:"+s.id()).content)).sum();
            if(state.wordCount>=6000&&state.wordCount<=9000)return;
            var section=MechanicalPaperPlan.SECTIONS.stream().max(Comparator.comparingDouble(s-> {
                double ratio=(double)bodyWords(state.tasks.get("text:"+s.id()).content)/s.words();
                return state.wordCount>9000?ratio:-ratio;
            })).orElseThrow();
            var task=state.tasks.get("text:"+section.id());
            synchronized(state){task.status="waiting";var summary=state.tasks.get("summary");if(summary!=null)summary.status="waiting";save(state);}
            if(attempt==4)break; // A retry must regenerate this branch, not loop over all-completed tasks.
            int target=Math.max(250,Math.min(1100,bodyWords(task.content)+8050-state.wordCount));
            var adjusted=new MechanicalPaperPlan.Section(section.id(),section.chapter(),section.title(),target);
            task(state,"text:"+section.id(),()->writeSection(state,adjusted));
        }
    }
    private JsonNode writeSection(MechanicalPaperState state,MechanicalPaperPlan.Section section) {
        String prompt="题目："+state.title+"。共享设计参数与计算依据："+state.design+"\n只写二级小节 "+section.id()+"，标题使用规划中的对应标题。"
            +"正文目标"+section.words()+"字，按自然段组织，不写一级二级标题，不写来源说明、编辑说明、示意或待补充，不编造文献、实验、真实仿真操作。"
            +"按设计假定和解析计算展开具体结构与参数分析，不把生成云图当作实际有限元求解证据。只返回 JSON："
            +"{\"paragraphs\":[\"自然段\"],\"formulas\":[\"可编辑线性数学表达，含代入计算及单位\"],\"tables\":[{\"caption\":\"表题\",\"headers\":[\"名称\",\"值\"],\"rows\":[[\"项\",\"值\"]]}]}。"
            +"第三章每节至少3条公式；仅2.1、4.1、5.3各一个表格，其他节tables为空；公式仅第三章。正文准确提及该节图号与对象。"
            +"不要增加三级标题，避免套话和重复段落。"+sectionWritingGuidance(section);
        JsonNode lastValid=null;
        for(int attempt=0;attempt<3;attempt++) {
            JsonNode result=parse(text.generateJson("你是机械论文作者，以材料力学解析计算和一致的设计假定撰写。严格返回 JSON。",prompt));
            try{validateSection(result,section);}catch(IllegalStateException invalid){prompt+="\n上次检查未通过："+invalid.getMessage()+"。重新输出，任何正文或图名都不要含示意、待补充或参考文献序号。";continue;}
            if(lastValid==null || Math.abs(bodyWords(result)-section.words())<Math.abs(bodyWords(lastValid)-section.words()))lastValid=result;
            int count=bodyWords(result);
            if(count >= Math.ceil(section.words()*.90) && count <= Math.floor(section.words()*1.06)) return result;
            prompt += "\n上次paragraphs正文实测为"+count+"字；公式、表格和标题不计入字数。重新输出整个小节，按内容组织自然段，不强制段数，补充或精简具体结构分析至"+section.words()+"字。不得靠通用知识、重复尺寸、图纸作用或意义评价凑字。";
        }
        if(lastValid!=null && bodyWords(lastValid)<section.words()*.9) {
            int extra=Math.max(120,section.words()-bodyWords(lastValid));
            JsonNode addition=parse(text.generateJson("你是机械说明书作者，只输出严格 JSON。",
                "在此小节末尾追加"+extra+"字尚未论述的具体设计依据，不重复已有内容、不含示意、来源、编辑说明或引用序号。不得重复定义、抄写表格或复述尺寸，不新增未经计算的结果。只返回{\"paragraphs\":[\"自然段\"]}。共享参数："+state.design+"，小节："+section.id()+"，已有内容："+lastValid+sectionWritingGuidance(section)));
            var expanded=lastValid.deepCopy();
            var paragraphs=(com.fasterxml.jackson.databind.node.ArrayNode)expanded.path("paragraphs");
            if(addition.path("paragraphs").isArray())for(var paragraph:addition.path("paragraphs"))if(paragraph.isTextual()&&!paragraph.asText().isBlank())paragraphs.add(paragraph.asText());
            validateSection(expanded,section);lastValid=expanded;
        }
        // Per-section budgets guide balance; the user's hard limit applies to the whole body.
        if(lastValid!=null && bodyWords(lastValid)>=Math.ceil(section.words()*.75) && bodyWords(lastValid)<=Math.floor(section.words()*1.5))return lastValid;
        throw new IllegalStateException("Doki 小节字数未达到要求，请重试");
    }
    static String sectionWritingGuidance(MechanicalPaperPlan.Section section) {
        String common="\n写法参照机械结构设计说明书：直接写本题对象、选择依据、关键尺寸关系、受力路径和校核条件，不写零件百科、软件教程、图纸有什么作用或重复的背景意义。参数表已有数据只解释决定选型的关键项，不逐行转述。图文必须对应本节规划的图号、零件名称及工况；尤其5.4仅分析图5-4/5的承载件，5.5仅分析图5-6/7的功能件，不替换成其他零件。"
            +"前章已确定的材料、整体尺寸和工作原理不整套重述，只在当前论证需要时引用。没有图像测量或求解证据，不声称无干涉、验证合理、最大应力或最大位移已测得；第三章数值只能作为解析结果引用。方法边界由5.3前固定文字说明一次，其他小节不反复解释生成过程。";
        return common+switch(section.chapter()) {
            case 1 -> "绪论只交代具体应用需求、现有问题和本设计范围，避免宽泛行业发展史和章节安排的逐章复述。";
            case 2 -> "总体方案写工况、方案取舍、各机构位置和工作循环，比较只保留与本题需求有关的理由。";
            case 3 -> "计算写模型简化条件、载荷来源、公式变量单位、代入关系、选用规格和判据；不在后章重复推导。";
            case 4 -> "零件设计集中写每个零件的实际结构、配合基准、关键公差和加工要求，不重复第三章计算或第二章总装原理。";
            case 5 -> "装配段只写基准、连接关系和需控制的间隙；二维图段只写关键装配尺寸链；网格段只写分析对象、约束及局部细化位置，正文和表格都不得写节点总数、单元总数或声称已完成实际离散求解；云图段只写受力路径、分布趋势和结构改进依据，不逐色描述、不复述全套几何参数、不追加第三章之外的数值。";
            default -> "总结归纳已确定的方案和关键解析结果，改进方向只保留本设计的具体限制，不列通用未来展望。";
        };
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
        for(var p:node.path("paragraphs"))if(p.asText().matches("(?s).*(通过三维建模|完成三维建模|通过[^。]*有限元分析|进行了仿真验证).*"))throw new IllegalStateException("Doki 正文不得声称进行了实际CAD建模或有限元求解，请用解析计算与结构表达说明");
        if(section.id().equals("5.3") && node.toString().matches("(?s).*(节点总数|单元总数|节点数|单元数).*"))throw new IllegalStateException("Doki 不得虚构节点数或单元数，正文与表格只说明划分策略和边界条件");
        for(var table:node.path("tables")) {
            int columns=table.path("headers").size();
            if(columns<2 || columns>6 || table.path("rows").isEmpty() || table.path("rows").size()>15) throw new IllegalStateException("Doki 表格结构检查未通过");
            for(var row:table.path("rows")) if(!row.isArray() || row.size()!=columns) throw new IllegalStateException("Doki 表格列数不一致");
        }
    }
    static void validateDesign(JsonNode design) {
        if(design.path("parts").size()!=4 || design.path("sections").size()!=MechanicalPaperPlan.SECTIONS.size()
           || design.path("figures").size()!=14 || design.path("inputs").asText().isBlank() || !planningCalculationsPresent(design.path("calculations"))
           || design.path("abstract").asText().isBlank() || design.path("englishAbstract").asText().isBlank() || design.path("keywords").isEmpty()) throw new IllegalStateException("Doki 设计规划不完整");
        Set<String> partNames=new HashSet<>();
        for(var part:design.path("parts")) if(part.path("name").asText().isBlank() || part.path("geometry").asText().isBlank() || !partNames.add(part.path("name").asText())) throw new IllegalStateException("Doki 零件规划不完整或存在重复");
        for(var s:MechanicalPaperPlan.SECTIONS) if(find(design,"sections",s.id()).path("title").asText().isBlank()) throw new IllegalStateException("Doki 章节规划不完整");
        for(var f:MechanicalPaperPlan.FIGURES) if(!f.kind().equals("photo") && (find(design,"figures",f.id()).path("description").asText().isBlank() || find(design,"figures",f.id()).path("caption").asText().isBlank())) throw new IllegalStateException("Doki 配图规划不完整");
    }
    private static boolean planningCalculationsPresent(JsonNode value) {
        return value.isTextual()?!value.asText().isBlank():value.isArray()&&value.size()>=6;
    }
    private static void normalizeCaptions(JsonNode design) {
        if(design==null)return;
        for(var figure:design.path("figures"))if(figure instanceof com.fasterxml.jackson.databind.node.ObjectNode object)object.put("caption",figure.path("caption").asText().replace("示意",""));
    }
    public static JsonNode find(JsonNode root,String field,String id) {
        for(JsonNode item:root.path(field)) if(id.equals(item.path("id").asText())) return item;
        return com.fasterxml.jackson.databind.node.MissingNode.getInstance();
    }
    static String planPrompt(String title) {
        return "为题目【"+title+"】规划完整机械设计论文，不改变题目、不要把非夹具题目写成夹具。制定一组明确且自洽的方案级设计假定：用途、尺寸、材料、载荷、结构、工况、单位，计算依据使用解析公式而非编造仿真或实验。"
            +"正文六章，一级标题："+MechanicalPaperPlan.CHAPTERS+"；二级章节槽："+MechanicalPaperPlan.SECTIONS
            +"。配图位置固定："+MechanicalPaperPlan.FIGURES+"。第一章实物图在程序中单独真实检索，不在规划中生成图1-1。四个零件必须不同且属于同一总装。图5-4/5是同一承载件、5-6/7是同一功能件。摘要不得声称实际进行了CAD建模、有限元求解或仿真验证。"
            +"输出 JSON：{\"abstract\":\"中文摘要300字\",\"englishAbstract\":\"英文摘要\",\"keywords\":[\"关键词\"],\"inputs\":\"统一参数、结构与设计工况假定\",\"calculations\":\"第三章解析公式、代入数据、单位与结果，至少6项\","
            +"\"parts\":[{\"name\":\"实际零件名\",\"geometry\":\"统一尺寸材料孔位\"}],\"sections\":[{\"id\":\"1.1\",\"title\":\"适配题目的二级标题\"}],\"figures\":[{\"id\":\"2-1\",\"caption\":\"准确零件/图名，不含示意\",\"description\":\"画面要求、对应零件、结构、尺寸、载荷与约束\"}]}。sections完整14项，figures完整14项，parts恰好4项。不给参考文献，不生成封面、诚信声明、流程图。";
    }
    static String figurePrompt(JsonNode design,MechanicalPaperPlan.Figure figure) {
        var specification=find(design,"figures",figure.id());
        if(List.of("structure","assembly").contains(figure.kind()))return
            "绘制单张机械CAD模型视口风格图片，不是产品渲染或照片。对象："+specification.path("caption").asText()+"。以下资料仅用于几何、数量、孔位和装配关系："+design.path("inputs").asText()+"；零件："+design.path("parts")
            +"。视觉要求优先于资料中的任何材质外观：严格仿照SolidWorks关闭RealView后的着色带边线模式，正交等轴测，所有零件表面为均匀无纹理的浅灰或中灰，工件可为纯淡蓝色，细黑线勾勒轮廓及零件接缝；平面只用单一色块，曲面仅简单平滑明暗。纯白或浅灰背景，左下角小XYZ坐标轴。禁止金属颗粒、铸铁粗糙纹理、拉丝、镜面反射、光泽高光、环境反射、地面、投影、摄影灯光或景深。必须像CAD建模视图而不是拟真实物。"
            +(figure.id().equals("5-1")?"总装图只显示装配模型和坐标轴，不加标题、文字标签、尺寸线或引出线。":"可用少量简短零件标签，不加材质文字。")
            +"结构与共享参数一致，不拼贴，不伪造软件窗口、品牌、菜单、操作状态或截图，不写示意、AI、水印、资料来源、论文图号。横向清晰画幅。";
        if(figure.kind().equals("mesh"))return "生成单张机械结构网格划分配图。对象："+specification.path("caption").asText()+"。仅依据此几何信息："+design.path("parts")
            +"。浅灰白工程视口背景，单一承载零件等轴视角，表面清晰均匀的细三角网格，孔边局部加密，蓝色约束符号和左下XYZ轴。"
            +"只显示图名和XYZ，不要任何数字、单位、节点数、单元数、网格质量、软件界面、软件品牌、示意、AI、水印或求解结果。网格仅用于讨论分区与细化策略，不代表真实软件求解。横向画幅。";
        if(List.of("stress","displacement").contains(figure.kind())) {
            String caption=specification.path("caption").asText();
            JsonNode target=com.fasterxml.jackson.databind.node.MissingNode.getInstance();
            for(var part:design.path("parts"))if(!part.path("name").asText().isBlank() && caption.contains(part.path("name").asText())) {target=part;break;}
            String geometry=target.isMissingNode()?"仅绘制图名指定的单一零件，不混入其他零件":target.toString();
            String boundary=caption.contains("夹具体")?
                "板式夹具体：四个角部安装孔内壁为固定约束，四处都绘制成组蓝色支承符号。上表面中部为向下的钻削载荷区，夹紧接触区有向下压力；在相应表面上方绘制三支粗紫红箭头，箭头尖端朝下并落在表面，标注载荷。不要画向上载荷，不要让箭头悬空。":
                caption.contains("定位销")?"圆柱定位销竖直放置，下部安装段固定，沿该段周向排列蓝色支承符号；上部工作段受水平切削分力，从左侧绘制三支粗紫红箭头指向工作段侧面，箭头尖端落在表面，标注载荷。它是短圆柱轴，不是梁、螺杆、螺栓或箱体。":
                "按该零件在装配中的实际连接面显示固定约束，在工作受力面显示载荷，载荷箭头尖端须接触受力面。";
            String distribution=figure.kind().equals("stress")?
                "应力趋势：大部分表面为深蓝到浅蓝，仅在受力连接部、孔缘或固定根部出现小面积青绿过渡和局部黄红热点。热点沿真实几何边缘局部分布，不得把整个零件涂满红黄绿，不得使用贯穿整件的等宽彩虹条纹。定位销热点在固定段上缘根部，工作端主要低值。":
                "位移趋势：固定部位为深蓝，随远离固定连接处平滑过渡为青绿，自由工作端或板中部只有局部黄橙高值。定位销从下部固定段蓝色向上端暖色渐变；板件从四角固定孔蓝色向中部局部暖色过渡。不要把应力热点分布直接复制为位移分布。";
            return "生成单张工程定性分析配图。本图对象："+caption+"；仅使用本零件的几何信息："+geometry
                +"。参考图片仅用于工程视口版式，不复制梁结构、软件字样或数值。浅灰白背景，单一零件正交等轴视角，主体占画面中间约三分之二，细淡网格，不使用发光、金属反光或照片级渲染。"
                +"载荷与约束是本次最重要内容，箭头长度约主体高度五分之一，粗线、大箭头头部，与主体对比清楚；蓝色约束符号成组贴在固定面。用清晰大字载荷、固定约束及细引线标注，不能只画左下角坐标轴而省略边界条件。"+boundary+distribution
                +"同一零件的应力与位移图必须保持几何、孔位、视角和载荷约束位置一致，仅改变颜色分布。左下XYZ坐标轴不得重复轴名；右侧小型蓝低红高竖直彩虹色标，色标不能遮挡主体。"
                +"仅显示本图短标题、XYZ、相对分布、高、低、载荷、固定约束；除以上文字外不写任何标注。不得出现任何数字、MPa、mm、应力结果、位移结果、软件名称、最大值、材料许用值、计算结论、示意、AI、水印或求解状态。"
                +"这是相对分布的定性分析图，不代表实际有限元求解。清晰横向构图。";
        }
        String common="仅生成一张机械论文插图，不拼贴不同图。统一设计依据："+design.path("inputs").asText()+"；零件尺寸："+design.path("parts")
            +"。本图："+specification.path("caption").asText().replace("示意","")+"。内容："+specification.path("description").asText().replace("示意","")
            +"。清晰横向构图，文字简洁准确且大字号，结构必须与总装一致。图内不得出现示意、AI、Doki、水印、资料来源、论文图号或编辑说明。";
        return common+switch(figure.kind()) {
            case "stress","displacement" -> "参考图仅用于视口版式、背景、坐标轴、箭头和色标位置，不复制参考图梁的几何结构、数值、单位或软件字样。本图对象必须是本图规划的零件。严格采用工程分析视口视觉：浅灰到白色背景，单一三维零件等轴视角，细网格，紫红色载荷箭头，蓝色约束符号，左下角XYZ坐标，右侧竖直彩虹色标从蓝色低值到红色高值。"
                +"左上短标题为"+specification.path("caption").asText()+"，小字定性分析。色标仅标相对分布、高、低；不得编造仿真数值、单位量程、软件名、软件界面、求解成功标志或真实截图。分析类型："+figure.kind()+"。";
            case "part","drawing" -> "白底黑线机械工程图，清晰的主视、俯视和必要轴测图。尺寸线箭头规范，仅标3至5个关键尺寸，只使用共享参数，不增加数字0、不改单位、不把毫米放大十倍。视图投影关系和孔位必须对应，侧视图不得重复俯视图。删除非必要文字，不添加学校标题栏，零件材料与标注准确。";
            case "mesh" -> "浅灰白工程视口背景，三维零件表面细密三角网格，孔位及边缘适当加密，坐标轴和约束符号清楚，无软件品牌和虚构节点数。";
            case "force" -> "白底黑线受力简图，箭头、尺寸、支承和符号清晰，与第三章解析计算的结构与载荷一致。";
            default -> "采用SolidWorks默认建模视口的视觉风格，仅画模型区域，不伪造软件窗口、菜单或截图。正交等轴测视角，着色带边线模式，细黑色轮廓与零件接缝清晰，零件为均匀浅灰、中灰实体色，少量功能件可用克制的淡蓝色区分。背景为纯白或浅灰，无地面和摄影场景。关闭RealView与照片级渲染效果：不要金属颗粒、拉丝、锈迹、镜面反射、环境贴图、景深、强光、真实材质纹理和戏剧性阴影；只有简单平滑面着色，不要拟真实物照片。零件清楚完整，总装几何、数量和孔位必须与共享设计参数一致。";
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
