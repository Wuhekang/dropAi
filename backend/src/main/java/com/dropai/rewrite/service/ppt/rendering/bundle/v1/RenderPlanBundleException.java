package com.dropai.rewrite.service.ppt.rendering.bundle.v1;

import com.dropai.rewrite.service.ppt.rendering.contract.v1.PptQualityCode;

import java.util.Objects;

/** Fail-fast bundle publication or loading error. */
public final class RenderPlanBundleException extends RuntimeException {
    private final PptQualityCode qualityCode;

    public RenderPlanBundleException(PptQualityCode qualityCode, String message) {
        this(qualityCode, message, null);
    }

    public RenderPlanBundleException(PptQualityCode qualityCode, String message, Throwable cause) {
        super(Objects.requireNonNull(message, "message"), cause);
        this.qualityCode = Objects.requireNonNull(qualityCode, "qualityCode");
    }

    public PptQualityCode qualityCode() {
        return qualityCode;
    }
}
