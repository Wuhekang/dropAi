package com.dropai.rewrite.mechanicalengine.reasoning;

import com.dropai.rewrite.mechanicalengine.domain.MechanicalRequirementAnalysis;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

@Service
public class MechanicalRequirementReasoner {
    private final MechanicalAiGateway model;
    private final ObjectMapper mapper;

    public MechanicalRequirementReasoner(MechanicalAiGateway model, ObjectMapper mapper) {
        this.model = model; this.mapper = mapper;
    }

    public MechanicalRequirementAnalysis analyze(String requirement) {
        if (requirement == null || requirement.isBlank()) throw new IllegalArgumentException("INVALID_REQUIREMENT_ANALYSIS: empty requirement");
        if (!model.available()) throw new IllegalStateException("AI_REASONING_UNAVAILABLE");
        String instructions = """
                You are a mechanical requirement analyst. Analyze the requirement without designing geometry or parts.
                Return JSON only with exactly these fields: productDescription, productCategory, applicationScenario,
                coreFunctions, operatingEnvironment, performanceRequirements, mechanicalChallenges, requiredSystems, constraints.
                All fields are required; list fields must be non-empty. Infer no unsupported dimensions.
                """;
        try {
            MechanicalRequirementAnalysis result = mapper.readValue(json(model.generate(instructions, requirement)), MechanicalRequirementAnalysis.class);
            if (blank(result.productDescription()) || blank(result.productCategory()) || result.coreFunctions() == null
                    || result.coreFunctions().isEmpty() || result.requiredSystems() == null || result.requiredSystems().isEmpty()) {
                throw new IllegalArgumentException("required fields missing");
            }
            return result;
        } catch (Exception exception) {
            if (exception instanceof IllegalStateException state && state.getMessage().contains("AI_REASONING_UNAVAILABLE")) throw state;
            throw new IllegalArgumentException("INVALID_REQUIREMENT_ANALYSIS: " + compact(exception.getMessage()), exception);
        }
    }

    static String json(String value) {
        if (value == null) return "";
        return value.trim().replaceFirst("^```(?:json)?\\s*", "").replaceFirst("\\s*```$", "").trim();
    }
    private boolean blank(String value) { return value == null || value.isBlank(); }
    private String compact(String value) { return value == null ? "invalid model response" : value.replaceAll("\\s+", " "); }
}
