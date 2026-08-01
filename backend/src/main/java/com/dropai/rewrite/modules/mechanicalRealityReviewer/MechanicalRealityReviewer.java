package com.dropai.rewrite.modules.mechanicalRealityReviewer;

import com.dropai.rewrite.modules.model.DesignProject;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
public class MechanicalRealityReviewer {
    public DesignProject review(DesignProject project) {
        List<String> errors = new ArrayList<>();
        if (project.getComponents().size() < 5) errors.add("component count is too low for an engineering assembly");
        if (project.getAssemblyConstraints().size() < Math.min(5, project.getComponents().size())) {
            errors.add("assembly constraints are incomplete");
        }
        if (project.getBom().isEmpty()) errors.add("BOM is empty");
        if (project.getDrawingPlan() == null
                || project.getDrawingPlan().getMainView().getVisibleParts().isEmpty()
                || project.getDrawingPlan().getTopView().getVisibleParts().isEmpty()
                || project.getDrawingPlan().getSideView().getVisibleParts().isEmpty()) {
            errors.add("three-view drawing plan is incomplete");
        }
        Set<String> partNames = new HashSet<>();
        for (DesignProject.Component component : project.getComponents()) {
            if (blank(component.getName())) errors.add("component has no name");
            if (blank(component.getFunction())) errors.add("component has no function: " + component.getName());
            if (blank(component.getMaterial())) errors.add("component has no material: " + component.getName());
            if (component.getLength() <= 0 || component.getWidth() <= 0 || component.getHeight() <= 0) {
                errors.add("component has invalid dimensions: " + component.getName());
            }
            partNames.add(component.getName());
        }
        for (DesignProject.BomItem item : project.getBom()) {
            if (!partNames.contains(item.getName())) errors.add("BOM item has no matching component: " + item.getName());
        }
        project.getVerificationItems().removeIf(item -> item != null
                && (item.startsWith("MechanicalRealityReviewer") || item.startsWith("FAILED_REVIEW")));
        if (errors.isEmpty()) {
            project.getVerificationItems().add("MechanicalRealityReviewer: PASSED");
        } else {
            project.getVerificationItems().add("FAILED_REVIEW: " + String.join("; ", errors));
        }
        return project;
    }

    private boolean blank(String value) {
        return value == null || value.isBlank();
    }
}
