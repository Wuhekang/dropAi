package com.dropai.rewrite.modules.drawingEngine;

import com.dropai.rewrite.modules.model.DesignProject;

import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

class ConceptRenderGenerator {
    DrawingEngine.Canvas draw(DesignProject project) {
        DrawingEngine.Canvas c = new DrawingEngine.Canvas(project.getProjectTitle(), "结构方案展示图", "FA-01");
        c.text("TEXT", 50, 548, 9, project.getProjectTitle());
        c.text("TEXT", 50, 524, 4.5, "结构方案展示图用于论文和网页预览，正式工程图见CAD总装三视图。");

        List<DesignProject.Component> parts = conceptParts(project);
        Bounds b = bounds(parts.isEmpty() ? project.getComponents() : parts);
        c.rect("OUTLINE", 45, 122, 570, 360);
        c.text("TEXT", 55, 466, 5, "主要结构示意图");
        for (DesignProject.Component part : parts.stream().limit(16).toList()) {
            Projection pr = front(part, b, 68, 150, 515, 280);
            drawConceptSymbol(c, part, pr.x(), pr.y(), pr.w(), pr.h());
            leader(c, part, pr.x() + pr.w() / 2, pr.y() + pr.h() / 2);
        }

        c.rect("TABLE", 640, 122, 170, 360);
        c.text("TABLE", 654, 454, 5, "结构清单");
        int row = 0;
        for (DesignProject.BomItem item : project.getDrawingPlan().getBomTable().stream().limit(10).toList()) {
            c.text("TABLE", 654, 430 - row++ * 27, 3.4,
                    item.getSequence() + ". " + trim(item.getName(), 14) + " x" + item.getQuantity());
        }

        c.rect("TABLE", 45, 42, 570, 58);
        c.text("TABLE", 58, 82, 4, "功能区域：驱动、支撑、功能模块、接口、安装结构");
        c.text("TABLE", 58, 62, 3.6, "图纸清晰度评分：" + project.getDrawingPlan().getQualityScore()
                + "；仅展示关键结构，非完整零件投影");
        parameterSummary(c, project);
        return c;
    }

    private List<DesignProject.Component> conceptParts(DesignProject project) {
        Set<String> ids = new LinkedHashSet<>();
        if (project.getDrawingPlan() != null) {
            ids.addAll(project.getDrawingPlan().getMainView().getVisibleParts());
            ids.addAll(project.getDrawingPlan().getTopView().getVisibleParts());
            ids.addAll(project.getDrawingPlan().getSideView().getVisibleParts());
            project.getDrawingPlan().getSectionViews().forEach(view -> ids.addAll(view.getVisibleParts()));
            project.getDrawingPlan().getDetailViews().forEach(view -> ids.addAll(view.getVisibleParts()));
        }
        return project.getComponents().stream()
                .filter(component -> ids.contains(component.getPartId()) || component.isKeyPart())
                .sorted(Comparator.comparing(DesignProject.Component::isKeyPart).reversed()
                        .thenComparingInt(DesignProject.Component::getSequence))
                .limit(18)
                .toList();
    }

    private void drawConceptSymbol(DrawingEngine.Canvas c, DesignProject.Component part, double x, double y, double w, double h) {
        String geometry = part.getGeometry() == null ? "" : part.getGeometry().toUpperCase();
        String name = normalize(part.getName());
        String layer = layer(part);
        if (geometry.contains("TRACK") || name.contains("track") || name.contains("履带")) {
            roundedTrack(c, layer, x, y, w, h);
        } else if (geometry.contains("WHEEL") || name.contains("wheel") || name.contains("轮")) {
            c.circle(layer, x + w / 2, y + h / 2, Math.max(5, Math.min(w, h) / 2));
            c.circle("CENTER", x + w / 2, y + h / 2, Math.max(2, Math.min(w, h) / 4));
        } else if (geometry.contains("BRUSH") || name.contains("brush") || name.contains("刷")) {
            double r = Math.max(8, Math.min(w, h) / 2);
            c.circle(layer, x + w / 2, y + h / 2, r);
            for (int i = 0; i < 12; i++) {
                double a = Math.PI * 2 * i / 12;
                c.line(layer, x + w / 2, y + h / 2, x + w / 2 + Math.cos(a) * r, y + h / 2 + Math.sin(a) * r);
            }
        } else if (geometry.contains("MAGNET") || name.contains("magnet") || name.contains("磁")) {
            c.rect(layer, x, y, w, h);
            c.text(layer, x + 3, y + h / 2, 3, "MAG");
            c.line("HIDDEN", x + 3, y + 3, x + w - 3, y + h - 3);
        } else if (geometry.contains("SENSOR") || name.contains("sensor") || name.contains("检测")) {
            c.rect(layer, x, y, w, h);
            c.circle("ANNOTATION", x + w * .75, y + h / 2, 4);
            c.text(layer, x + 3, y + h / 2, 3, "S");
        } else {
            c.rect(layer, x, y, w, h);
        }
    }

