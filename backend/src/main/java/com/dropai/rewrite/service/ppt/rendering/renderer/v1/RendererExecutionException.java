package com.dropai.rewrite.service.ppt.rendering.renderer.v1;

import com.dropai.rewrite.service.ppt.rendering.contract.v1.PptQualityCode;

import java.util.Objects;

/** Fail-fast execution error; the Renderer never repairs or substitutes a failed element. */
public final class RendererExecutionException extends RuntimeException {
    private final PptQualityCode qualityCode;
    private final String slideId;
    private final String elementId;

    public RendererExecutionException(
            PptQualityCode qualityCode,
            String message,
            String slideId,
            String elementId
    ) {
        this(qualityCode, message, slideId, elementId, null);
    }

    public RendererExecutionException(
            PptQualityCode qualityCode,
            String message,
            String slideId,
            String elementId,
            Throwable cause
    ) {
        super(Objects.requireNonNull(message, "message"), cause);
        this.qualityCode = Objects.requireNonNull(qualityCode, "qualityCode");
        this.slideId = slideId;
        this.elementId = elementId;
    }

    public PptQualityCode qualityCode() {
        return qualityCode;
    }

    public String slideId() {
        return slideId;
    }

    public String elementId() {
        return elementId;
    }
}
