package com.dropai.rewrite.service.writing;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.poi.util.Units;
import org.apache.poi.xddf.usermodel.chart.AxisCrosses;
import org.apache.poi.xddf.usermodel.chart.AxisPosition;
import org.apache.poi.xddf.usermodel.chart.BarDirection;
import org.apache.poi.xddf.usermodel.chart.BarGrouping;
import org.apache.poi.xddf.usermodel.chart.ChartTypes;
import org.apache.poi.xddf.usermodel.chart.LegendPosition;
import org.apache.poi.xddf.usermodel.chart.XDDFBarChartData;
import org.apache.poi.xddf.usermodel.chart.XDDFCategoryAxis;
import org.apache.poi.xddf.usermodel.chart.XDDFChartData;
import org.apache.poi.xddf.usermodel.chart.XDDFDataSource;
import org.apache.poi.xddf.usermodel.chart.XDDFDataSourcesFactory;
import org.apache.poi.xddf.usermodel.chart.XDDFNumericalDataSource;
import org.apache.poi.xddf.usermodel.chart.XDDFPieChartData;
import org.apache.poi.xddf.usermodel.chart.XDDFValueAxis;
import org.apache.poi.xwpf.usermodel.ParagraphAlignment;
import org.apache.poi.xwpf.usermodel.TableRowAlign;
import org.apache.poi.xwpf.usermodel.XWPFChart;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableCell;
import org.apache.poi.xwpf.usermodel.XWPFTableRow;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTFldChar;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTBody;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTP;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTR;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTTblBorders;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTTblPr;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTText;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.STBorder;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.STFldCharType;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.STTblLayoutType;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.io.OutputStream;
import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
public class DocxExportService {
    private static final String TEMPLATE = "templates/writing/douyin-text-baseline.docx";
    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public DocxExportService(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    public Path export(String projectId, Path output) {
        try {
            Files.createDirectories(output.getParent());
            Map<String, Object> project = WritingJdbc.one(jdbcTemplate, "SELECT * FROM writing_project WHERE id=?", projectId);
            try (InputStream template = new ClassPathResource(TEMPLATE).getInputStream();
                XWPFDocument doc = new XWPFDocument(template)) {
                removeOldBody(doc);
                addDocumentTitle(doc, WritingJdbc.text(project.get("title")));
                addHeading(doc, "摘  要", 1);
                addBodyText(doc, WritingJdbc.text(project.get("abstract_text")));
                addBodyText(doc, "关键词：" + String.join("；", keywords(project)));
                if (WritingJdbc.bool(project.get("generate_english_abstract"), true)
                        && !WritingJdbc.text(project.get("english_abstract")).isBlank()) {
                    addHeading(doc, "Abstract", 1);
                    addBodyText(doc, WritingJdbc.text(project.get("english_abstract")));
                }
                if (WritingJdbc.bool(project.get("generate_toc"), true)) addAutomaticToc(doc);
                for (Map<String, Object> chapter : chapters(projectId)) {
                    addHeading(doc, "第" + chineseNo(WritingJdbc.integer(chapter.get("chapter_no"), 1)) + "章  " + chapter.get("title"), 1);
                    for (Map<String, Object> section : secondSections(chapter)) {
                        addHeading(doc, section.get("section_no") + " " + section.get("title"), 2);
                        addBodyText(doc, WritingJdbc.text(section.get("content")));
                        addVisualsAfterSection(doc, chapter, section);
                        for (Map<String, Object> child : childSections(section)) {
                            addHeading(doc, child.get("section_no") + " " + child.get("title"), 3);
                            addBodyText(doc, WritingJdbc.text(child.get("content")));
                            addVisualsAfterSection(doc, chapter, child);
                        }
                    }
                }
                if (!WritingJdbc.bool(project.get("skip_references"), false)) {
                    addHeading(doc, "参考文献", 1);
                    for (Map<String, Object> ref : WritingJdbc.list(jdbcTemplate, """
                            SELECT * FROM writing_reference WHERE project_id=? AND final_number IS NOT NULL ORDER BY final_number
                            """, projectId)) {
                        addReference(doc, "[" + ref.get("final_number") + "] "
                                + WritingJdbc.text(ref.get("formatted_text")).replaceFirst("^\\[\\d+]\\s*", ""));
                    }
                }
                addHeading(doc, "致谢", 1);
                addBodyText(doc, "感谢指导教师在论文选题、资料整理和写作过程中给予的指导。感谢相关公开资料发布机构为本研究提供可核验的信息基础。");
                try (OutputStream out = Files.newOutputStream(output)) { doc.write(out); }
            }
            return output;
        } catch (Exception exception) {
            throw new IllegalStateException("DOCX导出失败：" + exception.getMessage(), exception);
        }
    }

    private void clearCell(XWPFTableCell cell) {
        while (cell.getParagraphs().size() > 1) cell.removeParagraph(cell.getParagraphs().size() - 1);
        XWPFParagraph p = cell.getParagraphs().get(0);
        for (int i = p.getRuns().size() - 1; i >= 0; i--) p.removeRun(i);
    }

    private void removeOldBody(XWPFDocument doc) {
        CTBody body = doc.getDocument().getBody();
        while (body.sizeOfPArray() > 0) body.removeP(0);
        while (body.sizeOfTblArray() > 0) body.removeTbl(0);
        while (!body.getSdtList().isEmpty()) body.removeSdt(0);
    }

    private void addDocumentTitle(XWPFDocument doc, String title) {
        XWPFParagraph paragraph = doc.createParagraph();
        paragraph.setStyle("Title");
        paragraph.setAlignment(ParagraphAlignment.CENTER);
        XWPFRun run = paragraph.createRun();
        run.setBold(true);
        run.setFontFamily("宋体");
        run.setFontSize(18);
        run.setText(title);
    }

    private void addAutomaticToc(XWPFDocument doc) {
        addHeading(doc, "目  录", 1);
        XWPFParagraph paragraph = doc.createParagraph();
        CTP ctp = paragraph.getCTP();
        CTR beginRun = ctp.addNewR();
        CTFldChar begin = beginRun.addNewFldChar();
        begin.setFldCharType(STFldCharType.BEGIN);
        begin.setDirty(true);
        CTR instrRun = ctp.addNewR();
        CTText instr = instrRun.addNewInstrText();
        instr.setSpace(org.apache.xmlbeans.impl.xb.xmlschema.SpaceAttribute.Space.PRESERVE);
        instr.setStringValue(" TOC \\o \"1-3\" \\h \\z \\u ");
        CTR separateRun = ctp.addNewR();
        separateRun.addNewFldChar().setFldCharType(STFldCharType.SEPARATE);
        separateRun.addNewT().setStringValue("右键更新目录");
        CTR endRun = ctp.addNewR();
        endRun.addNewFldChar().setFldCharType(STFldCharType.END);
    }

    private void addVisualsAfterSection(XWPFDocument doc, Map<String, Object> chapter, Map<String, Object> section) throws Exception {
        for (Map<String, Object> chart : WritingJdbc.list(jdbcTemplate,
                "SELECT * FROM writing_chart WHERE chapter_id=? AND insert_after_section=? ORDER BY sort_order", chapter.get("id"), section.get("id"))) {
            addNativeChart(doc, chart);
            addCaption(doc, "图" + chart.get("chart_no") + "  " + chart.get("title"));
            addSource(doc, "资料来源：" + WritingJdbc.text(chart.get("source_name")) + "。" + WritingJdbc.text(chart.get("description")));
        }
        for (Map<String, Object> table : WritingJdbc.list(jdbcTemplate,
                "SELECT * FROM writing_table WHERE chapter_id=? AND insert_after_section=? ORDER BY sort_order", chapter.get("id"), section.get("id"))) {
            addCaption(doc, "表" + table.get("table_no") + "  " + table.get("title"));
            addNativeTable(doc, table);
            addSource(doc, WritingJdbc.text(table.get("note")));
        }
    }

    @SuppressWarnings("unchecked")
    private void addNativeChart(XWPFDocument doc, Map<String, Object> row) throws Exception {
        Map<String, Object> data = objectMapper.readValue(WritingJdbc.text(row.get("data_json")), new TypeReference<>() {});
        List<String> categoryList = (List<String>) data.getOrDefault("categories", List.of());
        List<Map<String, Object>> seriesList = (List<Map<String, Object>>) data.getOrDefault("series", List.of());
        if (categoryList.isEmpty() || seriesList.isEmpty()) throw new IllegalStateException("图" + row.get("chart_no") + "缺少可编辑数据");
        String[] categories = categoryList.toArray(String[]::new);
        Map<String, Object> first = seriesList.get(0);
        List<?> rawValues = (List<?>) first.getOrDefault("values", List.of());
        Double[] values = rawValues.stream().map(value -> Double.valueOf(String.valueOf(value))).toArray(Double[]::new);
        XWPFChart chart = doc.createChart(Units.toEMU(6.2 * 72), Units.toEMU(3.6 * 72));
        chart.setTitleText(WritingJdbc.text(row.get("title")));
        chart.setTitleOverlay(false);
        chart.getOrAddLegend().setPosition(LegendPosition.BOTTOM);
        XDDFDataSource<String> categorySource = XDDFDataSourcesFactory.fromArray(categories);
        XDDFNumericalDataSource<Double> valueSource = XDDFDataSourcesFactory.fromArray(values);
        String type = WritingJdbc.text(row.get("chart_type")).toUpperCase();
        XDDFChartData chartData;
        if (type.contains("PIE")) {
            XDDFPieChartData pie = (XDDFPieChartData) chart.createData(ChartTypes.PIE, null, null);
            pie.setVaryColors(true);
            chartData = pie;
        } else {
            XDDFCategoryAxis bottom = chart.createCategoryAxis(AxisPosition.BOTTOM);
            XDDFValueAxis left = chart.createValueAxis(AxisPosition.LEFT);
            left.setCrosses(AxisCrosses.AUTO_ZERO);
            if (type.contains("LINE")) chartData = chart.createData(ChartTypes.LINE, bottom, left);
            else {
                XDDFBarChartData bar = (XDDFBarChartData) chart.createData(ChartTypes.BAR, bottom, left);
                bar.setBarDirection(BarDirection.COL);
                bar.setBarGrouping(BarGrouping.CLUSTERED);
                bar.setVaryColors(true);
                chartData = bar;
            }
        }
        XDDFChartData.Series series = chartData.addSeries(categorySource, valueSource);
        series.setTitle(WritingJdbc.text(first.get("name")), null);
        chart.plot(chartData);
    }

    private void addNativeTable(XWPFDocument doc, Map<String, Object> row) throws Exception {
        List<String> headers = objectMapper.readValue(WritingJdbc.text(row.get("header_json")), new TypeReference<>() {});
        List<List<String>> rows = objectMapper.readValue(WritingJdbc.text(row.get("rows_json")), new TypeReference<>() {});
        if (headers.isEmpty()) throw new IllegalStateException("表" + row.get("table_no") + "缺少表头");
        XWPFTable table = doc.createTable(Math.max(1, rows.size() + 1), headers.size());
        table.setTableAlignment(TableRowAlign.CENTER);
        setTableWidths(table, headers.size());
        setThreeLineTable(table);
        for (int c = 0; c < headers.size(); c++) setCell(table.getRow(0).getCell(c), headers.get(c), true);
        for (int r = 0; r < rows.size(); r++) for (int c = 0; c < headers.size(); c++) {
            setCell(table.getRow(r + 1).getCell(c), c < rows.get(r).size() ? rows.get(r).get(c) : "", false);
        }
    }

    private void setTableWidths(XWPFTable table, int columns) {
        int[] widths = switch (columns) {
            case 2 -> new int[]{2200, 6800};
            case 3 -> new int[]{1100, 2200, 5700};
            case 4 -> new int[]{900, 2100, 1100, 4900};
            default -> java.util.stream.IntStream.range(0, columns).map(ignored -> 9000 / columns).toArray();
        };
        table.setWidth(9000);
        CTTblPr pr = table.getCTTbl().getTblPr();
        if (pr == null) pr = table.getCTTbl().addNewTblPr();
        if (!pr.isSetTblLayout()) pr.addNewTblLayout();
        pr.getTblLayout().setType(STTblLayoutType.FIXED);
        for (XWPFTableRow row : table.getRows()) {
            for (int index = 0; index < row.getTableCells().size(); index++) {
                row.getCell(index).setWidth(String.valueOf(widths[Math.min(index, widths.length - 1)]));
            }
        }
    }

    private void setCell(XWPFTableCell cell, String text, boolean header) {
        clearCell(cell);
        cell.setVerticalAlignment(XWPFTableCell.XWPFVertAlign.CENTER);
        XWPFParagraph p = cell.getParagraphs().get(0); p.setAlignment(ParagraphAlignment.CENTER);
        XWPFRun run = p.createRun(); run.setFontFamily("宋体"); run.setFontSize(10); run.setBold(false); run.setText(text == null ? "" : text);
    }

    private void setThreeLineTable(XWPFTable table) {
        CTTblPr pr = table.getCTTbl().getTblPr();
        if (pr == null) pr = table.getCTTbl().addNewTblPr();
        CTTblBorders borders = pr.isSetTblBorders() ? pr.getTblBorders() : pr.addNewTblBorders();
        borders.addNewTop().setVal(STBorder.SINGLE); borders.getTop().setSz(BigInteger.valueOf(12));
        borders.addNewBottom().setVal(STBorder.SINGLE); borders.getBottom().setSz(BigInteger.valueOf(12));
        borders.addNewInsideH().setVal(STBorder.NONE); borders.addNewInsideV().setVal(STBorder.NONE);
        borders.addNewLeft().setVal(STBorder.NONE); borders.addNewRight().setVal(STBorder.NONE);
    }

    private void addHeading(XWPFDocument doc, String text, int level) {
        boolean hasEarlierLevelOne = level == 1 && doc.getDocument().getBody().getPList().stream()
                .anyMatch(existing -> existing.isSetPPr()
                        && existing.getPPr().isSetPStyle()
                        && "Heading 1".equals(existing.getPPr().getPStyle().getVal()));
        XWPFParagraph p = doc.createParagraph();
        p.setStyle("Heading " + level);
        if (hasEarlierLevelOne) p.getCTP().getPPr().addNewPageBreakBefore();
        p.setAlignment(level == 1 ? ParagraphAlignment.CENTER : ParagraphAlignment.LEFT);
        XWPFRun run = p.createRun(); run.setBold(true); run.setFontFamily("黑体");
        run.setFontSize(level == 1 ? 16 : level == 2 ? 15 : 14); run.setText(text);
    }

    private void addBodyText(XWPFDocument doc, String text) {
        if (text == null || text.isBlank()) return;
        for (String block : text.split("\\R+")) {
            String value = block.trim();
            if (value.isBlank() || value.matches("^\\d+(?:\\.\\d+){1,2}\\s+.*$")) continue;
            XWPFParagraph p = doc.createParagraph(); p.setAlignment(ParagraphAlignment.BOTH);
            p.setIndentationFirstLine(480); p.setSpacingBetween(1.5);
            XWPFRun run = p.createRun(); run.setFontFamily("宋体"); run.setFontSize(12); run.setText(value);
        }
    }

    private void addCaption(XWPFDocument doc, String text) {
        XWPFParagraph p = doc.createParagraph(); p.setAlignment(ParagraphAlignment.CENTER);
        XWPFRun run = p.createRun(); run.setFontFamily("黑体"); run.setFontSize(10); run.setText(text);
    }

    private void addSource(XWPFDocument doc, String text) {
        if (text == null || text.isBlank()) return;
        XWPFParagraph p = doc.createParagraph(); p.setAlignment(ParagraphAlignment.LEFT);
        XWPFRun run = p.createRun(); run.setFontFamily("宋体"); run.setFontSize(9); run.setText(text);
    }

    private void addReference(XWPFDocument doc, String text) {
        XWPFParagraph p = doc.createParagraph(); p.setIndentationHanging(420); p.setIndentationLeft(420);
        XWPFRun run = p.createRun(); run.setFontFamily("宋体"); run.setFontSize(10); run.setText(text);
    }

    private List<Map<String, Object>> chapters(String projectId) {
        return WritingJdbc.list(jdbcTemplate, "SELECT * FROM writing_chapter WHERE project_id=? ORDER BY chapter_no", projectId);
    }

    private List<Map<String, Object>> secondSections(Map<String, Object> chapter) {
        return WritingJdbc.list(jdbcTemplate, "SELECT * FROM writing_section WHERE chapter_id=? AND heading_level=2 ORDER BY sort_order", chapter.get("id"));
    }

    private List<Map<String, Object>> childSections(Map<String, Object> section) {
        return WritingJdbc.list(jdbcTemplate, "SELECT * FROM writing_section WHERE parent_section_id=? AND heading_level=3 ORDER BY sort_order", section.get("id"));
    }

    private List<String> keywords(Map<String, Object> project) {
        try {
            List<String> values = objectMapper.readValue(WritingJdbc.text(project.get("keywords_json")), new TypeReference<>() {});
            return values.stream().limit(5).toList();
        } catch (Exception ignored) { return List.of(); }
    }

    private String chineseNo(int no) {
        return switch (no) { case 1 -> "一"; case 2 -> "二"; case 3 -> "三"; case 4 -> "四"; case 5 -> "五"; case 6 -> "六"; default -> String.valueOf(no); };
    }
}
