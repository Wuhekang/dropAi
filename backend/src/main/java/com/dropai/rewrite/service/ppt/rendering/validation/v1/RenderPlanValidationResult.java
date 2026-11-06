package com.dropai.rewrite.service.ppt.rendering.validation.v1;

import com.dropai.rewrite.service.ppt.rendering.contract.v1.enums.QualitySeverity;
import com.dropai.rewrite.service.ppt.rendering.plan.v1.RenderPlanIssue;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;

public final class RenderPlanValidationResult {
    private final List<RenderPlanIssue> issues;
    private final ObjectNode validatedDocument;

    RenderPlanValidationResult(List<RenderPlanIssue> issues, JsonNode validatedDocument) {
        Objects.requireNonNull(validatedDocument, "validatedDocument");
        if (!validatedDocument.isObject()) {
            throw new IllegalArgumentException("Validated RenderPlan candidate must be a JSON object");
        }
        this.issues = (issues == null ? List.<RenderPlanIssue>of() : issues).stream()
                .sorted(Comparator
                        .comparing((RenderPlanIssue issue) -> issue.slideId() == null ? "" : issue.slideId())
                        .thenComparing(issue -> issue.elementId() == null ? "" : issue.elementId())
                        .thenComparing(RenderPlanIssue::code)
                .thenComparing(RenderPlanIssue::message))
                .toList();
        this.validatedDocument = ((ObjectNode) validatedDocument).deepCopy();
    }

    public List<RenderPlanIssue> issues() {
        return issues;
    }

    public boolean valid() {
        return issues.stream().noneMatch(issue -> issue.severity() == QualitySeverity.ERROR);
    }

    public ValidatedSlideRenderPlan accept() {
        if (!valid()) {
            throw new RenderPlanValidationException(issues);
        }
        return new ValidatedSlideRenderPlan(validatedDocument);
    }
}
