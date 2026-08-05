package com.dropai.rewrite.mechanicalengine.reasoning;

import com.dropai.rewrite.mechanicalengine.domain.DesignReviewReport;
import com.dropai.rewrite.mechanicalengine.domain.MechanicalDesignSpec;
import com.dropai.rewrite.mechanicalengine.domain.MechanicalRequirementAnalysis;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;

@Service
public class MechanicalDesignCritic {
    private final MechanicalAiGateway model;
    private final ObjectMapper mapper;

    public MechanicalDesignCritic(MechanicalAiGateway model, ObjectMapper mapper) {
        this.model = model; this.mapper = mapper;
    }

    public DesignReviewReport review(MechanicalRequirementAnalysis analysis, MechanicalDesignSpec design) {
        List<DesignReviewReport.Issue> mandatory = deterministicIssues(analysis, design);
        if (!model.available()) throw new IllegalStateException("AI_REASONING_UNAVAILABLE: mechanical design critic");
        String instructions = """
                Act as a mechanical chief engineer with more than twenty years of design-review experience.
                Review requirement coverage, system completeness, mechanism suitability, load and material compatibility,
                part purpose and manufacturability, assembly feasibility, maintenance access, and safety.
                Return JSON only: {score,issues:[{severity,category,message,relatedRequirement}],recommendations,approval,reviewerSource}.
                score is 0-100; severity is CRITICAL, MAJOR, or MINOR; reviewerSource must be AI_CRITIC.
                approval must be false for any CRITICAL issue, missing required system, unsafe mechanism, or impossible assembly.
                Do not redesign the product and do not output markdown.
                """;
        DesignReviewReport ai;
        try {
            String input = mapper.writeValueAsString(java.util.Map.of("requirementAnalysis", analysis, "mechanicalDesign", design));
            String response;
            try { response = model.generate(instructions, input); }
            catch (Exception exception) { throw new IllegalStateException("AI_REASONING_UNAVAILABLE: critic request failed", exception); }
            ai = mapper.readValue(MechanicalRequirementReasoner.json(response), DesignReviewReport.class);
        } catch (IllegalStateException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IllegalArgumentException("INVALID_DESIGN_REVIEW: " + compact(exception.getMessage()), exception);
        }
        if (ai.issues() == null || ai.recommendations() == null || !"AI_CRITIC".equals(ai.reviewerSource())
                || ai.score() < 0 || ai.score() > 100) {
            throw new IllegalArgumentException("INVALID_DESIGN_REVIEW: incomplete critic report");
        }
        List<DesignReviewReport.Issue> issues = new ArrayList<>(mandatory);
        issues.addAll(ai.issues());
        boolean blocking = issues.stream().anyMatch(issue -> "CRITICAL".equalsIgnoreCase(issue.severity()));
        int score = mandatory.isEmpty() ? ai.score() : Math.min(ai.score(), Math.max(0, 60 - mandatory.size() * 10));
        return new DesignReviewReport(score, List.copyOf(issues), ai.recommendations(), ai.approval() && !blocking && score >= 70, "AI_CRITIC");
    }

    private List<DesignReviewReport.Issue> deterministicIssues(MechanicalRequirementAnalysis analysis, MechanicalDesignSpec design) {
        List<DesignReviewReport.Issue> issues = new ArrayList<>();
        String architecture = (design.modules() == null ? "" : design.modules().stream()
                .map(module -> module.name() + " " + module.function()).reduce("", (a,b) -> a + " " + b)).toLowerCase(Locale.ROOT);
        for (String system : analysis.requiredSystems()) {
            if (!architecture.contains(system.toLowerCase(Locale.ROOT))) issues.add(issue("CRITICAL", "REQUIREMENT_COVERAGE",
                    "Required mechanical system is missing: " + system, system));
        }
        HashSet<String> names = new HashSet<>();
        for (MechanicalDesignSpec.PartPlan part : design.parts()) {
            if (!names.add(part.name())) issues.add(issue("CRITICAL", "PART_PLAN", "Duplicate part name: " + part.name(), "unique parts"));
            if (blank(part.function()) || blank(part.material()) || blank(part.manufacturing()))
                issues.add(issue("CRITICAL", "MANUFACTURABILITY", "Incomplete part engineering definition: " + part.name(), part.function()));
        }
        if (design.assemblyIntent() == null || design.assemblyIntent().size() < Math.max(1, design.parts().size() - 1))
            issues.add(issue("CRITICAL", "ASSEMBLY", "Assembly relationships do not connect the planned parts", "complete assembly"));
        return issues;
    }

    private DesignReviewReport.Issue issue(String severity, String category, String message, String requirement) {
        return new DesignReviewReport.Issue(severity, category, message, requirement);
    }
    private boolean blank(String value) { return value == null || value.isBlank(); }
    private String compact(String value) { return value == null ? "invalid response" : value.replaceAll("\\s+", " "); }
}
