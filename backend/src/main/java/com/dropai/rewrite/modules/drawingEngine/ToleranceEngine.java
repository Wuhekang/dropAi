package com.dropai.rewrite.modules.drawingEngine;

import com.dropai.rewrite.modules.model.DesignProject;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class ToleranceEngine {
    private final EngineeringSemanticLayer semanticLayer = new EngineeringSemanticLayer();

    public List<String> assemblyRequirements(DesignProject project) {
        List<String> items = new ArrayList<>();
        items.add("未注尺寸公差按 GB/T 1804-m 执行。");
        items.add("未注倒角 C1，锐边倒钝，运动部位不得有毛刺。");
        items.add("基准A为关键安装面，基准B为履带或导轨中心平面，基准C为主要轴孔中心线。");
        items.add("安装孔位置度按装配要求控制，孔口倒角并去毛刺。");
        items.add("装配后运动部件应转动灵活，无明显卡滞。");
        return items;
    }

    public List<String> partRequirements(DesignProject.Component part, MechanicalFeatureLibrary.FeatureSet features) {
        List<String> items = new ArrayList<>();
        items.add("材料：" + material(part) + "。");
        items.add("未注尺寸公差按 GB/T 1804-m，未注倒角 C1。");
        items.add("安装孔位置度按装配要求控制，孔口倒角并去毛刺。");
        items.add("关键安装面为基准A，轴孔或导轨中心线为基准B。");
        String family = features == null ? "" : features.family();
        if (family.contains("机架")) {
            items.add("焊缝连续均匀，焊后校正变形，表面喷涂防锈底漆。");
        } else if (family.contains("履带")) {
            items.add("轮轴孔同轴度按装配要求控制，运动面粗糙度 Ra3.2。");
        } else if (family.contains("清扫")) {
            items.add("刷盘回转中心与连接轴中心重合，刷毛分布均匀。");
        } else if (family.contains("磁吸附")) {
            items.add("磁块安装槽应保证定位可靠，防护盖固定后不得松动。");
        } else if (family.contains("检测")) {
            items.add("调节槽边缘倒钝，传感器安装面平面度满足检测精度要求。");
        }
        return items;
    }

    public void drawToleranceBlock(DrawingEngine.Canvas c, double x, double y, List<String> items) {
        c.text("TEXT", x, y + 68, 4, "技术要求");
        int row = 0;
        for (String item : items.stream().limit(5).toList()) {
            c.text("TEXT", x, y + 50 - row * 14, 2.8, (row + 1) + ". " + trim(item, 31));
            row++;
        }
    }

    public void drawDatumAndGdt(DrawingEngine.Canvas c, double x, double y) {
        c.rect("ANNOTATION", x, y, 16, 12);
        c.text("ANNOTATION", x + 5, y + 3, 3.5, "A");
        c.rect("ANNOTATION", x + 24, y, 16, 12);
        c.text("ANNOTATION", x + 29, y + 3, 3.5, "B");
        c.rect("ANNOTATION", x + 48, y, 16, 12);
        c.text("ANNOTATION", x + 53, y + 3, 3.5, "C");
        gdt(c, x, y - 22, "位置度", "0.20", "A-B");
        gdt(c, x, y - 42, "平行度", "0.10", "A");
        gdt(c, x, y - 62, "垂直度", "0.15", "A-C");
    }

    private void gdt(DrawingEngine.Canvas c, double x, double y, String symbol, String value, String datum) {
        c.rect("TOLERANCE", x, y, 116, 14);
        c.line("TOLERANCE", x + 34, y, x + 34, y + 14);
        c.line("TOLERANCE", x + 74, y, x + 74, y + 14);
        c.text("TOLERANCE", x + 5, y + 4, 3, symbol);
        c.text("TOLERANCE", x + 43, y + 4, 3, value);
        c.text("TOLERANCE", x + 83, y + 4, 3, datum);
    }

    private String material(DesignProject.Component part) {
        return semanticLayer.material(part);
    }

    private String trim(String value, int length) {
        return value == null ? "" : value.length() > length ? value.substring(0, length) : value;
    }
}
