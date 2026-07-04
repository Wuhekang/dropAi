package com.dropai.rewrite.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public class SystemNoticeDTO {
    private Long id;
    private String title;
    private String content;
    private String status;
    @JsonProperty("is_popup")
    private Boolean isPopup;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Boolean getIsPopup() { return isPopup; }
    public void setIsPopup(Boolean popup) { isPopup = popup; }
}
