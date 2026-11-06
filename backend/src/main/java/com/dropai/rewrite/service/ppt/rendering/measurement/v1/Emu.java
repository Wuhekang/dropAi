package com.dropai.rewrite.service.ppt.rendering.measurement.v1;

import java.math.BigDecimal;
import java.math.RoundingMode;

public final class Emu {
    public static final long PER_INCH = 914_400L;
    public static final long PER_POINT = 12_700L;

    private Emu() {
    }

    public static long fromInches(BigDecimal inches) {
        if (inches.signum() < 0) {
            throw new IllegalArgumentException("inches must not be negative");
        }
        return inches.multiply(BigDecimal.valueOf(PER_INCH))
                .setScale(0, RoundingMode.HALF_UP)
                .longValueExact();
    }

    public static long fromHundredthPoints(int hundredthPoints) {
        if (hundredthPoints < 0) {
            throw new IllegalArgumentException("hundredthPoints must not be negative");
        }
        return BigDecimal.valueOf(hundredthPoints)
                .multiply(BigDecimal.valueOf(PER_POINT))
                .divide(BigDecimal.valueOf(100), 0, RoundingMode.HALF_UP)
                .longValueExact();
    }

    public static long fromPoints(double points) {
        if (!Double.isFinite(points) || points < 0d) {
            throw new IllegalArgumentException("points must be a finite non-negative value");
        }
        return BigDecimal.valueOf(points)
                .multiply(BigDecimal.valueOf(PER_POINT))
                .setScale(0, RoundingMode.HALF_UP)
                .longValueExact();
    }

    public static long scale(long value, int numerator, int denominator) {
        if (value < 0 || numerator < 0 || denominator <= 0) {
            throw new IllegalArgumentException("scale arguments must be non-negative and denominator positive");
        }
        return BigDecimal.valueOf(value)
                .multiply(BigDecimal.valueOf(numerator))
                .divide(BigDecimal.valueOf(denominator), 0, RoundingMode.HALF_UP)
                .longValueExact();
    }
}
