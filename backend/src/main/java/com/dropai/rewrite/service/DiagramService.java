package com.dropai.rewrite.service;

import com.dropai.rewrite.auth.AuthContext;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.core.PreparedStatementCreator;
import java.sql.Statement;
import javax.xml.parsers.DocumentBuilderFactory;

@Service
public class DiagramService {
    private static final Logger log=LoggerFactory.getLogger(DiagramService.class);
    private static final int MAX_DSL = 100_000;
    private final ObjectMapper objectMapper;
    private final MatrixDesignService matrix;
    private final JdbcTemplate jdbc;
    private final String python;
    private final String worker;
    private final PointService points;
    private final DiagramPreviewBillingService billing;
    private final Path artifactRoot;
    private static final String RENDERER_VERSION="thesis-diagram-v1.6-export-2";

    public DiagramService(ObjectMapper objectMapper, MatrixDesignService matrix, JdbcTemplate jdbc,
                          @Value("${diagram.python:python}") String python,
                          @Value("${diagram.worker:diagram-worker/web_engine.py}") String worker,
                          @Value("${diagram.artifact-root:data/diagram-previews}") String artifactRoot,
                          PointService points, DiagramPreviewBillingService billing) {
        this.objectMapper=objectMapper; this.matrix=matrix; this.jdbc=jdbc; this.python=python; this.worker=worker;
        this.points=points;this.billing=billing;this.artifactRoot=Path.of(artifactRoot).toAbsolutePath().normalize();
    }

    public JsonNode validate(String dsl) { return run("validate", dsl, null); }
    public JsonNode renderLegacy(String dsl) {
        log.info("[diagram] request received");
        log.info("[diagram] source normalized");
        JsonNode result=run("render", dsl, null);
        log.info("[diagram] header detected: {}",result.path("diagramType").asText());
        log.info("[diagram] parser selected: {}",result.path("displayName").asText());
        log.info("[diagram] parse, validation, layout and svg render completed in {}ms",result.path("durationMs").asLong());
        log.info("[diagram] response sent"); return result;
    }

    public Map<String,Object> render(Long requestedProjectId,String dsl) {
        long userId=AuthContext.requireUserId(); JsonNode validation=validate(dsl);
        if(!validation.path("valid").asBoolean())return Map.of("ok",false,"code","DSL_INVALID","validation",validation);
        long projectId=ensureProject(requestedProjectId,validation.path("title").asText("未命名图形"),dsl,userId);
        String type=validation.path("diagramType").asText();String normalized=normalizeDsl(dsl);String hash=renderHash(userId,projectId,type,normalized);
        Map<String,Object> existing=billing.success(userId,projectId,hash);if(existing!=null)return previewResponse(existing,false,true,points.currentPoints(userId));
        try{points.ensureEnoughCustom(userId,10);}catch(PointsNotEnoughException e){return Map.of("ok",false,"code","INSUFFICIENT_POINTS","requiredPoints",10,"balance",points.currentPoints(userId),"projectId",projectId,"message","当前积分不足，生成图形需要10积分。您仍可继续编辑代码、检查格式或使用绘图助手。");}
        String taskId=billing.createTask(userId,projectId,type,hash,RENDERER_VERSION);if(taskId==null)return Map.of("ok",false,"code","RENDER_IN_PROGRESS","projectId",projectId,"renderHash",hash,"message","相同代码正在生成，请稍后重试");
        Path temp=artifactRoot.resolve(".tmp").resolve(taskId),finalDir=null;DiagramPreviewBillingService.Finalized finalized=null;
        try{
            Files.createDirectories(temp);JsonNode rendered=renderLegacy(dsl);String svg=rendered.path("svg").asText();validateSvg(svg);billing.rendered(taskId);
            String previewId=UUID.randomUUID().toString().replace("-","");finalDir=artifactRoot.resolve(String.valueOf(userId)).resolve(String.valueOf(projectId)).resolve(previewId).normalize();
            if(!finalDir.startsWith(artifactRoot))throw new IllegalStateException("非法预览目录");
            Files.writeString(temp.resolve("diagram.svg"),svg,StandardCharsets.UTF_8);
            Files.writeString(temp.resolve("diagram.json"),objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(Map.of("dsl",dsl,"diagramType",type,"structure",rendered.path("structure"),"layout",rendered.path("layout"))),StandardCharsets.UTF_8);
            List<DiagramPreviewBillingService.ArtifactDraft> artifacts=new ArrayList<>();
            artifacts.add(new DiagramPreviewBillingService.ArtifactDraft("svg","READY",finalDir.resolve("diagram.svg").toString(),Files.size(temp.resolve("diagram.svg")),null));
            artifacts.add(new DiagramPreviewBillingService.ArtifactDraft("json","READY",finalDir.resolve("diagram.json").toString(),Files.size(temp.resolve("diagram.json")),null));
            try{ExportFile png=export(dsl,"png");Files.write(temp.resolve("diagram.png"),png.content());artifacts.add(new DiagramPreviewBillingService.ArtifactDraft("png","READY",finalDir.resolve("diagram.png").toString(),png.content().length,null));}
            catch(Exception pngError){artifacts.add(new DiagramPreviewBillingService.ArtifactDraft("png","UNAVAILABLE",null,0,"PNG导出失败："+pngError.getMessage()));}
            try{ExportFile vsdx=export(dsl,"vsdx");Files.write(temp.resolve("diagram.vsdx"),vsdx.content());artifacts.add(new DiagramPreviewBillingService.ArtifactDraft("vsdx","READY",finalDir.resolve("diagram.vsdx").toString(),vsdx.content().length,null));}
            catch(Exception vsdxError){artifacts.add(new DiagramPreviewBillingService.ArtifactDraft("vsdx","UNAVAILABLE",null,0,"VSDX导出失败："+vsdxError.getMessage()));}
            finalized=billing.finalizeRendered(taskId,previewId,userId,projectId,type,hash,RENDERER_VERSION,normalized,svg,artifacts);
            if(!finalized.charged()){deleteTree(temp);Map<String,Object> reused=billing.ownedPreview(finalized.previewId(),userId);return previewResponse(reused,false,true,finalized.balance());}
            Files.createDirectories(finalDir.getParent());
            try{Files.move(temp,finalDir,StandardCopyOption.ATOMIC_MOVE);}
            catch(java.nio.file.AtomicMoveNotSupportedException unsupported){Files.move(temp,finalDir);}
            billing.published(taskId,finalized.previewId());
            Map<String,Object> preview=billing.ownedPreview(finalized.previewId(),userId);return previewResponse(preview,true,false,finalized.balance());
        }catch(Exception error){
            if(finalized!=null&&finalized.charged())billing.refundPublishFailure(taskId,finalized.previewId(),userId,error.getMessage());else billing.failed(taskId,error.getMessage());
            deleteTree(temp);throw error instanceof RuntimeException r?r:new IllegalStateException("预览生成失败",error);
        }
    }

