package com.dropai.rewrite.service.writing;

import com.dropai.rewrite.auth.AuthContext;
import com.dropai.rewrite.entity.DocumentJobRecord;
import com.dropai.rewrite.mapper.DocumentJobMapper;
import com.dropai.rewrite.service.PointService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.core.task.TaskExecutor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Service
public class WritingGenerationService {
    private final JdbcTemplate jdbcTemplate;
    private final ReferenceSearchService referenceSearchService;
    private final ChartRenderService chartRenderService;
    private final CitationManagerService citationManagerService;
    private final DocxExportService docxExportService;
    private final WritingQualityGate qualityGate;
    private final DocumentJobMapper documentJobMapper;
    private final PointService pointService;
    private final TaskExecutor taskExecutor;
    private final ObjectMapper objectMapper;

    public WritingGenerationService(JdbcTemplate jdbcTemplate,
                                    ReferenceSearchService referenceSearchService,
                                    ChartRenderService chartRenderService,
                                    CitationManagerService citationManagerService,
                                    DocxExportService docxExportService,
                                    WritingQualityGate qualityGate,
                                    DocumentJobMapper documentJobMapper,
                                    PointService pointService,
                                    TaskExecutor taskExecutor,
                                    ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.referenceSearchService = referenceSearchService;
        this.chartRenderService = chartRenderService;
        this.citationManagerService = citationManagerService;
        this.docxExportService = docxExportService;
        this.qualityGate = qualityGate;
        this.documentJobMapper = documentJobMapper;
        this.pointService = pointService;
        this.taskExecutor = taskExecutor;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public Map<String, Object> start(String projectId) {
        Long userId = AuthContext.requireUserId();
        Map<String, Object> project = WritingJdbc.one(jdbcTemplate, "SELECT * FROM writing_project WHERE id=? AND user_id=?", projectId, userId);
        int cost = estimateCost(projectId);
        pointService.ensureEnoughCustom(userId, cost);
        String taskId = WritingJdbc.id("wgt");
        LocalDateTime now = LocalDateTime.now();
        jdbcTemplate.update("""
                INSERT INTO writing_generation_task (id, project_id, user_id, task_type, status, stage, progress, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, taskId, projectId, userId, "FULL_GENERATE", "RUNNING", "创建生成任务", 1, now, now);
        jdbcTemplate.update("UPDATE writing_project SET status=?, current_stage=?, progress=?, error_message='', updated_at=? WHERE id=?",
                "RUNNING", "创建生成任务", 1, now, projectId);
        taskExecutor.execute(() -> run(projectId, taskId, userId, cost));
        return status(userId, projectId);
    }

    public Map<String, Object> status(Long userId, String projectId) {
        Map<String, Object> project = WritingJdbc.one(jdbcTemplate, "SELECT * FROM writing_project WHERE id=? AND user_id=?", projectId, userId);
        List<Map<String, Object>> tasks = WritingJdbc.list(jdbcTemplate, "SELECT * FROM writing_generation_task WHERE project_id=? ORDER BY created_at DESC", projectId);
        project.put("tasks", tasks);
        project.put("files", WritingJdbc.list(jdbcTemplate, "SELECT * FROM writing_export_file WHERE project_id=? ORDER BY created_at DESC", projectId));
        return project;
    }

    public String preview(Long userId, String projectId) {
        return WritingJdbc.text(WritingJdbc.one(jdbcTemplate, "SELECT preview_text FROM writing_project WHERE id=? AND user_id=?", projectId, userId).get("preview_text"));
    }

    private void run(String projectId, String taskId, Long userId, int cost) {
        Path root = Path.of("storage", "writing", userId.toString(), projectId).toAbsolutePath().normalize();
        try {
            update(projectId, taskId, "解析用户输入和章节配置", 8, "RUNNING", "");
            Map<String, Object> project = WritingJdbc.one(jdbcTemplate, "SELECT * FROM writing_project WHERE id=?", projectId);
            if (!WritingJdbc.bool(project.get("skip_references"), false)
                    && WritingJdbc.list(jdbcTemplate, "SELECT id FROM writing_reference WHERE project_id=?", projectId).size() < 12) {
                update(projectId, taskId, "联网搜索和验证参考文献", 28, "RUNNING", "");
                referenceSearchService.searchAndSave(userId, projectId, null);
            }
            update(projectId, taskId, "生成摘要和章节正文", 42, "RUNNING", "");
            generateText(projectId);
            update(projectId, taskId, "生成图表和表格", 72, "RUNNING", "");
            chartRenderService.renderProjectCharts(projectId, root);
            update(projectId, taskId, "处理引用编号", 84, "RUNNING", "");
            citationManagerService.normalize(projectId);
            update(projectId, taskId, "组装DOCX", 92, "RUNNING", "");
            Path docx = docxExportService.export(projectId, root.resolve(safeName(WritingJdbc.text(project.get("title"))) + ".docx"));
            update(projectId, taskId, "执行质量检查", 97, "RUNNING", "");
            Map<String, Object> report = qualityGate.check(projectId, docx);
            Path reportPath = root.resolve("writing-quality-report.json");
            qualityGate.writeReport(projectId, reportPath, report);
            if (!Boolean.TRUE.equals(report.get("passed"))) {
                throw new IllegalStateException("质量检查未通过：" + report.get("errors"));
            }
            byte[] bytes = Files.readAllBytes(docx);
            writeDocumentJob(projectId, userId, docx.getFileName().toString(), bytes);
            pointService.deductCustom(userId, taskId, "WRITING_DOCX", "纯文字稿生成", cost, "生成纯文字稿：" + project.get("title"));
            update(projectId, taskId, "文件确认存在并可下载", 100, "SUCCESS", "");
        } catch (Exception exception) {
            update(projectId, taskId, "生成失败", 0, "FAILED", exception.getMessage());
        }
    }

    private void generateText(String projectId) throws Exception {
        Map<String, Object> project = WritingJdbc.one(jdbcTemplate, "SELECT * FROM writing_project WHERE id=?", projectId);
        List<Map<String, Object>> references = WritingJdbc.list(jdbcTemplate,
                "SELECT * FROM writing_reference WHERE project_id=? ORDER BY relevance_score DESC LIMIT 20", projectId);
        if (!WritingJdbc.bool(project.get("skip_references"), false) && references.size() < 5) {
            throw new IllegalStateException("参考文献数量不足，无法生成带真实引用的正文");
        }
        String title = WritingJdbc.text(project.get("title"));
        String abstractText = title + "围绕研究对象、能力结构、现实问题和提升路径展开分析。研究在真实检索文献的基础上，结合章节图表和模拟分析数据，梳理影响因素、提出分层推进策略，并从课程体系、实践平台、评价机制和协同保障等方面形成可操作建议。研究结论认为，能力提升需要从知识、技能、素养和就业支持体系共同发力，才能提高人才培养与岗位需求的匹配度。";
        jdbcTemplate.update("UPDATE writing_project SET abstract_text=?, english_abstract=?, updated_at=? WHERE id=?",
                abstractText,
                "This paper analyzes the research object, capability structure, practical issues and improvement paths. Based on verified online references and chapter-level figures and tables, it proposes practical strategies for curriculum, practice platforms, evaluation mechanisms and collaborative support.",
                LocalDateTime.now(), projectId);
        int refIndex = 0;
        StringBuilder preview = new StringBuilder("摘要\n").append(abstractText).append("\n\n");
        for (Map<String, Object> chapter : WritingJdbc.list(jdbcTemplate, "SELECT * FROM writing_chapter WHERE project_id=? ORDER BY chapter_no", projectId)) {
            String chapterId = WritingJdbc.text(chapter.get("id"));
            StringBuilder chapterContent = new StringBuilder();
            List<Map<String, Object>> sections = WritingJdbc.list(jdbcTemplate, "SELECT * FROM writing_section WHERE chapter_id=? ORDER BY sort_order", chapterId);
            for (Map<String, Object> section : sections) {
                String marker = "";
                if (!references.isEmpty()) {
                    Map<String, Object> ref = references.get(refIndex++ % references.size());
                    marker = "[[REF:" + ref.get("id") + "]]";
                }
                String figureCitation = firstNo("writing_chart", "chart_no", chapterId, "图");
                String tableCitation = firstNo("writing_table", "table_no", chapterId, "表");
                String body = section.get("section_no") + " " + section.get("title") + "。围绕“" + title + "”，本节从概念界定、问题表现和实践约束三个方面展开。已有研究为该问题提供了可验证的理论和经验基础" + marker + "。"
                        + "结合" + figureCitation + "和" + tableCitation + "可以看出，相关指标在不同维度上呈现差异化变化，因此路径设计需要同时考虑主体能力、资源条件和制度环境。"
                        + "本节使用的图表数据均为模拟分析数据，用于展示趋势和结构关系，不代表真实统计调查结果。";
                jdbcTemplate.update("UPDATE writing_section SET content=?, summary=?, status=?, updated_at=? WHERE id=?",
                        body, body.substring(0, Math.min(120, body.length())), "SUCCESS", LocalDateTime.now(), section.get("id"));
                chapterContent.append(body).append("\n");
            }
            jdbcTemplate.update("UPDATE writing_chapter SET content=?, chapter_summary=?, status=?, updated_at=? WHERE id=?",
                    chapterContent.toString(), chapterContent.substring(0, Math.min(180, chapterContent.length())), "SUCCESS", LocalDateTime.now(), chapterId);
            preview.append("第").append(chapter.get("chapter_no")).append("章 ").append(chapter.get("title")).append("\n")
                    .append(chapterContent).append("\n");
        }
        jdbcTemplate.update("UPDATE writing_project SET preview_text=? WHERE id=?", preview.toString(), projectId);
    }

    private String firstNo(String table, String column, String chapterId, String prefix) {
        List<Map<String, Object>> rows = WritingJdbc.list(jdbcTemplate, "SELECT " + column + " AS no FROM " + table + " WHERE chapter_id=? ORDER BY sort_order LIMIT 1", chapterId);
        return rows.isEmpty() ? prefix : prefix + rows.get(0).get("no");
    }

    private void writeDocumentJob(String projectId, Long userId, String fileName, byte[] bytes) throws Exception {
        String jobId = "writing_" + projectId;
        DocumentJobRecord record = documentJobMapper.selectById(jobId);
        LocalDateTime now = LocalDateTime.now();
        if (record == null) {
            record = new DocumentJobRecord();
            record.setJobId(jobId);
            record.setUserId(userId);
            record.setCreatedAt(now);
        }
        record.setFileName(fileName);
        record.setSourceFeature("WRITING_DOCX");
        record.setMode("writing");
        record.setModeName("纯文字稿生成");
        record.setPlatform("DropAI");
        record.setPlatformName("DropAI");
        record.setStatus("SUCCESS");
        record.setTotalParagraphs(1);
        record.setProcessedParagraphs(1);
        record.setRewrittenParagraphs(1);
        record.setCharCount(bytes.length);
        record.setCostPoints(0);
        record.setPointsCharged(true);
        record.setMessage("纯文字稿生成完成");
        record.setParagraphsJson("[]");
        record.setOutputFile(bytes);
        record.setUpdatedAt(now);
        if (documentJobMapper.selectById(jobId) == null) documentJobMapper.insert(record);
        else documentJobMapper.updateById(record);
        jdbcTemplate.update("""
                INSERT INTO writing_export_file (id, project_id, document_job_id, file_name, file_type, file_size, download_url, created_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """, WritingJdbc.id("wef"), projectId, jobId, fileName, "docx", bytes.length, "/api/documents/" + jobId + "/download", now);
    }

    private int estimateCost(String projectId) {
        int chapters = WritingJdbc.list(jdbcTemplate, "SELECT id FROM writing_chapter WHERE project_id=?", projectId).size();
        int charts = WritingJdbc.list(jdbcTemplate, "SELECT id FROM writing_chart WHERE project_id=?", projectId).size();
        return Math.max(60, 40 + chapters * 8 + charts * 2);
    }

    private void update(String projectId, String taskId, String stage, int progress, String status, String error) {
        int realProgress = "FAILED".equals(status) ? Math.max(1, progress) : progress;
        jdbcTemplate.update("UPDATE writing_project SET status=?, current_stage=?, progress=?, error_message=?, updated_at=? WHERE id=?",
                status, stage, realProgress, error == null ? "" : error, LocalDateTime.now(), projectId);
        jdbcTemplate.update("UPDATE writing_generation_task SET status=?, stage=?, progress=?, error_message=?, updated_at=? WHERE id=?",
                status, stage, realProgress, error == null ? "" : error, LocalDateTime.now(), taskId);
    }

    private String safeName(String value) {
        String safe = value == null ? "writing" : value.replaceAll("[\\\\/:*?\"<>|\\s]+", "_");
        return safe.isBlank() ? "writing" : safe;
    }
}
