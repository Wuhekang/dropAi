package com.dropai.rewrite.service.writing;

import org.apache.poi.util.Units;
import org.apache.poi.xwpf.usermodel.ParagraphAlignment;
import org.apache.poi.xwpf.usermodel.TableRowAlign;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableCell;
import org.apache.poi.xwpf.usermodel.XWPFTableRow;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTTblBorders;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTTblPr;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.STBorder;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTFonts;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTRPr;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTPageMar;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTPageSz;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTSectPr;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.io.FileInputStream;
import java.io.OutputStream;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;
import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

@Service
public class DocxExportService {
    private final JdbcTemplate jdbcTemplate;

    public DocxExportService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Path export(String projectId, Path output) {
        try {
            Files.createDirectories(output.getParent());
            Map<String, Object> project = WritingJdbc.one(jdbcTemplate, "SELECT * FROM writing_project WHERE id=?", projectId);
            boolean skipReferences = WritingJdbc.bool(project.get("skip_references"), false);
            try (XWPFDocument doc = new XWPFDocument()) {
                configureA4Page(doc);
                title(doc, WritingJdbc.text(project.get("title")), 22);
                center(doc, "纯文字稿生成文档");
                paragraph(doc, "");
                heading(doc, "摘要", 1);
                paragraph(doc, WritingJdbc.text(project.get("abstract_text")));
                paragraph(doc, "关键词：" + String.join("；", keywords(project)));
                if (WritingJdbc.bool(project.get("generate_english_abstract"), true)) {
                    heading(doc, "Abstract", 1);
                    paragraph(doc, WritingJdbc.text(project.get("english_abstract")));
                }
                if (WritingJdbc.bool(project.get("generate_toc"), true)) {
                    heading(doc, "目录", 1);
                    for (Map<String, Object> chapter : chapters(projectId)) {
                        String type = chapterType(chapter);
                        if (skipReferences && "reference".equals(type)) continue;
                        paragraph(doc, specialChapter(type) ? WritingJdbc.text(chapter.get("title"))
                                : "第" + chineseNo(WritingJdbc.integer(chapter.get("chapter_no"), 1)) + "章 " + chapter.get("title"));
                        for (Map<String, Object> section : WritingJdbc.list(jdbcTemplate, "SELECT * FROM writing_section WHERE chapter_id=? ORDER BY sort_order", chapter.get("id"))) {
                            paragraph(doc, "  " + section.get("section_no") + " " + section.get("title"));
                        }
                    }
                }
                boolean containsReferenceChapter = false;
                for (Map<String, Object> chapter : chapters(projectId)) {
                    String type = chapterType(chapter);
                    if (skipReferences && "reference".equals(type)) continue;
                    heading(doc, specialChapter(type) ? WritingJdbc.text(chapter.get("title"))
                            : "第" + chineseNo(WritingJdbc.integer(chapter.get("chapter_no"), 1)) + "章 " + chapter.get("title"), 1);
                    if ("reference".equals(type)) {
                        containsReferenceChapter = true;
                        addReferences(doc, projectId);
                        continue;
                    }
                    if ("acknowledgement".equals(type)) {
                        paragraph(doc, WritingJdbc.text(chapter.get("content")));
                        continue;
                    }
                    if ("conclusion".equals(type)) {
                        paragraph(doc, WritingJdbc.text(chapter.get("content")));
                        continue;
                    }
                    List<Map<String, Object>> sections = WritingJdbc.list(jdbcTemplate, "SELECT * FROM writing_section WHERE chapter_id=? ORDER BY sort_order", chapter.get("id"));
                    for (Map<String, Object> section : sections) {
                        heading(doc, section.get("section_no") + " " + section.get("title"), 2);
                        paragraph(doc, WritingJdbc.text(section.get("content")));
                        addMaterialsAfterSection(doc, projectId, chapter, section);
                        addChartsAfterSection(doc, chapter, section);
                        addTablesAfterSection(doc, chapter, section);
                    }
                    if (sections.isEmpty()) paragraph(doc, WritingJdbc.text(chapter.get("content")));
                }
                if (!skipReferences && !containsReferenceChapter) {
                    heading(doc, "参考文献", 1);
                    addReferences(doc, projectId);
                }
                try (OutputStream out = Files.newOutputStream(output)) {
                    doc.write(out);
                }
            }
            return output;
        } catch (Exception exception) {
            throw new IllegalStateException("DOCX导出失败：" + exception.getMessage(), exception);
        }
    }