    public Map<String,Object> preview(String previewId){long userId=AuthContext.requireUserId();Map<String,Object> preview=billing.ownedPreview(previewId,userId);if(preview==null||!"SUCCESS".equals(String.valueOf(preview.get("status"))))throw new IllegalArgumentException("预览不存在或无权访问");Map<String,Object> out=previewResponse(preview,false,true,points.currentPoints(userId));out.put("dsl",preview.get("normalized_dsl"));return out;}

    public ExportFile download(String previewId,String format){
        long userId=AuthContext.requireUserId();Map<String,Object> preview=billing.ownedPreview(previewId,userId);
        if(preview==null)throw new IllegalArgumentException("预览不存在或无权访问");if(!"SUCCESS".equals(String.valueOf(preview.get("status")))||((Number)preview.get("charged_points")).intValue()!=10)throw new IllegalStateException("该预览未完成计费，禁止下载");
        String kind=format==null?"":format.toLowerCase();Map<String,Object> artifact=billing.artifact(previewId,kind);if(artifact==null||!"READY".equals(String.valueOf(artifact.get("status"))))throw new IllegalStateException(artifact==null?"产物不存在":String.valueOf(artifact.get("failure_reason")));
        try{Path path=Path.of(String.valueOf(artifact.get("file_path"))).toAbsolutePath().normalize();if(!path.startsWith(artifactRoot)||!Files.isRegularFile(path))throw new IllegalStateException("产物文件不存在");return new ExportFile("diagram."+kind,Files.readAllBytes(path));}catch(java.io.IOException e){throw new IllegalStateException("读取产物失败",e);}
    }
    public Map<String,Object> health() {
        Path path=resolveWorkerPath();
        return Map.of("ok",Files.isRegularFile(path),"engine","ThesisDiagram","version","1.6","supportedHeaders",List.of("@FunctionModule","@Flowchart","@ERDiagram","@ArchitectureDiagram","@UseCaseDiagram","@BlockDiagram","@SequenceDiagram"));
    }
    public ExportFile export(String dsl, String format) {
        JsonNode result=run("export",dsl,format);
        if (!result.path("success").asBoolean()) throw new IllegalArgumentException(result.path("error").asText("导出失败"));
        return new ExportFile(result.path("fileName").asText("diagram."+format), Base64.getDecoder().decode(result.path("data").asText()));
    }

