package com.dropai.rewrite.service.ppt;

import com.dropai.rewrite.auth.AuthContext;
import com.dropai.rewrite.config.PptProperties;
import com.dropai.rewrite.service.PointService;
import com.dropai.rewrite.service.ppt.rendering.bundle.v1.LoadedRenderPlanBundle;
import com.dropai.rewrite.service.ppt.rendering.bundle.v1.RenderPlanBundleException;
import com.dropai.rewrite.service.ppt.rendering.bundle.v1.RenderPlanBundleLoader;
import com.dropai.rewrite.service.ppt.rendering.production.v1.ProductionRenderPlanCoordinator;
import com.dropai.rewrite.service.ppt.rendering.production.v1.ProductionPresentationAdapter;
import com.dropai.rewrite.service.ppt.rendering.renderer.v1.RenderedPptx;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.core.io.FileSystemResource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

@Service
public class PptProjectService {
    public static final String FEATURE_CODE="PPT_GENERATE";
    private final JdbcTemplate jdbc; private final ObjectMapper mapper; private final PptDocumentParser parser; private final SourceDocumentPrecheckService precheck; private final PptAiService ai; private final PptTextValidator validator; private final PointService points; private final PptProperties properties; private final PptGenerationSkillService generationSkill; private final PptEngineV1Service engine; private final ProductionRenderPlanCoordinator renderPlans; private final PptContentPlannerV2InputAdapter inputAdapter; private final RenderPlanBundleLoader bundleLoader;
    private final Path root;
    @Autowired
    public PptProjectService(JdbcTemplate jdbc,ObjectMapper mapper,PptDocumentParser parser,SourceDocumentPrecheckService precheck,PptAiService ai,PptTextValidator validator,PointService points,PptProperties properties,PptGenerationSkillService generationSkill,PptEngineV1Service engine,ProductionRenderPlanCoordinator renderPlans,PptContentPlannerV2InputAdapter inputAdapter){this(jdbc,mapper,parser,precheck,ai,validator,points,properties,generationSkill,engine,renderPlans,inputAdapter,new RenderPlanBundleLoader(),Path.of("storage","ppt"));}
    PptProjectService(JdbcTemplate jdbc,ObjectMapper mapper,PptDocumentParser parser,SourceDocumentPrecheckService precheck,PptAiService ai,PptTextValidator validator,PointService points,PptProperties properties,PptGenerationSkillService generationSkill,PptEngineV1Service engine,ProductionRenderPlanCoordinator renderPlans,PptContentPlannerV2InputAdapter inputAdapter,RenderPlanBundleLoader bundleLoader,Path root){this.jdbc=jdbc;this.mapper=mapper;this.parser=parser;this.precheck=precheck;this.ai=ai;this.validator=validator;this.points=points;this.properties=properties;this.generationSkill=generationSkill;this.engine=engine;this.renderPlans=renderPlans;this.inputAdapter=inputAdapter;this.bundleLoader=bundleLoader;this.root=root.toAbsolutePath().normalize();}

    @Transactional public Map<String,Object> create(Map<String,Object> input){
        Long userId=AuthContext.requireUserId();String id=UUID.randomUUID().toString();String topic=text(input,"topic","");int target=integer(input.get("targetSlideCount"),16);target=Math.max(8,Math.min(properties.maxSlides(),target));LocalDateTime now=LocalDateTime.now();
        jdbc.update("INSERT INTO ppt_project(id,user_id,topic,english_topic,presenter,major,advisor,student_number,target_slide_count,status,current_stage,progress,created_at,updated_at) VALUES(?,?,?,?,?,?,?,?,?,'DRAFT','等待上传',0,?,?)",id,userId,topic,text(input,"englishTopic",""),text(input,"presenter",""),text(input,"major",""),text(input,"advisor",""),text(input,"studentNumber",""),target,now,now);
        return project(id,userId);
    }
    public List<Map<String,Object>> list(){Long userId=AuthContext.requireUserId();return jdbc.queryForList("SELECT id,topic,major,status,current_stage,progress,output_path,source_file_name,created_at,updated_at FROM ppt_project WHERE user_id=? ORDER BY created_at DESC",userId);}
    public Map<String,Object> get(String id){return project(id,AuthContext.requireUserId());}

