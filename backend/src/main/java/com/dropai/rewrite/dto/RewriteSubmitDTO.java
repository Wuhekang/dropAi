package com.dropai.rewrite.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class RewriteSubmitDTO {

    @NotBlank(message = "原文内容不能为空")
    @Size(max = 10000, message = "原文内容不能超过10000字")
    private String originalText;

    @NotBlank(message = "优化类型不能为空")
    private String rewriteType;

    private String platform;

    public String getOriginalText() {
        return originalText;
    }

    public void setOriginalText(String originalText) {
        this.originalText = originalText;
    }

    public String getRewriteType() {
        return rewriteType;
    }

    public void setRewriteType(String rewriteType) {
        this.rewriteType = rewriteType;
    }

    public String getPlatform() {
        return platform;
    }

    public void setPlatform(String platform) {
        this.platform = platform;
    }
}
