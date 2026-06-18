package com.dropai.rewrite.modules.drawingEngine;

import com.dropai.rewrite.modules.model.DesignProject;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

@Service
public class DrawingEngine {
    private static final List<String> FONTS = List.of("Microsoft YaHei", "SimHei", "Dialog");
    private final DimensionEngine dimensionEngine = new DimensionEngine();
    private final AnnotationEngine annotationEngine = new AnnotationEngine();
    private final PartDrawingEngine partDrawingEngine = new PartDrawingEngine();

    public List<DrawingArtifact> drawAssemblyDrawing(DesignProject project) {
        validateDrawingPlan(project);
        Canvas c = new Canvas(project.getProjectTitle(), "总装工程图", "ZD-00");
        frame(c);
        title(c, project);
        planViews(c, project);
        dimensionEngine.drawPlanDimensions(c, project);
        planSections(c, project);
        planDetails(c, project);
        planIsometric(c, project);
        annotationEngine.drawAssemblyAnnotations(c, project);
        bom(c, project);
        parameterTable(c, project);
        requirements(c, project);
        return List.of(
                new DrawingArtifact("assembly.dxf", c.dxf().getBytes(StandardCharsets.UTF_8), "application/dxf"),
                new DrawingArtifact("cad_preview.svg", c.svg().getBytes(StandardCharsets.UTF_8), "image/svg+xml"),
                new DrawingArtifact("cad_preview.png", renderCad(c), "image/png"),
                new DrawingArtifact("preview.svg", showcaseSvg(project).getBytes(StandardCharsets.UTF_8), "image/svg+xml"),
                new DrawingArtifact("preview.png", renderShowcase(project), "image/png"));
    }

    public List<DrawingArtifact> drawPartDrawing(DesignProject project) {
        return partDrawingEngine.drawPartDrawing(project);
    }

    private void validateDrawingPlan(DesignProject project) {
        DesignProject.DrawingPlan plan = project.getDrawingPlan();
        if (plan == null
                || !"DrawingPlan".equals(plan.getInputSource())
                || plan.getMainView().getVisibleParts().isEmpty()
                || plan.getTopView().getVisibleParts().isEmpty()
                || plan.getSideView().getVisibleParts().isEmpty()
                || plan.getSectionViews().isEmpty()
                || plan.getDetailViews().isEmpty()) {
            throw new IllegalStateException("DrawingPlan为空，禁止生成CAD");
        }
    }

    private void frame(Canvas c) {
        c.rect("FRAME", 20, 20, 800, 550);
        c.rect("FRAME", 30, 30, 780, 530);
    }

    private void title(Canvas c, DesignProject p) {
        String drawingName = p.getDrawingPlan().getTitleBlock().getOrDefault("drawingName", c.name);
        String drawingNo = p.getDrawingPlan().getTitleBlock().getOrDefault("drawingNo", c.no);
        String scale = p.getDrawingPlan().getTitleBlock().getOrDefault("scale", "1:10");
        c.rect("TITLE", 570, 30, 240, 75);
        c.line("TITLE", 570, 55, 810, 55);
        c.line("TITLE", 700, 30, 700, 105);
        c.text("TEXT", 580, 82, 5, drawingName);
        c.text("TEXT", 710, 82, 4, "图号 " + drawingNo);
        c.text("TEXT", 580, 42, 3.5, trim(c.title, 18));
        c.text("TEXT", 710, 42, 3.5, "比例 " + scale);
        c.text("TEXT", 580, 66, 3, "CADGeneratorInputSource: " + p.getDrawingPlan().getInputSource());
    }

