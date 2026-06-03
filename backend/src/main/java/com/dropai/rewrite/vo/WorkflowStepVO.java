package com.dropai.rewrite.vo;

public class WorkflowStepVO {

    private String nodeType;
    private String nodeName;
    private String summary;

    public WorkflowStepVO() {
    }

    public WorkflowStepVO(String nodeType, String nodeName, String summary) {
        this.nodeType = nodeType;
        this.nodeName = nodeName;
        this.summary = summary;
    }

    public String getNodeType() {
        return nodeType;
    }

    public void setNodeType(String nodeType) {
        this.nodeType = nodeType;
    }

    public String getNodeName() {
        return nodeName;
    }

    public void setNodeName(String nodeName) {
        this.nodeName = nodeName;
    }

    public String getSummary() {
        return summary;
    }

    public void setSummary(String summary) {
        this.summary = summary;
    }
}
