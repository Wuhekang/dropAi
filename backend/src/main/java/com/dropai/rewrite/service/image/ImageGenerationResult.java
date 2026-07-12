package com.dropai.rewrite.service.image;

public class ImageGenerationResult {
    private String status = "disabled";
    private String provider = "Doubao Image Provider";
    private String model = "";
    private String endpoint = "";
    private boolean enabled;
    private boolean apiKeyConfigured;
    private String message = "";
    private String filePath = "";
    private long elapsedMs;

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status == null ? "" : status; }
    public String getProvider() { return provider; }
    public void setProvider(String provider) { this.provider = provider == null ? "" : provider; }
    public String getModel() { return model; }
    public void setModel(String model) { this.model = model == null ? "" : model; }
    public String getEndpoint() { return endpoint; }
    public void setEndpoint(String endpoint) { this.endpoint = endpoint == null ? "" : endpoint; }
    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public boolean isApiKeyConfigured() { return apiKeyConfigured; }
    public void setApiKeyConfigured(boolean apiKeyConfigured) { this.apiKeyConfigured = apiKeyConfigured; }
    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message == null ? "" : message; }
    public String getFilePath() { return filePath; }
    public void setFilePath(String filePath) { this.filePath = filePath == null ? "" : filePath; }
    public long getElapsedMs() { return elapsedMs; }
    public void setElapsedMs(long elapsedMs) { this.elapsedMs = elapsedMs; }
}
