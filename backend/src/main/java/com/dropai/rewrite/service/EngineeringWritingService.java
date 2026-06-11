package com.dropai.rewrite.service;

import com.dropai.rewrite.auth.AuthContext;
import com.dropai.rewrite.entity.DocumentJobRecord;
import com.dropai.rewrite.mapper.DocumentJobMapper;
import com.dropai.rewrite.vo.DesignAnalysisVO;
import com.dropai.rewrite.vo.DesignParameterVO;
import com.dropai.rewrite.vo.DocumentRewriteJobVO;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class EngineeringWritingService {
    private final MatrixDesignService matrixDesignService;
    private final DocumentJobMapper documentJobMapper;
    private final ObjectMapper objectMapper;

    public EngineeringWritingService(MatrixDesignService matrixDesignService, DocumentJobMapper documentJobMapper, ObjectMapper objectMapper) {
        this.matrixDesignService = matrixDesignService;
        this.documentJobMapper = documentJobMapper;
        this.objectMapper = objectMapper;
    }

    public DesignAnalysisVO analyze(String title, List<MultipartFile> files) {
        try {
            String sources = extractSources(files);
            String response = matrixDesignService.generate(
                    "你是机械设计需求分析工程师。严格输出用户要求的合法 JSON，不输出 Markdown 或额外文字。",
                    buildAnalysisPrompt(title, sources)
            );
            DesignAnalysisVO analysis = objectMapper.readValue(extractJson(response), DesignAnalysisVO.class);
            normalizeAnalysis(analysis);
            return analysis;
        } catch (Exception exception) {
            throw new IllegalStateException("设计参数分析失败：" + readable(exception), exception);
        }
    }

    public DocumentRewriteJobVO generate(String title, String outputType, String requirements, List<MultipartFile> files) {
        Long userId = AuthContext.requireUserId();
        String normalizedTitle = title == null || title.isBlank() ? "机械毕业设计" : title.trim();
        String typeName = typeName(outputType);
        DocumentJobRecord record = initialRecord(userId, normalizedTitle, outputType, typeName);
        documentJobMapper.insert(record);
        try {
            String sourceSummary = extractSources(files);
            String generated = matrixDesignService.generate(
                    "你是机械设计与本科毕业设计工程师。输出可直接写入 Word 的中文设计说明，不虚构数据、标准或参考文献。",
                    buildPrompt(normalizedTitle, typeName, requirements, sourceSummary)
            );
            byte[] output = buildDocx(normalizedTitle, typeName, generated);
            record.setStatus("SUCCESS");
            record.setMessage(typeName + "已生成，资料文件 " + files.size() + " 个");
            record.setOutputFile(output);
            record.setProcessedParagraphs(1);
            record.setRewrittenParagraphs(1);
            record.setUpdatedAt(LocalDateTime.now());
            documentJobMapper.updateById(record);
            return toVO(record);
        } catch (Exception exception) {
            record.setStatus("FAILED");
            record.setMessage("生成失败：" + readable(exception));
            record.setUpdatedAt(LocalDateTime.now());
            documentJobMapper.updateById(record);
            throw new IllegalStateException(record.getMessage(), exception);
        }
    }

    private DocumentJobRecord initialRecord(Long userId, String title, String outputType, String typeName) {
        DocumentJobRecord record = new DocumentJobRecord();
        record.setJobId(UUID.randomUUID().toString().replace("-", ""));
        record.setUserId(userId);
        record.setFileName(title + "-" + typeName + ".docx");
        record.setSourceFeature("DESIGN_GENERATION");
        record.setMode(outputType);
        record.setModeName(typeName);
        record.setPlatform("ENGINEERING");
        record.setPlatformName("设计生成");
        record.setStatus("RUNNING");
        record.setTotalParagraphs(1);
        record.setProcessedParagraphs(0);
        record.setRewrittenParagraphs(0);
        record.setMessage("正在解析资料并生成 " + typeName);
        record.setParagraphsJson("[]");
        record.setCreatedAt(LocalDateTime.now());
        record.setUpdatedAt(LocalDateTime.now());
        return record;
    }

    private String extractSources(List<MultipartFile> files) throws Exception {
        List<String> sections = new ArrayList<>();
        int remaining = 28000;
        for (MultipartFile file : files) {
            String name = file.getOriginalFilename() == null ? "未命名文件" : file.getOriginalFilename();
            String lower = name.toLowerCase();
            String text;
            if (lower.endsWith(".docx")) text = extractDocx(file.getBytes());
            else if (lower.endsWith(".txt") || lower.endsWith(".md")) text = new String(file.getBytes(), StandardCharsets.UTF_8);
            else if (lower.endsWith(".dxf") || lower.endsWith(".dwg")) text = "CAD 文件已上传，可作为结构与零件依据。";
            else if (lower.matches(".*\\.(png|jpg|jpeg|webp|bmp)$")) text = "结构或设计图片已上传，可作为插图与图注规划依据。";
            else text = "文件已上传，当前版本仅记录文件名称与类型。";
            text = text.length() > remaining ? text.substring(0, Math.max(0, remaining)) : text;
            remaining -= text.length();
            sections.add("【资料：" + name + "】\n" + text);
            if (remaining <= 0) break;
        }
        return String.join("\n\n", sections);
    }

    private String extractDocx(byte[] bytes) throws Exception {
        try (XWPFDocument document = new XWPFDocument(new ByteArrayInputStream(bytes))) {
            return document.getParagraphs().stream().map(XWPFParagraph::getText)
                    .filter(text -> text != null && !text.isBlank()).reduce("", (a, b) -> a + "\n" + b).trim();
        }
    }

    private String buildPrompt(String title, String typeName, String requirements, String sources) {
        return """
                你正在生成一份机械设计方案与本科毕业设计交付文档。只输出可直接写入 Word 的正文，不解释过程，不虚构参考文献、数据、标准编号或图片内容。

                题目：%s
                输出文档：%s
                用户补充要求：%s

                写作规则：
                1. 内容以用户上传的任务书、开题报告、模板和参考文献为唯一事实依据。缺少数据时明确写“待补充”，不得编造。
                2. 使用连续、科学、自然的中文段落，避免过多分点与模板化 AI 话术。
                3. 首先输出“设计输入参数表”和“关键设计参数表”，明确参数名称、符号、数值、单位、来源与是否需要校核。随后输出结构方案、零部件选型、关键计算、CAD 图纸清单和工程校核项。
                4. 参考文献只引用资料中真实出现的文献；没有真实文献时不要生成参考文献。
                5. 图片与 CAD 无法直接读取细节时，只规划插图位置和图注，不虚构结构参数。用户明确提供的参数可以用于计算与设计说明。
                6. 公式使用可复制的线性表达，并在需要编号处使用“式 3-1”形式。
                7. 输出类型为设计方案包时，必须包含设计目标、工况与约束、参数表、总体方案、零部件清单、关键计算、CAD 图纸清单、截图图注建议和待工程师校核项。论文初稿应包含摘要、关键词、绪论、方案设计、主要零件计算、零件选型、二维三维或分析章节、结论与展望、参考文献占位。

                上传资料：
                %s
                """.formatted(title, typeName, requirements == null ? "" : requirements.trim(), sources);
    }

    private String buildAnalysisPrompt(String title, String sources) {
        return """
                你是机械设计需求分析工程师。请根据上传的任务书、开题报告和设计资料，提取并推导用于方案级 CAD 总装图的参数。
                题目：%s

                只输出一个合法 JSON 对象，不要 Markdown 代码块，不要解释文字。结构必须严格如下：
                {
                  "designType":"设计类型",
                  "summary":"不超过120字的设计目标摘要",
                  "parameters":{
                    "length":{"value":1600,"unit":"mm","source":"任务书原文或工程建议","status":"EXPLICIT或INFERRED或RECOMMENDED","basis":"依据"},
                    "width":{"value":900,"unit":"mm","source":"...","status":"...","basis":"..."},
                    "height":{"value":850,"unit":"mm","source":"...","status":"...","basis":"..."},
                    "wheelbase":{"value":1100,"unit":"mm","source":"...","status":"...","basis":"..."},
                    "wheelDiameter":{"value":260,"unit":"mm","source":"...","status":"...","basis":"..."},
                    "load":{"value":350,"unit":"kg","source":"...","status":"...","basis":"..."},
                    "speed":{"value":1.2,"unit":"m/s","source":"...","status":"...","basis":"..."},
                    "safetyFactor":{"value":1.8,"unit":"","source":"...","status":"...","basis":"..."}
                  },
                  "assumptions":["用于形成初稿的假设"],
                  "confirmations":["生成加工图前必须确认的问题"]
                }

                规则：
                1. 资料明确给出的参数标记 EXPLICIT，并在 source 中写明资料名称或原文依据。
                2. 可由其他数据计算得到的参数标记 INFERRED，basis 必须写清推导关系。
                3. 资料缺失但 CAD 初稿必须使用的参数，给出保守合理的工程建议值并标记 RECOMMENDED，禁止伪装成任务书原值。
                4. 所有 value 必须为正数。总体尺寸、轴距、轮径统一换算为 mm，载荷统一为 kg，速度统一为 m/s。
                5. wheelbase 必须小于 length，wheelDiameter 必须小于 height。
                6. 不虚构标准编号、材料性能或参考文献。

                上传资料：
                %s
                """.formatted(title == null ? "" : title.trim(), sources);
    }

    private String extractJson(String response) {
        int start = response.indexOf('{');
        int end = response.lastIndexOf('}');
        if (start < 0 || end <= start) throw new IllegalStateException("模型未返回合法参数 JSON");
        return response.substring(start, end + 1);
    }

    private void normalizeAnalysis(DesignAnalysisVO analysis) {
        Map<String, Double> defaults = new LinkedHashMap<>();
        defaults.put("length", 1600d);
        defaults.put("width", 900d);
        defaults.put("height", 850d);
        defaults.put("wheelbase", 1100d);
        defaults.put("wheelDiameter", 260d);
        defaults.put("load", 350d);
        defaults.put("speed", 1.2d);
        defaults.put("safetyFactor", 1.8d);
        Map<String, DesignParameterVO> parameters = analysis.getParameters() == null ? new LinkedHashMap<>() : analysis.getParameters();
        defaults.forEach((key, value) -> {
            DesignParameterVO parameter = parameters.computeIfAbsent(key, ignored -> new DesignParameterVO());
            if (!Double.isFinite(parameter.getValue()) || parameter.getValue() <= 0) parameter.setValue(value);
            if (parameter.getStatus() == null || parameter.getStatus().isBlank()) parameter.setStatus("RECOMMENDED");
            if (parameter.getSource() == null || parameter.getSource().isBlank()) parameter.setSource("系统工程建议");
            if (parameter.getBasis() == null || parameter.getBasis().isBlank()) parameter.setBasis("任务书未明确，生成方案初稿所需");
        });
        DesignParameterVO length = parameters.get("length");
        DesignParameterVO height = parameters.get("height");
        DesignParameterVO wheelbase = parameters.get("wheelbase");
        DesignParameterVO wheelDiameter = parameters.get("wheelDiameter");
        if (wheelbase.getValue() >= length.getValue()) wheelbase.setValue(length.getValue() * 0.7);
        if (wheelDiameter.getValue() >= height.getValue()) wheelDiameter.setValue(height.getValue() * 0.3);
        analysis.setParameters(parameters);
        if (analysis.getAssumptions() == null) analysis.setAssumptions(List.of());
        if (analysis.getConfirmations() == null) analysis.setConfirmations(List.of());
    }

    private byte[] buildDocx(String title, String typeName, String generated) throws Exception {
        try (XWPFDocument document = new XWPFDocument(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            XWPFParagraph heading = document.createParagraph();
            heading.setAlignment(org.apache.poi.xwpf.usermodel.ParagraphAlignment.CENTER);
            XWPFRun headingRun = heading.createRun();
            headingRun.setBold(true);
            headingRun.setFontSize(20);
            headingRun.setText(title + " " + typeName);
            for (String block : generated.split("\\R+")) {
                String text = block.trim();
                if (text.isEmpty()) continue;
                XWPFParagraph paragraph = document.createParagraph();
                paragraph.setSpacingAfter(120);
                paragraph.setIndentationFirstLine(480);
                XWPFRun run = paragraph.createRun();
                run.setFontSize(12);
                run.setText(text);
            }
            document.write(output);
            return output.toByteArray();
        }
    }

    private String typeName(String type) {
        return switch (type == null ? "" : type.toUpperCase()) {
            case "TASK_BOOK" -> "任务书";
            case "PROPOSAL" -> "开题报告";
            case "MIDTERM" -> "中期检查";
            case "THESIS_DRAFT" -> "论文初稿";
            case "DESIGN_PACKAGE" -> "设计方案包";
            default -> "设计文档";
        };
    }

    private DocumentRewriteJobVO toVO(DocumentJobRecord record) {
        DocumentRewriteJobVO vo = new DocumentRewriteJobVO();
        vo.setJobId(record.getJobId());
        vo.setFileName(record.getFileName());
        vo.setSourceFeature(record.getSourceFeature());
        vo.setMode(record.getMode());
        vo.setModeName(record.getModeName());
        vo.setPlatformName(record.getPlatformName());
        vo.setStatus(record.getStatus());
        vo.setMessage(record.getMessage());
        vo.setCreatedAt(record.getCreatedAt());
        vo.setUpdatedAt(record.getUpdatedAt());
        vo.setDownloadUrl("/api/documents/" + record.getJobId() + "/download");
        return vo;
    }

    private String readable(Exception exception) {
        String message = exception.getMessage();
        return message == null || message.isBlank() ? exception.getClass().getSimpleName() : message;
    }
}
