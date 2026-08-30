package com.dropai.rewrite.service.ppt.rendering.renderability.v1;

import com.dropai.rewrite.service.ppt.rendering.contract.v1.enums.QualitySeverity;

import java.util.Comparator;
import java.util.List;

public record PageRenderabilityResult(boolean renderable, List<PageRenderabilityIssue> issues) {
    public PageRenderabilityResult {
        issues = issues == null ? List.of() : issues.stream()
                .sorted(Comparator.comparingInt(PageRenderabilityIssue::pageIndex)
                        .thenComparing(issue -> issue.qualityCode().code())
                        .thenComparing(issue -> issue.sourcePageId() == null ? "" : issue.sourcePageId())
                        .thenComparing(PageRenderabilityIssue::message))
                .toList();
        boolean hasErrors = issues.stream()
                .anyMatch(issue -> issue.qualityCode().defaultSeverity() == QualitySeverity.ERROR);
        renderable = !hasErrors;
    }

    public static PageRenderabilityResult of(List<PageRenderabilityIssue> issues) {
        return new PageRenderabilityResult(true, issues);
    }
}
