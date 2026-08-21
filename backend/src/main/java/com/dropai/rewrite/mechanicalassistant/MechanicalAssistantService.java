package com.dropai.rewrite.mechanicalassistant;

import com.dropai.rewrite.modules.documentParser.DocumentParser;
import com.dropai.rewrite.service.ai.DoubaoMechanicalVisionService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class MechanicalAssistantService {
    private final MechanicalAiGateway textAi;
    private final DoubaoMechanicalVisionService visionAi;
    private final DocumentParser parser;
    private final ObjectMapper mapper;
    private final JdbcTemplate jdbc;
    private final Map<String, Map<String, Object>> projects = new ConcurrentHashMap<>();

    public MechanicalAssistantService(MechanicalAiGateway textAi, DoubaoMechanicalVisionService visionAi,
                                      DocumentParser parser, ObjectMapper mapper, JdbcTemplate jdbc) {
        this.textAi = textAi; this.visionAi = visionAi; this.parser = parser; this.mapper = mapper; this.jdbc = jdbc;
    }

    public Map<String, Object> analyze(Long userId, String projectName, String description, List<MultipartFile> files) throws Exception {
        if ((description == null || description.isBlank()) && (files == null || files.isEmpty())) throw new IllegalArgumentException("请填写需求或上传参考文件");
        if (!textAi.available()) throw new IllegalStateException("机械设计 AI 未配置，请检查桌面 .env 中的 DOUBAO_API_KEY");
        List<MultipartFile> safeFiles = files == null ? List.of() : files.stream().filter(f -> f != null && !f.isEmpty()).toList();
        List<String> fileNames = safeFiles.stream().map(f -> Objects.toString(f.getOriginalFilename(), "upload")).toList();
        List<Map<String, Object>> vision = new ArrayList<>();
        StringBuilder documentContext = new StringBuilder();
        for (MultipartFile file : safeFiles) {
            String name = Objects.toString(file.getOriginalFilename(), "").toLowerCase(Locale.ROOT);
            if (name.matches(".*\\.(png|jpg|jpeg|webp)$")) {
                var result = visionAi.analyze(file.getBytes(), name, "识别机械设备类型、可见部件、连接关系、动力传递路径、风险与不确定项。所有判断需区分图像证据和工程推断。");
                vision.add(mapper.convertValue(result.result(), new TypeReference<>() {}));
            } else if (name.endsWith(".pdf")) {
                var parsed = parser.parse(List.of(file), List.of("TASK_BOOK")).get(0);
                if (parsed.textReadable()) documentContext.append(parsed.text(), 0, Math.min(parsed.text().length(), 12000));
            }
        }
        Map<String, Object> input = new LinkedHashMap<>();
        input.put("projectName", blank(projectName) ? "未命名机械方案" : projectName.trim());
        input.put("requirement", Objects.toString(description, "")); input.put("files", fileNames);
        input.put("visionEvidence", vision); input.put("documentText", documentContext.toString());
        String instructions = """
                你是高级机械设计工程师。基于用户需求和视觉证据输出概念设计辅助结果，禁止声称已经生成真实CAD或完成有限元验证。
                必须只返回严格JSON，字段为：equipmentType(string), summary(string), components(array of {name,function,material}),
                workingPrinciple(array string), recommendations(array string), assumptions(array string), risks(array string),
                parameters(array of {name,value,basis}), solutions(array exactly 3 of {id,name,driveType,description,advantages(array),tradeoffs(array),conceptPrompt}),
                structureTree({name,children(array recursively with name,children)}), assemblySteps(array string)。内容使用中文，建议必须说明依据或待确认条件。
                """;
        String raw = textAi.generate(instructions, mapper.writeValueAsString(input));
        Map<String, Object> analysis = mapper.readValue(extractJson(raw), new TypeReference<>() {});
        String id = UUID.randomUUID().toString();
        Map<String, Object> project = new LinkedHashMap<>();
        project.put("id", id); project.put("userId", userId); project.put("projectName", input.get("projectName"));
        project.put("description", description); project.put("inputImages", fileNames); project.put("analysis", analysis);
        project.put("createdTime", LocalDateTime.now().toString()); project.put("provider", "MechanicalAIService/Doubao");
        project.put("futureInterfaces", List.of("FreeCAD", "STEP", "SolidWorks", "BOM")); projects.put(id, project);
        persist(project);
        return project;
    }

    public Map<String, Object> get(String id) {
        Map<String, Object> value = projects.get(id); if (value == null) throw new IllegalArgumentException("机械设计项目不存在"); return value;
    }

    public byte[] report(String id) throws Exception {
        Map<String, Object> project = get(id); Map<String, Object> a = cast(project.get("analysis"));
        try (XWPFDocument doc = new XWPFDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            title(doc, "DokiAI 机械设计辅助报告"); section(doc, "项目名称", project.get("projectName")); section(doc, "设计需求", project.get("description"));
            section(doc, "设备类型", a.get("equipmentType")); section(doc, "方案概述", a.get("summary"));
            list(doc, "主要组成", a.get("components")); list(doc, "工作原理", a.get("workingPrinciple")); list(doc, "参数建议", a.get("parameters"));
            list(doc, "装配步骤", a.get("assemblySteps")); list(doc, "设计建议", a.get("recommendations")); list(doc, "风险与待确认项", a.get("risks"));
            XWPFParagraph note = doc.createParagraph(); note.createRun().setText("说明：本报告为AI概念设计辅助资料，不替代详细计算、标准校核、工程签审或真实CAD模型。");
            doc.write(out); return out.toByteArray();
        }
    }

    private void persist(Map<String, Object> p) {
        try {
            jdbc.execute("CREATE TABLE IF NOT EXISTS mechanical_projects (id VARCHAR(64) PRIMARY KEY,user_id BIGINT,project_name VARCHAR(255),description TEXT,input_images TEXT,analysis_result TEXT,design_solution TEXT,structure_tree TEXT,report_file VARCHAR(500),created_time TIMESTAMP)");
            Map<String,Object> a=cast(p.get("analysis"));
            jdbc.update("INSERT INTO mechanical_projects(id,user_id,project_name,description,input_images,analysis_result,design_solution,structure_tree,created_time) VALUES(?,?,?,?,?,?,?,?,CURRENT_TIMESTAMP)",p.get("id"),p.get("userId"),p.get("projectName"),p.get("description"),json(p.get("inputImages")),json(a),json(a.get("solutions")),json(a.get("structureTree")));
        } catch (Exception e) { throw new IllegalStateException("机械项目持久化失败：" + e.getMessage(), e); }
    }
    private String json(Object value) { try { return mapper.writeValueAsString(value); } catch(Exception e) { throw new IllegalStateException(e); } }
    private Map<String,Object> cast(Object v){ return mapper.convertValue(v,new TypeReference<>(){}); }
    private String extractJson(String v){String s=v==null?"":v.trim().replaceFirst("^```(?:json)?\\s*","").replaceFirst("\\s*```$","");int a=s.indexOf('{'),b=s.lastIndexOf('}');if(a<0||b<=a)throw new IllegalStateException("AI未返回有效机械设计JSON");return s.substring(a,b+1);}
    private boolean blank(String s){return s==null||s.isBlank();}
    private void title(XWPFDocument d,String s){XWPFParagraph p=d.createParagraph();p.setStyle("Title");p.createRun().setText(s);}
    private void section(XWPFDocument d,String h,Object v){XWPFParagraph p=d.createParagraph();p.setStyle("Heading1");p.createRun().setText(h);d.createParagraph().createRun().setText(Objects.toString(v,""));}
    private void list(XWPFDocument d,String h,Object v){section(d,h,"");if(v instanceof Collection<?> c)for(Object x:c)d.createParagraph().createRun().setText("• "+(x instanceof Map<?,?>?json(x):x));}
}
