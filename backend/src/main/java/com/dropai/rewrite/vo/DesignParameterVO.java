package com.dropai.rewrite.vo;

public class DesignParameterVO {
    private double value;
    private String unit;
    private String source;
    private String status;
    private String basis;

    public double getValue() { return value; }
    public void setValue(double value) { this.value = value; }
    public String getUnit() { return unit; }
    public void setUnit(String unit) { this.unit = unit; }
    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getBasis() { return basis; }
    public void setBasis(String basis) { this.basis = basis; }
}
