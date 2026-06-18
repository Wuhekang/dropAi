package com.dropai.rewrite.vo;

import java.util.List;

public record PointAccountVO(Integer points, Integer totalPoints, Integer usedPoints,
                             List<PointTransactionVO> recentTransactions) {
}
