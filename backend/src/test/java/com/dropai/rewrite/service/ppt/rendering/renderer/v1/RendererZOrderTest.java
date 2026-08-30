package com.dropai.rewrite.service.ppt.rendering.renderer.v1;

import com.dropai.rewrite.service.ppt.rendering.contract.v1.enums.RenderElementType;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.apache.poi.xslf.usermodel.XMLSlideShow;
import org.apache.poi.xslf.usermodel.XSLFConnectorShape;
import org.apache.poi.xslf.usermodel.XSLFPictureShape;
import org.apache.poi.xslf.usermodel.XSLFShape;
import org.apache.poi.xslf.usermodel.XSLFTable;
import org.apache.poi.xslf.usermodel.XSLFTextBox;
import org.junit.jupiter.api.Test;

import java.awt.geom.Rectangle2D;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RendererZOrderTest {
    private static final double EMU_PER_POINT = 12_700d;

    @Test
    void writesEveryElementInDeclaredPaintOrderAtItsPlannedGeometry() throws Exception {
        RendererTestSupport.RenderedFixture fixture = RendererTestSupport.renderedFixture();
        try (XMLSlideShow show = RendererTestSupport.open(fixture.pptx())) {
            for (int slideIndex = 0; slideIndex < show.getSlides().size(); slideIndex++) {
                JsonNode plannedSlide = fixture.document().path("slides").get(slideIndex);
                List<XSLFShape> actualShapes = show.getSlides().get(slideIndex).getShapes();
                assertEquals(plannedSlide.path("elements").size(), actualShapes.size());
                for (int elementIndex = 0; elementIndex < actualShapes.size(); elementIndex++) {
                    JsonNode planned = plannedSlide.path("elements").get(elementIndex);
                    XSLFShape actual = actualShapes.get(elementIndex);
                    assertEquals(
                            planned.path("elementType").asText(),
                            actualElementType(actual),
                            planned.path("elementId").asText());
                    assertAnchor(planned, actual, planned.path("elementId").asText());
                }
            }
        }
    }

    @Test
    void rejectsOutOfOrderZIndexesInsteadOfSortingThePlan() {
        var invalid = RendererTestSupport.mutateFixture(plan -> {
            ArrayNode elements = (ArrayNode) plan.path("slides").get(0).path("elements");
            List<JsonNode> values = new ArrayList<>();
            elements.forEach(values::add);
            elements.removeAll();
            elements.add(values.get(1));
            elements.add(values.get(0));
            for (int index = 2; index < values.size(); index++) {
                elements.add(values.get(index));
            }
            return plan;
        });

        assertThrows(
                RendererExecutionException.class,
                () -> RendererTestSupport.render(invalid, RendererTestSupport.classpathAssetResolver()));
    }

    private String actualElementType(XSLFShape shape) {
        if (shape instanceof XSLFTable) {
            return RenderElementType.TABLE.name();
        }
        if (shape instanceof XSLFPictureShape) {
            return RenderElementType.IMAGE.name();
        }
        if (shape instanceof XSLFConnectorShape) {
            return RenderElementType.CONNECTOR.name();
        }
        if (shape instanceof XSLFTextBox) {
            return RenderElementType.TEXT.name();
        }
        return RenderElementType.SHAPE.name();
    }

    private void assertAnchor(JsonNode planned, XSLFShape shape, String elementId) {
        Rectangle2D actual = shape.getAnchor();
        assertEquals(planned.path("xEmu").asLong() / EMU_PER_POINT, actual.getX(), 0.02, elementId + " x");
        assertEquals(planned.path("yEmu").asLong() / EMU_PER_POINT, actual.getY(), 0.02, elementId + " y");
        if (shape instanceof XSLFTable table) {
            double nativeColumnsWidth = 0d;
            for (int column = 0; column < table.getNumberOfColumns(); column++) {
                nativeColumnsWidth += table.getColumnWidth(column);
                assertEquals(
                        planned.path("columns").get(column).path("widthEmu").asLong() / EMU_PER_POINT,
                        table.getColumnWidth(column),
                        0.02,
                        elementId + " column " + column);
            }
            assertEquals(planned.path("widthEmu").asLong() / EMU_PER_POINT,
                    nativeColumnsWidth, 0.02, elementId + " native column total");
        } else {
            assertEquals(planned.path("widthEmu").asLong() / EMU_PER_POINT,
                    actual.getWidth(), 0.02, elementId + " width");
        }
        assertEquals(planned.path("heightEmu").asLong() / EMU_PER_POINT,
                actual.getHeight(), 0.02, elementId + " height");
    }
}
