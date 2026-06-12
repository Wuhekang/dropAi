package com.dropai.rewrite.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.dropai.rewrite.auth.AuthContext;
import com.dropai.rewrite.entity.DocumentJobRecord;
import com.dropai.rewrite.mapper.DocumentJobMapper;
import com.dropai.rewrite.vo.DesignWorkflowStageVO;
import com.dropai.rewrite.vo.DesignWorkflowVO;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PreDestroy;
import org.apache.poi.xwpf.usermodel.ParagraphAlignment;
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
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Service
public class DesignWorkflowService {
    private final MatrixDesignService matrixDesignService;
    private final ParametricDxfService dxfService;
    private final DocumentJobMapper documentJobMapper;
    private final ObjectMapper objectMapper;
    private final ExecutorService executor = Executors.newFixedThreadPool(6);
    private final Map<String, DesignWorkflowVO> workflows = new ConcurrentHashMap<>();

    public DesignWorkflowService(MatrixDesignService matrixDesignService, ParametricDxfService dxfService,
                                 DocumentJobMapper documentJobMapper, ObjectMapper objectMapper) {
        this.matrixDesignService = matrixDesignService;
        this.dxfService = dxfService;
        this.documentJobMapper = documentJobMapper;
        this.objectMapper = objectMapper;
    }

    public DesignWorkflowVO submit(String title, String outputType, String requirements, List<MultipartFile> files,
                                   double length, double width, double height, double wheelbase, double wheelDiameter) {
        Long userId = AuthContext.requireUserId();
        String normalizedTitle = title == null || title.isBlank() ? "机械设计" : title.trim();
        String sourceSummary = extractSources(files);
        DesignWorkflowVO workflow = newWorkflow();
        workflows.put(workflow.getWorkflowId(), workflow);
        persistWorkflow(workflow, userId, normalizedTitle);

        updateStage(workflow, "SOURCES", "SUCCESS", "已提取 " + files.size() + " 个资料文件", null);
        CompletableFuture<Void> cad = CompletableFuture.runAsync(
                () -> generateCad(workflow, userId, normalizedTitle, length, width, height, wheelbase, wheelDiameter), executor);
        CompletableFuture<Void> document = CompletableFuture.runAsync(
                () -> generateDocument(workflow, userId, normalizedTitle, outputType, requirements, sourceSummary), executor);
        CompletableFuture.allOf(cad, document).whenCompleteAsync((ignored, error) -> finish(workflow, userId, normalizedTitle), executor);
        return workflow;
    }

    public DesignWorkflowVO get(String workflowId) {
        Long userId = AuthContext.requireUserId();
        DesignWorkflowVO cached = workflows.get(workflowId);
        if (cached != null) return cached;
        DocumentJobRecord record = documentJobMapper.selectOne(new LambdaQueryWrapper<DocumentJobRecord>()
                .eq(DocumentJobRecord::getJobId, workflowId).eq(DocumentJobRecord::getUserId, userId));
        if (record == null || !"DESIGN_WORKFLOW".equals(record.getSourceFeature())) {
            throw new IllegalArgumentException("设计工作流不存在");
        }
        return fromRecord(record);
    }

    private void generateCad(DesignWorkflowVO workflow, Long userId, String title, double length, double width,
                             double height, double wheelbase, double wheelDiameter) {
        updateStage(workflow, "CAD", "RUNNING", "正在生成标准 DXF 图元", null);
        try {
            byte[] bytes = dxfService.generate(length, width, height, wheelbase, wheelDiameter);
            DocumentJobRecord artifact = artifact(userId, title + "-总装方案图.dxf", "DESIGN_CAD", "CAD 图纸");
            artifact.setOutputFile(bytes);
            artifact.setStatus("SUCCESS");
            artifact.setMessage("参数化 DXF 已生成，可预览和下载");
            artifact.setProcessedParagraphs(1);
            artifact.setRewrittenParagraphs(1);
            artifact.setUpdatedAt(LocalDateTime.now());
            documentJobMapper.insert(artifact);
            updateStage(workflow, "CAD", "SUCCESS", artifact.getMessage(), artifact);
        } catch (Exception exception) {
            updateStage(workflow, "CAD", "FAILED", readable(exception), null);
        }
    }

