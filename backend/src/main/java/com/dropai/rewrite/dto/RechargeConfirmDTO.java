package com.dropai.rewrite.dto;

import java.math.BigDecimal;

public class RechargeConfirmDTO {
    private String orderNo;
    private BigDecimal payAmount;
    private String payAccountLast4;
    private String proofImage;

    public String getOrderNo() { return orderNo; }
    public void setOrderNo(String orderNo) { this.orderNo = orderNo; }
    public BigDecimal getPayAmount() { return payAmount; }
    public void setPayAmount(BigDecimal payAmount) { this.payAmount = payAmount; }
    public String getPayAccountLast4() { return payAccountLast4; }
    public void setPayAccountLast4(String payAccountLast4) { this.payAccountLast4 = payAccountLast4; }
    public String getProofImage() { return proofImage; }
    public void setProofImage(String proofImage) { this.proofImage = proofImage; }
}
