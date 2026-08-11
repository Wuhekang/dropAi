package com.dropai.rewrite.service.ppt;

import com.dropai.rewrite.auth.AuthContext;
import com.dropai.rewrite.service.PointService;
import com.dropai.rewrite.config.PptProperties;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.core.io.FileSystemResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Service
public class PptProjectService {
    public static final String FEATURE_CODE="PPT_GENERATE";
    private final JdbcTemplate jdbc; private final ObjectMapper mapper; private final PptDocumentParser parser; private final PptAiService ai; private final PptTextValidator validator; private final PptxGenerator generator; private final PointService points; private final PptProperties properties; private final PptTemplateService templates;
    private final Path root=Path.of("storage","ppt").toAbsolutePath().normalize();
    public PptProjectService(JdbcTemplate jdbc,ObjectMapper mapper,PptDocumentParser parser,PptAiService ai,PptTextValidator validator,PptxGenerator generator,PointService points,PptProperties properties,PptTemplateService templates){this.jdbc=jdbc;this.mapper=mapper;this.parser=parser;this.ai=ai;this.validator=validator;this.generator=generator;this.points=points;this.properties=properties;this.templates=templates;}

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
        Long userId=AuthContext.requireUserId();Map<String,Object> p=project(id,userId);Path source=requiredPath(p.get("source_file_path"));Path assetDir=inside(source.getParent().resolve("assets"));PptDocumentParser.ParsedDocument doc=parser.parse(source,assetDir);
        jdbc.update("DELETE FROM ppt_asset WHERE project_id=?",id);for(PptDocumentParser.Asset a:doc.assets()){jdbc.update("INSERT INTO ppt_asset(id,project_id,source_type,source_page,source_position,file_path,caption,width,height,created_at) VALUES(?,?,'SOURCE_DOCUMENT',?,?,?,?,?,?,?)",UUID.randomUUID().toString(),id,a.sourcePage(),a.sourcePosition(),a.path().toString(),a.caption(),a.width(),a.height(),LocalDateTime.now());}
        Map<String,Object> analysis=new LinkedHashMap<>();analysis.put("documentTitle",doc.title());analysis.put("headings",doc.headings());analysis.put("blocks",doc.blocks());analysis.put("imageCount",doc.assets().size());analysis.put("tableCount",doc.tableCount());analysis.put("characterCount",doc.characterCount());
        String topic=string(p.get("topic"));if(topic.isBlank())topic=doc.title();jdbc.update("UPDATE ppt_project SET topic=?,analysis_json=?,status='ANALYZED',current_stage='解析完成',progress=24,updated_at=? WHERE id=? AND user_id=?",topic,json(analysis),LocalDateTime.now(),id,userId);return detail(id,userId);
    }
    @Transactional public Map<String,Object> generateOutline(String id){
        Long userId=AuthContext.requireUserId();Map<String,Object> p=project(id,userId);PptDocumentParser.ParsedDocument doc=document(p);String topic=string(p.get("topic"));PptAiService.AiOutline result=ai.createOutline(topic,doc);if(string(p.get("english_topic")).isBlank())jdbc.update("UPDATE ppt_project SET english_topic=? WHERE id=? AND user_id=?",ai.createEnglishTitle(topic),id,userId);jdbc.update("DELETE FROM ppt_outline WHERE project_id=?",id);int order=0;for(PptAiService.OutlineItem item:result.items()){jdbc.update("INSERT INTO ppt_outline(id,project_id,section_order,title,description,target_slides) VALUES(?,?,?,?,?,?)",UUID.randomUUID().toString(),id,++order,item.title(),item.description(),item.slides());}
        jdbc.update("UPDATE ppt_project SET status='OUTLINE_READY',current_stage=?,progress=38,updated_at=? WHERE id=? AND user_id=?","目录已生成（"+result.providerStatus()+"）",LocalDateTime.now(),id,userId);Map<String,Object> out=detail(id,userId);out.put("providerInvoked",result.providerInvoked());out.put("providerStatus",result.providerStatus());return out;
    }
    @Transactional public Map<String,Object> saveOutline(String id,List<Map<String,Object>> items){
        Long userId=AuthContext.requireUserId();project(id,userId);if(items==null||items.size()<4)throw new IllegalArgumentException("目录至少需要4项");jdbc.update("DELETE FROM ppt_outline WHERE project_id=?",id);int order=0;for(Map<String,Object> item:items){String title=validator.compact(text(item,"title",""),20);if(title.isBlank())throw new IllegalArgumentException("目录名称不能为空");jdbc.update("INSERT INTO ppt_outline(id,project_id,section_order,title,description,target_slides) VALUES(?,?,?,?,?,?)",UUID.randomUUID().toString(),id,++order,title,text(item,"description",""),Math.max(1,Math.min(8,integer(item.get("targetSlides"),2))));}return detail(id,userId);
    }
    @Transactional public Map<String,Object> plan(String id){
        Long userId=AuthContext.requireUserId();Map<String,Object> p=project(id,userId);List<Map<String,Object>> outline=outline(id);if(outline.size()<4)throw new IllegalStateException("请先确认至少4项目录");List<String> blocks=stringList(readAnalysis(p).get("blocks"));List<Map<String,Object>> assets=jdbc.queryForList("SELECT id,file_path,source_page,caption FROM ppt_asset WHERE project_id=? ORDER BY source_page,id",id);jdbc.update("DELETE FROM ppt_slide WHERE project_id=?",id);int order=0,blockIndex=0,assetIndex=0;
        for(Map<String,Object> section:outline){String sectionId=string(section.get("id"));int count=integer(section.get("target_slides"),2);for(int i=0;i<count;i++){String source=blocks.isEmpty()?string(section.get("description")):blocks.get(blockIndex++%blocks.size());List<String> boxes=summarize(source);String title=i==0?string(section.get("title")):validator.compact(firstPhrase(source),24);String assetJson="[]";if(assetIndex<assets.size())assetJson=json(List.of(string(assets.get(assetIndex++).get("id"))));PptTextValidator.ValidationResult checked=validator.validateSlideTextLimits(title,boxes);jdbc.update("INSERT INTO ppt_slide(id,project_id,section_id,slide_order,slide_type,title,body_boxes_json,asset_ids_json,speaker_notes,layout_type,validation_status) VALUES(?,?,?,?,?,?,?,?,?,?,?)",UUID.randomUUID().toString(),id,sectionId,++order,"CONTENT",checked.title(),json(checked.bodyBoxes()),assetJson,source,assetJson.equals("[]")?"KEYWORDS":"IMAGE_TEXT",checked.status());}}
        jdbc.update("UPDATE ppt_project SET status='PLANNED',current_stage='幻灯片规划完成',progress=55,updated_at=? WHERE id=? AND user_id=?",LocalDateTime.now(),id,userId);return detail(id,userId);
    }
    @Transactional public Map<String,Object> updateSlide(String id,String slideId,Map<String,Object> input){Long userId=AuthContext.requireUserId();project(id,userId);PptTextValidator.ValidationResult checked=validator.validateSlideTextLimits(text(input,"title","内容概览"),stringList(input.get("bodyBoxes")));jdbc.update("UPDATE ppt_slide SET title=?,body_boxes_json=?,layout_type=?,validation_status=? WHERE id=? AND project_id=?",checked.title(),json(checked.bodyBoxes()),text(input,"layoutType","KEYWORDS"),checked.status(),slideId,id);return detail(id,userId);}
    public Map<String,Object> regenerateSlide(String id,String slideId){Long userId=AuthContext.requireUserId();project(id,userId);Map<String,Object> s=jdbc.queryForMap("SELECT * FROM ppt_slide WHERE id=? AND project_id=?",slideId,id);return updateSlide(id,slideId,Map.of("title",s.get("title"),"bodyBoxes",summarize(string(s.get("speaker_notes"))),"layoutType",s.get("layout_type")));}

    @Transactional public Map<String,Object> generate(String id)throws Exception{
        Long userId=AuthContext.requireUserId();Map<String,Object> p=project(id,userId);if(slides(id).isEmpty())plan(id);String taskId=UUID.randomUUID().toString();LocalDateTime now=LocalDateTime.now();jdbc.update("INSERT INTO ppt_generation_task(id,project_id,user_id,status,progress,current_stage,created_at,updated_at) VALUES(?,?,?,'RUNNING',60,'生成可编辑PPTX',?,?)",taskId,id,userId,now,now);
        try{return points.chargeAfterSuccess(FEATURE_CODE,"PPT智能生成："+string(p.get("topic")),()->{try{return generateCharged(id,userId,taskId);}catch(Exception e){throw new IllegalStateException(e.getMessage(),e);}});}catch(RuntimeException e){jdbc.update("UPDATE ppt_project SET status='FAILED',error_message=?,updated_at=? WHERE id=?",safeError(e),LocalDateTime.now(),id);jdbc.update("UPDATE ppt_generation_task SET status='FAILED',error_message=?,updated_at=? WHERE id=?",safeError(e),LocalDateTime.now(),taskId);throw e;}
    }
    private Map<String,Object> generateCharged(String id,Long userId,String taskId)throws Exception{
        Map<String,Object> p=project(id,userId);List<Map<String,Object>> sections=outline(id);List<Map<String,Object>> allSlides=slides(id);List<Map<String,Object>> assets=jdbc.queryForList("SELECT * FROM ppt_asset WHERE project_id=?",id);Map<String,Path> assetPaths=new LinkedHashMap<>();for(Map<String,Object>a:assets)assetPaths.put(string(a.get("id")),Path.of(string(a.get("file_path"))));List<PptxGenerator.SectionSpec> specs=new ArrayList<>();
        for(Map<String,Object> section:sections){String sid=string(section.get("id"));List<PptxGenerator.SlideSpec> ss=new ArrayList<>();for(Map<String,Object>s:allSlides)if(sid.equals(string(s.get("section_id")))){List<String> ids=stringList(readJson(string(s.get("asset_ids_json"))));Path asset=ids.isEmpty()?null:assetPaths.get(ids.get(0));ss.add(new PptxGenerator.SlideSpec(string(s.get("title")),stringList(readJson(string(s.get("body_boxes_json")))),string(s.get("speaker_notes")),asset,string(s.get("layout_type"))));}specs.add(new PptxGenerator.SectionSpec(sid,string(section.get("title")),ss));}
        Path dir=inside(root.resolve(userId.toString()).resolve(id).resolve("outputs"));String filename=safeFileName(string(p.get("topic")))+".pptx";Path output=inside(dir.resolve(filename));PptxGenerator.DeckSpec deck=new PptxGenerator.DeckSpec(string(p.get("topic")),englishTopic(p),string(p.get("presenter")),string(p.get("major")),string(p.get("advisor")),string(p.get("student_number")),specs,futureItems(p));PptxGenerator.TemplateProfile template=templates.selectedProfile(p);PptxGenerator.GenerationResult result=generator.generate(deck,output,template);
        jdbc.update("UPDATE ppt_project SET status='SUCCESS',current_stage='生成完成',progress=100,output_path=?,error_message=NULL,updated_at=? WHERE id=? AND user_id=?",output.toString(),LocalDateTime.now(),id,userId);jdbc.update("UPDATE ppt_generation_task SET status='SUCCESS',progress=100,current_stage='生成完成',updated_at=? WHERE id=?",LocalDateTime.now(),taskId);Map<String,Object> out=detail(id,userId);out.put("validationStatus",result.validationStatus());out.put("autoFixes",result.autoFixes());return out;
    }
    public Map<String,Object> progress(String id){Long userId=AuthContext.requireUserId();Map<String,Object> p=project(id,userId);List<Map<String,Object>> tasks=jdbc.queryForList("SELECT status,progress,current_stage,error_message,updated_at FROM ppt_generation_task WHERE project_id=? ORDER BY created_at DESC",id);p.put("task",tasks.isEmpty()?null:tasks.get(0));return p;}
    public FileSystemResource download(String id){Map<String,Object> p=project(id,AuthContext.requireUserId());Path path=requiredPath(p.get("output_path"));if(!Files.isRegularFile(path))throw new IllegalStateException("PPTX尚未生成");return new FileSystemResource(path);}
    public String downloadName(String id){Map<String,Object>p=project(id,AuthContext.requireUserId());return safeFileName(string(p.get("topic")))+".pptx";}

    private Map<String,Object> detail(String id,Long userId){Map<String,Object> p=project(id,userId);p.put("outline",outline(id));p.put("slides",slides(id));p.put("assets",jdbc.queryForList("SELECT id,source_type,source_page,caption,width,height FROM ppt_asset WHERE project_id=? ORDER BY source_page,id",id));return p;}
    private Map<String,Object> project(String id,Long userId){List<Map<String,Object>>rows=jdbc.queryForList("SELECT * FROM ppt_project WHERE id=? AND user_id=?",id,userId);if(rows.isEmpty())throw new IllegalArgumentException("PPT项目不存在或无权访问");return new LinkedHashMap<>(rows.get(0));}
    private List<Map<String,Object>> outline(String id){return jdbc.queryForList("SELECT * FROM ppt_outline WHERE project_id=? ORDER BY section_order",id);}
    private List<Map<String,Object>> slides(String id){return jdbc.queryForList("SELECT * FROM ppt_slide WHERE project_id=? ORDER BY slide_order",id);}
    private PptDocumentParser.ParsedDocument document(Map<String,Object> p){Map<String,Object>a=readAnalysis(p);return new PptDocumentParser.ParsedDocument(text(a,"documentTitle",string(p.get("topic"))),stringList(a.get("headings")),stringList(a.get("blocks")),List.of(),integer(a.get("tableCount"),0),integer(a.get("characterCount"),0));}
    private Map<String,Object> readAnalysis(Map<String,Object>p){Object v=readJson(string(p.get("analysis_json")));return v instanceof Map<?,?>m?mapper.convertValue(m,new TypeReference<>(){}):new LinkedHashMap<>();}
    private Object readJson(String json){try{return json==null||json.isBlank()?List.of():mapper.readValue(json,Object.class);}catch(Exception e){return List.of();}}
    private String json(Object value){try{return mapper.writeValueAsString(value);}catch(Exception e){throw new IllegalStateException("JSON处理失败",e);}}
    private List<String> summarize(String source){String clean=PptDocumentParser.clean(source).replaceFirst("^\\[第\\d+页]\\s*","");List<String>out=new ArrayList<>();for(String part:clean.split("[。！？；;.!?\\n]+")){part=part.trim();if(part.length()<2)continue;out.add(validator.compact(part,20));if(out.size()==4)break;}if(out.isEmpty())out.add("依据源文档提炼");return out;}
    private String firstPhrase(String source){List<String>s=summarize(source);return s.isEmpty()?"内容概览":s.get(0);}
    private String englishTopic(Map<String,Object>p){String e=string(p.get("english_topic"));if(!e.isBlank())return e;return "Academic Presentation: "+string(p.get("topic"));}
    private List<String> futureItems(Map<String,Object>p){String full=String.join(" ",stringList(readAnalysis(p).get("blocks")));List<String>out=new ArrayList<>();for(String part:full.split("[。！？；]"))if(part.contains("未来")||part.contains("展望")||part.contains("不足")||part.contains("优化")){out.add(validator.compact(part,20));if(out.size()==4)break;}return out;}
    private Path requiredPath(Object value){String s=string(value);if(s.isBlank())throw new IllegalStateException("请先上传并解析文档");return inside(Path.of(s));}
    private Path inside(Path p){Path n=p.toAbsolutePath().normalize();if(!n.startsWith(root))throw new IllegalArgumentException("非法文件路径");return n;}
    private String safeFileName(String n){String s=n==null?"":n.replaceAll("[\\\\/:*?\"<>|\\r\\n]","_").trim();return s.isBlank()?"未命名学术答辩":s;}
    private String extension(String n){int dot=n.lastIndexOf('.');return dot<0?"":n.substring(dot+1).toLowerCase(Locale.ROOT);}
    private String safeError(Exception e){String m=e.getMessage();return m==null?"生成失败":m.substring(0,Math.min(500,m.length()));}
    private String text(Map<String,Object>m,String k,String d){String v=string(m==null?null:m.get(k));return v.isBlank()?d:v;}
    private String string(Object v){return v==null?"":String.valueOf(v);}
    private int integer(Object v,int d){try{return v instanceof Number n?n.intValue():Integer.parseInt(string(v));}catch(Exception e){return d;}}
    private List<String> stringList(Object value){if(value instanceof List<?>l)return l.stream().map(this::string).filter(x->!x.isBlank()).toList();return List.of();}
}
