package com.dropai.rewrite.service.ppt.rendering.measurement.v1;

import java.util.Objects;

public record TextFitRequest(
        String text,
        ResolvedFontProfile fontProfile,
        String fontRole,
        int fontWeight,
        int defaultFontSizeHundredthPt,
        int minimumFontSizeHundredthPt,
        int lineSpacingPermille,
        long maxWidthEmu,
        long maxHeightEmu,
        int maxLines
) {
    public TextFitRequest {
        Objects.requireNonNull(text, "text");
        Objects.requireNonNull(fontProfile, "fontProfile");
        Objects.requireNonNull(fontRole, "fontRole");
        if (text.isBlank()) {
            throw new IllegalArgumentException("text must not be blank");
        }
        if (fontRole.isBlank()) {
            throw new IllegalArgumentException("fontRole must not be blank");
        }
        if (fontWeight < 100 || fontWeight > 900 || fontWeight % 100 != 0) {
            throw new IllegalArgumentException("fontWeight must be from 100 to 900 in increments of 100");
        }
        if (defaultFontSizeHundredthPt <= 0
                || minimumFontSizeHundredthPt <= 0
                || defaultFontSizeHundredthPt < minimumFontSizeHundredthPt) {
            throw new IllegalArgumentException("font sizes must be positive and default must be at least minimum");
        }
        if (lineSpacingPermille < 1_000) {
            throw new IllegalArgumentException("lineSpacingPermille must be at least 1000");
        }
        if (maxWidthEmu <= 0 || maxHeightEmu <= 0 || maxLines <= 0) {
            throw new IllegalArgumentException("text bounds and maxLines must be positive");
        }
    }
}
