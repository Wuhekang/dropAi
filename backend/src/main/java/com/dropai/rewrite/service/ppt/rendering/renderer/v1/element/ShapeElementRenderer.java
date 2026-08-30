package com.dropai.rewrite.service.ppt.rendering.renderer.v1.element;

import com.dropai.rewrite.service.ppt.rendering.contract.v1.PptQualityCode;
import com.dropai.rewrite.service.ppt.rendering.renderer.v1.RendererExecutionException;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.apache.poi.sl.usermodel.ShapeType;
import org.apache.poi.xslf.usermodel.XSLFAutoShape;

/** Executes native editable DrawingML shapes. */
public final class ShapeElementRenderer implements ElementRenderer {
    @Override
    public String elementType() {
        return "SHAPE";
    }

    @Override
    public void render(ObjectNode element, ElementRenderContext context) {
        String elementId = PlanElementSupport.requiredText(element, "elementId");
        try {
            ObjectNode style = PlanElementSupport.requiredObject(element, "resolvedStyle");
            XSLFAutoShape shape = context.slide().createAutoShape();
            shape.setShapeType(shapeType(PlanElementSupport.requiredText(element, "shapeType")));
            shape.setAnchor(PlanElementSupport.anchor(element));
            shape.setFillColor(PlanElementSupport.color(style, "fillColor"));
            int opacityPermille = PlanElementSupport.requiredInt(style, "opacityPermille");
            PoiElementSupport.applySolidFillOpacity(PoiElementSupport.shapeProperties(shape), opacityPermille);

            long borderWidthEmu = PlanElementSupport.requiredLong(style, "borderWidthEmu");
            if (borderWidthEmu == 0) {
                shape.setLineColor(null);
            } else {
                shape.setLineColor(PlanElementSupport.color(style, "borderColor"));
                shape.setLineWidth(PlanElementSupport.points(borderWidthEmu));
            }
            long cornerRadiusEmu = PlanElementSupport.requiredLong(style, "cornerRadiusEmu");
            PoiElementSupport.applyRoundedGeometry(
                    PoiElementSupport.shapeProperties(shape),
                    cornerRadiusEmu,
                    PlanElementSupport.requiredLong(element, "widthEmu"),
                    PlanElementSupport.requiredLong(element, "heightEmu"));
            if (style.path("shadow").isObject()) {
                PoiElementSupport.applyShadow(
                        PoiElementSupport.shapeProperties(shape),
                        (ObjectNode) style.path("shadow"));
            }
        } catch (RuntimeException exception) {
            if (exception instanceof RendererExecutionException renderer) {
                throw renderer;
            }
            throw context.failure(PptQualityCode.OOXML_PACKAGE_INVALID,
                    "Failed to execute SHAPE element " + elementId, elementId, exception);
        }
    }

    private static ShapeType shapeType(String value) {
        return switch (value) {
            case "RECTANGLE" -> ShapeType.RECT;
            case "ROUNDED_RECTANGLE" -> ShapeType.ROUND_RECT;
            case "ELLIPSE" -> ShapeType.ELLIPSE;
            default -> throw new IllegalArgumentException("Unsupported shapeType: " + value);
        };
    }
}
