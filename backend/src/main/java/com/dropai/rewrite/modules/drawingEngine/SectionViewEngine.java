package com.dropai.rewrite.modules.drawingEngine;

import com.dropai.rewrite.modules.model.DesignProject;
import org.springframework.stereotype.Service;

@Service
public class SectionViewEngine {
    public void drawSections(DrawingEngine.Canvas c, DesignProject p) {
        c.line("CUTTING", 250, 475, 250, 292);
        c.text("CUTTING", 238, 482, 4, "A");
        c.text("CUTTING", 238, 290, 4, "A");
        c.line("CUTTING", 515, 460, 665, 460);
        c.text("CUTTING", 505, 466, 4, "B");
        c.text("CUTTING", 668, 466, 4, "B");

        c.text("TEXT", 370, 532, 4, "A-A剖面");
        c.rect("SECTION", 360, 475, 135, 45);
        hatch(c, 360, 475, 135, 45);
        c.rect("SECTION", 382, 488, 90, 20);
        c.text("TEXT", 370, 466, 3.2, "壳体板厚t=" + fmt(p.number("壳体板厚", 4)));

        c.text("TEXT", 525, 532, 4, "B-B剖面");
        c.rect("SECTION", 520, 475, 100, 45);
        hatch(c, 520, 475, 100, 45);
        c.circle("SECTION", 570, 498, 15);
        c.text("TEXT", 525, 466, 3.2, "接口内腔与壁厚表达");
    }

    private void hatch(DrawingEngine.Canvas c, double x, double y, double w, double h) {
        for (double i = -h; i < w; i += 10) {
            c.line("HATCH", x + Math.max(0, i), y, x + Math.min(w, i + h), y + Math.min(h, h + i));
        }
    }

    private String fmt(double v) { return "%.0f".formatted(v); }
}
