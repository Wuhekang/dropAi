package com.dropai.rewrite.modules.drawingEngine;

import com.dropai.rewrite.modules.model.DesignProject;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

@Service
public class PartDrawingEngine {
    private final DimensionEngine dimensionEngine = new DimensionEngine();
    private final AnnotationEngine annotationEngine = new AnnotationEngine();

    public List<DrawingArtifact> drawPartDrawing(DesignProject project) {
        List<DesignProject.Component> keyParts = project.getComponents().stream()
                .filter(DesignProject.Component::isKeyPart).limit(4).toList();
        List<DrawingArtifact> result = new ArrayList<>();
        for (int i = 0; i < keyParts.size(); i++) {
            DesignProject.Component p = keyParts.get(i);
            DrawingEngine.Canvas c = new DrawingEngine.Canvas(project.getProjectTitle(), p.getName() + "零件图", "LJ-%02d".formatted(i + 1));
            frame(c); title(c);
            drawPartGeometry(c, p);
            c.text("TEXT", 180, 440, 4, "零件：" + p.getName());
            c.text("TEXT", 180, 420, 3.5, "功能：" + p.getFunction());
            dimensionEngine.drawPartDimensions(c, p, project.number("壳体板厚", 4));
            annotationEngine.drawPartAnnotations(c, p);
            result.add(new DrawingArtifact("part_%02d.dxf".formatted(i + 1), c.dxf().getBytes(StandardCharsets.UTF_8), "application/dxf"));
        }
        return result;
    }

    private void drawPartGeometry(DrawingEngine.Canvas c, DesignProject.Component p) {
        String g = p.getGeometry() == null ? "BOX" : p.getGeometry();
        if (g.contains("CYLINDER") || "DUCT_X".equals(g) || "ROTOR".equals(g)) {
            c.circle("OUTLINE", 340, 325, 62);
            c.circle("CENTER", 340, 325, 32);
            for (double a = 0; a < 360; a += 90) {
                double r = Math.toRadians(a);
                c.circle("CENTER", 340 + Math.cos(r) * 45, 325 + Math.sin(r) * 45, 5);
            }
            c.line("CENTER", 250, 325, 430, 325);
            c.line("CENTER", 340, 235, 340, 415);
        } else if ("HOPPER".equals(g)) {
            c.poly("OUTLINE", 190, 400, 490, 400, 420, 255, 360, 225, 320, 225, 260, 255);
            hatch(c, 205, 375, 260, 16);
        } else if ("ARM_XZ".equals(g) || "CLAW".equals(g)) {
            c.poly("OUTLINE", 190, 300, 220, 270, 500, 375, 470, 405);
            c.circle("CENTER", 205, 286, 18);
            c.circle("CENTER", 485, 390, 16);
        } else {
            c.rect("OUTLINE", 180, 245, 330, 150);
            c.rect("STRUCTURE", 210, 275, 270, 90);
            for (double x : List.of(230d, 460d)) {
                c.circle("CENTER", x, 295, 8);
                c.circle("CENTER", x, 345, 8);
            }
            c.line("STRUCTURE", 210, 275, 480, 365);
            c.line("STRUCTURE", 210, 365, 480, 275);
        }
    }

    private void hatch(DrawingEngine.Canvas c, double x, double y, double w, double h) {
        for (double i = 0; i < w; i += 12) c.line("HATCH", x + i, y, x + i + 16, y + h);
    }

    private void frame(DrawingEngine.Canvas c) { c.rect("FRAME", 20, 20, 800, 550); c.rect("FRAME", 30, 30, 780, 530); }
    private void title(DrawingEngine.Canvas c) {
        c.rect("TITLE", 570, 30, 240, 75); c.line("TITLE", 570, 55, 810, 55); c.line("TITLE", 700, 30, 700, 105);
        c.text("TEXT", 580, 82, 5, c.name); c.text("TEXT", 710, 82, 4, "图号 " + c.no);
        c.text("TEXT", 580, 42, 3.5, trim(c.title, 15)); c.text("TEXT", 710, 42, 3.5, "比例 1:5");
    }
    private String trim(String v, int n) { return v == null ? "" : v.length() > n ? v.substring(0, n) + "..." : v; }
}
