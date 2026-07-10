package com.dropai.rewrite.modules.exportEngine;

import com.dropai.rewrite.modules.drawingEngine.DrawingArtifact;
import com.dropai.rewrite.modules.model.DesignProject;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class DesignDeliverableQualityGate {
    private static final int MIN_STEP_SIZE = 300;
    private static final int MIN_PAPER_SIZE = 1_000;
    private static final Set<String> REQUIRED_FILES = Set.of(
            "MechanicalDesignPlan.json",
            "mechanical-pipeline-audit.json",
            "assembly-model.json",
            "model-generation-report.json",
            "model_3d.json",
            "assembly.step",
            "assembly.dxf",
            "cad_preview.svg",
            "cad_preview.png",
            "part_01.step",
            "part_02.step",
            "part_03.step",
            "part_04.step",
            "part_05.step",
            "part_01.dxf",
            "part_02.dxf",
            "part_03.dxf",
            "part_04.dxf",
            "part_05.dxf",
            "paper.docx"
    );

    public Report validate(DesignProject project, List<DrawingArtifact> artifacts) {
        List<String> errors = new ArrayList<>();
        Set<String> names = artifacts.stream().map(DrawingArtifact::fileName).collect(Collectors.toSet());
        for (String required : REQUIRED_FILES) {
            if (!names.contains(required)) {
                errors.add("missing required artifact: " + required);
            }
        }
        for (DrawingArtifact artifact : artifacts) {
            if (artifact.content() == null || artifact.content().length == 0) {
                errors.add("empty artifact: " + artifact.fileName());
            }
        }
        assertArtifactSize(artifacts, "assembly.step", MIN_STEP_SIZE, errors);
        assertArtifactSize(artifacts, "paper.docx", MIN_PAPER_SIZE, errors);
        if (project.getAssemblyModel() == null || project.getAssemblyModel().getComponents().size() < 5) {
            errors.add("assembly model has fewer than 5 components");
        }
        if (project.getAssemblyModel() == null || project.getAssemblyModel().getConstraints().size() < 5) {
            errors.add("assembly model has fewer than 5 constraints");
        }
        if (project.getBom() == null || project.getBom().isEmpty()) {
            errors.add("BOM is empty");
        }
        if (project.getDrawingPlan() == null
                || project.getDrawingPlan().getMainView().getVisibleParts().isEmpty()
                || project.getDrawingPlan().getTopView().getVisibleParts().isEmpty()
                || project.getDrawingPlan().getSideView().getVisibleParts().isEmpty()) {
            errors.add("drawing plan is missing required orthographic views");
        }
        long partDrawings = artifacts.stream()
                .filter(item -> item.fileName().matches("part_\\d{2}\\.dxf"))
                .count();
        if (partDrawings < 5) {
            errors.add("fewer than 5 key part drawings");
        }
        return new Report(errors.isEmpty(), errors);
    }

    private void assertArtifactSize(List<DrawingArtifact> artifacts, String name, int minSize, List<String> errors) {
        artifacts.stream().filter(item -> name.equals(item.fileName())).findFirst()
                .filter(item -> item.content() != null && item.content().length >= minSize)
                .orElseGet(() -> {
                    errors.add(name + " is missing or too small");
                    return null;
                });
    }

    public record Report(boolean passed, List<String> errors) {}
}
