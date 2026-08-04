package com.dropai.rewrite;

import com.dropai.rewrite.mechanicalengine.cadcore.FeatureInterpreter;
import com.dropai.rewrite.mechanicalengine.cadcore.MechanicalDesignCadConverter;
import com.dropai.rewrite.mechanicalengine.cadcore.PartDesignJobGenerator;
import com.dropai.rewrite.mechanicalengine.productplanner.MechanicalProductFamilyResolver;
import com.dropai.rewrite.mechanicalengine.productplanner.ProductFamily;
import com.dropai.rewrite.mechanicalengine.service.MechanicalAnalysisEngine;
import com.dropai.rewrite.mechanicalengine.service.MechanicalChiefEngineer;
import com.dropai.rewrite.mechanicalengine.service.MechanicalDocumentAgent;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

class MechanicalPlatformV3Tests {
    private final MechanicalChiefEngineer chief = new MechanicalChiefEngineer();
    private final MechanicalDesignCadConverter converter = new MechanicalDesignCadConverter();
    private final FeatureInterpreter interpreter = new FeatureInterpreter();

    @Test
    void resolverIdentifiesSupportedFamiliesAndRejectsUnknownProducts() {
        MechanicalProductFamilyResolver resolver = new MechanicalProductFamilyResolver();
        assertEquals(ProductFamily.FIXTURE, resolver.resolve("设计自动夹具"));
        assertEquals(ProductFamily.AGV, resolver.resolve("设计100kg AGV"));
        assertEquals(ProductFamily.ROBOT, resolver.resolve("设计油罐检测机器人"));
        assertEquals(ProductFamily.CONVEYOR, resolver.resolve("设计皮带输送机"));
        assertEquals(ProductFamily.MECHANISM, resolver.resolve("设计曲柄滑块机构"));
        assertThrows(IllegalArgumentException.class, () -> resolver.resolve("设计一个未知产品"));
    }

    @Test
    void fourProductFamiliesReachTheSharedFeatureCadBoundary() throws Exception {
        List<String> cases = List.of("设计自动夹具", "设计100kg AGV", "设计油罐检测机器人", "设计皮带输送机");
        String script = Files.readString(new PartDesignJobGenerator().generate(Files.createTempDirectory("v3-partdesign-")));
        for (String requirement : cases) {
            var design = chief.designSpec(requirement);
            var cad = converter.convert("v3-test", design);
            assertDoesNotThrow(() -> interpreter.validate(cad), requirement);
            assertTrue(cad.parts().stream().allMatch(part -> has(part, "SKETCH") && has(part, "PAD")
                    && (has(part, "HOLE") || has(part, "POCKET"))
                    && (has(part, "FILLET") || has(part, "CHAMFER"))), requirement);
            assertFalse(cad.assembly().constraints().isEmpty(), requirement);
        }
        assertTrue(Stream.of("PartDesign::Body", "Sketcher::SketchObject", "PartDesign::Pad", "PartDesign::Hole", "PartDesign::Fillet")
                .allMatch(script::contains));
        assertFalse(Stream.of("makeBox", "makeCylinder", "makeSphere").anyMatch(script::contains));
    }

    @Test
    void analysisAndDocumentAreGeneratedFromEachDesign() throws Exception {
        MechanicalAnalysisEngine analysis = new MechanicalAnalysisEngine();
        MechanicalDocumentAgent documents = new MechanicalDocumentAgent();
        for (String requirement : List.of("设计自动夹具", "设计100kg AGV", "设计油罐检测机器人", "设计皮带输送机")) {
            var project = chief.design(requirement);
            var report = analysis.analyze(project.getDesignSpec());
            assertTrue(report.governingLoadN() > 0);
            assertTrue(report.estimatedStressMpa() > 0);
            assertFalse(report.loadPath().isEmpty());
            assertFalse(report.stressCloud().isEmpty());
            project.setAnalysisReport(report);
            var output = Files.createTempFile("v3-report-", ".pdf");
            documents.generate(project, output);
            assertTrue(Files.size(output) > 100);
        }
    }

    private boolean has(com.dropai.rewrite.mechanicalengine.cadcore.FeatureBasedCadSpec.Part part, String type) {
        return part.body().stream().anyMatch(feature -> type.equals(feature.featureType()));
    }
}
