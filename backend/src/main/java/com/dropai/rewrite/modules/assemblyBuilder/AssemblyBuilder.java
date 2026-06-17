package com.dropai.rewrite.modules.assemblyBuilder;

import com.dropai.rewrite.modules.model.DesignProject;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class AssemblyBuilder {
    public DesignProject build(DesignProject project) {
        DesignProject.AssemblyNode root = new DesignProject.AssemblyNode("整机", "root");
        Map<String, DesignProject.AssemblyNode> parents = new LinkedHashMap<>();
        for (DesignProject.DesignPart part : project.getResolvedParts()) {
            String parentName = blank(part.getParentStructure()) ? "整机装配" : part.getParentStructure();
            DesignProject.AssemblyNode parent = parents.computeIfAbsent(parentName, key -> {
                DesignProject.AssemblyNode node = new DesignProject.AssemblyNode(key, relationOf(key));
                root.getChildren().add(node);
                return node;
            });
            parent.getChildren().add(new DesignProject.AssemblyNode(part.getName(), relationOf(part.getName())));
        }
        project.setAssemblyTree(root);
        project.setComponents(toComponents(project));
        return project;
    }

    private List<DesignProject.Component> toComponents(DesignProject project) {
        List<DesignProject.Component> result = new ArrayList<>();
        double l = project.number("总长", project.number("整机长度", 4200));
        double w = project.number("总宽", project.number("整机宽度", 1600));
        double h = project.number("总高", project.number("整机高度", 1800));
        int i = 0;
        for (DesignProject.DesignPart part : project.getResolvedParts()) {
            double row = i % 4;
            double col = i / 4.0;
            double x = l * (0.08 + row * 0.21);
            double y = w * (0.14 + (col % 3) * 0.26);
            double z = h * (0.08 + (i % 5) * 0.13);
            double pl = l * scale(part.getName(), 0.12, 0.22);
            double pw = w * scale(part.getName(), 0.10, 0.18);
            double ph = h * scale(part.getName(), 0.08, 0.18);
            DesignProject.Component component = new DesignProject.Component(result.size() + 1, role(part), part.getName(),
                    function(part), part.getMaterial(), Math.max(1, part.getQuantity()), x, y, z, pl, pw, ph, i < 12);
            component.setGeometry(geometry(part));
            result.add(component);
            i++;
        }
        return result;
    }

    private String relationOf(String name) {
        if (containsAny(name, "螺栓", "法兰", "快拆", "安装")) return "bolted";
        if (containsAny(name, "电机", "减速", "轴", "轮", "带")) return "driven";
        if (containsAny(name, "导轨", "滑轨")) return "sliding";
        return "fixed";
    }

    private String role(DesignProject.DesignPart part) {
        String name = part.getName();
        if ("standard".equals(part.getPartType())) return containsAny(name, "电机", "减速", "轴", "轮", "带") ? "DRIVE" : "CONNECT";
        if (containsAny(name, "机架", "支撑", "底座")) return "SUPPORT";
        if (containsAny(name, "外壳", "防护")) return "SAFETY";
        if (containsAny(name, "检修", "维护", "快拆")) return "MAINTENANCE";
        if (containsAny(name, "安装", "吸附", "磁")) return "MOUNT";
        return "FUNCTION";
    }

    private String geometry(DesignProject.DesignPart part) {
        String name = part.getName();
        if (containsAny(name, "履带", "带")) return "TRACK";
        if (containsAny(name, "轮", "滚筒")) return "WHEEL";
        if (containsAny(name, "电机")) return "MOTOR";
        if (containsAny(name, "减速")) return "GEARBOX";
        if (containsAny(name, "刷")) return "BRUSH";
        if (containsAny(name, "磁", "吸附")) return "MAGNET_BLOCK";
        if (containsAny(name, "导轨", "滑轨", "检测")) return "SENSOR_RAIL";
        if (containsAny(name, "机架", "支架")) return "FRAME";
        if (containsAny(name, "外壳", "防护", "盖")) return "COVER";
        if (containsAny(name, "螺栓", "孔")) return "BOLT_GROUP";
        if (containsAny(name, "法兰")) return "FLANGE";
        return "PLATE";
    }

    private String function(DesignProject.DesignPart part) {
        return ("standard".equals(part.getPartType()) ? "标准件：" + part.getModel() : "非标件：" + String.join("、", part.getGeometryFeatures()));
    }

    private double scale(String name, double min, double max) {
        if (containsAny(name, "机架", "履带", "外壳", "箱体", "主体")) return max;
        if (containsAny(name, "螺栓", "销", "键")) return min * 0.45;
        return (min + max) / 2;
    }

    private boolean blank(String value) { return value == null || value.isBlank(); }
    private boolean containsAny(String value, String... words) {
        if (value == null) return false;
        for (String word : words) if (value.contains(word)) return true;
        return false;
    }
}
