package com.dropai.rewrite.service.ppt.rendering.compiler.v1;

import com.dropai.rewrite.service.ppt.rendering.contract.v1.PptQualityCode;

import java.util.Objects;

public final class RenderPlanCompilationException extends RuntimeException {
    private final PptQualityCode qualityCode;

    public RenderPlanCompilationException(PptQualityCode qualityCode, String message) {
        super(message);
        this.qualityCode = Objects.requireNonNull(qualityCode, "qualityCode");
    }

    public RenderPlanCompilationException(PptQualityCode qualityCode, String message, Throwable cause) {
        super(message, cause);
        this.qualityCode = Objects.requireNonNull(qualityCode, "qualityCode");
    }

    public PptQualityCode qualityCode() {
        return qualityCode;
    }
}
