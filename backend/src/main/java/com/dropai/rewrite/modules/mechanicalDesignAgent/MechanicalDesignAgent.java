package com.dropai.rewrite.modules.mechanicalDesignAgent;

import com.dropai.rewrite.modules.model.DesignProject;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class MechanicalDesignAgent {
    private static final int MIN_PARTS = 20;
    private static final int MAX_PARTS = 40;

    public DesignProject design(DesignProject project) {
        String signature = text(project.getProjectTitle(), project.getEquipmentName(), project.getDesignType(),
                String.join(" ", project.getMainFunctions()), String.join(" ", project.getMainStructures()));
        DesignProject.StructureNode sourceRoot = project.getStructureTree();
        List<DesignProject.StructureNode> systems = sourceRoot == null ? List.of() : sourceRoot.getChildren();
        if (systems.isEmpty()) return project;

        DesignProject.StructureNode root = new DesignProject.StructureNode("整机", "root",
                "MechanicalDesignAgent：根据任务书、开题报告和核心结构树推导完整机械设计方案", 1.0);
        root.setRequired(true);

        int totalParts = 0;
        for (DesignProject.StructureNode system : systems) {
            DesignProject.StructureNode detailed = systemNode(system, signature);
            if (detailed.getChildren().isEmpty()) {
                detailed.setChildren(defaultParts(system.getName(), system.getSource()));
            }
            totalParts += detailed.getChildren().size();
            root.getChildren().add(detailed);
        }

        totalParts = expandUntilEnough(root, totalParts, systems);
        trimToMax(root, totalParts);
        project.setStructureTree(root);
        List<String> mainStructures = new ArrayList<>(flattenLeaves(root).stream().distinct().toList());
        if (contains(signature, "沉降", "除尘", "灰斗", "进气")) {
            for (String required : List.of("进气管", "灰斗", "检修门", "加强筋")) {
                if (!mainStructures.contains(required)) mainStructures.add(required);
            }
        }
        project.setMainStructures(mainStructures);
        if (blank(project.getWorkingPrinciple())) project.setWorkingPrinciple(workingPrinciple(signature));
        project.getEnhancementNotes().removeIf(note -> note != null && note.contains("MechanicalDesignAgent"));
        project.getEnhancementNotes().add("MechanicalDesignAgent：已完成设备类型、工作原理、机构、零件和装配对象推导，结构树叶子零件目标为20~40个。");
        return project;
    }

    private DesignProject.StructureNode systemNode(DesignProject.StructureNode input, String signature) {
        String name = clean(input.getName(), "功能机构");
        String family = family(name, signature);
        DesignProject.StructureNode node = new DesignProject.StructureNode(systemName(name, family), family,
                clean(input.getSource(), "任务书识别结构：" + name), Math.max(input.getConfidence(), 0.72));
        node.setRequired(input.isRequired());
        node.setChildren(partsFor(family, name, node.getSource()));
        return node;
    }

    private List<DesignProject.StructureNode> partsFor(String family, String rawName, String source) {
        return switch (family) {
            case "track" -> nodes(source, Map.ofEntries(
                    Map.entry("左侧履带总成", "non_standard"),
                    Map.entry("右侧履带总成", "non_standard"),
                    Map.entry("驱动轮", "standard"),
                    Map.entry("从动轮", "standard"),
                    Map.entry("支重轮", "standard"),
                    Map.entry("履带板", "non_standard"),
                    Map.entry("张紧座", "non_standard"),
                    Map.entry("轮轴", "standard"),
                    Map.entry("轴承", "standard"),
                    Map.entry("端盖", "non_standard"),
                    Map.entry("连接螺栓", "standard")));
            case "drive" -> nodes(source, Map.ofEntries(
                    Map.entry("驱动电机", "standard"),
                    Map.entry("减速器", "standard"),
                    Map.entry("联轴器", "standard"),
                    Map.entry("传动轴", "standard"),
                    Map.entry("电机安装板", "non_standard"),
                    Map.entry("减速器安装座", "non_standard"),
                    Map.entry("轴承座", "non_standard"),
                    Map.entry("键", "standard"),
                    Map.entry("紧固螺栓", "standard")));
            case "frame" -> nodes(source, Map.ofEntries(
                    Map.entry("机架主板", "non_standard"),
                    Map.entry("左侧板", "non_standard"),
                    Map.entry("右侧板", "non_standard"),
                    Map.entry("横梁", "non_standard"),
                    Map.entry("加强筋", "non_standard"),
                    Map.entry("安装孔板", "non_standard"),
                    Map.entry("底板", "non_standard"),
                    Map.entry("连接板", "non_standard")));
            case "sensor" -> nodes(source, Map.ofEntries(
                    Map.entry("检测传感器安装架", "non_standard"),
                    Map.entry("直线导轨", "standard"),
                    Map.entry("滑块", "standard"),
                    Map.entry("调节槽板", "non_standard"),
                    Map.entry("传感器安装板", "non_standard"),
                    Map.entry("快拆压板", "non_standard"),
                    Map.entry("限位块", "non_standard")));
            case "cleaning" -> nodes(source, Map.ofEntries(
                    Map.entry("圆盘清扫刷", "non_standard"),
                    Map.entry("刷盘连接轴", "standard"),
                    Map.entry("刷毛阵列", "non_standard"),
                    Map.entry("清扫电机", "standard"),
                    Map.entry("清扫电机座", "non_standard"),
                    Map.entry("防护罩", "non_standard")));
            case "adsorption" -> nodes(source, Map.ofEntries(
                    Map.entry("磁吸附安装板", "non_standard"),
                    Map.entry("永磁体块", "non_standard"),
                    Map.entry("磁体槽", "non_standard"),
                    Map.entry("防护盖板", "non_standard"),
                    Map.entry("调节孔板", "non_standard"),
                    Map.entry("固定螺栓", "standard")));
            case "shell" -> nodes(source, Map.ofEntries(
                    Map.entry("防护外壳", "non_standard"),
                    Map.entry("电池舱", "non_standard"),
                    Map.entry("控制模块安装板", "non_standard"),
                    Map.entry("检修盖板", "non_standard"),
                    Map.entry("密封条", "non_standard"),
                    Map.entry("散热孔板", "non_standard")));
            case "chamber" -> nodes(source, Map.ofEntries(
                    Map.entry("侧板", "non_standard"),
                    Map.entry("顶板", "non_standard"),
                    Map.entry("底板", "non_standard"),
                    Map.entry("加强筋", "non_standard"),
                    Map.entry("进气管", "non_standard"),
                    Map.entry("进气法兰", "standard"),
                    Map.entry("出气管", "non_standard"),
                    Map.entry("扩散段", "non_standard"),
                    Map.entry("导流板", "non_standard"),
                    Map.entry("观察窗", "non_standard"),
                    Map.entry("灰斗", "non_standard"),
                    Map.entry("卸灰口", "non_standard"),
                    Map.entry("密封装置", "non_standard"),
                    Map.entry("支撑腿", "non_standard"),
                    Map.entry("连接板", "non_standard"),
                    Map.entry("检修门", "non_standard")));
            default -> defaultParts(rawName, source);
        };
    }

    private List<DesignProject.StructureNode> defaultParts(String rawName, String source) {
        return nodes(source, Map.ofEntries(
                Map.entry(rawName + "主体板", "non_standard"),
                Map.entry(rawName + "安装板", "non_standard"),
                Map.entry(rawName + "支架", "non_standard"),
                Map.entry(rawName + "加强筋", "non_standard"),
                Map.entry(rawName + "连接螺栓", "standard"),
                Map.entry(rawName + "定位销", "standard")));
    }

    private int expandUntilEnough(DesignProject.StructureNode root, int totalParts, List<DesignProject.StructureNode> systems) {
        int cursor = 0;
        while (totalParts < MIN_PARTS && !root.getChildren().isEmpty()) {
            DesignProject.StructureNode system = root.getChildren().get(cursor % root.getChildren().size());
            String base = clean(system.getName(), "机构");
            system.getChildren().add(part(base + "校核安装孔板", "non_standard", system.getSource(), 0.58, false));
            totalParts++;
            cursor++;
        }
        return totalParts;
    }

    private void trimToMax(DesignProject.StructureNode root, int totalParts) {
        if (totalParts <= MAX_PARTS) return;
        int overflow = totalParts - MAX_PARTS;
        for (int i = root.getChildren().size() - 1; i >= 0 && overflow > 0; i--) {
            List<DesignProject.StructureNode> children = root.getChildren().get(i).getChildren();
            while (children.size() > 3 && overflow > 0) {
                children.remove(children.size() - 1);
                overflow--;
            }
        }
    }

    private List<String> flattenLeaves(DesignProject.StructureNode root) {
        List<String> result = new ArrayList<>();
        for (DesignProject.StructureNode system : root.getChildren()) {
            if (system.getChildren().isEmpty()) result.add(system.getName());
            else system.getChildren().forEach(part -> result.add(part.getName()));
        }
        return result;
    }

    private List<DesignProject.StructureNode> nodes(String source, Map<String, String> parts) {
        List<DesignProject.StructureNode> result = new ArrayList<>();
        for (Map.Entry<String, String> entry : new LinkedHashMap<>(parts).entrySet()) {
            result.add(part(entry.getKey(), entry.getValue(), source, "standard".equals(entry.getValue()) ? 0.86 : 0.76, true));
        }
        return result;
    }

    private DesignProject.StructureNode part(String name, String type, String source, double confidence, boolean required) {
        DesignProject.StructureNode node = new DesignProject.StructureNode(name, type,
                "MechanicalDesignAgent推导零件；依据：" + clean(source, "任务书和机械设计常规"), confidence);
        node.setRequired(required);
        return node;
    }

    private String family(String name, String signature) {
        String node = name == null ? "" : name.toLowerCase();
        if (contains(node, "履带", "爬壁", "行走", "驱动轮", "从动轮", "支重轮", "track", "crawler")) return "track";
        if (contains(node, "电机", "驱动", "减速", "传动", "motor", "drive")) return "drive";
        if (contains(node, "机架", "底座", "支撑", "框架", "frame")) return "frame";
        if (contains(node, "检测", "传感", "导轨", "滑轨", "sensor", "rail")) return "sensor";
        if (contains(node, "清扫", "刷", "clean", "brush")) return "cleaning";
        if (contains(node, "磁", "吸附", "magnet")) return "adsorption";
        if (contains(node, "外壳", "防护", "电池", "控制", "shell", "cover")) return "shell";
        if (contains(node, "沉降", "除尘", "箱体", "壳体", "灰斗", "进气", "出气", "法兰", "检修", "观察", "hopper", "chamber")) return "chamber";
        String text = signature == null ? "" : signature.toLowerCase();
        if (contains(text, "沉降", "除尘", "灰斗", "进气", "出气")) return "chamber";
        return "generic";
    }

    private String systemName(String name, String family) {
        return switch (family) {
            case "track" -> "履带行走系统";
            case "drive" -> "驱动传动系统";
            case "frame" -> "机架承载系统";
            case "sensor" -> "检测调节系统";
            case "cleaning" -> "清扫执行系统";
            case "adsorption" -> "磁吸附系统";
            case "shell" -> "防护与电控系统";
            case "chamber" -> name.contains("系统") ? name : name + "系统";
            default -> name.contains("系统") ? name : name + "系统";
        };
    }

    private String workingPrinciple(String signature) {
        if (contains(signature, "履带", "爬壁", "磁", "清扫", "检测")) {
            return "整机以机架为基准承载件，左右履带机构实现壁面爬行，磁吸附模块提供附着力，驱动电机经减速器带动轮系运动，前端清扫机构处理检测区域，检测支架用于安装和调节传感器。";
        }
        if (contains(signature, "沉降", "除尘", "灰斗", "进气")) {
            return "含尘气流经进气扩散段进入箱体，导流板降低局部紊流，颗粒在沉降区依靠重力分离并进入灰斗，净化气体由出口排出，检修门和观察窗用于维护检查。";
        }
        return "整机以承载结构为基础，通过驱动、传动、执行、支撑、连接和检修机构协同完成任务书规定的机械功能。";
    }

    private String text(String... values) {
        return String.join(" ", values == null ? List.of() : java.util.Arrays.stream(values).filter(v -> v != null).toList());
    }

    private boolean contains(String value, String... words) {
        String v = value == null ? "" : value.toLowerCase();
        for (String word : words) if (word != null && !word.isBlank() && v.contains(word.toLowerCase())) return true;
        return false;
    }

    private boolean blank(String value) { return value == null || value.isBlank(); }
    private String clean(String value, String fallback) { return blank(value) ? fallback : value; }
}