    private void configureA4Page(XWPFDocument doc) {
        CTSectPr section = doc.getDocument().getBody().isSetSectPr()
                ? doc.getDocument().getBody().getSectPr()
                : doc.getDocument().getBody().addNewSectPr();
        CTPageSz size = section.isSetPgSz() ? section.getPgSz() : section.addNewPgSz();
        size.setW(BigInteger.valueOf(11906));
        size.setH(BigInteger.valueOf(16838));
        CTPageMar margin = section.isSetPgMar() ? section.getPgMar() : section.addNewPgMar();
        margin.setTop(BigInteger.valueOf(1440));
        margin.setBottom(BigInteger.valueOf(1440));
        margin.setLeft(BigInteger.valueOf(1440));
        margin.setRight(BigInteger.valueOf(1440));
        margin.setHeader(BigInteger.valueOf(720));
        margin.setFooter(BigInteger.valueOf(720));
        margin.setGutter(BigInteger.ZERO);
    }

    private void addReferences(XWPFDocument doc, String projectId) {
        int number = 0;
        List<Map<String, Object>> refs = WritingJdbc.list(jdbcTemplate,
                "SELECT * FROM writing_reference WHERE project_id=? ORDER BY COALESCE(final_number, citation_number, 999999), relevance_score DESC", projectId);
        for (Map<String, Object> ref : refs) {
            String formatted = WritingJdbc.text(ref.get("formatted_text")).replaceFirst("^\\[\\d+]\\s*", "");
            paragraph(doc, "[" + (++number) + "] " + formatted);
        }
    }

    private String chapterType(Map<String, Object> chapter) {
        String type = WritingJdbc.text(chapter.get("chapter_type"));
        if (!type.isBlank()) return type;
        String title = WritingJdbc.text(chapter.get("title")).toLowerCase();
        if (title.contains("参考文献") || title.equals("references")) return "reference";
        if (title.contains("致谢") || title.equals("acknowledgement") || title.equals("acknowledgments")) return "acknowledgement";
        if (title.contains("结论") || title.contains("展望") || title.contains("总结")) return "conclusion";
        return "content";
    }

    private boolean specialChapter(String type) {
        return "reference".equals(type) || "acknowledgement".equals(type);
    }

    private void addMaterialsAfterSection(XWPFDocument doc, String projectId, Map<String, Object> chapter,
                                          Map<String, Object> section) throws Exception {
        List<Map<String, Object>> all = WritingJdbc.list(jdbcTemplate,
                """
                   SELECT m.* FROM writing_image_material m LEFT JOIN writing_section s ON s.id=m.user_confirmed_section
                   WHERE m.project_id=? AND m.user_confirmed_chapter=? AND m.is_confirmed=1
                   ORDER BY COALESCE(s.sort_order,999999),m.display_order,m.created_at,m.id""",
                projectId, chapter.get("id"));
        for (int index = 0; index < all.size(); index++) {
            Map<String, Object> material = all.get(index);
            if (!WritingJdbc.text(section.get("id")).equals(WritingJdbc.text(material.get("user_confirmed_section")))) continue;
            Path path = Path.of(WritingJdbc.text(material.get("file_path"))).toAbsolutePath().normalize();
            if (!Files.isRegularFile(path)) continue;
            XWPFParagraph paragraph = doc.createParagraph();
            paragraph.setAlignment(ParagraphAlignment.CENTER);
            XWPFRun run = paragraph.createRun();
            try (FileInputStream input = new FileInputStream(path.toFile())) {
                int[] size = imageSize(path, 430, 280);
                run.addPicture(input, pictureType(path), path.getFileName().toString(), Units.toEMU(size[0]), Units.toEMU(size[1]));
            }
            center(doc, "图" + chapter.get("chapter_no") + "-" + (index + 1) + " " + WritingJdbc.text(material.get("display_name")));
        }
    }

