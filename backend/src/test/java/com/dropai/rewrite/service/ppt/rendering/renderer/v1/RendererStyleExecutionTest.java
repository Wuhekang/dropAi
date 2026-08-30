package com.dropai.rewrite.service.ppt.rendering.renderer.v1;

import com.fasterxml.jackson.databind.JsonNode;
import org.apache.poi.sl.usermodel.LineDecoration;
import org.apache.poi.sl.usermodel.PaintStyle;
import org.apache.poi.sl.usermodel.ShapeType;
import org.apache.poi.sl.usermodel.TextParagraph;
import org.apache.poi.sl.usermodel.VerticalAlignment;
import org.apache.poi.xslf.usermodel.XMLSlideShow;
import org.apache.poi.xslf.usermodel.XSLFAutoShape;
import org.apache.poi.xslf.usermodel.XSLFConnectorShape;
import org.apache.poi.xslf.usermodel.XSLFPictureShape;
import org.apache.poi.xslf.usermodel.XSLFSimpleShape;
import org.apache.poi.xslf.usermodel.XSLFTextBox;
import org.junit.jupiter.api.Test;

import java.awt.Color;
import java.util.Locale;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RendererStyleExecutionTest {
    private static final double EMU_PER_POINT = 12_700d;

    @Test
    void executesResolvedTextTypographyWithoutFontOrSpacingDecisions() throws Exception {
        RendererTestSupport.RenderedFixture fixture = RendererTestSupport.renderedFixture();
        try (XMLSlideShow show = RendererTestSupport.open(fixture.pptx())) {
            for (int slideIndex = 0; slideIndex < show.getSlides().size(); slideIndex++) {
                JsonNode elements = fixture.document().path("slides").get(slideIndex).path("elements");
                var shapes = show.getSlides().get(slideIndex).getShapes();
                for (int index = 0; index < shapes.size(); index++) {
                    JsonNode element = elements.get(index);
                    if (!(shapes.get(index) instanceof XSLFTextBox text)) {
                        continue;
                    }
                    JsonNode style = element.path("resolvedStyle");
                    assertEquals(style.path("verticalAlign").asText(), vertical(text.getVerticalAlignment()));
                    assertEquals(style.path("marginLeftEmu").asLong() / EMU_PER_POINT,
                            text.getLeftInset(), 0.01);
                    assertEquals(style.path("marginRightEmu").asLong() / EMU_PER_POINT,
                            text.getRightInset(), 0.01);
                    assertEquals(style.path("marginTopEmu").asLong() / EMU_PER_POINT,
                            text.getTopInset(), 0.01);
                    assertEquals(style.path("marginBottomEmu").asLong() / EMU_PER_POINT,
                            text.getBottomInset(), 0.01);
                    for (var paragraph : text.getTextParagraphs()) {
                        assertEquals(style.path("horizontalAlign").asText(), align(paragraph.getTextAlign()));
                        assertEquals(style.path("lineSpacingPermille").asInt() / 10d,
                                paragraph.getLineSpacing(), 0.01);
                        assertEquals(style.path("paragraphSpaceBeforeEmu").asLong() / EMU_PER_POINT,
                                value(paragraph.getSpaceBefore()), 0.01);
                        assertEquals(style.path("paragraphSpaceAfterEmu").asLong() / EMU_PER_POINT,
                                value(paragraph.getSpaceAfter()), 0.01);
                        for (var run : paragraph.getTextRuns()) {
                            assertEquals(style.path("fontFamily").asText(), run.getFontFamily());
                            assertEquals(style.path("fontSizeHundredthPt").asInt() / 100d,
                                    run.getFontSize(), 0.01);
                            assertEquals(style.path("fontWeight").asInt() >= 600, run.isBold());
                            assertEquals(style.path("textColor").asText(), hex(color(run.getFontColor())));
                        }
                    }
                }
            }
        }
    }

    @Test
    void executesShapeImageShadowAndConnectorPaintFromThePlan() throws Exception {
        RendererTestSupport.RenderedFixture fixture = RendererTestSupport.renderedFixture();
        int plannedShadows = 0;
        try (XMLSlideShow show = RendererTestSupport.open(fixture.pptx())) {
            for (int slideIndex = 0; slideIndex < show.getSlides().size(); slideIndex++) {
                JsonNode elements = fixture.document().path("slides").get(slideIndex).path("elements");
                var shapes = show.getSlides().get(slideIndex).getShapes();
                for (int index = 0; index < shapes.size(); index++) {
                    JsonNode element = elements.get(index);
                    if (!(shapes.get(index) instanceof XSLFSimpleShape actual)) {
                        continue;
                    }
                    JsonNode style = element.path("resolvedStyle");
                    String type = element.path("elementType").asText();
                    if ("SHAPE".equals(type)) {
                        assertEquals(element.path("shapeType").asText(), shapeType(actual.getShapeType()));
                        assertEquals(style.path("fillColor").asText(), hex(actual.getFillColor()));
                    }
                    if ("SHAPE".equals(type) || "IMAGE".equals(type)) {
                        long borderWidth = style.path("borderWidthEmu").asLong();
                        if (borderWidth > 0) {
                            assertEquals(style.path("borderColor").asText(), hex(actual.getLineColor()));
                            assertEquals(borderWidth / EMU_PER_POINT, actual.getLineWidth(), 0.01);
                        }
                    }
                    if (style.path("shadow").isObject()) {
                        plannedShadows++;
                        assertNotNull(actual.getShadow(), element.path("elementId").asText());
                    }
                    if (actual instanceof XSLFConnectorShape connector) {
                        assertEquals(style.path("lineColor").asText(), hex(connector.getLineColor()));
                        assertEquals(style.path("lineWidthEmu").asLong() / EMU_PER_POINT,
                                connector.getLineWidth(), 0.01);
                        assertTrue(connector.getLineHeadDecoration() == null
                                        || connector.getLineHeadDecoration() == LineDecoration.DecorationShape.NONE,
                                "NONE may be represented by an absent OOXML head decoration");
                        assertEquals(LineDecoration.DecorationShape.TRIANGLE, connector.getLineTailDecoration());
                    }
                }
            }
        }

        assertEquals(54, plannedShadows);
        String slideXml = String.join("\n", RendererTestSupport.slideXml(fixture.pptx()));
        assertEquals(plannedShadows,
                Pattern.compile("<a:outerShdw(?:\\s|>)").matcher(slideXml).results().count());
    }

    private double value(Double value) {
        return value == null ? 0d : value;
    }

    private String vertical(VerticalAlignment value) {
        return (value == null ? VerticalAlignment.TOP : value).name();
    }

    private String align(TextParagraph.TextAlign value) {
        return (value == null ? TextParagraph.TextAlign.LEFT : value).name();
    }

    private String shapeType(ShapeType value) {
        return switch (value) {
            case RECT -> "RECTANGLE";
            case ROUND_RECT -> "ROUNDED_RECTANGLE";
            default -> value.name();
        };
    }

    private Color color(PaintStyle paint) {
        if (paint instanceof PaintStyle.SolidPaint solid) {
            return solid.getSolidColor().getColor();
        }
        throw new AssertionError("Expected solid text paint but was " + paint);
    }

    private String hex(Color color) {
        assertNotNull(color);
        return String.format(Locale.ROOT, "#%02X%02X%02X",
                color.getRed(), color.getGreen(), color.getBlue());
    }

}
