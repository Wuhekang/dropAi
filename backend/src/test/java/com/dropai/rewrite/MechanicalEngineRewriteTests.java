package com.dropai.rewrite;

import com.dropai.rewrite.mechanicalengine.cad.CadDslService;
import com.dropai.rewrite.mechanicalengine.cadcore.FeatureBasedCadSpec;
import com.dropai.rewrite.mechanicalengine.cadcore.FeatureInterpreter;
import com.dropai.rewrite.mechanicalengine.cadcore.PartDesignJobGenerator;
import com.dropai.rewrite.mechanicalengine.domain.MechanicalProject;
import com.dropai.rewrite.mechanicalengine.service.MechanicalChiefEngineer;
import com.dropai.rewrite.mechanicalengine.service.MechanicalPackageBuilder;
import com.dropai.rewrite.mechanicalengine.validation.CADRealityValidator;
import com.dropai.rewrite.mechanicalengine.validation.MechanicalArtifactValidator;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import java.util.zip.ZipInputStream;

import static org.junit.jupiter.api.Assertions.*;

class MechanicalEngineRewriteTests {
    private final ObjectMapper mapper = new ObjectMapper();
    private final FeatureInterpreter interpreter = new FeatureInterpreter();

    @Test
    void clampDemoProducesPartDesignFeatureSpecAndConstraints() throws Exception {
        MechanicalProject project = new MechanicalChiefEngineer().design("automatic clamp");
        assertEquals(5, project.getParts().size());
        assertTrue(project.getParts().stream().allMatch(part ->
                part.features().stream().anyMatch(feature -> "SKETCH".equals(feature.type())) &&
                part.features().stream().anyMatch(feature -> "PAD".equals(feature.type()))));
        assertTrue(project.getParts().stream().flatMap(part -> part.features().stream()).anyMatch(feature -> "HOLE".equals(feature.type())));
        assertTrue(project.getParts().stream().flatMap(part -> part.features().stream()).anyMatch(feature -> "FILLET".equals(feature.type())));
        assertEquals(project.getAssembly().getComponents().size(), project.getAssembly().getConstraints().size());

        FeatureBasedCadSpec featureSpec = FeatureBasedCadSpec.from(project);
        assertDoesNotThrow(() -> interpreter.validate(featureSpec));
        Path root = Files.createTempDirectory("feature-cad-");
        Path spec = new CadDslService(mapper, interpreter).write(project, root);
        assertEquals(5, mapper.readTree(spec.toFile()).path("parts").size());
        String script = Files.readString(new PartDesignJobGenerator().generate(root));
        assertTrue(script.contains("PartDesign::Body"));
        assertTrue(script.contains("Sketcher::SketchObject"));
        assertTrue(script.contains("PartDesign::Pad"));
        assertTrue(script.contains("PartDesign::Hole"));
        assertTrue(script.contains("PartDesign::Fillet"));
        assertTrue(script.contains("DROP_AI_FEATURE|"));
        assertTrue(script.contains("shape.isValid()"));
        assertTrue(script.contains("len(shape.Solids) <= 0"));
        assertTrue(script.contains("shape.exportStep(step_path)"));
        assertFalse(script.contains("Part.export([body]"));
        assertTrue(script.contains("FEATURE_FAILED:%s:%s:%s"));
        assertFalse(script.contains("makeBox"));
        assertFalse(script.contains("makeCylinder"));
        assertFalse(script.contains("makeSphere"));
    }

    @Test
    void interpreterRejectsPrimitiveAndIncompleteFeatureSpecs() {
        MechanicalProject project = new MechanicalChiefEngineer().design("automatic clamp");
        FeatureBasedCadSpec valid = FeatureBasedCadSpec.from(project);
        FeatureBasedCadSpec invalid = new FeatureBasedCadSpec(valid.projectId(), java.util.List.of(
                new FeatureBasedCadSpec.Part("P999", "Forbidden", "Q235B", java.util.List.of(
                        new FeatureBasedCadSpec.Feature(1, "BOX", "forbidden", java.util.Map.of())
                ))), valid.assembly());
        IllegalArgumentException failure = assertThrows(IllegalArgumentException.class,
                () -> interpreter.validate(invalid));
        assertTrue(failure.getMessage().contains("unsupported feature BOX"));
    }

    @Test
    void realityValidatorRejectsMissingFeatureHistory() throws Exception {
        Path root = Files.createTempDirectory("cad-invalid-");
        MechanicalProject project = new MechanicalChiefEngineer().design("automatic clamp");
        Files.createDirectories(root.resolve("01_Model/Parts"));
        Files.createDirectories(root.resolve("02_STEP"));
        Files.writeString(root.resolve("01_Model/Assembly.FCStd"), "fake");
        for (var part : project.getParts()) Files.writeString(root.resolve("01_Model/Parts/" + part.partNumber() + ".brep"), "box");
        Files.writeString(root.resolve("02_STEP/Assembly.STEP"), "fake step");
        var report = new MechanicalArtifactValidator(mapper, new CADRealityValidator(mapper)).validate(project, root);
        assertFalse(report.passed());
        assertTrue(report.errors().stream().anyMatch(error -> error.contains("CAD reality report is missing")));
    }

    @Test
    void mechanicalPackageExcludesDocumentOutputs() throws Exception {
        Path root = Files.createTempDirectory("cad-package-");
        Set<String> expected = new HashSet<>();
        for (String directory : new String[]{"01_Model", "02_STEP", "03_Drawing", "05_Analysis"}) {
            Files.createDirectories(root.resolve(directory));
            Files.writeString(root.resolve(directory + "/artifact.bin"), directory);
            expected.add(directory + "/artifact.bin");
        }
        byte[] zip = new MechanicalPackageBuilder().build(root);
        Set<String> names = new HashSet<>();
        try (ZipInputStream input = new ZipInputStream(new ByteArrayInputStream(zip))) {
            for (var entry = input.getNextEntry(); entry != null; entry = input.getNextEntry()) names.add(entry.getName());
        }
        assertEquals(expected, names);
        assertTrue(names.stream().noneMatch(name -> name.endsWith(".docx") || name.contains("Design_Report")));
    }
}
