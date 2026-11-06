package com.dropai.rewrite.service.writing;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipFile;

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
        int bodyWords = bodyWordCount(projectId);
        if (bodyWords < 6000) errors.add("正文字数不足6000字：" + bodyWords);
        if (bodyWords > 9000) errors.add("正文字数超过9000字：" + bodyWords);
        if (!WritingJdbc.list(jdbcTemplate, "SELECT id FROM writing_chapter WHERE project_id=? AND content LIKE '%[[REF:%'", projectId).isEmpty()) {
            errors.add("存在未解析的REF标记");
        }
        List<Map<String, Object>> references = WritingJdbc.list(jdbcTemplate, "SELECT * FROM writing_reference WHERE project_id=?", projectId);
        boolean checkReferences = !WritingJdbc.bool(project.get("skip_references"), false);
        List<String> titles = new ArrayList<>();
        List<String> dois = new ArrayList<>();
        for (Map<String, Object> reference : checkReferences ? references : List.<Map<String, Object>>of()) {
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
        List<Map<String, Object>> cited = WritingJdbc.list(jdbcTemplate,
                "SELECT * FROM writing_reference WHERE project_id=? AND final_number IS NOT NULL ORDER BY final_number", projectId);
        if (checkReferences && cited.size() != 15) errors.add("正文必须依次引用15篇文献，当前为" + cited.size() + "篇");
        for (int i = 0; checkReferences && i < cited.size(); i++) if (WritingJdbc.integer(cited.get(i).get("final_number"), 0) != i + 1) {
            errors.add("参考文献首次出现顺序不是连续的[1]-[15]"); break;
        }
        int lateFirstCitations = WritingJdbc.integer(WritingJdbc.one(jdbcTemplate, """
                SELECT COUNT(DISTINCT c.reference_id) AS n FROM writing_citation c
                JOIN writing_chapter ch ON ch.id=c.chapter_id
                WHERE c.project_id=? AND ch.chapter_no>2
                AND NOT EXISTS (SELECT 1 FROM writing_citation earlier JOIN writing_chapter ech ON ech.id=earlier.chapter_id
                    WHERE earlier.project_id=c.project_id AND earlier.reference_id=c.reference_id AND ech.chapter_no<=2)
                """, projectId).get("n"), 0);
        if (checkReferences && lateFirstCitations > 0) errors.add("有" + lateFirstCitations + "篇文献未在前两章完成首次引用");
        for (Map<String, Object> parent : WritingJdbc.list(jdbcTemplate,
                "SELECT id,title FROM writing_section WHERE project_id=? AND heading_level=2", projectId)) {
            int childCount = WritingJdbc.integer(WritingJdbc.one(jdbcTemplate,
                    "SELECT COUNT(*) AS n FROM writing_section WHERE parent_section_id=?", parent.get("id")).get("n"), 0);
            if (childCount == 1) errors.add("二级标题下不能只有一个三级标题：" + parent.get("title"));
        }
        for (Map<String, Object> chart : WritingJdbc.list(jdbcTemplate, "SELECT * FROM writing_chart WHERE project_id=?", projectId)) {
            if (WritingJdbc.text(chart.get("data_json")).isBlank()) errors.add("原生图表缺少嵌入数据：" + chart.get("chart_no"));
            if (WritingJdbc.bool(chart.get("is_simulated"), true)) errors.add("不允许使用模拟图表数据：" + chart.get("chart_no"));
        }
        if (docx == null || !Files.isRegularFile(docx)) errors.add("DOCX不存在");
        else {
            try {
                if (Files.size(docx) < 1024) errors.add("DOCX大小异常");
                verifyDocxStructure(projectId, docx, errors);
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
                "referenceChecks", checkReferences ? references.size() : 0,
                "bodyWordCount", bodyWords
        );
    }

    private int bodyWordCount(String projectId) {
        StringBuilder text = new StringBuilder();
        for (Map<String, Object> row : WritingJdbc.list(jdbcTemplate,
                "SELECT content FROM writing_section WHERE project_id=?", projectId)) text.append(WritingJdbc.text(row.get("content")));
        Matcher matcher = Pattern.compile("[\\p{IsHan}A-Za-z0-9]").matcher(text);
        int count = 0;
        while (matcher.find()) count++;
        return count;
    }

    private void verifyDocxStructure(String projectId, Path docx, List<String> errors) {
        try (ZipFile zip = new ZipFile(docx.toFile())) {
            int expectedCharts = WritingJdbc.list(jdbcTemplate, "SELECT id FROM writing_chart WHERE project_id=?", projectId).size();
            var document = zip.getEntry("word/document.xml");
            String xml = document == null ? "" : new String(zip.getInputStream(document).readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
            Matcher drawingMatcher = Pattern.compile("<w:drawing>.*?<c:chart\\b.*?</w:drawing>", Pattern.DOTALL).matcher(xml);
            int visibleCharts = 0;
            while (drawingMatcher.find()) {
                Matcher extent = Pattern.compile("<wp:extent cx=\"(\\d+)\" cy=\"(\\d+)\"").matcher(drawingMatcher.group());
                if (extent.find() && Long.parseLong(extent.group(1)) >= 3_000_000L
                        && Long.parseLong(extent.group(2)) >= 2_000_000L) visibleCharts++;
            }
            if (visibleCharts < expectedCharts) errors.add("DOCX正文中可见的原生图表数量不足：" + visibleCharts + "/" + expectedCharts);
            if (!xml.contains("TOC \\o") && !xml.contains("TOC  \\o")) errors.add("DOCX缺少1-3级自动目录域");
            if (xml.contains("本人声明") || xml.contains("毕业设计（论文）")) errors.add("通用纯文字稿不应包含学校封面或诚信声明");
            for (String forbidden : List.of("数据为模拟分析数据", "待补充", "修改说明", "原稿提到")) {
                if (xml.contains(forbidden)) errors.add("成稿含有禁止的过程性或模拟数据文字：" + forbidden);
            }
        } catch (Exception exception) {
            errors.add("DOCX结构检查失败：" + exception.getMessage());
        }
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
