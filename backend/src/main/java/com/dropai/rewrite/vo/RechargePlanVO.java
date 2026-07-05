package com.dropai.rewrite.vo;

import java.math.BigDecimal;

public record RechargePlanVO(String planId, BigDecimal amount, Integer points, Boolean recommended) {
}
