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

    public List<DrawingArtifact> drawAssemblyDrawing(DesignProject project) {
        Canvas c = new Canvas(project.getProjectTitle(), "总装图", "ZD-00");
        frame(c); title(c); componentViews(c, project); dimensions(c, project); bom(c, project); requirements(c, project);
        return List.of(
                new DrawingArtifact("assembly.dxf", c.dxf().getBytes(StandardCharsets.UTF_8), "application/dxf"),
                new DrawingArtifact("cad_preview.svg", c.svg().getBytes(StandardCharsets.UTF_8), "image/svg+xml"),
                new DrawingArtifact("cad_preview.png", renderCad(c), "image/png"),
                new DrawingArtifact("preview.svg", showcaseSvg(project).getBytes(StandardCharsets.UTF_8), "image/svg+xml"),
                new DrawingArtifact("preview.png", renderShowcase(project), "image/png"));
    }

    public List<DrawingArtifact> drawPartDrawing(DesignProject project) {
        List<DesignProject.Component> keyParts = project.getComponents().stream().filter(DesignProject.Component::isKeyPart).limit(4).toList();
        List<DrawingArtifact> result = new ArrayList<>();
        for (int i = 0; i < keyParts.size(); i++) {
            DesignProject.Component p = keyParts.get(i);
            Canvas c = new Canvas(project.getProjectTitle(), p.getName() + "零件图", "LJ-%02d".formatted(i + 1));
            frame(c); title(c);
            c.rect("OUTLINE", 160, 230, 360, 190); c.rect("STRUCTURE", 195, 265, 290, 120);
            drawPartFeatures(c, p);
            c.text("TEXT", 180, 440, 4, "零件：" + p.getName());
            c.text("TEXT", 180, 420, 3.5, "材料：" + p.getMaterial());
            c.text("TEXT", 180, 400, 3.5, "功能：" + p.getFunction());
            dimension(c, 160, 205, 520, 205, "长度 " + fmt(p.getLength()) + " mm");
            dimension(c, 140, 230, 140, 420, "高度 " + fmt(p.getHeight()) + " mm");
            requirements(c, project);
            result.add(new DrawingArtifact("part_%02d.dxf".formatted(i + 1), c.dxf().getBytes(StandardCharsets.UTF_8), "application/dxf"));
        }
        return result;
    }

    private void componentViews(Canvas c, DesignProject p) {
        double totalL = p.number("总长", 4200), totalW = p.number("总宽", 1600), totalH = p.number("总高", 1800);
        double mx = 70, my = 300, mw = 400, mh = 160;
        double tx = 70, ty = 105, tw = 400, th = 130;
        double sx = 520, sy = 205, sw = 145, sh = 165;
        c.text("TEXT", mx, my - 18, 4, "主视图"); c.text("TEXT", tx, ty - 18, 4, "俯视图"); c.text("TEXT", sx, sy - 18, 4, "侧视图");
        for (DesignProject.Component part : p.getComponents()) {
            c.rect(layer(part), mx + part.getX() / totalL * mw, my + part.getZ() / totalH * mh,
                    Math.max(8, part.getLength() / totalL * mw), Math.max(6, part.getHeight() / totalH * mh));
            c.rect(layer(part), tx + part.getX() / totalL * tw, ty + part.getY() / totalW * th,
                    Math.max(8, part.getLength() / totalL * tw), Math.max(6, part.getWidth() / totalW * th));
            c.rect(layer(part), sx + part.getY() / totalW * sw, sy + part.getZ() / totalH * sh,
                    Math.max(8, part.getWidth() / totalW * sw), Math.max(6, part.getHeight() / totalH * sh));
            drawViewFeatures(c, part, mx, my, mw, mh, totalL, totalH);
            if (part.isKeyPart()) {
                double bx = mx + (part.getX() + part.getLength() / 2) / totalL * mw;
                double by = my + (part.getZ() + part.getHeight() / 2) / totalH * mh;
                c.circle("ANNOTATION", bx, by, 8); c.text("ANNOTATION", bx - 2, by - 2, 3.5, String.valueOf(part.getSequence()));
            }
        }
    }

    private void drawViewFeatures(Canvas c, DesignProject.Component part, double x, double y, double w, double h, double totalL, double totalH) {
        double cx = x + (part.getX() + part.getLength() / 2) / totalL * w;
        double cy = y + (part.getZ() + part.getHeight() / 2) / totalH * h;
        if ("INTERFACE".equals(part.getRole()) || "FUNCTION".equals(part.getRole())) {
            c.circle(layer(part), cx, cy, Math.max(5, Math.min(part.getLength() / totalL * w, part.getHeight() / totalH * h) / 3));
        }
        if ("SUPPORT".equals(part.getRole()) || "BASE".equals(part.getRole())) {
            c.line("STRUCTURE", cx - 12, cy - 8, cx + 12, cy + 8);
            c.line("STRUCTURE", cx - 12, cy + 8, cx + 12, cy - 8);
        }
    }

    private void drawPartFeatures(Canvas c, DesignProject.Component part) {
        if ("INTERFACE".equals(part.getRole()) || "FUNCTION".equals(part.getRole())) {
            c.circle("STRUCTURE", 340, 325, 48);
            c.circle("CENTER", 340, 325, 22);
        } else if ("BASE".equals(part.getRole()) || "SUPPORT".equals(part.getRole())) {
            for (double x : List.of(215d, 465d)) {
                c.circle("CENTER", x, 290, 10);
                c.circle("CENTER", x, 360, 10);
            }
            c.line("STRUCTURE", 195, 265, 485, 385);
            c.line("STRUCTURE", 195, 385, 485, 265);
        } else {
            c.rect("STRUCTURE", 245, 290, 190, 70);
            c.circle("CENTER", 270, 325, 8);
            c.circle("CENTER", 410, 325, 8);
        }
    }

    private void dimensions(Canvas c, DesignProject p) {
        int i = 0;
        for (DesignProject.DimensionChain d : p.getDimensionChains()) {
            if (i >= 6) break;
            c.text("DIMENSION", 690, 350 - i * 20, 3.2, d.getName() + "：" + fmt(d.getValue()) + " " + d.getUnit());
            i++;
        }
        dimension(c, 70, 285, 470, 285, "总长 " + fmt(p.number("总长", 4200)));
        dimension(c, 55, 300, 55, 460, "总高 " + fmt(p.number("总高", 1800)));
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
    private void dimension(Canvas c, double x1, double y1, double x2, double y2, String label) {
        c.line("DIMENSION", x1, y1, x2, y2); c.line("DIMENSION", x1 - 4, y1 - 4, x1 + 4, y1 + 4);
        c.line("DIMENSION", x2 - 4, y2 - 4, x2 + 4, y2 + 4); c.text("DIMENSION", (x1+x2)/2, (y1+y2)/2+8, 3.2, label);
    }

    private byte[] renderCad(Canvas c) {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            BufferedImage image = new BufferedImage(1680, 1180, BufferedImage.TYPE_INT_RGB);
            Graphics2D g = graphics(image, Color.WHITE);
            for (Shape s : c.shapes) draw(g, s, 2, false);
            g.dispose(); ImageIO.write(image, "png", out); return out.toByteArray();
        } catch (Exception e) { throw new IllegalStateException("生成CAD预览失败：" + e.getMessage(), e); }
    }

    private String showcaseSvg(DesignProject p) {
        StringBuilder b = new StringBuilder("<svg xmlns=\"http://www.w3.org/2000/svg\" viewBox=\"0 0 1200 720\"><rect width=\"1200\" height=\"720\" fill=\"#f3f7fb\"/>");
        b.append("<text x=\"60\" y=\"70\" font-size=\"32\" font-family=\"Noto Sans CJK SC,sans-serif\" fill=\"#10233f\">").append(escape(p.getProjectTitle())).append("</text>");
        b.append("<text x=\"60\" y=\"108\" font-size=\"17\" font-family=\"Noto Sans CJK SC,sans-serif\" fill=\"#52657d\">").append(escape(p.getWorkingPrinciple())).append("</text>");
        String[] colors = {"#5b8ff9","#61d9a0","#f6bd16","#e8684a","#9270ca","#6dc8ec","#ff99c3"};
        int i = 0;
        for (DesignProject.Component c : p.getComponents()) {
            double x=80+c.getX()/p.number("总长",4200)*720, y=180+c.getZ()/p.number("总高",1800)*330;
            double w=Math.max(45,c.getLength()/p.number("总长",4200)*720), h=Math.max(28,c.getHeight()/p.number("总高",1800)*330);
            b.append("<rect x=\"").append(x).append("\" y=\"").append(560-y-h).append("\" width=\"").append(w).append("\" height=\"").append(h)
                    .append("\" rx=\"8\" fill=\"").append(colors[i++%colors.length]).append("\" opacity=\".82\" stroke=\"#17324d\"/>");
            b.append("<text x=\"").append(x+5).append("\" y=\"").append(580-y-h).append("\" font-size=\"13\" font-family=\"Noto Sans CJK SC,sans-serif\">").append(escape(c.getName())).append("</text>");
        }
        b.append("<rect x=\"850\" y=\"155\" width=\"290\" height=\"420\" rx=\"18\" fill=\"white\" stroke=\"#d7e2ed\"/><text x=\"880\" y=\"200\" font-size=\"22\" font-family=\"Noto Sans CJK SC,sans-serif\">结构与参数摘要</text>");
        int row=0; for (DesignProject.BomItem item:p.getBom().stream().limit(9).toList()) b.append("<text x=\"880\" y=\"").append(240+row++*28).append("\" font-size=\"15\" font-family=\"Noto Sans CJK SC,sans-serif\">").append(item.getSequence()).append(". ").append(escape(item.getName())).append(" ×").append(item.getQuantity()).append("</text>");
        return b.append("</svg>").toString();
    }

    private byte[] renderShowcase(DesignProject p) {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            BufferedImage image = new BufferedImage(1600, 960, BufferedImage.TYPE_INT_RGB);
            Graphics2D g = graphics(image, new Color(243,247,251)); Font f=font();
            g.setColor(new Color(16,35,63)); g.setFont(f.deriveFont(34f)); g.drawString(p.getProjectTitle(),70,70);
            Color[] colors={new Color(91,143,249),new Color(97,217,160),new Color(246,189,22),new Color(232,104,74),new Color(146,112,202),new Color(109,200,236)};
            int i=0; double L=p.number("总长",4200),H=p.number("总高",1800);
            for(DesignProject.Component c:p.getComponents()){int x=(int)(90+c.getX()/L*950), y=(int)(700-(c.getZ()+c.getHeight())/H*470), w=(int)Math.max(55,c.getLength()/L*950), h=(int)Math.max(35,c.getHeight()/H*470); g.setColor(colors[i++%colors.length]);g.fillRoundRect(x,y,w,h,14,14);g.setColor(new Color(20,45,70));g.drawRoundRect(x,y,w,h,14,14);g.setFont(f.deriveFont(17f));g.drawString(c.getName(),x+6,y-6);}
            g.setColor(Color.WHITE);g.fillRoundRect(1150,130,380,650,24,24);g.setColor(new Color(20,45,70));g.setFont(f.deriveFont(24f));g.drawString("结构与参数摘要",1190,185);g.setFont(f.deriveFont(17f));int y=230;for(DesignProject.BomItem item:p.getBom().stream().limit(12).toList()){g.drawString(item.getSequence()+". "+item.getName()+" ×"+item.getQuantity(),1190,y);y+=34;}
            g.dispose();ImageIO.write(image,"png",out);return out.toByteArray();
        }catch(Exception e){throw new IllegalStateException("生成方案展示图失败："+e.getMessage(),e);}
    }

    private Graphics2D graphics(BufferedImage image, Color bg){Graphics2D g=image.createGraphics();g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,RenderingHints.VALUE_TEXT_ANTIALIAS_ON);g.setColor(bg);g.fillRect(0,0,image.getWidth(),image.getHeight());g.setColor(new Color(24,34,48));g.setStroke(new BasicStroke(2));return g;}
    private void draw(Graphics2D g,Shape s,double scale,boolean fill){int x=(int)(s.x1*scale),y=(int)((590-s.y1)*scale);if("LINE".equals(s.type))g.drawLine(x,y,(int)(s.x2*scale),(int)((590-s.y2)*scale));else if("CIRCLE".equals(s.type)){int r=(int)(s.size*scale);g.drawOval(x-r,y-r,2*r,2*r);}else{g.setFont(font().deriveFont(Math.max(12f,(float)(s.size*scale))));g.drawString(s.text,x,y);}}
    private Font font(){for(String n:FONTS){Font f=new Font(n,Font.PLAIN,14);if(f.canDisplay('中'))return f;}return new Font("Dialog",Font.PLAIN,14);}
    private String layer(DesignProject.Component c){return switch(c.getRole()){case "BODY"->"BODY";case "SUPPORT","BASE"->"SUPPORT";case "INTERFACE"->"INTERFACE";case "FUNCTION"->"FUNCTION";default->"STRUCTURE";};}
    private String fmt(double v){return "%.0f".formatted(v);} private String trim(String v,int n){return v==null?"":v.length()>n?v.substring(0,n)+"…":v;} private String escape(String v){return v.replace("&","&amp;").replace("<","&lt;").replace(">","&gt;");}

    static class Canvas {
        final String title,name,no; final List<Shape> shapes=new ArrayList<>();
        Canvas(String t,String n,String no){title=t;name=n;this.no=no;}
        void line(String l,double a,double b,double c,double d){shapes.add(new Shape("LINE",l,a,b,c,d,0,""));}
        void rect(String l,double x,double y,double w,double h){line(l,x,y,x+w,y);line(l,x+w,y,x+w,y+h);line(l,x+w,y+h,x,y+h);line(l,x,y+h,x,y);}
        void circle(String l,double x,double y,double r){shapes.add(new Shape("CIRCLE",l,x,y,0,0,r,""));}
        void text(String l,double x,double y,double s,String t){shapes.add(new Shape("TEXT",l,x,y,0,0,s,t));}
        String dxf(){StringBuilder b=new StringBuilder("0\nSECTION\n2\nHEADER\n9\n$ACADVER\n1\nAC1027\n0\nENDSEC\n0\nSECTION\n2\nTABLES\n0\nTABLE\n2\nLAYER\n70\n13\n");for(String l:List.of("FRAME","TITLE","BODY","SUPPORT","INTERFACE","FUNCTION","STRUCTURE","CENTER","DIMENSION","ANNOTATION","TABLE","TEXT"))b.append("0\nLAYER\n2\n").append(l).append("\n70\n0\n62\n7\n6\nCONTINUOUS\n");b.append("0\nENDTAB\n0\nENDSEC\n0\nSECTION\n2\nENTITIES\n");shapes.forEach(s->s.dxf(b));return b.append("0\nENDSEC\n0\nEOF\n").toString();}
        String svg(){StringBuilder b=new StringBuilder("<svg xmlns=\"http://www.w3.org/2000/svg\" viewBox=\"0 0 840 590\"><rect width=\"840\" height=\"590\" fill=\"white\"/>");shapes.forEach(s->s.svg(b));return b.append("</svg>").toString();}
    }
    record Shape(String type,String layer,double x1,double y1,double x2,double y2,double size,String text){
        void dxf(StringBuilder b){b.append("0\n").append(type).append("\n8\n").append(layer).append('\n');if("LINE".equals(type))b.append("10\n").append(x1).append("\n20\n").append(y1).append("\n11\n").append(x2).append("\n21\n").append(y2).append('\n');else if("CIRCLE".equals(type))b.append("10\n").append(x1).append("\n20\n").append(y1).append("\n40\n").append(size).append('\n');else b.append("10\n").append(x1).append("\n20\n").append(y1).append("\n40\n").append(size).append("\n1\n").append(text).append('\n');}
        void svg(StringBuilder b){if("LINE".equals(type))b.append("<line x1=\"").append(x1).append("\" y1=\"").append(590-y1).append("\" x2=\"").append(x2).append("\" y2=\"").append(590-y2).append("\" stroke=\"#182230\"/>");else if("CIRCLE".equals(type))b.append("<circle cx=\"").append(x1).append("\" cy=\"").append(590-y1).append("\" r=\"").append(size).append("\" fill=\"white\" stroke=\"#182230\"/>");else b.append("<text x=\"").append(x1).append("\" y=\"").append(590-y1).append("\" font-size=\"").append(size).append("\" font-family=\"Noto Sans CJK SC,sans-serif\">").append(escape(text)).append("</text>");}
        private static String escape(String v){return v.replace("&","&amp;").replace("<","&lt;").replace(">","&gt;");}
    }
}