    private void planViews(Canvas c, DesignProject p) {
        double totalL = p.number("总长", p.number("整机长度", 4200));
        double totalW = p.number("总宽", p.number("整机宽度", 1600));
        double totalH = p.number("总高", p.number("整机高度", 1800));
        drawPlanView(c, p, p.getDrawingPlan().getMainView(), "FRONT", 70, 300, 400, 160, totalL, totalW, totalH, "主视图");
        drawPlanView(c, p, p.getDrawingPlan().getTopView(), "TOP", 70, 105, 400, 130, totalL, totalW, totalH, "俯视图");
        drawPlanView(c, p, p.getDrawingPlan().getSideView(), "SIDE", 520, 205, 145, 165, totalL, totalW, totalH, "侧视图");
    }

    private void drawPlanView(Canvas c, DesignProject p, DesignProject.DrawingViewPlan view, String orientation,
                              double ox, double oy, double vw, double vh, double totalL, double totalW, double totalH, String title) {
        c.text("TEXT", ox, oy - 18, 4, title + " / " + view.getName());
        c.rect("OUTLINE", ox, oy, vw, vh);
        for (DesignProject.Component part : planParts(p, view)) {
            projectPart(c, part, orientation, ox, oy, vw, vh, totalL, totalW, totalH);
            if (part.isKeyPart()) balloon(c, part, orientation, ox, oy, vw, vh, totalL, totalW, totalH);
        }
        int row = 0;
        for (String label : view.getLabels().stream().limit(4).toList()) {
            c.text("ANNOTATION", ox + vw + 8, oy + vh - 16 - row++ * 14, 2.8, trim(label, 22));
        }
        for (String center : view.getCenterLines()) {
            c.text("CENTER", ox + 8, oy + 12 + row++ * 10, 2.5, trim(center, 22));
        }
        for (String marker : view.getSectionMarkers()) c.text("CUTTING", ox + 10, oy + vh + 10, 3, trim(marker, 36));
    }

    private void balloon(Canvas c, DesignProject.Component part, String orientation, double ox, double oy, double vw, double vh,
                         double totalL, double totalW, double totalH) {
        double bx = ox + (part.getX() + part.getLength() / 2) / totalL * vw;
        double by = "TOP".equals(orientation)
                ? oy + (part.getY() + part.getWidth() / 2) / totalW * vh
                : oy + (part.getZ() + part.getHeight() / 2) / totalH * vh;
        c.circle("ANNOTATION", bx, by, 7);
        c.text("ANNOTATION", bx - 2, by - 2, 3.2, String.valueOf(part.getSequence()));
    }

    private List<DesignProject.Component> planParts(DesignProject p, DesignProject.DrawingViewPlan view) {
        return p.getComponents().stream().filter(component -> view.getVisibleParts().contains(component.getPartId())).toList();
    }

    private void projectPart(Canvas c, DesignProject.Component p, String view, double ox, double oy, double vw, double vh,
                             double totalL, double totalW, double totalH) {
        double x;
        double y;
        double w;
        double h;
        if ("TOP".equals(view)) {
            x = ox + p.getX() / totalL * vw;
            y = oy + p.getY() / totalW * vh;
            w = p.getLength() / totalL * vw;
            h = p.getWidth() / totalW * vh;
        } else if ("SIDE".equals(view)) {
            x = ox + p.getY() / totalW * vw;
            y = oy + p.getZ() / totalH * vh;
            w = p.getWidth() / totalW * vw;
            h = p.getHeight() / totalH * vh;
        } else {
            x = ox + p.getX() / totalL * vw;
            y = oy + p.getZ() / totalH * vh;
            w = p.getLength() / totalL * vw;
            h = p.getHeight() / totalH * vh;
        }
        w = Math.max(7, w);
        h = Math.max(6, h);
        drawGeometry(c, p, x, y, w, h);
    }

