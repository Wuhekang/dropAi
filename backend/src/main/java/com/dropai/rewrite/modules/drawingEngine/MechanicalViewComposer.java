package com.dropai.rewrite.modules.drawingEngine;

import com.dropai.rewrite.modules.model.DesignProject;

import java.util.List;

class MechanicalViewComposer {
    DrawingEngine.Canvas compose(DesignProject project, MechanicalDrawingAgent.DrawingRepairPlan plan, String deviceType) {
        if ("settling_chamber".equals(deviceType)) return settlingChamber(project, plan);
        return genericMechanical(project, plan);
    }

    private DrawingEngine.Canvas settlingChamber(DesignProject project, MechanicalDrawingAgent.DrawingRepairPlan plan) {
        DrawingEngine.Canvas c = sheet(project, "\u603b\u88c5\u4e09\u89c6\u56fe", "ZD-00");
        frontSettling(c, 62, 316, 430, 140);
        topSettling(c, 62, 126, 430, 112);
        sideSettling(c, 536, 270, 170, 176);
        assemblyBom(c, 586, 116);
        requirements(c, 586, 38, plan);
        return c;
    }

    private DrawingEngine.Canvas genericMechanical(DesignProject project, MechanicalDrawingAgent.DrawingRepairPlan plan) {
        DrawingEngine.Canvas c = sheet(project, "\u603b\u88c5\u4e09\u89c6\u56fe", "ZD-00");
        c.text("TEXT", 62, 470, 4.8, "\u4e3b\u89c6\u56fe");
        c.text("TEXT", 132, 470, 3.2, "Front view");
        c.rect("BODY", 84, 330, 360, 94);
        c.rect("STRUCTURE", 118, 352, 292, 46);
        c.line("HIDDEN", 124, 375, 404, 375);
        c.line("CENTER", 84, 377, 444, 377);
        c.text("TEXT", 62, 260, 4.8, "\u4fef\u89c6\u56fe");
        c.text("TEXT", 132, 260, 3.2, "Top view");
        c.rect("BODY", 84, 150, 360, 80);
        for (int i = 0; i < 6; i++) c.circle("CENTER", 132 + i * 52, 190, 4);
        c.text("TEXT", 536, 470, 4.8, "\u4fa7\u89c6\u56fe");
        c.text("TEXT", 606, 470, 3.2, "Side view");
        c.rect("BODY", 546, 314, 150, 96);
        c.rect("STRUCTURE", 572, 332, 96, 58);
        c.line("HIDDEN", 572, 361, 668, 361);
        dimensions(c, 84, 314, 360, 94, 546, 314, 150, 96);
        c.rect("TABLE", 586, 112, 194, 64);
        c.text("TABLE", 594, 160, 4.0, "Core BOM");
        c.text("TABLE", 594, 142, 3.0, "01 \u673a\u67b6");
        c.text("TABLE", 594, 128, 3.0, "02 \u9a71\u52a8\u5355\u5143");
        return c;
    }

    private DrawingEngine.Canvas sheet(DesignProject project, String drawingName, String no) {
        DrawingEngine.Canvas c = new DrawingEngine.Canvas(project.getProjectTitle(), drawingName, no);
        c.rect("FRAME", 20, 20, 800, 550);
        c.rect("FRAME", 30, 30, 780, 530);
        c.rect("TITLE", 570, 30, 240, 72);
        c.line("TITLE", 570, 55, 810, 55);
        c.line("TITLE", 700, 30, 700, 102);
        c.text("TEXT", 580, 82, 5, drawingName);
        c.text("TEXT", 710, 82, 3.8, "\u56fe\u53f7 " + no);
        c.text("TEXT", 580, 66, 3.5, "\u672c\u79d1\u6bd5\u4e1a\u8bbe\u8ba1\u5de5\u7a0b\u56fe");
        c.text("TEXT", 580, 42, 3.3, trim(project.getProjectTitle(), 20));
        c.text("TEXT", 710, 42, 3.3, "\u6bd4\u4f8b 1:10");
        return c;
    }

