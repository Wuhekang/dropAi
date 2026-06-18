package com.dropai.rewrite.modules.drawingEngine;

import com.dropai.rewrite.modules.model.DesignProject;
import org.springframework.stereotype.Service;

@Service
public class DimensionEngine {
    public void drawPlanDimensions(DrawingEngine.Canvas c, DesignProject project) {
        drawViewDimensions(c, project.getDrawingPlan().getMainView(), "front");
        drawViewDimensions(c, project.getDrawingPlan().getTopView(), "top");
        drawViewDimensions(c, project.getDrawingPlan().getSideView(), "side");
    }

    private void drawViewDimensions(DrawingEngine.Canvas c, DesignProject.DrawingViewPlan view, String type) {
        double x = vp(view, "x", 60);
        double y = vp(view, "y", 300);
        double w = vp(view, "width", 400);
        double h = vp(view, "height", 150);
        int index = 0;
        for (DesignProject.DimensionChain item : view.getDimensions().stream().limit(4).toList()) {
            String label = label(item);
            if ("side".equals(type)) {
                double xx = x + w + 12 + index * 11;
                double y1 = y + 8 + index * 7;
                double y2 = y + h - 8 - index * 7;
                dimNoLabel(c, xx, y1, xx, y2);
                c.text("DIMENSION", xx + 5, (y1 + y2) / 2, 3, label);
            } else if ("top".equals(type) || index % 2 == 0) {
                double yy = y - 15 - index * 12;
                dim(c, x + 8, yy, x + w - 8, yy, label);
            } else {
                double xx = x - 16 - index * 10;
                dim(c, xx, y + 8, xx, y + h - 8, label);
            }
            index++;
        }
    }

    public void drawPartDimensions(DrawingEngine.Canvas c, DesignProject.Component part) {
        double length = Math.max(80, part.getLength());
        double width = Math.max(40, part.getWidth());
        double height = Math.max(35, part.getHeight());
        dim(c, 145, 215, 485, 215, "总长 " + fmt(length));
        dim(c, 125, 245, 125, 405, "高度 " + fmt(height));
        dim(c, 170, 435, 470, 435, "宽度 " + fmt(width));
        c.text("DIMENSION", 510, 365, 3.2, "安装孔：4×M8");
        c.text("DIMENSION", 510, 347, 3.2, "孔距：" + fmt(Math.max(60, length * .55)));
        c.text("DIMENSION", 510, 329, 3.2, "板厚/壁厚：" + fmt(Math.max(4, Math.min(12, height * .08))) + " mm");
    }

    public void drawHolePattern(DrawingEngine.Canvas c, double x, double y, double w, double h, int columns, int rows) {
        for (int i = 0; i < columns; i++) {
            for (int j = 0; j < rows; j++) {
                double cx = x + w * (i + 1) / (columns + 1);
                double cy = y + h * (j + 1) / (rows + 1);
                c.circle("CENTER", cx, cy, 3.2);
                c.line("CENTER", cx - 6, cy, cx + 6, cy);
                c.line("CENTER", cx, cy - 6, cx, cy + 6);
            }
        }
    }

    private String label(DesignProject.DimensionChain item) {
        if (item.getValue() <= 0) return trim(item.getName(), 16) + " 待校核";
        return trim(item.getName(), 16) + " " + fmt(item.getValue()) + item.getUnit();
    }

    private void dim(DrawingEngine.Canvas c, double x1, double y1, double x2, double y2, String label) {
        dimNoLabel(c, x1, y1, x2, y2);
        c.text("DIMENSION", (x1 + x2) / 2 + 4, (y1 + y2) / 2 + 7, 3, label);
    }

    private void dimNoLabel(DrawingEngine.Canvas c, double x1, double y1, double x2, double y2) {
        c.line("DIMENSION", x1, y1, x2, y2);
        c.line("DIMENSION", x1 - 4, y1 - 4, x1 + 4, y1 + 4);
        c.line("DIMENSION", x2 - 4, y2 - 4, x2 + 4, y2 + 4);
    }

    private double vp(DesignProject.DrawingViewPlan view, String key, double fallback) {
        if (view == null || view.getViewport() == null) return fallback;
        return view.getViewport().getOrDefault(key, fallback);
    }

    private String fmt(double value) {
        return "%.0f".formatted(value);
    }

    private String trim(String value, int length) {
        return value == null ? "" : value.length() > length ? value.substring(0, length) : value;
    }
}