    @Transactional public Map<String,Object> upload(String id,MultipartFile file)throws Exception{
        Long userId=AuthContext.requireUserId();project(id,userId);if(file==null||file.isEmpty())throw new IllegalArgumentException("请选择文档文件");String original=safeFileName(file.getOriginalFilename());String ext=extension(original);if(!List.of("doc","docx","pdf","pptx","txt","md","markdown").contains(ext))throw new IllegalArgumentException("仅支持 DOCX、PDF、PPTX、TXT、Markdown，可兼容DOC");if(file.getSize()>80L*1024*1024)throw new IllegalArgumentException("文件不能超过80MB");
        Path dir=inside(root.resolve(userId.toString()).resolve(id));Files.createDirectories(dir);Path target=inside(dir.resolve("source-"+UUID.randomUUID()+"."+ext));Files.copy(file.getInputStream(),target,StandardCopyOption.REPLACE_EXISTING);
        jdbc.update("UPDATE ppt_project SET source_file_path=?,source_file_name=?,source_file_size=?,status='UPLOADED',current_stage='等待解析',progress=8,updated_at=? WHERE id=? AND user_id=?",target.toString(),original,file.getSize(),LocalDateTime.now(),id,userId);return project(id,userId);
    }
    @Transactional public Map<String,Object> analyze(String id)throws Exception{
        Long userId=AuthContext.requireUserId();generationSkill.requireManifest();Map<String,Object> p=project(id,userId);Path source=requiredPath(p.get("source_file_path"));Path assetDir=inside(source.getParent().resolve("assets"));PptDocumentParser.ParsedDocument doc=parser.parse(source,assetDir);
        SourceDocumentPrecheckService.Report report=precheck.require(doc);
        jdbc.update("DELETE FROM ppt_asset WHERE project_id=?",id);for(PptDocumentParser.Asset a:doc.assets()){jdbc.update("INSERT INTO ppt_asset(id,project_id,source_type,source_page,source_position,file_path,caption,width,height,created_at) VALUES(?,?,'SOURCE_DOCUMENT',?,?,?,?,?,?,?)",UUID.randomUUID().toString(),id,a.sourcePage(),a.sourcePosition(),a.path().toString(),a.caption(),a.width(),a.height(),LocalDateTime.now());}
        Map<String,Object> analysis=new LinkedHashMap<>();analysis.put("documentTitle",doc.title());analysis.put("headings",doc.headings());analysis.put("blocks",doc.blocks());analysis.put("imageCount",doc.totalImageCount());analysis.put("validImageCount",doc.assets().size());analysis.put("filteredAssetCount",doc.filteredAssetCount());analysis.put("tableCount",doc.tableCount());analysis.put("characterCount",doc.characterCount());analysis.put("precheck",report);
        Map<String,String> metadata=inputAdapter.fromParsedDocument(doc,"computer").metadata();String topic=resolveAnalyzedTopic(string(p.get("topic")),metadata,doc.title());jdbc.update("UPDATE ppt_project SET topic=?,english_topic=COALESCE(NULLIF(english_topic,''),?),presenter=COALESCE(NULLIF(presenter,''),?),major=COALESCE(NULLIF(major,''),?),advisor=COALESCE(NULLIF(advisor,''),?),student_number=COALESCE(NULLIF(student_number,''),?),analysis_json=?,status='ANALYZED',current_stage='解析完成',progress=24,updated_at=? WHERE id=? AND user_id=?",topic,metadata.getOrDefault("englishTitle",topic),metadata.getOrDefault("presenter",""),metadata.getOrDefault("major",""),metadata.getOrDefault("advisor",""),metadata.getOrDefault("studentNumber",""),json(analysis),LocalDateTime.now(),id,userId);return detail(id,userId);
    }
    @Transactional public Map<String,Object> generateOutline(String id){
        generationSkill.requireManifest();
        Long userId=AuthContext.requireUserId();Map<String,Object> p=project(id,userId);PptDocumentParser.ParsedDocument doc=document(p);String topic=string(p.get("topic"));PptAiService.AiOutline result=ai.createOutline(topic,doc);if(string(p.get("english_topic")).isBlank())jdbc.update("UPDATE ppt_project SET english_topic=? WHERE id=? AND user_id=?",ai.createEnglishTitle(topic),id,userId);jdbc.update("DELETE FROM ppt_outline WHERE project_id=?",id);int order=0;for(PptAiService.OutlineItem item:result.items()){jdbc.update("INSERT INTO ppt_outline(id,project_id,section_order,title,description,target_slides) VALUES(?,?,?,?,?,?)",UUID.randomUUID().toString(),id,++order,item.title(),item.description(),item.slides());}
        jdbc.update("UPDATE ppt_project SET status='OUTLINE_READY',current_stage=?,progress=38,updated_at=? WHERE id=? AND user_id=?","目录已生成（"+result.providerStatus()+"）",LocalDateTime.now(),id,userId);Map<String,Object> out=detail(id,userId);out.put("providerInvoked",result.providerInvoked());out.put("providerStatus",result.providerStatus());return out;
    }
    @Transactional public Map<String,Object> saveOutline(String id,List<Map<String,Object>> items){
        Long userId=AuthContext.requireUserId();project(id,userId);if(items==null||items.size()<2||items.size()>5)throw new IllegalArgumentException("一级目录必须为2至5项");List<Map<String,Object>> before=outline(id);jdbc.update("DELETE FROM ppt_outline WHERE project_id=?",id);int order=0;for(Map<String,Object> item:items){String title=validator.compact(text(item,"title",""),20);if(title.isBlank())throw new IllegalArgumentException("目录名称不能为空");String itemId=text(item,"id",UUID.randomUUID().toString());jdbc.update("INSERT INTO ppt_outline(id,project_id,section_order,title,description,target_slides) VALUES(?,?,?,?,?,?)",itemId,id,++order,title,text(item,"description",""),Math.max(1,Math.min(2,integer(item.get("targetSlides"),2))));}jdbc.update("UPDATE ppt_project SET status='OUTLINE_READY',current_stage='目录已修改，等待重新规划',progress=38,updated_at=? WHERE id=? AND user_id=?",LocalDateTime.now(),id,userId);Map<String,Object> result=detail(id,userId);result.putAll(outlineImpact(before,items,id));return result;
    }
    @Transactional public Map<String,Object> plan(String id){
        generationSkill.requireManifest();
        Long userId=AuthContext.requireUserId();Map<String,Object> p=project(id,userId);List<Map<String,Object>> outline=outline(id);if(outline.size()<4)throw new IllegalStateException("请先确认至少4项目录");List<String> blocks=stringList(readAnalysis(p).get("blocks"));List<Map<String,Object>> assets=jdbc.queryForList("SELECT id,file_path,source_page,caption FROM ppt_asset WHERE project_id=? ORDER BY source_page,id",id);jdbc.update("DELETE FROM ppt_slide WHERE project_id=?",id);int order=0,blockIndex=0,assetIndex=0;
        for(Map<String,Object> section:outline){String sectionId=string(section.get("id"));int count=integer(section.get("target_slides"),2);for(int i=0;i<count;i++){String source=blocks.isEmpty()?string(section.get("description")):blocks.get(blockIndex++%blocks.size());List<String> boxes=summarize(source);String title=i==0?string(section.get("title")):validator.compact(firstPhrase(source),24);String assetJson="[]";if(assetIndex<assets.size())assetJson=json(List.of(string(assets.get(assetIndex++).get("id"))));PptTextValidator.ValidationResult checked=validator.validateSlideTextLimits(title,boxes);jdbc.update("INSERT INTO ppt_slide(id,project_id,section_id,slide_order,slide_type,title,body_boxes_json,asset_ids_json,speaker_notes,layout_type,validation_status) VALUES(?,?,?,?,?,?,?,?,?,?,?)",UUID.randomUUID().toString(),id,sectionId,++order,"CONTENT",checked.title(),json(checked.bodyBoxes()),assetJson,source,assetJson.equals("[]")?"KEYWORDS":"IMAGE_TEXT",checked.status());}}
        jdbc.update("DELETE FROM ppt_page_task WHERE project_id=?",id);for(Map<String,Object> slide:slides(id))jdbc.update("INSERT INTO ppt_page_task(id,project_id,slide_plan_id,status,progress,retry_count) VALUES(?,?,?,'WAITING',0,0)",UUID.randomUUID().toString(),id,slide.get("id"));
        jdbc.update("UPDATE ppt_project SET status='PLANNED',current_stage='幻灯片规划完成',progress=55,updated_at=? WHERE id=? AND user_id=?",LocalDateTime.now(),id,userId);return detail(id,userId);
    }
    @Transactional public Map<String,Object> updateSlide(String id,String slideId,Map<String,Object> input){Long userId=AuthContext.requireUserId();project(id,userId);PptTextValidator.ValidationResult checked=validator.validateSlideTextLimits(text(input,"title","内容概览"),stringList(input.get("bodyBoxes")));jdbc.update("UPDATE ppt_slide SET title=?,body_boxes_json=?,layout_type=?,validation_status=? WHERE id=? AND project_id=?",checked.title(),json(checked.bodyBoxes()),text(input,"layoutType","KEYWORDS"),checked.status(),slideId,id);jdbc.update("UPDATE ppt_project SET status='OUTLINE_READY',current_stage='页面内容已修改，等待重新规划',progress=38,output_path=NULL,updated_at=? WHERE id=? AND user_id=?",LocalDateTime.now(),id,userId);return detail(id,userId);}
    public Map<String,Object> regenerateSlide(String id,String slideId){Long userId=AuthContext.requireUserId();project(id,userId);Map<String,Object> s=jdbc.queryForMap("SELECT * FROM ppt_slide WHERE id=? AND project_id=?",slideId,id);return updateSlide(id,slideId,Map.of("title",s.get("title"),"bodyBoxes",summarize(string(s.get("speaker_notes"))),"layoutType",s.get("layout_type")));}

