package com.dropai.rewrite.service.image;

import java.util.List;

public class ImageGenerationRequest {
    private String prompt = "";
    private List<String> referenceImageUrls = List.of();

    public String getPrompt() { return prompt; }
    public void setPrompt(String prompt) { this.prompt = prompt == null ? "" : prompt; }
    public List<String> getReferenceImageUrls() { return referenceImageUrls; }
    public void setReferenceImageUrls(List<String> referenceImageUrls) {
        this.referenceImageUrls = referenceImageUrls == null ? List.of() : referenceImageUrls;
    }
}
