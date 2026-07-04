package com.dropai.rewrite.vo;

import java.math.BigDecimal;

public record RechargePlanVO(BigDecimal amount, Integer points, Boolean recommended) {
}
