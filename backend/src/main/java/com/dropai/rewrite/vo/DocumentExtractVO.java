package com.dropai.rewrite.vo;

public class DocumentExtractVO {
    private String fileName;
    private String text;
    private int charCount;
    private boolean readable;
    private String message;

    public DocumentExtractVO() {
    }

    public DocumentExtractVO(String fileName, String text, boolean readable, String message) {
        this.fileName = fileName;
        this.text = text == null ? "" : text;
        this.charCount = this.text.length();
        this.readable = readable;
        this.message = message;
    }

    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text;
        this.charCount = text == null ? 0 : text.length();
    }

    public int getCharCount() {
        return charCount;
    }

    public void setCharCount(int charCount) {
        this.charCount = charCount;
    }

    public boolean isReadable() {
        return readable;
    }

    public void setReadable(boolean readable) {
        this.readable = readable;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }
}
