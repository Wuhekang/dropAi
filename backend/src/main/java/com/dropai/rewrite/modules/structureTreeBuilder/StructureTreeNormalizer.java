package com.dropai.rewrite.modules.structureTreeBuilder;

import com.dropai.rewrite.modules.mechanicalDesignPlanner.MechanicalDesignPlan;
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
        addPlanSubsystems(project, nodes);
        addTaskStructures(project, nodes);
        addPlanComponents(project, nodes);
        addFunctionFallback(project, nodes);
        return prioritize(new ArrayList<>(nodes.values()), project).stream().limit(MAX_NODES).toList();
    }

    private void addPlanSubsystems(DesignProject project, Map<String, DesignProject.StructureNode> nodes) {
        MechanicalDesignPlan plan = project.getMechanicalDesignPlan();
        if (plan == null || plan.getSubsystems().isEmpty()) return;
        for (MechanicalDesignPlan.SubsystemPlan subsystem : plan.getSubsystems()) {
            String name = canonical(subsystem.getName());
            if (skip(name)) continue;
            DesignProject.StructureNode node = node(name, classify(name), "MechanicalDesignPlan subsystem: " + subsystem.getSource(),
                    clamp(subsystem.getConfidence(), 0.72, 0.96), subsystem.isRequired());
            nodes.putIfAbsent(name, node);
        }
    }

    private void addTaskStructures(DesignProject project, Map<String, DesignProject.StructureNode> nodes) {
        for (String raw : project.getMainStructures()) {
            String name = canonical(raw);
            if (skip(name)) continue;
            nodes.computeIfAbsent(name, item -> node(item, classify(item), "task structure: " + raw, 0.86, true));
        }
    }

    private void addPlanComponents(DesignProject project, Map<String, DesignProject.StructureNode> nodes) {
        MechanicalDesignPlan plan = project.getMechanicalDesignPlan();
        if (plan == null || plan.getSubsystems().isEmpty()) return;
        for (MechanicalDesignPlan.SubsystemPlan subsystem : plan.getSubsystems()) {
            for (String component : subsystem.getComponents()) {
                if (nodes.size() >= MAX_NODES) return;
                String name = canonical(component);
                if (skip(name)) continue;
                nodes.computeIfAbsent(name, item -> node(item, classify(item),
                        "MechanicalDesignPlan component of " + subsystem.getName(), 0.70, true));
            }
            if (nodes.size() >= MIN_NODES) return;
        }
    }

    private void addFunctionFallback(DesignProject project, Map<String, DesignProject.StructureNode> nodes) {
        if (nodes.size() >= MIN_NODES) return;
        for (String item : inferredFromPlanOrFunctions(project)) {
            if (nodes.size() >= MIN_NODES) break;
            String name = canonical(item);
            if (skip(name)) continue;
            nodes.computeIfAbsent(name, key -> node(key, classify(key), "fallback inferred from mechanical design intent", 0.62, false));
        }
    }

    private List<DesignProject.StructureNode> prioritize(List<DesignProject.StructureNode> input, DesignProject project) {
        List<String> priority = priorityFor(project);
        return input.stream().sorted((a, b) -> {
            int score = Integer.compare(score(b.getName(), priority), score(a.getName(), priority));
            if (score != 0) return score;
            return Double.compare(b.getConfidence(), a.getConfidence());
        }).toList();
    }

    private List<String> priorityFor(DesignProject project) {
        String mechanism = project.getMechanicalDesignPlan() == null ? "" : project.getMechanicalDesignPlan().getMechanismType();
        String text = (mechanism + " " + project.getProjectTitle() + " " + project.getEquipmentName()).toLowerCase();
        if (containsAny(text, "爬壁", "吸附", "履带", "wall", "climbing")) {
            return List.of("移动", "履带", "吸附", "驱动", "检测", "控制", "电源", "防护", "主动轮", "从动轮", "电机", "减速");
        }
        if (containsAny(text, "沉降", "除尘", "灰斗", "chamber")) {
            return List.of("箱体", "进出口", "导流", "沉降", "排灰", "灰斗", "支撑", "检修", "法兰", "加强");
        }
        if (containsAny(text, "输送", "传送", "带式", "conveyor", "belt")) {
            return List.of("机架", "输送带", "驱动", "主动滚筒", "改向滚筒", "托辊", "张紧", "防护", "清扫", "电机", "减速");
        }
        return List.of("机架", "支撑", "驱动", "传动", "执行", "连接", "安装", "防护", "检修", "电机", "减速");
    }

    private int score(String name, List<String> priority) {
        for (int i = 0; i < priority.size(); i++) {
            if (name.contains(priority.get(i))) return 100 - i;
        }
        return 1;
    }

    private List<String> inferredFromPlanOrFunctions(DesignProject project) {
        String text = project.getEquipmentName() + " " + project.getDesignType() + " "
                + project.getProjectTitle() + " " + String.join(" ", project.getMainFunctions());
        if (containsAny(text, "爬壁", "履带", "吸附", "油罐")) {
            return List.of("移动机构", "履带组件", "吸附机构", "驱动机构", "检测机构", "控制与电源机构", "防护外壳", "连接支架");
        }
        if (containsAny(text, "沉降", "除尘", "灰斗")) {
            return List.of("箱体组件", "进出口组件", "导流组件", "沉降与排灰组件", "灰斗", "支撑组件", "检修组件", "加强筋");
        }
        if (containsAny(text, "输送", "传送", "皮带", "带式")) {
            return List.of("机架组件", "输送带组件", "驱动组件", "主动滚筒", "托辊组件", "张紧组件", "防护组件", "清扫组件");
        }
        return List.of("主体结构", "支撑结构", "驱动机构", "传动机构", "执行机构", "连接结构", "安装结构", "防护结构");
    }

    private DesignProject.StructureNode node(String name, String type, String source, double confidence, boolean required) {
        DesignProject.StructureNode node = new DesignProject.StructureNode(name, type, source, confidence);
        node.setRequired(required);
        return node;
    }

    private String canonical(String value) {
        String text = value == null ? "" : value.trim();
        text = text.replace("行走机构", "移动机构")
                .replace("履带机构", "履带组件")
                .replace("检测机架", "检测机构")
                .replace("清扫机构", "清扫组件")
                .replace("吸附模块", "吸附机构")
                .replace("磁吸附模块", "吸附机构")
                .replace("沉降与排灰组件", "沉降排灰组件")
                .replace("控制与电源机构", "控制电源机构");
        return text;
    }

    private boolean skip(String name) {
        return name.isBlank() || List.of("整机", "主体机构", "功能执行机构", "承载机架", "安装机构",
                "连接机构", "维护机构", "MechanicalDesignAgent", "non_standard", "frame").contains(name);
    }

    private String classify(String value) {
        if (containsAny(value, "电机", "减速", "驱动", "传动", "滚筒", "轮", "履带", "输送带")) return "drive";
        if (containsAny(value, "检测", "传感", "云台", "摄像")) return "sensor";
        if (containsAny(value, "机架", "箱体", "外壳", "支撑", "底座", "防护", "灰斗")) return "structure";
        if (containsAny(value, "螺栓", "法兰", "铰链", "连接", "支架")) return "connection";
        if (containsAny(value, "吸附", "磁", "安装", "张紧")) return "mount";
        if (containsAny(value, "清扫", "导流", "排灰", "检修")) return "working";
        return "mechanism";
    }

    private boolean containsAny(String value, String... words) {
        if (value == null) return false;
        for (String word : words) {
            if (word != null && !word.isBlank() && value.contains(word)) return true;
        }
        return false;
    }

    private double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }
}
