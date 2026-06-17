package com.dropai.rewrite.modules.structureTreeBuilder;

import com.dropai.rewrite.modules.model.DesignProject;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Service
public class StructureTreeBuilder {
    public DesignProject build(DesignProject project) {
        DesignProject.StructureNode root = new DesignProject.StructureNode("整机", "root", "任务书识别", 1.0);
        List<String> structures = normalize(project);
        for (String group : mechanismGroups(structures, project.getMainFunctions())) {
            DesignProject.StructureNode parent = new DesignProject.StructureNode(group, classify(group), sourceOf(group, structures), 0.86);
            for (String item : structures) {
                if (belongsTo(group, item)) {
                    parent.getChildren().add(new DesignProject.StructureNode(item, classify(item), "任务书结构项：" + item, confidence(item)));
                }
            }
            if (parent.getChildren().isEmpty()) parent.getChildren().add(new DesignProject.StructureNode(group + "组件", classify(group), "由功能要求推导", 0.68));
            root.getChildren().add(parent);
        }
        project.setStructureTree(root);
        project.setMainStructures(flatten(root).stream().filter(name -> !"整机".equals(name)).distinct().toList());
        return project;
    }

    private List<String> normalize(DesignProject project) {
        Set<String> result = new LinkedHashSet<>();
        for (String item : project.getMainStructures()) if (!blank(item)) result.add(clean(item));
        for (String function : project.getMainFunctions()) {
            if (containsAny(function, "驱动", "传动", "动力")) result.add("驱动机构");
            if (containsAny(function, "检测", "传感")) result.add("检测机构");
            if (containsAny(function, "清扫", "刷")) result.add("清扫机构");
            if (containsAny(function, "吸附", "固定", "安装")) result.add("吸附/安装机构");
            if (containsAny(function, "支撑", "承载", "机架")) result.add("承载机架");
        }
        if (result.isEmpty()) {
            result.addAll(List.of("主体机构", "驱动机构", "支撑机构", "连接机构", "检修维护机构"));
        }
        return new ArrayList<>(result);
    }

    private List<String> mechanismGroups(List<String> structures, List<String> functions) {
        Set<String> groups = new LinkedHashSet<>();
        for (String item : structures) {
            if (containsAny(item, "驱动", "电机", "减速", "传动", "轮", "轴", "链", "带")) groups.add("驱动传动机构");
            else if (containsAny(item, "检测", "传感", "测量", "探头")) groups.add("检测机构");
            else if (containsAny(item, "清扫", "刷", "除尘", "清理")) groups.add("清扫/处理机构");
            else if (containsAny(item, "吸附", "磁", "固定", "安装", "法兰", "螺栓")) groups.add("安装吸附机构");
            else if (containsAny(item, "机架", "壳", "箱", "底座", "支撑", "外壳")) groups.add("承载防护机构");
            else if (containsAny(item, "检修", "维护", "快拆", "观察")) groups.add("检修维护机构");
            else groups.add("功能执行机构");
        }
        if (groups.isEmpty()) groups.addAll(List.of("承载防护机构", "驱动传动机构", "功能执行机构", "安装连接机构"));
        return new ArrayList<>(groups);
    }

    private boolean belongsTo(String group, String item) {
        return switch (group) {
            case "驱动传动机构" -> containsAny(item, "驱动", "电机", "减速", "传动", "轮", "轴", "链", "带");
            case "检测机构" -> containsAny(item, "检测", "传感", "测量", "探头");
            case "清扫/处理机构" -> containsAny(item, "清扫", "刷", "除尘", "清理", "处理");
            case "安装吸附机构" -> containsAny(item, "吸附", "磁", "固定", "安装", "法兰", "螺栓");
            case "承载防护机构" -> containsAny(item, "机架", "壳", "箱", "底座", "支撑", "外壳");
            case "检修维护机构" -> containsAny(item, "检修", "维护", "快拆", "观察");
            default -> true;
        };
    }

    private String classify(String value) {
        if (containsAny(value, "电机", "减速", "驱动", "传动")) return "drive";
        if (containsAny(value, "检测", "传感")) return "sensor";
        if (containsAny(value, "机架", "底座", "支撑", "壳")) return "structure";
        if (containsAny(value, "螺栓", "法兰", "快拆", "连接")) return "connection";
        if (containsAny(value, "吸附", "磁")) return "mount";
        return "mechanism";
    }

    private String sourceOf(String group, List<String> structures) {
        return structures.stream().filter(item -> belongsTo(group, item)).findFirst().map(item -> "任务书原文/识别结构：" + item).orElse("根据任务书功能要求推导");
    }

    private double confidence(String item) { return item.endsWith("机构") || item.endsWith("组件") ? 0.76 : 0.9; }

    public List<String> flatten(DesignProject.StructureNode node) {
        List<String> result = new ArrayList<>();
        if (node == null) return result;
        result.add(node.getName());
        for (DesignProject.StructureNode child : node.getChildren()) result.addAll(flatten(child));
        return result;
    }

    private String clean(String value) { return value == null ? "" : value.trim(); }
    private boolean blank(String value) { return value == null || value.isBlank(); }
    private boolean containsAny(String value, String... words) {
        if (value == null) return false;
        for (String word : words) if (value.contains(word)) return true;
        return false;
    }
}
