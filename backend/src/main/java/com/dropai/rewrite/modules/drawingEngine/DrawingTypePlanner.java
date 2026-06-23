package com.dropai.rewrite.modules.drawingEngine;

import com.dropai.rewrite.modules.model.DesignProject;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

class DrawingTypePlanner {
    List<DrawingPlanItem> plan(DesignProject project) {
        List<NodeText> nodes = flatten(project.getStructureTree());
        String all = normalize(String.join(" ", nodes.stream().map(NodeText::text).toList()));
        List<DrawingPlanItem> result = new ArrayList<>();
        add(result, "overall_structure", "总体结构图", "overall_structure", source(nodes, ""), "root structure overview");
        detectCrawler(result, nodes, all);
        detectConveyor(result, nodes, all);
        detectCleaning(result, nodes, all);
        detectFrame(result, nodes, all);
        detectDrive(result, nodes, all);
        detectSettlingChamber(result, nodes, all);
        detectManipulator(result, nodes, all);
        return dedupe(result);
    }

    private void detectCrawler(List<DrawingPlanItem> result, List<NodeText> nodes, String all) {
        if (hasAny(all, "履带", "track") && hasAny(all, "驱动轮", "drive wheel") && hasAny(all, "从动轮", "idler")
                && hasAny(all, "支重轮", "support roller")) {
            add(result, "track_mechanism", "履带行走机构图", "crawler_track", source(nodes, "履带", "track"),
                    "StructureTree contains crawler, drive wheel, idler wheel and support rollers");
        }
    }

    private void detectCleaning(List<DrawingPlanItem> result, List<NodeText> nodes, String all) {
        if (hasAny(all, "圆盘刷", "刷盘", "清扫", "brush") && hasAny(all, "清扫电机", "电机", "motor")) {
            add(result, "cleaning_mechanism", "清扫机构图", "cleaning_brush", source(nodes, "清扫", "刷", "brush"),
                    "StructureTree contains brush disk and cleaning drive parts");
        }
    }

    private void detectFrame(List<DrawingPlanItem> result, List<NodeText> nodes, String all) {
        if (hasAny(all, "机架", "frame")) {
            add(result, "frame_structure", "机架结构图", "frame_structure", source(nodes, "机架", "frame"),
                    "StructureTree contains frame plates, beams, ribs or mounting holes");
        }
    }

    private void detectDrive(List<DrawingPlanItem> result, List<NodeText> nodes, String all) {
        if (hasAny(all, "驱动轴", "电机", "减速器", "联轴器", "键槽", "drive shaft", "motor", "reducer", "coupling", "keyway")) {
            add(result, "drive_mechanism", "驱动机构图", "drive_unit", source(nodes, "驱动", "电机", "drive", "motor"),
                    "StructureTree contains shaft, motor, reducer, coupling or keyway");
        }
    }

    private void detectConveyor(List<DrawingPlanItem> result, List<NodeText> nodes, String all) {
        if (hasAny(all, "输送带", "传送带", "conveyor belt")) {
            add(result, "conveyor_belt", "输送带机构图", "conveyor_belt", source(nodes, "输送带", "conveyor"),
                    "StructureTree contains conveyor belt mechanism");
        }
        if (hasAny(all, "滚筒", "主动滚筒", "从动滚筒", "roller", "drum")) {
            add(result, "roller_mechanism", "滚筒机构图", "roller_mechanism", source(nodes, "滚筒", "roller", "drum"),
                    "StructureTree contains roller or drum mechanism");
        }
    }