    private void generateDocument(DesignWorkflowVO workflow, Long userId, String title, String outputType,
                                  String requirements, String sources) {
        List<SectionSpec> specs = List.of(
                new SectionSpec("PLAN", "总体方案与参数", "输出设计目标、工况约束、设计输入参数表、总体结构方案和 CAD 图纸清单。"),
                new SectionSpec("CALCULATION", "计算与零部件选型", "输出关键工程计算、零部件选型依据、安全校核和待工程师确认项。"),
                new SectionSpec("DRAFT", "论文初稿章节", "输出摘要、关键词、绪论、方案设计、主要零件计算、结论与展望。")
        );
        List<CompletableFuture<SectionResult>> futures = specs.stream()
                .map(spec -> CompletableFuture.supplyAsync(() -> generateSection(workflow, spec, title, outputType, requirements, sources), executor))
                .toList();
        try {
            List<SectionResult> results = futures.stream().map(CompletableFuture::join).toList();
            List<SectionResult> successful = results.stream().filter(SectionResult::success).toList();
            if (successful.size() != specs.size()) {
                throw new IllegalStateException("论文章节未完整生成，已禁止导出半成品Word；请稍后重试或更换可用API Key");
            }
            if (successful.isEmpty()) throw new IllegalStateException("所有文档章节均生成失败");
            byte[] docx = buildDocx(title, successful);
            DocumentJobRecord artifact = artifact(userId, title + "-设计说明初稿.docx", "DESIGN_DOCUMENT", "设计说明初稿");
            artifact.setOutputFile(docx);
            artifact.setStatus("SUCCESS");
            artifact.setMessage("已汇总 " + successful.size() + "/" + specs.size() + " 个章节");
            artifact.setTotalParagraphs(specs.size());
            artifact.setProcessedParagraphs(successful.size());
            artifact.setRewrittenParagraphs(successful.size());
            artifact.setUpdatedAt(LocalDateTime.now());
            documentJobMapper.insert(artifact);
            updateStage(workflow, "DOCUMENT", "SUCCESS", artifact.getMessage(), artifact);
        } catch (Exception exception) {
            updateStage(workflow, "DOCUMENT", "FAILED", readable(exception), null);
        }
    }

    private SectionResult generateSection(DesignWorkflowVO workflow, SectionSpec spec, String title, String outputType,
                                          String requirements, String sources) {
        updateStage(workflow, spec.key(), "RUNNING", "正在调用万量矩阵生成", null);
        try {
            String prompt = """
                    请为机械设计项目生成一个独立章节。只输出可直接写入 Word 的中文正文，不输出 Markdown。
                    不虚构数据、标准编号或参考文献；缺少信息时明确写“待补充”或“待工程师校核”。
                    题目：%s
                    交付类型：%s
                    本章节任务：%s
                    用户要求与已确认参数：%s
                    上传资料摘要：%s
                    """.formatted(title, outputType, spec.instruction(), requirements == null ? "" : requirements, sources);
            String text = matrixDesignService.generate("你是机械设计与本科毕业设计工程师。", prompt);
            updateStage(workflow, spec.key(), "SUCCESS", "章节已生成", null);
            return new SectionResult(spec.name(), text, true);
        } catch (Exception exception) {
            updateStage(workflow, spec.key(), "FAILED", readable(exception), null);
            return new SectionResult(spec.name(), "", false);
        }
    }