    private void frontSettling(DrawingEngine.Canvas c, double x, double y, double w, double h) {
        c.text("TEXT", x, y + h + 20, 4.8, "\u4e3b\u89c6\u56fe");
        c.text("TEXT", x + 70, y + h + 20, 3.2, "Front view");
        c.rect("BODY", x, y, w, h);
        c.rect("HIDDEN", x + 12, y + 12, w - 24, h - 24);
        c.poly("STRUCTURE", x + w * .27, y, x + w * .73, y, x + w * .62, y - 92, x + w * .38, y - 92);
        c.rect("INTERFACE", x + w * .45, y - 132, w * .1, 40);
        c.line("CENTER", x + w * .5, y - 144, x + w * .5, y - 78);
        c.rect("INTERFACE", x - 70, y + h * .42, 70, 36);
        c.poly("INTERFACE", x - 110, y + h * .38, x - 70, y + h * .42, x - 70, y + h * .68, x - 110, y + h * .72);
        c.rect("INTERFACE", x + w, y + h * .42, 76, 36);
        flange(c, x - 112, y + h * .55, 24, "\u8fdb\u6c14\u6cd5\u5170");
        flange(c, x + w + 78, y + h * .55, 24, "\u51fa\u6c14\u6cd5\u5170");
        c.rect("FUNCTION", x + w * .56, y + h * .28, 78, 52);
        c.rect("STRUCTURE", x + w * .56 + 8, y + h * .28 + 8, 62, 36);
        for (int i = 0; i < 7; i++) c.line("STRUCTURE", x + 38 + i * 56, y, x + 38 + i * 56, y + h);
        for (int i = 0; i < 4; i++) c.rect("SUPPORT", x + 38 + i * ((w - 76) / 3), y - 172, 20, 80);
        c.rect("SUPPORT", x + 22, y - 178, w - 44, 14);
        c.line("HIDDEN", x + 110, y + h - 18, x + 150, y + 18);
        c.line("HIDDEN", x + 190, y + h - 18, x + 230, y + 18);
        c.line("SECTION", x + 96, y + h + 8, x + 330, y + h + 8);
        c.text("TEXT", x + 206, y + h + 13, 3.2, "A-A");
        dimensions(c, x, y - 178, w, h + 178, x + w + 76, y, 0, h);
        balloon(c, 1, x + w * .5, y + h, x + w + 124, y + h + 12, "\u7bb1\u4f53");
        balloon(c, 2, x - 90, y + h * .55, x - 36, y + h + 34, "\u8fdb\u6c14\u7ba1");
        balloon(c, 3, x + w + 62, y + h * .55, x + w + 116, y + h - 18, "\u51fa\u6c14\u7ba1");
        balloon(c, 4, x + w * .5, y - 80, x + w + 116, y - 22, "\u7070\u6597");
        balloon(c, 5, x + w * .62, y + h * .55, x + w + 116, y + 46, "\u68c0\u4fee\u95e8");
    }

    private void topSettling(DrawingEngine.Canvas c, double x, double y, double w, double h) {
        c.text("TEXT", x, y + h + 20, 4.8, "\u4fef\u89c6\u56fe");
        c.text("TEXT", x + 70, y + h + 20, 3.2, "Top view");
        c.rect("BODY", x, y, w, h);
        c.rect("HIDDEN", x + 12, y + 12, w - 24, h - 24);
        c.rect("INTERFACE", x - 82, y + h * .34, 82, 34);
        c.rect("INTERFACE", x + w, y + h * .34, 82, 34);
        c.line("CENTER", x - 92, y + h * .5, x + w + 92, y + h * .5);
        for (int i = 0; i < 8; i++) c.line("STRUCTURE", x + 28 + i * 50, y + 10, x + 28 + i * 50, y + h - 10);
        c.rect("FUNCTION", x + w * .57, y + 18, 74, h - 36);
        for (int i = 0; i < 6; i++) {
            c.circle("CENTER", x - 40 + Math.cos(i * Math.PI / 3) * 22, y + h * .5 + Math.sin(i * Math.PI / 3) * 13, 2.4);
            c.circle("CENTER", x + w + 40 + Math.cos(i * Math.PI / 3) * 22, y + h * .5 + Math.sin(i * Math.PI / 3) * 13, 2.4);
        }
        c.text("ANNOTATION", x - 70, y + h + 2, 3.2, "\u8fdb\u6c14\u65b9\u5411");
        c.text("ANNOTATION", x + w + 4, y + h + 2, 3.2, "\u51fa\u6c14\u65b9\u5411");
        balloon(c, 6, x + w * .2, y + h - 12, x + w + 106, y + h + 14, "\u52a0\u5f3a\u7b4b");
        balloon(c, 7, x + w * .66, y + h * .5, x + w + 106, y + 42, "\u68c0\u4fee\u95e8");
    }

    private void sideSettling(DrawingEngine.Canvas c, double x, double y, double w, double h) {
        c.text("TEXT", x, y + h + 20, 4.8, "\u4fa7\u89c6\u56fe");
        c.text("TEXT", x + 70, y + h + 20, 3.2, "Side view");
        c.rect("BODY", x, y + 40, w, h - 40);
        c.poly("STRUCTURE", x + 24, y + 40, x + w - 24, y + 40, x + w * .62, y - 68, x + w * .38, y - 68);
        c.rect("INTERFACE", x + w * .42, y - 106, w * .16, 38);
        c.line("CENTER", x + w * .5, y - 116, x + w * .5, y - 54);
        c.rect("INTERFACE", x - 36, y + h * .44, 36, 32);
        c.rect("INTERFACE", x + w, y + h * .44, 36, 32);
        c.line("CENTER", x - 44, y + h * .54, x + w + 44, y + h * .54);
        c.line("HIDDEN", x + 48, y + h - 4, x + 92, y + 54);
        c.line("HIDDEN", x + 104, y + h - 4, x + 132, y + 54);
        c.rect("SUPPORT", x + 18, y - 150, 20, 82);
        c.rect("SUPPORT", x + w - 38, y - 150, 20, 82);
        c.rect("SUPPORT", x, y - 156, w, 14);
        for (int i = 0; i < 4; i++) c.line("HATCH", x + 30 + i * 26, y + 40, x + 8 + i * 26, y - 68);
        c.text("DIMENSION", x + w + 42, y + h * .5, 3.2, "\u8fdb\u51fa\u53e3\u9ad8\u5ea6");
        balloon(c, 8, x + w * .5, y - 84, x + w + 64, y - 54, "\u5378\u7070\u53e3");
        balloon(c, 9, x + 70, y + h * .62, x + w + 64, y + h + 8, "\u5bfc\u6d41\u677f");
    }

