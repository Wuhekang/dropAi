package com.dropai.rewrite.service.ppt.rendering.renderer.v1;

import com.fasterxml.jackson.databind.JsonNode;
import org.apache.poi.xslf.usermodel.XMLSlideShow;
import org.apache.poi.xslf.usermodel.XSLFConnectorShape;
import org.apache.poi.xslf.usermodel.XSLFPictureShape;
import org.apache.poi.xslf.usermodel.XSLFTable;
import org.junit.jupiter.api.Test;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RendererNativeElementTest {
    @Test
    void embedsExactlyTheTwentyFiveVerifiedSourceImagesWithoutCroppingOrReplacement() throws Exception {
        RendererTestSupport.RenderedFixture fixture = RendererTestSupport.renderedFixture();
        List<String> expectedHashes = new ArrayList<>();
        fixture.document().path("assets").forEach(asset ->
                expectedHashes.add(asset.path("sha256").asText()));
        List<String> actualHashes = new ArrayList<>();

        try (XMLSlideShow show = RendererTestSupport.open(fixture.pptx())) {
            for (var slide : show.getSlides()) {
                for (var shape : slide.getShapes()) {
                    if (shape instanceof XSLFPictureShape picture) {
                        actualHashes.add(RendererTestSupport.sha256(picture.getPictureData().getData()));
                        if (picture.getClipping() != null) {
                            assertEquals(0, picture.getClipping().top);
                            assertEquals(0, picture.getClipping().right);
                            assertEquals(0, picture.getClipping().bottom);
                            assertEquals(0, picture.getClipping().left);
                        }
                    }
                }
            }
        }

        Collections.sort(expectedHashes);
        Collections.sort(actualHashes);
        assertEquals(expectedHashes, actualHashes);
    }

    @Test
    void keepsBothTablesAndAllConnectorsAsNativeEditableObjects() throws Exception {
        RendererTestSupport.RenderedFixture fixture = RendererTestSupport.renderedFixture();
        List<JsonNode> plannedTables = new ArrayList<>();
        fixture.document().path("slides").forEach(slide -> slide.path("elements").forEach(element -> {
            if ("TABLE".equals(element.path("elementType").asText())) {
                plannedTables.add(element);
            }
        }));

        int tableIndex = 0;
        int connectors = 0;
        try (XMLSlideShow show = RendererTestSupport.open(fixture.pptx())) {
            for (var slide : show.getSlides()) {
                for (var shape : slide.getShapes()) {
                    if (shape instanceof XSLFTable table) {
                        JsonNode planned = plannedTables.get(tableIndex++);
                        assertEquals(planned.path("rows").size() + 1, table.getNumberOfRows());
                        assertEquals(planned.path("columns").size(), table.getNumberOfColumns());
                        for (int column = 0; column < table.getNumberOfColumns(); column++) {
                            assertEquals(planned.path("columns").get(column).path("header").asText(),
                                    table.getCell(0, column).getText());
                        }
                        for (int row = 0; row < planned.path("rows").size(); row++) {
                            for (int column = 0; column < table.getNumberOfColumns(); column++) {
                                assertEquals(planned.path("rows").get(row).path("cells").get(column).asText(),
                                        table.getCell(row + 1, column).getText());
                            }
                        }
                        if (planned.path("statusCells").isArray()) {
                            planned.path("statusCells").forEach(status -> {
                                Color fill = table.getCell(
                                        status.path("rowIndex").asInt() + 1,
                                        status.path("columnIndex").asInt()).getFillColor();
                                assertEquals(status.path("fillColor").asText(), hex(fill));
                            });
                        }
                    } else if (shape instanceof XSLFConnectorShape) {
                        connectors++;
                    }
                }
            }
        }

        assertEquals(2, tableIndex);
        assertEquals(4, connectors);
        String xml = RendererTestSupport.allXml(fixture.pptx());
        assertEquals(2, occurrences(xml, "<a:tbl>"));
        assertEquals(4, occurrences(xml, "<p:cxnSp>"));
        assertFalse(xml.contains("data:image"));
    }

    private int occurrences(String value, String token) {
        int count = 0;
        int index = 0;
        while ((index = value.indexOf(token, index)) >= 0) {
            count++;
            index += token.length();
        }
        return count;
    }

    private String hex(Color color) {
        assertTrue(color != null, "Expected a solid fill color");
        return String.format("#%02X%02X%02X", color.getRed(), color.getGreen(), color.getBlue());
    }
}
