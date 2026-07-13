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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.io.FileInputStream;
import java.io.OutputStream;
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
            try (XWPFDocument doc = new XWPFDocument()) {
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
                        paragraph(doc, "第" + chineseNo(WritingJdbc.integer(chapter.get("chapter_no"), 1)) + "章 " + chapter.get("title"));
                        for (Map<String, Object> section : WritingJdbc.list(jdbcTemplate, "SELECT * FROM writing_section WHERE chapter_id=? ORDER BY sort_order", chapter.get("id"))) {
                            paragraph(doc, "  " + section.get("section_no") + " " + section.get("title"));
                        }
                    }
                }
                for (Map<String, Object> chapter : chapters(projectId)) {
                    heading(doc, "第" + chineseNo(WritingJdbc.integer(chapter.get("chapter_no"), 1)) + "章 " + chapter.get("title"), 1);
                    List<Map<String, Object>> sections = WritingJdbc.list(jdbcTemplate, "SELECT * FROM writing_section WHERE chapter_id=? ORDER BY sort_order", chapter.get("id"));
                    for (Map<String, Object> section : sections) {
                        heading(doc, section.get("section_no") + " " + section.get("title"), 2);
                        paragraph(doc, WritingJdbc.text(section.get("content")));
                        addChartsAfterSection(doc, chapter, section);
                        addTablesAfterSection(doc, chapter, section);
                    }
                    if (sections.isEmpty()) paragraph(doc, WritingJdbc.text(chapter.get("content")));
                }
                heading(doc, "\u53c2\u8003\u6587\u732e", 1);
                heading(doc, "\u4e2d\u6587\u53c2\u8003\u6587\u732e", 2);
                for (Map<String, Object> ref : WritingJdbc.list(jdbcTemplate,
                        "SELECT * FROM writing_reference WHERE project_id=? AND final_number IS NOT NULL AND COALESCE(language,'UNKNOWN')='ZH' ORDER BY final_number", projectId)) {
                    paragraph(doc, "[" + ref.get("final_number") + "] " + WritingJdbc.text(ref.get("formatted_text")).replaceFirst("^\\[\\d+]\\s*", ""));
                }
                heading(doc, "English References", 2);
                for (Map<String, Object> ref : WritingJdbc.list(jdbcTemplate,
                        "SELECT * FROM writing_reference WHERE project_id=? AND final_number IS NOT NULL AND COALESCE(language,'UNKNOWN')<>'ZH' ORDER BY final_number", projectId)) {
                    paragraph(doc, "[" + ref.get("final_number") + "] " + WritingJdbc.text(ref.get("formatted_text")).replaceFirst("^\\[\\d+]\\s*", ""));
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

    private void addChartsAfterSection(XWPFDocument doc, Map<String, Object> chapter, Map<String, Object> section) throws Exception {
        List<Map<String, Object>> charts = WritingJdbc.list(jdbcTemplate,
                "SELECT * FROM writing_chart WHERE chapter_id=? AND (insert_after_section=? OR insert_after_section='' OR insert_after_section IS NULL) ORDER BY sort_order",
                chapter.get("id"), section.get("id"));
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
                "SELECT * FROM writing_table WHERE chapter_id=? AND (insert_after_section=? OR insert_after_section='' OR insert_after_section IS NULL) ORDER BY sort_order",
                chapter.get("id"), section.get("id"));
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
        run.setBold(true);
        run.setFontSize(size);
        run.setText(text);
    }

    private void heading(XWPFDocument doc, String text, int level) {
        XWPFParagraph p = doc.createParagraph();
        XWPFRun run = p.createRun();
        run.setBold(true);
        run.setFontSize(level == 1 ? 16 : 14);
        run.setText(text);
    }

    private void paragraph(XWPFDocument doc, String text) {
        XWPFParagraph p = doc.createParagraph();
        p.setAlignment(ParagraphAlignment.BOTH);
        XWPFRun run = p.createRun();
        run.setFontSize(12);
        run.setText(text == null ? "" : text);
    }

    private void center(XWPFDocument doc, String text) {
        XWPFParagraph p = doc.createParagraph();
        p.setAlignment(ParagraphAlignment.CENTER);
        XWPFRun run = p.createRun();
        run.setFontSize(11);
        run.setText(text == null ? "" : text);
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
