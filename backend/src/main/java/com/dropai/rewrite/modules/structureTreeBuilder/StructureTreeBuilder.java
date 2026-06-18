package com.dropai.rewrite.modules.structureTreeBuilder;

import com.dropai.rewrite.modules.model.DesignProject;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class StructureTreeBuilder {
    private final StructureTreeNormalizer normalizer;

    public StructureTreeBuilder() {
        this(new StructureTreeNormalizer());
    }

    public StructureTreeBuilder(StructureTreeNormalizer normalizer) {
        this.normalizer = normalizer;
    }

    public DesignProject build(DesignProject project) {
        List<DesignProject.StructureNode> nodes = normalizer.normalize(project);
        if (nodes.size() < 3) {
            throw new IllegalStateException("结构树生成失败：缺少可用结构信息，请补充主要机构和关键零部件。");
        }
        DesignProject.StructureNode root = new DesignProject.StructureNode("整机", "root", "当前任务书识别结果", 1.0);
        root.setRequired(true);
        root.setChildren(nodes);
        project.setStructureTree(root);
        project.setMainStructures(nodes.stream().map(DesignProject.StructureNode::getName).distinct().toList());
        project.getEnhancementNotes().add("StructureTreeNormalizer已合并重复结构，保留" + nodes.size() + "个核心节点。");
        return project;
    }

    public List<String> flatten(DesignProject.StructureNode node) {
        List<String> result = new ArrayList<>();
        if (node == null) return result;
        result.add(node.getName());
        for (DesignProject.StructureNode child : node.getChildren()) result.addAll(flatten(child));
        return result;
    }
}
