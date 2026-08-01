package com.dropai.rewrite.modules.mechanicalWorkflowMemory;

import com.dropai.rewrite.modules.model.DesignProject;

import java.util.List;

/** Applies proven workflow rules without copying dimensions from earlier projects. */
public class MechanicalWorkflowMemory {
    public DesignProject apply(DesignProject project) {
        String signature = String.join(" ", List.of(
                safe(project.getProjectTitle()), safe(project.getEquipmentName()),
                String.join(" ", project.getMainFunctions()), String.join(" ", project.getMainStructures())));
        String workflow = classify(signature);
        project.getEnhancementNotes().removeIf(note -> note != null && note.startsWith("WorkflowMemory:"));
        project.getEnhancementNotes().add("WorkflowMemory: " + workflow
                + "; reused architecture/assembly/CAD rules only; dimensions remain project-specific engineering decisions.");
        return project;
    }

    private String classify(String value) {
        if (contains(value, "爬壁", "吸附", "油罐")) return "wall-climbing-robot";
        if (contains(value, "AGV", "搬运小车", "移动底盘")) return "agv";
        if (contains(value, "机械臂", "机器人手臂")) return "robot-arm";
        if (contains(value, "夹具", "夹持")) return "fixture";
        return "general-machine";
    }

    private boolean contains(String value, String... words) {
        for (String word : words) if (value.contains(word)) return true;
        return false;
    }

    private String safe(String value) { return value == null ? "" : value; }
}
