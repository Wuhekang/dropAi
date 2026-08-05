package com.dropai.rewrite.mechanicalengine.domain;

import java.util.List;

public record MechanicalRequirementAnalysis(
        String productDescription,
        String productCategory,
        String applicationScenario,
        List<String> coreFunctions,
        List<String> operatingEnvironment,
        List<String> performanceRequirements,
        List<String> mechanicalChallenges,
        List<String> requiredSystems,
        List<String> constraints
) {}
