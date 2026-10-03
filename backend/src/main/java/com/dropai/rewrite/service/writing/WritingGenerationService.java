package com.dropai.rewrite.service.writing;

import com.dropai.rewrite.auth.AuthContext;
import com.dropai.rewrite.entity.DocumentJobRecord;
import com.dropai.rewrite.mapper.DocumentJobMapper;
import com.dropai.rewrite.service.PointService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.core.task.TaskExecutor;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
    private final DoubaoWritingService doubao;

    public WritingGenerationService(JdbcTemplate jdbcTemplate,
                                    ReferenceSearchService referenceSearchService,
                                    ChartRenderService chartRenderService,
                                    CitationManagerService citationManagerService,
                                    DocxExportService docxExportService,
                                    WritingQualityGate qualityGate,
                                    DocumentJobMapper documentJobMapper,
                                    PointService pointService,
                                    TaskExecutor taskExecutor,
                                    ObjectMapper objectMapper,
                                    DoubaoWritingService doubao) {
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
        this.doubao = doubao;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void recoverInterruptedTasks() {
        String message = "上次运行已中断，可从大纲页重新生成";
        LocalDateTime now = LocalDateTime.now();
        jdbcTemplate.update("""
                UPDATE writing_project SET status='FAILED', current_stage=?, error_message=?, updated_at=?
                WHERE status='RUNNING' AND id IN
                  (SELECT project_id FROM writing_generation_task WHERE status='RUNNING')
                """, message, message, now);
        jdbcTemplate.update("""
                UPDATE writing_generation_task SET status='FAILED', stage=?, error_message=?, updated_at=?
                WHERE status='RUNNING'
                """, message, message, now);
    }

    @Transactional
    public Map<String, Object> start(String projectId) {
        Long userId = AuthContext.requireUserId();
        Map<String, Object> current = WritingJdbc.one(jdbcTemplate,
                "SELECT * FROM writing_project WHERE id=? AND user_id=?", projectId, userId);
        if ("RUNNING".equalsIgnoreCase(WritingJdbc.text(current.get("status")))) return status(userId, projectId);
        Map<String, Object> project = WritingJdbc.one(jdbcTemplate,
                "SELECT * FROM writing_project WHERE id=? AND user_id=? FOR UPDATE", projectId, userId);
        if ("RUNNING".equalsIgnoreCase(WritingJdbc.text(project.get("status")))) return status(userId, projectId);
        int cost = estimateCost(projectId);
        pointService.ensureEnoughCustom(userId, cost);
        String taskId = WritingJdbc.id("wgt");
        LocalDateTime now = LocalDateTime.now();
        jdbcTemplate.update("""
                INSERT INTO writing_generation_task (id, project_id, user_id, task_type, status, stage, progress, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, taskId, projectId, userId, "FULL_GENERATE", "RUNNING", "创建生成任务", 1, now, now);
        jdbcTemplate.update("UPDATE writing_project SET status=?, current_stage=?, progress=?, error_message='', skip_references=1, updated_at=? WHERE id=?",
                "RUNNING", "创建生成任务", 1, now, projectId);
        Runnable launch = () -> taskExecutor.execute(() -> run(projectId, taskId, userId, cost));
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() { launch.run(); }
            });
        } else launch.run();
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
            update(projectId, taskId, "所有二级标题并行生成正文", 28, "RUNNING", "");
            generateText(projectId, taskId);
            update(projectId, taskId, "生成图表和表格", 72, "RUNNING", "");
            chartRenderService.renderProjectCharts(projectId, root);
            update(projectId, taskId, "组装正文与原生图表", 84, "RUNNING", "");
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

    private void generateText(String projectId, String taskId) throws Exception {
        Map<String, Object> project = WritingJdbc.one(jdbcTemplate, "SELECT * FROM writing_project WHERE id=?", projectId);
        if (!doubao.configured()) throw new IllegalStateException("未配置豆包，无法执行二级标题多分支写作");
        List<Map<String, Object>> chapters = WritingJdbc.list(jdbcTemplate,
                "SELECT * FROM writing_chapter WHERE project_id=? ORDER BY chapter_no", projectId);
        List<BranchInput> branches = new ArrayList<>();
        for (Map<String, Object> chapter : chapters) {
            for (Map<String, Object> section : WritingJdbc.list(jdbcTemplate,
                    "SELECT * FROM writing_section WHERE chapter_id=? AND heading_level=2 ORDER BY sort_order", chapter.get("id"))) {
                List<Map<String, Object>> children = WritingJdbc.list(jdbcTemplate,
                        "SELECT * FROM writing_section WHERE parent_section_id=? AND heading_level=3 ORDER BY sort_order", section.get("id"));
                branches.add(new BranchInput(chapter, section, children));
            }
        }
        if (branches.isEmpty()) throw new IllegalStateException("大纲至少需要一个二级标题");
        int target = Math.max(6000, Math.min(9000, WritingJdbc.integer(project.get("target_word_count"), 8000)));
        int branchTarget = Math.max(420, (target - 450) / Math.max(1, branches.size()));
        AtomicInteger completedBranches = new AtomicInteger(0);
        int totalBranches = branches.size();
        List<BranchResult> results = runBranchesConcurrently(branches.stream()
                .<Supplier<BranchResult>>map(branch -> () -> {
                    BranchResult result = generateBranch(project, branch, branchTarget);
                    reportBranchProgress(projectId, taskId, completedBranches, totalBranches);
                    return result;
                }).toList());
        for (int i = 0; i < branches.size(); i++) persistBranch(branches.get(i), results.get(i));
        rebalanceWordCount(projectId, target);
        rebuildChapterContent(projectId);
        String preview = WritingJdbc.text(WritingJdbc.one(jdbcTemplate,
                "SELECT preview_text FROM writing_project WHERE id=?", projectId).get("preview_text"));
        String abstractText = doubao.complete("""
                你是中文本科论文摘要编辑。根据题目和正文生成约300字中文摘要，交代研究对象、资料与方法、主要发现、方案和边界。
                不使用“本文首先、其次、最后”等模板句，不虚构数据，不输出标题、关键词或Markdown。
                """, "题目：" + project.get("title") + "\n正文摘录：\n" + preview.substring(0, Math.min(9000, preview.length())), 1400);
        jdbcTemplate.update("UPDATE writing_project SET abstract_text=?, english_abstract=?, updated_at=? WHERE id=?",
                abstractText.trim(), "", LocalDateTime.now(), projectId);
        rebuildChapterContent(projectId);
    }

    private BranchResult generateBranch(Map<String, Object> project, BranchInput branch, int targetWords) {
        String children = branch.children().isEmpty() ? "无三级标题，正文全部写入lead。" : branch.children().stream()
                .map(child -> child.get("id") + " | " + child.get("section_no") + " " + child.get("title"))
                .reduce((a, b) -> a + "\n" + b).orElse("");
        String visuals = visualPlan(branch.chapter(), branch.section());
        String response = doubao.complete("""
                你是中文本科论文分节作者。本次只写一个二级标题分支，并返回严格JSON，不输出Markdown代码围栏。
                格式：{\"lead\":\"二级标题直属正文\",\"children\":[{\"id\":\"三级标题ID\",\"content\":\"正文\"}]}。
                围绕具体问题写作，交代论据、分析过程、观察结果和边界，避免套话、段落同构和机械转折。
                本稿不设置参考文献，不写引用标号或[[REF:...]]标记，不虚构来源。
                图表只能解释给定图表计划，禁止虚构调查、实验、比例、企业数据或图表数值。
                若有三级标题，每个children对象必须与给定ID一一对应；若没有三级标题，children返回空数组。
                """, """
                论文题目：%s
                章标题：第%s章 %s
                二级标题：%s %s
                本分支目标长度：约%d字
                三级标题：
                %s
                图表计划：
                %s
                """.formatted(project.get("title"), branch.chapter().get("chapter_no"), branch.chapter().get("title"),
                branch.section().get("section_no"), branch.section().get("title"), targetWords, children, visuals), 5200);
        BranchResult result = parseBranch(response, branch);
        return result;
    }

    private BranchResult parseBranch(String response, BranchInput branch) {
        try {
            String clean = jsonObjectOnly(response);
            Map<?, ?> root = objectMapper.readValue(clean, Map.class);
            String lead = WritingJdbc.text(root.get("lead"));
            Map<String, String> children = new LinkedHashMap<>();
            Object value = root.get("children");
            if (value instanceof List<?> list) for (Object item : list) if (item instanceof Map<?, ?> map) {
                children.put(WritingJdbc.text(map.get("id")), WritingJdbc.text(map.get("content")));
            }
            for (Map<String, Object> child : branch.children()) children.putIfAbsent(WritingJdbc.text(child.get("id")), "");
            return new BranchResult(lead, children);
        } catch (Exception ignored) {
            return new BranchResult(response.replaceAll("(?m)^#{1,3}\\s*", "").trim(), Map.of());
        }
    }

    private void persistBranch(BranchInput branch, BranchResult result) {
        updateSectionContent(branch.section(), result.lead());
        for (Map<String, Object> child : branch.children()) {
            String content = result.children().getOrDefault(WritingJdbc.text(child.get("id")), "");
            updateSectionContent(child, content);
        }
    }

    private void updateSectionContent(Map<String, Object> section, String content) {
        String clean = WritingJdbc.text(content);
        jdbcTemplate.update("UPDATE writing_section SET content=?,summary=?,status='SUCCESS',updated_at=? WHERE id=?",
                clean, clean.substring(0, Math.min(160, clean.length())), LocalDateTime.now(), section.get("id"));
    }

    private String visualPlan(Map<String, Object> chapter, Map<String, Object> section) {
        String chapterId = WritingJdbc.text(chapter.get("id"));
        String sectionId = WritingJdbc.text(section.get("id"));
        List<String> lines = new ArrayList<>();
        for (Map<String, Object> chart : WritingJdbc.list(jdbcTemplate,
                "SELECT chart_no,title FROM writing_chart WHERE chapter_id=? AND insert_after_section=? ORDER BY sort_order", chapterId, sectionId)) {
            lines.add("图" + chart.get("chart_no") + " " + chart.get("title") + "（Word原生图表，数据由本稿大纲和正文统计）");
        }
        for (Map<String, Object> table : WritingJdbc.list(jdbcTemplate,
                "SELECT table_no,title FROM writing_table WHERE chapter_id=? AND insert_after_section=? ORDER BY sort_order", chapterId, sectionId)) {
            lines.add("表" + table.get("table_no") + " " + table.get("title") + "（Word原生三线表）");
        }
        return lines.isEmpty() ? "本节无图表。" : String.join("\n", lines);
    }

    private void rebalanceWordCount(String projectId, int target) {
        rebuildChapterContent(projectId);
        int count = bodyWordCount(projectId);
        if (count < 6000) {
            List<Map<String, Object>> sections = WritingJdbc.list(jdbcTemplate,
                    "SELECT * FROM writing_section WHERE project_id=? AND heading_level=2 ORDER BY section_no", projectId);
            int each = Math.max(180, (Math.max(6000, target - 250) - count) / Math.max(1, sections.size()) + 40);
            List<String> additions = runBranchesConcurrently(sections.stream().<Supplier<String>>map(section -> () ->
                    doubao.complete("""
                            为已有本科论文小节补写一个有实质信息的新段落。不得复述原文，不新增引用编号、调查数据或图表数值，
                            重点补充概念边界、机制解释、实际约束或方案适用条件。只输出补充正文，不输出标题。
                            """, "小节：" + section.get("section_no") + " " + section.get("title") + "\n目标约" + each + "字\n已有正文：" + section.get("content"), 1800)).toList());
            for (int i = 0; i < sections.size(); i++) {
                String merged = WritingJdbc.text(sections.get(i).get("content")) + "\n" + additions.get(i).trim();
                updateSectionContent(sections.get(i), merged);
            }
        }
        rebuildChapterContent(projectId);
        count = bodyWordCount(projectId);
        if (count > 9000) {
            List<Map<String, Object>> sections = WritingJdbc.list(jdbcTemplate,
                    "SELECT * FROM writing_section WHERE project_id=? ORDER BY heading_level,section_no", projectId);
            double ratio = 8750.0 / count;
            List<String> condensed = runBranchesConcurrently(sections.stream().<Supplier<String>>map(section -> () -> {
                String content = WritingJdbc.text(section.get("content"));
                int wanted = Math.max(120, (int) (content.length() * ratio));
                return doubao.complete("压缩下面论文段落到约" + wanted + "字，保留事实、限制说明和所有[[REF:...]]标记，不新增内容，不输出标题。",
                        content, 3000);
            }).toList());
            for (int i = 0; i < sections.size(); i++) updateSectionContent(sections.get(i), condensed.get(i));
        }
        rebuildChapterContent(projectId);
        int finalCount = bodyWordCount(projectId);
        if (finalCount < 6000 || finalCount > 9000) throw new IllegalStateException("正文字数自动校准失败：" + finalCount + "字");
    }

    private int bodyWordCount(String projectId) {
        StringBuilder text = new StringBuilder();
        for (Map<String, Object> row : WritingJdbc.list(jdbcTemplate,
                "SELECT content FROM writing_section WHERE project_id=? ORDER BY heading_level,section_no", projectId)) text.append(WritingJdbc.text(row.get("content")));
        Matcher matcher = Pattern.compile("[\\p{IsHan}A-Za-z0-9]").matcher(text);
        int count = 0;
        while (matcher.find()) count++;
        return count;
    }

    private void rebuildChapterContent(String projectId) {
        StringBuilder preview = new StringBuilder();
        for (Map<String, Object> chapter : WritingJdbc.list(jdbcTemplate,
                "SELECT * FROM writing_chapter WHERE project_id=? ORDER BY chapter_no", projectId)) {
            StringBuilder body = new StringBuilder();
            for (Map<String, Object> section : WritingJdbc.list(jdbcTemplate,
                    "SELECT * FROM writing_section WHERE chapter_id=? AND heading_level=2 ORDER BY sort_order", chapter.get("id"))) {
                body.append(section.get("section_no")).append(' ').append(section.get("title")).append('\n')
                        .append(WritingJdbc.text(section.get("content"))).append("\n");
                for (Map<String, Object> child : WritingJdbc.list(jdbcTemplate,
                        "SELECT * FROM writing_section WHERE parent_section_id=? AND heading_level=3 ORDER BY sort_order", section.get("id"))) {
                    body.append(child.get("section_no")).append(' ').append(child.get("title")).append('\n')
                            .append(WritingJdbc.text(child.get("content"))).append("\n");
                }
            }
            String chapterContent = body.toString().trim();
            jdbcTemplate.update("UPDATE writing_chapter SET content=?,chapter_summary=?,status='SUCCESS',updated_at=? WHERE id=?",
                    chapterContent, chapterContent.substring(0, Math.min(180, chapterContent.length())), LocalDateTime.now(), chapter.get("id"));
            preview.append("第").append(chapter.get("chapter_no")).append("章 ").append(chapter.get("title")).append('\n').append(chapterContent).append("\n\n");
        }
        jdbcTemplate.update("UPDATE writing_project SET preview_text=? WHERE id=?", preview.toString().trim(), projectId);
    }

    private String jsonObjectOnly(String value) {
        String clean = value == null ? "{}" : value.trim().replaceFirst("^```(?:json)?\\s*", "").replaceFirst("\\s*```$", "");
        int start = clean.indexOf('{'), end = clean.lastIndexOf('}');
        return start >= 0 && end > start ? clean.substring(start, end + 1) : clean;
    }

    private record BranchInput(Map<String, Object> chapter, Map<String, Object> section,
                               List<Map<String, Object>> children) {}
    private record BranchResult(String lead, Map<String, String> children) {}

    static <T> List<T> runBranchesConcurrently(List<Supplier<T>> branches) {
        if (branches.isEmpty()) return List.of();
        AtomicInteger branchNumber = new AtomicInteger();
        ExecutorService executor = Executors.newFixedThreadPool(branches.size(), runnable -> {
            Thread thread = new Thread(runnable, "writing-branch-" + branchNumber.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        });
        try {
            List<CompletableFuture<T>> futures = branches.stream()
                    .map(branch -> CompletableFuture.supplyAsync(branch, executor)).toList();
            CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new)).join();
            return futures.stream().map(CompletableFuture::join).toList();
        } finally {
            executor.shutdownNow();
        }
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

    private synchronized void reportBranchProgress(String projectId, String taskId,
                                                    AtomicInteger completedBranches, int totalBranches) {
        int finished = completedBranches.incrementAndGet();
        int progress = Math.min(70, 28 + (int) Math.round(finished * 42.0 / Math.max(1, totalBranches)));
        update(projectId, taskId,
                "二级标题并行生成中（" + finished + "/" + totalBranches + "）",
                progress, "RUNNING", "");
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
