package com.dropai.rewrite.service.ppt.rendering.compiler.v1;

import java.math.BigDecimal;
import java.math.RoundingMode;

final class Emu {
    static final BigDecimal PER_INCH = BigDecimal.valueOf(914_400L);
    static final BigDecimal PER_POINT = BigDecimal.valueOf(12_700L);

    private Emu() {
    }

    static long inches(BigDecimal value) {
        return value.multiply(PER_INCH).setScale(0, RoundingMode.HALF_UP).longValueExact();
    }

    static long points(BigDecimal value) {
        return value.multiply(PER_POINT).setScale(0, RoundingMode.HALF_UP).longValueExact();
    }

    static int hundredthPoints(BigDecimal value) {
        return value.multiply(BigDecimal.valueOf(100L))
                .setScale(0, RoundingMode.HALF_UP)
                .intValueExact();
    }

    static int permille(BigDecimal value) {
        return value.multiply(BigDecimal.valueOf(1000L))
                .setScale(0, RoundingMode.HALF_UP)
                .intValueExact();
    }
}
