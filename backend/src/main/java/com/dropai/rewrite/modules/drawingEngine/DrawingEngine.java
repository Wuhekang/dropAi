package com.dropai.rewrite.modules.drawingEngine;

import com.dropai.rewrite.modules.model.DesignProject;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

@Service
public class DrawingEngine {
    private static final List<String> FONTS = List.of("Noto Sans CJK SC", "Microsoft YaHei", "SimHei", "Dialog");
    private final DimensionEngine dimensionEngine = new DimensionEngine();
    private final SectionViewEngine sectionViewEngine = new SectionViewEngine();
    private final IsometricViewEngine isometricViewEngine = new IsometricViewEngine();
    private final AnnotationEngine annotationEngine = new AnnotationEngine();
    private final PartDrawingEngine partDrawingEngine = new PartDrawingEngine();

    public List<DrawingArtifact> drawAssemblyDrawing(DesignProject project) {
        Canvas c = new Canvas(project.getProjectTitle(), "总装图", "ZD-00");
        frame(c); title(c); componentViews(c, project); dimensionEngine.drawAssemblyDimensions(c, project);
        sectionViewEngine.drawSections(c, project); isometricViewEngine.drawIsometric(c, project);
        annotationEngine.drawAssemblyAnnotations(c, project); bom(c, project); requirements(c, project);
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

    private void componentViews(Canvas c, DesignProject p) {
        double totalL = p.number("总长", 4200), totalW = p.number("总宽", 1600), totalH = p.number("总高", 1800);
        double mx = 70, my = 300, mw = 400, mh = 160;
        double tx = 70, ty = 105, tw = 400, th = 130;
        double sx = 520, sy = 205, sw = 145, sh = 165;
        c.text("TEXT", mx, my - 18, 4, "主视图"); c.text("TEXT", tx, ty - 18, 4, "俯视图"); c.text("TEXT", sx, sy - 18, 4, "侧视图");
        for (DesignProject.Component part : p.getComponents()) {
            project(c, part, "FRONT", mx, my, mw, mh, totalL, totalW, totalH);
            project(c, part, "TOP", tx, ty, tw, th, totalL, totalW, totalH);
            project(c, part, "SIDE", sx, sy, sw, sh, totalL, totalW, totalH);
            if (part.isKeyPart()) {
                double bx = mx + (part.getX() + part.getLength() / 2) / totalL * mw;
                double by = my + (part.getZ() + part.getHeight() / 2) / totalH * mh;
                c.circle("ANNOTATION", bx, by, 8); c.text("ANNOTATION", bx - 2, by - 2, 3.5, String.valueOf(part.getSequence()));
            }
        }
    }

    private void project(Canvas c, DesignProject.Component p, String view, double ox, double oy, double vw, double vh,
                         double totalL, double totalW, double totalH) {
        double x, y, w, h;
        if ("TOP".equals(view)) {
            x = ox + p.getX() / totalL * vw; y = oy + p.getY() / totalW * vh;
            w = p.getLength() / totalL * vw; h = p.getWidth() / totalW * vh;
        } else if ("SIDE".equals(view)) {
            x = ox + p.getY() / totalW * vw; y = oy + p.getZ() / totalH * vh;
            w = p.getWidth() / totalW * vw; h = p.getHeight() / totalH * vh;
        } else {
            x = ox + p.getX() / totalL * vw; y = oy + p.getZ() / totalH * vh;
            w = p.getLength() / totalL * vw; h = p.getHeight() / totalH * vh;
        }
        w = Math.max(7, w); h = Math.max(6, h);
        String geometry = p.getGeometry() == null ? "BOX" : p.getGeometry();
        String layer = layer(p);
        boolean circle = ("CYLINDER_Y".equals(geometry) && "FRONT".equals(view))
                || ("CYLINDER_Z".equals(geometry) && "TOP".equals(view))
                || ("DUCT_X".equals(geometry) && "SIDE".equals(view))
                || "JOINT".equals(geometry) || "ROTOR".equals(geometry);
        if (circle) {
            c.circle(layer, x + w / 2, y + h / 2, Math.max(4, Math.min(w, h) / 2));
            c.line("CENTER", x + w / 2 - 5, y + h / 2, x + w / 2 + 5, y + h / 2);
            c.line("CENTER", x + w / 2, y + h / 2 - 5, x + w / 2, y + h / 2 + 5);
        } else if ("HOPPER".equals(geometry) && !"TOP".equals(view)) {
            c.poly(layer, x, y + h, x + w, y + h, x + w * .68, y + h * .18, x + w * .55, y, x + w * .45, y, x + w * .32, y + h * .18);
        } else if ("BELT".equals(geometry) && "FRONT".equals(view)) {
            c.circle(layer, x + h / 2, y + h / 2, h / 2);
            c.circle(layer, x + w - h / 2, y + h / 2, h / 2);
            c.line(layer, x + h / 2, y + h, x + w - h / 2, y + h);
            c.line(layer, x + h / 2, y, x + w - h / 2, y);
        } else if ("ARM_XZ".equals(geometry) && "FRONT".equals(view)) {
            c.poly(layer, x, y, x + w * .12, y - h * .16, x + w, y + h * .76, x + w * .88, y + h);
            c.circle("JOINT", x, y, Math.max(4, h * .2)); c.circle("JOINT", x + w, y + h * .78, Math.max(4, h * .2));
        } else if ("CLAW".equals(geometry) && "FRONT".equals(view)) {
            c.line(layer, x, y + h / 2, x + w * .45, y + h / 2);
            c.line(layer, x + w * .45, y + h / 2, x + w, y + h);
            c.line(layer, x + w * .45, y + h / 2, x + w, y);
            c.line(layer, x + w, y + h, x + w * .82, y + h * .72);
            c.line(layer, x + w, y, x + w * .82, y + h * .28);
        } else {
            c.rect(layer, x, y, w, h);
            if ("CHAMBER".equals(geometry)) {
                c.line("STRUCTURE", x + w * .35, y, x + w * .35, y + h);
                c.line("STRUCTURE", x + w * .68, y, x + w * .68, y + h);
            } else if ("TRUSS".equals(geometry) || "FRAME".equals(geometry)) {
                c.line("STRUCTURE", x, y, x + w, y + h);
                c.line("STRUCTURE", x, y + h, x + w, y);
            } else if ("DOOR".equals(geometry)) {
                c.line("STRUCTURE", x, y, x + w, y + h);
                c.circle("STRUCTURE", x + w * .82, y + h / 2, 2.5);
            } else if ("MOTOR".equals(geometry)) {
                c.circle("STRUCTURE", x + w * .78, y + h / 2, Math.min(w, h) * .24);
                c.line("STRUCTURE", x, y + h * .25, x - w * .18, y + h * .25);
            }
        }
    }

    private void bom(Canvas c, DesignProject p) {
        double x = 510, y = 390, w = 300, h = 155;
        c.rect("TABLE", x, y, w, h); c.text("TABLE", x + 8, y + h - 14, 4, "零件明细表（BOM）");
        c.text("TABLE", x + 8, y + h - 32, 3, "序号  名称              材料       数量");
        int i = 0;
        for (DesignProject.BomItem item : p.getBom().stream().limit(6).toList()) {
            c.text("TABLE", x + 8, y + h - 50 - i * 16, 3,
                    "%02d    %-12s  %-8s  %d".formatted(item.getSequence(), trim(item.getName(), 10), trim(item.getMaterial(), 6), item.getQuantity()));
            i++;
        }
    }

    private void requirements(Canvas c, DesignProject p) {
        c.text("TEXT", 510, 165, 4, "技术要求");
        int i = 0;
        for (String item : p.getTechnicalRequirements().stream().limit(4).toList()) {
            c.text("TEXT", 510, 148 - i * 17, 3, (i + 1) + ". " + trim(item, 24)); i++;
        }
    }

    private void frame(Canvas c) { c.rect("FRAME", 20, 20, 800, 550); c.rect("FRAME", 30, 30, 780, 530); }
    private void title(Canvas c) {
        c.rect("TITLE", 570, 30, 240, 75); c.line("TITLE", 570, 55, 810, 55); c.line("TITLE", 700, 30, 700, 105);
        c.text("TEXT", 580, 82, 5, c.name); c.text("TEXT", 710, 82, 4, "图号 " + c.no);
        c.text("TEXT", 580, 42, 3.5, trim(c.title, 15)); c.text("TEXT", 710, 42, 3.5, "比例 1:10");
    }
    private byte[] renderCad(Canvas c) {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            if (c.title != null && (c.title.contains("沉降") || c.title.contains("除尘"))) {
                ImageIO.write(sedimentationBoard(null, false), "png", out);
                return out.toByteArray();
            }
            BufferedImage image = new BufferedImage(1680, 1180, BufferedImage.TYPE_INT_RGB);
            Graphics2D g = graphics(image, Color.WHITE);
            for (Shape s : c.shapes) draw(g, s, 2, false);
            g.dispose(); ImageIO.write(image, "png", out); return out.toByteArray();
        } catch (Exception e) { throw new IllegalStateException("生成CAD预览失败：" + e.getMessage(), e); }
    }

