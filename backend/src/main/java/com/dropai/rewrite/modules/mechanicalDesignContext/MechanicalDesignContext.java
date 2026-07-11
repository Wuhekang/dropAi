package com.dropai.rewrite.modules.mechanicalDesignContext;

import com.dropai.rewrite.modules.model.DesignProject;

import java.util.LinkedHashMap;
import java.util.Map;

public class MechanicalDesignContext {
    private final Map<String, Object> data = new LinkedHashMap<>();

    public static MechanicalDesignContext from(DesignProject project) {
        DesignProject safe = project == null ? new DesignProject() : project;
        MechanicalDesignContext context = new MechanicalDesignContext();
        context.data.put("projectInfo", Map.of(
                "projectTitle", safe.getProjectTitle(),
                "equipmentName", safe.getEquipmentName(),
                "equipmentType", safe.getEquipmentType(),
                "designType", safe.getDesignType(),
                "designDepth", safe.getDesignDepth()
        ));
        context.data.put("mechanicalPlan", safe.getMechanicalDesignPlan());
        context.data.put("structureTree", safe.getStructureTree());
        context.data.put("components", safe.getComponents());
        context.data.put("standardParts", safe.getStandardParts());
        context.data.put("nonStandardParts", safe.getResolvedParts().stream()
                .filter(part -> !"standard".equalsIgnoreCase(part.getPartType()))
                .toList());
        context.data.put("resolvedParts", safe.getResolvedParts());
        context.data.put("assemblyTree", safe.getAssemblyTree());
        context.data.put("assemblyModel", safe.getAssemblyModel());
        context.data.put("constraints", safe.getAssemblyConstraints());
        context.data.put("bom", safe.getBom());
        context.data.put("calculations", safe.getCalculations());
        context.data.put("drawingPlan", safe.getDrawingPlan());
        context.data.put("paperContext", Map.of(
                "figures", safe.getDrawingViews(),
                "parameters", safe.allParameters(),
                "qualityNotes", safe.getEnhancementNotes()
        ));
        context.data.put("status", Map.of(
                "hasMechanicalPlan", safe.getMechanicalDesignPlan() != null,
                "hasStructureTree", safe.getStructureTree() != null && !safe.getStructureTree().getChildren().isEmpty(),
                "componentCount", safe.getComponents().size(),
                "constraintCount", safe.getAssemblyConstraints().size(),
                "bomCount", safe.getBom().size(),
                "hasDrawingPlan", safe.getDrawingPlan() != null
        ));
        return context;
    }

    public Map<String, Object> getData() {
        return data;
    }
}
