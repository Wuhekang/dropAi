package com.dropai.rewrite.service.ppt.rendering.renderer.v1.element;

import com.dropai.rewrite.service.ppt.rendering.contract.v1.PptQualityCode;
import com.dropai.rewrite.service.ppt.rendering.renderer.v1.RendererExecutionException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.apache.poi.sl.usermodel.TableCell;
import org.apache.poi.sl.usermodel.TextParagraph;
import org.apache.poi.sl.usermodel.TextShape;
import org.apache.poi.sl.usermodel.VerticalAlignment;
import org.apache.poi.xslf.usermodel.XSLFTable;
import org.apache.poi.xslf.usermodel.XSLFTableCell;
import org.apache.poi.xslf.usermodel.XSLFTextParagraph;
import org.apache.poi.xslf.usermodel.XSLFTextRun;

import java.awt.Color;
import java.util.HashMap;
import java.util.Map;

/** Writes a native editable DrawingML table using only the frozen row/column measurements. */
public final class TableElementRenderer implements ElementRenderer {
    @Override
    public String elementType() {
        return "TABLE";
    }

    @Override
    public void render(ObjectNode element, ElementRenderContext context) {
        String elementId = PlanElementSupport.requiredText(element, "elementId");
        try {
            ArrayNode columns = PlanElementSupport.requiredArray(element, "columns");
            ArrayNode rows = PlanElementSupport.requiredArray(element, "rows");
            ObjectNode style = PlanElementSupport.requiredObject(element, "resolvedStyle");
            XSLFTable table = context.slide().createTable(rows.size() + 1, columns.size());
            table.setAnchor(PlanElementSupport.anchor(element));
            for (int column = 0; column < columns.size(); column++) {
                table.setColumnWidth(column, PlanElementSupport.points(
                        PlanElementSupport.requiredLong((ObjectNode) columns.get(column), "widthEmu")));
            }
            table.setRowHeight(0, PlanElementSupport.points(
                    PlanElementSupport.requiredLong(element, "headerRowHeightEmu")));
            for (int row = 1; row <= rows.size(); row++) {
                table.setRowHeight(row, PlanElementSupport.points(
                        PlanElementSupport.requiredLong(element, "bodyRowHeightEmu")));
            }

            CellStyle headerStyle = baseCellStyle(style, true);
            CellStyle bodyStyle = baseCellStyle(style, false);
            for (int column = 0; column < columns.size(); column++) {
                String text = PlanElementSupport.requiredText((ObjectNode) columns.get(column), "header");
                applyCell(table.getCell(0, column), text, headerStyle, style);
            }
            for (int row = 0; row < rows.size(); row++) {
                ArrayNode cells = PlanElementSupport.requiredArray((ObjectNode) rows.get(row), "cells");
                if (cells.size() != columns.size()) {
                    throw new IllegalArgumentException("Table row does not match its frozen column count");
                }
                for (int column = 0; column < columns.size(); column++) {
                    JsonNode text = cells.get(column);
                    if (!text.isTextual()) {
                        throw new IllegalArgumentException("Table cell must be text");
                    }
                    applyCell(table.getCell(row + 1, column), text.textValue(), bodyStyle, style);
                }
            }
            applyStatusCells(table, element, style);
            table.updateCellAnchor();
            // POI adjusts the graphic-frame extent while column widths are assigned.
            // Re-apply the frozen frame last; this is execution of the plan, not fitting.
            table.setAnchor(PlanElementSupport.anchor(element));
        } catch (RuntimeException exception) {
            if (exception instanceof RendererExecutionException renderer) {
                throw renderer;
            }
            throw context.failure(PptQualityCode.OOXML_PACKAGE_INVALID,
                    "Failed to execute TABLE element " + elementId, elementId, exception);
        }
    }

    private static CellStyle baseCellStyle(ObjectNode style, boolean header) {
        return new CellStyle(
                PlanElementSupport.color(style, header ? "headerFillColor" : "bodyFillColor"),
                PlanElementSupport.color(style, "textColor"),
                PlanElementSupport.requiredText(style, "fontFamily"),
                PlanElementSupport.requiredInt(style, "fontSizeHundredthPt") / 100d,
                PlanElementSupport.requiredInt(style, header ? "headerFontWeight" : "bodyFontWeight"),
                TextParagraph.TextAlign.LEFT,
                VerticalAlignment.MIDDLE
        );
    }

