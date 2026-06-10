package com.dropai.rewrite.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;

@TableName("document_job")
public class DocumentJobRecord {
    @TableId
    private String jobId;
    private Long userId;
    private String fileName;
    private String mode;
    private String modeName;
    private String platform;
    private String platformName;
    private String status;
    private Integer totalParagraphs;
    private Integer processedParagraphs;
    private Integer rewrittenParagraphs;
    private String message;
    private String paragraphsJson;
    private byte[] outputFile;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public String getJobId() { return jobId; }
    public void setJobId(String jobId) { this.jobId = jobId; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public String getFileName() { return fileName; }
    public void setFileName(String fileName) { this.fileName = fileName; }
    public String getMode() { return mode; }
    public void setMode(String mode) { this.mode = mode; }
    public String getModeName() { return modeName; }
    public void setModeName(String modeName) { this.modeName = modeName; }
    public String getPlatform() { return platform; }
    public void setPlatform(String platform) { this.platform = platform; }
    public String getPlatformName() { return platformName; }
    public void setPlatformName(String platformName) { this.platformName = platformName; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Integer getTotalParagraphs() { return totalParagraphs; }
    public void setTotalParagraphs(Integer totalParagraphs) { this.totalParagraphs = totalParagraphs; }
    public Integer getProcessedParagraphs() { return processedParagraphs; }
    public void setProcessedParagraphs(Integer processedParagraphs) { this.processedParagraphs = processedParagraphs; }
    public Integer getRewrittenParagraphs() { return rewrittenParagraphs; }
    public void setRewrittenParagraphs(Integer rewrittenParagraphs) { this.rewrittenParagraphs = rewrittenParagraphs; }
    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
    public String getParagraphsJson() { return paragraphsJson; }
    public void setParagraphsJson(String paragraphsJson) { this.paragraphsJson = paragraphsJson; }
    public byte[] getOutputFile() { return outputFile; }
    public void setOutputFile(byte[] outputFile) { this.outputFile = outputFile; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