    private void drawGeometry(Canvas c, DesignProject.Component p, double x, double y, double w, double h) {
        String geometry = p.getGeometry() == null ? "PLATE" : p.getGeometry();
        String layer = layer(p);
        if ("TRACK".equals(geometry)) {
            c.rect(layer, x, y, w, h);
            c.circle(layer, x + Math.min(w, h) / 2, y + h / 2, Math.min(w, h) / 2);
            c.circle(layer, x + w - Math.min(w, h) / 2, y + h / 2, Math.min(w, h) / 2);
            c.line("CENTER", x + w * .15, y + h / 2, x + w * .85, y + h / 2);
        } else if ("WHEEL".equals(geometry) || "BRUSH".equals(geometry)) {
            c.circle(layer, x + w / 2, y + h / 2, Math.max(4, Math.min(w, h) / 2));
            c.line("CENTER", x + w / 2 - 8, y + h / 2, x + w / 2 + 8, y + h / 2);
            c.line("CENTER", x + w / 2, y + h / 2 - 8, x + w / 2, y + h / 2 + 8);
            if ("BRUSH".equals(geometry)) {
                for (int i = 0; i < 8; i++) {
                    double a = Math.PI * 2 * i / 8;
                    c.line("STRUCTURE", x + w / 2, y + h / 2,
                            x + w / 2 + Math.cos(a) * Math.min(w, h) / 2,
                            y + h / 2 + Math.sin(a) * Math.min(w, h) / 2);
                }
            }
        } else if ("MAGNET_BLOCK".equals(geometry)) {
            c.rect("STRUCTURE", x, y, w, h);
            c.line("CENTER", x, y, x + w, y + h);
            c.line("CENTER", x, y + h, x + w, y);
        } else if ("SENSOR_RAIL".equals(geometry)) {
            c.rect(layer, x, y, w, h);
            c.line("CENTER", x, y + h / 2, x + w, y + h / 2);
            c.circle("ANNOTATION", x + w * .78, y + h / 2, 3);
        } else if ("MOTOR".equals(geometry)) {
            c.rect(layer, x, y, w, h);
            c.circle("STRUCTURE", x + w * .78, y + h / 2, Math.min(w, h) * .24);
        } else if ("GEARBOX".equals(geometry)) {
            c.rect(layer, x, y, w, h);
            c.line("STRUCTURE", x, y, x + w, y + h);
        } else if ("FRAME".equals(geometry)) {
            c.rect(layer, x, y, w, h);
            c.line("STRUCTURE", x, y, x + w, y + h);
            c.line("STRUCTURE", x, y + h, x + w, y);
        } else if ("COVER".equals(geometry)) {
            c.rect(layer, x, y, w, h);
            c.line("HIDDEN", x + w * .15, y + h * .5, x + w * .85, y + h * .5);
        } else {
            c.rect(layer, x, y, w, h);
        }
    }

    private void planSections(Canvas c, DesignProject p) {
        int index = 0;
        for (DesignProject.DrawingViewPlan section : p.getDrawingPlan().getSectionViews()) {
            double x = 350 + index * 150;
            double y = 475;
            c.text("TEXT", x, y + 58, 4, section.getName());
            c.rect("SECTION", x, y, 125, 45);
            hatch(c, x, y, 125, 45);
            c.line("CUTTING", 250, 475, 250, 292);
            c.text("CUTTING", 238, 482, 4, "A");
            c.text("CUTTING", 238, 290, 4, "A");
            int row = 0;
            for (String label : section.getLabels()) c.text("TEXT", x, y - 10 - row++ * 13, 3, trim(label, 26));
            index++;
        }
    }

    private void planDetails(Canvas c, DesignProject p) {
        int index = 0;
        for (DesignProject.DrawingViewPlan detail : p.getDrawingPlan().getDetailViews()) {
            double x = 520;
            double y = 475 - index * 65;
            c.text("TEXT", x, y + 58, 4, detail.getName());
            c.rect("STRUCTURE", x, y, 120, 45);
            for (DesignProject.Component part : planParts(p, detail).stream().limit(3).toList()) {
                c.circle(layer(part), x + 22 + part.getSequence() % 4 * 24, y + 22, 10);
                c.text("ANNOTATION", x + 18 + part.getSequence() % 4 * 24, y + 18, 2.8, String.valueOf(part.getSequence()));
            }
            int row = 0;
            for (String label : detail.getLabels()) c.text("TEXT", x, y - 10 - row++ * 13, 3, trim(label, 26));
            index++;
        }
    }

