package com.dropai.rewrite.dto;

public class FeaturePricingUpdateDTO {
    private Integer costPoints;
    private Boolean enabled;

    public Integer getCostPoints() { return costPoints; }
    public void setCostPoints(Integer costPoints) { this.costPoints = costPoints; }
    public Boolean getEnabled() { return enabled; }
    public void setEnabled(Boolean enabled) { this.enabled = enabled; }
}