    private void roundedTrack(DrawingEngine.Canvas c, String layer, double x, double y, double w, double h) {
        c.rect(layer, x, y, w, h);
        c.circle(layer, x + h / 2, y + h / 2, h / 2);
        c.circle(layer, x + w - h / 2, y + h / 2, h / 2);
        c.line("CENTER", x + h / 2, y + h / 2, x + w - h / 2, y + h / 2);
    }

    private void leader(DrawingEngine.Canvas c, DesignProject.Component part, double x, double y) {
        double tx = Math.min(585, x + 28);
        double ty = Math.min(450, y + 20);
        c.line("ANNOTATION", x, y, tx, ty);
        c.text("ANNOTATION", tx + 3, ty, 3.2, part.getSequence() + " " + trim(part.getName(), 11));
    }

    private void parameterSummary(DrawingEngine.Canvas c, DesignProject project) {
        c.rect("TABLE", 640, 42, 170, 58);
        c.text("TABLE", 654, 82, 4, "Parameter summary");
        int row = 0;
        for (DesignProject.Parameter parameter : project.getDrawingPlan().getParameterTable().stream().limit(2).toList()) {
            c.text("TABLE", 654, 64 - row++ * 17, 3,
                    trim(parameter.getName(), 10) + "=" + trim(String.valueOf(parameter.getValue()), 10) + parameter.getUnit());
        }
    }

    private Projection front(DesignProject.Component part, Bounds b, double ox, double oy, double vw, double vh) {
        double sx = (vw - 24) / Math.max(1, b.maxX() - b.minX());
        double sy = (vh - 24) / Math.max(1, b.maxZ() - b.minZ());
        double x = ox + 12 + (part.getX() - b.minX()) * sx;
        double y = oy + 12 + (part.getZ() - b.minZ()) * sy;
        return new Projection(x, y, Math.max(8, part.getLength() * sx), Math.max(7, part.getHeight() * sy));
    }

    private Bounds bounds(List<DesignProject.Component> parts) {
        double minX = parts.stream().mapToDouble(DesignProject.Component::getX).min().orElse(0);
        double minZ = parts.stream().mapToDouble(DesignProject.Component::getZ).min().orElse(0);
        double maxX = parts.stream().mapToDouble(c -> c.getX() + c.getLength()).max().orElse(1000);
        double maxZ = parts.stream().mapToDouble(c -> c.getZ() + c.getHeight()).max().orElse(600);
        return new Bounds(minX, minZ, maxX, maxZ);
    }

    private String layer(DesignProject.Component c) {
        return switch (c.getRole()) {
            case "BODY" -> "BODY";
            case "SUPPORT", "BASE" -> "SUPPORT";
            case "INTERFACE" -> "INTERFACE";
            case "FUNCTION" -> "FUNCTION";
            case "DRIVE" -> "JOINT";
            case "MOUNT" -> "STRUCTURE";
            default -> "STRUCTURE";
        };
    }

    private String normalize(String value) {
        return value == null ? "" : value.toLowerCase();
    }

    private String trim(String value, int n) {
        return value == null ? "" : value.length() > n ? value.substring(0, n) + "..." : value;
    }

    private record Projection(double x, double y, double w, double h) {}
    private record Bounds(double minX, double minZ, double maxX, double maxZ) {}
}
