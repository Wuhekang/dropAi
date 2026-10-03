package com.dropai.rewrite.service.writing;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Prepares manuscript-derived chart/table datasets. Rendering is deliberately left to
 * DocxExportService so the final file contains editable OOXML charts rather
 * than raster screenshots.
 */
@Service
public class ChartRenderService {
    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public ChartRenderService(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    public void renderProjectCharts(String projectId, Path ignoredRoot) {
        List<Map<String, Object>> chapters = WritingJdbc.list(jdbcTemplate,
                "SELECT id,chapter_no,title FROM writing_chapter WHERE project_id=? ORDER BY chapter_no", projectId);
        List<Map<String, Object>> sections = WritingJdbc.list(jdbcTemplate,
                "SELECT section_no,title,content FROM writing_section WHERE project_id=? AND heading_level=2 ORDER BY section_no", projectId);
        if (sections.isEmpty()) throw new IllegalStateException("大纲缺少二级标题，无法准备原生图表和表格");
        for (Map<String, Object> chart : WritingJdbc.list(jdbcTemplate,
                "SELECT * FROM writing_chart WHERE project_id=? ORDER BY chart_no", projectId)) {
            prepareChart(chart, chapters);
        }
        int offset = 0;
        for (Map<String, Object> table : WritingJdbc.list(jdbcTemplate,
                "SELECT * FROM writing_table WHERE project_id=? ORDER BY table_no", projectId)) {
            prepareTable(table, sections, offset++);
        }
    }

    private void prepareChart(Map<String, Object> chart, List<Map<String, Object>> chapters) {
        try {
            String type = WritingJdbc.text(chart.get("chart_type")).toUpperCase();
            List<String> categories = new ArrayList<>();
            List<Integer> values = new ArrayList<>();
            String seriesName;
            boolean countWords = type.contains("LINE");
            for (Map<String, Object> chapter : chapters) {
                String chapterId = WritingJdbc.text(chapter.get("id"));
                List<Map<String, Object>> chapterSections = WritingJdbc.list(jdbcTemplate,
                        "SELECT content FROM writing_section WHERE chapter_id=? AND heading_level=2", chapterId);
                categories.add("第" + chapter.get("chapter_no") + "章");
                values.add(countWords ? chapterSections.stream()
                        .mapToInt(section -> WritingJdbc.text(section.get("content")).replaceAll("\\s+", "").length()).sum()
                        : chapterSections.size());
            }
            seriesName = countWords ? "正文字符数" : "二级标题数";
            Map<String, Object> dataset = Map.of(
                    "categories", categories,
                    "series", List.of(Map.of("name", seriesName, "values", values)),
                    "source", "本稿章节及二级标题统计（非外部调查数据）"
            );
            String dataJson = objectMapper.writeValueAsString(dataset);
            String title = WritingJdbc.text(chart.get("title"));
            if (title.contains("文献")) title = countWords ? "各章正文篇幅分布" : "各章二级标题分布";
            jdbcTemplate.update("""
                    UPDATE writing_chart SET title=?,source_type='MANUSCRIPT_STRUCTURE',source_name=?,is_simulated=0,
                    data_json=?,image_path=NULL,description=?,updated_at=? WHERE id=?
                    """, title, "本稿大纲和正文", dataJson, "统计本稿章节的二级标题或字符数量；不是外部调查数据，可在Word中编辑。",
                    LocalDateTime.now(), chart.get("id"));
            List<Map<String, Object>> series = WritingJdbc.list(jdbcTemplate,
                    "SELECT * FROM writing_chart_series WHERE chart_id=? ORDER BY sort_order", chart.get("id"));
            if (!series.isEmpty()) jdbcTemplate.update("""
                    UPDATE writing_chart_series SET series_name=?,source_type='MANUSCRIPT_STRUCTURE',data_json=?,updated_at=? WHERE id=?
                    """, seriesName, objectMapper.writeValueAsString(values), LocalDateTime.now(), series.get(0).get("id"));
        } catch (Exception exception) {
            throw new IllegalStateException("原生图表数据准备失败：" + chart.get("chart_no") + " " + exception.getMessage(), exception);
        }
    }

    private void prepareTable(Map<String, Object> table, List<Map<String, Object>> sections, int offset) {
        try {
            List<List<String>> rows = new ArrayList<>();
            int size = Math.min(5, sections.size());
            for (int i = 0; i < size; i++) {
                Map<String, Object> section = sections.get((offset * size + i) % sections.size());
                String content = WritingJdbc.text(section.get("content")).replaceAll("\\s+", " ").trim();
                rows.add(List.of(
                        WritingJdbc.text(section.get("section_no")),
                        WritingJdbc.text(section.get("title")),
                        content.substring(0, Math.min(90, content.length()))
                ));
            }
            jdbcTemplate.update("""
                    UPDATE writing_table SET source_type='MANUSCRIPT_STRUCTURE',source_name=?,is_simulated=0,
                    header_json=?,rows_json=?,note=?,updated_at=? WHERE id=?
                    """, "本稿二级标题与正文", objectMapper.writeValueAsString(List.of("节号", "小节主题", "正文要点摘录")),
                    objectMapper.writeValueAsString(rows), "根据本稿正文整理，非外部统计数据。", LocalDateTime.now(), table.get("id"));
        } catch (Exception exception) {
            throw new IllegalStateException("表格数据准备失败：" + table.get("table_no") + " " + exception.getMessage(), exception);
        }
    }
}
