package com.dropai.rewrite.modules.drawingEngine;

import com.dropai.rewrite.modules.model.DesignProject;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Service
public class PartImportanceAnalyzer {
    private final EngineeringSemanticLayer semanticLayer = new EngineeringSemanticLayer();

    public List<DesignProject.Component> selectTopFive(DesignProject project) {
        List<String> structureNames = flatten(project.getStructureTree());
        List<DesignProject.Component> selected = new ArrayList<>();
        for (String structure : structureNames) {
            project.getComponents().stream()
                    .filter(component -> samePart(component, structure))
                    .min(Comparator.comparingInt(this::rank).thenComparingInt(DesignProject.Component::getSequence))
                    .ifPresent(component -> add(selected, component));
            if (selected.size() >= 5) break;
        }
        project.getComponents().stream()
                .sorted(Comparator.comparingInt(this::rank).thenComparingInt(DesignProject.Component::getSequence))
                .forEach(component -> add(selected, component));
        if (selected.size() < 5) {
            throw new IllegalStateException("关键零件图生成失败：StructureTree未能提供5个可出图关键零件。");
        }
        return selected.stream().limit(5).toList();
    }

    private void add(List<DesignProject.Component> selected, DesignProject.Component component) {
        if (component == null || selected.size() >= 5) return;
        if (selected.stream().noneMatch(item -> item.getPartId().equals(component.getPartId()))) selected.add(component);
    }

    private boolean samePart(DesignProject.Component component, String structure) {
        String left = normalize(component.getName() + " " + component.getParentAssembly() + " " + component.getGeometry());
        String right = normalize(structure);
        return !right.isBlank() && (left.contains(right) || right.contains(left)
                || tokens(right).stream().anyMatch(token -> token.length() >= 2 && left.contains(token)));
    }

    private int rank(DesignProject.Component component) {
        String category = semanticLayer.semanticOf(component).category();
        return switch (category) {
            case "frame", "shell" -> 1;
            case "track", "interface", "base" -> 2;
            case "wheel", "hopper", "arm" -> 3;
            case "motor", "reducer", "brush", "door" -> 4;
            case "support", "magnet", "sensor", "rail", "gripper" -> 5;
            case "coupling", "bearing", "rib" -> 6;
            default -> component.isKeyPart() ? 8 : 20;
        };
    }

    private List<String> flatten(DesignProject.StructureNode node) {
        List<String> result = new ArrayList<>();
        collect(node, result, new LinkedHashSet<>());
        return result;
    }

    private void collect(DesignProject.StructureNode node, List<String> result, Set<String> seen) {
        if (node == null) return;
        if (seen.add(node.getName()) && !"整机".equals(node.getName())) result.add(node.getName());
        for (DesignProject.StructureNode child : node.getChildren()) collect(child, result, seen);
    }

    private List<String> tokens(String value) {
        return List.of(value.split("[\\s/、,，\\-]+")).stream().filter(item -> !item.isBlank()).toList();
    }

    private String normalize(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT).replaceAll("\\s+", "");
    }
}
