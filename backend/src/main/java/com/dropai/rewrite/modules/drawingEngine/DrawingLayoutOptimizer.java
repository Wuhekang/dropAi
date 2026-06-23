package com.dropai.rewrite.modules.drawingEngine;

import com.dropai.rewrite.modules.model.DesignProject;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

class DrawingLayoutOptimizer {
    private static final List<String> CORE_ORDER = List.of(
            "frame", "shell", "track", "wheel", "interface", "hopper", "brush", "sensor", "cover", "magnet", "door", "support");

    private final EngineeringSemanticLayer semanticLayer = new EngineeringSemanticLayer();

    Layout optimize(DesignProject project) {
        List<DesignProject.Component> coreParts = coreParts(project);
        return new Layout(
                coreParts,
                List.of(
                        new ViewBox("FRONT", "Front view", 58, 342, 520, 150),
                        new ViewBox("TOP", "Top view", 58, 176, 520, 118),
                        new ViewBox("SIDE", "Side view", 58, 62, 235, 88)
                ),
                new PanelBox("BOM", "Core BOM", 620, 346, 190, 170),
                new PanelBox("PARAMETERS", "Main parameters", 620, 214, 190, 112),
                new PanelBox("REQUIREMENTS", "Technical notes", 620, 112, 190, 82)
        );
    }

    List<DesignProject.Component> coreParts(DesignProject project) {
        Map<String, DesignProject.Component> selected = new LinkedHashMap<>();
        for (DesignProject.Component component : project.getComponents()) {
            String category = semanticLayer.semanticOf(component).category();
            if (isCore(category)) {
                selected.putIfAbsent(component.getPartId(), component);
            }
        }
        if (selected.size() < 5) {
            project.getComponents().stream()
                    .filter(DesignProject.Component::isKeyPart)
                    .sorted(Comparator.comparingInt(DesignProject.Component::getSequence))
                    .limit(8)
                    .forEach(component -> selected.putIfAbsent(component.getPartId(), component));
        }
        return new ArrayList<>(selected.values()).stream()
                .sorted(Comparator.comparingInt(this::coreRank).thenComparingInt(DesignProject.Component::getSequence))
                .limit(8)
                .toList();
    }

    private boolean isCore(String category) {
        return CORE_ORDER.contains(category);
    }

    private int coreRank(DesignProject.Component component) {
        String category = semanticLayer.semanticOf(component).category();
        int index = CORE_ORDER.indexOf(category);
        return index < 0 ? 99 : index;
    }

    record Layout(List<DesignProject.Component> coreParts, List<ViewBox> views,
                  PanelBox bom, PanelBox parameters, PanelBox requirements) {
    }

    record ViewBox(String orientation, String title, double x, double y, double width, double height) {
    }

    record PanelBox(String key, String title, double x, double y, double width, double height) {
    }
}
