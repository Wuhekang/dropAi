package com.dropai.rewrite.modules.drawingEngine;

import com.dropai.rewrite.modules.model.DesignProject;
import org.springframework.stereotype.Service;

@Service
public class DimensionEngine {
    public void drawAssemblyDimensions(DrawingEngine.Canvas c, DesignProject p) {
        double l = p.number("总长", 4200), w = p.number("总宽", 1600), h = p.number("总高", 1800);
        dim(c, 70, 285, 470, 285, "总长 " + fmt(l));
        dim(c, 55, 300, 55, 460, "总高 " + fmt(h));
        dim(c, 70, 88, 470, 88, "总宽投影 " + fmt(w));
        dim(c, 95, 248, 445, 248, "安装孔距 " + fmt(l * .72));
        dim(c, 120, 238, 420, 238, "支撑跨距 " + fmt(l * .64));
        dim(c, 520, 190, 665, 190, "侧向宽度 " + fmt(w));
        dim(c, 675, 205, 675, 370, "接口中心高 " + fmt(h * .55));
        c.text("DIMENSION", 690, 340, 3.2, "接口尺寸：Φ" + fmt(Math.min(w, h) * .22));
        c.text("DIMENSION", 690, 320, 3.2, "安装孔：4×Φ18");
        c.text("DIMENSION", 690, 300, 3.2, "板厚：t=" + fmt(p.number("壳体板厚", 4)) + " mm");
        c.text("DIMENSION", 690, 280, 3.2, "关键结构尺寸与参数表联动");
    }

    public void drawPartDimensions(DrawingEngine.Canvas c, DesignProject.Component p, double thickness) {
        dim(c, 160, 205, 520, 205, "L=" + fmt(p.getLength()));
        dim(c, 140, 230, 140, 420, "H=" + fmt(p.getHeight()));
        dim(c, 195, 245, 485, 245, "孔距 " + fmt(Math.max(120, p.getLength() * .58)));
        c.text("DIMENSION", 510, 360, 3.2, "孔径：2×Φ18");
        c.text("DIMENSION", 510, 342, 3.2, "板厚：t=" + fmt(thickness));
        c.text("DIMENSION", 510, 324, 3.2, "倒角：C2，未注圆角R3");
    }

    private void dim(DrawingEngine.Canvas c, double x1, double y1, double x2, double y2, String label) {
        c.line("DIMENSION", x1, y1, x2, y2);
        c.line("DIMENSION", x1 - 4, y1 - 4, x1 + 4, y1 + 4);
        c.line("DIMENSION", x2 - 4, y2 - 4, x2 + 4, y2 + 4);
        c.text("DIMENSION", (x1 + x2) / 2 + 5, (y1 + y2) / 2 + 8, 3.2, label);
    }

    private String fmt(double v) { return "%.0f".formatted(v); }
}