    private synchronized void updateStage(DesignWorkflowVO workflow, String key, String status, String message, DocumentJobRecord artifact) {
        DesignWorkflowStageVO stage = workflow.getStages().stream().filter(item -> key.equals(item.getKey())).findFirst().orElseThrow();
        stage.setStatus(status);
        stage.setMessage(message);
        if (artifact != null) {
            stage.setJobId(artifact.getJobId());
            stage.setFileName(artifact.getFileName());
            stage.setDownloadUrl("/api/documents/" + artifact.getJobId() + "/download");
        }
        workflow.setUpdatedAt(LocalDateTime.now());
        workflow.setMessage("正在生成：" + workflow.getStages().stream().filter(item -> "RUNNING".equals(item.getStatus())).count()
                + "，已完成：" + workflow.getStages().stream().filter(item -> "SUCCESS".equals(item.getStatus())).count());
        persistWorkflow(workflow, null, null);
    }

    private synchronized void finish(DesignWorkflowVO workflow, Long userId, String title) {
        boolean failed = workflow.getStages().stream().anyMatch(stage -> "FAILED".equals(stage.getStatus()));
        boolean hasArtifact = workflow.getStages().stream().anyMatch(stage -> stage.getJobId() != null);
        workflow.setStatus(!hasArtifact ? "FAILED" : failed ? "PARTIAL_SUCCESS" : "SUCCESS");
        workflow.setMessage(!hasArtifact ? "工作流生成失败，请查看各阶段错误"
                : failed ? "工作流已结束，部分文件生成失败，可单独下载成功文件" : "CAD 与设计文档均已生成");
        workflow.setUpdatedAt(LocalDateTime.now());
        persistWorkflow(workflow, userId, title);
    }

    private DesignWorkflowVO newWorkflow() {
        DesignWorkflowVO workflow = new DesignWorkflowVO();
        workflow.setWorkflowId(id());
        workflow.setStatus("RUNNING");
        workflow.setMessage("设计任务已进入后台处理");
        workflow.setCreatedAt(LocalDateTime.now());
        workflow.setUpdatedAt(LocalDateTime.now());
        workflow.setStages(new ArrayList<>(List.of(
                stage("SOURCES", "资料准备"), stage("CAD", "CAD 图纸"), stage("PLAN", "总体方案与参数"),
                stage("CALCULATION", "计算与零部件选型"), stage("DRAFT", "论文初稿章节"), stage("DOCUMENT", "汇总 Word 文档")
        )));
        return workflow;
    }

    private DesignWorkflowStageVO stage(String key, String name) {
        DesignWorkflowStageVO stage = new DesignWorkflowStageVO();
        stage.setKey(key);
        stage.setName(name);
        stage.setStatus("PENDING");
        stage.setMessage("等待生成");
        return stage;
    }

    private DocumentJobRecord artifact(Long userId, String fileName, String feature, String modeName) {
        DocumentJobRecord record = new DocumentJobRecord();
        record.setJobId(id());
        record.setUserId(userId);
        record.setFileName(fileName);
        record.setSourceFeature(feature);
        record.setMode(feature);
        record.setModeName(modeName);
        record.setPlatform("ENGINEERING");
        record.setPlatformName("设计生成");
        record.setStatus("RUNNING");
        record.setTotalParagraphs(1);
        record.setProcessedParagraphs(0);
        record.setRewrittenParagraphs(0);
        record.setParagraphsJson("[]");
        record.setCreatedAt(LocalDateTime.now());
        record.setUpdatedAt(LocalDateTime.now());
        return record;
    }

    private void persistWorkflow(DesignWorkflowVO workflow, Long userId, String title) {
        try {
            DocumentJobRecord record = documentJobMapper.selectById(workflow.getWorkflowId());
            if (record == null) {
                record = artifact(userId, (title == null ? "设计任务" : title) + "-生成工作流", "DESIGN_WORKFLOW", "设计生成工作流");
                record.setJobId(workflow.getWorkflowId());
                record.setCreatedAt(workflow.getCreatedAt());
            }
            record.setStatus(workflow.getStatus());
            record.setMessage(workflow.getMessage());
            record.setTotalParagraphs(workflow.getStages().size());
            record.setProcessedParagraphs((int) workflow.getStages().stream().filter(stage -> "SUCCESS".equals(stage.getStatus()) || "FAILED".equals(stage.getStatus())).count());
            record.setParagraphsJson(objectMapper.writeValueAsString(workflow.getStages()));
            record.setUpdatedAt(workflow.getUpdatedAt());
            if (documentJobMapper.selectById(record.getJobId()) == null) documentJobMapper.insert(record);
            else documentJobMapper.updateById(record);
        } catch (Exception exception) {
            throw new IllegalStateException("保存设计工作流失败：" + readable(exception), exception);
        }
    }

