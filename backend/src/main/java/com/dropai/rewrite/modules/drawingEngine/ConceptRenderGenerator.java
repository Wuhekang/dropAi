package com.dropai.rewrite.modules.drawingEngine;

import com.dropai.rewrite.modules.model.DesignProject;

import java.util.Comparator;
import java.util.List;

class ConceptRenderGenerator {
    private final EngineeringSemanticLayer semanticLayer = new EngineeringSemanticLayer();

    DrawingEngine.Canvas draw(DesignProject project) {
        DrawingEngine.Canvas c = new DrawingEngine.Canvas(project.getProjectTitle(), "整机装配爆炸图", "FA-01");
        c.text("TEXT", 50, 548, 8.5, trim(project.getProjectTitle(), 34));
        c.text("TEXT", 50, 524, 4.6, "整机装配爆炸图：用于展示零件编号、装配层级和安装关系；正式尺寸以CAD工程图为准。");
        drawAxis(c);
        drawExplodedAssembly(c, project);
        drawPartList(c, project);
        return c;
    }

    private void drawAxis(DrawingEngine.Canvas c) {
        c.line("CENTER", 74, 92, 178, 92);
        c.line("CENTER", 74, 92, 74, 172);
        c.line("CENTER", 74, 92, 126, 132);
        c.text("TEXT", 184, 92, 4, "X 装配长度");
        c.text("TEXT", 78, 178, 4, "Z 高度");
        c.text("TEXT", 132, 138, 4, "Y 宽度");
    }

    private void drawExplodedAssembly(DrawingEngine.Canvas c, DesignProject project) {
        List<DesignProject.Component> parts = project.getComponents().stream()
                .sorted(Comparator.comparing(DesignProject.Component::isKeyPart).reversed()
                        .thenComparingInt(DesignProject.Component::getSequence))
                .limit(10)
                .toList();
        double cx = 360;
        double cy = 280;
        c.rect("FRAME", cx - 120, cy - 42, 240, 84);
        c.text("TEXT", cx - 30, cy + 6, 4.6, "基准机架");

        for (int i = 0; i < parts.size(); i++) {
            DesignProject.Component part = parts.get(i);
            double angle = Math.PI * 2 * i / Math.max(1, parts.size());
            double px = cx + Math.cos(angle) * (175 + (i % 2) * 28);
            double py = cy + Math.sin(angle) * (118 + (i % 3) * 12);
            drawPartSymbol(c, part, px, py);
            c.line("ANNOTATION", cx, cy, px, py);
            c.circle("ANNOTATION", px - 18, py + 18, 8);
            c.text("ANNOTATION", px - 21, py + 15, 3.2, String.valueOf(part.getSequence()));
        }
    }

    private void drawPartSymbol(DrawingEngine.Canvas c, DesignProject.Component part, double x, double y) {
        String category = semanticLayer.semanticOf(part).category();
        switch (category) {
            case "track" -> {
                c.rect("BASE", x - 38, y - 14, 76, 28);
                c.circle("BASE", x - 26, y, 12);
                c.circle("BASE", x + 26, y, 12);
            }
            case "wheel", "bearing" -> {
                c.circle("JOINT", x, y, 18);
                c.circle("JOINT", x, y, 8);
            }
            case "motor" -> {
                c.rect("FUNCTION", x - 28, y - 14, 42, 28);
                c.circle("FUNCTION", x + 18, y, 13);
                c.line("CENTER", x + 30, y, x + 45, y);
            }
            case "reducer" -> {
                c.rect("FUNCTION", x - 26, y - 18, 52, 36);
                c.line("CENTER", x - 38, y, x + 38, y);
            }
            case "brush" -> {
                c.circle("FUNCTION", x, y, 24);
                for (int i = 0; i < 12; i++) {
                    double a = Math.PI * 2 * i / 12;
                    c.line("FUNCTION", x, y, x + Math.cos(a) * 30, y + Math.sin(a) * 30);
                }
            }
            case "magnet" -> {
                c.rect("INTERFACE", x - 34, y - 12, 68, 24);
                for (int i = 0; i < 4; i++) c.rect("INTERFACE", x - 26 + i * 17, y - 7, 10, 14);
            }
            case "sensor", "rail" -> {
                c.rect("SUPPORT", x - 36, y - 10, 72, 20);
                c.rect("SUPPORT", x - 16, y + 10, 32, 20);
            }
            case "cover" -> c.rect("STRUCTURE", x - 42, y - 20, 84, 40);
            default -> c.rect("FRAME", x - 34, y - 16, 68, 32);
        }
        c.text("TEXT", x - 34, y - 30, 3.4, trim(semanticLayer.drawingLabel(part), 10));
    }

    private void drawPartList(DrawingEngine.Canvas c, DesignProject project) {
        double x = 620;
        double y = 450;
        c.text("TEXT", x, y + 32, 5.2, "装配明细");
        c.rect("TABLE", x - 8, y - 150, 165, 172);
        int row = 0;
        for (DesignProject.Component part : project.getComponents().stream()
                .sorted(Comparator.comparingInt(DesignProject.Component::getSequence))
                .limit(9)
                .toList()) {
            c.text("TEXT", x, y - row * 17, 3.4, part.getSequence() + "  " + trim(semanticLayer.drawingLabel(part), 12));
            row++;
        }
        c.text("TEXT", 56, 64, 4.2, "爆炸图表达装配层级和相对安装关系，不作为尺寸检验图。");
    }

    private String trim(String value, int n) {
        if (value == null || value.isBlank()) return "机械装配图";
        return value.length() > n ? value.substring(0, n) + "..." : value;
    }
}
