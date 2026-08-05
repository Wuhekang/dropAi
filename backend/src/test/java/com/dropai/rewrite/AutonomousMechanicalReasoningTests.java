package com.dropai.rewrite;

import com.dropai.rewrite.mechanicalengine.cadcore.MechanicalDesignCadConverter;
import com.dropai.rewrite.mechanicalengine.domain.MechanicalDesignSpec;
import com.dropai.rewrite.mechanicalengine.domain.MechanicalRequirementAnalysis;
import com.dropai.rewrite.mechanicalengine.knowledge.ProductFamilyKnowledgeRepository;
import com.dropai.rewrite.mechanicalengine.productplanner.AgvProductPlanner;
import com.dropai.rewrite.mechanicalengine.productplanner.ConveyorProductPlanner;
import com.dropai.rewrite.mechanicalengine.productplanner.MechanismProductPlanner;
import com.dropai.rewrite.mechanicalengine.productplanner.RobotProductPlanner;
import com.dropai.rewrite.mechanicalengine.reasoning.AutonomousMechanicalChiefEngineer;
import com.dropai.rewrite.mechanicalengine.reasoning.MechanicalAiGateway;
import com.dropai.rewrite.mechanicalengine.reasoning.MechanicalRequirementReasoner;
import com.dropai.rewrite.mechanicalengine.service.MechanicalChiefEngineer;
import com.dropai.rewrite.mechanicalengine.service.MechanicalDesignPlanner;
import com.dropai.rewrite.mechanicalengine.service.MechanicalDesignQualityValidator;
import com.dropai.rewrite.mechanicalengine.validation.ArchitectureReviewValidator;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.ArrayDeque;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class AutonomousMechanicalReasoningTests {
    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();

    @Test
    void productionReasoningCreatesDifferentArchitecturesAcrossFamilies() throws Exception {
        List<Case> cases = List.of(
                new Case("油罐检测爬壁机器人", "robot", new RobotProductPlanner().plan("油罐爬壁履带磁吸机器人")),
                new Case("自动夹紧装置", "fixture", new MechanicalDesignPlanner().plan("automatic clamp")),
                new Case("AGV运输车", "agv", new AgvProductPlanner().plan("AGV")),
                new Case("带式输送机", "conveyor", new ConveyorProductPlanner().plan("conveyor")),
                new Case("六轴机械臂", "mechanism", new MechanismProductPlanner().plan("mechanism")));

        java.util.Set<String> signatures = new java.util.HashSet<>();
        for (Case item : cases) {
            MechanicalDesignSpec aiDesign = withAiProvenance(item.design());
            MechanicalRequirementAnalysis analysis = analysis(item.category(), aiDesign);
            ScriptedGateway gateway = new ScriptedGateway(mapper.writeValueAsString(analysis), mapper.writeValueAsString(aiDesign));
            MechanicalChiefEngineer chief = productionChief(gateway);
            var project = chief.design(item.requirement());
            assertEquals("AI", project.getDesignSpec().provenance().reasoningSource());
            assertNotNull(project.getRequirementAnalysis());
            assertFalse(project.getParts().isEmpty());
            signatures.add(project.getDesignSpec().modules().stream().map(MechanicalDesignSpec.Module::name).sorted().toList().toString());
        }
        assertEquals(cases.size(), signatures.size(), "Each product family must produce a different module architecture");
    }

    @Test
    void unavailableAiFailsWithoutPlannerFallback() {
        MechanicalRequirementReasoner reasoner = new MechanicalRequirementReasoner(new UnavailableGateway(), mapper);
        IllegalStateException error = assertThrows(IllegalStateException.class, () -> reasoner.analyze("unknown machine"));
        assertEquals("AI_REASONING_UNAVAILABLE", error.getMessage());
    }

    @Test
    void invalidAnalysisIsVisibleAndDoesNotReachDesign() {
        MechanicalRequirementReasoner reasoner = new MechanicalRequirementReasoner(new ScriptedGateway("not-json"), mapper);
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class, () -> reasoner.analyze("unknown machine"));
        assertTrue(error.getMessage().startsWith("INVALID_REQUIREMENT_ANALYSIS"));
    }

    @Test
    void modelTransportFailureIsReportedAsUnavailable() {
        MechanicalAiGateway failing = new MechanicalAiGateway() {
            public String generate(String instructions, String input) { throw new IllegalStateException("timeout"); }
            public boolean available() { return true; }
        };
        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> new MechanicalRequirementReasoner(failing, mapper).analyze("custom lifting machine"));
        assertTrue(error.getMessage().startsWith("AI_REASONING_UNAVAILABLE"));
    }

    @Test
    void architectureReviewStopsMissingRequiredSystemBeforeCad() {
        MechanicalDesignSpec design = withAiProvenance(new AgvProductPlanner().plan("AGV"));
        MechanicalRequirementAnalysis analysis = new MechanicalRequirementAnalysis("adhesive inspection robot", "robot", "wall",
                List.of("inspect"), List.of("vertical steel wall"), List.of("stable motion"), List.of("adhesion"),
                List.of("permanent magnet adhesion system"), List.of("no fall"));
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> new ArchitectureReviewValidator().validate(analysis, design));
        assertTrue(error.getMessage().startsWith("ARCHITECTURE_REVIEW_FAILED"));
    }

    private MechanicalChiefEngineer productionChief(MechanicalAiGateway gateway) {
        var reasoner = new MechanicalRequirementReasoner(gateway, mapper);
        var autonomous = new AutonomousMechanicalChiefEngineer(gateway, mapper, new ProductFamilyKnowledgeRepository(mapper));
        return new MechanicalChiefEngineer(reasoner, autonomous, new ArchitectureReviewValidator(),
                new MechanicalDesignQualityValidator(), new MechanicalDesignCadConverter());
    }

    private MechanicalRequirementAnalysis analysis(String category, MechanicalDesignSpec design) {
        List<String> systems = design.modules().stream().map(MechanicalDesignSpec.Module::name).toList();
        return new MechanicalRequirementAnalysis(design.product().purpose(), category, design.product().environment(),
                design.product().coreFunctions(), List.of(design.product().environment()), design.requirements().performanceGoals(),
                List.of("load path", "manufacturability"), systems, design.requirements().engineeringConstraints());
    }

    private MechanicalDesignSpec withAiProvenance(MechanicalDesignSpec source) {
        return new MechanicalDesignSpec(source.product(), source.requirements(), source.functions(), source.architecture(),
                source.modules(), source.parts(), source.assemblyIntent(), source.parameters(), source.materials(), source.manufacturing(),
                new MechanicalDesignSpec.DesignProvenance("AI", List.of(source.product().type()),
                        List.of("Compared candidate mechanisms", "Selected architecture against functional constraints")));
    }

    private record Case(String requirement, String category, MechanicalDesignSpec design) {}
    private static class ScriptedGateway implements MechanicalAiGateway {
        private final ArrayDeque<String> responses = new ArrayDeque<>();
        ScriptedGateway(String... values) { responses.addAll(List.of(values)); }
        public String generate(String instructions, String input) { return responses.removeFirst(); }
        public boolean available() { return true; }
    }
    private static class UnavailableGateway implements MechanicalAiGateway {
        public String generate(String instructions, String input) { throw new AssertionError("must not call unavailable model"); }
        public boolean available() { return false; }
    }
}
