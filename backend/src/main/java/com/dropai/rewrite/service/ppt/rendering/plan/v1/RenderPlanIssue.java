package com.dropai.rewrite.service.ppt.rendering.plan.v1;

import com.dropai.rewrite.service.ppt.rendering.contract.v1.PptQualityCode;
import com.dropai.rewrite.service.ppt.rendering.contract.v1.enums.QualitySeverity;
import com.dropai.rewrite.service.ppt.rendering.contract.v1.enums.QualityStage;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

/** A stable, non-mutating validation finding. */
public record RenderPlanIssue(
        PptQualityCode qualityCode,
        String slideId,
        String elementId,
        String message,
        Map<String, Object> metrics
) {
    public RenderPlanIssue {
        qualityCode = Objects.requireNonNull(qualityCode, "qualityCode");
        message = requireText(message, "message");
        slideId = normalizeOptional(slideId);
        elementId = normalizeOptional(elementId);
        metrics = immutableMetrics(metrics);
    }

    public QualitySeverity severity() {
        return qualityCode.defaultSeverity();
    }

    public QualityStage stage() {
        return qualityCode.defaultStage();
    }

    public String code() {
        return qualityCode.code();
    }

    private static Map<String, Object> immutableMetrics(Map<String, Object> source) {
        if (source == null || source.isEmpty()) {
            return Map.of();
        }
        TreeMap<String, Object> sorted = new TreeMap<>();
        source.forEach((key, value) -> {
            String normalizedKey = requireText(key, "metric key");
            if (!(value instanceof String || value instanceof Number || value instanceof Boolean)) {
                throw new IllegalArgumentException("Metric values must be scalar: " + normalizedKey);
            }
            sorted.put(normalizedKey, value);
        });
        return Collections.unmodifiableMap(new LinkedHashMap<>(sorted));
    }

    private static String normalizeOptional(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private static String requireText(String value, String name) {
        Objects.requireNonNull(value, name);
        if (value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value;
    }
}
