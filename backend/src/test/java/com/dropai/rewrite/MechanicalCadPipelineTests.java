package com.dropai.rewrite;

import com.dropai.rewrite.modules.cadFeatureGenerator.CADFeatureGenerator;
import com.dropai.rewrite.modules.drawingEngine.DrawingArtifact;
import com.dropai.rewrite.modules.model.DesignProject;
import com.dropai.rewrite.modules.standardPartSelector.LocalStandardPartDatabase;
import com.dropai.rewrite.modules.standardPartSelector.MockOnlineStandardPartProvider;
import com.dropai.rewrite.modules.standardPartSelector.StandardPartCache;
import com.dropai.rewrite.modules.standardPartSelector.StandardPartSelector;
import com.dropai.rewrite.modules.stepExportEngine.StepExportEngine;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MechanicalCadPipelineTests {
    @Test
    void localStandardPartDatabaseFeedsCadFeaturesAndStepExport() {
        DesignProject project = new DesignProject();
        project.setProjectTitle("带式输送机结构设计");
        DesignProject.StructureNode root = new DesignProject.StructureNode("整机", "root", "test", 1.0);
        root.setChildren(List.of(new DesignProject.StructureNode("驱动电机", "drive", "test", 1.0)));
        project.setStructureTree(root);

        ObjectMapper mapper = new ObjectMapper();
        StandardPartCache cache = new StandardPartCache(mapper);
        StandardPartSelector selector = new StandardPartSelector(cache, new MockOnlineStandardPartProvider(cache), new LocalStandardPartDatabase(mapper));
        selector.select(project);

        assertTrue(project.getResolvedParts().stream().anyMatch(part -> "local_library".equals(part.getRetrievalStatus())));

        new CADFeatureGenerator().generate(project);
        assertTrue(project.getResolvedParts().stream().allMatch(part -> !part.getCadFeatures().isEmpty()));

        List<DrawingArtifact> stepFiles = new StepExportEngine().export(project);
        assertTrue(stepFiles.stream().anyMatch(file -> "assembly.step".equals(file.fileName())));
        assertFalse(stepFiles.stream().filter(file -> "assembly.step".equals(file.fileName()))
                .map(file -> new String(file.content(), StandardCharsets.UTF_8))
                .findFirst().orElseThrow().isBlank());
        assertTrue(new String(stepFiles.get(0).content(), StandardCharsets.UTF_8).contains("ISO-10303-21"));
    }
}
