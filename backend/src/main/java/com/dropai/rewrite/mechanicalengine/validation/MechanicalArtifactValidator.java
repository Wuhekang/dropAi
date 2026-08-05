package com.dropai.rewrite.mechanicalengine.validation;

import com.dropai.rewrite.mechanicalengine.domain.MechanicalProject;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
public class MechanicalArtifactValidator {
    private final ObjectMapper mapper;
    private final CADRealityValidator realityValidator;
    public MechanicalArtifactValidator(ObjectMapper mapper, CADRealityValidator realityValidator) {
        this.mapper = mapper;
        this.realityValidator = realityValidator;
    }

    public ValidationReport validate(MechanicalProject project, Path root) {
        List<String> errors = new ArrayList<>();
        requireFile(root.resolve("01_Model/Assembly.FCStd"), 64, "FreeCAD assembly", errors);
        for (MechanicalProject.CADModelSpec part : project.getParts()) {
            Path brep = root.resolve("01_Model/Parts/" + part.partNumber() + ".brep");
            String value = text(brep, "BRep " + part.partNumber(), errors);
            if (!value.contains("DBRep_DrawableShape") && !value.contains("CASCADE Topology")) errors.add(part.partNumber() + " is not an OpenCascade BRep file");
            requireStep(root.resolve("02_STEP/" + part.partNumber() + ".step"), "STEP " + part.partNumber(), errors);
            Set<String> featureTypes = new HashSet<>();
            part.features().forEach(feature -> featureTypes.add(feature.type()));
            if (part.features().size() < 2 || featureTypes.size() < 2) errors.add(part.partNumber() + " has only trivial primitive geometry");
        }
        requireStep(root.resolve("02_STEP/Assembly.STEP"), "assembly STEP", errors);
        requireStl(root.resolve("02_STEP/Assembly.stl"), errors);
        errors.addAll(realityValidator.validate(project, root));
        requireSvg(root.resolve("03_Drawing/Assembly.svg"), "assembly drawing", errors);
        validateProjection(root.resolve("03_Drawing/projection-lines.json"), errors);
        requireDxf(root.resolve("03_Drawing/Assembly.dxf"), errors);
        validateDrawingMetadata(root.resolve("03_Drawing/drawing-metadata.json"), project, errors);
        requirePdf(root.resolve("03_Drawing/Assembly_Drawing.pdf"), "drawing PDF", errors);
        for (MechanicalProject.CADModelSpec part : project.getParts()) requireSvg(root.resolve("03_Drawing/Parts_Drawing/" + part.partNumber() + ".svg"), "part drawing " + part.partNumber(), errors);
        requireSvg(root.resolve("05_Analysis/stress-cloud.svg"), "analysis cloud", errors);
        if (project.getAssembly().getConstraints().size() < project.getAssembly().getComponents().size()) errors.add("assembly constraints are incomplete");
        return new ValidationReport(errors.isEmpty(), errors);
    }

    private void requireStep(Path file, String label, List<String> errors) {
        String value = text(file, label, errors);
        if (!value.contains("ISO-10303-21") || !value.contains("END-ISO-10303-21")) errors.add(label + " is incomplete");
    }
    private void requireStl(Path file, List<String> errors) {
        try { if (!Files.isRegularFile(file) || Files.size(file) < 84) errors.add("browser STL preview is missing or empty"); }
        catch (Exception exception) { errors.add("browser STL preview cannot be read"); }
    }
    private void requireSvg(Path file, String label, List<String> errors) {
        String value = text(file, label, errors);
        if (!value.contains("<svg") || !value.contains("</svg>")) errors.add(label + " is not valid SVG");
    }
    private void requireDxf(Path file, List<String> errors) {
        String value = text(file, "DXF drawing", errors);
        if (!value.contains("SECTION") || !value.contains("ENTITIES") || !value.contains("EOF")) errors.add("DXF drawing is incomplete");
    }
    private void validateProjection(Path file, List<String> errors) {
        try {
            JsonNode node=mapper.readTree(file.toFile());
            if(node.path("front").isEmpty()||node.path("top").isEmpty()||node.path("right").isEmpty()) errors.add("BRep drawing projections are incomplete");
        } catch(Exception exception) { errors.add("BRep drawing projection data is missing or invalid"); }
    }
    private void validateDrawingMetadata(Path file, MechanicalProject project, List<String> errors) {
        try {
            JsonNode node = mapper.readTree(file.toFile());
            if (!"FreeCAD PartDesign BRep".equals(node.path("source").asText())) errors.add("drawing is not traceable to PartDesign BRep");
            if (node.path("views").size() < 3) errors.add("drawing metadata has fewer than three views");
            if (node.path("generalTolerance").asText().isBlank()) errors.add("drawing has no general tolerance");
            if (node.path("materials").size() != project.getParts().size()) errors.add("drawing material table is incomplete");
            if (node.path("technicalRequirements").isEmpty()) errors.add("drawing technical requirements are missing");
        } catch (Exception exception) { errors.add("drawing metadata is missing or invalid"); }
    }
    private void requirePdf(Path file, String label, List<String> errors) {
        try {
            byte[] value = Files.readAllBytes(file);
            if (value.length < 5 || !new String(value, 0, 5, StandardCharsets.US_ASCII).equals("%PDF-")) errors.add(label + " is invalid");
        } catch (Exception exception) { errors.add(label + " is missing or unreadable"); }
    }
    private void requireFile(Path file, long min, String label, List<String> errors) {
        try { if (!Files.isRegularFile(file) || Files.size(file) < min) errors.add(label + " is missing or empty"); }
        catch (Exception exception) { errors.add(label + " cannot be read"); }
    }
    private String text(Path file, String label, List<String> errors) {
        try { return Files.readString(file, StandardCharsets.ISO_8859_1); }
        catch (Exception exception) { errors.add(label + " is missing or unreadable"); return ""; }
    }
    public record ValidationReport(boolean passed, List<String> errors) {}
}
