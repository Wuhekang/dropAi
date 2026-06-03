package com.dropai.rewrite.vo;

public class DocumentParagraphJobVO {

    private int index;
    private String status;
    private String originalText;
    private String rewrittenText;
    private String message;

    public int getIndex() {
        return index;
    }

    public void setIndex(int index) {
        this.index = index;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
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

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }
}
