package com.dropai.rewrite.modules.drawingEngine;

import com.dropai.rewrite.modules.model.DesignProject;

import java.util.List;

class ChapterDrawingEngine {
    List<Sheet> draw(DesignProject project) {
        return List.of(
                new Sheet("overall_structure", overallStructure(project), true),
                new Sheet("track_mechanism", trackMechanism(project), false),
                new Sheet("cleaning_mechanism", cleaningMechanism(project), false),
                new Sheet("frame_structure", frameStructure(project), false),
                new Sheet("drive_mechanism", driveMechanism(project), false)
        );
    }

    private DrawingEngine.Canvas sheet(DesignProject project, String name, String no) {
        DrawingEngine.Canvas c = new DrawingEngine.Canvas(project.getProjectTitle(), name, no);
        c.rect("FRAME", 20, 20, 800, 550);
        c.rect("FRAME", 30, 30, 780, 530);
        c.text("TEXT", 42, 548, 7.2, name);
        c.text("TEXT", 42, 526, 3.8, no + "  Chapter drawing, one subject per sheet");
        c.rect("TITLE", 570, 30, 240, 62);
        c.text("TEXT", 582, 72, 4.2, name);
        c.text("TEXT", 582, 52, 3.4, no);
        c.text("TEXT", 704, 52, 3.4, "Scale 1:8");
        return c;
    }

    private DrawingEngine.Canvas overallStructure(DesignProject project) {
        DrawingEngine.Canvas c = sheet(project, "Overall structure diagram", "FIG-3.4");
        c.rect("OUTLINE", 88, 148, 520, 270);
        c.poly("BODY", 170, 235, 505, 235, 570, 285, 235, 285);
        c.poly("STRUCTURE", 235, 285, 570, 285, 570, 340, 235, 340);
        c.rect("STRUCTURE", 230, 300, 300, 58);
        c.rect("INTERFACE", 405, 360, 94, 52);
        c.rect("SUPPORT", 255, 360, 105, 38);
        c.rect("FUNCTION", 112, 250, 72, 76);
        c.circle("FUNCTION", 148, 288, 30);
        c.rect("FUNCTION", 288, 204, 174, 22);
        c.circle("JOINT", 220, 232, 24);
        c.circle("JOINT", 510, 232, 24);
        c.rect("JOINT", 474, 338, 62, 32);
        callout(c, "control box", 452, 388, 638, 450);
        callout(c, "frame", 342, 330, 642, 416);
        callout(c, "crawler mechanism", 352, 220, 636, 380);
        callout(c, "suction cup assembly", 378, 214, 636, 344);
        callout(c, "cleaning brush disk", 148, 288, 52, 438);
        callout(c, "drive motor", 505, 354, 636, 308);
        c.text("TEXT", 88, 116, 3.8, "Purpose: Chapter 3 overall scheme. No full dimension chain on this sheet.");
        return c;
    }

    private DrawingEngine.Canvas trackMechanism(DesignProject project) {
        DrawingEngine.Canvas c = sheet(project, "Crawler walking mechanism", "FIG-4.1");
        c.text("TEXT", 62, 466, 4.8, "Front view");
        detailedTrack(c, 76, 312, 424, 82);
        c.text("TEXT", 62, 284, 4.8, "Top view");
        c.rect("BODY", 76, 170, 424, 74);
        for (int i = 0; i < 14; i++) c.rect("STRUCTURE", 84 + i * 29, 172, 16, 70);
        c.rect("SUPPORT", 116, 198, 356, 18);
        c.text("TEXT", 546, 466, 4.8, "Side view");
        detailedTrack(c, 548, 300, 180, 66);
        c.rect("STRUCTURE", 170, 405, 238, 30);
        c.rect("STRUCTURE", 170, 274, 238, 26);
        dimension(c, 76, 286, 500, 286, "track length");
        callout(c, "drive wheel", 116, 352, 560, 238);
        callout(c, "idler wheel", 462, 352, 560, 214);
        callout(c, "support rollers", 285, 350, 560, 190);
        callout(c, "track plates", 302, 206, 560, 166);
        callout(c, "mounting plate", 300, 420, 560, 142);
        return c;
    }

