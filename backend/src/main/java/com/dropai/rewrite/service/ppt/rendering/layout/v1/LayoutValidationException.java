package com.dropai.rewrite.service.ppt.rendering.layout.v1;

import com.dropai.rewrite.service.ppt.rendering.contract.v1.PptQualityCode;

import java.util.List;
import java.util.Objects;

public final class LayoutValidationException extends RuntimeException {
    private final PptQualityCode qualityCode;
    private final List<String> violations;

    public LayoutValidationException(PptQualityCode qualityCode, String message) {
        this(qualityCode, List.of(message));
    }

    public LayoutValidationException(PptQualityCode qualityCode, List<String> violations) {
        super(String.join("; ", violations));
        this.qualityCode = Objects.requireNonNull(qualityCode, "qualityCode");
        this.violations = List.copyOf(violations);
    }

    public PptQualityCode qualityCode() {
        return qualityCode;
    }

    public List<String> violations() {
        return violations;
    }
}
