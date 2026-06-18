package com.dropai.rewrite.modules.drawingEngine;

import com.dropai.rewrite.modules.model.DesignProject;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Comparator;
import java.util.List;

@Service
public class DrawingEngine {
    private static final List<String> FONTS = List.of("Microsoft YaHei", "SimHei", "Dialog");

    private final DimensionEngine dimensionEngine = new DimensionEngine();
    private final PartDrawingEngine partDrawingEngine = new PartDrawingEngine();
    private final ConceptRenderGenerator conceptRenderGenerator = new ConceptRenderGenerator();

    public List<DrawingArtifact> drawAssemblyDrawing(DesignProject project) {
        validateDrawingPlan(project);
        Canvas c = new Canvas(project.getProjectTitle(), "总装三视图", "ZD-00");
        frame(c);
        title(c, project);
        planViews(c, project);
        dimensionEngine.drawPlanDimensions(c, project);
        bom(c, project);
        parameterTable(c, project);
        requirements(c, project);

        Canvas concept = conceptRenderGenerator.draw(project);
        return List.of(
                new DrawingArtifact("assembly.dxf", c.dxf().getBytes(StandardCharsets.UTF_8), "application/dxf"),
                new DrawingArtifact("cad_preview.svg", c.svg(false).getBytes(StandardCharsets.UTF_8), "image/svg+xml"),
                new DrawingArtifact("cad_preview.png", render(c, Color.WHITE, false), "image/png"),
                new DrawingArtifact("preview.svg", concept.svg(true).getBytes(StandardCharsets.UTF_8), "image/svg+xml"),
                new DrawingArtifact("preview.png", render(concept, new Color(243, 247, 251), true), "image/png"));
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
                || plan.getQualityScore() < 70) {
            throw new IllegalStateException("DrawingPlan is empty or below engineering quality gate; CAD generation is blocked.");
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
        c.text("TEXT", 580, 42, 3.5, trim(c.title, 22));
        c.text("TEXT", 710, 42, 3.5, "比例 " + scale);
        c.text("TEXT", 580, 66, 3, "本科毕业设计总装图");
    }

    private void planViews(Canvas c, DesignProject p) {
        Bounds bounds = bounds(p.getComponents());
        drawPlanView(c, p, p.getDrawingPlan().getMainView(), "FRONT", "主视图", bounds);
        drawPlanView(c, p, p.getDrawingPlan().getTopView(), "TOP", "俯视图", bounds);
        drawPlanView(c, p, p.getDrawingPlan().getSideView(), "SIDE", "侧视图", bounds);
    }

    private void drawPlanView(Canvas c, DesignProject p, DesignProject.DrawingViewPlan view,
                              String orientation, String title, Bounds bounds) {
        double ox = vp(view, "x", 60);
        double oy = vp(view, "y", 300);
        double vw = vp(view, "width", 400);
        double vh = vp(view, "height", 150);
        c.text("TEXT", ox, oy + vh + 13, 4.2, title);
        c.rect("OUTLINE", ox, oy, vw, vh);
        for (DesignProject.Component part : parts(p, view)) {
            Projection pr = project(part, orientation, ox, oy, vw, vh, bounds);
            drawLodSymbol(c, part, pr.x(), pr.y(), pr.w(), pr.h());
            if (part.isKeyPart()) balloon(c, part, pr.x() + pr.w() / 2, pr.y() + pr.h() / 2);
        }
    }

    private Projection project(DesignProject.Component part, String orientation,
                               double ox, double oy, double vw, double vh, Bounds b) {
        if ("TOP".equals(orientation)) {
            return fit(part.getX(), part.getY(), part.getLength(), part.getWidth(),
                    b.minX(), b.maxX(), b.minY(), b.maxY(), ox, oy, vw, vh);
        }
        if ("SIDE".equals(orientation)) {
            return fit(part.getY(), part.getZ(), part.getWidth(), part.getHeight(),
                    b.minY(), b.maxY(), b.minZ(), b.maxZ(), ox, oy, vw, vh);
        }
        return fit(part.getX(), part.getZ(), part.getLength(), part.getHeight(),
                b.minX(), b.maxX(), b.minZ(), b.maxZ(), ox, oy, vw, vh);
    }