    public Map<String,Object> generate(String id){
        Long userId=AuthContext.requireUserId();
        Map<String,Object> project=project(id,userId);
        generationSkill.requireManifest();
        if(!List.of("PLANNED","SUCCESS","FAILED").contains(string(project.get("status")))){
            throw new IllegalStateException("RenderPlan 未准备好或已因项目内容变更而失效，请先重新生成PPT方案");
        }
        Path bundleDirectory=inside(root.resolve(userId.toString()).resolve(id).resolve("rendering-v1"));
        if(!Files.isDirectory(bundleDirectory)||!Files.isRegularFile(bundleDirectory.resolve("current"))){
            throw new IllegalStateException("RenderPlan 未准备好或不存在，请先重新生成PPT方案");
        }
        LoadedRenderPlanBundle bundle;
        try{
            bundle=bundleLoader.load(bundleDirectory,renderPlans.runtimeExpectations());
        }catch(RenderPlanBundleException exception){
            throw new IllegalStateException("RenderPlan 未准备好或不存在，请先重新生成PPT方案："+exception.getMessage(),exception);
        }
        String plannedProject=bundle.renderPlan().document().path("presentationId").asText();
        if(!ProductionPresentationAdapter.belongsToProject(plannedProject,id)){
            throw new IllegalStateException("RenderPlan 与当前PPT项目不匹配，请重新生成PPT方案");
        }
        String taskId=UUID.randomUUID().toString();LocalDateTime now=LocalDateTime.now();
        AtomicReference<Path> generatedOutput=new AtomicReference<>();
        boolean claimed=false;
        try{
            int claim=jdbc.update("UPDATE ppt_project SET status='GENERATING',current_stage='PureRenderer 正在执行冻结计划',progress=60,output_path=NULL,error_message=NULL,updated_at=? WHERE id=? AND user_id=? AND status=?",now,id,userId,string(project.get("status")));
            if(claim!=1)throw new IllegalStateException("PPT生成任务已被其他请求领取，请稍后查看进度");
            claimed=true;
            jdbc.update("INSERT INTO ppt_generation_task(id,project_id,user_id,status,progress,current_stage,created_at,updated_at) VALUES(?,?,?,'RUNNING',60,'加载冻结渲染计划',?,?)",taskId,id,userId,now,now);
            return points.chargeAfterSuccess(FEATURE_CODE,"PPT智能生成："+string(project.get("topic")),()->{
                try{
                    List<Map<String,Object>> pageTasks=jdbc.queryForList("SELECT id FROM ppt_page_task WHERE project_id=? ORDER BY id",id);
                    int completed=0;
                    for(Map<String,Object> pageTask:pageTasks){
                        String pageTaskId=string(pageTask.get("id"));
                        jdbc.update("UPDATE ppt_page_task SET status='RENDERING',progress=60,started_at=? WHERE id=?",LocalDateTime.now(),pageTaskId);
                        completed++;
                        int progress=60+(int)Math.round(completed*25.0/Math.max(1,pageTasks.size()));
                        jdbc.update("UPDATE ppt_project SET status='GENERATING',current_stage='PureRenderer 正在执行冻结计划',progress=?,updated_at=? WHERE id=? AND user_id=?",progress,LocalDateTime.now(),id,userId);
                    }
                    Path output=inside(root.resolve(userId.toString()).resolve(id).resolve("outputs").resolve(taskId+".pptx"));
                    RenderedPptx rendered=engine.generate(bundle.renderPlan(),bundle.assetResolver(),output);
                    generatedOutput.set(output);
                    int expectedSlides=bundle.renderPlan().document().path("slides").size();
                    if(!bundle.renderPlanHash().equals(rendered.renderPlanHash()))throw new IllegalStateException("PureRenderer 返回的 RenderPlan 哈希不一致");
                    if(expectedSlides!=rendered.slideCount())throw new IllegalStateException("PureRenderer 输出页数与冻结 RenderPlan 不一致");
                    jdbc.update("UPDATE ppt_page_task SET status='SUCCESS',progress=100,completed_at=? WHERE project_id=?",LocalDateTime.now(),id);
                    jdbc.update("UPDATE ppt_project SET status='SUCCESS',current_stage='生成完成',progress=100,output_path=?,error_message=NULL,updated_at=? WHERE id=? AND user_id=?",output.toString(),LocalDateTime.now(),id,userId);
                    jdbc.update("UPDATE ppt_generation_task SET status='SUCCESS',progress=100,current_stage='生成完成',updated_at=? WHERE id=?",LocalDateTime.now(),taskId);
                    Map<String,Object> result=detail(id,userId);
                    result.put("validationStatus","VALID");result.put("autoFixes",List.of());result.put("renderPlanHash",rendered.renderPlanHash());result.put("rendererVersion",rendered.rendererVersion());result.put("slideCount",rendered.slideCount());result.put("writtenBytes",rendered.writtenBytes());result.put("assetCount",bundle.assetCount());
                    return result;
                }catch(java.io.IOException exception){
                    throw new IllegalStateException("PureRenderer 生成PPTX失败："+exception.getMessage(),exception);
                }
            });
        }catch(RuntimeException exception){
            Path incompleteOutput=generatedOutput.get();
            if(incompleteOutput!=null){
                try{Files.deleteIfExists(incompleteOutput);}catch(java.io.IOException cleanupFailure){exception.addSuppressed(cleanupFailure);}
            }
            String error=safeError(exception);
            if(claimed){
                jdbc.update("UPDATE ppt_project SET status='FAILED',current_stage='生成失败',progress=0,output_path=NULL,error_message=?,updated_at=? WHERE id=? AND user_id=? AND status='GENERATING'",error,LocalDateTime.now(),id,userId);
                jdbc.update("UPDATE ppt_page_task SET status='FAILED',progress=0,error_message=?,completed_at=? WHERE project_id=? AND status<>'SUCCESS'",error,LocalDateTime.now(),id);
                jdbc.update("UPDATE ppt_generation_task SET status='FAILED',current_stage='生成失败',error_message=?,updated_at=? WHERE id=?",error,LocalDateTime.now(),taskId);
            }
            throw exception;
        }
    }
    public Map<String,Object> progress(String id){Long userId=AuthContext.requireUserId();Map<String,Object> p=project(id,userId);List<Map<String,Object>> tasks=jdbc.queryForList("SELECT status,progress,current_stage,error_message,updated_at FROM ppt_generation_task WHERE project_id=? ORDER BY created_at DESC",id);p.put("task",tasks.isEmpty()?null:tasks.get(0));Map<String,Object> counts=jdbc.queryForMap("SELECT COUNT(*) total_pages,SUM(CASE WHEN status='SUCCESS' THEN 1 ELSE 0 END) completed_pages,SUM(CASE WHEN status='FAILED' THEN 1 ELSE 0 END) failed_pages FROM ppt_page_task WHERE project_id=?",id);p.putAll(counts);p.put("downloadable",publishedOutput(p,userId,id)!=null);return p;}
    public Map<String,Object> analysis(String id){Long userId=AuthContext.requireUserId();Map<String,Object> p=project(id,userId);Map<String,Object> out=new LinkedHashMap<>(readAnalysis(p));out.put("fileName",p.get("source_file_name"));out.put("generationMode","串行逐页生成");return out;}
    public List<Map<String,Object>> pages(String id){Long userId=AuthContext.requireUserId();project(id,userId);return jdbc.queryForList("SELECT s.id,s.section_id AS outline_item_id,s.slide_order,s.slide_type AS page_role,s.title,COALESCE(t.status,s.validation_status) AS status,COALESCE(t.retry_count,0) retry_count FROM ppt_slide s LEFT JOIN ppt_page_task t ON t.slide_plan_id=s.id WHERE s.project_id=? ORDER BY s.slide_order",id);}
    @Transactional public Map<String,Object> retryPage(String id,String pageId){Long userId=AuthContext.requireUserId();project(id,userId);int changed=jdbc.update("UPDATE ppt_page_task SET status='WAITING',progress=0,retry_count=retry_count+1,error_message=NULL,started_at=NULL,completed_at=NULL WHERE project_id=? AND slide_plan_id=? AND status='FAILED' AND retry_count<?",id,pageId,properties.maxRetries());if(changed==0)throw new IllegalArgumentException("页面不是失败状态或已达到最大重试次数");Map<String,Object> out=new LinkedHashMap<>();out.put("pageId",pageId);out.put("status","WAITING");out.put("queuedPageIds",List.of(pageId));return out;}
    public FileSystemResource download(String id){Long userId=AuthContext.requireUserId();Map<String,Object> p=project(id,userId);Path path=publishedOutput(p,userId,id);if(path==null)throw new IllegalStateException("PPTX尚未发布");return new FileSystemResource(path);}
    public String downloadName(String id){Map<String,Object>p=project(id,AuthContext.requireUserId());return safeFileName(string(p.get("topic")))+".pptx";}

