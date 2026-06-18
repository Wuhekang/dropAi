package com.dropai.rewrite.modules.drawingEngine;

import com.dropai.rewrite.modules.model.DesignProject;
import org.springframework.stereotype.Service;

@Service
public class DimensionEngine {
    public void drawAssemblyDimensions(DrawingEngine.Canvas c, DesignProject p) {
        dim(c, 70, 285, 470, 285, "总长");
        dim(c, 55, 300, 55, 460, "总高");
        dim(c, 70, 88, 470, 88, "总宽");
    }

    public void drawPlanDimensions(DrawingEngine.Canvas c, DesignProject p) {
        drawViewDimensions(c, p.getDrawingPlan().getMainView(), "main");
        drawViewDimensions(c, p.getDrawingPlan().getTopView(), "top");
        drawViewDimensions(c, p.getDrawingPlan().getSideView(), "side");
    }

    private void drawViewDimensions(DrawingEngine.Canvas c, DesignProject.DrawingViewPlan view, String type) {
        double x = vp(view, "x", 60);
        double y = vp(view, "y", 300);
        double w = vp(view, "width", 400);
        double h = vp(view, "height", 150);
        int index = 0;
        for (DesignProject.DimensionChain item : view.getDimensions().stream().limit(4).toList()) {
            boolean horizontal = switch (type) {
                case "top" -> index < 3;
                case "side" -> false;
                default -> index % 2 == 0;
            };
            if (horizontal) {
                double yy = y - 15 - index * 13;
                dim(c, x + 8, yy, x + w - 8, yy, label(item));
            } else if ("side".equals(type)) {
                double xx = x + w + 12 + index * 10;
                double y1 = y + 8 + index * 7;
                double y2 = y + h - 8 - index * 7;
                dimNoLabel(c, xx, y1, xx, y2);
                c.text("DIMENSION", xx + 5, y + h - 20 - index * 18, 3, label(item));
            } else {
                double xx = x - 16 - index * 10;
                dim(c, xx, y + 8, xx, y + h - 8, label(item));
            }
            index++;
        }
    }

    public void drawPartDimensions(DrawingEngine.Canvas c, DesignProject.Component p, double thickness) {
        dim(c, 160, 205, 520, 205, "L=" + fmt(p.getLength()));
        dim(c, 140, 230, 140, 420, "H=" + fmt(p.getHeight()));
        dim(c, 195, 245, 485, 245, "孔距 " + fmt(Math.max(120, p.getLength() * .58)));
        c.text("DIMENSION", 510, 360, 3.2, "安装孔：4×M18");
        c.text("DIMENSION", 510, 342, 3.2, "板厚 t=" + fmt(thickness));
        c.text("DIMENSION", 510, 324, 3.2, "倒角C2，未注圆角R3");
    }

    private String label(DesignProject.DimensionChain item) {
        return trim(item.getName(), 18) + " " + fmt(item.getValue()) + item.getUnit();
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

    private String fmt(double v) {
        return "%.0f".formatted(v);
    }

    private String trim(String v, int n) {
        return v == null ? "" : v.length() > n ? v.substring(0, n) : v;
    }
}
