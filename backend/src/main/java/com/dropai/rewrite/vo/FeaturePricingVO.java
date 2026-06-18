package com.dropai.rewrite.vo;

import com.dropai.rewrite.entity.FeaturePricing;

public record FeaturePricingVO(Long id, String featureCode, String featureName, Integer costPoints, Boolean enabled) {
    public static FeaturePricingVO of(FeaturePricing item) {
        return new FeaturePricingVO(item.getId(), item.getFeatureCode(), item.getFeatureName(),
                item.getCostPoints(), item.getEnabled());
    }
}
