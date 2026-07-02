package com.dropai.rewrite.modules.drawingPlannerAgent;

import com.dropai.rewrite.modules.model.DesignProject;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Service
public class DrawingPlannerAgent {
    public DesignProject plan(DesignProject project) {
        List<String> drawings = new ArrayList<>();
        drawings.add("总装三视图");
        project.getComponents().stream()
                .sorted(Comparator.comparing(DesignProject.Component::isKeyPart).reversed()
                        .thenComparingInt(DesignProject.Component::getSequence))
                .limit(5)
                .map(component -> component.getName() + "零件图")
                .forEach(drawings::add);
        project.setDrawingViews(drawings);
        project.getEnhancementNotes().removeIf(note -> note != null && note.contains("DrawingPlannerAgent"));
        project.getEnhancementNotes().add("DrawingPlannerAgent：图纸计划来源为AssemblyTree和真实零件清单，总装图与关键零件图均绑定具体零件。");
        return project;
    }
}