    private void planIsometric(Canvas c, DesignProject p) {
        DesignProject.DrawingViewPlan iso = p.getDrawingPlan().getIsometricView();
        c.text("TEXT", 690, 520, 4, iso.getName() + " / 轴测图");
        double ox = 690;
        double oy = 405;
        double scale = 0.035;
        for (DesignProject.Component part : planParts(p, iso).stream().limit(8).toList()) {
            double x = ox + part.getX() * scale * .75;
            double y = oy + part.getZ() * scale * .65 - part.getY() * scale * .25;
            double w = Math.max(10, part.getLength() * scale * .75);
            double d = Math.max(7, part.getWidth() * scale * .45);
            double h = Math.max(7, part.getHeight() * scale * .65);
            isoBox(c, layer(part), x, y, w, d, h);
        }
    }

    private void isoBox(Canvas c, String layer, double x, double y, double w, double d, double h) {
        c.rect(layer, x, y, w, h);
        c.line(layer, x, y + h, x + d, y + h + d);
        c.line(layer, x + w, y + h, x + w + d, y + h + d);
        c.line(layer, x + d, y + h + d, x + w + d, y + h + d);
        c.line(layer, x + w, y, x + w + d, y + d);
        c.line(layer, x + w + d, y + d, x + w + d, y + h + d);
    }

    private void hatch(Canvas c, double x, double y, double w, double h) {
        for (double i = -h; i < w; i += 10) c.line("HATCH", x + Math.max(0, i), y, x + Math.min(w, i + h), y + Math.min(h, h + i));
    }

    private void bom(Canvas c, DesignProject p) {
        double x = 510;
        double y = 390;
        double w = 300;
        double h = 155;
        c.rect("TABLE", x, y, w, h);
        c.text("TABLE", x + 8, y + h - 14, 4, "零件明细表 BOM");
        c.text("TABLE", x + 8, y + h - 32, 3, "序号  名称              材料       数量");
        int i = 0;
        for (DesignProject.BomItem item : p.getDrawingPlan().getBomTable().stream().limit(6).toList()) {
            c.text("TABLE", x + 8, y + h - 50 - i * 16, 3,
                    "%02d    %-12s  %-8s  %d".formatted(item.getSequence(), trim(item.getName(), 10), trim(item.getMaterial(), 6), item.getQuantity()));
            i++;
        }
    }

    private void parameterTable(Canvas c, DesignProject p) {
        c.rect("TABLE", 690, 250, 110, 95);
        c.text("TABLE", 696, 330, 3.2, "主要参数表");
        int i = 0;
        for (DesignProject.Parameter parameter : p.getDrawingPlan().getParameterTable().stream().limit(4).toList()) {
            c.text("TABLE", 696, 313 - i++ * 16, 2.8,
                    trim(parameter.getName(), 8) + "=" + parameter.getValue() + parameter.getUnit());
        }
    }

    private void requirements(Canvas c, DesignProject p) {
        c.text("TEXT", 510, 165, 4, "技术要求");
        int i = 0;
        for (String item : p.getDrawingPlan().getTechnicalRequirements().stream().limit(4).toList()) {
            c.text("TEXT", 510, 148 - i * 17, 3, (i + 1) + ". " + trim(item, 24));
            i++;
        }
    }

