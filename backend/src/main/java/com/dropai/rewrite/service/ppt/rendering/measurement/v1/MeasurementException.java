package com.dropai.rewrite.service.ppt.rendering.measurement.v1;

import com.dropai.rewrite.service.ppt.rendering.contract.v1.PptQualityCode;

import java.util.Objects;

public final class MeasurementException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    private final PptQualityCode qualityCode;

    public MeasurementException(PptQualityCode qualityCode, String message) {
        super(message);
        this.qualityCode = Objects.requireNonNull(qualityCode, "qualityCode");
    }

    public MeasurementException(PptQualityCode qualityCode, String message, Throwable cause) {
        super(message, cause);
        this.qualityCode = Objects.requireNonNull(qualityCode, "qualityCode");
    }

    public PptQualityCode qualityCode() {
        return qualityCode;
    }
}
