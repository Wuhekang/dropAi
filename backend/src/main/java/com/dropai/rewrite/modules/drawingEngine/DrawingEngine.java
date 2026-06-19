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
    private final ToleranceGenerator toleranceGenerator = new ToleranceGenerator();
    private final EngineeringSemanticLayer semanticLayer = new EngineeringSemanticLayer();
    private final DimensionSourceValidator dimensionSourceValidator = new DimensionSourceValidator();

    public List<DrawingArtifact> drawAssemblyDrawing(DesignProject project) {
        validateDrawingPlan(project);
        Canvas canvas = new Canvas(clean(project.getProjectTitle(), "本科毕业设计"), "总装三视图", "ZD-00");
        frame(canvas);
        titleBlock(canvas, project);
        planViews(canvas, project);
        dimensionEngine.drawPlanDimensions(canvas, project);
        bom(canvas, project);
        parameterTable(canvas, project);
        requirements(canvas, project);

        Canvas concept = conceptRenderGenerator.draw(project);
        return List.of(
                new DrawingArtifact("assembly.dxf", canvas.dxf().getBytes(StandardCharsets.UTF_8), "application/dxf"),
                new DrawingArtifact("cad_preview.svg", canvas.svg(false).getBytes(StandardCharsets.UTF_8), "image/svg+xml"),
                new DrawingArtifact("cad_preview.png", render(canvas, Color.WHITE, false), "image/png"),
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
            throw new IllegalStateException("图纸规划为空或低于工程图质量门禁，禁止生成CAD。");
        }
        dimensionSourceValidator.validateView(plan.getMainView());
        dimensionSourceValidator.validateView(plan.getTopView());
        dimensionSourceValidator.validateView(plan.getSideView());
    }

    private void frame(Canvas c) {
        c.rect("FRAME", 20, 20, 800, 550);
        c.rect("FRAME", 30, 30, 780, 530);
    }

    private void titleBlock(Canvas c, DesignProject project) {
        String drawingName = project.getDrawingPlan().getTitleBlock().getOrDefault("drawingName", c.name);
        String drawingNo = project.getDrawingPlan().getTitleBlock().getOrDefault("drawingNo", c.no);
        String scale = project.getDrawingPlan().getTitleBlock().getOrDefault("scale", "1:10");
        c.rect("TITLE", 570, 30, 240, 75);
        c.line("TITLE", 570, 55, 810, 55);
        c.line("TITLE", 700, 30, 700, 105);
        c.text("TEXT", 580, 82, 5, clean(drawingName, "总装三视图"));
        c.text("TEXT", 710, 82, 4, "图号 " + drawingNo);
        c.text("TEXT", 580, 66, 3, "本科毕业设计总装图");
        c.text("TEXT", 580, 42, 3.5, trim(clean(project.getProjectTitle(), c.title), 22));
        c.text("TEXT", 710, 42, 3.5, "比例 " + scale);
    }

    private void planViews(Canvas c, DesignProject project) {
        drawPlanView(c, project, project.getDrawingPlan().getMainView(), "FRONT", "主视图");
        drawPlanView(c, project, project.getDrawingPlan().getTopView(), "TOP", "俯视图");
        drawPlanView(c, project, project.getDrawingPlan().getSideView(), "SIDE", "侧视图");
    }

    private void drawPlanView(Canvas c, DesignProject project, DesignProject.DrawingViewPlan view,
                              String orientation, String title) {
        double ox = vp(view, "x", 60);
        double oy = vp(view, "y", 300);
        double vw = vp(view, "width", 400);
        double vh = vp(view, "height", 150);
        c.text("TEXT", ox, oy + vh + 13, 4.2, title);
        c.rect("OUTLINE", ox, oy, vw, vh);
        List<DesignProject.Component> viewParts = parts(project, view);
        Bounds bounds = bounds(viewParts);
        int balloonIndex = 0;
        for (DesignProject.Component part : viewParts) {
            Projection projection = project(part, orientation, ox, oy, vw, vh, bounds);
            drawEngineeringSymbol(c, part, projection.x(), projection.y(), projection.w(), projection.h());
            if (part.isKeyPart()) {
                balloon(c, part, projection.x() + projection.w() / 2, projection.y() + projection.h() / 2,
                        ox, oy, vw, vh, balloonIndex++);
            }
        }
    }

    private Projection project(DesignProject.Component part, String orientation,
                               double ox, double oy, double vw, double vh, Bounds bounds) {
        if ("TOP".equals(orientation)) {
            return fit(part.getX(), part.getY(), part.getLength(), part.getWidth(), bounds.minX(), bounds.maxX(), bounds.minY(), bounds.maxY(), ox, oy, vw, vh);
        }
        if ("SIDE".equals(orientation)) {
            return fit(part.getY(), part.getZ(), part.getWidth(), part.getHeight(), bounds.minY(), bounds.maxY(), bounds.minZ(), bounds.maxZ(), ox, oy, vw, vh);
        }
        return fit(part.getX(), part.getZ(), part.getLength(), part.getHeight(), bounds.minX(), bounds.maxX(), bounds.minZ(), bounds.maxZ(), ox, oy, vw, vh);
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

    private void drawEngineeringSymbol(Canvas c, DesignProject.Component part, double x, double y, double w, double h) {
        EngineeringSemanticLayer.SemanticPart semantic = semanticLayer.semanticOf(part);
        String layer = semantic.layer();
        switch (semantic.category()) {
            case "track" -> drawTrack(c, layer, x, y, w, h);
            case "wheel" -> drawWheel(c, layer, x, y, w, h);
            case "brush" -> drawBrush(c, layer, x, y, w, h);
            case "magnet" -> drawMagnet(c, layer, x, y, w, h);
            case "bearing" -> drawBearing(c, layer, x, y, w, h);
            case "motor" -> drawMotor(c, layer, x, y, w, h);
            case "reducer" -> drawReducer(c, layer, x, y, w, h);
            case "rail" -> drawRail(c, layer, x, y, w, h);
            case "coupling" -> drawCoupling(c, layer, x, y, w, h);
            case "bolt" -> drawBoltPattern(c, layer, x, y, w, h);
            case "flange" -> drawFlange(c, layer, x, y, w, h);
            case "sensor" -> drawSensor(c, layer, x, y, w, h);
            case "frame" -> drawFrameSymbol(c, layer, x, y, w, h);
            case "cover" -> drawCover(c, layer, x, y, w, h);
            default -> c.rect(layer, x, y, w, h);
        }
    }

    private void drawTrack(Canvas c, String layer, double x, double y, double w, double h) {
        c.rect(layer, x, y, w, h);
        double r = Math.min(w, h) / 2;
        c.circle(layer, x + r, y + h / 2, r);
        c.circle(layer, x + w - r, y + h / 2, r);
        for (int i = 0; i < 7; i++) c.line("STRUCTURE", x + w * (.12 + i * .12), y, x + w * (.08 + i * .12), y + h);
        c.line("CENTER", x + w * .12, y + h / 2, x + w * .88, y + h / 2);
    }

    private void drawWheel(Canvas c, String layer, double x, double y, double w, double h) {
        double r = Math.max(4, Math.min(w, h) / 2);
        c.circle(layer, x + w / 2, y + h / 2, r);
        c.circle("CENTER", x + w / 2, y + h / 2, r * .42);
        for (int i = 0; i < 6; i++) {
            double a = Math.PI * 2 * i / 6;
            c.line("CENTER", x + w / 2, y + h / 2, x + w / 2 + Math.cos(a) * r * .8, y + h / 2 + Math.sin(a) * r * .8);
        }
    }

    private void drawBrush(Canvas c, String layer, double x, double y, double w, double h) {
        double r = Math.max(6, Math.min(w, h) / 2);
        c.circle(layer, x + w / 2, y + h / 2, r);
        c.circle("CENTER", x + w / 2, y + h / 2, r * .25);
        for (int i = 0; i < 16; i++) {
            double a = Math.PI * 2 * i / 16;
            c.line(layer, x + w / 2, y + h / 2, x + w / 2 + Math.cos(a) * r, y + h / 2 + Math.sin(a) * r);
        }
    }

    private void drawMagnet(Canvas c, String layer, double x, double y, double w, double h) {
        c.rect(layer, x, y, w, h);
        for (int i = 0; i < 4; i++) c.rect("FUNCTION", x + w * (.1 + i * .22), y + h * .25, w * .13, h * .5);
        c.text("ANNOTATION", x + 3, y + h / 2, 2.5, "磁吸");
    }

    private void drawBearing(Canvas c, String layer, double x, double y, double w, double h) {
        double r = Math.max(5, Math.min(w, h) / 2);
        c.circle(layer, x + w / 2, y + h / 2, r);
        c.circle(layer, x + w / 2, y + h / 2, r * .55);
        c.line("CENTER", x + w / 2 - r, y + h / 2, x + w / 2 + r, y + h / 2);
        c.line("HATCH", x + w / 2 - r * .65, y + h / 2 - r * .65, x + w / 2 + r * .65, y + h / 2 + r * .65);
    }

    private void drawMotor(Canvas c, String layer, double x, double y, double w, double h) {
        c.rect(layer, x, y, w, h);
        c.circle(layer, x + w * .18, y + h / 2, Math.min(w, h) * .28);
        c.circle("STRUCTURE", x + w * .76, y + h / 2, Math.min(w, h) * .24);
        c.rect("STRUCTURE", x + w * .45, y + h * .74, w * .22, h * .18);
        c.text("ANNOTATION", x + 3, y + h / 2, 2.5, "电机");
    }

    private void drawReducer(Canvas c, String layer, double x, double y, double w, double h) {
        c.rect(layer, x, y, w, h);
        c.line("STRUCTURE", x, y, x + w, y + h);
        c.line("STRUCTURE", x, y + h, x + w, y);
        c.line("CENTER", x - 8, y + h / 2, x + w + 8, y + h / 2);
    }

    private void drawRail(Canvas c, String layer, double x, double y, double w, double h) {
        c.rect(layer, x, y + h * .35, w, h * .3);
        c.rect("STRUCTURE", x + w * .25, y + h * .18, w * .5, h * .64);
        for (int i = 0; i < 3; i++) c.circle("CENTER", x + w * (.2 + i * .3), y + h / 2, 2.5);
    }

    private void drawCoupling(Canvas c, String layer, double x, double y, double w, double h) {
        c.circle(layer, x + w * .33, y + h / 2, Math.min(w, h) * .25);
        c.circle(layer, x + w * .67, y + h / 2, Math.min(w, h) * .25);
        c.line("CENTER", x, y + h / 2, x + w, y + h / 2);
        c.circle("CENTER", x + w * .5, y + h * .25, 2.5);
    }

    private void drawBoltPattern(Canvas c, String layer, double x, double y, double w, double h) {
        for (int i = 0; i < 4; i++) {
            double bx = x + w * (.2 + (i % 2) * .6);
            double by = y + h * (.25 + (i / 2) * .5);
            c.circle(layer, bx, by, Math.max(2.5, Math.min(w, h) * .08));
            c.line("CENTER", bx - 4, by, bx + 4, by);
        }
    }

    private void drawFlange(Canvas c, String layer, double x, double y, double w, double h) {
        double r = Math.max(6, Math.min(w, h) / 2);
        c.circle(layer, x + w / 2, y + h / 2, r);
        c.circle(layer, x + w / 2, y + h / 2, r * .45);
        for (int i = 0; i < 6; i++) {
            double a = Math.PI * 2 * i / 6;
            c.circle("CENTER", x + w / 2 + Math.cos(a) * r * .72, y + h / 2 + Math.sin(a) * r * .72, 2.2);
        }
    }

    private void drawSensor(Canvas c, String layer, double x, double y, double w, double h) {
        c.rect(layer, x, y, w, h);
        c.line("CENTER", x, y + h / 2, x + w, y + h / 2);
        c.circle("ANNOTATION", x + w * .76, y + h / 2, 3);
    }

    private void drawFrameSymbol(Canvas c, String layer, double x, double y, double w, double h) {
        c.rect(layer, x, y, w, h);
        c.line("STRUCTURE", x, y, x + w, y + h);
        c.line("STRUCTURE", x, y + h, x + w, y);
    }

    private void drawCover(Canvas c, String layer, double x, double y, double w, double h) {
        c.rect(layer, x, y, w, h);
        c.line("HIDDEN", x + w * .1, y + h * .5, x + w * .9, y + h * .5);
    }

    private void bom(Canvas c, DesignProject project) {
        double x = 515, y = 405, w = 295, h = 140;
        c.rect("TABLE", x, y, w, h);
        c.text("TABLE", x + 8, y + h - 12, 4, "BOM明细表");
        c.text("TABLE", x + 8, y + h - 28, 3, "序号  名称              材料       数量/备注");
        int row = 0;
        for (DesignProject.BomItem item : project.getDrawingPlan().getBomTable().stream().limit(6).toList()) {
            c.text("TABLE", x + 8, y + h - 45 - row * 15, 2.8,
                    "%02d    %-12s  %-8s  %d %s".formatted(item.getSequence(), trim(clean(item.getName(), "零件"), 10), trim(clean(item.getMaterial(), "Q235B"), 6), item.getQuantity(), trim(clean(item.getRemark(), ""), 14)));
            row++;
        }
    }

    private void parameterTable(Canvas c, DesignProject project) {
        double x = 690, y = 112, w = 120, h = 96;
        c.rect("TABLE", x, y, w, h);
        c.text("TABLE", x + 6, y + h - 12, 3.6, "主要参数表");
        int row = 0;
        for (DesignProject.Parameter parameter : project.getDrawingPlan().getParameterTable().stream().limit(4).toList()) {
            c.text("TABLE", x + 6, y + h - 30 - row++ * 16, 2.7,
                    trim(clean(parameter.getName(), "参数"), 8) + "=" + trim(String.valueOf(parameter.getValue()), 8) + parameter.getUnit());
        }
    }

    private void requirements(Canvas c, DesignProject project) {
        List<String> requirements = project.getDrawingPlan().getTechnicalRequirements().isEmpty()
                ? toleranceGenerator.assemblyRequirements(project)
                : project.getDrawingPlan().getTechnicalRequirements();
        toleranceGenerator.drawToleranceBlock(c, 510, 122, requirements);
        toleranceGenerator.drawDatumAndGdt(c, 510, 97);
    }

    private void balloon(Canvas c, DesignProject.Component part, double x, double y,
                         double ox, double oy, double vw, double vh, int index) {
        boolean rightSide = index % 2 == 0;
        double bx = rightSide ? ox + vw + 10 : ox - 10;
        double by = oy + vh - 18 - index * 16;
        if (by < oy + 14) by = oy + 14 + (index % 4) * 15;
        c.line("ANNOTATION", x, y, bx, by);
        c.circle("ANNOTATION", bx, by, 6);
        c.text("ANNOTATION", bx - 2.5, by - 2.5, 3, String.valueOf(part.getSequence()));
    }

    private List<DesignProject.Component> parts(DesignProject project, DesignProject.DrawingViewPlan view) {
        return project.getComponents().stream()
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

    private byte[] render(Canvas canvas, Color background, boolean colorByLayer) {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            BufferedImage image = new BufferedImage(1680, 1180, BufferedImage.TYPE_INT_RGB);
            Graphics2D graphics = graphics(image, background);
            for (Shape shape : canvas.shapes) draw(graphics, shape, 2, colorByLayer);
            graphics.dispose();
            ImageIO.write(image, "png", out);
            return out.toByteArray();
        } catch (Exception e) {
            throw new IllegalStateException("图纸预览生成失败：" + e.getMessage(), e);
        }
    }

    private Graphics2D graphics(BufferedImage image, Color background) {
        Graphics2D graphics = image.createGraphics();
        graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        graphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        graphics.setColor(background);
        graphics.fillRect(0, 0, image.getWidth(), image.getHeight());
        graphics.setColor(new Color(24, 34, 48));
        graphics.setStroke(new BasicStroke(2));
        return graphics;
    }

    private void draw(Graphics2D graphics, Shape shape, double scale, boolean colorByLayer) {
        Color previous = graphics.getColor();
        if (colorByLayer) graphics.setColor(layerColor(shape.layer));
        int x = (int) (shape.x1 * scale);
        int y = (int) ((590 - shape.y1) * scale);
        if ("LINE".equals(shape.type)) {
            graphics.drawLine(x, y, (int) (shape.x2 * scale), (int) ((590 - shape.y2) * scale));
        } else if ("CIRCLE".equals(shape.type)) {
            int r = (int) (shape.size * scale);
            graphics.drawOval(x - r, y - r, 2 * r, 2 * r);
        } else {
            graphics.setFont(font().deriveFont(Math.max(12f, (float) (shape.size * scale))));
            graphics.drawString(shape.text, x, y);
        }
        graphics.setColor(previous);
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
        for (String name : FONTS) {
            Font font = new Font(name, Font.PLAIN, 14);
            if (font.canDisplay('主')) return font;
        }
        return new Font("Dialog", Font.PLAIN, 14);
    }

    private double vp(DesignProject.DrawingViewPlan view, String key, double fallback) {
        if (view == null || view.getViewport() == null) return fallback;
        return view.getViewport().getOrDefault(key, fallback);
    }

    private String clean(String value, String fallback) {
        return value == null || value.isBlank() || semanticLayer.looksCorrupted(value) ? fallback : value;
    }

    private String trim(String value, int length) {
        return value == null ? "" : value.length() > length ? value.substring(0, length) + "..." : value;
    }

    record Projection(double x, double y, double w, double h) {}
    record Bounds(double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {}

    static class Canvas {
        final String title;
        final String name;
        final String no;
        final java.util.List<Shape> shapes = new java.util.ArrayList<>();

        Canvas(String title, String name, String no) {
            this.title = title == null ? "" : title;
            this.name = name == null ? "" : name;
            this.no = no == null ? "" : no;
        }
        void line(String layer, double a, double b, double c, double d) { shapes.add(new Shape("LINE", layer, a, b, c, d, 0, "")); }
        void rect(String layer, double x, double y, double w, double h) { line(layer, x, y, x + w, y); line(layer, x + w, y, x + w, y + h); line(layer, x + w, y + h, x, y + h); line(layer, x, y + h, x, y); }
        void circle(String layer, double x, double y, double r) { shapes.add(new Shape("CIRCLE", layer, x, y, 0, 0, r, "")); }
        void poly(String layer, double... points) {
            for (int i = 0; i + 3 < points.length; i += 2) line(layer, points[i], points[i + 1], points[i + 2], points[i + 3]);
            if (points.length >= 4) line(layer, points[points.length - 2], points[points.length - 1], points[0], points[1]);
        }
        void text(String layer, double x, double y, double size, String text) { shapes.add(new Shape("TEXT", layer, x, y, 0, 0, size, text == null ? "" : text)); }
        String dxf() {
            StringBuilder builder = new StringBuilder("0\nSECTION\n2\nHEADER\n9\n$ACADVER\n1\nAC1027\n0\nENDSEC\n0\nSECTION\n2\nTABLES\n0\nTABLE\n2\nLAYER\n70\n20\n");
            for (String layer : List.of("FRAME", "TITLE", "BODY", "SUPPORT", "INTERFACE", "FUNCTION", "STRUCTURE", "OUTLINE", "CENTER", "DIMENSION", "ANNOTATION", "TABLE", "TEXT", "HATCH", "JOINT", "HIDDEN", "TOLERANCE")) {
                builder.append("0\nLAYER\n2\n").append(layer).append("\n70\n0\n62\n7\n6\nCONTINUOUS\n");
            }
            builder.append("0\nENDTAB\n0\nENDSEC\n0\nSECTION\n2\nENTITIES\n");
            shapes.forEach(shape -> shape.dxf(builder));
            return builder.append("0\nENDSEC\n0\nEOF\n").toString();
        }
        String svg(boolean colorByLayer) {
            StringBuilder builder = new StringBuilder("<svg xmlns=\"http://www.w3.org/2000/svg\" viewBox=\"0 0 840 590\"><rect width=\"840\" height=\"590\" fill=\"white\"/>");
            shapes.forEach(shape -> shape.svg(builder, colorByLayer));
            return builder.append("</svg>").toString();
        }
    }

    record Shape(String type, String layer, double x1, double y1, double x2, double y2, double size, String text) {
        void dxf(StringBuilder builder) {
            builder.append("0\n").append(type).append("\n8\n").append(layer).append('\n');
            if ("LINE".equals(type)) builder.append("10\n").append(x1).append("\n20\n").append(y1).append("\n11\n").append(x2).append("\n21\n").append(y2).append('\n');
            else if ("CIRCLE".equals(type)) builder.append("10\n").append(x1).append("\n20\n").append(y1).append("\n40\n").append(size).append('\n');
            else builder.append("10\n").append(x1).append("\n20\n").append(y1).append("\n40\n").append(size).append("\n1\n").append(text).append('\n');
        }
        void svg(StringBuilder builder, boolean colorByLayer) {
            String stroke = colorByLayer ? color(layer) : "#182230";
            if ("LINE".equals(type)) builder.append("<line x1=\"").append(x1).append("\" y1=\"").append(590 - y1).append("\" x2=\"").append(x2).append("\" y2=\"").append(590 - y2).append("\" stroke=\"").append(stroke).append("\"/>");
            else if ("CIRCLE".equals(type)) builder.append("<circle cx=\"").append(x1).append("\" cy=\"").append(590 - y1).append("\" r=\"").append(size).append("\" fill=\"white\" stroke=\"").append(stroke).append("\"/>");
            else builder.append("<text x=\"").append(x1).append("\" y=\"").append(590 - y1).append("\" font-size=\"").append(size).append("\" font-family=\"Microsoft YaHei,Arial\" fill=\"").append(stroke).append("\">").append(escape(text)).append("</text>");
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
        private static String escape(String value) { return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;"); }
    }
}
