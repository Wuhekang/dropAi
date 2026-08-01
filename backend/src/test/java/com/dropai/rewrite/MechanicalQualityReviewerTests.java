package com.dropai.rewrite;

import com.dropai.rewrite.modules.cadFeatureGenerator.CADFeature;
import com.dropai.rewrite.modules.model.DesignProject;
import com.dropai.rewrite.modules.model.MechanicalDesignResult;
import com.dropai.rewrite.modules.modelQualityGate.MechanicalQualityReviewer;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MechanicalQualityReviewerTests {
    @Test
    void reviewerBlocksEmptyMechanicalDesign() {
        MechanicalQualityReviewer.Review review = new MechanicalQualityReviewer().review(new DesignProject());

        assertFalse(review.passed());
        assertTrue(review.errors().stream().anyMatch(error -> error.startsWith("structure:")));
        assertTrue(review.errors().stream().anyMatch(error -> error.startsWith("calculations:")));
    }

    @Test
    void schemaCollectsProjectOutputs() {
        DesignProject project = new DesignProject();
        project.setProjectId("p1");
        project.setProjectTitle("Conveyor design");
        project.setEquipmentName("Belt conveyor");
        project.getVerificationItems().add("load check");
        project.getMainStructures().addAll(List.of("frame", "drive drum", "belt"));
        project.getMaterials().add("Q235B");
        project.getCalculations().add(new DesignProject.Calculation("load", "F=m*g", "m=10", 98.1, "N", "pass"));
        DesignProject.DesignPart part = new DesignProject.DesignPart();
        part.setName("frame");
        part.setMaterial("Q235B");
        part.getDimensions().put("length", 1200);
        part.setCadFeatures(List.of(new CADFeature("Extrude", Map.of("depth", 20), "test")));
        project.setResolvedParts(List.of(part));

        MechanicalDesignResult result = MechanicalDesignResult.fromProject(project);

        assertEquals("p1", result.getProduct().get("projectId"));
        assertEquals(1, result.getParts().size());
        assertEquals(1, result.getCadFeatures().size());
        assertEquals(1, result.getCalculations().size());
    }
}
