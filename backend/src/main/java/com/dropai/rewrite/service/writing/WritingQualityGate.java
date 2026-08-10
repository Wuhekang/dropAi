package com.dropai.rewrite.service.writing;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.io.InputStream;
import javax.imageio.ImageIO;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;

@Service
public class WritingQualityGate {
    private static final Pattern FIGURE_CITATION = Pattern.compile("如图(\\d+-\\d+)所示");
    private static final Pattern TABLE_CITATION = Pattern.compile("如表(\\d+-\\d+)所示");
    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public WritingQualityGate(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    public Map<String, Object> check(String projectId, Path docx) {
        return check(projectId, docx, List.of());
    }

    public Map<String, Object> check(String projectId, Path docx, List<String> outlineAutoFixes) {
        List<String> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        List<String> autoFixes = new ArrayList<>(outlineAutoFixes == null ? List.of() : outlineAutoFixes);
        Map<String, Object> project = WritingJdbc.one(jdbcTemplate, "SELECT * FROM writing_project WHERE id=?", projectId);
        if (WritingJdbc.text(project.get("abstract_text")).isBlank()) errors.add("缺少中文摘要");
        if (WritingJdbc.text(project.get("keywords_json")).isBlank()) errors.add("缺少关键词");
        List<Map<String, Object>> chapters = WritingJdbc.list(jdbcTemplate, "SELECT * FROM writing_chapter WHERE project_id=? ORDER BY chapter_no", projectId);
        if (chapters.isEmpty()) errors.add("缺少正文章节");
        List<Map<String, Object>> references = WritingJdbc.list(jdbcTemplate, "SELECT * FROM writing_reference WHERE project_id=?", projectId);
        boolean skipReferences = WritingJdbc.bool(project.get("skip_references"), false);
        for (Map<String, Object> chapter : chapters) {
            String type = WritingJdbc.text(chapter.get("chapter_type"));
            if (type.isBlank()) type = inferChapterType(WritingJdbc.text(chapter.get("title")));
            if (("content".equals(type) || "conclusion".equals(type)) && WritingJdbc.text(chapter.get("content")).isBlank()) {
                errors.add("章节未生成正文：" + chapter.get("title"));
            } else if ("reference".equals(type) && references.isEmpty() && !skipReferences) {
                errors.add("参考文献列表为空");
            } else if ("acknowledgement".equals(type) && WritingJdbc.text(chapter.get("content")).isBlank()) {
                errors.add("致谢未生成");
            }
        }
        if (!WritingJdbc.list(jdbcTemplate, "SELECT id FROM writing_chapter WHERE project_id=? AND content LIKE '%[[REF:%'", projectId).isEmpty()
                || !WritingJdbc.list(jdbcTemplate, "SELECT id FROM writing_section WHERE project_id=? AND content LIKE '%[[REF:%'", projectId).isEmpty()) {
            errors.add("存在未解析的REF标记");
        }
        if (!WritingJdbc.list(jdbcTemplate, "SELECT id FROM writing_chapter WHERE project_id=? AND content LIKE '%[[DROP_AI_PROTECTED_%'", projectId).isEmpty()
                || !WritingJdbc.list(jdbcTemplate, "SELECT id FROM writing_section WHERE project_id=? AND content LIKE '%[[DROP_AI_PROTECTED_%'", projectId).isEmpty()) {
            errors.add("存在未恢复的结构保护占位符");
        }
        checkVisualReferences(projectId, errors);
        int unfinishedImageTasks = WritingJdbc.list(jdbcTemplate,
                "SELECT id FROM writing_image_task WHERE project_id=? AND status<>'CONFIRMED'", projectId).size();
        if (unfinishedImageTasks > 0) errors.add("存在未确认的图片任务：" + unfinishedImageTasks + "项");
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
            if ("AI_SEARCH".equalsIgnoreCase(WritingJdbc.text(reference.get("source_type")))
                    && WritingJdbc.text(reference.get("url")).isBlank()) errors.add("参考文献缺少公开URL：" + title);
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
                else checkDocxStyles(docx, warnings, autoFixes);
            } catch (Exception exception) {
                errors.add("DOCX大小读取失败");
            }
        }
        return Map.of(
                "passed", errors.isEmpty(),
                "errors", errors,
                "warnings", warnings,
                "autoFixes", autoFixes,
                "normalized", !autoFixes.isEmpty(),
                "chapterChecks", chapters.size(),
                "chartChecks", WritingJdbc.list(jdbcTemplate, "SELECT id FROM writing_chart WHERE project_id=?", projectId).size(),
                "tableChecks", WritingJdbc.list(jdbcTemplate, "SELECT id FROM writing_table WHERE project_id=?", projectId).size(),
                "citationChecks", WritingJdbc.list(jdbcTemplate, "SELECT id FROM writing_citation WHERE project_id=?", projectId).size(),
                "referenceChecks", references.size()
        );
    }

    private void checkVisualReferences(String projectId, List<String> errors) {
        Set<String> figures = new HashSet<>();
        List<Map<String, Object>> sectionRows = WritingJdbc.list(jdbcTemplate, "SELECT content FROM writing_section WHERE project_id=?", projectId);
        String allContent = sectionRows.stream().map(row -> WritingJdbc.text(row.get("content"))).collect(java.util.stream.Collectors.joining("\n"));
        for (Map<String, Object> chapter : WritingJdbc.list(jdbcTemplate, "SELECT * FROM writing_chapter WHERE project_id=?", projectId)) {
            List<Map<String, Object>> materials = WritingJdbc.list(jdbcTemplate,
                    """
                       SELECT m.* FROM writing_image_material m LEFT JOIN writing_section s ON s.id=m.user_confirmed_section
                       WHERE m.project_id=? AND m.user_confirmed_chapter=? AND m.is_confirmed=1
                       ORDER BY COALESCE(s.sort_order,999999),m.display_order,m.created_at,m.id""", projectId, chapter.get("id"));
            for (int i = 0; i < materials.size(); i++) {
                String path = WritingJdbc.text(materials.get(i).get("file_path"));
                String number = chapter.get("chapter_no") + "-" + (i + 1);
                Map<String, Object> material = materials.get(i);
                String sourceType = WritingJdbc.text(material.get("source_type")).toUpperCase();
                String displayName = WritingJdbc.text(material.get("display_name"));
                if (!Set.of("USER_UPLOAD", "WEB_SEARCH").contains(sourceType)) {
                    errors.add("图片来源类型不受支持：图" + number);
                }
                if (isPlaceholderImageName(displayName)) errors.add("图片尚未完成名称确认：图" + number);
                if (path.isBlank() || !Files.isRegularFile(Path.of(path))) errors.add("图片文件不存在：图" + number);
                else if (!isReadableImage(Path.of(path))) errors.add("图片文件无法打开：图" + number);
                else {
                    figures.add(number);
                    if (!allContent.contains("如图" + number + "所示")) errors.add("真实图片未在正文中引用：图" + number);
                }
            }
        }
        for (Map<String, Object> chart : WritingJdbc.list(jdbcTemplate, "SELECT * FROM writing_chart WHERE project_id=?", projectId)) {
            String number = WritingJdbc.text(chart.get("chart_no"));
            String path = WritingJdbc.text(chart.get("image_path"));
            if (!path.isBlank() && Files.isRegularFile(Path.of(path))) figures.add(number);
        }
        Set<String> tables = new HashSet<>();
        WritingJdbc.list(jdbcTemplate, "SELECT table_no FROM writing_table WHERE project_id=? AND is_confirmed=1", projectId)
                .forEach(row -> tables.add(WritingJdbc.text(row.get("table_no"))));
        for (Map<String, Object> section : sectionRows) {
            String content = WritingJdbc.text(section.get("content"));
            Matcher figureMatcher = FIGURE_CITATION.matcher(content);
            while (figureMatcher.find()) if (!figures.contains(figureMatcher.group(1))) errors.add("正文图引用没有真实图片：图" + figureMatcher.group(1));
            Matcher tableMatcher = TABLE_CITATION.matcher(content);
            while (tableMatcher.find()) if (!tables.contains(tableMatcher.group(1))) errors.add("正文表引用没有对应表格：表" + tableMatcher.group(1));
        }
    }

    private boolean isReadableImage(Path path) {
        try {
            return ImageIO.read(path.toFile()) != null;
        } catch (Exception ignored) {
            return false;
        }
    }

    private boolean isPlaceholderImageName(String value) {
        return value == null || value.isBlank() || "未命名图片".equals(value)
                || value.trim().matches("(?i)^(page|image|img|图片|截图)[-_ ]?\\d+$");
    }

    private void checkDocxStyles(Path docx, List<String> warnings, List<String> autoFixes) {
        try (InputStream input = Files.newInputStream(docx); XWPFDocument document = new XWPFDocument(input)) {
            for (XWPFParagraph paragraph : document.getParagraphs()) {
                String text = paragraph.getText().trim();
                if (text.isBlank() || paragraph.getRuns().isEmpty()) continue;
                var run = paragraph.getRuns().get(0);
                String eastAsia = run.getCTR().isSetRPr() && run.getCTR().getRPr().sizeOfRFontsArray() > 0
                        ? run.getCTR().getRPr().getRFontsArray(0).getEastAsia() : "";
                int size = run.getFontSize();
                if (text.matches("第.+章.*") && (!("黑体".equals(eastAsia)) || size != 16)) {
                    warnings.add("标题样式已交由导出模板自动规范：" + text);
                    autoFixes.add("AUTO_FIX 一级标题样式：" + text);
                } else if (text.matches("\\d+\\.\\d+\\s+.*") && (!("黑体".equals(eastAsia)) || size != 14)) {
                    warnings.add("标题样式已交由导出模板自动规范：" + text);
                    autoFixes.add("AUTO_FIX 二级标题样式：" + text);
                } else if (text.matches("[图表]\\d+-\\d+.*") && (!("宋体".equals(eastAsia)) || size != 10)) {
                    warnings.add("图表标题样式已交由导出模板自动规范：" + text);
                    autoFixes.add("AUTO_FIX 图表标题样式：" + text);
                }
            }
        } catch (Exception exception) {
            warnings.add("DOCX样式复核暂不可用：" + exception.getMessage());
        }
    }

    public List<String> normalizeOutline(String projectId) {
        OutlineNormalizeService normalizer = new OutlineNormalizeService();
        List<String> fixes = new ArrayList<>();
        for (Map<String, Object> chapter : WritingJdbc.list(jdbcTemplate,
                "SELECT id,title FROM writing_chapter WHERE project_id=? ORDER BY sort_order", projectId)) {
            String before = WritingJdbc.text(chapter.get("title"));
            String after = normalizer.chapterTitle(before);
            if (!before.equals(after)) {
                jdbcTemplate.update("UPDATE writing_chapter SET title=?,updated_at=CURRENT_TIMESTAMP WHERE id=?", after, chapter.get("id"));
                fixes.add("AUTO_FIX 一级标题编号：" + before + " -> " + after);
            }
        }
        for (Map<String, Object> section : WritingJdbc.list(jdbcTemplate,
                "SELECT id,title FROM writing_section WHERE project_id=? ORDER BY sort_order", projectId)) {
            String before = WritingJdbc.text(section.get("title"));
            String after = normalizer.sectionTitle(before);
            if (!before.equals(after)) {
                jdbcTemplate.update("UPDATE writing_section SET title=?,updated_at=CURRENT_TIMESTAMP WHERE id=?", after, section.get("id"));
                fixes.add("AUTO_FIX 二级标题编号：" + before + " -> " + after);
            }
        }
        return fixes;
    }

    private String inferChapterType(String title) {
        String normalized = title == null ? "" : title.toLowerCase();
        if (normalized.contains("参考文献") || normalized.equals("references")) return "reference";
        if (normalized.contains("致谢") || normalized.equals("acknowledgement") || normalized.equals("acknowledgments")) return "acknowledgement";
        if (normalized.contains("结论") || normalized.contains("展望") || normalized.contains("总结")) return "conclusion";
        return "content";
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
