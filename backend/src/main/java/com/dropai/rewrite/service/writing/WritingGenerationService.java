package com.dropai.rewrite.service.writing;

import com.dropai.rewrite.auth.AuthContext;
import com.dropai.rewrite.entity.DocumentJobRecord;
import com.dropai.rewrite.mapper.DocumentJobMapper;
import com.dropai.rewrite.service.PointService;
import com.dropai.rewrite.service.AiRewriteService;
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
    private final AiRewriteService aiRewriteService;
    private final WritingImageLibraryService imageLibraryService;

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
                                    AiRewriteService aiRewriteService,
                                    WritingImageLibraryService imageLibraryService) {
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
        this.aiRewriteService = aiRewriteService;
        this.imageLibraryService = imageLibraryService;
    }

    @Transactional
    public Map<String, Object> start(String projectId) {
        Long userId = AuthContext.requireUserId();
        Map<String, Object> project = WritingJdbc.one(jdbcTemplate, "SELECT * FROM writing_project WHERE id=? AND user_id=?", projectId, userId);
        if (!List.of("OUTLINE_CONFIRMED", "SUCCESS", "FAILED").contains(WritingJdbc.text(project.get("status")))) {
            throw new IllegalStateException("请先保存并确认提纲和图片需求，再生成正文");
        }
        List<Map<String, Object>> pendingImageTasks = WritingJdbc.list(jdbcTemplate, """
                SELECT t.requirement_name,s.section_no,s.title FROM writing_image_task t
                JOIN writing_section s ON s.id=t.section_id
                WHERE t.project_id=? AND t.status<>'CONFIRMED' ORDER BY s.sort_order,t.sort_order
                """, projectId);
        if (!pendingImageTasks.isEmpty()) {
            String pending = pendingImageTasks.stream().limit(6)
                    .map(row -> row.get("section_no") + " " + row.get("requirement_name"))
                    .collect(java.util.stream.Collectors.joining("、"));
            throw new IllegalStateException("正文生成前必须确认全部图片任务，当前待确认：" + pending);
        }
        if (!WritingJdbc.bool(project.get("skip_references"), false)) referenceSearchService.ensureQuota(userId, projectId);
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
            update(projectId, taskId, "准备图片素材库", 20, "RUNNING", "");
            // Step2 owns image discovery and binding. Generation only validates the
            // already confirmed library and must never invent or fetch new images.
            imageLibraryService.validateUploads(projectId);
            // start() has already checked the configured Chinese/English quota. Do not
            // launch a second paid search based on an unrelated hard-coded total here.
            update(projectId, taskId, "生成摘要和章节正文", 42, "RUNNING", "");
            generateText(projectId);
            update(projectId, taskId, "生成图表和表格", 72, "RUNNING", "");
            chartRenderService.renderProjectCharts(projectId, root);
            update(projectId, taskId, "处理引用编号", 84, "RUNNING", "");
            citationManagerService.normalize(projectId);
            appendReferencesToPreview(projectId);
            update(projectId, taskId, "正在自动规范文档格式", 89, "RUNNING", "");
            List<String> outlineAutoFixes = qualityGate.normalizeOutline(projectId);
            update(projectId, taskId, "组装DOCX", 92, "RUNNING", "");
            Path docx = docxExportService.export(projectId, root.resolve(safeName(WritingJdbc.text(project.get("title"))) + ".docx"));
            update(projectId, taskId, "执行质量检查", 97, "RUNNING", "");
            Map<String, Object> report = qualityGate.check(projectId, docx, outlineAutoFixes);
            Path reportPath = root.resolve("writing-quality-report.json");
            qualityGate.writeReport(projectId, reportPath, report);
            if (!Boolean.TRUE.equals(report.get("passed"))) {
                throw new IllegalStateException("文档生成未完成：" + report.get("errors"));
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
        String abstractText = abstractText(project);
        jdbcTemplate.update("UPDATE writing_project SET abstract_text=?, english_abstract=?, updated_at=? WHERE id=?",
                abstractText,
                englishAbstract(project),
                LocalDateTime.now(), projectId);
        int refIndex = 0;
        StringBuilder preview = new StringBuilder("摘要\n").append(abstractText).append("\n\n");
        for (Map<String, Object> chapter : WritingJdbc.list(jdbcTemplate, "SELECT * FROM writing_chapter WHERE project_id=? ORDER BY chapter_no", projectId)) {
            String chapterId = WritingJdbc.text(chapter.get("id"));
            String chapterType = WritingJdbc.text(chapter.get("chapter_type"));
            if (chapterType.isBlank()) chapterType = inferChapterType(WritingJdbc.text(chapter.get("title")));
            if ("reference".equals(chapterType)) {
                String content = referenceChapterContent(references);
                saveChapter(chapterId, content);
                preview.append("参考文献\n").append(content).append("\n");
                continue;
            }
            if ("acknowledgement".equals(chapterType)) {
                String content = acknowledgementText(project);
                saveChapter(chapterId, content);
                preview.append("致谢\n").append(content).append("\n\n");
                continue;
            }
            StringBuilder chapterContent = new StringBuilder();
            List<Map<String, Object>> sections = WritingJdbc.list(jdbcTemplate, "SELECT * FROM writing_section WHERE chapter_id=? ORDER BY sort_order", chapterId);
            if ("conclusion".equals(chapterType)) {
                chapterContent.append(conclusionText(project));
                saveChapter(chapterId, chapterContent.toString());
                preview.append("第").append(chapter.get("chapter_no")).append("章 ")
                        .append(chapter.get("title")).append("\n")
                        .append(chapterContent).append("\n\n");
                continue;
            }
            for (Map<String, Object> section : sections) {
                String marker = "";
                if (!references.isEmpty()) {
                    Map<String, Object> ref = references.get(refIndex++ % references.size());
                    marker = "[[REF:" + ref.get("id") + "]]";
                }
                String visualCitation = visualCitation(chapter, section);
                String prompt = """
                        请为论文《%s》生成“%s %s”小节正文，只输出正文，不重复标题。
                        专业模式：%s。小节类型：%s。
                        要求：论述清晰、避免虚构数据；保留引用标记%s；如提供图表引用句必须原样写入正文：%s
                        只能引用系统提供的已确认图片句，不得自行创建图号、图题或推断图片中不可见的事实。篇幅约600至900字。
                        """.formatted(title, section.get("section_no"), section.get("title"),
                        WritingJdbc.text(project.get("document_mode")), WritingJdbc.text(section.get("content_type")), marker, visualCitation);
                String body = aiRewriteService.rewrite(prompt, "ACADEMIC_WRITING").trim();
                // 图题由DOCX装配阶段根据已确认图片统一生成，正文模型不得自行创建图号。
                body = body.replaceAll("(?m)^\\s*图\\d+-\\d+[^\\r\\n]*(?:\\R|$)", "").trim();
                body = body.replaceAll("\\[\\[DROP_AI_PROTECTED_\\d+]]", "").trim();
                if (!marker.isBlank() && !body.contains(marker)) body += marker;
                if (!visualCitation.isBlank() && !body.contains(visualCitation)) body += visualCitation;
                jdbcTemplate.update("UPDATE writing_section SET content=?, summary=?, status=?, updated_at=? WHERE id=?",
                        body, body.substring(0, Math.min(120, body.length())), "SUCCESS", LocalDateTime.now(), section.get("id"));
                chapterContent.append(body).append("\n");
            }
            saveChapter(chapterId, chapterContent.toString());
            preview.append("第").append(chapter.get("chapter_no")).append("章 ").append(chapter.get("title")).append("\n")
                    .append(chapterContent).append("\n");
        }
        jdbcTemplate.update("UPDATE writing_project SET preview_text=? WHERE id=?", preview.toString(), projectId);
    }

    private void saveChapter(String chapterId, String content) {
        String value = content == null ? "" : content.trim();
        jdbcTemplate.update("UPDATE writing_chapter SET content=?, chapter_summary=?, status=?, updated_at=? WHERE id=?",
                value, value.substring(0, Math.min(180, value.length())), "SUCCESS", LocalDateTime.now(), chapterId);
    }

    private String referenceChapterContent(List<Map<String, Object>> references) {
        StringBuilder content = new StringBuilder();
        int number = 0;
        for (Map<String, Object> reference : references) {
            String formatted = WritingJdbc.text(reference.get("formatted_text")).replaceFirst("^\\[\\d+]\\s*", "");
            if (!formatted.isBlank()) content.append("[").append(++number).append("] ").append(formatted).append("\n");
        }
        return content.toString().trim();
    }

    private String acknowledgementText(Map<String, Object> project) {
        String major = WritingJdbc.text(project.get("major"));
        String field = major.isBlank() ? "本研究" : major + "专业学习与本研究";
        return "本论文的完成离不开指导教师在选题、研究思路和论文修改方面给予的指导与帮助。感谢在"
                + field + "过程中提供支持的各位老师和同学，也感谢家人在学习与写作期间给予的理解和鼓励。谨向所有提供帮助的人表示诚挚谢意。";
    }

    private String abstractText(Map<String, Object> project) {
        String title = WritingJdbc.text(project.get("title"));
        if ("environment".equalsIgnoreCase(WritingJdbc.text(project.get("document_mode")))) {
            return "本文以“" + title + "”为研究对象，从城市存量空间更新背景出发，围绕项目概况、基址环境、适老化使用需求与低碳营造策略展开分析。研究结合项目区位、街区现状和公共空间图像资料，梳理通行安全、停留休憩、设施可达性、生态效益与后期维护等关键问题，并据此提出空间组织、慢行优化、复合活动、材料选择和绿色基础设施等设计策略。研究认为，街道微更新应在保留社区生活连续性的基础上，以小尺度、低干预和可维护的方式改善老年群体的日常使用体验，同时兼顾资源节约与环境韧性，为既有社区公共空间更新提供设计参考。";
        }
        return "本文围绕“" + title + "”展开研究，结合项目资料、章节分析和已确认的图表素材梳理研究对象、现实问题及实施条件，并提出与专业情境相匹配的解决策略。研究强调结论应建立在可核验材料和明确的分析过程之上，通过需求识别、方案组织、实施保障与效果复核形成完整工作链路，为同类项目提供可参考的思路。";
    }

    private String englishAbstract(Map<String, Object> project) {
        String title = WritingJdbc.text(project.get("title"));
        if ("environment".equalsIgnoreCase(WritingJdbc.text(project.get("document_mode")))) {
            return "Taking the project \"" + title + "\" as its subject, this study examines site conditions, age-friendly needs and low-carbon renewal strategies for existing community streets. It reviews spatial accessibility, pedestrian safety, resting facilities, ecological performance and maintenance, and proposes small-scale, low-intervention design measures. The study argues that community micro-renewal should preserve everyday social life while improving comfort, inclusiveness and environmental resilience.";
        }
        return "This study examines \"" + title + "\" through verified project materials, chapter-based analysis and confirmed visual evidence. It identifies practical constraints, develops context-specific strategies and establishes a coherent workflow from problem definition to implementation and review.";
    }

    private String conclusionText(Map<String, Object> project) {
        String title = WritingJdbc.text(project.get("title"));
        if ("environment".equalsIgnoreCase(WritingJdbc.text(project.get("document_mode")))) {
            int confirmedWebImages = WritingJdbc.integer(WritingJdbc.one(jdbcTemplate,
                    "SELECT COUNT(*) AS n FROM writing_image_material WHERE project_id=? AND is_confirmed=1 AND UPPER(source_type)='WEB_SEARCH'",
                    project.get("id")).get("n"), 0);
            String evidenceSummary = confirmedWebImages > 0
                    ? "经用户确认的联网图片为相关章节提供了辅助视觉证据，但不替代现场测绘与核验。"
                    : "本次未使用经用户确认的联网图片，正文不据此声称已完成地图或场地影像分析。";
            return """
                    研究总结：本文围绕“%s”展开研究，从项目区位、街区现状、老年群体日常活动与低碳营造要求出发，梳理既有公共空间在慢行连续性、停留设施、功能复合、环境舒适度和维护管理方面的主要问题。研究强调，社区街道微更新不是对空间形态的简单翻新，而是在有限建设条件下协调安全、便利、交往、生态与地方生活连续性的综合设计过程。

                    主要成果：研究形成了以适老需求识别为基础、以低干预更新为原则的设计思路。方案通过优化步行路径与节点衔接、补充连续休憩设施、组织分时复合活动空间、采用耐久低维护材料，并结合遮阴、透水铺装和乡土植物等绿色措施，改善街道公共空间的可达性、舒适性与环境效益。%s设计章节仅依据最终确认的素材位置组织图文关系。

                    存在不足：本研究主要依据项目地址、已确认材料与设计分析展开，尚缺少持续性的现场行为观察、分季节环境监测和更新后的使用反馈。公开资料不能替代精确测绘、产权核实、地下管线调查及居民协商。具体尺寸、材料构造和投资安排仍需在后续深化阶段结合现场条件与实施主体进一步校核。

                    未来展望：后续工作可通过现场访谈、行为地图、步行可达性测评和微气候监测完善证据基础，并建立居民、社区管理者与设计团队共同参与的方案复核机制。实施层面可采用分阶段、小规模试点和使用后评估，根据老年群体的真实反馈持续调整设施布局与维护方式，使适老化与低碳目标在社区长期运营中得到落实。
                    """.formatted(title, evidenceSummary).trim();
        }
        return """
                研究总结：本文围绕“%s”展开研究，根据项目材料和章节分析梳理研究对象、现实问题与实施条件，形成从问题识别到策略提出的完整论证过程。

                主要成果：研究结合专业情境提出了具有对应关系的解决策略，并通过确认后的图表和素材组织正文，使分析依据、方案内容与最终产物保持一致。

                存在不足：现有研究仍受资料范围、验证周期和实施条件限制，部分判断需要通过后续调查、实践和使用反馈进一步核实。

                未来展望：后续可扩大证据来源，细化实施参数并建立阶段性复核机制，使研究结论在具体应用中持续接受验证和完善。
                """.formatted(title).trim();
    }

    private String inferChapterType(String title) {
        String normalized = title == null ? "" : title.toLowerCase();
        if (normalized.contains("参考文献") || normalized.equals("references")) return "reference";
        if (normalized.contains("致谢") || normalized.equals("acknowledgement") || normalized.equals("acknowledgments")) return "acknowledgement";
        if (normalized.contains("结论") || normalized.contains("展望") || normalized.contains("总结")) return "conclusion";
        return "content";
    }

    private void appendReferencesToPreview(String projectId) {
        String preview = WritingJdbc.text(WritingJdbc.one(jdbcTemplate,
                "SELECT preview_text FROM writing_project WHERE id=?", projectId).get("preview_text"));
        List<Map<String, Object>> refs = WritingJdbc.list(jdbcTemplate, """
                SELECT * FROM writing_reference
                WHERE project_id=?
                ORDER BY COALESCE(final_number, citation_number, 999999), relevance_score DESC
                """, projectId);
        StringBuilder referenceText = new StringBuilder();
        int sequentialNumber = 0;
        for (Map<String, Object> ref : refs) {
            String formatted = WritingJdbc.text(ref.get("formatted_text")).replaceFirst("^\\[\\d+]\\s*", "");
            referenceText.append("[").append(++sequentialNumber).append("] ").append(formatted).append("\n");
        }
        jdbcTemplate.update("UPDATE writing_chapter SET content=?,chapter_summary=?,status='SUCCESS',updated_at=? WHERE project_id=? AND chapter_type='reference'",
                referenceText.toString().trim(), "参考文献共" + refs.size() + "篇", LocalDateTime.now(), projectId);
        int referenceStart = preview.indexOf("\n参考文献\n");
        if (referenceStart < 0) referenceStart = preview.indexOf("参考文献\n");
        String before = referenceStart < 0 ? preview.stripTrailing() : preview.substring(0, referenceStart).stripTrailing();
        int acknowledgementStart = referenceStart < 0 ? -1 : preview.indexOf("\n致谢\n", referenceStart + 1);
        String acknowledgement = acknowledgementStart < 0 ? "" : preview.substring(acknowledgementStart);
        String result = before + "\n\n参考文献\n" + referenceText + acknowledgement;
        jdbcTemplate.update("UPDATE writing_project SET preview_text=? WHERE id=?", result, projectId);
    }

    private String firstNo(String table, String column, String chapterId, String prefix) {
        List<Map<String, Object>> rows = WritingJdbc.list(jdbcTemplate, "SELECT " + column + " AS no FROM " + table + " WHERE chapter_id=? ORDER BY sort_order LIMIT 1", chapterId);
        return rows.isEmpty() ? "" : prefix + rows.get(0).get("no");
    }

    private String visualCitation(Map<String, Object> chapter, Map<String, Object> section) {
        String chapterId = WritingJdbc.text(chapter.get("id"));
        List<Map<String, Object>> chapterMaterials = WritingJdbc.list(jdbcTemplate,
                """
                   SELECT m.id,m.display_name,m.user_confirmed_section FROM writing_image_material m
                   LEFT JOIN writing_section s ON s.id=m.user_confirmed_section
                   WHERE m.project_id=? AND m.user_confirmed_chapter=? AND m.is_confirmed=1
                   ORDER BY COALESCE(s.sort_order,999999),m.display_order,m.created_at,m.id""",
                chapter.get("project_id"), chapterId);
        List<String> materialCitations = new java.util.ArrayList<>();
        for (int i = 0; i < chapterMaterials.size(); i++) {
            if (WritingJdbc.text(section.get("id")).equals(WritingJdbc.text(chapterMaterials.get(i).get("user_confirmed_section")))) {
                String number = chapter.get("chapter_no") + "-" + (i + 1);
                String name = WritingJdbc.text(chapterMaterials.get(i).get("display_name"));
                materialCitations.add("如图" + number + "所示，本节引用已确认的“" + (name.isBlank() ? "图片素材" : name)
                        + "”，具体内容以图中可见信息为准。");
            }
        }
        String materialCitation = String.join("", materialCitations);
        String figureCitation = WritingJdbc.list(jdbcTemplate,
                "SELECT chart_no AS no FROM writing_chart WHERE chapter_id=? AND (insert_after_section=? OR ((insert_after_section IS NULL OR insert_after_section='') AND ?=1)) ORDER BY sort_order LIMIT 1",
                chapterId, section.get("id"), WritingJdbc.integer(section.get("sort_order"), 0)).stream()
                .findFirst().map(row -> "图" + row.get("no")).orElse("");
        String tableCitation = WritingJdbc.list(jdbcTemplate,
                "SELECT table_no AS no FROM writing_table WHERE chapter_id=? AND is_confirmed=1 AND (user_confirmed_section=? OR insert_after_section=?) ORDER BY sort_order LIMIT 1",
                chapterId, section.get("id"), section.get("id")).stream()
                .findFirst().map(row -> "表" + row.get("no")).orElse("");
        if (!materialCitation.isBlank() && !tableCitation.isBlank()) {
            return materialCitation + "如" + tableCitation + "所示，相关指标进一步说明了结构特征。";
        }
        if (!materialCitation.isBlank()) return materialCitation;
        if (!figureCitation.isBlank() && !tableCitation.isBlank()) {
            return "如" + figureCitation + "所示，相关趋势存在差异；如" + tableCitation + "所示，各项指标具有不同特征。";
        }
        if (!figureCitation.isBlank()) {
            return "如" + figureCitation + "所示，相关指标在不同维度上呈现差异化变化。";
        }
        if (!tableCitation.isBlank()) {
            return "如" + tableCitation + "所示，不同指标之间存在明显差异。";
        }
        return "从文献与章节分析可以看出，不同维度之间仍存在明显差异。";
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

    String safeName(String value) {
        String safe = value == null ? "writing" : value.replaceAll("[\\\\/:*?\"<>|]+", "_").trim();
        safe = safe.replaceAll("[. ]+$", "");
        return safe.isBlank() ? "writing" : safe;
    }
}