    private Map<String,Object> detail(String id,Long userId){Map<String,Object> p=project(id,userId);p.put("outline",outline(id));p.put("slides",slides(id));p.put("assets",jdbc.queryForList("SELECT id,source_type,source_page,caption,width,height FROM ppt_asset WHERE project_id=? ORDER BY source_page,id",id));return p;}
    private Map<String,Object> project(String id,Long userId){List<Map<String,Object>>rows=jdbc.queryForList("SELECT * FROM ppt_project WHERE id=? AND user_id=?",id,userId);if(rows.isEmpty())throw new IllegalArgumentException("PPT项目不存在或无权访问");return new LinkedHashMap<>(rows.get(0));}
    private List<Map<String,Object>> outline(String id){return jdbc.queryForList("SELECT * FROM ppt_outline WHERE project_id=? ORDER BY section_order",id);}
    private List<Map<String,Object>> slides(String id){return jdbc.queryForList("SELECT * FROM ppt_slide WHERE project_id=? ORDER BY slide_order",id);}
    private PptDocumentParser.ParsedDocument document(Map<String,Object> p){Map<String,Object>a=readAnalysis(p);return new PptDocumentParser.ParsedDocument(text(a,"documentTitle",string(p.get("topic"))),stringList(a.get("headings")),stringList(a.get("blocks")),List.of(),integer(a.get("tableCount"),0),integer(a.get("characterCount"),0));}
    private Map<String,Object> readAnalysis(Map<String,Object>p){Object v=readJson(string(p.get("analysis_json")));return v instanceof Map<?,?>m?mapper.convertValue(m,new TypeReference<>(){}):new LinkedHashMap<>();}
    private Map<String,Object> outlineImpact(List<Map<String,Object>> before,List<Map<String,Object>> after,String projectId){
        Map<String,String> oldTitles=new LinkedHashMap<>();for(Map<String,Object> item:before)oldTitles.put(string(item.get("id")),string(item.get("title")));
        List<String> affected=new ArrayList<>(),removed=new ArrayList<>(),queued=new ArrayList<>();
        for(Map<String,Object> item:after){String itemId=text(item,"id","");if(itemId.isBlank()||!oldTitles.containsKey(itemId)){queued.addAll(jdbc.queryForList("SELECT id FROM ppt_slide WHERE project_id=? AND section_id=?",projectId,itemId).stream().map(x->string(x.get("id"))).toList());continue;}if(!oldTitles.get(itemId).equals(text(item,"title","")))affected.addAll(jdbc.queryForList("SELECT id FROM ppt_slide WHERE project_id=? AND section_id=?",projectId,itemId).stream().map(x->string(x.get("id"))).toList());oldTitles.remove(itemId);}
        for(String deleted:oldTitles.keySet())removed.addAll(jdbc.queryForList("SELECT id FROM ppt_slide WHERE project_id=? AND section_id=?",projectId,deleted).stream().map(x->string(x.get("id"))).toList());
        return Map.of("affectedPageIds",affected,"removedPageIds",removed,"queuedPageIds",queued);
    }
    private Object readJson(String json){try{return json==null||json.isBlank()?List.of():mapper.readValue(json,Object.class);}catch(Exception e){return List.of();}}
    private String json(Object value){try{return mapper.writeValueAsString(value);}catch(Exception e){throw new IllegalStateException("JSON处理失败",e);}}
    private List<String> summarize(String source){String clean=PptDocumentParser.clean(source).replaceFirst("^\\[第\\d+页]\\s*","");List<String>out=new ArrayList<>();for(String part:clean.split("[。！？；;.!?\\n]+")){part=part.trim();if(part.length()<2)continue;out.add(validator.compact(part,20));if(out.size()==4)break;}if(out.isEmpty())out.add("依据源文档提炼");return out;}
    private String firstPhrase(String source){List<String>s=summarize(source);return s.isEmpty()?"内容概览":s.get(0);}
    private Path requiredPath(Object value){String s=string(value);if(s.isBlank())throw new IllegalStateException("请先上传并解析文档");return inside(Path.of(s));}
    private Path publishedOutput(Map<String,Object> project,Long userId,String projectId){
        if(!"SUCCESS".equals(string(project.get("status"))))return null;
        String raw=string(project.get("output_path"));if(raw.isBlank())return null;
        Path output=inside(Path.of(raw));
        Path outputRoot=inside(root.resolve(userId.toString()).resolve(projectId).resolve("outputs"));
        if(!output.startsWith(outputRoot)||!Files.isRegularFile(output,java.nio.file.LinkOption.NOFOLLOW_LINKS))return null;
        try{
            Path realRoot=outputRoot.toRealPath();Path realOutput=output.toRealPath();
            return realOutput.startsWith(realRoot)?realOutput:null;
        }catch(java.io.IOException exception){return null;}
    }
    private Path inside(Path p){Path n=p.toAbsolutePath().normalize();if(!n.startsWith(root))throw new IllegalArgumentException("非法文件路径");return n;}
    private String safeFileName(String n){String s=n==null?"":n.replaceAll("[\\\\/:*?\"<>|\\r\\n]","_").trim();return s.isBlank()?"未命名学术答辩":s;}
    private String extension(String n){int dot=n.lastIndexOf('.');return dot<0?"":n.substring(dot+1).toLowerCase(Locale.ROOT);}
    private String text(Map<String,Object>m,String k,String d){String v=string(m==null?null:m.get(k));return v.isBlank()?d:v;}
    static String resolveAnalyzedTopic(String configuredTopic,Map<String,String> metadata,String parserFallback){String configured=configuredTopic==null?"":configuredTopic.strip();if(!configured.isBlank())return configured;String extracted=metadata==null?"":metadata.getOrDefault("title","").strip();if(!extracted.isBlank())return extracted;return parserFallback==null?"":parserFallback.strip();}
    private String string(Object v){return v==null?"":String.valueOf(v);}
    private String safeError(Throwable error){String message=error==null?"":string(error.getMessage()).replaceAll("[\\r\\n]+"," ").trim();return message.isBlank()?"PPT生成失败":message.substring(0,Math.min(500,message.length()));}
    private int integer(Object v,int d){try{return v instanceof Number n?n.intValue():Integer.parseInt(string(v));}catch(Exception e){return d;}}
    private List<String> stringList(Object value){if(value instanceof List<?>l)return l.stream().map(this::string).filter(x->!x.isBlank()).toList();return List.of();}
}
