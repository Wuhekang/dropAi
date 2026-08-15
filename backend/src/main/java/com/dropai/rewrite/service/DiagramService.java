package com.dropai.rewrite.service;

import com.dropai.rewrite.auth.AuthContext;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Files;
import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.Map;

@Service
public class DiagramService {
    private static final int MAX_DSL = 100_000;
    private final ObjectMapper objectMapper;
    private final MatrixDesignService matrix;
    private final JdbcTemplate jdbc;
    private final String python;
    private final String worker;

    public DiagramService(ObjectMapper objectMapper, MatrixDesignService matrix, JdbcTemplate jdbc,
                          @Value("${diagram.python:python}") String python,
                          @Value("${diagram.worker:diagram-worker/web_engine.py}") String worker) {
        this.objectMapper=objectMapper; this.matrix=matrix; this.jdbc=jdbc; this.python=python; this.worker=worker;
    }

    public JsonNode validate(String dsl) { return run("validate", dsl, null); }
    public JsonNode render(String dsl) { return run("render", dsl, null); }
    public ExportFile export(String dsl, String format) {
        JsonNode result=run("export",dsl,format);
        if (!result.path("success").asBoolean()) throw new IllegalArgumentException(result.path("error").asText("导出失败"));
        return new ExportFile(result.path("fileName").asText("diagram."+format), Base64.getDecoder().decode(result.path("data").asText()));
    }

    public JsonNode aiGenerate(String type, String description) {
        if (description == null || description.isBlank() || description.length()>6000) throw new IllegalArgumentException("绘图描述长度应为1-6000字符");
        String header=header(type);
        String response=matrix.generate(systemPrompt(header), "图形类型："+header+"\n用户需求："+description+"\n只返回JSON。");
        return parseAi(response);
    }

    public JsonNode aiReview(String dsl) {
        checkDsl(dsl); JsonNode local=validate(dsl);
        String response=matrix.generate(systemPrompt(local.path("header").asText("")), "检查下面DSL并返回summary、suggestions、revised_dsl。\n本地检查："+local.path("issues")+"\nDSL：\n"+dsl);
        return parseAi(response);
    }

    @Transactional
    public long save(Long id, String title, String dsl) {
        long userId=AuthContext.requireUserId(); JsonNode validation=validate(dsl);
        String type=validation.path("diagramType").asText("");
        if (id == null) {
            jdbc.update("INSERT INTO diagram_project(user_id,title,diagram_type,dsl_version,source_dsl,latest_valid_dsl,created_at,updated_at) VALUES(?,?,?,?,?,?,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)",userId,cleanTitle(title),type,"1.6",dsl,validation.path("valid").asBoolean()?dsl:null);
            return jdbc.queryForObject("SELECT MAX(id) FROM diagram_project WHERE user_id=?",Long.class,userId);
        }
        int changed=jdbc.update("UPDATE diagram_project SET title=?,diagram_type=?,source_dsl=?,latest_valid_dsl=?,updated_at=CURRENT_TIMESTAMP WHERE id=? AND user_id=?",cleanTitle(title),type,dsl,validation.path("valid").asBoolean()?dsl:null,id,userId);
        if(changed==0) throw new IllegalArgumentException("项目不存在或无权修改"); return id;
    }

    public List<Map<String,Object>> projects() { return jdbc.queryForList("SELECT id,title,diagram_type,dsl_version,source_dsl,updated_at FROM diagram_project WHERE user_id=? ORDER BY updated_at DESC",AuthContext.requireUserId()); }

    private JsonNode run(String command,String dsl,String format) {
        checkDsl(dsl);
        try {
            Path workerPath=resolveWorkerPath();
            Process process=new ProcessBuilder(python, workerPath.toString()).redirectErrorStream(false).start();
            Map<String,Object> payload=format==null?Map.of("command",command,"dsl",dsl):Map.of("command",command,"dsl",dsl,"format",format);
            process.getOutputStream().write(objectMapper.writeValueAsBytes(payload)); process.getOutputStream().close();
            boolean done=process.waitFor(Duration.ofSeconds(60).toMillis(),java.util.concurrent.TimeUnit.MILLISECONDS);
            if(!done){process.destroyForcibly();throw new IllegalStateException("绘图引擎执行超时");}
            ByteArrayOutputStream stderr=new ByteArrayOutputStream(); process.getErrorStream().transferTo(stderr);
            String output=new String(process.getInputStream().readAllBytes(),StandardCharsets.UTF_8).trim();
            if(process.exitValue()!=0||output.isBlank()) throw new IllegalStateException("绘图引擎失败："+safe(stderr.toString(StandardCharsets.UTF_8)));
            JsonNode result=objectMapper.readTree(output); if(!result.path("success").asBoolean(true)&&result.has("error")) throw new IllegalArgumentException(result.path("error").asText()); return result;
        } catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new IllegalStateException("绘图任务已取消"); }
        catch (Exception e) { if(e instanceof RuntimeException runtime) throw runtime; throw new IllegalStateException("绘图引擎调用失败",e); }
    }

    private JsonNode parseAi(String response) {
        try { String s=response==null?"":response.trim().replaceFirst("^```(?:json)?","").replaceFirst("```$","").trim(); return objectMapper.readTree(s.substring(s.indexOf('{'),s.lastIndexOf('}')+1)); }
        catch(Exception e){throw new IllegalStateException("AI未返回有效JSON，请重试");}
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
        String base="你是ThesisDiagram DSL v1.6代码生成与审查器。只生成或修正"+header+" DSL；禁止输出坐标、SVG、PNG、VSDX、HTML或绘图代码。第一条有效行必须是正确头标记。输出严格JSON，不使用Markdown围栏，不覆盖用户明确业务步骤。";
        try {
            Path rules=Path.of("knowledge","thesis-diagram","v1.6");
            String common=Files.readString(rules.resolve("common.md"),StandardCharsets.UTF_8);
            String file=switch(header){case "@FunctionModule"->"function_module.md";case "@ERDiagram"->"er_diagram.md";case "@ArchitectureDiagram"->"architecture.md";case "@UseCaseDiagram"->"use_case.md";case "@BlockDiagram"->"block_diagram.md";case "@SequenceDiagram"->"sequence.md";default->"flowchart.md";};
            String specific=Files.readString(rules.resolve(file),StandardCharsets.UTF_8);
            return base+"\n"+common+"\n"+specific.substring(0,Math.min(5000,specific.length()));
        } catch(Exception ignored){return base;}
    }
    private static String header(String type){return switch(type==null?"":type){case "function_module"->"@FunctionModule";case "er_diagram"->"@ERDiagram";case "architecture"->"@ArchitectureDiagram";case "use_case"->"@UseCaseDiagram";case "block_diagram"->"@BlockDiagram";case "sequence"->"@SequenceDiagram";default->"@Flowchart";};}
    private static void checkDsl(String dsl){if(dsl==null||dsl.isBlank())throw new IllegalArgumentException("DSL不能为空");if(dsl.length()>MAX_DSL)throw new IllegalArgumentException("DSL不能超过100000字符");}
    private static String cleanTitle(String s){String x=s==null?"未命名图形":s.trim();return x.isBlank()?"未命名图形":x.substring(0,Math.min(120,x.length()));}
    private static String safe(String s){if(s==null)return "";String cleaned=s.replaceAll("(?i)(api[_-]?key|authorization)[^\\s]*","[REDACTED]");return cleaned.substring(0,Math.min(300,cleaned.length()));}
    public record ExportFile(String name,byte[] content){}
}
