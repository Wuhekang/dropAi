package com.dropai.rewrite.vo;

import com.dropai.rewrite.entity.RechargeOrder;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record RechargeOrderVO(Long id, String orderNo, BigDecimal amount, Integer points,
                              String status, String payMethod, LocalDateTime createdAt, LocalDateTime paidAt,
                              String paymentUrl) {
    public static RechargeOrderVO of(RechargeOrder order) {
        return new RechargeOrderVO(order.getId(), order.getOrderNo(), order.getAmount(), order.getPoints(),
                order.getStatus(), order.getPayMethod(), order.getCreatedAt(), order.getPaidAt(),
                "/api/recharge/orders/" + order.getOrderNo() + "/mock-pay");
    }
}
