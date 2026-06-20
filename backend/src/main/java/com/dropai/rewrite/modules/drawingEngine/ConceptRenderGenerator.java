package com.dropai.rewrite.modules.drawingEngine;

import com.dropai.rewrite.modules.model.DesignProject;

class ConceptRenderGenerator {
    DrawingEngine.Canvas draw(DesignProject project) {
        DrawingEngine.Canvas c = new DrawingEngine.Canvas(project.getProjectTitle(), "\u8bbe\u5907\u7ed3\u6784\u793a\u610f\u56fe", "FA-01");
        c.text("TEXT", 50, 548, 8.5, trim(project.getProjectTitle(), 34));
        c.text("TEXT", 50, 524, 4.6, "Color concept diagram - \u5f69\u8272\u65b9\u6848\u56fe\u7528\u4e8e\u8bba\u6587\u548c\u7b54\u8fa9\u5c55\u793a\uff1bCAD\u5de5\u7a0b\u56fe\u5355\u72ec\u751f\u6210\u3002");

        drawMachine(c);
        drawLabels(c);
        drawLegend(c);
        return c;
    }

    private void drawMachine(DrawingEngine.Canvas c) {
        c.fillRect("BASE", 96, 150, 520, 70);
        c.rect("BASE", 96, 150, 520, 70);
        c.text("TEXT", 308, 190, 5.2, "\u5c65\u5e26\u673a\u6784");

        c.fillRect("FRAME", 146, 230, 430, 118);
        c.rect("FRAME", 146, 230, 430, 118);
        c.text("TEXT", 324, 294, 5.2, "\u673a\u67b6");

        c.fillRect("FUNCTION", 94, 250, 62, 78);
        c.rect("FUNCTION", 94, 250, 62, 78);
        c.circle("FUNCTION", 125, 289, 24);
        c.text("TEXT", 101, 342, 4.3, "\u6e05\u626b\u7cfb\u7edf");

        c.fillRect("JOINT", 132, 166, 50, 38);
        c.fillRect("JOINT", 526, 166, 50, 38);
        c.circle("JOINT", 157, 185, 19);
        c.circle("JOINT", 551, 185, 19);

        for (int i = 0; i < 5; i++) {
            double x = 225 + i * 56;
            c.circle("JOINT", x, 184, 13);
        }

        c.fillRect("INTERFACE", 410, 360, 112, 64);
        c.rect("INTERFACE", 410, 360, 112, 64);
        c.text("TEXT", 425, 393, 4.6, "\u63a7\u5236\u7bb1");

        c.fillRect("SUPPORT", 210, 364, 126, 44);
        c.rect("SUPPORT", 210, 364, 126, 44);
        c.text("TEXT", 228, 391, 4.5, "\u68c0\u6d4b\u7cfb\u7edf");

        c.fillRect("FUNCTION", 246, 118, 214, 26);
        c.rect("FUNCTION", 246, 118, 214, 26);
        c.text("TEXT", 287, 136, 4.2, "\u5438\u9644\u7cfb\u7edf");

        c.line("STRUCTURE", 250, 230, 214, 408);
        c.line("STRUCTURE", 482, 230, 522, 424);
    }

    private void drawLabels(DrawingEngine.Canvas c) {
        label(c, "\u5c65\u5e26\u673a\u6784", 356, 185, 70, 166);
        label(c, "\u6e05\u626b\u7cfb\u7edf", 124, 290, 52, 322);
        label(c, "\u68c0\u6d4b\u7cfb\u7edf", 272, 386, 205, 444);
        label(c, "\u5438\u9644\u7cfb\u7edf", 352, 132, 274, 90);
        label(c, "\u63a7\u5236\u7bb1", 466, 392, 560, 444);
        label(c, "\u673a\u67b6", 358, 290, 608, 304);
    }

    private void label(DrawingEngine.Canvas c, String text, double x, double y, double tx, double ty) {
        c.line("ANNOTATION", x, y, tx, ty);
        c.circle("ANNOTATION", x, y, 3);
        c.text("ANNOTATION", tx + 4, ty, 4.4, text);
    }

    private void drawLegend(DrawingEngine.Canvas c) {
        double x = 640;
        double y = 382;
        c.text("TEXT", x, y + 64, 5.2, "Functional areas / \u529f\u80fd\u533a\u57df");
        swatch(c, "BASE", x, y + 34, "\u884c\u8d70\u673a\u6784");
        swatch(c, "FRAME", x, y + 8, "\u627f\u8f7d\u673a\u67b6");
        swatch(c, "FUNCTION", x, y - 18, "\u5de5\u4f5c\u6a21\u5757");
        swatch(c, "SUPPORT", x, y - 44, "\u68c0\u6d4b\u6a21\u5757");
        swatch(c, "INTERFACE", x, y - 70, "\u63a7\u5236\u63a5\u53e3");
        c.text("TEXT", 56, 64, 4.2, "\u672c\u56fe\u7528\u4e8e\u8bf4\u660e\u8bbe\u5907\u7ed3\u6784\uff0c\u5df2\u7701\u7565\u5c3a\u5bf8\u94fe\u548cBOM\u3002");
    }

    private void swatch(DrawingEngine.Canvas c, String layer, double x, double y, String text) {
        c.fillRect(layer, x, y, 24, 14);
        c.rect(layer, x, y, 24, 14);
        c.text("TEXT", x + 34, y + 12, 4.0, text);
    }

    private String trim(String value, int n) {
        if (value == null || value.isBlank()) return "\u6f14\u793a\u8bbe\u5907\u7ed3\u6784\u793a\u610f\u56fe";
        return value.length() > n ? value.substring(0, n) + "..." : value;
    }
}
