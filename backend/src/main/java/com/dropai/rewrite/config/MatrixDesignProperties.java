package com.dropai.rewrite.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "ai.matrix-design")
public class MatrixDesignProperties {
    private boolean enabled = true;
    private String apiKey;
    private String endpoint = "https://www.aikeys.me/v1/chat/completions";
    private String model = "claude-opus-4-7";
    private int connectTimeoutSeconds = 15;
    private int readTimeoutSeconds = 180;

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public String getApiKey() { return apiKey; }
    public void setApiKey(String apiKey) { this.apiKey = apiKey; }
    public String getEndpoint() { return endpoint; }
    public void setEndpoint(String endpoint) { this.endpoint = endpoint; }
    public String getModel() { return model; }
    public void setModel(String model) { this.model = model; }
    public int getConnectTimeoutSeconds() { return connectTimeoutSeconds; }
    public void setConnectTimeoutSeconds(int connectTimeoutSeconds) { this.connectTimeoutSeconds = connectTimeoutSeconds; }
    public int getReadTimeoutSeconds() { return readTimeoutSeconds; }
    public void setReadTimeoutSeconds(int readTimeoutSeconds) { this.readTimeoutSeconds = readTimeoutSeconds; }
}
