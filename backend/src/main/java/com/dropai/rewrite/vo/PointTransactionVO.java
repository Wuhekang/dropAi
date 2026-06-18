package com.dropai.rewrite.vo;

import com.dropai.rewrite.entity.PointTransaction;

import java.time.LocalDateTime;

public record PointTransactionVO(Long id, String featureCode, String featureName, Integer pointsChange,
                                 Integer balanceAfter, String remark, LocalDateTime createdAt) {
    public static PointTransactionVO of(PointTransaction item) {
        return new PointTransactionVO(item.getId(), item.getFeatureCode(), item.getFeatureName(),
                item.getPointsChange(), item.getBalanceAfter(), item.getRemark(), item.getCreatedAt());
    }
}