    private void detailedTrack(DrawingEngine.Canvas c, double x, double y, double w, double h) {
        c.rect("BODY", x, y, w, h);
        double r = h / 2;
        c.circle("JOINT", x + r, y + r, r - 4);
        c.circle("JOINT", x + w - r, y + r, r - 4);
        for (int i = 0; i < 6; i++) c.circle("SUPPORT", x + 92 + i * 45, y + r, 14);
        for (int i = 0; i < 12; i++) c.line("STRUCTURE", x + 24 + i * 31, y + 5, x + 38 + i * 31, y + h - 5);
        c.rect("STRUCTURE", x + 60, y + h + 12, w - 120, 24);
        c.line("CENTER", x + 20, y + r, x + w - 20, y + r);
    }

    private DrawingEngine.Canvas cleaningMechanism(DesignProject project) {
        DrawingEngine.Canvas c = sheet(project, "Cleaning mechanism", "FIG-4.3");
        c.text("TEXT", 74, 462, 4.8, "Left view");
        cleaningView(c, 148, 286);
        c.text("TEXT", 430, 462, 4.8, "Right view");
        c.rect("STRUCTURE", 440, 292, 210, 72);
        c.circle("FUNCTION", 545, 326, 54);
        for (int i = 0; i < 18; i++) {
            double a = Math.PI * 2 * i / 18;
            c.line("FUNCTION", 545, 326, 545 + Math.cos(a) * 70, 326 + Math.sin(a) * 70);
        }
        c.rect("JOINT", 512, 392, 66, 42);
        c.rect("STRUCTURE", 458, 246, 174, 30);
        numbered(c, 1, 545, 326, "brush disk", 690, 438);
        numbered(c, 2, 610, 326, "bristles", 690, 408);
        numbered(c, 3, 546, 408, "motor seat", 690, 378);
        numbered(c, 4, 500, 260, "bracket bolts", 690, 348);
        return c;
    }

    private void cleaningView(DrawingEngine.Canvas c, double cx, double cy) {
        c.circle("FUNCTION", cx, cy, 62);
        c.circle("CENTER", cx, cy, 16);
        for (int i = 0; i < 20; i++) {
            double a = Math.PI * 2 * i / 20;
            c.line("FUNCTION", cx + Math.cos(a) * 36, cy + Math.sin(a) * 36, cx + Math.cos(a) * 78, cy + Math.sin(a) * 78);
        }
        c.rect("STRUCTURE", cx - 34, cy + 82, 68, 52);
        c.rect("JOINT", cx - 12, cy + 24, 24, 74);
    }

    private DrawingEngine.Canvas frameStructure(DesignProject project) {
        DrawingEngine.Canvas c = sheet(project, "Frame structure", "FIG-4.5");
        c.text("TEXT", 60, 466, 4.8, "Front view");
        frameView(c, 72, 315, 300, 112);
        c.text("TEXT", 60, 276, 4.8, "Top view");
        frameTop(c, 72, 150, 300, 86);
        c.text("TEXT", 430, 466, 4.8, "Side view");
        frameView(c, 438, 318, 160, 98);
        c.text("TEXT", 430, 276, 4.8, "Simplified axonometric");
        c.poly("STRUCTURE", 438, 150, 620, 150, 680, 206, 498, 206);
        c.poly("STRUCTURE", 438, 150, 438, 230, 498, 286, 498, 206);
        c.poly("STRUCTURE", 620, 150, 620, 230, 680, 286, 680, 206);
        c.line("STRUCTURE", 438, 230, 620, 230);
        c.line("STRUCTURE", 498, 286, 680, 286);
        for (int i = 0; i < 5; i++) c.circle("CENTER", 110 + i * 52, 190, 4);
        callout(c, "mounting holes", 215, 190, 642, 128);
        callout(c, "reinforcing ribs", 222, 360, 642, 104);
        return c;
    }