    private void assemblyBom(DrawingEngine.Canvas c, double x, double y) {
        c.rect("TABLE", x, y, 194, 126);
        c.text("TABLE", x + 8, y + 110, 4.4, "BOM");
        c.text("TABLE", x + 58, y + 110, 3.2, "Core BOM");
        List<String> rows = List.of("01 \u7bb1\u4f53 Q235B", "02 \u8fdb\u6c14\u7ba1 Q235B", "03 \u51fa\u6c14\u7ba1 Q235B", "04 \u7070\u6597 Q235B", "05 \u68c0\u4fee\u95e8 Q235B", "06 \u52a0\u5f3a\u7b4b Q235B", "07 \u652f\u6491\u67b6 Q235B", "08 \u5378\u7070\u53e3 Q235B");
        for (int i = 0; i < rows.size(); i++) c.text("TABLE", x + 8, y + 92 - i * 11, 3.1, rows.get(i));
    }

    private void requirements(DrawingEngine.Canvas c, double x, double y, MechanicalDrawingAgent.DrawingRepairPlan plan) {
        c.rect("TABLE", x, y, 194, 64);
        c.text("TABLE", x + 8, y + 50, 3.6, "\u6280\u672f\u8981\u6c42");
        c.text("TABLE", x + 8, y + 36, 2.9, "1. \u710a\u7f1d\u8fde\u7eed\u5747\u5300\uff0c\u710a\u540e\u53bb\u6bdb\u523a");
        c.text("TABLE", x + 8, y + 24, 2.9, "2. \u6cd5\u5170\u5b54\u9635\u5217\u6309\u4e2d\u5fc3\u7ebf\u52a0\u5de5");
        c.text("TABLE", x + 8, y + 12, 2.9, "3. \u56fe\u7eb8\u8d28\u91cf\u5206 " + plan.drawingQualityScore());
    }

    private void flange(DrawingEngine.Canvas c, double cx, double cy, double r, String label) {
        c.circle("INTERFACE", cx, cy, r);
        c.circle("INTERFACE", cx, cy, r * .52);
        c.line("CENTER", cx - r - 8, cy, cx + r + 8, cy);
        c.line("CENTER", cx, cy - r - 8, cx, cy + r + 8);
        for (int i = 0; i < 6; i++) {
            double a = i * Math.PI / 3;
            c.circle("CENTER", cx + Math.cos(a) * r * .74, cy + Math.sin(a) * r * .74, 2.5);
        }
        c.text("ANNOTATION", cx - r, cy - r - 12, 2.8, label);
    }

    private void dimensions(DrawingEngine.Canvas c, double x, double y, double w, double h, double sx, double sy, double sw, double sh) {
        c.line("DIMENSION", x, y - 18, x + w, y - 18);
        c.line("DIMENSION", x, y - 23, x, y - 13);
        c.line("DIMENSION", x + w, y - 23, x + w, y - 13);
        c.text("DIMENSION", x + w * .42, y - 30, 3.2, "\u603b\u957f L");
        c.line("DIMENSION", x - 18, y, x - 18, y + h);
        c.line("DIMENSION", x - 23, y, x - 13, y);
        c.line("DIMENSION", x - 23, y + h, x - 13, y + h);
        c.text("DIMENSION", x - 52, y + h * .5, 3.2, "\u603b\u9ad8 H");
    }

    private void balloon(DrawingEngine.Canvas c, int no, double x, double y, double tx, double ty, String label) {
        c.line("ANNOTATION", x, y, tx - 10, ty);
        c.circle("ANNOTATION", tx, ty, 6.5);
        c.text("ANNOTATION", tx - 3, ty - 2.6, 3.2, String.valueOf(no));
        c.text("ANNOTATION", tx + 10, ty - 2, 3.2, label);
    }

    private String trim(String value, int length) {
        if (value == null || value.isBlank()) return "\u6bd5\u4e1a\u8bbe\u8ba1";
        return value.length() > length ? value.substring(0, length) : value;
    }
}
