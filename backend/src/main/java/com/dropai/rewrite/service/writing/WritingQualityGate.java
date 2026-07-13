package com.dropai.rewrite.service.writing;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
public class WritingQualityGate {
    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public WritingQualityGate(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    public Map<String, Object> check(String projectId, Path docx) {
        List<String> errors = new ArrayList<>();
        Map<String, Object> project = WritingJdbc.one(jdbcTemplate, "SELECT * FROM writing_project WHERE id=?", projectId);
        if (WritingJdbc.text(project.get("abstract_text")).isBlank()) errors.add("缺少中文摘要");
        if (WritingJdbc.text(project.get("keywords_json")).isBlank()) errors.add("缺少关键词");
        List<Map<String, Object>> chapters = WritingJdbc.list(jdbcTemplate, "SELECT * FROM writing_chapter WHERE project_id=? ORDER BY chapter_no", projectId);
        if (chapters.isEmpty()) errors.add("缺少正文章节");
        for (Map<String, Object> chapter : chapters) {
            String chapterId = WritingJdbc.text(chapter.get("id"));
            if (WritingJdbc.text(chapter.get("content")).isBlank()) errors.add("章节未生成正文：" + chapter.get("title"));
        }
        if (!WritingJdbc.list(jdbcTemplate, "SELECT id FROM writing_chapter WHERE project_id=? AND content LIKE '%[[REF:%'", projectId).isEmpty()) {
            errors.add("存在未解析的REF标记");
        }
        List<Map<String, Object>> references = WritingJdbc.list(jdbcTemplate, "SELECT * FROM writing_reference WHERE project_id=?", projectId);
        List<String> titles = new ArrayList<>();
        List<String> dois = new ArrayList<>();
        for (Map<String, Object> reference : references) {
            String title = WritingJdbc.text(reference.get("title"));
            String doi = WritingJdbc.text(reference.get("doi"));
            String status = WritingJdbc.text(reference.get("verification_status"));
            if (title.isBlank()) errors.add("参考文献缺少题名");
            if (WritingJdbc.text(reference.get("authors")).isBlank()) errors.add("参考文献缺少作者：" + title);
            if (WritingJdbc.integer(reference.get("publication_year"), 0) <= 1900) errors.add("参考文献缺少年份：" + title);
            if (WritingJdbc.text(reference.get("journal_or_publisher")).isBlank()) errors.add("参考文献缺少来源：" + title);
            if (WritingJdbc.text(reference.get("url")).isBlank()) errors.add("参考文献缺少公开URL：" + title);
            if ("UNVERIFIED".equalsIgnoreCase(status) || "REJECTED".equalsIgnoreCase(status)) errors.add("存在未验证或已拒绝参考文献：" + title);
            if (!title.isBlank() && titles.contains(title)) errors.add("参考文献题名重复：" + title);
            if (!title.isBlank()) titles.add(title);
            if (!doi.isBlank() && dois.contains(doi)) errors.add("参考文献DOI重复：" + doi);
            if (!doi.isBlank()) dois.add(doi);
        }
        for (Map<String, Object> chart : WritingJdbc.list(jdbcTemplate, "SELECT * FROM writing_chart WHERE project_id=?", projectId)) {
            String image = WritingJdbc.text(chart.get("image_path"));
            if (image.isBlank() || !Files.isRegularFile(Path.of(image))) errors.add("图表图片不存在：" + chart.get("chart_no"));
            if (WritingJdbc.bool(chart.get("is_simulated"), true) && !WritingJdbc.text(chart.get("description")).contains("模拟")
                    && !WritingJdbc.text(chart.get("description")).contains("情境")) {
                errors.add("模拟图表未标注：" + chart.get("chart_no"));
            }
        }
        if (docx == null || !Files.isRegularFile(docx)) errors.add("DOCX不存在");
        else {
            try {
                if (Files.size(docx) < 1024) errors.add("DOCX大小异常");
            } catch (Exception exception) {
                errors.add("DOCX大小读取失败");
            }
        }
        return Map.of(
                "passed", errors.isEmpty(),
                "errors", errors,
                "warnings", List.of(),
                "chapterChecks", chapters.size(),
                "chartChecks", WritingJdbc.list(jdbcTemplate, "SELECT id FROM writing_chart WHERE project_id=?", projectId).size(),
                "tableChecks", WritingJdbc.list(jdbcTemplate, "SELECT id FROM writing_table WHERE project_id=?", projectId).size(),
                "citationChecks", WritingJdbc.list(jdbcTemplate, "SELECT id FROM writing_citation WHERE project_id=?", projectId).size(),
                "referenceChecks", references.size()
        );
    }

    public void writeReport(String projectId, Path reportPath, Map<String, Object> report) {
        try {
            Files.createDirectories(reportPath.getParent());
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(reportPath.toFile(), report);
            jdbcTemplate.update("UPDATE writing_project SET quality_report_path=? WHERE id=?", reportPath.toString(), projectId);
        } catch (Exception exception) {
            throw new IllegalStateException("质量报告写入失败：" + exception.getMessage(), exception);
        }
    }
}
