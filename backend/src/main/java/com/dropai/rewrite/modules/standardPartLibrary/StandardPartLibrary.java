package com.dropai.rewrite.modules.standardPartLibrary;

import com.dropai.rewrite.modules.model.DesignProject;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class StandardPartLibrary {
    private final Map<String, String> catalog = new LinkedHashMap<>();

    public StandardPartLibrary() {
        catalog.put("轴承", "深沟球轴承 6204");
        catalog.put("电机", "直流减速电机 24V");
        catalog.put("减速器", "行星减速器 i=20");
        catalog.put("联轴器", "弹性联轴器 LX");
        catalog.put("导轨", "微型直线导轨 MGN");
        catalog.put("滑轨", "微型直线导轨 MGN");
        catalog.put("滚轮", "包胶滚轮");
        catalog.put("轮", "包胶滚轮");
        catalog.put("链轮", "08B链轮");
        catalog.put("同步带轮", "HTD同步带轮");
        catalog.put("螺栓", "M6 8.8级内六角螺栓");
        catalog.put("螺母", "M6防松螺母");
        catalog.put("法兰", "平焊法兰 PN10");
        catalog.put("轴", "45钢传动轴");
        catalog.put("键", "A型平键");
        catalog.put("销", "圆柱销");
        catalog.put("弹簧", "压缩弹簧");
        catalog.put("传感器", "检测传感器安装位");
    }

    public DesignProject resolve(DesignProject project) {
        List<DesignProject.DesignPart> parts = new ArrayList<>();
        collect(project.getStructureTree(), "", parts);
        project.setResolvedParts(parts);
        project.setStandardParts(parts.stream().filter(p -> "standard".equals(p.getPartType())).map(DesignProject.DesignPart::getName).distinct().toList());
        return project;
    }

    private void collect(DesignProject.StructureNode node, String parent, List<DesignProject.DesignPart> parts) {
        if (node == null) return;
        if (!"整机".equals(node.getName())) parts.add(partFor(node.getName(), parent));
        for (DesignProject.StructureNode child : node.getChildren()) collect(child, node.getName(), parts);
    }

    private DesignProject.DesignPart partFor(String name, String parent) {
        DesignProject.DesignPart part = new DesignProject.DesignPart();
        part.setName(name);
        part.setParentStructure(parent);
        for (Map.Entry<String, String> entry : catalog.entrySet()) {
            if (name.contains(entry.getKey())) {
                part.setPartType("standard");
                part.setModel(entry.getValue());
                part.setSource("library");
                part.setMaterial(materialOf(name));
                part.setProcess("标准件选型");
                part.setGeometryFeatures(List.of("标准安装孔", "型号选型", "装配定位面"));
                part.setQuantity(quantityOf(name));
                return part;
            }
        }
        part.setPartType("unresolved");
        part.setSource("structureTree");
        part.setQuantity(quantityOf(name));
        return part;
    }

    private String materialOf(String name) {
        if (name.contains("电机") || name.contains("减速")) return "标准件";
        if (name.contains("导轨") || name.contains("轴") || name.contains("轮")) return "45钢";
        if (name.contains("螺栓") || name.contains("螺母") || name.contains("销") || name.contains("键")) return "标准件";
        return "Q235B";
    }

    private int quantityOf(String name) {
        if (name.contains("螺栓")) return 8;
        if (name.contains("支重") || name.contains("滚轮")) return 4;
        if (name.contains("轮") || name.contains("电机") || name.contains("减速器")) return 2;
        return 1;
    }
}