    private void detectSettlingChamber(List<DrawingPlanItem> result, List<NodeText> nodes, String all) {
        if (hasAny(all, "壳体", "箱体", "沉降室", "shell", "housing")) {
            add(result, "shell_structure", "壳体结构图", "shell_structure", source(nodes, "壳体", "箱体", "shell"),
                    "StructureTree contains chamber shell or housing");
        }
        if (hasAny(all, "进口", "出口", "进出口", "进气", "出气", "进气管", "出气管", "接口", "法兰", "inlet", "outlet", "flange")) {
            add(result, "inlet_outlet", "进出口接口图", "inlet_outlet", source(nodes, "进口", "出口", "进气", "出气", "接口", "法兰", "inlet", "outlet"),
                    "StructureTree contains inlet/outlet interface");
        }
        if (hasAny(all, "排灰斗", "灰斗", "hopper", "ash")) {
            add(result, "ash_hopper", "排灰斗结构图", "ash_hopper", source(nodes, "灰斗", "hopper"),
                    "StructureTree contains ash hopper");
        }
        if (hasAny(all, "检修门", "检查门", "access door", "maintenance door")) {
            add(result, "access_door", "检修门结构图", "access_door", source(nodes, "检修门", "检查门", "door"),
                    "StructureTree contains access door");
        }
        if (hasAny(all, "支撑框架", "支撑架", "支架", "支腿", "support frame", "support")) {
            add(result, "support_frame", "支撑架结构图", "support_frame", source(nodes, "支撑", "支架", "support"),
                    "StructureTree contains support frame");
        }
    }

    private void detectManipulator(List<DrawingPlanItem> result, List<NodeText> nodes, String all) {
        if (hasAny(all, "底座", "base")) add(result, "base_structure", "底座结构图", "base_structure", source(nodes, "底座", "base"), "StructureTree contains base");
        if (hasAny(all, "大臂", "主臂", "upper arm")) add(result, "upper_arm", "大臂结构图", "upper_arm", source(nodes, "大臂", "主臂", "upper arm"), "StructureTree contains upper arm");
        if (hasAny(all, "小臂", "前臂", "forearm")) add(result, "forearm", "小臂结构图", "forearm", source(nodes, "小臂", "前臂", "forearm"), "StructureTree contains forearm");
        if (hasAny(all, "夹爪", "夹持", "gripper", "claw")) add(result, "gripper", "夹爪结构图", "gripper", source(nodes, "夹爪", "gripper"), "StructureTree contains gripper");
        if (hasAny(all, "关节", "舵机", "伺服", "joint", "servo")) add(result, "joint_drive", "关节驱动结构图", "joint_drive", source(nodes, "关节", "舵机", "伺服", "joint"), "StructureTree contains joint drive");
    }

    private void add(List<DrawingPlanItem> result, String key, String name, String type, String source, String reason) {
        result.add(new DrawingPlanItem(key, name, type, source, reason));
    }

    private List<DrawingPlanItem> dedupe(List<DrawingPlanItem> input) {
        Set<String> keys = new LinkedHashSet<>();
        List<DrawingPlanItem> result = new ArrayList<>();
        for (DrawingPlanItem item : input) {
            if (keys.add(item.key())) result.add(item);
        }
        return result;
    }

    private List<NodeText> flatten(DesignProject.StructureNode root) {
        List<NodeText> result = new ArrayList<>();
        collect(root, "", result);
        return result;
    }

    private void collect(DesignProject.StructureNode node, String parent, List<NodeText> result) {
        if (node == null) return;
        result.add(new NodeText(node.getName(), parent, normalize(node.getName() + " " + node.getType() + " " + node.getSource())));
        for (DesignProject.StructureNode child : node.getChildren()) collect(child, node.getName(), result);
    }

    private String source(List<NodeText> nodes, String... keywords) {
        for (String keyword : keywords) {
            if (keyword == null || keyword.isBlank()) continue;
            String normalized = normalize(keyword);
            for (NodeText node : nodes) {
                if (node.text().contains(normalized)) return node.name();
            }
        }
        return nodes.isEmpty() ? "" : nodes.get(0).name();
    }

    private boolean hasAny(String text, String... keywords) {
        for (String keyword : keywords) {
            if (text.contains(normalize(keyword))) return true;
        }
        return false;
    }

    private String normalize(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT).replaceAll("\\s+", "");
    }

    record DrawingPlanItem(String key, String drawingName, String drawingType, String sourceStructureNode, String reason) {
    }

    private record NodeText(String name, String parent, String text) {
    }
}
