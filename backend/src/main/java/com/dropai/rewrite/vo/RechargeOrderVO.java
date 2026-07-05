package com.dropai.rewrite.vo;

import com.dropai.rewrite.entity.RechargeOrder;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record RechargeOrderVO(Long id, String orderNo, BigDecimal amount, Integer points,
                              String status, String payMethod, BigDecimal payAmount, String payAccountLast4,
                              String proofImage, LocalDateTime createdAt, LocalDateTime updatedAt,
                              LocalDateTime paidAt, LocalDateTime auditedAt,
                              String payQrUrl, String paymentUrl) {
    public static RechargeOrderVO of(RechargeOrder order) {
        return of(order, "/api/recharge/orders/" + order.getOrderNo() + "/mock-pay");
    }

    public static RechargeOrderVO of(RechargeOrder order, String paymentUrl) {
        return new RechargeOrderVO(order.getId(), order.getOrderNo(), order.getAmount(), order.getPoints(),
                order.getStatus(), order.getPayMethod(), order.getPayAmount(), order.getPayAccountLast4(),
                order.getProofImage(), order.getCreatedAt(), order.getUpdatedAt(), order.getPaidAt(), order.getAuditedAt(),
                "/static/alipay-qrcode.png",
                paymentUrl);
    }
}
