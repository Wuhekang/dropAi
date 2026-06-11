package com.dropai.rewrite.vo;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class DesignAnalysisVO {
    private String designType;
    private String summary;
    private Map<String, DesignParameterVO> parameters = new LinkedHashMap<>();
    private List<String> assumptions = List.of();
    private List<String> confirmations = List.of();

    public String getDesignType() { return designType; }
    public void setDesignType(String designType) { this.designType = designType; }
    public String getSummary() { return summary; }
    public void setSummary(String summary) { this.summary = summary; }
    public Map<String, DesignParameterVO> getParameters() { return parameters; }
    public void setParameters(Map<String, DesignParameterVO> parameters) { this.parameters = parameters; }
    public List<String> getAssumptions() { return assumptions; }
    public void setAssumptions(List<String> assumptions) { this.assumptions = assumptions; }
    public List<String> getConfirmations() { return confirmations; }
    public void setConfirmations(List<String> confirmations) { this.confirmations = confirmations; }
}