    private DesignWorkflowVO fromRecord(DocumentJobRecord record) {
        DesignWorkflowVO workflow = new DesignWorkflowVO();
        workflow.setWorkflowId(record.getJobId());
        workflow.setStatus(record.getStatus());
        workflow.setMessage(record.getMessage());
        workflow.setCreatedAt(record.getCreatedAt());
        workflow.setUpdatedAt(record.getUpdatedAt());
        try {
            workflow.setStages(objectMapper.readValue(record.getParagraphsJson(), new TypeReference<List<DesignWorkflowStageVO>>() {}));
        } catch (Exception exception) {
            workflow.setStages(List.of());
        }
        return workflow;
    }

    private String extractSources(List<MultipartFile> files) {
        try {
            List<String> sections = new ArrayList<>();
            int remaining = 12000;
            for (MultipartFile file : files) {
                String name = file.getOriginalFilename() == null ? "未命名文件" : file.getOriginalFilename();
                String lower = name.toLowerCase();
                String text;
                if (lower.endsWith(".docx")) {
                    try (XWPFDocument document = new XWPFDocument(new ByteArrayInputStream(file.getBytes()))) {
                        text = document.getParagraphs().stream().map(XWPFParagraph::getText).filter(value -> value != null && !value.isBlank())
                                .reduce("", (a, b) -> a + "\n" + b);
                    }
                } else if (lower.endsWith(".txt") || lower.endsWith(".md")) {
                    text = new String(file.getBytes(), StandardCharsets.UTF_8);
                } else {
                    text = "已上传 " + name + "，当前仅记录文件类型与名称。";
                }
                text = text.substring(0, Math.min(text.length(), remaining));
                sections.add("【资料：" + name + "】\n" + text);
                remaining -= text.length();
                if (remaining <= 0) break;
            }
            return String.join("\n\n", sections);
        } catch (Exception exception) {
            throw new IllegalStateException("读取上传资料失败：" + readable(exception), exception);
        }
    }

    private byte[] buildDocx(String title, List<SectionResult> sections) throws Exception {
        try (XWPFDocument document = new XWPFDocument(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            XWPFParagraph heading = document.createParagraph();
            heading.setAlignment(ParagraphAlignment.CENTER);
            XWPFRun headingRun = heading.createRun();
            headingRun.setBold(true);
            headingRun.setFontSize(20);
            headingRun.setText(title + " 设计说明初稿");
            for (SectionResult section : sections) {
                XWPFParagraph sectionHeading = document.createParagraph();
                XWPFRun sectionRun = sectionHeading.createRun();
                sectionRun.setBold(true);
                sectionRun.setFontSize(16);
                sectionRun.setText(section.name());
                for (String block : section.text().split("\\R+")) {
                    if (block.isBlank()) continue;
                    XWPFParagraph paragraph = document.createParagraph();
                    paragraph.setIndentationFirstLine(480);
                    paragraph.createRun().setText(block.trim());
                }
            }
            document.write(output);
            return output.toByteArray();
        }
    }

    private String id() { return UUID.randomUUID().toString().replace("-", ""); }
    private String readable(Exception exception) {
        String message = exception.getMessage();
        return message == null || message.isBlank() ? exception.getClass().getSimpleName() : message;
    }
    @PreDestroy public void shutdown() { executor.shutdown(); }
    private record SectionSpec(String key, String name, String instruction) {}
    private record SectionResult(String name, String text, boolean success) {}
}
