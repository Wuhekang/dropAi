package com.dropai.rewrite;

import com.dropai.rewrite.mechanicalengine.domain.MechanicalProject;
import com.dropai.rewrite.mechanicalengine.service.MechanicalChiefEngineer;
import com.dropai.rewrite.mechanicalengine.service.MechanicalPackageBuilder;
import com.dropai.rewrite.mechanicalengine.validation.MechanicalArtifactValidator;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import java.util.zip.ZipInputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MechanicalEngineRewriteTests {
    @Test
    void chiefEngineerProducesFeaturePlansAndRealMateSpecifications() {
        MechanicalProject project = new MechanicalChiefEngineer().design("油罐检测爬壁机器人，需要磁吸附、移动和传感器检测");
        assertTrue(project.getParts().size() >= 5);
        assertTrue(project.getParts().stream().allMatch(part -> part.featureTree().size() >= 5));
        assertTrue(project.getParts().stream().flatMap(part -> part.featureTree().stream()).anyMatch(feature -> "HoleWizard".equals(feature.type())));
        assertTrue(project.getAssembly().getMates().size() >= project.getAssembly().getComponents().size() - 1);
        assertTrue(project.getParameters().stream().allMatch(parameter -> parameter.reason() != null && !parameter.reason().isBlank()));
    }

    @Test
    void validatorRejectsPlaceholderFiles() throws Exception {
        Path root = Files.createTempDirectory("mechanical-invalid-");
        MechanicalProject project = new MechanicalChiefEngineer().design("通用机械设备");
        Files.createDirectories(root.resolve("01_Model/Parts"));
        Files.createDirectories(root.resolve("02_STEP"));
        Files.createDirectories(root.resolve("03_Drawing"));
        Files.createDirectories(root.resolve("04_Document"));
        Files.writeString(root.resolve("01_Model/Assembly.SLDASM"), "fake assembly");
        for (var part : project.getParts()) Files.writeString(root.resolve("01_Model/Parts/" + part.partNumber() + ".SLDPRT"), "fake part");
        Files.writeString(root.resolve("02_STEP/Assembly.STEP"), "fake step");
        Files.writeString(root.resolve("03_Drawing/Assembly.DWG"), "fake dwg");
        Files.writeString(root.resolve("04_Document/Design_Report.pdf"), "fake pdf");

        var report = new MechanicalArtifactValidator().validate(project, root);
        assertFalse(report.passed());
        assertTrue(report.errors().stream().anyMatch(error -> error.contains("not a native SolidWorks")));
        assertTrue(report.errors().stream().anyMatch(error -> error.contains("STEP cannot be reopened")));
    }

    @Test
    void finalPackageKeepsOnlyFourEngineeringFolders() throws Exception {
        Path root = Files.createTempDirectory("mechanical-package-");
        for (String directory : new String[]{"01_Model", "02_STEP", "03_Drawing", "04_Document"}) {
            Files.createDirectories(root.resolve(directory));
            Files.writeString(root.resolve(directory).resolve("artifact.bin"), directory);
        }
        Files.writeString(root.resolve("debug.json"), "must not be packaged");
        byte[] zip = new MechanicalPackageBuilder().build(root);
        Set<String> names = new HashSet<>();
        try (ZipInputStream input = new ZipInputStream(new ByteArrayInputStream(zip))) {
            for (var entry = input.getNextEntry(); entry != null; entry = input.getNextEntry()) names.add(entry.getName());
        }
        assertEquals(Set.of("01_Model/artifact.bin", "02_STEP/artifact.bin", "03_Drawing/artifact.bin", "04_Document/artifact.bin"), names);
    }
}
