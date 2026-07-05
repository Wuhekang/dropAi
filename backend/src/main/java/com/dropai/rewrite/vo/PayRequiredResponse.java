package com.dropai.rewrite.vo;

public record PayRequiredResponse(Integer requiredPoints, Integer currentPoints, Integer missingPoints) {
}