    public JsonNode aiGenerate(String sourceDsl, String description) {
        if (description == null || description.isBlank() || description.length()>6000) throw new IllegalArgumentException("绘图描述长度应为1-6000字符");
        String header=requireHeader(sourceDsl);
        String response=matrix.generate(systemPrompt(header), "图形类型："+header+"\n用户需求："+description+"\n只返回JSON。");
        return validateAiResult(parseAi(response),header,"dsl");
    }

    public JsonNode aiReview(String dsl) {
        checkDsl(dsl); JsonNode local=validate(dsl);
        String header=requireHeader(dsl);
        String response=matrix.generate(systemPrompt(header), "当前头标记："+header+"\n本地检查错误："+local.path("issues")+"\n用户原始代码：\n"+dsl+"\n返回summary、suggestions、revised_dsl。不得修改头标记。");
        return validateAiResult(parseAi(response),header,"revised_dsl");
    }

    @Transactional
    public long save(Long id, String title, String dsl) {
        long userId=AuthContext.requireUserId(); JsonNode validation=validate(dsl);
        String type=validation.path("diagramType").asText("");
        if (id == null) {
            GeneratedKeyHolder keys=new GeneratedKeyHolder();
            jdbc.update(connection->{var ps=connection.prepareStatement("INSERT INTO diagram_project(user_id,title,diagram_type,dsl_version,source_dsl,latest_valid_dsl,created_at,updated_at) VALUES(?,?,?,?,?,?,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)",Statement.RETURN_GENERATED_KEYS);ps.setLong(1,userId);ps.setString(2,cleanTitle(title));ps.setString(3,type);ps.setString(4,"1.6");ps.setString(5,dsl);ps.setString(6,validation.path("valid").asBoolean()?dsl:null);return ps;},keys);
            if(keys.getKey()==null)throw new IllegalStateException("创建智能画图项目失败");return keys.getKey().longValue();
        }
        int changed=jdbc.update("UPDATE diagram_project SET title=?,diagram_type=?,source_dsl=?,latest_valid_dsl=?,updated_at=CURRENT_TIMESTAMP WHERE id=? AND user_id=?",cleanTitle(title),type,dsl,validation.path("valid").asBoolean()?dsl:null,id,userId);
        if(changed==0) throw new IllegalArgumentException("项目不存在或无权修改"); return id;
    }

    public List<Map<String,Object>> projects() { return jdbc.queryForList("SELECT id,title,diagram_type,dsl_version,source_dsl,updated_at FROM diagram_project WHERE user_id=? ORDER BY updated_at DESC",AuthContext.requireUserId()); }

