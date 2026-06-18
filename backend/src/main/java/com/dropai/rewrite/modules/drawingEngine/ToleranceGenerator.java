package com.dropai.rewrite.modules.drawingEngine;

import com.dropai.rewrite.modules.model.DesignProject;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class ToleranceGenerator {
    public List<String> assemblyRequirements(DesignProject project) {
        List<String> items = new ArrayList<>();
        items.add("未注尺寸公差按 GB/T 1804-m 执行。");
        items.add("未注倒角 C1，锐边倒钝，运动部位不得有毛刺。");
        items.add("关键安装面设为基准A，履带或导轨中心平面设为基准B，主要轴孔中心线设为基准C。");
        items.add("焊接件焊缝应连续均匀，焊后清理飞溅并进行防锈处理。");
        items.add("装配后履带、清扫刷、检测支架运动应平稳，无明显卡滞。");
        return items;
    }

    public List<String> partRequirements(DesignProject.Component part, MechanicalFeatureLibrary.FeatureSet features) {
        List<String> items = new ArrayList<>();
        items.add("材料：" + material(part) + "。");
        items.add("未注尺寸公差按 GB/T 1804-m，未注倒角 C1。");
        items.add("安装孔位置度按装配要求控制，孔口倒角并去毛刺。");
        items.add("关键安装面为基准A，轴孔或导轨中心线为基准B。");
        if (features.family().contains("机架")) {
            items.add("焊缝连续均匀，焊后校正变形，表面喷涂防锈底漆。");
        } else if (features.family().contains("履带")) {
            items.add("轮轴孔同轴度按装配要求控制，运动面粗糙度 Ra3.2。");
        } else if (features.family().contains("清扫")) {
            items.add("刷盘回转中心与连接轴中心重合，刷毛分布均匀。");
        } else if (features.family().contains("磁吸附")) {
            items.add("磁块安装槽尺寸应保证定位可靠，防护盖固定后不得松动。");
        } else if (features.family().contains("检测")) {
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
        c.rect("TOLERANCE", x, y - 22, 110, 14);
        c.line("TOLERANCE", x + 28, y - 22, x + 28, y - 8);
        c.line("TOLERANCE", x + 70, y - 22, x + 70, y - 8);
        c.text("TOLERANCE", x + 6, y - 18, 3, "位置度");
        c.text("TOLERANCE", x + 36, y - 18, 3, "0.20");
        c.text("TOLERANCE", x + 78, y - 18, 3, "A-B");
        c.text("ANNOTATION", x, y - 42, 3, "表面粗糙度：安装面 Ra3.2，其余 Ra6.3");
    }

    private String material(DesignProject.Component part) {
        return part.getMaterial() == null || part.getMaterial().isBlank() ? "Q235B" : part.getMaterial();
    }

    private String trim(String value, int length) {
        return value == null ? "" : value.length() > length ? value.substring(0, length) : value;
    }
}
