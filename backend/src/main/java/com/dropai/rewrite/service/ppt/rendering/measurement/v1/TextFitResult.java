package com.dropai.rewrite.service.ppt.rendering.measurement.v1;

import java.util.List;
import java.util.Objects;

public record TextFitResult(
        TextFitStatus status,
        String sourceText,
        String renderedText,
        List<String> lines,
        String fontFamily,
        String fontFingerprint,
        int fontSizeHundredthPt,
        int lineSpacingPermille,
        long lineHeightEmu,
        long requiredWidthEmu,
        long requiredHeightEmu,
        String failureReason
) {
    public TextFitResult {
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(sourceText, "sourceText");
        Objects.requireNonNull(renderedText, "renderedText");
        lines = List.copyOf(lines);
        Objects.requireNonNull(fontFamily, "fontFamily");
        Objects.requireNonNull(fontFingerprint, "fontFingerprint");
        Objects.requireNonNull(failureReason, "failureReason");
        if (lineHeightEmu < 0 || requiredWidthEmu < 0 || requiredHeightEmu < 0) {
            throw new IllegalArgumentException("required dimensions must not be negative");
        }
    }

    public boolean fits() {
        return status != TextFitStatus.UNFIT;
    }
}
