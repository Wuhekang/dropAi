package com.dropai.rewrite.service.ppt.rendering.measurement.v1;

import com.dropai.rewrite.service.ppt.rendering.contract.v1.enums.ImageFitMode;

import java.util.Objects;

public record ImageFitRequest(
        int sourceWidthPx,
        int sourceHeightPx,
        long targetXEmu,
        long targetYEmu,
        long targetWidthEmu,
        long targetHeightEmu,
        ImageFitMode fitMode,
        boolean cropAllowed
) {
    public ImageFitRequest {
        if (sourceWidthPx <= 0 || sourceHeightPx <= 0) {
            throw new IllegalArgumentException("source image dimensions must be positive");
        }
        if (targetXEmu < 0 || targetYEmu < 0 || targetWidthEmu <= 0 || targetHeightEmu <= 0) {
            throw new IllegalArgumentException("target geometry must be non-negative with positive dimensions");
        }
        Objects.requireNonNull(fitMode, "fitMode");
    }
}
