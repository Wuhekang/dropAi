package com.dropai.rewrite.service.ppt.rendering.renderer.v1.element;

import com.dropai.rewrite.service.ppt.rendering.contract.v1.PptQualityCode;
import com.dropai.rewrite.service.ppt.rendering.renderer.v1.RendererExecutionException;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.apache.poi.sl.usermodel.LineDecoration;
import org.apache.poi.sl.usermodel.ShapeType;
import org.apache.poi.sl.usermodel.StrokeStyle;
import org.apache.poi.xslf.usermodel.XSLFConnectorShape;

import java.awt.geom.Rectangle2D;

/** Executes connector endpoints and line styling without snapping or obstacle routing. */
public final class ConnectorElementRenderer implements ElementRenderer {
    @Override
    public String elementType() {
        return "CONNECTOR";
    }

    @Override
    public void render(ObjectNode element, ElementRenderContext context) {
        String elementId = PlanElementSupport.requiredText(element, "elementId");
        try {
            long startX = PlanElementSupport.requiredLong(element, "startXEmu");
            long startY = PlanElementSupport.requiredLong(element, "startYEmu");
            long endX = PlanElementSupport.requiredLong(element, "endXEmu");
            long endY = PlanElementSupport.requiredLong(element, "endYEmu");
            long x = Math.min(startX, endX);
            long y = Math.min(startY, endY);
            long width = Math.max(1L, Math.abs(Math.subtractExact(endX, startX)));
            long height = Math.max(1L, Math.abs(Math.subtractExact(endY, startY)));

            XSLFConnectorShape connector = context.slide().createConnector();
            connector.setShapeType(switch (PlanElementSupport.requiredText(element, "lineType")) {
                case "STRAIGHT" -> ShapeType.STRAIGHT_CONNECTOR_1;
                case "ELBOW" -> ShapeType.BENT_CONNECTOR_3;
                default -> throw new IllegalArgumentException("Unsupported connector lineType");
            });
            connector.setAnchor(new Rectangle2D.Double(
                    PlanElementSupport.points(x),
                    PlanElementSupport.points(y),
                    PlanElementSupport.points(width),
                    PlanElementSupport.points(height)));
            connector.setFlipHorizontal(endX < startX);
            connector.setFlipVertical(endY < startY);

            ObjectNode style = PlanElementSupport.requiredObject(element, "resolvedStyle");
            connector.setLineColor(PlanElementSupport.color(style, "lineColor"));
            connector.setLineWidth(PlanElementSupport.points(
                    PlanElementSupport.requiredLong(style, "lineWidthEmu")));
            connector.setLineDash(StrokeStyle.LineDash.valueOf(
                    PlanElementSupport.requiredText(style, "dashStyle")));
            connector.setLineHeadDecoration(arrow(element.path("startArrow").asText("NONE")));
            connector.setLineTailDecoration(arrow(element.path("endArrow").asText("NONE")));
        } catch (RuntimeException exception) {
            if (exception instanceof RendererExecutionException renderer) {
                throw renderer;
            }
            throw context.failure(PptQualityCode.OOXML_PACKAGE_INVALID,
                    "Failed to execute CONNECTOR element " + elementId, elementId, exception);
        }
    }

    private static LineDecoration.DecorationShape arrow(String value) {
        return switch (value) {
            case "NONE" -> LineDecoration.DecorationShape.NONE;
            case "TRIANGLE" -> LineDecoration.DecorationShape.TRIANGLE;
            case "STEALTH" -> LineDecoration.DecorationShape.STEALTH;
            default -> throw new IllegalArgumentException("Unsupported connector arrow: " + value);
        };
    }
}
