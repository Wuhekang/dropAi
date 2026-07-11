package com.dropai.rewrite;

import com.dropai.rewrite.modules.assemblyBuilder.AssemblyBuilder;
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
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertTrue;

class MechanicalCadPipelineTests {
    @Test
    void localStandardPartDatabaseFeedsCadFeaturesAssemblyAndStepExport() {
        DesignProject project = new DesignProject();
        project.setProjectTitle("belt conveyor mechanical design");
        project.setEquipmentName("belt conveyor");
        project.setSuggestedParameters(List.of(
                new DesignProject.Parameter("整机长度", 900, "mm", "test", "cad smoke"),
                new DesignProject.Parameter("整机宽度", 420, "mm", "test", "cad smoke"),
                new DesignProject.Parameter("整机高度", 260, "mm", "test", "cad smoke")
        ));

        DesignProject.StructureNode root = new DesignProject.StructureNode("assembly", "root", "test", 1.0);
        root.setChildren(List.of(new DesignProject.StructureNode("drive motor", "drive", "test", 1.0)));
        project.setStructureTree(root);

        ObjectMapper mapper = new ObjectMapper();
        StandardPartCache cache = new StandardPartCache(mapper);
        StandardPartSelector selector = new StandardPartSelector(cache, new MockOnlineStandardPartProvider(cache), new LocalStandardPartDatabase(mapper));
        selector.select(project);

        assertTrue(project.getResolvedParts().stream().anyMatch(part -> "local_library".equals(part.getRetrievalStatus())));
        project.getResolvedParts().add(nonStandard("frame", "frame", "Q235", "base assembly"));
        project.getResolvedParts().add(nonStandard("drive roller", "roller", "45 steel", "drive assembly"));
        project.getResolvedParts().add(nonStandard("tail roller", "roller", "45 steel", "drive assembly"));
        project.getResolvedParts().add(nonStandard("tension plate", "plate", "Q235", "frame assembly"));

        new CADFeatureGenerator().generate(project);
        assertTrue(project.getResolvedParts().stream().allMatch(part -> !part.getCadFeatures().isEmpty()));

        new AssemblyBuilder().build(project);
        assertTrue(project.getAssemblyModel().getComponents().size() >= 5);
        assertTrue(project.getAssemblyModel().getConstraints().size() >= 5);

        List<DrawingArtifact> stepFiles = new StepExportEngine().export(project);
        assertTrue(stepFiles.stream().anyMatch(file -> "assembly.step".equals(file.fileName())));
        assertTrue(stepFiles.stream().anyMatch(file -> "assembly-validation.json".equals(file.fileName())));
        assertTrue(stepFiles.stream().filter(file -> "assembly.step".equals(file.fileName()))
                .findFirst().orElseThrow().content().length > 300);
        String validation = new String(stepFiles.stream().filter(file -> "assembly-validation.json".equals(file.fileName()))
                .findFirst().orElseThrow().content(), StandardCharsets.UTF_8);
        assertTrue(validation.contains("\"passed\": true"));
    }

    private DesignProject.DesignPart nonStandard(String name, String category, String material, String parent) {
        DesignProject.DesignPart part = new DesignProject.DesignPart();
        part.setPartType("non_standard");
        part.setCategory(category);
        part.setName(name);
        part.setMaterial(material);
        part.setQuantity(1);
        part.setParentStructure(parent);
        part.setDimensions(Map.of("length", 180, "width", 80, "height", 24));
        part.setGeometryFeatures(List.of("parametric solid", "mounting holes"));
        return part;
    }
}