    private Projection fit(double x, double y, double w, double h,
                           double minX, double maxX, double minY, double maxY,
                           double ox, double oy, double vw, double vh) {
        double pad = 12;
        double sx = (vw - pad * 2) / Math.max(1, maxX - minX);
        double sy = (vh - pad * 2) / Math.max(1, maxY - minY);
        double px = ox + pad + (x - minX) * sx;
        double py = oy + pad + (y - minY) * sy;
        double pw = Math.max(7, w * sx);
        double ph = Math.max(6, h * sy);
        return new Projection(px, py, Math.min(pw, vw - pad), Math.min(ph, vh - pad));
    }

    private void drawLodSymbol(Canvas c, DesignProject.Component part, double x, double y, double w, double h) {
        String geometry = part.getGeometry() == null ? "" : part.getGeometry().toUpperCase();
        String name = normalized(part.getName());
        String layer = layer(part);
        if (geometry.contains("TRACK") || name.contains("track") || name.contains("履带")) {
            c.rect(layer, x, y, w, h);
            c.circle(layer, x + Math.min(w, h) / 2, y + h / 2, Math.min(w, h) / 2);
            c.circle(layer, x + w - Math.min(w, h) / 2, y + h / 2, Math.min(w, h) / 2);
            c.line("CENTER", x + w * .12, y + h / 2, x + w * .88, y + h / 2);
        } else if (geometry.contains("WHEEL") || name.contains("wheel") || name.contains("轮")) {
            double r = Math.max(4, Math.min(w, h) / 2);
            c.circle(layer, x + w / 2, y + h / 2, r);
            c.circle("CENTER", x + w / 2, y + h / 2, r * .45);
            c.line("CENTER", x + w / 2 - r, y + h / 2, x + w / 2 + r, y + h / 2);
        } else if (geometry.contains("BRUSH") || name.contains("brush") || name.contains("刷")) {
            double r = Math.max(6, Math.min(w, h) / 2);
            c.circle(layer, x + w / 2, y + h / 2, r);
            for (int i = 0; i < 10; i++) {
                double a = Math.PI * 2 * i / 10;
                c.line(layer, x + w / 2, y + h / 2, x + w / 2 + Math.cos(a) * r, y + h / 2 + Math.sin(a) * r);
            }
        } else if (geometry.contains("MAGNET") || name.contains("magnet") || name.contains("磁")) {
            c.rect(layer, x, y, w, h);
            c.line("HIDDEN", x + 4, y + 4, x + w - 4, y + h - 4);
            c.line("HIDDEN", x + 4, y + h - 4, x + w - 4, y + 4);
            c.text("ANNOTATION", x + 3, y + h / 2, 2.5, "磁");
        } else if (geometry.contains("MOTOR") || name.contains("motor") || name.contains("电机")) {
            c.rect(layer, x, y, w, h);
            c.circle("STRUCTURE", x + w * .76, y + h / 2, Math.min(w, h) * .24);
            c.text("ANNOTATION", x + 3, y + h / 2, 2.5, "电机");
        } else if (geometry.contains("GEAR") || name.contains("reducer") || name.contains("减速")) {
            c.rect(layer, x, y, w, h);
            c.line("STRUCTURE", x, y, x + w, y + h);
            c.line("STRUCTURE", x, y + h, x + w, y);
        } else if (geometry.contains("SENSOR") || name.contains("sensor") || name.contains("检测") || name.contains("传感")) {
            c.rect(layer, x, y, w, h);
            c.line("CENTER", x, y + h / 2, x + w, y + h / 2);
            c.circle("ANNOTATION", x + w * .76, y + h / 2, 3);
        } else if (geometry.contains("FRAME") || name.contains("frame") || name.contains("机架")) {
            c.rect(layer, x, y, w, h);
            c.line("STRUCTURE", x, y, x + w, y + h);
            c.line("STRUCTURE", x, y + h, x + w, y);
        } else if (geometry.contains("COVER") || name.contains("cover") || name.contains("外壳")) {
            c.rect(layer, x, y, w, h);
            c.line("HIDDEN", x + w * .1, y + h * .5, x + w * .9, y + h * .5);
        } else {
            c.rect(layer, x, y, w, h);
        }
    }