    private String showcaseSvg(DesignProject p) {
        Canvas c = new Canvas(p.getProjectTitle(), "方案展示图", "FA-01");
        c.text("TEXT", 55, 540, 9, p.getProjectTitle());
        c.text("TEXT", 55, 520, 4.5, "source: AssemblyTree + DrawingPlan");
        double l = p.number("总长", p.number("整机长度", 4200));
        double w = p.number("总宽", p.number("整机宽度", 1600));
        double h = p.number("总高", p.number("整机高度", 1800));
        for (DesignProject.Component part : p.getComponents().stream().filter(DesignProject.Component::isKeyPart).limit(18).toList()) {
            projectPart(c, part, "FRONT", 55, 130, 560, 330, l, w, h);
            double tx = 55 + (part.getX() + part.getLength() / 2) / l * 560;
            double ty = 140 + (part.getZ() + part.getHeight()) / h * 330;
            c.text("ANNOTATION", tx, Math.min(485, ty), 3.5, part.getSequence() + " " + trim(part.getName(), 10));
        }
        c.rect("TABLE", 640, 130, 170, 350);
        c.text("TABLE", 655, 455, 5, "结构组成");
        int row = 0;
        for (DesignProject.BomItem item : p.getBom().stream().limit(10).toList()) {
            c.text("TABLE", 655, 425 - row++ * 28, 3.5, item.getSequence() + ". " + trim(item.getName(), 12) + " x" + item.getQuantity());
        }
        return c.svg();
    }

