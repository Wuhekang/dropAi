package com.dropai.rewrite.service.writing;

import com.dropai.rewrite.auth.AuthContext;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
public class WritingProjectService {
    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final DoubaoWritingService doubao;

    public WritingProjectService(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper, DoubaoWritingService doubao) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
        this.doubao = doubao;
    }

    @Transactional
    public Map<String, Object> create(Map<String, Object> request) {
        Long userId = AuthContext.requireUserId();
        String title = required(request.get("title"), "文章题目不能为空");
        LocalDateTime now = LocalDateTime.now();
        String projectId = WritingJdbc.id("wp");
        int target = clampTargetWords(WritingJdbc.integer(request.get("targetWordCount"), 8000));
        int abstractWords = WritingJdbc.integer(request.get("abstractWordCount"), Math.max(250, target / 25));
        int keywordCount = WritingJdbc.integer(request.get("keywordCount"), 4);
        List<String> keywords = readStringList(request.get("keywords"));
        if (keywords.isEmpty()) keywords = inferKeywords(title, keywordCount);
        jdbcTemplate.update("""
                INSERT INTO writing_project (id, user_id, title, major, document_type, target_word_count,
                abstract_word_count, keyword_count, chapter_count, reference_count, chinese_reference_count,
                english_reference_count, year_start, year_end, citation_style, writing_tone, generate_english_abstract,
                generate_toc, generate_figure_list, generate_table_list, skip_references, requirements, keywords_json,
                status, current_stage, progress, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                projectId, userId, title, text(request.get("major")), text(request.get("documentType")),
                target, abstractWords, keywordCount, 0,
                0, 0, 0,
                WritingJdbc.integer(request.get("yearStart"), 2020), WritingJdbc.integer(request.get("yearEnd"), 2026),
                textOr(request.get("citationStyle"), "GB/T 7714"), textOr(request.get("writingTone"), "本科论文"),
                bool(request.get("generateEnglishAbstract"), true), bool(request.get("generateToc"), true),
                bool(request.get("generateFigureList"), true), bool(request.get("generateTableList"), true),
                true, text(request.get("requirements")), json(keywords),
                "DRAFT", "项目已创建", 0, now, now);
        List<Map<String, Object>> chapters = readMapList(request.get("chapters"));
        if (chapters.isEmpty()) chapters = defaultChapters(title, target);
        for (Map<String, Object> chapter : chapters) {
            addChapterInternal(projectId, chapter, false);
        }
        renumberProject(projectId);
        return detail(projectId);
    }

    public Map<String, Object> detail(String projectId) {
        Long userId = AuthContext.requireUserId();
        Map<String, Object> project = WritingJdbc.one(jdbcTemplate,
                "SELECT * FROM writing_project WHERE id=? AND user_id=?", projectId, userId);
        project.put("chapters", chapters(projectId));
        project.put("references", WritingJdbc.list(jdbcTemplate,
                "SELECT * FROM writing_reference WHERE project_id=? ORDER BY final_number IS NULL, final_number, relevance_score DESC", projectId));
        project.put("files", WritingJdbc.list(jdbcTemplate,
                "SELECT * FROM writing_export_file WHERE project_id=? ORDER BY created_at DESC", projectId));
        return project;
    }

    @Transactional
    public Map<String, Object> updateProject(String projectId, Map<String, Object> request) {
        Long userId = AuthContext.requireUserId();
        Map<String, Object> existing = lockProjectForWrite(userId, projectId);
        Object keywords = request.containsKey("keywords") ? request.get("keywords") : existing.get("keywords_json");
        List<String> keywordValues = keywords instanceof String raw
                ? readJsonStringList(raw)
                : readStringList(keywords);
        jdbcTemplate.update("""
                UPDATE writing_project SET title=?, major=?, document_type=?, target_word_count=?, abstract_word_count=?,
                keyword_count=?, reference_count=?, chinese_reference_count=?, english_reference_count=?, year_start=?,
                year_end=?, citation_style=?, writing_tone=?, generate_english_abstract=?, skip_references=?,
                requirements=?, keywords_json=?, updated_at=? WHERE id=?
                """,
                required(request.getOrDefault("title", existing.get("title")), "文章题目不能为空"),
                text(request.getOrDefault("major", existing.get("major"))),
                textOr(request.getOrDefault("documentType", existing.get("document_type")), "纯文字稿"),
                clampTargetWords(WritingJdbc.integer(request.get("targetWordCount"), WritingJdbc.integer(existing.get("target_word_count"), 8000))),
                WritingJdbc.integer(request.get("abstractWordCount"), WritingJdbc.integer(existing.get("abstract_word_count"), 300)),
                WritingJdbc.integer(request.get("keywordCount"), WritingJdbc.integer(existing.get("keyword_count"), 4)), 0,
                0, 0,
                WritingJdbc.integer(request.get("yearStart"), WritingJdbc.integer(existing.get("year_start"), 2020)),
                WritingJdbc.integer(request.get("yearEnd"), WritingJdbc.integer(existing.get("year_end"), 2026)),
                textOr(request.getOrDefault("citationStyle", existing.get("citation_style")), "GB/T 7714"),
                textOr(request.getOrDefault("writingTone", existing.get("writing_tone")), "本科论文"),
                request.containsKey("generateEnglishAbstract") ? bool(request.get("generateEnglishAbstract"), true) : WritingJdbc.bool(existing.get("generate_english_abstract"), true),
                true,
                text(request.getOrDefault("requirements", existing.get("requirements"))), json(keywordValues), LocalDateTime.now(), projectId);
        return detail(projectId);
    }

    @Transactional
    public Map<String, Object> generateOutline(String projectId) {
        Long userId = AuthContext.requireUserId();
        Map<String, Object> project = lockProjectForWrite(userId, projectId);
        jdbcTemplate.update("DELETE FROM writing_chart_series WHERE chart_id IN (SELECT id FROM writing_chart WHERE project_id=?)", projectId);
        jdbcTemplate.update("DELETE FROM writing_chart WHERE project_id=?", projectId);
        jdbcTemplate.update("DELETE FROM writing_table WHERE project_id=?", projectId);
        jdbcTemplate.update("DELETE FROM writing_section WHERE project_id=?", projectId);
        jdbcTemplate.update("DELETE FROM writing_chapter WHERE project_id=?", projectId);
        List<Map<String, Object>> outline = aiOutline(project);
        int chapterNo = 1;
        for (Map<String, Object> chapter : outline) {
            addStructuredChapter(projectId, chapter, chapterNo++);
        }
        renumberProject(projectId);
        jdbcTemplate.update("UPDATE writing_project SET status=?, current_stage=?, progress=?, updated_at=? WHERE id=?",
                "OUTLINE_READY", "大纲和原生图表位置已生成，可直接确认", 18, LocalDateTime.now(), projectId);
        return detail(projectId);
    }

    @Transactional
    public Map<String, Object> addChapter(String projectId, Map<String, Object> request) {
        Long userId = AuthContext.requireUserId();
        lockProjectForWrite(userId, projectId);
        addChapterInternal(projectId, request, true);
        renumberProject(projectId);
        return detail(projectId);
    }

    @Transactional
    public Map<String, Object> updateChapter(String projectId, String chapterId, Map<String, Object> request) {
        Long userId = AuthContext.requireUserId();
        lockProjectForWrite(userId, projectId);
        lockChaptersForWrite(projectId);
        ownedChapter(userId, projectId, chapterId);
        int imageCount = WritingJdbc.integer(request.get("imageCount"), 1);
        int tableCount = WritingJdbc.integer(request.get("tableCount"), 1);
        jdbcTemplate.update("""
                UPDATE writing_chapter SET title=?, target_word_count=?, section_count=?, image_count=?, table_count=?,
                use_references=?, default_chart_type=?, updated_at=? WHERE id=?
                """,
                required(request.get("title"), "章节标题不能为空"), WritingJdbc.integer(request.get("targetWordCount"), 1200),
                WritingJdbc.integer(request.get("sectionCount"), 3), imageCount, tableCount,
                bool(request.get("useReferences"), true), textOr(request.get("defaultChartType"), "COMBO"),
                LocalDateTime.now(), chapterId);
        ensureSectionCount(projectId, chapterId, WritingJdbc.integer(request.get("sectionCount"), 3));
        ensureChartTableCounts(projectId, chapterId, imageCount, tableCount);
        renumberProject(projectId);
        return detail(projectId);
    }

    @Transactional
    public Map<String, Object> deleteChapter(String projectId, String chapterId) {
        Long userId = AuthContext.requireUserId();
        lockProjectForWrite(userId, projectId);
        lockChaptersForWrite(projectId);
        ownedChapter(userId, projectId, chapterId);
        jdbcTemplate.update("DELETE FROM writing_chapter WHERE id=?", chapterId);
        renumberProject(projectId);
        return detail(projectId);
    }

    @Transactional
    public Map<String, Object> reorderChapters(String projectId, List<String> ids) {
        Long userId = AuthContext.requireUserId();
        lockProjectForWrite(userId, projectId);
        lockChaptersForWrite(projectId);
        int order = 1;
        for (String id : ids) jdbcTemplate.update("UPDATE writing_chapter SET sort_order=? WHERE project_id=? AND id=?", order++, projectId, id);
        renumberProject(projectId);
        return detail(projectId);
    }

    @Transactional
    public Map<String, Object> addSection(String projectId, String chapterId, Map<String, Object> request) {
        Long userId = AuthContext.requireUserId();
        lockProjectForWrite(userId, projectId);
        ownedChapter(userId, projectId, chapterId);
        String parentId = text(request.get("parentSectionId"));
        int level = parentId.isBlank() ? 2 : 3;
        if (level == 3) {
            Map<String, Object> chapter = WritingJdbc.one(jdbcTemplate, "SELECT chapter_no FROM writing_chapter WHERE id=? AND project_id=?", chapterId, projectId);
            int chapterNo = WritingJdbc.integer(chapter.get("chapter_no"), 0);
            if (chapterNo < 3 || chapterNo > 4) {
                // 第一、二、五、六章允许人工添加三级标题；只是不会由系统默认生成。
                parentId = required(request.get("parentSectionId"), "三级标题必须指定所属二级标题");
            }
        }
        int next = WritingJdbc.integer(WritingJdbc.one(jdbcTemplate,
                parentId.isBlank()
                        ? "SELECT COALESCE(MAX(sort_order),0)+1 AS n FROM writing_section WHERE chapter_id=? AND heading_level=2"
                        : "SELECT COALESCE(MAX(sort_order),0)+1 AS n FROM writing_section WHERE parent_section_id=? AND heading_level=3",
                parentId.isBlank() ? chapterId : parentId).get("n"), 1);
        insertSection(projectId, chapterId, parentId, level, next, textOr(request.get("title"), level == 3 ? "新增三级标题" : "新增二级标题"), WritingJdbc.integer(request.get("targetWordCount"), level == 3 ? 280 : 520));
        if (level == 3 && next == 1) {
            insertSection(projectId, chapterId, parentId, 3, 2, "新增三级标题2", 280);
        }
        enforceThirdLevelRule(projectId, parentId);
        renumberProject(projectId);
        return detail(projectId);
    }

    @Transactional
    public Map<String, Object> updateSection(String projectId, String sectionId, Map<String, Object> request) {
        Long userId = AuthContext.requireUserId();
        lockProjectForWrite(userId, projectId);
        ownedProject(userId, projectId);
        jdbcTemplate.update("UPDATE writing_section SET title=?, target_word_count=?, updated_at=? WHERE project_id=? AND id=?",
                required(request.get("title"), "小节标题不能为空"), WritingJdbc.integer(request.get("targetWordCount"), 400),
                LocalDateTime.now(), projectId, sectionId);
        return detail(projectId);
    }

    @Transactional
    public Map<String, Object> deleteSection(String projectId, String sectionId) {
        Long userId = AuthContext.requireUserId();
        lockProjectForWrite(userId, projectId);
        ownedProject(userId, projectId);
        Map<String, Object> section = WritingJdbc.one(jdbcTemplate, "SELECT parent_section_id,heading_level FROM writing_section WHERE project_id=? AND id=?", projectId, sectionId);
        String parentId = WritingJdbc.text(section.get("parent_section_id"));
        if (WritingJdbc.integer(section.get("heading_level"), 2) == 3 && !parentId.isBlank()) {
            int siblings = WritingJdbc.integer(WritingJdbc.one(jdbcTemplate,
                    "SELECT COUNT(*) AS n FROM writing_section WHERE project_id=? AND parent_section_id=?", projectId, parentId).get("n"), 0);
            if (siblings == 2) jdbcTemplate.update("DELETE FROM writing_section WHERE project_id=? AND parent_section_id=?", projectId, parentId);
            else jdbcTemplate.update("DELETE FROM writing_section WHERE project_id=? AND id=?", projectId, sectionId);
        } else {
            jdbcTemplate.update("DELETE FROM writing_section WHERE project_id=? AND (id=? OR parent_section_id=?)", projectId, sectionId, sectionId);
        }
        enforceThirdLevelRule(projectId, parentId);
        renumberProject(projectId);
        return detail(projectId);
    }

    @Transactional
    public Map<String, Object> reorderSections(String projectId, List<String> ids) {
        Long userId = AuthContext.requireUserId();
        ownedProject(userId, projectId);
        int order = 1;
        for (String id : ids) jdbcTemplate.update("UPDATE writing_section SET sort_order=? WHERE project_id=? AND id=?", order++, projectId, id);
        renumberProject(projectId);
        return detail(projectId);
    }

    @Transactional
    public Map<String, Object> addChart(String projectId, String chapterId, Map<String, Object> request) {
        Long userId = AuthContext.requireUserId();
        lockProjectForWrite(userId, projectId);
        ownedChapter(userId, projectId, chapterId);
        int next = WritingJdbc.integer(WritingJdbc.one(jdbcTemplate, "SELECT COALESCE(MAX(sort_order),0)+1 AS n FROM writing_chart WHERE chapter_id=?", chapterId).get("n"), 1);
        String chartId = insertChart(projectId, chapterId, next, textOr(request.get("chartType"), "BAR"), text(request.get("title")));
        String after = text(request.get("insertAfterSection"));
        if (!after.isBlank()) bindChart(projectId, chartId, after);
        renumberProject(projectId);
        return detail(projectId);
    }

    @Transactional
    public Map<String, Object> updateChart(String projectId, String chartId, Map<String, Object> request) {
        Long userId = AuthContext.requireUserId();
        lockProjectForWrite(userId, projectId);
        ownedProject(userId, projectId);
        jdbcTemplate.update("""
                UPDATE writing_chart SET title=?, chart_type=?, source_type=?, source_name=?, source_url=?, is_simulated=?,
                show_legend=?, show_data_label=?, show_axis_title=?, use_secondary_axis=?, stacked=?, show_trendline=?,
                description=?, insert_after_section=?, updated_at=? WHERE project_id=? AND id=?
                """,
                required(request.get("title"), "图表标题不能为空"), textOr(request.get("chartType"), "COMBO"),
                textOr(request.get("sourceType"), "SIMULATED"), text(request.get("sourceName")), text(request.get("sourceUrl")),
                bool(request.get("isSimulated"), true), bool(request.get("showLegend"), true), bool(request.get("showDataLabel"), false),
                bool(request.get("showAxisTitle"), true), bool(request.get("useSecondaryAxis"), false),
                bool(request.get("stacked"), false), bool(request.get("showTrendline"), false),
                text(request.get("description")), text(request.get("insertAfterSection")), LocalDateTime.now(), projectId, chartId);
        return detail(projectId);
    }

    @Transactional
    public Map<String, Object> deleteChart(String projectId, String chartId) {
        Long userId = AuthContext.requireUserId();
        ownedProject(userId, projectId);
        jdbcTemplate.update("DELETE FROM writing_chart WHERE project_id=? AND id=?", projectId, chartId);
        renumberProject(projectId);
        return detail(projectId);
    }

    @Transactional
    public Map<String, Object> addSeries(String projectId, String chartId, Map<String, Object> request) {
        Long userId = AuthContext.requireUserId();
        ownedProject(userId, projectId);
        int next = WritingJdbc.integer(WritingJdbc.one(jdbcTemplate, "SELECT COALESCE(MAX(sort_order),0)+1 AS n FROM writing_chart_series WHERE chart_id=?", chartId).get("n"), 1);
        insertSeries(chartId, next, textOr(request.get("seriesName"), "系列" + next), textOr(request.get("chartType"), "BAR"), bool(request.get("useSecondaryAxis"), false), text(request.get("unit")));
        return detail(projectId);
    }

    @Transactional
    public Map<String, Object> updateSeries(String projectId, String seriesId, Map<String, Object> request) {
        Long userId = AuthContext.requireUserId();
        ownedProject(userId, projectId);
        jdbcTemplate.update("UPDATE writing_chart_series SET series_name=?, chart_type=?, use_secondary_axis=?, unit=?, source_type=?, data_json=?, updated_at=? WHERE id=?",
                required(request.get("seriesName"), "系列名称不能为空"), textOr(request.get("chartType"), "BAR"),
                bool(request.get("useSecondaryAxis"), false), text(request.get("unit")), textOr(request.get("sourceType"), "SIMULATED"),
                text(request.get("dataJson")), LocalDateTime.now(), seriesId);
        return detail(projectId);
    }

    @Transactional
    public Map<String, Object> deleteSeries(String projectId, String seriesId) {
        Long userId = AuthContext.requireUserId();
        ownedProject(userId, projectId);
        jdbcTemplate.update("DELETE FROM writing_chart_series WHERE id=?", seriesId);
        return detail(projectId);
    }

    @Transactional
    public Map<String, Object> addTable(String projectId, String chapterId, Map<String, Object> request) {
        Long userId = AuthContext.requireUserId();
        lockProjectForWrite(userId, projectId);
        ownedChapter(userId, projectId, chapterId);
        int next = WritingJdbc.integer(WritingJdbc.one(jdbcTemplate, "SELECT COALESCE(MAX(sort_order),0)+1 AS n FROM writing_table WHERE chapter_id=?", chapterId).get("n"), 1);
        String tableId = insertTable(projectId, chapterId, next, text(request.get("title")));
        String after = text(request.get("insertAfterSection"));
        if (!after.isBlank()) bindTable(projectId, tableId, after);
        renumberProject(projectId);
        return detail(projectId);
    }

    @Transactional
    public Map<String, Object> updateTable(String projectId, String tableId, Map<String, Object> request) {
        Long userId = AuthContext.requireUserId();
        lockProjectForWrite(userId, projectId);
        ownedProject(userId, projectId);
        jdbcTemplate.update("""
                UPDATE writing_table SET title=?, table_type=?, source_type=?, source_name=?, source_url=?, is_simulated=?,
                header_json=?, rows_json=?, use_three_line_style=?, note=?, insert_after_section=?, updated_at=?
                WHERE project_id=? AND id=?
                """,
                required(request.get("title"), "表格标题不能为空"), textOr(request.get("tableType"), "INDICATOR_STAT"),
                textOr(request.get("sourceType"), "SIMULATED"), text(request.get("sourceName")), text(request.get("sourceUrl")),
                bool(request.get("isSimulated"), true), text(request.get("headerJson")), text(request.get("rowsJson")),
                bool(request.get("useThreeLineStyle"), true), text(request.get("note")), text(request.get("insertAfterSection")),
                LocalDateTime.now(), projectId, tableId);
        return detail(projectId);
    }

    @Transactional
    public Map<String, Object> deleteTable(String projectId, String tableId) {
        Long userId = AuthContext.requireUserId();
        ownedProject(userId, projectId);
        jdbcTemplate.update("DELETE FROM writing_table WHERE project_id=? AND id=?", projectId, tableId);
        renumberProject(projectId);
        return detail(projectId);
    }

    private void addChapterInternal(String projectId, Map<String, Object> request, boolean renumber) {
        int next = WritingJdbc.integer(WritingJdbc.one(jdbcTemplate, "SELECT COALESCE(MAX(sort_order),0)+1 AS n FROM writing_chapter WHERE project_id=?", projectId).get("n"), 1);
        String id = WritingJdbc.id("wc");
        int imageCount = WritingJdbc.integer(request.get("imageCount"), 1);
        int tableCount = WritingJdbc.integer(request.get("tableCount"), 1);
        LocalDateTime now = LocalDateTime.now();
        jdbcTemplate.update("""
                INSERT INTO writing_chapter (id, project_id, chapter_no, title, target_word_count, section_count,
                image_count, table_count, use_references, default_chart_type, status, sort_order, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, id, projectId, next, textOr(request.get("title"), "新增章节"), WritingJdbc.integer(request.get("targetWordCount"), 1200),
                WritingJdbc.integer(request.get("sectionCount"), 3), imageCount, tableCount, bool(request.get("useReferences"), true),
                textOr(request.get("defaultChartType"), "COMBO"), "DRAFT", next, now, now);
        ensureSectionCount(projectId, id, WritingJdbc.integer(request.get("sectionCount"), 3));
        ensureChartTableCounts(projectId, id, imageCount, tableCount);
        if (renumber) renumberProject(projectId);
    }

    private void ensureSectionCount(String projectId, String chapterId, int count) {
        List<Map<String, Object>> existing = WritingJdbc.list(jdbcTemplate, "SELECT * FROM writing_section WHERE chapter_id=? AND heading_level=2 ORDER BY sort_order", chapterId);
        for (int i = existing.size() + 1; i <= count; i++) {
            insertSection(projectId, chapterId, "", 2, i, defaultSectionTitle(i), 520);
        }
    }

    private void ensureChartTableCounts(String projectId, String chapterId, int imageCount, int tableCount) {
        List<Map<String, Object>> charts = WritingJdbc.list(jdbcTemplate, "SELECT * FROM writing_chart WHERE chapter_id=? ORDER BY sort_order", chapterId);
        for (int i = charts.size() + 1; i <= imageCount; i++) insertChart(projectId, chapterId, i, "COMBO", "");
        for (int i = imageCount; i < charts.size(); i++) {
            jdbcTemplate.update("DELETE FROM writing_chart WHERE id=?", charts.get(i).get("id"));
        }
        List<Map<String, Object>> tables = WritingJdbc.list(jdbcTemplate, "SELECT * FROM writing_table WHERE chapter_id=? ORDER BY sort_order", chapterId);
        for (int i = tables.size() + 1; i <= tableCount; i++) insertTable(projectId, chapterId, i, "");
        for (int i = tableCount; i < tables.size(); i++) {
            jdbcTemplate.update("DELETE FROM writing_table WHERE id=?", tables.get(i).get("id"));
        }
    }

    private void insertSection(String projectId, String chapterId, String parentSectionId, int level, int order, String title, int words) {
        LocalDateTime now = LocalDateTime.now();
        String sectionId = WritingJdbc.id("ws");
        jdbcTemplate.update("""
                INSERT INTO writing_section (id, project_id, chapter_id, parent_section_id, heading_level, section_no, title, target_word_count, sort_order, status, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, sectionId, projectId, chapterId, parentSectionId == null || parentSectionId.isBlank() ? null : parentSectionId,
                level, sectionId, title, words, order, "DRAFT", now, now);
    }

    private String insertChart(String projectId, String chapterId, int order, String type, String title) {
        LocalDateTime now = LocalDateTime.now();
        String chartId = WritingJdbc.id("wch");
        jdbcTemplate.update("""
                INSERT INTO writing_chart (id, project_id, chapter_id, chart_no, title, chart_type, source_type,
                is_simulated, x_axis_name, y_axis_name, secondary_y_axis_name, chart_config_json, data_json,
                description, sort_order, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, chartId, projectId, chapterId, chapterId + "-" + order, title.isBlank() ? "各章二级标题分布" : title,
                type, "MANUSCRIPT_STRUCTURE", false, "章节", "二级标题数", "", "{}", "", "数据由本稿大纲和正文统计。", order, now, now);
        insertSeries(chartId, 1, "二级标题数", type.contains("LINE") ? "LINE" : "BAR", false, "个");
        return chartId;
    }

    private void insertSeries(String chartId, int order, String name, String type, boolean secondary, String unit) {
        LocalDateTime now = LocalDateTime.now();
        jdbcTemplate.update("""
                INSERT INTO writing_chart_series (id, chart_id, series_name, chart_type, use_secondary_axis, unit, source_type, data_json, sort_order, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, WritingJdbc.id("wcs"), chartId, name, type, secondary, unit, "MANUSCRIPT_STRUCTURE", "", order, now, now);
    }

    private String insertTable(String projectId, String chapterId, int order, String title) {
        LocalDateTime now = LocalDateTime.now();
        String tableId = WritingJdbc.id("wt");
        jdbcTemplate.update("""
                INSERT INTO writing_table (id, project_id, chapter_id, table_no, title, table_type, source_type, is_simulated,
                header_json, rows_json, use_three_line_style, note, sort_order, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, tableId, projectId, chapterId, chapterId + "-" + order, title.isBlank() ? "研究资料与分析依据" : title,
                "MANUSCRIPT_SUMMARY", "MANUSCRIPT_STRUCTURE", false, "[\"节号\",\"小节主题\",\"正文要点摘录\"]", "[]", true,
                "根据本稿正文整理，非外部统计数据。", order, now, now);
        return tableId;
    }

    private void renumberProject(String projectId) {
        List<Map<String, Object>> chapters = WritingJdbc.list(jdbcTemplate, "SELECT * FROM writing_chapter WHERE project_id=? ORDER BY sort_order, created_at, id", projectId);
        int temp = 1;
        for (Map<String, Object> chapter : chapters.stream().sorted((a, b) -> WritingJdbc.text(a.get("id")).compareTo(WritingJdbc.text(b.get("id")))).toList()) {
            jdbcTemplate.update("UPDATE writing_chapter SET chapter_no=?, sort_order=?, updated_at=? WHERE id=?",
                    -(1000000 + temp), -(1000000 + temp), LocalDateTime.now(), chapter.get("id"));
            temp++;
        }
        int chapterNo = 1;
        for (Map<String, Object> chapter : chapters) {
            String chapterId = WritingJdbc.text(chapter.get("id"));
            jdbcTemplate.update("UPDATE writing_chapter SET chapter_no=?, sort_order=?, updated_at=? WHERE id=?", chapterNo, chapterNo, LocalDateTime.now(), chapterId);
            int sectionNo = 1;
            List<Map<String, Object>> sections = WritingJdbc.list(jdbcTemplate, "SELECT * FROM writing_section WHERE chapter_id=? AND heading_level=2 ORDER BY sort_order, created_at, id", chapterId);
            int sectionTemp = 1;
            List<Map<String, Object>> allSections = WritingJdbc.list(jdbcTemplate,
                    "SELECT * FROM writing_section WHERE chapter_id=? ORDER BY heading_level,sort_order,created_at,id", chapterId);
            for (Map<String, Object> section : allSections.stream().sorted((a, b) -> WritingJdbc.text(a.get("id")).compareTo(WritingJdbc.text(b.get("id")))).toList()) {
                jdbcTemplate.update("UPDATE writing_section SET section_no=?, sort_order=?, updated_at=? WHERE id=?",
                        "tmp-" + sectionTemp, -(1000000 + sectionTemp), LocalDateTime.now(), section.get("id"));
                sectionTemp++;
            }
            for (Map<String, Object> section : sections) {
                jdbcTemplate.update("UPDATE writing_section SET section_no=?, sort_order=?, updated_at=? WHERE id=?",
                        chapterNo + "." + sectionNo, sectionNo, LocalDateTime.now(), section.get("id"));
                List<Map<String, Object>> children = allSections.stream()
                        .filter(item -> WritingJdbc.integer(item.get("heading_level"), 2) == 3
                                && WritingJdbc.text(item.get("parent_section_id")).equals(WritingJdbc.text(section.get("id"))))
                        .sorted((a, b) -> {
                            int byOrder = Integer.compare(WritingJdbc.integer(a.get("sort_order"), 0), WritingJdbc.integer(b.get("sort_order"), 0));
                            return byOrder != 0 ? byOrder : WritingJdbc.text(a.get("id")).compareTo(WritingJdbc.text(b.get("id")));
                        }).toList();
                int childNo = 1;
                for (Map<String, Object> child : children) {
                    jdbcTemplate.update("UPDATE writing_section SET section_no=?, sort_order=?, updated_at=? WHERE id=?",
                            chapterNo + "." + sectionNo + "." + childNo, childNo, LocalDateTime.now(), child.get("id"));
                    childNo++;
                }
                sectionNo++;
            }
            int chartNo = 1;
            List<Map<String, Object>> charts = WritingJdbc.list(jdbcTemplate, "SELECT * FROM writing_chart WHERE chapter_id=? ORDER BY sort_order, created_at, id", chapterId);
            int chartTemp = 1;
            for (Map<String, Object> chart : charts.stream().sorted((a, b) -> WritingJdbc.text(a.get("id")).compareTo(WritingJdbc.text(b.get("id")))).toList()) {
                jdbcTemplate.update("UPDATE writing_chart SET chart_no=?, sort_order=?, updated_at=? WHERE id=?",
                        "tmp-" + chartTemp, -(1000000 + chartTemp), LocalDateTime.now(), chart.get("id"));
                chartTemp++;
            }
            for (Map<String, Object> chart : charts) {
                jdbcTemplate.update("UPDATE writing_chart SET chart_no=?, sort_order=?, updated_at=? WHERE id=?",
                        chapterNo + "-" + chartNo, chartNo, LocalDateTime.now(), chart.get("id"));
                chartNo++;
            }
            int tableNo = 1;
            List<Map<String, Object>> tables = WritingJdbc.list(jdbcTemplate, "SELECT * FROM writing_table WHERE chapter_id=? ORDER BY sort_order, created_at, id", chapterId);
            int tableTemp = 1;
            for (Map<String, Object> table : tables.stream().sorted((a, b) -> WritingJdbc.text(a.get("id")).compareTo(WritingJdbc.text(b.get("id")))).toList()) {
                jdbcTemplate.update("UPDATE writing_table SET table_no=?, sort_order=?, updated_at=? WHERE id=?",
                        "tmp-" + tableTemp, -(1000000 + tableTemp), LocalDateTime.now(), table.get("id"));
                tableTemp++;
            }
            for (Map<String, Object> table : tables) {
                jdbcTemplate.update("UPDATE writing_table SET table_no=?, sort_order=?, updated_at=? WHERE id=?",
                        chapterNo + "-" + tableNo, tableNo, LocalDateTime.now(), table.get("id"));
                tableNo++;
            }
            chapterNo++;
        }
        jdbcTemplate.update("UPDATE writing_project SET chapter_count=?, updated_at=? WHERE id=?", chapters.size(), LocalDateTime.now(), projectId);
    }

    private List<Map<String, Object>> chapters(String projectId) {
        List<Map<String, Object>> chapters = new ArrayList<>();
        for (Map<String, Object> chapter : WritingJdbc.list(jdbcTemplate, "SELECT * FROM writing_chapter WHERE project_id=? ORDER BY sort_order", projectId)) {
            String chapterId = WritingJdbc.text(chapter.get("id"));
            List<Map<String, Object>> sections = WritingJdbc.list(jdbcTemplate,
                    "SELECT * FROM writing_section WHERE chapter_id=? AND heading_level=2 ORDER BY sort_order", chapterId);
            for (Map<String, Object> section : sections) {
                section.put("children", WritingJdbc.list(jdbcTemplate,
                        "SELECT * FROM writing_section WHERE parent_section_id=? AND heading_level=3 ORDER BY sort_order", section.get("id")));
            }
            chapter.put("sections", sections);
            List<Map<String, Object>> charts = WritingJdbc.list(jdbcTemplate, "SELECT * FROM writing_chart WHERE chapter_id=? ORDER BY sort_order", chapterId);
            for (Map<String, Object> chart : charts) {
                chart.put("series", WritingJdbc.list(jdbcTemplate, "SELECT * FROM writing_chart_series WHERE chart_id=? ORDER BY sort_order", chart.get("id")));
            }
            chapter.put("charts", charts);
            chapter.put("tables", WritingJdbc.list(jdbcTemplate, "SELECT * FROM writing_table WHERE chapter_id=? ORDER BY sort_order", chapterId));
            chapters.add(chapter);
        }
        return chapters;
    }

    private Map<String, Object> ownedProject(Long userId, String projectId) {
        return WritingJdbc.one(jdbcTemplate, "SELECT id FROM writing_project WHERE id=? AND user_id=?", projectId, userId);
    }

    private Map<String, Object> lockProjectForWrite(Long userId, String projectId) {
        try {
            return WritingJdbc.one(jdbcTemplate, "SELECT * FROM writing_project WHERE id=? AND user_id=? FOR UPDATE", projectId, userId);
        } catch (Exception exception) {
            return WritingJdbc.one(jdbcTemplate, "SELECT * FROM writing_project WHERE id=? AND user_id=?", projectId, userId);
        }
    }

    private void lockChaptersForWrite(String projectId) {
        try {
            WritingJdbc.list(jdbcTemplate, "SELECT id FROM writing_chapter WHERE project_id=? ORDER BY id ASC FOR UPDATE", projectId);
        } catch (Exception exception) {
            WritingJdbc.list(jdbcTemplate, "SELECT id FROM writing_chapter WHERE project_id=? ORDER BY id ASC", projectId);
        }
    }

    private Map<String, Object> ownedChapter(Long userId, String projectId, String chapterId) {
        ownedProject(userId, projectId);
        return WritingJdbc.one(jdbcTemplate, "SELECT id FROM writing_chapter WHERE id=? AND project_id=?", chapterId, projectId);
    }

    private void addStructuredChapter(String projectId, Map<String, Object> request, int chapterNo) {
        String chapterId = WritingJdbc.id("wc");
        LocalDateTime now = LocalDateTime.now();
        List<Map<String, Object>> sections = readMapList(request.get("sections"));
        int target = WritingJdbc.integer(request.get("targetWordCount"), chapterNo == 6 ? 700 : 1350);
        jdbcTemplate.update("""
                INSERT INTO writing_chapter (id, project_id, chapter_no, title, target_word_count, section_count,
                image_count, table_count, use_references, default_chart_type, status, sort_order, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, 0, 0, ?, 'BAR', 'DRAFT', ?, ?, ?)
                """, chapterId, projectId, chapterNo, textOr(request.get("title"), "章节" + chapterNo), target,
                sections.size(), chapterNo <= 2, chapterNo, now, now);
        int sectionOrder = 1;
        List<String> secondLevelIds = new ArrayList<>();
        for (Map<String, Object> section : sections) {
            String sectionId = WritingJdbc.id("ws");
            String sectionTitle = textOr(section.get("title"), defaultSectionTitle(sectionOrder));
            jdbcTemplate.update("""
                    INSERT INTO writing_section (id, project_id, chapter_id, parent_section_id, heading_level, section_no,
                    title, target_word_count, sort_order, status, created_at, updated_at)
                    VALUES (?, ?, ?, NULL, 2, ?, ?, ?, ?, 'DRAFT', ?, ?)
                    """, sectionId, projectId, chapterId, chapterNo + "." + sectionOrder, sectionTitle,
                    WritingJdbc.integer(section.get("targetWordCount"), 560), sectionOrder, now, now);
            secondLevelIds.add(sectionId);
            List<String> children = readStringList(section.get("children"));
            if (chapterNo < 3 || chapterNo > 4 || children.size() == 1) children = List.of();
            int childOrder = 1;
            for (String child : children.stream().limit(4).toList()) {
                insertSection(projectId, chapterId, sectionId, 3, childOrder++, child, 300);
            }
            sectionOrder++;
        }
        applyBaselineVisualPlan(projectId, chapterId, chapterNo, secondLevelIds);
    }

    private void applyBaselineVisualPlan(String projectId, String chapterId, int chapterNo, List<String> sections) {
        if (sections.isEmpty()) return;
        if (chapterNo == 1) {
            bindChart(projectId, insertChart(projectId, chapterId, 1, "LINE", "各章正文篇幅分布"), sections.get(0));
        } else if (chapterNo == 2) {
            bindTable(projectId, insertTable(projectId, chapterId, 1, "核心概念、理论与判断标准"), sections.get(Math.min(1, sections.size() - 1)));
            bindChart(projectId, insertChart(projectId, chapterId, 1, "BAR", "各章二级标题分布"), sections.get(sections.size() - 1));
        } else if (chapterNo == 3) {
            bindTable(projectId, insertTable(projectId, chapterId, 1, "公开资料与问题识别依据"), sections.get(0));
        } else if (chapterNo == 4) {
            bindTable(projectId, insertTable(projectId, chapterId, 1, "优化策略与实施动作对应表"), sections.get(Math.min(1, sections.size() - 1)));
            bindTable(projectId, insertTable(projectId, chapterId, 2, "问题、措施与评价指标对应表"), sections.get(sections.size() - 1));
        } else if (chapterNo == 5) {
            bindTable(projectId, insertTable(projectId, chapterId, 1, "实施记录字段及用途"), sections.get(0));
            bindChart(projectId, insertChart(projectId, chapterId, 1, "PIE", "各章小节构成"), sections.get(Math.min(1, sections.size() - 1)));
        }
    }

    private void bindChart(String projectId, String chartId, String sectionId) {
        jdbcTemplate.update("UPDATE writing_chart SET insert_after_section=? WHERE project_id=? AND id=?", sectionId, projectId, chartId);
    }

    private void bindTable(String projectId, String tableId, String sectionId) {
        jdbcTemplate.update("UPDATE writing_table SET insert_after_section=? WHERE project_id=? AND id=?", sectionId, projectId, tableId);
    }

    private List<Map<String, Object>> aiOutline(Map<String, Object> project) {
        if (!doubao.configured()) throw new IllegalStateException("豆包未配置，请检查桌面 .env 中的 DOUBAO_API_KEY");
        try {
            String response = doubao.complete("""
                    你是中文本科纯文字论文的大纲设计器。参考“抖音电商短视频封面优化方案”的六章论证模式，
                    但必须围绕用户题目重新命名，禁止照抄例文主题。只返回JSON数组，不要Markdown。
                    每项格式：{\"title\":\"章标题\",\"sections\":[{\"title\":\"二级标题\",\"children\":[\"三级标题\"]}]}。
                    固定六章逻辑：绪论、理论与框架、现状与问题、优化方案、实施验证、结论。
                    每章2至4个二级标题。一级到三级为最大层级。
                    第一、二、五、六章children必须为空数组；第三、第四章的children可以为空，也可以是2至4项，绝不能只有1项。
                    标题简洁、学术化、可直接用于本科论文目录，不出现“待补充”等编辑说明。
                    """, "论文题目：" + project.get("title") + "\n目标字数：8000字左右", 5000);
            List<Map<String, Object>> parsed = objectMapper.readValue(jsonOnly(response),
                    objectMapper.getTypeFactory().constructCollectionType(List.class, Map.class));
            if (parsed.size() != 6) throw new IllegalStateException("豆包大纲未返回六章结构");
            for (int i = 0; i < parsed.size(); i++) {
                List<Map<String, Object>> sections = readMapList(parsed.get(i).get("sections"));
                if (sections.size() < 2 || sections.size() > 4) throw new IllegalStateException("豆包大纲中有章节缺少有效的二级标题");
                for (Map<String, Object> section : sections) {
                    List<String> children = readStringList(section.get("children"));
                    if ((i < 2 || i > 3) && !children.isEmpty()) section.put("children", List.of());
                    else if (children.size() == 1) section.put("children", List.of());
                }
            }
            return parsed;
        } catch (Exception exception) {
            throw new IllegalStateException("豆包大纲生成失败：" + exception.getMessage(), exception);
        }
    }

    private List<Map<String, Object>> defaultChapters(String title, int target) {
        int each = Math.max(900, (clampTargetWords(target) - 800) / 5);
        return List.of(
                chapterSpec("绪论", each, List.of(
                        sectionSpec("研究背景与意义"), sectionSpec("国内外研究现状"), sectionSpec("研究对象、资料来源与方法"))),
                chapterSpec("理论基础与方案设计框架", each, List.of(
                        sectionSpec("核心概念与指标口径"), sectionSpec("相关理论基础"), sectionSpec("分析框架与研究思路"))),
                chapterSpec("现状分析与问题识别", each, List.of(
                        sectionSpec("资料选择与分析规则"), sectionSpec("主要现状", List.of("对象与场景特征", "运行或表现特征")),
                        sectionSpec("突出问题", List.of("表层问题表现", "深层约束因素", "问题影响")))),
                chapterSpec("优化方案与实施路径", each, List.of(
                        sectionSpec("总体设计原则", List.of("目标与原则", "方案构成")),
                        sectionSpec("核心优化方案", List.of("关键措施一", "关键措施二", "关键措施三")),
                        sectionSpec("流程与协同机制"), sectionSpec("风险控制与适用边界"))),
                chapterSpec("方案实施、验证与迭代", each, List.of(
                        sectionSpec("实施条件与数据字段"), sectionSpec("验证方法与评价指标"), sectionSpec("结果复盘与持续迭代"))),
                chapterSpec("结论", 700, List.of(sectionSpec("研究结论"), sectionSpec("研究不足与展望")))
        );
    }

    private Map<String, Object> chapterSpec(String title, int words, List<Map<String, Object>> sections) {
        return Map.of("title", title, "targetWordCount", words, "sections", sections);
    }

    private Map<String, Object> sectionSpec(String title) {
        return sectionSpec(title, List.of());
    }

    private Map<String, Object> sectionSpec(String title, List<String> children) {
        return Map.of("title", title, "children", children);
    }

    private void enforceThirdLevelRule(String projectId, String parentId) {
        if (parentId == null || parentId.isBlank()) return;
        int count = WritingJdbc.integer(WritingJdbc.one(jdbcTemplate,
                "SELECT COUNT(*) AS n FROM writing_section WHERE project_id=? AND parent_section_id=?", projectId, parentId).get("n"), 0);
        if (count == 1) throw new IllegalArgumentException("每个二级标题下的三级标题必须为0个或至少2个");
    }

    private int clampTargetWords(int value) {
        return Math.max(6000, Math.min(9000, value <= 0 ? 8000 : value));
    }

    private String jsonOnly(String value) {
        if (value == null) return "[]";
        String clean = value.trim().replaceFirst("^```(?:json)?\\s*", "").replaceFirst("\\s*```$", "");
        int start = clean.indexOf('[');
        int end = clean.lastIndexOf(']');
        return start >= 0 && end > start ? clean.substring(start, end + 1) : clean;
    }

    private String defaultSectionTitle(int order) {
        return switch (order) {
            case 1 -> "研究背景";
            case 2 -> "核心问题";
            case 3 -> "分析方法";
            case 4 -> "实施路径";
            default -> "小节" + order;
        };
    }

    private List<String> inferKeywords(String title, int count) {
        List<String> keywords = new ArrayList<>();
        for (String part : title.split("[\\s，,、：:]+")) if (part.length() >= 2) keywords.add(part);
        for (String fallback : List.of("能力提升", "路径研究", "现状分析", "优化策略", "保障机制")) {
            if (keywords.size() >= count) break;
            keywords.add(fallback);
        }
        return keywords.stream().distinct().limit(count).toList();
    }

    private List<String> readStringList(Object value) {
        try {
            if (value instanceof List<?> list) return list.stream().map(String::valueOf).filter(s -> !s.isBlank()).toList();
            if (value instanceof String text && !text.isBlank()) return List.of(text.split("[,，、\\s]+"));
        } catch (Exception ignored) {
        }
        return List.of();
    }

    private List<String> readJsonStringList(String value) {
        try {
            if (value == null || value.isBlank()) return List.of();
            return objectMapper.readValue(value, objectMapper.getTypeFactory().constructCollectionType(List.class, String.class));
        } catch (Exception ignored) {
            return readStringList(value);
        }
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> readMapList(Object value) {
        if (value instanceof List<?> list) return (List<Map<String, Object>>) list;
        return List.of();
    }

    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception exception) {
            return "[]";
        }
    }

    private String required(Object value, String message) {
        String text = text(value).trim();
        if (text.isBlank()) throw new IllegalArgumentException(message);
        return text;
    }

    private String text(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    private String textOr(Object value, String fallback) {
        String text = text(value);
        return text.isBlank() ? fallback : text;
    }

    private boolean bool(Object value, boolean fallback) {
        return WritingJdbc.bool(value, fallback);
    }
}
