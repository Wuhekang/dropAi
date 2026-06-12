package com.dropai.rewrite.modules.drawingEngine;

import com.dropai.rewrite.modules.model.DesignProject;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.io.ByteArrayOutputStream;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Service
public class DrawingEngine {
    public List<DrawingArtifact> drawAssemblyDrawing(DesignProject project) {
        double l = project.number("总长", 4200), w = project.number("总宽", 1600), h = project.number("总高", 1800);
        Canvas c = new Canvas(project.getProjectTitle(), "总装图", "ZD-00", l, w, h);
        drawFrame(c); drawTitleBlock(c); drawMainView(c); drawSideView(c); drawTopView(c);
        drawDimension(c, 180, 260, 180 + l / 10, 260, "总长 " + fmt(l));
        drawDimension(c, 180, 260, 180, 260 - h / 10, "总高 " + fmt(h));
        drawPartBalloon(c, 320, 190, "1"); drawPartBalloon(c, 500, 190, "2"); drawLeaderLabel(c, 610, 190, "3 进出口组件");
        drawParameterTable(c, project); drawTechnicalRequirements(c, project);
        return List.of(new DrawingArtifact("assembly.dxf", exportDXF(c), "application/dxf"),
                new DrawingArtifact("preview.svg", exportSVGPreview(c), "image/svg+xml"),
                new DrawingArtifact("cad_preview.png", exportPNGPreview(c), "image/png"));
    }

    public List<DrawingArtifact> drawPartDrawing(DesignProject project) {
        List<DrawingArtifact> files = new ArrayList<>();
        files.add(part(project, "壳体零件图", "LJ-01", "part_shell.dxf", project.number("总长", 4200), project.number("总高", 1800)));
        files.add(part(project, "底座零件图", "LJ-02", "part_base.dxf", project.number("总长", 4200), project.number("总宽", 1600)));
        files.add(part(project, "进出口结构图", "LJ-03", "part_inlet.dxf", project.number("总宽", 1600) * .45, project.number("总高", 1800) * .4));
        files.add(part(project, "关键连接件图", "LJ-04", "part_connector.dxf", 320, 220));
        return files;
    }

    private DrawingArtifact part(DesignProject project, String name, String no, String file, double width, double height) {
        Canvas c = new Canvas(project.getProjectTitle(), name, no, width, width, height);
        drawFrame(c); drawTitleBlock(c);
        c.rect("OUTLINE", 190, 170, Math.min(430, width / 8), Math.min(250, height / 8));
        c.circle("CENTER", 250, 230, 12); c.circle("CENTER", 480, 230, 12);
        drawDimension(c, 190, 150, 620, 150, "外形长度 " + fmt(width));
        drawDimension(c, 165, 170, 165, 420, "外形高度 " + fmt(height));
        drawLeaderLabel(c, 520, 205, "板厚 t=" + fmt(project.number("壳体板厚", 4)) + " mm");
        drawTechnicalRequirements(c, project);
        return new DrawingArtifact(file, exportDXF(c), "application/dxf");
    }

