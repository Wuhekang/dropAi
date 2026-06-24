package com.dropai.rewrite.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;

@TableName("computer_generation_jobs")
public class ComputerGenerationJob {
    @TableId
    private String id;
    private Long userId;
    private String title;
    private String projectType;
    private String techStack;
    private String status;
    private Integer progress;
    private String currentStage;
    private String currentFile;
    private String inputText;
    private String uploadedFiles;
    private String outputZipPath;
    private String frontendPath;
    private String backendPath;
    private String sqlPath;
    private String paperPath;
    private String previewUrl;
    private String errorMessage;
    private Integer pointsCost;
    private Boolean pointsCharged;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getProjectType() { return projectType; }
    public void setProjectType(String projectType) { this.projectType = projectType; }
    public String getTechStack() { return techStack; }
    public void setTechStack(String techStack) { this.techStack = techStack; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Integer getProgress() { return progress; }
    public void setProgress(Integer progress) { this.progress = progress; }
    public String getCurrentStage() { return currentStage; }
    public void setCurrentStage(String currentStage) { this.currentStage = currentStage; }
    public String getCurrentFile() { return currentFile; }
    public void setCurrentFile(String currentFile) { this.currentFile = currentFile; }
    public String getInputText() { return inputText; }
    public void setInputText(String inputText) { this.inputText = inputText; }
    public String getUploadedFiles() { return uploadedFiles; }
    public void setUploadedFiles(String uploadedFiles) { this.uploadedFiles = uploadedFiles; }
    public String getOutputZipPath() { return outputZipPath; }
    public void setOutputZipPath(String outputZipPath) { this.outputZipPath = outputZipPath; }
    public String getFrontendPath() { return frontendPath; }
    public void setFrontendPath(String frontendPath) { this.frontendPath = frontendPath; }
    public String getBackendPath() { return backendPath; }
    public void setBackendPath(String backendPath) { this.backendPath = backendPath; }
    public String getSqlPath() { return sqlPath; }
    public void setSqlPath(String sqlPath) { this.sqlPath = sqlPath; }
    public String getPaperPath() { return paperPath; }
    public void setPaperPath(String paperPath) { this.paperPath = paperPath; }
    public String getPreviewUrl() { return previewUrl; }
    public void setPreviewUrl(String previewUrl) { this.previewUrl = previewUrl; }
    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
    public Integer getPointsCost() { return pointsCost; }
    public void setPointsCost(Integer pointsCost) { this.pointsCost = pointsCost; }
    public Boolean getPointsCharged() { return pointsCharged; }
    public void setPointsCharged(Boolean pointsCharged) { this.pointsCharged = pointsCharged; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
