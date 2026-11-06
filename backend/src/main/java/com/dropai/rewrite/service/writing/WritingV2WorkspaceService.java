package com.dropai.rewrite.service.writing;

import com.dropai.rewrite.auth.AuthContext;
import com.dropai.rewrite.entity.DocumentJobRecord;
import com.dropai.rewrite.mapper.DocumentJobMapper;
import com.dropai.rewrite.service.PointService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.Year;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class WritingV2WorkspaceService {
    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final DoubaoWritingService doubao;
    private final DocxExportService docxExportService;
    private final DocumentJobMapper documentJobMapper;
    private final PointService pointService;

    public WritingV2WorkspaceService(JdbcTemplate jdbcTemplate,
                                     ObjectMapper objectMapper,
                                     DoubaoWritingService doubao,
                                     DocxExportService docxExportService,
                                     DocumentJobMapper documentJobMapper,
                                     PointService pointService) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
        this.doubao = doubao;
        this.docxExportService = docxExportService;
        this.documentJobMapper = documentJobMapper;
        this.pointService = pointService;
    }

    public List<Map<String, Object>> templates() {
        return List.of(
                template("business", "工商管理", List.of("绪论", "理论基础", "现状分析", "问题分析", "优化策略", "总结")),
                template("engineering", "工程技术", List.of("绪论", "系统需求分析", "总体设计", "关键技术实现", "测试与分析", "总结与展望")),
                template("art_design", "艺术设计", List.of("绪论", "设计理论基础", "项目现状分析", "设计方案构思", "方案实现与展示", "总结")),
                template("general", "通用论文", List.of("绪论", "理论基础", "研究现状", "问题分析", "对策建议", "总结"))
        );
    }

    @Transactional
    public Map<String, Object> createProject(Map<String, Object> request) {
        Long userId = AuthContext.requireUserId();
        String title = required(request.get("title"), "题目不能为空");
        LocalDateTime now = LocalDateTime.now();
        String projectId = WritingJdbc.id("wp");
        int target = WritingJdbc.integer(request.get("targetWordCount"), 8000);
        List<String> keywords = inferKeywords(title);
        jdbcTemplate.update("""
                INSERT INTO writing_project (id, user_id, title, major, document_type, target_word_count,
                abstract_word_count, keyword_count, chapter_count, reference_count, chinese_reference_count,
                english_reference_count, year_start, year_end, citation_style, writing_tone, generate_english_abstract,
                generate_toc, generate_figure_list, generate_table_list, skip_references, requirements, keywords_json,
                status, current_stage, progress, paper_type, research_object, research_organization, research_industry,
                research_location, research_scene, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                projectId, userId, title, text(request.get("major")), textOr(request.get("writingType"), "毕业论文"),
                target, Math.max(250, target / 25), 4, 0, 0, 0, 0,
                Year.now().getValue() - 5, Year.now().getValue(), "GB/T 7714", textOr(request.get("writingTone"), "本科论文"),
                true, true, false, false, true, text(request.get("requirements")), json(keywords),
                "DRAFT", "项目设置完成", 5, textOr(request.get("paperType"), "THEORETICAL"),
                text(request.get("researchObject")), text(request.get("researchOrganization")),
                text(request.get("researchIndustry")), text(request.get("researchLocation")),
                text(request.get("researchScene")), now, now);
        applyTemplate(projectId, textOr(request.get("template"), "business"), false);
        return detail(projectId);
    }

    public Map<String, Object> detail(String projectId) {
        Long userId = AuthContext.requireUserId();
        Map<String, Object> project = WritingJdbc.one(jdbcTemplate, "SELECT * FROM writing_project WHERE id=? AND user_id=?", projectId, userId);
        project.put("chapters", chapters(projectId));
        project.put("references", references(projectId));
        project.put("assets", assets(userId, projectId));
        project.put("files", WritingJdbc.list(jdbcTemplate, "SELECT * FROM writing_export_file WHERE project_id=? ORDER BY created_at DESC", projectId));
        project.put("doubaoStatus", doubao.providerStatus());
        return project;
    }

    @Transactional
    public Map<String, Object> saveSettings(String projectId, Map<String, Object> request) {
        Long userId = AuthContext.requireUserId();
        WritingJdbc.one(jdbcTemplate, "SELECT id FROM writing_project WHERE id=? AND user_id=?", projectId, userId);
        jdbcTemplate.update("""
                UPDATE writing_project SET title=?, major=?, document_type=?, target_word_count=?, writing_tone=?,
                requirements=?, paper_type=?, research_object=?, research_organization=?, research_industry=?,
                research_location=?, research_scene=?, updated_at=?, current_stage=?, progress=? WHERE id=? AND user_id=?
                """,
                required(request.get("title"), "题目不能为空"), text(request.get("major")),
                textOr(request.get("writingType"), "毕业论文"), WritingJdbc.integer(request.get("targetWordCount"), 8000),
                textOr(request.get("writingTone"), "本科论文"), text(request.get("requirements")),
                textOr(request.get("paperType"), "THEORETICAL"), text(request.get("researchObject")),
                text(request.get("researchOrganization")), text(request.get("researchIndustry")),
                text(request.get("researchLocation")), text(request.get("researchScene")),
                LocalDateTime.now(), "项目设置已保存", 12, projectId, userId);
        return detail(projectId);
    }

    @Transactional
    public Map<String, Object> saveChapterResources(String projectId, String chapterId, Map<String, Object> request) {
        Long userId = AuthContext.requireUserId();
        WritingJdbc.one(jdbcTemplate, "SELECT id FROM writing_project WHERE id=? AND user_id=?", projectId, userId);
        WritingJdbc.one(jdbcTemplate, "SELECT id FROM writing_chapter WHERE id=? AND project_id=?", chapterId, projectId);
        List<String> types = List.of("TEXT", "IMAGE_SEARCH", "IMAGE_UPLOAD", "TABLE", "CHART", "FLOW", "MODEL", "AI_ANALYSIS");
        for (String type : types) {
            Object raw = request.get(type);
            Map<?, ?> config = raw instanceof Map<?, ?> map ? map : Map.of();
            boolean enabled = WritingJdbc.bool(config.get("enabled"), "TEXT".equals(type));
            int quantity = Math.max(0, Math.min(20, WritingJdbc.integer(config.get("quantity"), enabled ? 1 : 0)));
            jdbcTemplate.update("DELETE FROM writing_chapter_resource WHERE chapter_id=? AND resource_type=?", chapterId, type);
            jdbcTemplate.update("""
                    INSERT INTO writing_chapter_resource (id, project_id, chapter_id, resource_type, enabled, quantity, config_json, created_at, updated_at)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """, WritingJdbc.id("wcr"), projectId, chapterId, type, enabled, quantity, json(config), LocalDateTime.now(), LocalDateTime.now());
        }
        jdbcTemplate.update("UPDATE writing_project SET current_stage=?, progress=?, updated_at=? WHERE id=?",
                "章节资源配置已保存", 45, LocalDateTime.now(), projectId);
        return detail(projectId);
    }

    @Transactional
    public Map<String, Object> saveCaseMaterial(String projectId, Map<String, Object> request) {
        Long userId = AuthContext.requireUserId();
        WritingJdbc.one(jdbcTemplate, "SELECT id FROM writing_project WHERE id=? AND user_id=?", projectId, userId);
        jdbcTemplate.update("DELETE FROM writing_v2_asset WHERE project_id=? AND user_id=? AND asset_type='CASE_TEXT'", projectId, userId);
        jdbcTemplate.update("""
                INSERT INTO writing_v2_asset (id, project_id, user_id, asset_type, content_text, created_at)
                VALUES (?, ?, ?, 'CASE_TEXT', ?, ?)
                """, WritingJdbc.id("wva"), projectId, userId, text(request.get("content")), LocalDateTime.now());
        jdbcTemplate.update("UPDATE writing_project SET current_stage=?, progress=?, updated_at=? WHERE id=?",
                "案例资料已保存", 22, LocalDateTime.now(), projectId);
        return detail(projectId);
    }

    @Transactional
    public Map<String, Object> uploadImages(String projectId, List<MultipartFile> files) {
        Long userId = AuthContext.requireUserId();
        WritingJdbc.one(jdbcTemplate, "SELECT id FROM writing_project WHERE id=? AND user_id=?", projectId, userId);
        if (files == null || files.isEmpty()) return detail(projectId);
        Path root = Path.of("storage", "writing-v2", userId.toString(), projectId, "images").toAbsolutePath().normalize();
        try {
            Files.createDirectories(root);
            for (MultipartFile file : files) {
                if (file == null || file.isEmpty()) continue;
                String original = safeName(file.getOriginalFilename());
                String ext = extension(original).toLowerCase(Locale.ROOT);
                if (!List.of("png", "jpg", "jpeg").contains(ext)) throw new IllegalArgumentException("图片素材仅支持 PNG/JPG");
                String id = WritingJdbc.id("wva");
                Path target = root.resolve(id + "_" + original).normalize();
                if (!target.startsWith(root)) throw new IllegalArgumentException("非法文件名");
                file.transferTo(target);
                jdbcTemplate.update("""
                        INSERT INTO writing_v2_asset (id, project_id, user_id, asset_type, file_name, file_path, mime_type, created_at)
                        VALUES (?, ?, ?, 'IMAGE', ?, ?, ?, ?)
                        """, id, projectId, userId, original, target.toString(), file.getContentType(), LocalDateTime.now());
            }
            jdbcTemplate.update("UPDATE writing_project SET current_stage=?, progress=?, updated_at=? WHERE id=?",
                    "图片素材已上传", 32, LocalDateTime.now(), projectId);
            return detail(projectId);
        } catch (Exception exception) {
            throw new IllegalStateException("图片上传失败：" + exception.getMessage(), exception);
        }
    }

    @Transactional
    public Map<String, Object> designOutline(String projectId, Map<String, Object> request) {
        Long userId = AuthContext.requireUserId();
        Map<String, Object> project = WritingJdbc.one(jdbcTemplate, "SELECT * FROM writing_project WHERE id=? AND user_id=?", projectId, userId);
        String templateKey = textOr(request.get("template"), "business");
        boolean optimize = WritingJdbc.bool(request.get("aiOptimize"), true);
        applyTemplate(projectId, templateKey, false);
        if (optimize && doubao.configured()) {
            optimizeOutlineWithAi(projectId, project, templateKey);
        }
        initializeChapterResources(projectId);
        jdbcTemplate.update("UPDATE writing_project SET status=?, current_stage=?, progress=?, updated_at=? WHERE id=?",
                "OUTLINE_READY", "大纲已按模板生成并完成优化", 45, LocalDateTime.now(), projectId);
        return detail(projectId);
    }

    @Transactional
    public Map<String, Object> saveReferences(String projectId, Map<String, Object> request) {
        Long userId = AuthContext.requireUserId();
        WritingJdbc.one(jdbcTemplate, "SELECT id FROM writing_project WHERE id=? AND user_id=?", projectId, userId);
        String text = text(request.get("references"));
        List<String> lines = parseReferenceLines(text);
        jdbcTemplate.update("DELETE FROM writing_reference WHERE project_id=?", projectId);
        int index = 1;
        LocalDateTime now = LocalDateTime.now();
        for (String line : lines) {
            String cleaned = line.replaceFirst("^\\[\\d+]\\s*", "").trim();
            String id = WritingJdbc.id("ref");
            jdbcTemplate.update("""
                    INSERT INTO writing_reference (id, project_id, reference_key, title, authors, publication_year,
                    journal_or_publisher, volume, issue, pages, doi, url, source_platform, abstract_text, search_keywords,
                    searched_at, applicable_chapters, verification_status, relevance_score, formatted_text, final_number,
                    created_at, updated_at)
                    VALUES (?, ?, ?, ?, ?, ?, ?, '', '', '', NULL, '', 'USER_INPUT', '', '', ?, '', 'MANUAL', 1, ?, ?, ?, ?)
                    """,
                    id, projectId, "ref_" + String.format("%03d", index), titleFromReference(cleaned),
                    "用户提供", Year.now().getValue(), "用户提供参考文献", now,
                    "[" + index + "] " + cleaned, index, now, now);
            try {
                jdbcTemplate.update("""
                        UPDATE writing_reference SET citation_number=?, language=?, document_type=?, format_incomplete=? WHERE id=?
                        """, index, hasChinese(cleaned) ? "ZH" : "EN", "JOURNAL", false, id);
            } catch (Exception ignored) {
            }
            index++;
        }
        jdbcTemplate.update("UPDATE writing_project SET reference_count=?, current_stage=?, progress=?, updated_at=? WHERE id=?",
                lines.size(), "参考文献已导入", 52, LocalDateTime.now(), projectId);
        return detail(projectId);
    }

    @Transactional
    public Map<String, Object> generateContent(String projectId) {
        Long userId = AuthContext.requireUserId();
        Map<String, Object> project = WritingJdbc.one(jdbcTemplate, "SELECT * FROM writing_project WHERE id=? AND user_id=?", projectId, userId);
        int cost = Math.max(1, pointService.featureCostPoints("WRITING_DOCX"));
        pointService.ensureEnoughCustom(userId, cost);
        jdbcTemplate.update("UPDATE writing_project SET status=?, current_stage=?, progress=?, error_message='', updated_at=? WHERE id=?",
                "RUNNING", "豆包正在生成正文", 62, LocalDateTime.now(), projectId);
        try {
            String content = doubao.complete(contentSystemPrompt(), contentUserPrompt(projectId, project), 12000);
            content = ensureReferenceSection(removeDuplicateReferenceSection(content), references(projectId));
            if (!validCitationOrder(content, references(projectId).size())) {
                content = doubao.complete(repairSystemPrompt(), repairUserPrompt(content, references(projectId)), 12000);
                content = ensureReferenceSection(removeDuplicateReferenceSection(content), references(projectId));
            }
            if (!validCitationOrder(content, references(projectId).size())) {
                throw new IllegalStateException("正文引用顺序不符合 [1][2][3] 规则，请减少参考文献数量或补充资料后重试");
            }
            writeContentToSections(projectId, content);
            jdbcTemplate.update("UPDATE writing_project SET preview_text=?, abstract_text=?, status=?, current_stage=?, progress=?, updated_at=? WHERE id=?",
                    content, abstractText(content), "CONTENT_READY", "正文已生成，等待文档处理", 78, LocalDateTime.now(), projectId);
            pointService.deductCustom(userId, null, "WRITING_DOCX", "纯文字稿生成", cost, "文字创作中心正文生成：" + project.get("title"));
            return detail(projectId);
        } catch (Exception exception) {
            jdbcTemplate.update("UPDATE writing_project SET status=?, current_stage=?, progress=?, error_message=?, updated_at=? WHERE id=?",
                    "FAILED", "正文生成失败", 0, exception.getMessage(), LocalDateTime.now(), projectId);
            throw exception;
        }
    }

    @Transactional
    public Map<String, Object> exportDocx(String projectId) {
        Long userId = AuthContext.requireUserId();
        Map<String, Object> project = WritingJdbc.one(jdbcTemplate, "SELECT * FROM writing_project WHERE id=? AND user_id=?", projectId, userId);
        Path root = Path.of("storage", "writing-v2", userId.toString(), projectId, "exports").toAbsolutePath().normalize();
        Path docx = docxExportService.export(projectId, root.resolve(safeName(WritingJdbc.text(project.get("title"))) + ".docx"));
        try {
            byte[] bytes = Files.readAllBytes(docx);
            String jobId = "writing_v2_" + projectId;
            writeDocumentJob(jobId, userId, docx.getFileName().toString(), bytes);
            jdbcTemplate.update("""
                    INSERT INTO writing_export_file (id, project_id, document_job_id, file_name, file_type, file_size, download_url, created_at)
                    VALUES (?, ?, ?, ?, 'docx', ?, ?, ?)
                    """, WritingJdbc.id("wef"), projectId, jobId, docx.getFileName().toString(), bytes.length,
                    "/api/documents/" + jobId + "/download", LocalDateTime.now());
            jdbcTemplate.update("UPDATE writing_project SET status=?, current_stage=?, progress=?, updated_at=? WHERE id=?",
                    "SUCCESS", "DOCX 已导出", 100, LocalDateTime.now(), projectId);
            return detail(projectId);
        } catch (Exception exception) {
            throw new IllegalStateException("DOCX导出失败：" + exception.getMessage(), exception);
        }
    }

    private void applyTemplate(String projectId, String templateKey, boolean keepExisting) {
        if (!keepExisting) {
            jdbcTemplate.update("DELETE FROM writing_chapter WHERE project_id=?", projectId);
            jdbcTemplate.update("DELETE FROM writing_section WHERE project_id=?", projectId);
        }
        List<String> chapterTitles = templateChapters(templateKey);
        int chapterNo = 1;
        for (String chapterTitle : chapterTitles) {
            String chapterId = WritingJdbc.id("wc");
            jdbcTemplate.update("""
                    INSERT INTO writing_chapter (id, project_id, chapter_no, title, target_word_count, section_count,
                    image_count, table_count, use_references, default_chart_type, status, sort_order, created_at, updated_at)
                    VALUES (?, ?, ?, ?, 1200, 2, 0, 0, 1, 'NONE', 'DRAFT', ?, ?, ?)
                    """, chapterId, projectId, chapterNo, chapterTitle, chapterNo, LocalDateTime.now(), LocalDateTime.now());
            int sectionNo = 1;
            for (String section : defaultSections(chapterTitle, chapterNo)) {
                jdbcTemplate.update("""
                        INSERT INTO writing_section (id, project_id, chapter_id, section_no, title, target_word_count,
                        sort_order, status, created_at, updated_at)
                        VALUES (?, ?, ?, ?, ?, 600, ?, 'DRAFT', ?, ?)
                        """, WritingJdbc.id("ws"), projectId, chapterId, chapterNo + "." + sectionNo, section,
                        sectionNo, LocalDateTime.now(), LocalDateTime.now());
                sectionNo++;
            }
            chapterNo++;
        }
        jdbcTemplate.update("UPDATE writing_project SET chapter_count=?, updated_at=? WHERE id=?",
                chapterTitles.size(), LocalDateTime.now(), projectId);
        initializeChapterResources(projectId);
    }

    private void initializeChapterResources(String projectId) {
        for (Map<String, Object> chapter : WritingJdbc.list(jdbcTemplate, "SELECT id, chapter_no FROM writing_chapter WHERE project_id=?", projectId)) {
            String chapterId = WritingJdbc.text(chapter.get("id"));
            int no = WritingJdbc.integer(chapter.get("chapter_no"), 1);
            if (!WritingJdbc.list(jdbcTemplate, "SELECT id FROM writing_chapter_resource WHERE chapter_id=?", chapterId).isEmpty()) continue;
            for (String type : List.of("TEXT", "IMAGE_SEARCH", "IMAGE_UPLOAD", "TABLE", "CHART", "FLOW", "MODEL", "AI_ANALYSIS")) {
                boolean enabled = "TEXT".equals(type);
                int quantity = enabled ? 1 : 0;
                jdbcTemplate.update("""
                        INSERT INTO writing_chapter_resource (id, project_id, chapter_id, resource_type, enabled, quantity, config_json, created_at, updated_at)
                        VALUES (?, ?, ?, ?, ?, ?, '{}', ?, ?)
                        """, WritingJdbc.id("wcr"), projectId, chapterId, type, enabled, quantity, LocalDateTime.now(), LocalDateTime.now());
            }
        }
    }

    private void optimizeOutlineWithAi(String projectId, Map<String, Object> project, String templateKey) {
        try {
            String outline = doubao.complete("""
                    你是毕业论文大纲设计助手。只能基于模板微调章节标题，不新增业务功能，不生成正文。
                    输出 JSON 数组，每项包含 title 和 sections。sections 为 2 个二级标题。不要输出 Markdown。
                    """, """
                    论文题目：%s
                    专业：%s
                    模板：%s
                    当前章节：%s
                    """.formatted(project.get("title"), project.get("major"), templateKey, templateChapters(templateKey)), 4096);
            List<Map<String, Object>> items = objectMapper.readValue(jsonOnly(outline), objectMapper.getTypeFactory().constructCollectionType(List.class, Map.class));
            if (items.size() < 4) return;
            jdbcTemplate.update("DELETE FROM writing_chapter WHERE project_id=?", projectId);
            jdbcTemplate.update("DELETE FROM writing_section WHERE project_id=?", projectId);
            int chapterNo = 1;
            for (Map<String, Object> item : items) {
                String chapterId = WritingJdbc.id("wc");
                String title = textOr(item.get("title"), "章节" + chapterNo);
                jdbcTemplate.update("""
                        INSERT INTO writing_chapter (id, project_id, chapter_no, title, target_word_count, section_count,
                        image_count, table_count, use_references, default_chart_type, status, sort_order, created_at, updated_at)
                        VALUES (?, ?, ?, ?, 1200, 2, 0, 0, 1, 'NONE', 'DRAFT', ?, ?, ?)
                        """, chapterId, projectId, chapterNo, title, chapterNo, LocalDateTime.now(), LocalDateTime.now());
                List<?> sections = item.get("sections") instanceof List<?> list ? list : defaultSections(title, chapterNo);
                int sectionNo = 1;
                for (Object section : sections.stream().limit(3).toList()) {
                    jdbcTemplate.update("""
                            INSERT INTO writing_section (id, project_id, chapter_id, section_no, title, target_word_count,
                            sort_order, status, created_at, updated_at)
                            VALUES (?, ?, ?, ?, ?, 600, ?, 'DRAFT', ?, ?)
                            """, WritingJdbc.id("ws"), projectId, chapterId, chapterNo + "." + sectionNo,
                            textOr(section, "小节" + sectionNo), sectionNo, LocalDateTime.now(), LocalDateTime.now());
                    sectionNo++;
                }
                chapterNo++;
            }
        } catch (Exception ignored) {
        }
    }

    private String contentSystemPrompt() {
        return """
                你是 DokiAI 文字创作中心的正文生成引擎，只负责根据用户已经提供的题目、大纲、案例资料、图片说明和参考文献生成论文正文。
                禁止联网搜索，禁止补造参考文献，禁止随机编造数据、企业、作者、年份或图表。
                必须遵守引用顺序：正文第一次引用必须是 [1]，第二次必须是 [2]，依次递增；禁止同一句出现 [1][2] 这种连续堆叠引用。
                需要引用多篇文献时，拆成不同句子分别引用。
                输出包含章节标题和正文，末尾不要自行编造参考文献；参考文献由系统追加。
                """;
    }

    private String contentUserPrompt(String projectId, Map<String, Object> project) {
        return """
                项目题目：%s
                专业：%s
                写作类型：%s
                目标字数：%s
                语言风格：%s
                特殊要求：%s
                论文类型：%s
                研究对象：%s
                企业/机构：%s
                地区：%s
                行业领域：%s
                应用场景：%s

                最终大纲：
                %s

                章节资源配置（必须严格按配置生成；表格必须输出为结构化表格，不得伪装成图片）：
                %s

                案例资料：
                %s

                图片资料：
                %s

                用户提供参考文献（只能使用这些编号，不得新增）：
                %s

                请生成论文正文。引用编号必须从 [1] 开始按首次出现顺序递增。
                """.formatted(project.get("title"), project.get("major"), project.get("document_type"),
                project.get("target_word_count"), project.get("writing_tone"), project.get("requirements"),
                project.get("paper_type"), project.get("research_object"), project.get("research_organization"),
                project.get("research_location"), project.get("research_industry"), project.get("research_scene"),
                outlineText(projectId), chapterResourceText(projectId), caseText(projectId), imageText(projectId), referencesText(projectId));
    }

    private String repairSystemPrompt() {
        return "你是引用编号修复器。只修复正文中的参考文献引用编号顺序，不新增事实、不新增参考文献、不改变章节结构。";
    }

    private String repairUserPrompt(String content, List<Map<String, Object>> references) {
        return """
                请把下面正文中的引用编号修复为首次出现顺序 [1]、[2]、[3]...。
                禁止一句话堆叠多个引用，遇到 [1][2] 应拆成两句。
                可用参考文献数量：%d
                参考文献列表：
                %s

                正文：
                %s
                """.formatted(references.size(), referencesText(references), content);
    }

    private void writeContentToSections(String projectId, String content) {
        List<Map<String, Object>> sections = WritingJdbc.list(jdbcTemplate,
                "SELECT * FROM writing_section WHERE project_id=? ORDER BY sort_order", projectId);
        if (sections.isEmpty()) return;
        String body = content.replaceFirst("(?s)\\R*参考文献\\R.*$", "").trim();
        int cursor = 0;
        for (int i = 0; i < sections.size(); i++) {
            Map<String, Object> section = sections.get(i);
            String sectionNo = WritingJdbc.text(section.get("section_no"));
            String nextNo = i + 1 < sections.size() ? WritingJdbc.text(sections.get(i + 1).get("section_no")) : "";
            int start = indexOfSection(body, sectionNo, cursor);
            int end = nextNo.isBlank() ? body.length() : indexOfSection(body, nextNo, Math.max(cursor, start + 1));
            String part;
            if (start >= 0 && end > start) {
                part = body.substring(start, end).trim();
                cursor = end;
            } else if (i == 0) {
                part = body;
            } else {
                part = "";
            }
            jdbcTemplate.update("UPDATE writing_section SET content=?, summary=?, status=?, updated_at=? WHERE id=?",
                    part, part.substring(0, Math.min(120, part.length())), part.isBlank() ? "DRAFT" : "SUCCESS", LocalDateTime.now(), section.get("id"));
        }
        for (Map<String, Object> chapter : chapters(projectId)) {
            String chapterId = WritingJdbc.text(chapter.get("id"));
            String joined = String.join("\n", WritingJdbc.list(jdbcTemplate,
                    "SELECT content FROM writing_section WHERE chapter_id=? ORDER BY sort_order", chapterId)
                    .stream().map(row -> WritingJdbc.text(row.get("content"))).filter(value -> !value.isBlank()).toList());
            jdbcTemplate.update("UPDATE writing_chapter SET content=?, chapter_summary=?, status=?, updated_at=? WHERE id=?",
                    joined, joined.substring(0, Math.min(180, joined.length())), joined.isBlank() ? "DRAFT" : "SUCCESS", LocalDateTime.now(), chapterId);
        }
    }

    private int indexOfSection(String body, String sectionNo, int from) {
        if (sectionNo == null || sectionNo.isBlank()) return -1;
        Pattern pattern = Pattern.compile("(?m)^\\s*" + Pattern.quote(sectionNo) + "\\s+");
        Matcher matcher = pattern.matcher(body);
        return matcher.find(Math.max(0, from)) ? matcher.start() : -1;
    }

    private boolean validCitationOrder(String content, int refCount) {
        if (refCount <= 0) return true;
        String body = content.replaceFirst("(?s)\\R*参考文献\\R.*$", "");
        Matcher matcher = Pattern.compile("\\[(\\d+)]").matcher(body);
        int expected = 1;
        while (matcher.find()) {
            int value = Integer.parseInt(matcher.group(1));
            if (value == expected) expected++;
            else if (value >= expected) return false;
            if (value > refCount) return false;
        }
        return expected > 1;
    }

    private String ensureReferenceSection(String content, List<Map<String, Object>> references) {
        StringBuilder builder = new StringBuilder(content.stripTrailing()).append("\n\n参考文献\n");
        for (int i = 0; i < references.size(); i++) {
            String formatted = WritingJdbc.text(references.get(i).get("formatted_text")).replaceFirst("^\\[\\d+]\\s*", "");
            builder.append("[").append(i + 1).append("] ").append(formatted).append("\n");
        }
        return builder.toString().trim();
    }

    private String removeDuplicateReferenceSection(String content) {
        return content == null ? "" : content.replaceFirst("(?s)\\R*参考文献\\R.*$", "").trim();
    }

    private List<Map<String, Object>> chapters(String projectId) {
        List<Map<String, Object>> chapters = WritingJdbc.list(jdbcTemplate, "SELECT * FROM writing_chapter WHERE project_id=? ORDER BY chapter_no", projectId);
        for (Map<String, Object> chapter : chapters) {
            chapter.put("sections", WritingJdbc.list(jdbcTemplate, "SELECT * FROM writing_section WHERE chapter_id=? ORDER BY sort_order", chapter.get("id")));
            chapter.put("resources", WritingJdbc.list(jdbcTemplate, "SELECT resource_type, enabled, quantity, config_json FROM writing_chapter_resource WHERE chapter_id=? ORDER BY resource_type", chapter.get("id")));
        }
        return chapters;
    }

    private List<Map<String, Object>> references(String projectId) {
        return WritingJdbc.list(jdbcTemplate,
                "SELECT * FROM writing_reference WHERE project_id=? ORDER BY final_number IS NULL, final_number, citation_number IS NULL, citation_number, created_at", projectId);
    }

    private List<Map<String, Object>> assets(Long userId, String projectId) {
        return WritingJdbc.list(jdbcTemplate,
                "SELECT id, asset_type, file_name, mime_type, content_text, created_at FROM writing_v2_asset WHERE project_id=? AND user_id=? ORDER BY created_at",
                projectId, userId);
    }

    private String outlineText(String projectId) {
        StringBuilder builder = new StringBuilder();
        for (Map<String, Object> chapter : chapters(projectId)) {
            builder.append("第").append(chapter.get("chapter_no")).append("章 ").append(chapter.get("title")).append("\n");
            for (Map<String, Object> section : (List<Map<String, Object>>) chapter.get("sections")) {
                builder.append(section.get("section_no")).append(" ").append(section.get("title")).append("\n");
            }
        }
        return builder.toString().trim();
    }

    private String caseText(String projectId) {
        return WritingJdbc.list(jdbcTemplate, "SELECT content_text FROM writing_v2_asset WHERE project_id=? AND asset_type='CASE_TEXT' ORDER BY created_at", projectId)
                .stream().map(row -> WritingJdbc.text(row.get("content_text"))).filter(value -> !value.isBlank()).reduce("", (a, b) -> a + "\n" + b).trim();
    }

    private String chapterResourceText(String projectId) {
        StringBuilder builder = new StringBuilder();
        for (Map<String, Object> chapter : chapters(projectId)) {
            builder.append("第").append(chapter.get("chapter_no")).append("章 ").append(chapter.get("title")).append("：");
            List<Map<String, Object>> resources = (List<Map<String, Object>>) chapter.get("resources");
            for (Map<String, Object> resource : resources) {
                if (WritingJdbc.bool(resource.get("enabled"), false)) builder.append(resource.get("resource_type")).append("×").append(resource.get("quantity")).append(" ");
            }
            builder.append("\n");
        }
        return builder.toString().trim();
    }

    private String imageText(String projectId) {
        List<Map<String, Object>> images = WritingJdbc.list(jdbcTemplate, "SELECT file_name FROM writing_v2_asset WHERE project_id=? AND asset_type='IMAGE' ORDER BY created_at", projectId);
        if (images.isEmpty()) return "无图片素材";
        StringBuilder builder = new StringBuilder();
        int index = 1;
        for (Map<String, Object> image : images) builder.append("图片").append(index++).append("：").append(image.get("file_name")).append("\n");
        return builder.toString().trim();
    }

    private String referencesText(String projectId) {
        return referencesText(references(projectId));
    }

    private String referencesText(List<Map<String, Object>> references) {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < references.size(); i++) {
            String formatted = WritingJdbc.text(references.get(i).get("formatted_text")).replaceFirst("^\\[\\d+]\\s*", "");
            builder.append("[").append(i + 1).append("] ").append(formatted).append("\n");
        }
        return builder.toString().trim();
    }

    private String abstractText(String content) {
        String body = removeDuplicateReferenceSection(content).replaceAll("(?m)^\\s*(第.+章|\\d+(\\.\\d+)?\\s+.+)\\s*$", "").replaceAll("\\s+", "");
        return body.substring(0, Math.min(320, body.length()));
    }

    private void writeDocumentJob(String jobId, Long userId, String fileName, byte[] bytes) {
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
        record.setMode("writing-v2");
        record.setModeName("文字创作中心 V2");
        record.setPlatform("DropAI");
        record.setPlatformName("DropAI");
        record.setStatus("SUCCESS");
        record.setTotalParagraphs(1);
        record.setProcessedParagraphs(1);
        record.setRewrittenParagraphs(1);
        record.setCharCount(bytes.length);
        record.setCostPoints(0);
        record.setPointsCharged(true);
        record.setMessage("文字创作中心 DOCX 导出完成");
        record.setParagraphsJson("[]");
        record.setOutputFile(bytes);
        record.setUpdatedAt(now);
        if (documentJobMapper.selectById(jobId) == null) documentJobMapper.insert(record);
        else documentJobMapper.updateById(record);
    }

    private List<String> parseReferenceLines(String text) {
        List<String> result = new ArrayList<>();
        for (String line : text.split("\\R+")) {
            String value = line.trim();
            if (!value.isBlank()) result.add(value);
        }
        if (result.isEmpty()) throw new IllegalArgumentException("请粘贴至少一条参考文献");
        return result;
    }

    private Map<String, Object> template(String key, String name, List<String> chapters) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("key", key);
        map.put("name", name);
        map.put("chapters", chapters);
        return map;
    }

    private List<String> templateChapters(String key) {
        return templates().stream()
                .filter(template -> key.equals(template.get("key")))
                .findFirst()
                .map(template -> (List<String>) template.get("chapters"))
                .orElse((List<String>) templates().get(0).get("chapters"));
    }

    private List<String> defaultSections(String chapterTitle, int chapterNo) {
        if (chapterNo == 1) return List.of("研究背景与意义", "研究内容与方法");
        if (chapterTitle.contains("理论")) return List.of("核心概念界定", "相关理论基础");
        if (chapterTitle.contains("现状")) return List.of("发展现状", "特征分析");
        if (chapterTitle.contains("问题")) return List.of("主要问题", "原因分析");
        if (chapterTitle.contains("策略") || chapterTitle.contains("对策")) return List.of("优化路径", "实施保障");
        return List.of("章节内容分析", "小结");
    }

    private List<String> inferKeywords(String title) {
        List<String> result = new ArrayList<>();
        for (String part : title.split("[\\s，,、：:]+")) {
            if (part.length() >= 2) result.add(part);
        }
        if (result.isEmpty()) result.add(title);
        return result.stream().limit(4).toList();
    }

    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception exception) {
            return "[]";
        }
    }

    private String jsonOnly(String value) {
        String text = text(value);
        int start = text.indexOf('[');
        int end = text.lastIndexOf(']');
        return start >= 0 && end > start ? text.substring(start, end + 1) : text;
    }

    private String titleFromReference(String value) {
        String cleaned = value.replaceAll("[\\[\\]JMDCOLEB/0-9.]", " ").trim();
        return cleaned.length() > 180 ? cleaned.substring(0, 180) : cleaned;
    }

    private boolean hasChinese(String value) {
        return value != null && value.codePoints().anyMatch(code -> Character.UnicodeScript.of(code) == Character.UnicodeScript.HAN);
    }

    private String safeName(String value) {
        String name = value == null || value.isBlank() ? "file" : value.replaceAll("[\\\\/:*?\"<>|\\s]+", "_");
        return name.length() > 120 ? name.substring(name.length() - 120) : name;
    }

    private String extension(String name) {
        int dot = name == null ? -1 : name.lastIndexOf('.');
        return dot < 0 ? "" : name.substring(dot + 1);
    }

    private String text(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    private String textOr(Object value, String fallback) {
        String text = text(value);
        return text.isBlank() ? fallback : text;
    }

    private String required(Object value, String message) {
        String text = text(value);
        if (text.isBlank()) throw new IllegalArgumentException(message);
        return text;
    }
}
