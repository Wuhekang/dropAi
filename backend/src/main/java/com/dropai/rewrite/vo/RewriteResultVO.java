package com.dropai.rewrite.vo;

import java.time.LocalDateTime;
import java.util.List;

public class RewriteResultVO {

    private Long id;
    private String originalText;
    private String rewrittenText;
    private String rewriteType;
    private String aiProvider;
    private String aiModel;
    private int aiScore;
    private String aiLevel;
    private List<String> suggestions;
    private QualityCheckVO qualityCheck;
    private List<WorkflowStepVO> workflowSteps;
    private LocalDateTime createdAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getOriginalText() {
        return originalText;
    }

    public void setOriginalText(String originalText) {
        this.originalText = originalText;
    }

    public String getRewrittenText() {
        return rewrittenText;
    }

    public void setRewrittenText(String rewrittenText) {
        this.rewrittenText = rewrittenText;
    }

    public String getRewriteType() {
        return rewriteType;
    }

    public void setRewriteType(String rewriteType) {
        this.rewriteType = rewriteType;
    }

    public String getAiProvider() {
        return aiProvider;
    }

    public void setAiProvider(String aiProvider) {
        this.aiProvider = aiProvider;
    }

    public String getAiModel() {
        return aiModel;
    }

    public void setAiModel(String aiModel) {
        this.aiModel = aiModel;
    }

    public int getAiScore() {
        return aiScore;
    }

    public void setAiScore(int aiScore) {
        this.aiScore = aiScore;
    }

    public String getAiLevel() {
        return aiLevel;
    }

    public void setAiLevel(String aiLevel) {
        this.aiLevel = aiLevel;
    }

    public List<String> getSuggestions() {
        return suggestions;
    }

    public void setSuggestions(List<String> suggestions) {
        this.suggestions = suggestions;
    }

    public QualityCheckVO getQualityCheck() {
        return qualityCheck;
    }

    public void setQualityCheck(QualityCheckVO qualityCheck) {
        this.qualityCheck = qualityCheck;
    }

    public List<WorkflowStepVO> getWorkflowSteps() {
        return workflowSteps;
    }

    public void setWorkflowSteps(List<WorkflowStepVO> workflowSteps) {
        this.workflowSteps = workflowSteps;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
