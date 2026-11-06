package com.dropai.rewrite.service.ppt.rendering.measurement.v1;

import com.dropai.rewrite.service.ppt.rendering.contract.v1.enums.ImageFitMode;

import java.util.Objects;
import java.util.Optional;

public record ImageFitResult(
        long xEmu,
        long yEmu,
        long widthEmu,
        long heightEmu,
        ImageFitMode fitMode,
        boolean cropAllowed,
        Optional<SourceCrop> sourceCrop
) {
    public ImageFitResult {
        if (xEmu < 0 || yEmu < 0 || widthEmu <= 0 || heightEmu <= 0) {
            throw new IllegalArgumentException("image geometry must be non-negative with positive dimensions");
        }
        Objects.requireNonNull(fitMode, "fitMode");
        sourceCrop = Objects.requireNonNull(sourceCrop, "sourceCrop");
        if (fitMode == ImageFitMode.CONTAIN && (cropAllowed || sourceCrop.isPresent())) {
            throw new IllegalArgumentException("CONTAIN must never crop");
        }
        if (fitMode == ImageFitMode.COVER && (!cropAllowed || sourceCrop.isEmpty())) {
            throw new IllegalArgumentException("COVER requires an explicit crop");
        }
    }
}
