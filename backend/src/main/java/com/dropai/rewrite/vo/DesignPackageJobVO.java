package com.dropai.rewrite.vo;

import java.time.LocalDateTime;

public class DesignPackageJobVO {
    private String jobId;
    private String status;
    private String stage;
    private Integer progress;
    private String message;
    private String errorCode;
    private LocalDateTime createdAt;
    private LocalDateTime heartbeatAt;
    private LocalDateTime finishedAt;
    private Integer costPoints;
    private Boolean pointsCharged;
    private DesignPackageVO result;

    public String getJobId() { return jobId; }
    public void setJobId(String jobId) { this.jobId = jobId; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getStage() { return stage; }
    public void setStage(String stage) { this.stage = stage; }
    public Integer getProgress() { return progress; }
    public void setProgress(Integer progress) { this.progress = progress; }
    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
    public String getErrorCode() { return errorCode; }
    public void setErrorCode(String errorCode) { this.errorCode = errorCode; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getHeartbeatAt() { return heartbeatAt; }
    public void setHeartbeatAt(LocalDateTime heartbeatAt) { this.heartbeatAt = heartbeatAt; }
    public LocalDateTime getFinishedAt() { return finishedAt; }
    public void setFinishedAt(LocalDateTime finishedAt) { this.finishedAt = finishedAt; }
    public Integer getCostPoints() { return costPoints; }
    public void setCostPoints(Integer costPoints) { this.costPoints = costPoints; }
    public Boolean getPointsCharged() { return pointsCharged; }
    public void setPointsCharged(Boolean pointsCharged) { this.pointsCharged = pointsCharged; }
    public DesignPackageVO getResult() { return result; }
    public void setResult(DesignPackageVO result) { this.result = result; }
}
