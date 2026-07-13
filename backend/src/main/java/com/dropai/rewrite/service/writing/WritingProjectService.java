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

    public WritingProjectService(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public Map<String, Object> create(Map<String, Object> request) {
        Long userId = AuthContext.requireUserId();
        String title = required(request.get("title"), "文章题目不能为空");
        LocalDateTime now = LocalDateTime.now();
        String projectId = WritingJdbc.id("wp");
        int target = WritingJdbc.integer(request.get("targetWordCount"), 8000);
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
                target, abstractWords, keywordCount, 0, WritingJdbc.integer(request.get("referenceCount"), 20),
                WritingJdbc.integer(request.get("chineseReferenceCount"), 8), WritingJdbc.integer(request.get("englishReferenceCount"), 12),
                WritingJdbc.integer(request.get("yearStart"), 2020), WritingJdbc.integer(request.get("yearEnd"), 2026),
                textOr(request.get("citationStyle"), "GB/T 7714"), textOr(request.get("writingTone"), "本科论文"),
                bool(request.get("generateEnglishAbstract"), true), bool(request.get("generateToc"), true),
                bool(request.get("generateFigureList"), true), bool(request.get("generateTableList"), true),
                bool(request.get("skipReferences"), false), text(request.get("requirements")), json(keywords),
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
        WritingJdbc.one(jdbcTemplate, "SELECT id FROM writing_project WHERE id=? AND user_id=?", projectId, userId);
        jdbcTemplate.update("""
                UPDATE writing_project SET title=?, major=?, document_type=?, target_word_count=?, abstract_word_count=?,
                keyword_count=?, reference_count=?, chinese_reference_count=?, english_reference_count=?, year_start=?,
                year_end=?, citation_style=?, writing_tone=?, generate_english_abstract=?, skip_references=?,
                requirements=?, keywords_json=?, updated_at=? WHERE id=?
                """,
                required(request.get("title"), "文章题目不能为空"), text(request.get("major")), text(request.get("documentType")),
                WritingJdbc.integer(request.get("targetWordCount"), 8000), WritingJdbc.integer(request.get("abstractWordCount"), 300),
                WritingJdbc.integer(request.get("keywordCount"), 4), WritingJdbc.integer(request.get("referenceCount"), 20),
                WritingJdbc.integer(request.get("chineseReferenceCount"), 8), WritingJdbc.integer(request.get("englishReferenceCount"), 12),
                WritingJdbc.integer(request.get("yearStart"), 2020), WritingJdbc.integer(request.get("yearEnd"), 2026),
                textOr(request.get("citationStyle"), "GB/T 7714"), textOr(request.get("writingTone"), "本科论文"),
                bool(request.get("generateEnglishAbstract"), true), bool(request.get("skipReferences"), false),
                text(request.get("requirements")), json(readStringList(request.get("keywords"))), LocalDateTime.now(), projectId);
        return detail(projectId);
    }

    @Transactional
    public Map<String, Object> generateOutline(String projectId) {
        Long userId = AuthContext.requireUserId();
        Map<String, Object> project = WritingJdbc.one(jdbcTemplate,
                "SELECT * FROM writing_project WHERE id=? AND user_id=?", projectId, userId);
        List<Map<String, Object>> chapters = chapters(projectId);
        if (chapters.isEmpty()) {
            for (Map<String, Object> chapter : defaultChapters(WritingJdbc.text(project.get("title")), WritingJdbc.integer(project.get("target_word_count"), 8000))) {
                addChapterInternal(projectId, chapter, false);
            }
        }
        renumberProject(projectId);
        jdbcTemplate.update("UPDATE writing_project SET status=?, current_stage=?, progress=?, updated_at=? WHERE id=?",
                "OUTLINE_READY", "提纲已生成，可编辑章节、小节和图表", 18, LocalDateTime.now(), projectId);
        return detail(projectId);
    }

    @Transactional
    public Map<String, Object> addChapter(String projectId, Map<String, Object> request) {
        Long userId = AuthContext.requireUserId();
        WritingJdbc.one(jdbcTemplate, "SELECT id FROM writing_project WHERE id=? AND user_id=?", projectId, userId);
        addChapterInternal(projectId, request, true);
        renumberProject(projectId);
        return detail(projectId);
    }

    @Transactional
    public Map<String, Object> updateChapter(String projectId, String chapterId, Map<String, Object> request) {
        Long userId = AuthContext.requireUserId();
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
        ownedChapter(userId, projectId, chapterId);
        jdbcTemplate.update("DELETE FROM writing_chapter WHERE id=?", chapterId);
        renumberProject(projectId);
        return detail(projectId);
    }

    @Transactional
    public Map<String, Object> reorderChapters(String projectId, List<String> ids) {
        Long userId = AuthContext.requireUserId();
        WritingJdbc.one(jdbcTemplate, "SELECT id FROM writing_project WHERE id=? AND user_id=?", projectId, userId);
        int order = 1;
        for (String id : ids) jdbcTemplate.update("UPDATE writing_chapter SET sort_order=? WHERE project_id=? AND id=?", order++, projectId, id);
        renumberProject(projectId);
        return detail(projectId);
    }

    @Transactional
    public Map<String, Object> addSection(String projectId, String chapterId, Map<String, Object> request) {
        Long userId = AuthContext.requireUserId();
        ownedChapter(userId, projectId, chapterId);
        int next = WritingJdbc.integer(WritingJdbc.one(jdbcTemplate, "SELECT COALESCE(MAX(sort_order),0)+1 AS n FROM writing_section WHERE chapter_id=?", chapterId).get("n"), 1);
        insertSection(projectId, chapterId, next, textOr(request.get("title"), "新增小节"), WritingJdbc.integer(request.get("targetWordCount"), 400));
        renumberProject(projectId);
        return detail(projectId);
    }

    @Transactional
    public Map<String, Object> updateSection(String projectId, String sectionId, Map<String, Object> request) {
        Long userId = AuthContext.requireUserId();
        ownedProject(userId, projectId);
        jdbcTemplate.update("UPDATE writing_section SET title=?, target_word_count=?, updated_at=? WHERE project_id=? AND id=?",
                required(request.get("title"), "小节标题不能为空"), WritingJdbc.integer(request.get("targetWordCount"), 400),
                LocalDateTime.now(), projectId, sectionId);
        return detail(projectId);
    }

    @Transactional
    public Map<String, Object> deleteSection(String projectId, String sectionId) {
        Long userId = AuthContext.requireUserId();
        ownedProject(userId, projectId);
        jdbcTemplate.update("DELETE FROM writing_section WHERE project_id=? AND id=?", projectId, sectionId);
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
        ownedChapter(userId, projectId, chapterId);
        int next = WritingJdbc.integer(WritingJdbc.one(jdbcTemplate, "SELECT COALESCE(MAX(sort_order),0)+1 AS n FROM writing_chart WHERE chapter_id=?", chapterId).get("n"), 1);
        insertChart(projectId, chapterId, next, textOr(request.get("chartType"), "COMBO"), text(request.get("title")));
        renumberProject(projectId);
        return detail(projectId);
    }

    @Transactional
    public Map<String, Object> updateChart(String projectId, String chartId, Map<String, Object> request) {
        Long userId = AuthContext.requireUserId();
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
        ownedChapter(userId, projectId, chapterId);
        int next = WritingJdbc.integer(WritingJdbc.one(jdbcTemplate, "SELECT COALESCE(MAX(sort_order),0)+1 AS n FROM writing_table WHERE chapter_id=?", chapterId).get("n"), 1);
        insertTable(projectId, chapterId, next, text(request.get("title")));
        renumberProject(projectId);
        return detail(projectId);
    }

    @Transactional
    public Map<String, Object> updateTable(String projectId, String tableId, Map<String, Object> request) {
        Long userId = AuthContext.requireUserId();
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
        List<Map<String, Object>> existing = WritingJdbc.list(jdbcTemplate, "SELECT * FROM writing_section WHERE chapter_id=? ORDER BY sort_order", chapterId);
        for (int i = existing.size() + 1; i <= count; i++) {
            insertSection(projectId, chapterId, i, defaultSectionTitle(i), 400);
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

    private void insertSection(String projectId, String chapterId, int order, String title, int words) {
        LocalDateTime now = LocalDateTime.now();
        jdbcTemplate.update("""
                INSERT INTO writing_section (id, project_id, chapter_id, section_no, title, target_word_count, sort_order, status, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, WritingJdbc.id("ws"), projectId, chapterId, String.valueOf(order), title, words, order, "DRAFT", now, now);
    }

    private void insertChart(String projectId, String chapterId, int order, String type, String title) {
        LocalDateTime now = LocalDateTime.now();
        String chartId = WritingJdbc.id("wch");
        jdbcTemplate.update("""
                INSERT INTO writing_chart (id, project_id, chapter_id, chart_no, title, chart_type, source_type,
                is_simulated, x_axis_name, y_axis_name, secondary_y_axis_name, chart_config_json, data_json,
                description, sort_order, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, chartId, projectId, chapterId, chapterId + "-" + order, title.isBlank() ? "指标变化趋势" : title,
                type, "SIMULATED", true, "类别", "指标值", "增速", "{}", "", "数据根据研究情境构建，仅用于趋势分析。", order, now, now);
        insertSeries(chartId, 1, "规模指标", type.contains("LINE") ? "LINE" : "BAR", false, "%");
        if (type.contains("COMBO") || type.contains("DUAL")) insertSeries(chartId, 2, "变化趋势", "LINE", true, "%");
    }

    private void insertSeries(String chartId, int order, String name, String type, boolean secondary, String unit) {
        LocalDateTime now = LocalDateTime.now();
        jdbcTemplate.update("""
                INSERT INTO writing_chart_series (id, chart_id, series_name, chart_type, use_secondary_axis, unit, source_type, data_json, sort_order, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, WritingJdbc.id("wcs"), chartId, name, type, secondary, unit, "SIMULATED", "", order, now, now);
    }

    private void insertTable(String projectId, String chapterId, int order, String title) {
        LocalDateTime now = LocalDateTime.now();
        jdbcTemplate.update("""
                INSERT INTO writing_table (id, project_id, chapter_id, table_no, title, table_type, source_type, is_simulated,
                header_json, rows_json, use_three_line_style, note, sort_order, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, WritingJdbc.id("wt"), projectId, chapterId, chapterId + "-" + order, title.isBlank() ? "主要指标分析表" : title,
                "INDICATOR_STAT", "SIMULATED", true, "[\"指标\",\"说明\",\"评价\"]", "[]", true, "数据为模拟分析数据。", order, now, now);
    }

    private void renumberProject(String projectId) {
        List<Map<String, Object>> chapters = WritingJdbc.list(jdbcTemplate, "SELECT * FROM writing_chapter WHERE project_id=? ORDER BY sort_order, created_at", projectId);
        int chapterNo = 1;
        for (Map<String, Object> chapter : chapters) {
            String chapterId = WritingJdbc.text(chapter.get("id"));
            jdbcTemplate.update("UPDATE writing_chapter SET chapter_no=?, sort_order=?, updated_at=? WHERE id=?", chapterNo, chapterNo, LocalDateTime.now(), chapterId);
            int sectionNo = 1;
            for (Map<String, Object> section : WritingJdbc.list(jdbcTemplate, "SELECT * FROM writing_section WHERE chapter_id=? ORDER BY sort_order, created_at", chapterId)) {
                jdbcTemplate.update("UPDATE writing_section SET section_no=?, sort_order=?, updated_at=? WHERE id=?",
                        chapterNo + "." + sectionNo, sectionNo, LocalDateTime.now(), section.get("id"));
                sectionNo++;
            }
            int chartNo = 1;
            for (Map<String, Object> chart : WritingJdbc.list(jdbcTemplate, "SELECT * FROM writing_chart WHERE chapter_id=? ORDER BY sort_order, created_at", chapterId)) {
                jdbcTemplate.update("UPDATE writing_chart SET chart_no=?, sort_order=?, updated_at=? WHERE id=?",
                        chapterNo + "-" + chartNo, chartNo, LocalDateTime.now(), chart.get("id"));
                chartNo++;
            }
            int tableNo = 1;
            for (Map<String, Object> table : WritingJdbc.list(jdbcTemplate, "SELECT * FROM writing_table WHERE chapter_id=? ORDER BY sort_order, created_at", chapterId)) {
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
            chapter.put("sections", WritingJdbc.list(jdbcTemplate, "SELECT * FROM writing_section WHERE chapter_id=? ORDER BY sort_order", chapterId));
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

    private Map<String, Object> ownedChapter(Long userId, String projectId, String chapterId) {
        ownedProject(userId, projectId);
        return WritingJdbc.one(jdbcTemplate, "SELECT id FROM writing_chapter WHERE id=? AND project_id=?", chapterId, projectId);
    }

    private List<Map<String, Object>> defaultChapters(String title, int target) {
        int each = Math.max(900, (target - Math.max(300, target / 25)) / 5);
        return List.of(
                Map.of("title", "绪论", "targetWordCount", each, "sectionCount", 3, "imageCount", 1, "tableCount", 1, "defaultChartType", "COMBO"),
                Map.of("title", "理论基础与分析框架", "targetWordCount", each, "sectionCount", 4, "imageCount", 2, "tableCount", 1, "defaultChartType", "DUAL_COMBO"),
                Map.of("title", "现状分析", "targetWordCount", each, "sectionCount", 4, "imageCount", 2, "tableCount", 2, "defaultChartType", "LINE"),
                Map.of("title", "路径设计与策略", "targetWordCount", each, "sectionCount", 4, "imageCount", 1, "tableCount", 2, "defaultChartType", "BAR"),
                Map.of("title", "保障机制与结论", "targetWordCount", each, "sectionCount", 3, "imageCount", 0, "tableCount", 1, "defaultChartType", "RADAR")
        );
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
