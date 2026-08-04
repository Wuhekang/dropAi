package com.dropai.rewrite.mechanicalengine.validation;

import com.dropai.rewrite.mechanicalengine.domain.MechanicalProject;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

@Service
public class CADRealityValidator {
    private final ObjectMapper mapper;
    public CADRealityValidator(ObjectMapper mapper) { this.mapper = mapper; }

    public List<String> validate(MechanicalProject project, Path root) {
        List<String> errors = new ArrayList<>();
        Path report = root.resolve("02_STEP/cad-reality-report.json");
        if (!Files.isRegularFile(report)) return List.of("CAD reality report is missing");
        try {
            JsonNode node = mapper.readTree(report.toFile());
            if (!node.path("passed").asBoolean()) errors.add("CAD kernel did not report success");
            if (!"OpenCascade".equals(node.path("kernel").asText())) errors.add("STEP source is not OpenCascade");
            if (!"FreeCAD PartDesign".equals(node.path("modelingMethod").asText())) errors.add("modeling method is not FreeCAD PartDesign");
            if (node.path("primitiveOnly").asBoolean(true)) errors.add("primitive-only CAD generation is forbidden");
            if (node.path("parts").size() != project.getParts().size()) errors.add("PartDesign body count does not match the design spec");
            for (JsonNode part : node.path("parts")) {
                List<String> types = new ArrayList<>();
                part.path("features").forEach(value -> types.add(value.asText()));
                if (part.path("body").asText().isBlank()) errors.add("part has no PartDesign Body");
                if (types.stream().noneMatch(v -> v.contains("Sketcher::SketchObject"))) errors.add(part.path("partNumber").asText() + " has no Sketch");
                if (types.stream().noneMatch(v -> v.contains("PartDesign::Pad"))) errors.add(part.path("partNumber").asText() + " has no Pad");
                if (part.path("featureCount").asInt() < 3) errors.add(part.path("partNumber").asText() + " has no meaningful feature history");
                if (part.path("volume").asDouble() <= 0 || part.path("solidCount").asInt() <= 0) errors.add(part.path("partNumber").asText() + " has an empty solid");
            }
            if (node.path("featureLog").isEmpty()) errors.add("feature execution log is empty");
            node.path("featureLog").forEach(entry -> { if (!"SUCCESS".equals(entry.path("status").asText())) errors.add("feature execution failed: " + entry.path("feature").asText()); });
            if (node.path("assemblyConstraints").size() != project.getAssembly().getConstraints().size()) errors.add("assembly constraint count does not match the design spec");
            node.path("assemblyConstraints").forEach(entry -> { if (!"SOLVED".equals(entry.path("status").asText())) errors.add("assembly constraint was not solved"); });
        } catch (Exception exception) {
            errors.add("CAD reality report is invalid: " + exception.getMessage());
        }
        return errors;
    }
}
