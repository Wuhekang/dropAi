package com.dropai.rewrite.mechanicalengine.domain;

import java.util.List;

public record DesignReviewReport(int score, List<Issue> issues, List<String> recommendations,
                                 boolean approval, String reviewerSource) {
    public record Issue(String severity, String category, String message, String relatedRequirement) {}
}
