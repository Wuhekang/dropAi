package com.dropai.rewrite.mechanicalengine.reasoning;

import com.dropai.rewrite.mechanicalengine.domain.MechanicalDesignSpec;
import com.dropai.rewrite.mechanicalengine.domain.MechanicalRequirementAnalysis;
import com.dropai.rewrite.mechanicalengine.knowledge.ProductFamilyKnowledge;
import com.dropai.rewrite.mechanicalengine.knowledge.ProductFamilyKnowledgeRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

@Service
public class AutonomousMechanicalChiefEngineer {
    private final MechanicalAiGateway model;
    private final ObjectMapper mapper;
    private final ProductFamilyKnowledgeRepository knowledgeRepository;

    public AutonomousMechanicalChiefEngineer(MechanicalAiGateway model, ObjectMapper mapper,
                                             ProductFamilyKnowledgeRepository knowledgeRepository) {
        this.model = model; this.mapper = mapper; this.knowledgeRepository = knowledgeRepository;
    }

    public MechanicalDesignSpec design(MechanicalRequirementAnalysis analysis) {
        if (!model.available()) throw new IllegalStateException("AI_REASONING_UNAVAILABLE");
        ProductFamilyKnowledge knowledge = knowledgeRepository.findFor(analysis);
        String instructions = """
                You are an autonomous senior mechanical chief engineer. Produce one manufacturable MechanicalDesignSpec as JSON only.
                Execute functional decomposition, required-system identification, candidate mechanism comparison, concept selection,
                module planning, part planning, assembly intent, and engineering parameter sizing. Product-family knowledge is advisory,
                never a template. Every required system must be covered. Every part needs a unique purpose, material, process, ordered
                feature requirements, and assembly relationship. Features may only be SKETCH, PAD, POCKET, HOLE, FILLET, CHAMFER.
                Assembly types may only be FIXED, FASTENED, COINCIDENT, CONCENTRIC, DISTANCE, ANGLE, SLIDER.
                Return fields: product, requirements, functions, architecture, modules, parts, assemblyIntent, parameters, materials,
                manufacturing, provenance. provenance.reasoningSource must be AI; include knowledgeReferences and architectureDecisions.
                Do not reuse a fixed product structure and do not output markdown.
                """;
        try {
            String input = mapper.writeValueAsString(java.util.Map.of("analysis", analysis, "advisoryKnowledge", knowledge));
            MechanicalDesignSpec design = mapper.readValue(MechanicalRequirementReasoner.json(model.generate(instructions, input)), MechanicalDesignSpec.class);
            if (design.provenance() == null || !"AI".equals(design.provenance().reasoningSource())) {
                throw new IllegalArgumentException("missing AI provenance");
            }
            return design;
        } catch (Exception exception) {
            throw new IllegalArgumentException("INVALID_MECHANICAL_DESIGN: " + compact(exception.getMessage()), exception);
        }
    }

    private String compact(String value) { return value == null ? "invalid model response" : value.replaceAll("\\s+", " "); }
}