    private void bom(Canvas c, DesignProject p) {
        double x = 515;
        double y = 405;
        double w = 295;
        double h = 140;
        c.rect("TABLE", x, y, w, h);
        c.text("TABLE", x + 8, y + h - 12, 4, "BOM明细表");
        c.text("TABLE", x + 8, y + h - 28, 3, "序号  名称              材料       数量");
        int row = 0;
        for (DesignProject.BomItem item : p.getDrawingPlan().getBomTable().stream().limit(6).toList()) {
            c.text("TABLE", x + 8, y + h - 45 - row * 15, 2.8,
                    "%02d    %-12s  %-8s  %d".formatted(item.getSequence(), trim(item.getName(), 10), trim(item.getMaterial(), 6), item.getQuantity()));
            row++;
        }
    }

    private void parameterTable(Canvas c, DesignProject p) {
        double x = 690;
        double y = 112;
        double w = 120;
        double h = 96;
        c.rect("TABLE", x, y, w, h);
        c.text("TABLE", x + 6, y + h - 12, 3.6, "主要参数表");
        int row = 0;
        for (DesignProject.Parameter parameter : p.getDrawingPlan().getParameterTable().stream().limit(4).toList()) {
            c.text("TABLE", x + 6, y + h - 30 - row++ * 16, 2.7,
                    trim(parameter.getName(), 8) + "=" + trim(String.valueOf(parameter.getValue()), 8) + parameter.getUnit());
        }
    }

    private void requirements(Canvas c, DesignProject p) {
        c.text("TEXT", 510, 205, 4, "技术要求");
        int row = 0;
        for (String item : p.getDrawingPlan().getTechnicalRequirements().stream().limit(3).toList()) {
            c.text("TEXT", 510, 188 - row * 17, 3, (row + 1) + ". " + trim(item, 24));
            row++;
        }
    }

    private void balloon(Canvas c, DesignProject.Component part, double x, double y) {
        c.circle("ANNOTATION", x, y, 6);
        c.text("ANNOTATION", x - 2.5, y - 2.5, 3, String.valueOf(part.getSequence()));
    }

    private List<DesignProject.Component> parts(DesignProject p, DesignProject.DrawingViewPlan view) {
        return p.getComponents().stream()
                .filter(component -> view.getVisibleParts().contains(component.getPartId()))
                .sorted(Comparator.comparingInt(DesignProject.Component::getSequence))
                .toList();
    }

    private Bounds bounds(List<DesignProject.Component> parts) {
        double minX = parts.stream().mapToDouble(DesignProject.Component::getX).min().orElse(0);
        double minY = parts.stream().mapToDouble(DesignProject.Component::getY).min().orElse(0);
        double minZ = parts.stream().mapToDouble(DesignProject.Component::getZ).min().orElse(0);
        double maxX = parts.stream().mapToDouble(c -> c.getX() + c.getLength()).max().orElse(1000);
        double maxY = parts.stream().mapToDouble(c -> c.getY() + c.getWidth()).max().orElse(800);
        double maxZ = parts.stream().mapToDouble(c -> c.getZ() + c.getHeight()).max().orElse(600);
        return new Bounds(minX, minY, minZ, maxX, maxY, maxZ);
    }