    private int pictureType(Path path) {
        String name = path.getFileName().toString().toLowerCase();
        if (name.endsWith(".jpg") || name.endsWith(".jpeg")) return XWPFDocument.PICTURE_TYPE_JPEG;
        if (name.endsWith(".webp")) return XWPFDocument.PICTURE_TYPE_PNG;
        return XWPFDocument.PICTURE_TYPE_PNG;
    }

    private int[] imageSize(Path path, int maxWidth, int maxHeight) {
        try {
            BufferedImage image = ImageIO.read(path.toFile());
            if (image == null || image.getWidth() <= 0 || image.getHeight() <= 0) return new int[]{maxWidth, maxHeight};
            double scale = Math.min((double) maxWidth / image.getWidth(), (double) maxHeight / image.getHeight());
            scale = Math.min(1.0, scale);
            return new int[]{Math.max(120, (int) Math.round(image.getWidth() * scale)),
                    Math.max(80, (int) Math.round(image.getHeight() * scale))};
        } catch (Exception ignored) {
            return new int[]{maxWidth, maxHeight};
        }
    }

    private void addChartsAfterSection(XWPFDocument doc, Map<String, Object> chapter, Map<String, Object> section) throws Exception {
        List<Map<String, Object>> charts = WritingJdbc.list(jdbcTemplate,
                "SELECT * FROM writing_chart WHERE chapter_id=? AND (insert_after_section=? OR ((insert_after_section='' OR insert_after_section IS NULL) AND ?=1)) ORDER BY sort_order",
                chapter.get("id"), section.get("id"), WritingJdbc.integer(section.get("sort_order"), 0));
        for (Map<String, Object> chart : charts) {
            String imagePath = WritingJdbc.text(chart.get("image_path"));
            if (!Files.isRegularFile(Path.of(imagePath))) continue;
            XWPFParagraph p = doc.createParagraph();
            p.setAlignment(ParagraphAlignment.CENTER);
            XWPFRun run = p.createRun();
            try (FileInputStream input = new FileInputStream(imagePath)) {
                run.addPicture(input, XWPFDocument.PICTURE_TYPE_PNG, imagePath, Units.toEMU(460), Units.toEMU(260));
            }
            center(doc, "图" + chart.get("chart_no") + " " + chart.get("title"));
            center(doc, WritingJdbc.text(chart.get("description")));
        }
    }

    private void addTablesAfterSection(XWPFDocument doc, Map<String, Object> chapter, Map<String, Object> section) {
        List<Map<String, Object>> tables = WritingJdbc.list(jdbcTemplate,
                "SELECT * FROM writing_table WHERE chapter_id=? AND is_confirmed=1 AND (user_confirmed_section=? OR insert_after_section=? OR insert_after_section='' OR insert_after_section IS NULL) ORDER BY sort_order",
                chapter.get("id"), section.get("id"), section.get("id"));
        for (Map<String, Object> table : tables) {
            center(doc, "表" + table.get("table_no") + " " + table.get("title"));
            XWPFTable xwpfTable = doc.createTable(4, 3);
            xwpfTable.setTableAlignment(TableRowAlign.CENTER);
            setThreeLineTable(xwpfTable);
            String[] headers = {"指标", "说明", "评价"};
            XWPFTableRow header = xwpfTable.getRow(0);
            for (int i = 0; i < headers.length; i++) header.getCell(i).setText(headers[i]);
            for (int r = 1; r < 4; r++) {
                xwpfTable.getRow(r).getCell(0).setText("指标" + r);
                xwpfTable.getRow(r).getCell(1).setText("围绕研究主题构建的分析维度");
                xwpfTable.getRow(r).getCell(2).setText(r == 1 ? "基础" : r == 2 ? "提升" : "优化");
            }
            for (XWPFTableRow row : xwpfTable.getRows()) for (XWPFTableCell cell : row.getTableCells())
                cell.getParagraphs().forEach(p -> p.getRuns().forEach(run -> styleRun(run, "宋体", 10, false)));
            paragraph(doc, WritingJdbc.text(table.get("note")).isBlank() ? "注：数据为模拟分析数据。" : WritingJdbc.text(table.get("note")));
        }
    }

