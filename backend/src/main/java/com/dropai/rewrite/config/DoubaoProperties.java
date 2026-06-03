package com.dropai.rewrite.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "ai.doubao")
public class DoubaoProperties {

    private boolean enabled = true;
    private String apiKey;
    private String endpoint = "https://ark.cn-beijing.volces.com/api/v3/chat/completions";
    private String model = "doubao-seed-1-8-251228";
    private double temperature = 0.35;
    private int connectTimeoutSeconds = 10;
    private int readTimeoutSeconds = 120;
    private int documentConcurrency = 64;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    public String getEndpoint() {
        return endpoint;
    }

    public void setEndpoint(String endpoint) {
        this.endpoint = endpoint;
    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public double getTemperature() {
        return temperature;
    }

    public void setTemperature(double temperature) {
        this.temperature = temperature;
    }

    public int getConnectTimeoutSeconds() {
        return connectTimeoutSeconds;
    }

    public void setConnectTimeoutSeconds(int connectTimeoutSeconds) {
        this.connectTimeoutSeconds = connectTimeoutSeconds;
    }

    public int getReadTimeoutSeconds() {
        return readTimeoutSeconds;
    }

    public void setReadTimeoutSeconds(int readTimeoutSeconds) {
        this.readTimeoutSeconds = readTimeoutSeconds;
    }

    public int getDocumentConcurrency() {
        return documentConcurrency;
    }

    public void setDocumentConcurrency(int documentConcurrency) {
        this.documentConcurrency = documentConcurrency;
    }
}
