package com.dropai.rewrite.service.ppt.rendering.theme.v1;

import com.dropai.rewrite.service.ppt.rendering.contract.v1.PptQualityCode;
import com.dropai.rewrite.service.ppt.rendering.contract.v1.enums.QualityStage;

import java.util.List;
import java.util.Objects;

public final class ThemeValidationException extends IllegalArgumentException {
    private final PptQualityCode qualityCode;
    private final List<String> violations;

    public ThemeValidationException(PptQualityCode qualityCode, String violation) {
        this(qualityCode, List.of(violation));
    }

    public ThemeValidationException(PptQualityCode qualityCode, List<String> violations) {
        super(message(qualityCode, violations));
        this.qualityCode = Objects.requireNonNull(qualityCode, "qualityCode");
        this.violations = List.copyOf(violations);
        if (this.violations.isEmpty()) {
            throw new IllegalArgumentException("Theme validation violations must not be empty");
        }
    }

    public PptQualityCode qualityCode() {
        return qualityCode;
    }

    public List<String> violations() {
        return violations;
    }

    public QualityStage stage() {
        return QualityStage.THEME_RESOLUTION;
    }

    private static String message(PptQualityCode qualityCode, List<String> violations) {
        Objects.requireNonNull(qualityCode, "qualityCode");
        Objects.requireNonNull(violations, "violations");
        return qualityCode.code() + " theme validation failed: " + String.join("; ", violations);
    }
}
