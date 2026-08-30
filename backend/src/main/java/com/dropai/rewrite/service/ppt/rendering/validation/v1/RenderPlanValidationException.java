package com.dropai.rewrite.service.ppt.rendering.validation.v1;

import com.dropai.rewrite.service.ppt.rendering.plan.v1.RenderPlanIssue;

import java.util.List;

public final class RenderPlanValidationException extends IllegalStateException {
    private final List<RenderPlanIssue> issues;

    public RenderPlanValidationException(List<RenderPlanIssue> issues) {
        super("RenderPlan validation failed with " + issues.size() + " issue(s)");
        this.issues = List.copyOf(issues);
    }

    public List<RenderPlanIssue> issues() {
        return issues;
    }
}