    private byte[] render(Canvas c, Color bg, boolean colorByLayer) {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            BufferedImage image = new BufferedImage(1680, 1180, BufferedImage.TYPE_INT_RGB);
            Graphics2D g = graphics(image, bg);
            for (Shape s : c.shapes) draw(g, s, 2, colorByLayer);
            g.dispose();
            ImageIO.write(image, "png", out);
            return out.toByteArray();
        } catch (Exception e) {
            throw new IllegalStateException("Drawing preview generation failed: " + e.getMessage(), e);
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

    private void draw(Graphics2D g, Shape s, double scale, boolean colorByLayer) {
        Color previous = g.getColor();
        if (colorByLayer) g.setColor(layerColor(s.layer));
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
        g.setColor(previous);
    }

    private Color layerColor(String layer) {
        return switch (layer) {
            case "BODY", "FRAME" -> new Color(35, 99, 185);
            case "SUPPORT", "BASE" -> new Color(44, 132, 83);
            case "FUNCTION" -> new Color(211, 118, 37);
            case "INTERFACE" -> new Color(128, 77, 178);
            case "JOINT", "STRUCTURE" -> new Color(78, 92, 116);
            case "TABLE", "TEXT", "ANNOTATION" -> new Color(23, 36, 55);
            default -> new Color(24, 34, 48);
        };
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

    private double vp(DesignProject.DrawingViewPlan view, String key, double fallback) {
        if (view == null || view.getViewport() == null) return fallback;
        return view.getViewport().getOrDefault(key, fallback);
    }

    private String normalized(String value) {
        return value == null ? "" : value.toLowerCase();
    }

    private String trim(String v, int n) {
        return v == null ? "" : v.length() > n ? v.substring(0, n) + "..." : v;
    }

    record Projection(double x, double y, double w, double h) {}
    record Bounds(double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {}

    static class Canvas {
        final String title;
        final String name;
        final String no;
        final java.util.List<Shape> shapes = new java.util.ArrayList<>();

        Canvas(String t, String n, String no) {
            title = t == null ? "" : t;
            name = n == null ? "" : n;
            this.no = no == null ? "" : no;
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

        String svg(boolean colorByLayer) {
            StringBuilder b = new StringBuilder("<svg xmlns=\"http://www.w3.org/2000/svg\" viewBox=\"0 0 840 590\"><rect width=\"840\" height=\"590\" fill=\"white\"/>");
            shapes.forEach(s -> s.svg(b, colorByLayer));
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

        void svg(StringBuilder b, boolean colorByLayer) {
            String stroke = colorByLayer ? color(layer) : "#182230";
            if ("LINE".equals(type)) {
                b.append("<line x1=\"").append(x1).append("\" y1=\"").append(590 - y1).append("\" x2=\"").append(x2)
                        .append("\" y2=\"").append(590 - y2).append("\" stroke=\"").append(stroke).append("\"/>");
            } else if ("CIRCLE".equals(type)) {
                b.append("<circle cx=\"").append(x1).append("\" cy=\"").append(590 - y1).append("\" r=\"").append(size)
                        .append("\" fill=\"white\" stroke=\"").append(stroke).append("\"/>");
            } else {
                b.append("<text x=\"").append(x1).append("\" y=\"").append(590 - y1).append("\" font-size=\"").append(size)
                        .append("\" font-family=\"Microsoft YaHei,Arial\" fill=\"").append(stroke).append("\">").append(escape(text)).append("</text>");
            }
        }

        private static String color(String layer) {
            return switch (layer) {
                case "BODY", "FRAME" -> "#2363b9";
                case "SUPPORT", "BASE" -> "#2c8453";
                case "FUNCTION" -> "#d37625";
                case "INTERFACE" -> "#804db2";
                case "JOINT", "STRUCTURE" -> "#4e5c74";
                default -> "#182230";
            };
        }

        private static String escape(String v) {
            return v.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
        }
    }
}