    private void setThreeLineTable(XWPFTable table) {
        CTTblPr pr = table.getCTTbl().getTblPr();
        if (pr == null) pr = table.getCTTbl().addNewTblPr();
        CTTblBorders borders = pr.isSetTblBorders() ? pr.getTblBorders() : pr.addNewTblBorders();
        borders.addNewTop().setVal(STBorder.SINGLE);
        borders.getTop().setSz(BigInteger.valueOf(12));
        borders.addNewBottom().setVal(STBorder.SINGLE);
        borders.getBottom().setSz(BigInteger.valueOf(12));
        borders.addNewInsideH().setVal(STBorder.NONE);
        borders.addNewInsideV().setVal(STBorder.NONE);
        for (XWPFTableRow row : table.getRows()) {
            for (XWPFTableCell cell : row.getTableCells()) {
                cell.setVerticalAlignment(XWPFTableCell.XWPFVertAlign.CENTER);
            }
        }
    }

    private List<Map<String, Object>> chapters(String projectId) {
        return WritingJdbc.list(jdbcTemplate, "SELECT * FROM writing_chapter WHERE project_id=? ORDER BY chapter_no", projectId);
    }

    private List<String> keywords(Map<String, Object> project) {
        String json = WritingJdbc.text(project.get("keywords_json"));
        if (json.startsWith("[") && json.endsWith("]")) {
            return List.of(json.replace("[", "").replace("]", "").replace("\"", "").split("\\s*,\\s*"));
        }
        return List.of();
    }

    private void title(XWPFDocument doc, String text, int size) {
        XWPFParagraph p = doc.createParagraph();
        p.setAlignment(ParagraphAlignment.CENTER);
        XWPFRun run = p.createRun();
        styleRun(run, "黑体", size, true);
        run.setText(text);
    }

    private void heading(XWPFDocument doc, String text, int level) {
        XWPFParagraph p = doc.createParagraph();
        XWPFRun run = p.createRun();
        styleRun(run, "黑体", level == 1 ? 16 : 14, true);
        run.setText(text);
    }

    private void paragraph(XWPFDocument doc, String text) {
        XWPFParagraph p = doc.createParagraph();
        p.setAlignment(ParagraphAlignment.BOTH);
        XWPFRun run = p.createRun();
        styleRun(run, "宋体", 12, false);
        run.setText(text == null ? "" : text);
    }

    private void center(XWPFDocument doc, String text) {
        XWPFParagraph p = doc.createParagraph();
        p.setAlignment(ParagraphAlignment.CENTER);
        XWPFRun run = p.createRun();
        styleRun(run, "宋体", 10, false);
        run.setText(text == null ? "" : text);
    }

    private void styleRun(XWPFRun run, String eastAsiaFont, int points, boolean bold) {
        run.setBold(bold);
        run.setFontSize(points);
        run.setFontFamily("Times New Roman");
        CTRPr properties = run.getCTR().isSetRPr() ? run.getCTR().getRPr() : run.getCTR().addNewRPr();
        CTFonts fonts = properties.sizeOfRFontsArray() > 0 ? properties.getRFontsArray(0) : properties.addNewRFonts();
        fonts.setAscii("Times New Roman");
        fonts.setHAnsi("Times New Roman");
        fonts.setEastAsia(eastAsiaFont);
    }

    private String chineseNo(int no) {
        return switch (no) {
            case 1 -> "一";
            case 2 -> "二";
            case 3 -> "三";
            case 4 -> "四";
            case 5 -> "五";
            case 6 -> "六";
            default -> String.valueOf(no);
        };
    }
}
