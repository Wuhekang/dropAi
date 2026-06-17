package com.dropai.rewrite.modules.drawingEngine;

import com.dropai.rewrite.modules.model.DesignProject;
import org.springframework.stereotype.Service;

@Service
public class IsometricViewEngine {
    public void drawIsometric(DrawingEngine.Canvas c, DesignProject p) {
        c.text("TEXT", 690, 520, 4, "轴测辅助图");
        double ox = 690, oy = 405, scale = 0.035;
        for (DesignProject.Component part : p.getComponents().stream().filter(DesignProject.Component::isKeyPart).limit(5).toList()) {
            double x = ox + part.getX() * scale * .75;
            double y = oy + part.getZ() * scale * .65 - part.getY() * scale * .25;
            double w = Math.max(10, part.getLength() * scale * .75);
            double d = Math.max(7, part.getWidth() * scale * .45);
            double h = Math.max(7, part.getHeight() * scale * .65);
            box(c, layer(part), x, y, w, d, h);
        }
    }

    private void box(DrawingEngine.Canvas c, String layer, double x, double y, double w, double d, double h) {
        c.rect(layer, x, y, w, h);
        c.line(layer, x, y + h, x + d, y + h + d);
        c.line(layer, x + w, y + h, x + w + d, y + h + d);
        c.line(layer, x + d, y + h + d, x + w + d, y + h + d);
        c.line(layer, x + w, y, x + w + d, y + d);
        c.line(layer, x + w + d, y + d, x + w + d, y + h + d);
    }

    private String layer(DesignProject.Component c) {
        return switch (c.getRole()) {
            case "BODY" -> "BODY";
            case "SUPPORT", "BASE" -> "SUPPORT";
            case "INTERFACE" -> "INTERFACE";
            case "FUNCTION" -> "FUNCTION";
            default -> "STRUCTURE";
        };
    }
}