    private String showcaseSvg(DesignProject p) {
        return showcaseCanvas(p).svg();
    }

    private byte[] renderShowcase(DesignProject p) {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            if ((p.getEquipmentName() + p.getProjectTitle()).contains("沉降") || (p.getEquipmentName() + p.getProjectTitle()).contains("除尘")) {
                ImageIO.write(sedimentationBoard(p, true), "png", out);
                return out.toByteArray();
            }
            BufferedImage image = new BufferedImage(1680, 1180, BufferedImage.TYPE_INT_RGB);
            Graphics2D g = graphics(image, new Color(243,247,251));
            g.setColor(new Color(24,34,48));
            for (Shape shape : showcaseCanvas(p).shapes) draw(g, shape, 2, false);
            g.dispose();ImageIO.write(image,"png",out);return out.toByteArray();
        }catch(Exception e){throw new IllegalStateException("生成方案展示图失败："+e.getMessage(),e);}
    }

    private BufferedImage sedimentationBoard(DesignProject p, boolean showcase) {
        BufferedImage image = new BufferedImage(1800, 1280, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, image.getWidth(), image.getHeight());
        g.setColor(new Color(24, 34, 48));
        g.setStroke(new BasicStroke(2f));
        g.setFont(font().deriveFont(Font.BOLD, 34f));
        g.drawString("重力沉降室详细设计", 42, 54);
        draw3dAssembly(g, 55, 105);
        drawPartLegend(g, 650, 120);
        drawThreeViews(g, 900, 90);
        drawParameterTable(g, 42, 660);
        drawMaterialTable(g, 42, 980);
        drawDetailViews(g, 650, 665);
        drawCalculationBlock(g, 650, 1010);
        drawSupportLayout(g, 1070, 930);
        drawTechnicalNotes(g, 1390, 500);
        drawEquipmentBom(g, 1390, 930);
        g.dispose();
        return image;
    }

    private void draw3dAssembly(Graphics2D g, int x, int y) {
        g.setFont(font().deriveFont(Font.BOLD, 20f));
        g.drawString("一、总体设计", x, y - 18);
        g.setFont(font().deriveFont(Font.PLAIN, 16f));
        g.drawString("1. 三维装配图", x, y + 18);
        Color old = g.getColor();
        g.setColor(new Color(52, 60, 68));
        g.fillPolygon(new int[]{x + 120, x + 520, x + 610, x + 215}, new int[]{y + 145, y + 95, y + 230, y + 285}, 4);
        g.setColor(new Color(77, 89, 99));
        g.fillPolygon(new int[]{x + 120, x + 215, x + 215, x + 120}, new int[]{y + 145, y + 285, y + 425, y + 295}, 4);
        g.setColor(new Color(36, 46, 57));
        g.fillPolygon(new int[]{x + 215, x + 610, x + 610, x + 215}, new int[]{y + 285, y + 230, y + 370, y + 425}, 4);
        g.setColor(new Color(52, 106, 159, 145));
        g.fillPolygon(new int[]{x + 205, x + 505, x + 565, x + 260}, new int[]{y + 165, y + 130, y + 205, y + 245}, 4);
        g.setColor(new Color(148, 112, 34));
        for (int i = 0; i < 5; i++) {
            int px = x + 150 + i * 90;
            g.drawLine(px, y + 112 - i * 8, px, y + 58 - i * 8);
            g.drawLine(px, y + 58 - i * 8, px + 95, y + 46 - i * 10);
        }
        g.drawLine(x + 135, y + 112, x + 520, y + 62);
        g.drawLine(x + 235, y + 90, x + 620, y + 42);
        g.drawLine(x + 620, y + 42, x + 620, y + 158);
        g.drawLine(x + 520, y + 62, x + 620, y + 42);
        g.setColor(old);
        g.drawRect(x + 255, y + 240, 120, 95);
        g.drawOval(x + 302, y + 272, 34, 34);
        drawPipe(g, x + 15, y + 260, 95, 38);
        drawPipe(g, x + 610, y + 235, 95, 38);
        drawHopper(g, x + 280, y + 425, 90, 90);
        drawValve(g, x + 305, y + 515);
        drawLeg(g, x + 140, y + 418); drawLeg(g, x + 570, y + 365);
        drawLeader(g, x + 35, y + 245, "1 进气管");
        drawLeader(g, x + 260, y + 145, "5 沉降区");
        drawLeader(g, x + 325, y + 348, "11 检修孔");
        drawLeader(g, x + 635, y + 222, "8 出气管");
        drawLeader(g, x + 355, y + 505, "10 排灰阀");
        drawLeader(g, x + 535, y + 70, "13 护栏");
    }

    private void drawThreeViews(Graphics2D g, int x, int y) {
        g.setFont(font().deriveFont(Font.BOLD, 20f));
        g.drawString("二、三视图（单位：mm）", x, y - 28);
        drawFrontView(g, x + 20, y + 45);
        drawSideView(g, x + 555, y + 45);
        drawTopView(g, x + 20, y + 365);
    }

    private void drawFrontView(Graphics2D g, int x, int y) {
        g.setFont(font().deriveFont(Font.BOLD, 15f));
        g.drawString("主视图", x + 145, y - 18);
        g.drawRect(x, y, 410, 205);
        for (int i = 1; i < 6; i++) g.drawLine(x + i * 68, y, x + i * 68, y + 35);
        g.drawRect(x - 85, y + 78, 85, 42);
        g.drawOval(x - 70, y + 88, 36, 22);
        g.drawRect(x + 410, y + 88, 85, 42);
        drawHopper(g, x + 172, y + 205, 72, 64);
        drawLeg(g, x + 42, y + 205); drawLeg(g, x + 345, y + 205);
        dimension(g, x, y - 24, x + 410, y - 24, "4000");
        dimension(g, x - 35, y, x - 35, y + 270, "3200");
        dimension(g, x + 40, y + 298, x + 345, y + 298, "2800");
        g.drawString("Ø159", x - 78, y + 68);
    }

    private void drawSideView(Graphics2D g, int x, int y) {
        g.setFont(font().deriveFont(Font.BOLD, 15f));
        g.drawString("左视图", x + 120, y - 18);
        g.drawRect(x, y, 260, 205);
        g.drawRect(x + 50, y + 35, 160, 130);
        g.drawOval(x + 100, y + 78, 58, 58);
        g.drawLine(x + 129, y + 35, x + 129, y + 165);
        g.drawLine(x + 50, y + 100, x + 210, y + 100);
        drawHopper(g, x + 98, y + 205, 65, 58);
        drawLeg(g, x + 22, y + 205); drawLeg(g, x + 218, y + 205);
        dimension(g, x, y - 24, x + 260, y - 24, "2000");
        dimension(g, x + 285, y, x + 285, y + 205, "2500");
        dimension(g, x + 50, y + 230, x + 210, y + 230, "1500");
    }

    private void drawTopView(Graphics2D g, int x, int y) {
        g.setFont(font().deriveFont(Font.BOLD, 15f));
        g.drawString("俯视图", x + 150, y - 18);
        g.drawRect(x, y, 410, 160);
        g.drawLine(x + 45, y + 26, x + 365, y + 26);
        g.drawLine(x + 45, y + 132, x + 365, y + 132);
        g.drawRect(x + 285, y + 40, 90, 80);
        drawPipe(g, x - 85, y + 60, 85, 38);
        drawPipe(g, x + 410, y + 60, 85, 38);
        dimension(g, x, y - 22, x + 410, y - 22, "4000");
        dimension(g, x + 435, y, x + 435, y + 160, "2000");
        dimension(g, x + 62, y + 82, x + 320, y + 82, "3000");
    }

    private void drawDetailViews(Graphics2D g, int x, int y) {
        g.setFont(font().deriveFont(Font.BOLD, 20f));
        g.drawString("三、关键部件详细设计", x, y - 18);
        g.setFont(font().deriveFont(Font.PLAIN, 15f));
        g.drawString("1. 布流装置（配水槽与布水孔）", x, y + 14);
        g.drawRect(x, y + 35, 240, 80);
        for (int i = 0; i < 12; i++) g.drawOval(x + 20 + i * 17, y + 67, 5, 5);
        dimension(g, x, y + 25, x + 240, y + 25, "1800");
        g.drawString("φ20布孔", x + 72, y + 96);
        g.drawString("2. 出水/出气堰板", x + 300, y + 14);
        g.drawRect(x + 300, y + 35, 230, 32);
        g.drawLine(x + 328, y + 35, x + 328, y + 130);
        g.drawString("堰板", x + 385, y + 92);
        g.drawString("3. 排灰系统", x + 580, y + 14);
        drawHopper(g, x + 610, y + 35, 110, 120);
        drawValve(g, x + 642, y + 158);
        g.drawString("60°", x + 665, y + 102);
        g.drawString("DN100排灰阀", x + 720, y + 170);
    }

    private void drawParameterTable(Graphics2D g, int x, int y) {
        drawTable(g, x, y, 575, 275, "二、主要结构尺寸参数表",
                new String[]{"序号", "名称", "尺寸/参数", "单位", "备注"},
                new String[][]{
                        {"1", "处理风量", "10000", "m³/h", "设计流量"},
                        {"2", "有效水深/高度", "2.80", "m", "沉降区有效深度"},
                        {"3", "有效长度", "3.60", "m", "水/气流方向长度"},
                        {"4", "有效宽度", "1.80", "m", "垂直流向宽度"},
                        {"5", "表面负荷", "6.94", "m³/(m²·h)", "Q/(L×B)"},
                        {"6", "停留时间", "3.33", "s", "V/Q"},
                        {"7", "池体总长", "4.00", "m", "含进出口槽"},
                        {"8", "池体总宽", "2.00", "m", ""},
                        {"9", "池体总高", "3.20", "m", "含灰斗"},
                        {"10", "灰斗角度", "60", "°", "利于排灰"},
                        {"11", "底板厚度", "10", "mm", ""},
                        {"12", "侧板厚度", "8", "mm", ""},
                        {"13", "顶板厚度", "6", "mm", ""},
                        {"14", "配水孔直径", "φ20", "mm", ""},
                        {"15", "配水孔间距", "150", "mm", ""}
                });
    }

    private void drawMaterialTable(Graphics2D g, int x, int y) {
        drawTable(g, x, y, 575, 230, "四、材料汇总表",
                new String[]{"部件", "材料", "厚度(mm)", "数量", "备注"},
                new String[][]{
                        {"底板", "Q235B", "10", "1", "整体钢板"},
                        {"侧板（下部）", "Q235B", "8", "2", "水深以下"},
                        {"侧板（上部）", "Q235B", "6", "2", "水深以上"},
                        {"端板", "Q235B", "8", "2", ""},
                        {"配水槽", "Q235B", "6", "1", ""},
                        {"堰板", "Q235B", "6", "1", ""},
                        {"灰斗", "Q235B", "10", "1", ""}
                });
    }

    private void drawCalculationBlock(Graphics2D g, int x, int y) {
        g.setFont(font().deriveFont(Font.BOLD, 20f));
        g.drawString("五、设计计算", x, y - 18);
        g.setFont(font().deriveFont(Font.PLAIN, 17f));
        g.drawString("1. 表面负荷计算", x, y + 20);
        g.drawString("qₛ = Q / (L × B) = 50 / (3.6 × 1.8) = 6.94 m³/(m²·h)", x + 25, y + 58);
        g.drawString("2. 有效容积计算", x, y + 105);
        g.drawString("V = L × B × H = 3.6 × 1.8 × 2.8 = 18.144 m³", x + 25, y + 143);
        g.drawString("3. 停留时间计算", x, y + 190);
        g.drawString("t = V / Q = 18.144 / 50 = 0.36288 h = 21.77 min", x + 25, y + 228);
    }

    private void drawSupportLayout(Graphics2D g, int x, int y) {
        g.setFont(font().deriveFont(Font.BOLD, 20f));
        g.drawString("六、支座布置图", x, y - 18);
        g.drawRect(x + 20, y + 20, 390, 210);
        int[][] pts = {{60, 55}, {350, 55}, {60, 180}, {350, 180}};
        for (int[] pt : pts) {
            g.fillRect(x + pt[0], y + pt[1], 25, 25);
        }
        dimension(g, x + 20, y, x + 410, y, "4000");
        dimension(g, x, y + 20, x, y + 230, "2000");
        dimension(g, x + 80, y + 255, x + 350, y + 255, "2800");
        g.drawString("■ 支座位置", x + 120, y + 290);
    }

    private void drawTechnicalNotes(Graphics2D g, int x, int y) {
        g.setFont(font().deriveFont(Font.BOLD, 20f));
        g.drawString("技术说明：", x, y - 18);
        g.setFont(font().deriveFont(Font.PLAIN, 16f));
        String[] lines = {
                "1. 进出口管法兰：DN200（φ219×6）。",
                "2. 排灰阀：DN100，采用星型卸料器。",
                "3. 检修孔：φ500，观察窗设密封圈。",
                "4. 材质：Q235B钢板，t=6~10mm。",
                "5. 焊缝：角焊缝，焊脚高度≥6mm。"
        };
        for (int i = 0; i < lines.length; i++) g.drawString(lines[i], x, y + 18 + i * 34);
    }

    private void drawEquipmentBom(Graphics2D g, int x, int y) {
        drawTable(g, x, y, 350, 225, "八、主要设备及管件表",
                new String[]{"名称", "规格", "材质", "数量", "备注"},
                new String[][]{
                        {"进气管", "DN200", "碳钢", "1", ""},
                        {"出气管", "DN200", "碳钢", "1", ""},
                        {"排灰阀", "DN100", "铸铁", "1", "间歇"},
                        {"检修孔盖", "φ500", "碳钢", "1", ""},
                        {"法兰", "DN200", "碳钢", "2", "GB/T 9119"}
                });
    }

    private void drawPartLegend(Graphics2D g, int x, int y) {
        g.setFont(font().deriveFont(Font.PLAIN, 17f));
        String[] parts = {"1-进气管", "2-进口三通", "3-布流槽", "4-布水孔", "5-沉降区", "6-出水/出气堰板", "7-出水槽", "8-出气管", "9-排灰斗", "10-排灰阀", "11-检修孔", "12-爬梯", "13-护栏", "14-支座", "15-底板"};
        for (int i = 0; i < parts.length; i++) g.drawString(parts[i], x, y + i * 30);
    }

    private void drawTable(Graphics2D g, int x, int y, int w, int h, String title, String[] headers, String[][] rows) {
        g.setFont(font().deriveFont(Font.BOLD, 20f));
        g.drawString(title, x, y - 18);
        int rowH = Math.max(15, h / (rows.length + 1));
        int colW = w / headers.length;
        g.setFont(font().deriveFont(Font.BOLD, rowH < 20 ? 12f : 14f));
        g.drawRect(x, y, w, rowH * (rows.length + 1));
        for (int c = 1; c < headers.length; c++) g.drawLine(x + c * colW, y, x + c * colW, y + rowH * (rows.length + 1));
        for (int r = 0; r <= rows.length; r++) g.drawLine(x, y + r * rowH, x + w, y + r * rowH);
        for (int c = 0; c < headers.length; c++) g.drawString(headers[c], x + c * colW + 8, y + 18);
        g.setFont(font().deriveFont(Font.PLAIN, rowH < 20 ? 11f : 13f));
        for (int r = 0; r < rows.length; r++) {
            for (int c = 0; c < headers.length; c++) {
                g.drawString(rows[r][c], x + c * colW + 8, y + (r + 1) * rowH + Math.max(12, rowH - 6));
            }
        }
    }

    private void drawPipe(Graphics2D g, int x, int y, int w, int h) {
        g.drawRect(x, y, w, h);
        g.drawOval(x - 10, y - 6, 20, h + 12);
        g.drawOval(x + w - 10, y - 6, 20, h + 12);
    }

    private void drawHopper(Graphics2D g, int x, int y, int w, int h) {
        g.drawPolygon(new int[]{x, x + w, x + w * 2 / 3, x + w / 3}, new int[]{y, y, y + h, y + h}, 4);
    }

    private void drawValve(Graphics2D g, int x, int y) {
        g.drawRect(x, y, 62, 24);
        g.drawOval(x + 18, y - 8, 26, 40);
        g.drawLine(x - 18, y + 12, x, y + 12);
        g.drawLine(x + 62, y + 12, x + 82, y + 12);
    }

    private void drawLeg(Graphics2D g, int x, int y) {
        g.drawPolygon(new int[]{x, x + 34, x + 44, x - 8}, new int[]{y, y, y + 92, y + 92}, 4);
    }

    private void drawLeader(Graphics2D g, int x, int y, String text) {
        g.setFont(font().deriveFont(Font.PLAIN, 15f));
        g.drawString(text, x, y);
        g.drawLine(x + 12, y + 5, x + 62, y + 35);
    }

    private void dimension(Graphics2D g, int x1, int y1, int x2, int y2, String text) {
        g.drawLine(x1, y1, x2, y2);
        g.drawLine(x1, y1 - 6, x1, y1 + 6);
        g.drawLine(x2, y2 - 6, x2, y2 + 6);
        g.setFont(font().deriveFont(Font.PLAIN, 14f));
        g.drawString(text, (x1 + x2) / 2 - 18, (y1 + y2) / 2 - 7);
    }

    private Canvas showcaseCanvas(DesignProject p) {
        Canvas c = new Canvas(p.getProjectTitle(), "设备结构示意图", "FA-01");
        c.text("TEXT", 55, 540, 9, p.getProjectTitle());
        c.text("TEXT", 55, 520, 4.5, "识别架构：" + p.getDesignType() + "；设备：" + p.getEquipmentName());
        double l = p.number("总长", 4200), w = p.number("总宽", 1600), h = p.number("总高", 1800);
        for (DesignProject.Component part : p.getComponents()) {
            project(c, part, "FRONT", 55, 130, 560, 330, l, w, h);
            double tx = 55 + (part.getX() + part.getLength() / 2) / l * 560;
            double ty = 140 + (part.getZ() + part.getHeight()) / h * 330;
            c.text("ANNOTATION", tx, Math.min(485, ty), 3.5, part.getSequence() + " " + part.getName());
        }
        c.rect("TABLE", 640, 130, 170, 350);
        c.text("TABLE", 655, 455, 5, "结构组成");
        int row = 0;
        for (DesignProject.BomItem item : p.getBom().stream().limit(10).toList()) {
            c.text("TABLE", 655, 425 - row++ * 28, 3.5, item.getSequence() + ". " + item.getName() + " ×" + item.getQuantity());
        }
        return c;
    }

    private Graphics2D graphics(BufferedImage image, Color bg){Graphics2D g=image.createGraphics();g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,RenderingHints.VALUE_TEXT_ANTIALIAS_ON);g.setColor(bg);g.fillRect(0,0,image.getWidth(),image.getHeight());g.setColor(new Color(24,34,48));g.setStroke(new BasicStroke(2));return g;}
    private void draw(Graphics2D g,Shape s,double scale,boolean fill){int x=(int)(s.x1*scale),y=(int)((590-s.y1)*scale);if("LINE".equals(s.type))g.drawLine(x,y,(int)(s.x2*scale),(int)((590-s.y2)*scale));else if("CIRCLE".equals(s.type)){int r=(int)(s.size*scale);g.drawOval(x-r,y-r,2*r,2*r);}else{g.setFont(font().deriveFont(Math.max(12f,(float)(s.size*scale))));g.drawString(s.text,x,y);}}
    private Font font(){for(String n:FONTS){Font f=new Font(n,Font.PLAIN,14);if(f.canDisplay('中'))return f;}return new Font("Dialog",Font.PLAIN,14);}
    private String layer(DesignProject.Component c){return switch(c.getRole()){case "BODY"->"BODY";case "SUPPORT","BASE"->"SUPPORT";case "INTERFACE"->"INTERFACE";case "FUNCTION"->"FUNCTION";default->"STRUCTURE";};}
    private String trim(String v,int n){return v==null?"":v.length()>n?v.substring(0,n)+"…":v;} private String escape(String v){return v.replace("&","&amp;").replace("<","&lt;").replace(">","&gt;");}

    static class Canvas {
        final String title,name,no; final List<Shape> shapes=new ArrayList<>();
        Canvas(String t,String n,String no){title=t;name=n;this.no=no;}
        void line(String l,double a,double b,double c,double d){shapes.add(new Shape("LINE",l,a,b,c,d,0,""));}
        void rect(String l,double x,double y,double w,double h){line(l,x,y,x+w,y);line(l,x+w,y,x+w,y+h);line(l,x+w,y+h,x,y+h);line(l,x,y+h,x,y);}
        void circle(String l,double x,double y,double r){shapes.add(new Shape("CIRCLE",l,x,y,0,0,r,""));}
        void poly(String l,double... points){for(int i=0;i<points.length;i+=2){int n=(i+2)%points.length;line(l,points[i],points[i+1],points[n],points[n+1]);}}
        void text(String l,double x,double y,double s,String t){shapes.add(new Shape("TEXT",l,x,y,0,0,s,t));}
        String dxf(){StringBuilder b=new StringBuilder("0\nSECTION\n2\nHEADER\n9\n$ACADVER\n1\nAC1027\n0\nENDSEC\n0\nSECTION\n2\nTABLES\n0\nTABLE\n2\nLAYER\n70\n20\n");for(String l:List.of("FRAME","TITLE","BODY","SUPPORT","INTERFACE","FUNCTION","STRUCTURE","OUTLINE","CENTER","DIMENSION","ANNOTATION","TABLE","TEXT","SECTION","HATCH","CUTTING","TOLERANCE","JOINT"))b.append("0\nLAYER\n2\n").append(l).append("\n70\n0\n62\n7\n6\nCONTINUOUS\n");b.append("0\nENDTAB\n0\nENDSEC\n0\nSECTION\n2\nENTITIES\n");shapes.forEach(s->s.dxf(b));return b.append("0\nENDSEC\n0\nEOF\n").toString();}
        String svg(){StringBuilder b=new StringBuilder("<svg xmlns=\"http://www.w3.org/2000/svg\" viewBox=\"0 0 840 590\"><rect width=\"840\" height=\"590\" fill=\"white\"/>");shapes.forEach(s->s.svg(b));return b.append("</svg>").toString();}
    }
    record Shape(String type,String layer,double x1,double y1,double x2,double y2,double size,String text){
        void dxf(StringBuilder b){b.append("0\n").append(type).append("\n8\n").append(layer).append('\n');if("LINE".equals(type))b.append("10\n").append(x1).append("\n20\n").append(y1).append("\n11\n").append(x2).append("\n21\n").append(y2).append('\n');else if("CIRCLE".equals(type))b.append("10\n").append(x1).append("\n20\n").append(y1).append("\n40\n").append(size).append('\n');else b.append("10\n").append(x1).append("\n20\n").append(y1).append("\n40\n").append(size).append("\n1\n").append(text).append('\n');}
        void svg(StringBuilder b){if("LINE".equals(type))b.append("<line x1=\"").append(x1).append("\" y1=\"").append(590-y1).append("\" x2=\"").append(x2).append("\" y2=\"").append(590-y2).append("\" stroke=\"#182230\"/>");else if("CIRCLE".equals(type))b.append("<circle cx=\"").append(x1).append("\" cy=\"").append(590-y1).append("\" r=\"").append(size).append("\" fill=\"white\" stroke=\"#182230\"/>");else b.append("<text x=\"").append(x1).append("\" y=\"").append(590-y1).append("\" font-size=\"").append(size).append("\" font-family=\"Noto Sans CJK SC,sans-serif\">").append(escape(text)).append("</text>");}
        private static String escape(String v){return v.replace("&","&amp;").replace("<","&lt;").replace(">","&gt;");}
    }
}
