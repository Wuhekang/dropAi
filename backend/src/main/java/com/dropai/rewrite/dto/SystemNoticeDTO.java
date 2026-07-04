package com.dropai.rewrite.dto;

public class SystemNoticeDTO {
    private String title;
    private String content;
    private String status;
    private Boolean isPopup;

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Boolean getIsPopup() { return isPopup; }
    public void setIsPopup(Boolean popup) { isPopup = popup; }
}
