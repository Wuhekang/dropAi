package com.dropai.rewrite.dto;

import java.math.BigDecimal;

public class RechargeOrderCreateDTO {
    private Long userId;
    private String planId;
    private BigDecimal amount;
    private String payMethod;

    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public String getPlanId() { return planId; }
    public void setPlanId(String planId) { this.planId = planId; }
    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }
    public String getPayMethod() { return payMethod; }
    public void setPayMethod(String payMethod) { this.payMethod = payMethod; }
}