    private byte[] renderCad(Canvas c) {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            BufferedImage image = new BufferedImage(1680, 1180, BufferedImage.TYPE_INT_RGB);
            Graphics2D g = graphics(image, Color.WHITE);
            for (Shape s : c.shapes) draw(g, s, 2, false);
            g.dispose();
            ImageIO.write(image, "png", out);
            return out.toByteArray();
        } catch (Exception e) {
            throw new IllegalStateException("生成CAD预览失败：" + e.getMessage(), e);
        }
    }

    private byte[] renderShowcase(DesignProject p) {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Canvas c = new Canvas(p.getProjectTitle(), "方案展示图", "FA-01");
            String svg = showcaseSvg(p);
            BufferedImage image = new BufferedImage(1680, 1180, BufferedImage.TYPE_INT_RGB);
            Graphics2D g = graphics(image, new Color(243, 247, 251));
            for (Shape s : c.shapes) draw(g, s, 2, false);
            g.setFont(font().deriveFont(Font.BOLD, 32f));
            g.drawString("Preview generated from DrawingPlan", 80, 90);
            g.setFont(font().deriveFont(Font.PLAIN, 20f));
            g.drawString(trim(svg.replaceAll("<[^>]+>", " "), 70), 80, 130);
            g.dispose();
            ImageIO.write(image, "png", out);
            return out.toByteArray();
        } catch (Exception e) {
            throw new IllegalStateException("生成方案展示图失败：" + e.getMessage(), e);
        }
    }

    private Graphics2D graphics(BufferedImage image, Color bg) {
        Graphics2D g = image.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setColor(bg);
        g.fillRect(0, 0, image.getWidth(), image.getHeight());
        g.setColor(new Color(24, 34, 48));
        g.setStroke(new BasicStroke(2));
        return g;
    }

    private void draw(Graphics2D g, Shape s, double scale, boolean fill) {
        int x = (int) (s.x1 * scale);
        int y = (int) ((590 - s.y1) * scale);
        if ("LINE".equals(s.type)) {
            g.drawLine(x, y, (int) (s.x2 * scale), (int) ((590 - s.y2) * scale));
        } else if ("CIRCLE".equals(s.type)) {
            int r = (int) (s.size * scale);
            g.drawOval(x - r, y - r, 2 * r, 2 * r);
        } else {
            g.setFont(font().deriveFont(Math.max(12f, (float) (s.size * scale))));
            g.drawString(s.text, x, y);
        }
    }

    private Font font() {
        for (String n : FONTS) {
            Font f = new Font(n, Font.PLAIN, 14);
            if (f.canDisplay('中')) return f;
        }
        return new Font("Dialog", Font.PLAIN, 14);
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

    private String trim(String v, int n) {
        return v == null ? "" : v.length() > n ? v.substring(0, n) + "..." : v;
    }

    static class Canvas {
        final String title;
        final String name;
        final String no;
        final java.util.List<Shape> shapes = new java.util.ArrayList<>();

        Canvas(String t, String n, String no) {
            title = t == null ? "" : t;
            name = n;
            this.no = no;
        }

        void line(String l, double a, double b, double c, double d) {
            shapes.add(new Shape("LINE", l, a, b, c, d, 0, ""));
        }

        void rect(String l, double x, double y, double w, double h) {
            line(l, x, y, x + w, y);
            line(l, x + w, y, x + w, y + h);
            line(l, x + w, y + h, x, y + h);
            line(l, x, y + h, x, y);
        }

        void circle(String l, double x, double y, double r) {
            shapes.add(new Shape("CIRCLE", l, x, y, 0, 0, r, ""));
        }

        void poly(String l, double... points) {
            for (int i = 0; i < points.length; i += 2) {
                int n = (i + 2) % points.length;
                line(l, points[i], points[i + 1], points[n], points[n + 1]);
            }
        }

        void text(String l, double x, double y, double s, String t) {
            shapes.add(new Shape("TEXT", l, x, y, 0, 0, s, t == null ? "" : t));
        }

        String dxf() {
            StringBuilder b = new StringBuilder("0\nSECTION\n2\nHEADER\n9\n$ACADVER\n1\nAC1027\n0\nENDSEC\n0\nSECTION\n2\nTABLES\n0\nTABLE\n2\nLAYER\n70\n20\n");
            for (String l : List.of("FRAME", "TITLE", "BODY", "SUPPORT", "INTERFACE", "FUNCTION", "STRUCTURE", "OUTLINE", "CENTER",
                    "DIMENSION", "ANNOTATION", "TABLE", "TEXT", "SECTION", "HATCH", "CUTTING", "TOLERANCE", "JOINT", "HIDDEN")) {
                b.append("0\nLAYER\n2\n").append(l).append("\n70\n0\n62\n7\n6\nCONTINUOUS\n");
            }
            b.append("0\nENDTAB\n0\nENDSEC\n0\nSECTION\n2\nENTITIES\n");
            shapes.forEach(s -> s.dxf(b));
            return b.append("0\nENDSEC\n0\nEOF\n").toString();
        }

        String svg() {
            StringBuilder b = new StringBuilder("<svg xmlns=\"http://www.w3.org/2000/svg\" viewBox=\"0 0 840 590\"><rect width=\"840\" height=\"590\" fill=\"white\"/>");
            shapes.forEach(s -> s.svg(b));
            return b.append("</svg>").toString();
        }
    }

    record Shape(String type, String layer, double x1, double y1, double x2, double y2, double size, String text) {
        void dxf(StringBuilder b) {
            b.append("0\n").append(type).append("\n8\n").append(layer).append('\n');
            if ("LINE".equals(type)) {
                b.append("10\n").append(x1).append("\n20\n").append(y1).append("\n11\n").append(x2).append("\n21\n").append(y2).append('\n');
            } else if ("CIRCLE".equals(type)) {
                b.append("10\n").append(x1).append("\n20\n").append(y1).append("\n40\n").append(size).append('\n');
            } else {
                b.append("10\n").append(x1).append("\n20\n").append(y1).append("\n40\n").append(size).append("\n1\n").append(text).append('\n');
            }
        }

        void svg(StringBuilder b) {
            if ("LINE".equals(type)) {
                b.append("<line x1=\"").append(x1).append("\" y1=\"").append(590 - y1).append("\" x2=\"").append(x2)
                        .append("\" y2=\"").append(590 - y2).append("\" stroke=\"#182230\"/>");
            } else if ("CIRCLE".equals(type)) {
                b.append("<circle cx=\"").append(x1).append("\" cy=\"").append(590 - y1).append("\" r=\"").append(size)
                        .append("\" fill=\"white\" stroke=\"#182230\"/>");
            } else {
                b.append("<text x=\"").append(x1).append("\" y=\"").append(590 - y1).append("\" font-size=\"").append(size)
                        .append("\" font-family=\"Microsoft YaHei,Arial\">").append(escape(text)).append("</text>");
            }
        }

        private static String escape(String v) {
            return v.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
        }
    }
}
