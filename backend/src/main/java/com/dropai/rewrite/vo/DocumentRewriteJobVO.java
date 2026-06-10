package com.dropai.rewrite.vo;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class DocumentRewriteJobVO {

    private String jobId;
    private String fileName;
    private String sourceFeature;
    private String mode;
    private String modeName;
    private String platform;
    private String platformName;
    private String status;
    private int totalParagraphs;
    private int processedParagraphs;
    private int rewrittenParagraphs;
    private String message;
    private String downloadUrl;
    private List<DocumentParagraphJobVO> paragraphs = new ArrayList<>();
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public String getJobId() {
        return jobId;
    }

    public void setJobId(String jobId) {
        this.jobId = jobId;
    }

    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }
    public String getSourceFeature() { return sourceFeature; }
    public void setSourceFeature(String sourceFeature) { this.sourceFeature = sourceFeature; }

    public String getMode() {
        return mode;
    }

    public void setMode(String mode) {
        this.mode = mode;
    }

    public String getModeName() {
        return modeName;
    }

    public void setModeName(String modeName) {
        this.modeName = modeName;
    }

    public String getPlatform() {
        return platform;
    }

    public void setPlatform(String platform) {
        this.platform = platform;
    }

    public String getPlatformName() {
        return platformName;
    }

    public void setPlatformName(String platformName) {
        this.platformName = platformName;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public int getTotalParagraphs() {
        return totalParagraphs;
    }

    public void setTotalParagraphs(int totalParagraphs) {
        this.totalParagraphs = totalParagraphs;
    }

    public int getProcessedParagraphs() {
        return processedParagraphs;
    }

    public void setProcessedParagraphs(int processedParagraphs) {
        this.processedParagraphs = processedParagraphs;
    }

    public int getRewrittenParagraphs() {
        return rewrittenParagraphs;
    }

    public void setRewrittenParagraphs(int rewrittenParagraphs) {
        this.rewrittenParagraphs = rewrittenParagraphs;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public String getDownloadUrl() {
        return downloadUrl;
    }

    public void setDownloadUrl(String downloadUrl) {
        this.downloadUrl = downloadUrl;
    }

    public List<DocumentParagraphJobVO> getParagraphs() {
        return paragraphs;
    }

    public void setParagraphs(List<DocumentParagraphJobVO> paragraphs) {
        this.paragraphs = paragraphs;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
