package com.dropai.rewrite.modules.modelQualityGate;

import com.dropai.rewrite.modules.model.DesignProject;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class MechanicalQualityReviewer {
    public Review review(DesignProject project) {
        List<String> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        if (project == null) {
            return new Review(false, 0, List.of("project is null"), warnings);
        }
        checkStructure(project, errors, warnings);
        checkDimensions(project, errors, warnings);
        checkMaterials(project, errors, warnings);
        checkCalculations(project, errors, warnings);
        checkCad(project, errors, warnings);
        checkAssembly(project, errors, warnings);
        checkManufacturing(project, errors, warnings);
        int score = Math.max(0, 100 - errors.size() * 14 - warnings.size() * 4);
        return new Review(errors.isEmpty(), score, errors, warnings);
    }

    private void checkStructure(DesignProject project, List<String> errors, List<String> warnings) {
        if (project.getMainStructures().size() < 3) {
            errors.add("structure: fewer than 3 main structures");
        }
        if (project.getComponents().size() < 5 && project.getResolvedParts().size() < 5) {
            errors.add("structure: fewer than 5 parts/components");
        }
    }

    private void checkDimensions(DesignProject project, List<String> errors, List<String> warnings) {
        long dimensioned = project.getResolvedParts().stream()
                .filter(part -> part.getDimensions() != null && !part.getDimensions().isEmpty())
                .count();
        if (!project.getResolvedParts().isEmpty() && dimensioned < Math.min(3, project.getResolvedParts().size())) {
            warnings.add("dimensions: too few resolved parts contain dimensions");
        }
        if (project.getDimensionChains().isEmpty()) {
            warnings.add("dimensions: no dimension chain found");
        }
    }

    private void checkMaterials(DesignProject project, List<String> errors, List<String> warnings) {
        long missing = project.getResolvedParts().stream()
                .filter(part -> blank(part.getMaterial()))
                .count();
        if (missing > 0) {
            errors.add("materials: " + missing + " resolved parts missing material");
        }
        if (project.getMaterials().isEmpty()) {
            warnings.add("materials: project material summary is empty");
        }
    }

    private void checkCalculations(DesignProject project, List<String> errors, List<String> warnings) {
        if (project.getCalculations().isEmpty()) {
            errors.add("calculations: no engineering calculation");
            return;
        }
        boolean hasJudgment = project.getCalculations().stream().anyMatch(item -> !blank(item.getConclusion()));
        if (!hasJudgment) {
            warnings.add("calculations: no explicit judgment text");
        }
    }

    private void checkCad(DesignProject project, List<String> errors, List<String> warnings) {
        long withoutFeatures = project.getResolvedParts().stream()
                .filter(part -> part.getCadFeatures() == null || part.getCadFeatures().isEmpty())
                .count();
        if (!project.getResolvedParts().isEmpty() && withoutFeatures > 0) {
            errors.add("cad: " + withoutFeatures + " resolved parts missing CAD feature tree");
        }
    }

    private void checkAssembly(DesignProject project, List<String> errors, List<String> warnings) {
        if (project.getAssemblyTree() == null || project.getAssemblyTree().getChildren().isEmpty()) {
            errors.add("assembly: assembly tree is empty");
        }
        if (project.getAssemblyConstraints().isEmpty()) {
            warnings.add("assembly: no assembly constraints found");
        }
    }

    private void checkManufacturing(DesignProject project, List<String> errors, List<String> warnings) {
        if (project.getTechnicalRequirements().isEmpty()) {
            warnings.add("manufacturing: no technical/manufacturing requirements");
        }
        boolean hasFallback = project.getResolvedParts().stream()
                .anyMatch(part -> "mock".equals(part.getRetrievalStatus()) || part.getRetrievalStatus().contains("fallback"));
        if (hasFallback) {
            warnings.add("standard parts: mock/fallback standard parts require final verification");
        }
    }

    private boolean blank(String value) {
        return value == null || value.isBlank();
    }

    public record Review(boolean passed, int score, List<String> errors, List<String> warnings) {}
}
