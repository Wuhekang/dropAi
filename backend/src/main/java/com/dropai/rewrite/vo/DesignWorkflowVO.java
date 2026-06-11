package com.dropai.rewrite.vo;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class DesignWorkflowVO {
    private String workflowId;
    private String status;
    private String message;
    private List<DesignWorkflowStageVO> stages = new ArrayList<>();
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public String getWorkflowId() { return workflowId; }
    public void setWorkflowId(String workflowId) { this.workflowId = workflowId; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
    public List<DesignWorkflowStageVO> getStages() { return stages; }
    public void setStages(List<DesignWorkflowStageVO> stages) { this.stages = stages; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