    public void drawFrame(Canvas c) { c.rect("FRAME", 20, 20, 800, 550); c.rect("FRAME", 30, 30, 780, 530); }
    public void drawTitleBlock(Canvas c) {
        c.rect("TITLE", 570, 30, 240, 75); c.line("TITLE", 570, 55, 810, 55); c.line("TITLE", 700, 30, 700, 105);
        c.text("TEXT", 580, 82, 5, c.drawingName); c.text("TEXT", 710, 82, 4, "图号 " + c.drawingNo);
        c.text("TEXT", 580, 42, 3.5, c.projectTitle); c.text("TEXT", 710, 42, 3.5, "比例 1:10");
    }
    public void drawMainView(Canvas c) { c.rect("OUTLINE", 180, 260, c.length / 10, c.height / 10); c.text("TEXT", 180, 245, 4, "主视图"); }
    public void drawSideView(Canvas c) { c.rect("OUTLINE", 650, 260, c.width / 10, c.height / 10); c.text("TEXT", 650, 245, 4, "侧视图"); }
    public void drawTopView(Canvas c) { c.rect("OUTLINE", 180, 80, c.length / 10, c.width / 10); c.text("TEXT", 180, 65, 4, "俯视图"); }
    public void drawDimension(Canvas c, double x1, double y1, double x2, double y2, String label) {
        c.line("DIMENSION", x1, y1, x2, y2); c.line("DIMENSION", x1 - 4, y1 - 4, x1 + 4, y1 + 4);
        c.line("DIMENSION", x2 - 4, y2 - 4, x2 + 4, y2 + 4); c.text("DIMENSION", (x1 + x2) / 2, (y1 + y2) / 2 + 6, 3.5, label);
    }
    public void drawLeaderLabel(Canvas c, double x, double y, String text) { c.line("ANNOTATION", x, y, x + 45, y + 25); c.text("ANNOTATION", x + 48, y + 25, 3.5, text); }
    public void drawPartBalloon(Canvas c, double x, double y, String number) { c.circle("ANNOTATION", x, y, 10); c.text("ANNOTATION", x - 3, y - 2, 4, number); }
    public void drawParameterTable(Canvas c, DesignProject project) {
        c.rect("TABLE", 570, 365, 240, 155); c.text("TABLE", 580, 500, 4, "主要参数表");
        int i = 0;
        for (DesignProject.Parameter p : project.allParameters().stream().limit(6).toList()) {
            c.text("TABLE", 580, 480 - i * 18, 3.2, p.getName() + ": " + p.getValue() + " " + p.getUnit()); i++;
        }
    }
    public void drawTechnicalRequirements(Canvas c, DesignProject project) {
        c.text("TEXT", 40, 145, 4, "技术要求");
        c.text("TEXT", 40, 128, 3.2, "1. 未注尺寸及材料要求须经设计人员确认。");
        c.text("TEXT", 40, 112, 3.2, "2. 焊接、装配后清理毛刺并复核关键尺寸。");
        c.text("TEXT", 40, 96, 3.2, "3. 图纸参数与设计参数表、计算书保持一致。");
    }
    public byte[] exportDXF(Canvas c) { return c.dxf().getBytes(StandardCharsets.UTF_8); }
    public byte[] exportSVGPreview(Canvas c) { return c.svg().getBytes(StandardCharsets.UTF_8); }
    public byte[] exportPNGPreview(Canvas c) {
        try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            BufferedImage image = new BufferedImage(1680, 1180, BufferedImage.TYPE_INT_RGB);
            Graphics2D g = image.createGraphics();
            g.setColor(Color.WHITE); g.fillRect(0, 0, image.getWidth(), image.getHeight());
            g.setColor(new Color(24, 34, 48)); g.setStroke(new BasicStroke(2f));
            double scale = 2;
            for (Shape shape : c.shapes) {
                int x1 = (int) (shape.x1 * scale), y1 = (int) ((590 - shape.y1) * scale);
                if ("LINE".equals(shape.type)) g.drawLine(x1, y1, (int) (shape.x2 * scale), (int) ((590 - shape.y2) * scale));
                else if ("CIRCLE".equals(shape.type)) {
                    int r = (int) (shape.size * scale); g.drawOval(x1 - r, y1 - r, r * 2, r * 2);
                } else g.drawString(shape.text, x1, y1);
            }
            g.dispose();
            ImageIO.write(image, "png", output);
            return output.toByteArray();
        } catch (Exception e) { throw new IllegalStateException("生成CAD PNG预览失败：" + e.getMessage(), e); }
    }
    private String fmt(double value) { return String.format(Locale.ROOT, "%.0f", value); }

    public static class Canvas {
        private final String projectTitle, drawingName, drawingNo;
        private final double length, width, height;
        private final List<Shape> shapes = new ArrayList<>();
        Canvas(String projectTitle, String drawingName, String drawingNo, double length, double width, double height) {
            this.projectTitle = projectTitle; this.drawingName = drawingName; this.drawingNo = drawingNo;
            this.length = length; this.width = width; this.height = height;
        }
        void line(String layer, double x1, double y1, double x2, double y2) { shapes.add(new Shape("LINE", layer, x1, y1, x2, y2, 0, "")); }
        void rect(String layer, double x, double y, double w, double h) { line(layer,x,y,x+w,y); line(layer,x+w,y,x+w,y+h); line(layer,x+w,y+h,x,y+h); line(layer,x,y+h,x,y); }
        void circle(String layer, double x, double y, double r) { shapes.add(new Shape("CIRCLE", layer, x, y, 0, 0, r, "")); }
        void text(String layer, double x, double y, double size, String text) { shapes.add(new Shape("TEXT", layer, x, y, 0, 0, size, text)); }
        String dxf() {
            StringBuilder b = new StringBuilder("0\nSECTION\n2\nHEADER\n9\n$ACADVER\n1\nAC1009\n0\nENDSEC\n0\nSECTION\n2\nTABLES\n0\nTABLE\n2\nLAYER\n70\n7\n");
            for (String layer : List.of("FRAME","TITLE","OUTLINE","CENTER","DIMENSION","ANNOTATION","TABLE","TEXT")) b.append("0\nLAYER\n2\n").append(layer).append("\n70\n0\n62\n7\n6\nCONTINUOUS\n");
            b.append("0\nENDTAB\n0\nENDSEC\n0\nSECTION\n2\nENTITIES\n");
            shapes.forEach(s -> s.dxf(b)); return b.append("0\nENDSEC\n0\nEOF\n").toString();
        }
        String svg() {
            StringBuilder b = new StringBuilder("<svg xmlns=\"http://www.w3.org/2000/svg\" viewBox=\"0 0 840 590\"><rect width=\"840\" height=\"590\" fill=\"white\"/>");
            shapes.forEach(s -> s.svg(b)); return b.append("</svg>").toString();
        }
    }
    private record Shape(String type, String layer, double x1, double y1, double x2, double y2, double size, String text) {
        void dxf(StringBuilder b) {
            b.append("0\n").append(type).append("\n8\n").append(layer).append('\n');
            if ("LINE".equals(type)) b.append("10\n").append(x1).append("\n20\n").append(y1).append("\n11\n").append(x2).append("\n21\n").append(y2).append('\n');
            else if ("CIRCLE".equals(type)) b.append("10\n").append(x1).append("\n20\n").append(y1).append("\n40\n").append(size).append('\n');
            else b.append("10\n").append(x1).append("\n20\n").append(y1).append("\n40\n").append(size).append("\n1\n").append(text).append('\n');
        }
        void svg(StringBuilder b) {
            if ("LINE".equals(type)) b.append("<line x1=\"").append(x1).append("\" y1=\"").append(590-y1).append("\" x2=\"").append(x2).append("\" y2=\"").append(590-y2).append("\" stroke=\"#182230\" stroke-width=\"1\"/>");
            else if ("CIRCLE".equals(type)) b.append("<circle cx=\"").append(x1).append("\" cy=\"").append(590-y1).append("\" r=\"").append(size).append("\" fill=\"none\" stroke=\"#182230\"/>");
            else b.append("<text x=\"").append(x1).append("\" y=\"").append(590-y1).append("\" font-size=\"").append(size).append("\" font-family=\"sans-serif\">").append(escape(text)).append("</text>");
        }
        private static String escape(String value) { return value.replace("&","&amp;").replace("<","&lt;").replace(">","&gt;"); }
    }
}
