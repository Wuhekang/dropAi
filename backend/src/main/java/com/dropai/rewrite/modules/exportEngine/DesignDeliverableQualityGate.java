package com.dropai.rewrite.modules.exportEngine;

import com.dropai.rewrite.modules.drawingEngine.DrawingArtifact;
import com.dropai.rewrite.modules.model.DesignProject;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.security.MessageDigest;
import java.util.Set;
import java.util.stream.Collectors;
import javax.imageio.ImageIO;
import java.io.ByteArrayInputStream;

public class DesignDeliverableQualityGate {
    private static final int MIN_STEP_SIZE = 300;
    private static final int MIN_PAPER_SIZE = 1_000;
    private static final Set<String> REQUIRED_FILES = Set.of(
            "MechanicalDesignPlan.json",
            "mechanical-pipeline-audit.json",
            "assembly-model.json",
            "model-generation-report.json",
            "assembly-validation.json",
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
        validateStep(artifacts, errors);
        validateDrawingValidation(artifacts, errors);
        validateSvg(artifacts, "assembly.svg", errors);
        validateSvg(artifacts, "cad_preview.svg", errors);
        validatePng(artifacts, "assembly.png", errors);
        validatePng(artifacts, "cad_preview.png", errors);
        artifacts.stream().filter(item -> "assembly-validation.json".equals(item.fileName())).findFirst()
                .ifPresentOrElse(item -> {
                    String json = new String(item.content(), java.nio.charset.StandardCharsets.UTF_8);
                    if (!json.contains("\"passed\": true")) {
                        errors.add("assembly-validation.json did not pass");
                    }
                }, () -> errors.add("missing assembly-validation.json"));
        if (project.getAssemblyModel() == null || project.getAssemblyModel().getComponents().size() < 5) {
            errors.add("assembly model has fewer than 5 components");
        }
        if (project.getAssemblyModel() == null || project.getAssemblyModel().getConstraints().size() < 5) {
            errors.add("assembly model has fewer than 5 constraints");
        }
        validateAssemblyReferences(project, errors);
        if (project.getBom() == null || project.getBom().isEmpty()) {
            errors.add("BOM is empty");
        }
        if (project.getVerificationItems() != null && project.getVerificationItems().stream()
                .anyMatch(item -> item != null && item.startsWith("FAILED_REVIEW"))) {
            errors.add("mechanical reality review failed");
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
        validateDxfGeometry(artifacts, "assembly.dxf", 35, errors);
        Set<String> partHashes = new HashSet<>();
        artifacts.stream()
                .filter(item -> item.fileName().matches("part_\\d{2}\\.dxf"))
                .forEach(item -> {
                    validateDxfGeometry(artifacts, item.fileName(), 20, errors);
                    String hash = sha256(item.content());
                    if (!partHashes.add(hash)) {
                        errors.add("duplicated part drawing content: " + item.fileName());
                    }
                });
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

    private void validateDxfGeometry(List<DrawingArtifact> artifacts, String name, int minEntities, List<String> errors) {
        artifacts.stream().filter(item -> name.equals(item.fileName())).findFirst().ifPresentOrElse(item -> {
            String content = new String(item.content(), java.nio.charset.StandardCharsets.UTF_8);
            long entities = content.lines()
                    .filter(line -> line.equals("LINE") || line.equals("CIRCLE") || line.equals("TEXT") || line.equals("FILL_RECT"))
                    .count();
            if (entities < minEntities) {
                errors.add(name + " has too few DXF entities: " + entities);
            }
            if (!content.contains("SECTION") || !content.endsWith("EOF\n")) {
                errors.add(name + " is not a complete DXF document");
            }
        }, () -> errors.add("missing drawing artifact: " + name));
    }

    private void validateStep(List<DrawingArtifact> artifacts, List<String> errors) {
        artifacts.stream().filter(item -> "assembly.step".equals(item.fileName())).findFirst().ifPresent(item -> {
            String content = new String(item.content(), java.nio.charset.StandardCharsets.ISO_8859_1);
            if (!content.contains("ISO-10303-21") || !content.contains("END-ISO-10303-21")) {
                errors.add("assembly.step is not a complete STEP exchange file");
            }
        });
    }

    private void validateDrawingValidation(List<DrawingArtifact> artifacts, List<String> errors) {
        artifacts.stream().filter(item -> "drawing-validation.json".equals(item.fileName())).findFirst()
                .ifPresentOrElse(item -> {
                    String json = new String(item.content(), java.nio.charset.StandardCharsets.UTF_8);
                    if (!json.contains("\"passed\": true") && !json.contains("\"pass\": true")) {
                        errors.add("drawing-validation.json did not pass");
                    }
                }, () -> errors.add("missing drawing-validation.json"));
    }

    private void validateSvg(List<DrawingArtifact> artifacts, String name, List<String> errors) {
        artifacts.stream().filter(item -> name.equals(item.fileName())).findFirst().ifPresentOrElse(item -> {
            String svg = new String(item.content(), java.nio.charset.StandardCharsets.UTF_8).trim();
            if (!svg.startsWith("<svg") || !svg.endsWith("</svg>")) errors.add(name + " is not renderable SVG");
        }, () -> errors.add("missing drawing preview: " + name));
    }

    private void validatePng(List<DrawingArtifact> artifacts, String name, List<String> errors) {
        artifacts.stream().filter(item -> name.equals(item.fileName())).findFirst().ifPresentOrElse(item -> {
            try {
                if (ImageIO.read(new ByteArrayInputStream(item.content())) == null) errors.add(name + " is not a readable PNG");
            } catch (Exception exception) {
                errors.add(name + " is not a readable PNG");
            }
        }, () -> errors.add("missing drawing preview: " + name));
    }

    private void validateAssemblyReferences(DesignProject project, List<String> errors) {
        if (project.getAssemblyModel() == null) return;
        Set<String> ids = project.getAssemblyModel().getComponents().stream()
                .map(item -> item.getId()).filter(id -> id != null && !id.isBlank()).collect(Collectors.toSet());
        project.getAssemblyModel().getConstraints().forEach(item -> {
            if (!ids.contains(item.getComponentA())) errors.add("assembly constraint references missing component: " + item.getComponentA());
            if (item.getComponentB() == null || item.getComponentB().isBlank()) errors.add("assembly constraint has empty mate target: " + item.getComponentA());
        });
    }

    private String sha256(byte[] content) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(content == null ? new byte[0] : content);
            StringBuilder hex = new StringBuilder();
            for (byte b : digest) hex.append("%02x".formatted(b));
            return hex.toString();
        } catch (Exception exception) {
            return "";
        }
    }

    public record Report(boolean passed, List<String> errors) {}
}
