package com.dropai.rewrite.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class AnalyzeTextDTO {

    @NotBlank(message = "原文内容不能为空")
    @Size(max = 10000, message = "原文内容不能超过10000字")
    private String originalText;

    public String getOriginalText() {
        return originalText;
    }

    public void setOriginalText(String originalText) {
        this.originalText = originalText;
    }
}
