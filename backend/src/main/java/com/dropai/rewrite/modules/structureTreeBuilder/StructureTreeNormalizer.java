package com.dropai.rewrite.modules.structureTreeBuilder;

import com.dropai.rewrite.modules.model.DesignProject;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class StructureTreeNormalizer {
    private static final int MIN_NODES = 8;
    private static final int MAX_NODES = 15;

    public List<DesignProject.StructureNode> normalize(DesignProject project) {
        Map<String, DesignProject.StructureNode> nodes = new LinkedHashMap<>();
        for (String raw : project.getMainStructures()) {
            String name = canonical(raw);
            if (name.isBlank() || isGenericEmptyNode(name)) continue;
            nodes.computeIfAbsent(name, item -> node(item, classify(item), "任务书明确识别结构：" + raw, 0.90, true));
        }

        if (nodes.size() < MIN_NODES) {
            for (String item : inferredFromFunctions(project)) {
                nodes.computeIfAbsent(item, key -> node(key, classify(key), "根据任务书功能要求推导：" + String.join("、", project.getMainFunctions()), 0.66, false));
                if (nodes.size() >= MIN_NODES) break;
            }
        }

        return prioritize(new ArrayList<>(nodes.values())).stream().limit(MAX_NODES).toList();
    }

    private List<DesignProject.StructureNode> prioritize(List<DesignProject.StructureNode> input) {
        List<String> priority = List.of("机架", "履带", "驱动轮", "从动轮", "支重轮", "磁吸附", "清扫", "检测", "滑轨", "电机", "减速器", "外壳", "电池", "快拆", "螺栓");
        return input.stream().sorted((a, b) -> Integer.compare(score(b.getName(), priority), score(a.getName(), priority))).toList();
    }

    private int score(String name, List<String> priority) {
        for (int i = 0; i < priority.size(); i++) if (name.contains(priority.get(i))) return 100 - i;
        return 1;
    }

    private List<String> inferredFromFunctions(DesignProject project) {
        String text = project.getEquipmentName() + " " + project.getDesignType() + " " + String.join(" ", project.getMainFunctions());
        List<String> result = new ArrayList<>();
        if (containsAny(text, "爬壁", "履带", "磁吸附", "油罐", "清扫", "检测")) {
            result.addAll(List.of("机架", "左右履带组件", "驱动电机", "减速器", "磁吸附模块", "检测传感器安装架", "圆盘清扫刷", "防护外壳", "电池/控制模块安装舱"));
        } else {
            result.addAll(List.of("主体结构", "支撑结构", "驱动机构", "传动机构", "连接结构", "安装结构", "检修结构", "防护结构"));
        }
        return result;
    }

    private DesignProject.StructureNode node(String name, String type, String source, double confidence, boolean required) {
        DesignProject.StructureNode node = new DesignProject.StructureNode(name, type, source, confidence);
        node.setRequired(required);
        return node;
    }

    private String canonical(String value) {
        String text = value == null ? "" : value.trim();
        text = text.replace("行走机构", "行走组件")
                .replace("履带机构", "履带组件")
                .replace("检测机构", "检测组件")
                .replace("清扫机构", "清扫组件")
                .replace("吸附机构", "吸附组件");
        return text;
    }

    private boolean isGenericEmptyNode(String name) {
        return List.of("整机", "主体机构", "功能执行机构", "承载机架", "安装机构", "连接机构", "维护机构").contains(name);
    }

    private String classify(String value) {
        if (containsAny(value, "电机", "减速", "驱动", "传动", "轮", "履带")) return "drive";
        if (containsAny(value, "检测", "传感", "滑轨")) return "sensor";
        if (containsAny(value, "机架", "外壳", "支撑", "底座", "防护")) return "structure";
        if (containsAny(value, "螺栓", "法兰", "快拆", "连接")) return "connection";
        if (containsAny(value, "吸附", "磁", "安装")) return "mount";
        if (containsAny(value, "清扫", "刷")) return "working";
        return "mechanism";
    }

    private boolean containsAny(String value, String... words) {
        if (value == null) return false;
        for (String word : words) if (word != null && !word.isBlank() && value.contains(word)) return true;
        return false;
    }
}
