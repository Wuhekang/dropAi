package com.dropai.rewrite.service.ppt.rendering.renderability.v1;

import com.dropai.rewrite.service.ppt.rendering.contract.v1.PptQualityCode;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

public record PageRenderabilityIssue(
        PptQualityCode qualityCode,
        int pageIndex,
        String sourcePageId,
        String message,
        Map<String, Object> metrics
) {
    public PageRenderabilityIssue {
        qualityCode = Objects.requireNonNull(qualityCode, "qualityCode");
        if (pageIndex < 0) {
            throw new IllegalArgumentException("pageIndex must be non-negative");
        }
        sourcePageId = sourcePageId == null || sourcePageId.isBlank() ? null : sourcePageId;
        Objects.requireNonNull(message, "message");
        if (message.isBlank()) {
            throw new IllegalArgumentException("message must not be blank");
        }
        if (metrics == null || metrics.isEmpty()) {
            metrics = Map.of();
        } else {
            TreeMap<String, Object> sorted = new TreeMap<>(metrics);
            metrics = Collections.unmodifiableMap(new LinkedHashMap<>(sorted));
        }
    }
}
