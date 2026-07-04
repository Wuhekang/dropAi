package com.dropai.rewrite.dto;

import java.math.BigDecimal;

public class RechargeOrderCreateDTO {
    private BigDecimal amount;
    private String payMethod;

    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }
    public String getPayMethod() { return payMethod; }
    public void setPayMethod(String payMethod) { this.payMethod = payMethod; }
}