    private static void applyCell(
            XSLFTableCell cell,
            String text,
            CellStyle cellStyle,
            ObjectNode tableStyle
    ) {
        cell.setFillColor(cellStyle.fillColor());
        cell.setLeftInset(PlanElementSupport.points(
                PlanElementSupport.requiredLong(tableStyle, "cellMarginLeftEmu")));
        cell.setRightInset(PlanElementSupport.points(
                PlanElementSupport.requiredLong(tableStyle, "cellMarginRightEmu")));
        cell.setTopInset(PlanElementSupport.points(
                PlanElementSupport.requiredLong(tableStyle, "cellMarginTopEmu")));
        cell.setBottomInset(PlanElementSupport.points(
                PlanElementSupport.requiredLong(tableStyle, "cellMarginBottomEmu")));
        cell.setVerticalAlignment(cellStyle.verticalAlignment());
        cell.setTextAutofit(TextShape.TextAutofit.NONE);
        cell.setWordWrap(false);
        long borderWidthEmu = PlanElementSupport.requiredLong(tableStyle, "borderWidthEmu");
        for (TableCell.BorderEdge edge : TableCell.BorderEdge.values()) {
            if (borderWidthEmu == 0) {
                cell.removeBorder(edge);
            } else {
                cell.setBorderColor(edge, PlanElementSupport.color(tableStyle, "borderColor"));
                cell.setBorderWidth(edge, PlanElementSupport.points(borderWidthEmu));
            }
        }

        cell.setText(text);
        double lineSpacingPercent = PlanElementSupport.requiredInt(tableStyle, "lineSpacingPermille") / 10d;
        double beforePt = PlanElementSupport.points(
                PlanElementSupport.requiredLong(tableStyle, "paragraphSpaceBeforeEmu"));
        double afterPt = PlanElementSupport.points(
                PlanElementSupport.requiredLong(tableStyle, "paragraphSpaceAfterEmu"));
        for (XSLFTextParagraph paragraph : cell.getTextParagraphs()) {
            paragraph.setTextAlign(cellStyle.horizontalAlignment());
            paragraph.setLineSpacing(lineSpacingPercent);
            paragraph.setSpaceBefore(-beforePt);
            paragraph.setSpaceAfter(-afterPt);
            paragraph.setBullet(false);
            for (XSLFTextRun run : paragraph.getTextRuns()) {
                TextElementRenderer.applyRun(
                        run,
                        cellStyle.fontFamily(),
                        cellStyle.fontSize(),
                        cellStyle.fontWeight(),
                        cellStyle.textColor());
            }
        }
    }

    private static void applyStatusCells(XSLFTable table, ObjectNode element, ObjectNode tableStyle) {
        if (!element.has("statusCells")) {
            return;
        }
        ArrayNode statuses = PlanElementSupport.requiredArray(element, "statusCells");
        Map<String, ObjectNode> unique = new HashMap<>();
        for (JsonNode node : statuses) {
            ObjectNode status = (ObjectNode) node;
            int dataRow = PlanElementSupport.requiredInt(status, "rowIndex");
            int column = PlanElementSupport.requiredInt(status, "columnIndex");
            String key = dataRow + ":" + column;
            if (unique.putIfAbsent(key, status) != null) {
                throw new IllegalArgumentException("Duplicate status cell: " + key);
            }
            CellStyle statusStyle = new CellStyle(
                    PlanElementSupport.color(status, "fillColor"),
                    PlanElementSupport.color(status, "textColor"),
                    PlanElementSupport.requiredText(tableStyle, "fontFamily"),
                    PlanElementSupport.requiredInt(tableStyle, "fontSizeHundredthPt") / 100d,
                    PlanElementSupport.requiredInt(status, "fontWeight"),
                    TextParagraph.TextAlign.valueOf(
                            PlanElementSupport.requiredText(status, "horizontalAlign")),
                    VerticalAlignment.valueOf(
                            PlanElementSupport.requiredText(status, "verticalAlign"))
            );
            applyCell(
                    table.getCell(dataRow + 1, column),
                    PlanElementSupport.requiredText(status, "text"),
                    statusStyle,
                    tableStyle);
        }
    }

    private record CellStyle(
            Color fillColor,
            Color textColor,
            String fontFamily,
            double fontSize,
            int fontWeight,
            TextParagraph.TextAlign horizontalAlignment,
            VerticalAlignment verticalAlignment
    ) {
    }
}