    private void frameView(DrawingEngine.Canvas c, double x, double y, double w, double h) {
        c.rect("STRUCTURE", x, y, w, h);
        c.rect("STRUCTURE", x + 18, y + 16, w - 36, h - 32);
        c.line("STRUCTURE", x + 36, y + 16, x + w - 36, y + h - 16);
        c.line("STRUCTURE", x + w - 36, y + 16, x + 36, y + h - 16);
        for (int i = 0; i < 5; i++) {
            c.circle("CENTER", x + 42 + i * ((w - 84) / 4), y + 18, 4);
            c.circle("CENTER", x + 42 + i * ((w - 84) / 4), y + h - 18, 4);
        }
    }

    private void frameTop(DrawingEngine.Canvas c, double x, double y, double w, double h) {
        c.rect("STRUCTURE", x, y, w, h);
        c.rect("STRUCTURE", x + 16, y + 16, w - 32, h - 32);
        for (int i = 0; i < 6; i++) c.circle("CENTER", x + 36 + i * ((w - 72) / 5), y + h / 2, 4);
        c.rect("BODY", x + 58, y + 28, 54, 30);
        c.rect("BODY", x + w - 112, y + 28, 54, 30);
    }

    private DrawingEngine.Canvas driveMechanism(DesignProject project) {
        DrawingEngine.Canvas c = sheet(project, "Drive shaft mechanism", "FIG-4.6");
        double y = 318;
        c.line("CENTER", 74, y, 720, y);
        shaftSegment(c, 94, y - 16, 96, 32, "M8 thread");
        shaftSegment(c, 190, y - 28, 118, 56, "bearing seat");
        shaftSegment(c, 308, y - 22, 112, 44, "coupling");
        shaftSegment(c, 420, y - 35, 140, 70, "reducer");
        shaftSegment(c, 560, y - 46, 132, 92, "motor");
        c.rect("STRUCTURE", 334, y + 38, 68, 16);
        c.text("ANNOTATION", 338, y + 66, 3.8, "keyway 6x4");
        dimension(c, 94, 244, 190, 244, "L1 80");
        dimension(c, 190, 226, 308, 226, "L2 120");
        dimension(c, 308, 244, 420, 244, "L3 100");
        dimension(c, 420, 226, 560, 226, "L4 140");
        dimension(c, 560, 244, 692, 244, "L5 130");
        c.line("DIMENSION", 180, y - 28, 180, y + 28);
        c.text("DIMENSION", 142, y + 58, 3.6, "diameter d25");
        c.line("DIMENSION", 84, y - 16, 84, y + 16);
        c.text("DIMENSION", 54, y + 46, 3.6, "M8");
        callout(c, "drive shaft", 260, y, 92, 438);
        callout(c, "motor", 632, y, 690, 438);
        callout(c, "reducer", 486, y, 690, 408);
        callout(c, "coupling", 360, y, 690, 378);
        callout(c, "thread end", 118, y, 92, 408);
        return c;
    }

    private void shaftSegment(DrawingEngine.Canvas c, double x, double y, double w, double h, String label) {
        c.rect("BODY", x, y, w, h);
        c.text("TEXT", x + 8, y + h + 16, 3.3, label);
    }

    private void dimension(DrawingEngine.Canvas c, double x1, double y1, double x2, double y2, String text) {
        c.line("DIMENSION", x1, y1, x2, y2);
        c.line("DIMENSION", x1, y1 - 5, x1, y1 + 5);
        c.line("DIMENSION", x2, y2 - 5, x2, y2 + 5);
        c.text("DIMENSION", (x1 + x2) / 2 - 24, y1 - 8, 3.4, text);
    }

    private void callout(DrawingEngine.Canvas c, String text, double x, double y, double tx, double ty) {
        c.line("ANNOTATION", x, y, tx, ty);
        c.circle("ANNOTATION", x, y, 3);
        c.text("ANNOTATION", tx + 4, ty, 3.9, text);
    }

    private void numbered(DrawingEngine.Canvas c, int number, double x, double y, String text, double tx, double ty) {
        c.circle("ANNOTATION", tx, ty, 7);
        c.text("ANNOTATION", tx - 2.5, ty - 2.5, 3.5, String.valueOf(number));
        c.line("ANNOTATION", x, y, tx - 10, ty);
        c.text("ANNOTATION", tx + 13, ty, 3.8, text);
    }

    record Sheet(String key, DrawingEngine.Canvas canvas, boolean conceptLike) {
    }
}