    private JsonNode run(String command,String dsl,String format) {
        checkDsl(dsl);
        String traceId=UUID.randomUUID().toString().replace("-","").substring(0,12);
        try {
            Path workerPath=resolveWorkerPath();
            ProcessBuilder builder=new ProcessBuilder(python, "-X", "utf8", workerPath.toString()).redirectErrorStream(false);
            builder.environment().put("PYTHONUTF8", "1");
            builder.environment().put("PYTHONIOENCODING", "utf-8");
            Process process=builder.start();
            Map<String,Object> payload=format==null?Map.of("command",command,"dsl",dsl,"traceId",traceId):Map.of("command",command,"dsl",dsl,"format",format,"traceId",traceId);
            process.getOutputStream().write(objectMapper.writeValueAsBytes(payload)); process.getOutputStream().close();
            CompletableFuture<byte[]> stdout=CompletableFuture.supplyAsync(()->readStream(process.getInputStream()));
            CompletableFuture<byte[]> stderr=CompletableFuture.supplyAsync(()->readStream(process.getErrorStream()));
            boolean done=process.waitFor(5,TimeUnit.SECONDS);
            if(!done){process.destroyForcibly();process.waitFor(1,TimeUnit.SECONDS);String trace=safe(new String(stderr.get(1,TimeUnit.SECONDS),StandardCharsets.UTF_8));log.error("[diagram-render][{}] worker timeout pid={} trace={}",traceId,process.pid(),trace);throw new IllegalStateException("绘图引擎执行超过5秒，已终止。traceId="+traceId);}
            String trace=safe(new String(stderr.get(1,TimeUnit.SECONDS),StandardCharsets.UTF_8));
            for(String line:trace.split("\\R")) if(!line.isBlank()) log.info("{}",line);
            String output=new String(stdout.get(1,TimeUnit.SECONDS),StandardCharsets.UTF_8).trim();
            if(process.exitValue()!=0||output.isBlank()) throw new IllegalStateException("绘图引擎失败："+trace);
            JsonNode result=objectMapper.readTree(output); if(!result.path("success").asBoolean(true)&&result.has("error")) throw new IllegalArgumentException(result.path("error").asText()); return result;
        } catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new IllegalStateException("绘图任务已取消"); }
        catch (Exception e) { if(e instanceof RuntimeException runtime) throw runtime; throw new IllegalStateException("绘图引擎调用失败",e); }
    }
    private static byte[] readStream(java.io.InputStream stream) {
        try{return stream.readAllBytes();}catch(java.io.IOException e){throw new java.io.UncheckedIOException(e);}
    }

    private JsonNode parseAi(String response) {
        try { String s=response==null?"":response.trim().replaceFirst("^```(?:json)?","").replaceFirst("```$","").trim(); return objectMapper.readTree(s.substring(s.indexOf('{'),s.lastIndexOf('}')+1)); }
        catch(Exception e){throw new IllegalStateException("AI未返回有效JSON，请重试");}
    }
    private String requireHeader(String dsl) {
        JsonNode local=validate(dsl); String header=local.path("header").asText("");
        if(header.isBlank()) throw new IllegalArgumentException("第一条有效语句必须是受支持的@图形头标记");
        return header;
    }
    private JsonNode validateAiResult(JsonNode result,String expectedHeader,String field) {
        String candidate=result.path(field).asText("").trim();
        if(candidate.isBlank() && "revised_dsl".equals(field)) candidate=result.path("revisedDsl").asText("").trim();
        candidate=candidate.replaceFirst("^```(?:[A-Za-z]+)?\\s*","").replaceFirst("\\s*```$","").trim();
        if(candidate.contains("{") || candidate.matches("(?s).*(?:^|\\n)\\s*(?:System|Module|Function)\\b.*")) throw new IllegalArgumentException("AI_DSL_INVALID：AI返回内容不符合 ThesisDiagram DSL v1.6，已拒绝应用");
        JsonNode validation=validate(candidate);
        if(!expectedHeader.equals(validation.path("header").asText()) || !validation.path("valid").asBoolean()) throw new IllegalArgumentException("AI_DSL_INVALID：AI返回内容不符合 ThesisDiagram DSL v1.6，已拒绝应用");
        ObjectNode output=result.deepCopy(); output.put(field,candidate); output.set("validation",validation); return output;
    }
    private Path resolveWorkerPath() {
        Path configured=Path.of(worker);
        if(configured.isAbsolute()) {
            if(Files.isRegularFile(configured)) return configured.normalize();
            throw new IllegalStateException("绘图引擎文件不存在："+configured.normalize());
        }
        Path current=Path.of("").toAbsolutePath().normalize();
        Path direct=current.resolve(configured).normalize();
        if(Files.isRegularFile(direct)) return direct;
        Path parent=current.getParent();
        if(parent!=null) {
            Path fromParent=parent.resolve(configured).normalize();
            if(Files.isRegularFile(fromParent)) return fromParent;
        }
        throw new IllegalStateException("绘图引擎文件不存在，请设置 DIAGRAM_WORKER。已检查："+direct);
    }
    private String systemPrompt(String header){
        String whitelist="@FunctionModule".equals(header)?"允许的关键字白名单：@FunctionModule、系统：、模块：、功能：。正确示例：\\n@FunctionModule\\n系统：系统名称\\n\\n模块：模块名称\\n功能：功能1，功能2，功能3":"只允许当前图形规则包中定义的关键字。";
        String base="你是 ThesisDiagram DSL v1.6 的代码助手。你只能使用提供给你的当前图形类型语法。第一行 "+header+" 决定图形类型，禁止修改或删除。禁止创造规范中不存在的语法。禁止输出 System、Module、Function、大括号、PlantUML、Mermaid、坐标、SVG、PNG、VSDX、HTML或绘图代码。DSL字段只能包含能被本地解析器直接解析的 ThesisDiagram DSL，不使用Markdown代码围栏。"+whitelist;
        try {
            Path rules=Path.of("knowledge","thesis-diagram","v1.6");
            String common=Files.readString(rules.resolve("common.md"),StandardCharsets.UTF_8);
            String file=switch(header){case "@FunctionModule"->"function_module.md";case "@ERDiagram"->"er_diagram.md";case "@ArchitectureDiagram"->"architecture.md";case "@UseCaseDiagram"->"use_case.md";case "@BlockDiagram"->"block_diagram.md";case "@SequenceDiagram"->"sequence.md";default->"flowchart.md";};
            String specific=Files.readString(rules.resolve(file),StandardCharsets.UTF_8);
            return base+"\n"+common+"\n"+specific.substring(0,Math.min(5000,specific.length()));
        } catch(Exception ignored){return base;}
    }
    private static void checkDsl(String dsl){if(dsl==null||dsl.isBlank())throw new IllegalArgumentException("DSL不能为空");if(dsl.length()>MAX_DSL)throw new IllegalArgumentException("DSL不能超过100000字符");}
    private long ensureProject(Long projectId,String title,String dsl,long userId){
        if(projectId==null)return save(null,title,dsl);
        Integer owned=jdbc.queryForObject("SELECT COUNT(*) FROM diagram_project WHERE id=? AND user_id=?",Integer.class,projectId,userId);if(owned==null||owned==0)throw new IllegalArgumentException("项目不存在或无权访问");
        save(projectId,title,dsl);return projectId;
    }
    static String normalizeDsl(String dsl){String value=dsl==null?"":dsl.replace("\uFEFF","").replace("\r\n","\n").replace('\r','\n');String[] lines=value.split("\n",-1);StringBuilder out=new StringBuilder();int end=lines.length;while(end>0&&lines[end-1].stripTrailing().isEmpty())end--;for(int i=0;i<end;i++){if(i>0)out.append('\n');out.append(lines[i].stripTrailing());}return out.toString();}
    static String renderHash(long userId,long projectId,String type,String normalized){
        try{MessageDigest digest=MessageDigest.getInstance("SHA-256");String input=userId+"\n"+projectId+"\n"+type+"\n"+normalized+"\n"+RENDERER_VERSION;return java.util.HexFormat.of().formatHex(digest.digest(input.getBytes(StandardCharsets.UTF_8)));}catch(Exception e){throw new IllegalStateException(e);}
    }
    private void validateSvg(String svg){
        if(svg==null||svg.isBlank()||!svg.contains("<svg"))throw new IllegalStateException("绘图引擎没有返回有效SVG");
        try{DocumentBuilderFactory f=DocumentBuilderFactory.newInstance();f.setFeature("http://apache.org/xml/features/disallow-doctype-decl",true);f.setExpandEntityReferences(false);var doc=f.newDocumentBuilder().parse(new ByteArrayInputStream(svg.getBytes(StandardCharsets.UTF_8)));var root=doc.getDocumentElement();if(!"svg".equalsIgnoreCase(root.getLocalName()==null?root.getNodeName():root.getLocalName()))throw new IllegalStateException("SVG根节点无效");boolean size=!root.getAttribute("viewBox").isBlank()||(!root.getAttribute("width").isBlank()&&!root.getAttribute("height").isBlank());if(!size)throw new IllegalStateException("SVG缺少有效viewBox或宽高");int shapes=0;for(String tag:List.of("rect","path","line","polyline","polygon","ellipse","circle","text"))shapes+=doc.getElementsByTagName(tag).getLength();if(shapes<2)throw new IllegalStateException("SVG没有有效图形节点");}catch(RuntimeException e){throw e;}catch(Exception e){throw new IllegalStateException("SVG解析失败",e);}
    }
    private Map<String,Object> previewResponse(Map<String,Object> preview,boolean charged,boolean reused,int balance){
        String id=String.valueOf(preview.get("id"));Map<String,Object> out=new LinkedHashMap<>();out.put("ok",true);out.put("charged",charged);out.put("chargedPoints",charged?10:0);out.put("balance",balance);out.put("reused",reused);out.put("renderHash",preview.get("render_hash"));out.put("previewId",id);out.put("projectId",preview.get("project_id"));out.put("diagramType",preview.get("diagram_type"));out.put("svg",preview.get("svg_content"));out.put("downloadable",true);out.put("artifacts",billing.artifactStates(id));return out;
    }
    private static void deleteTree(Path path){if(path==null||!Files.exists(path))return;try(var stream=Files.walk(path)){stream.sorted(java.util.Comparator.reverseOrder()).forEach(p->{try{Files.deleteIfExists(p);}catch(Exception ignored){}});}catch(Exception ignored){}}
    private static String cleanTitle(String s){String x=s==null?"未命名图形":s.trim();return x.isBlank()?"未命名图形":x.substring(0,Math.min(120,x.length()));}
    private static String safe(String s){if(s==null)return "";String cleaned=s.replaceAll("(?i)(api[_-]?key|authorization)[^\\s]*","[REDACTED]");return cleaned.substring(0,Math.min(2000,cleaned.length()));}
    public record ExportFile(String name,byte[] content){}
}
