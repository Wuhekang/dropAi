package com.dropai.rewrite.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "ai.doubao")
public class DoubaoProperties {

    private boolean enabled = true;
    private String apiKey;
    private String baseUrl = "https://ark.cn-beijing.volces.com/api/v3";
    private String responsesPath = "/responses";
    private String chatPath = "/chat/completions";
    private String endpoint = "https://ark.cn-beijing.volces.com/api/v3/chat/completions";
    private String model = "doubao-seed-2-1-turbo-260628";
    private String textModel = "doubao-seed-2-1-turbo-260628";
    private String reviewModel = "doubao-seed-2-1-pro-260628";
    private String fallbackModel = "doubao-seed-2-0-lite-260428";
    private String visionModel;
    private String mechanicalVisionModel = "doubao-seed-2-1-turbo-260628";
    private boolean webSearchEnabled = false;
    private boolean webSearchForce = true;
    private String webSearchModel = "";
    private String responsesBaseUrl = "https://ark.cn-beijing.volces.com/api/v3";
    private int webSearchTimeoutSeconds = 60;
    private int webSearchMaxResults = 20;
    private int webSearchRetryCount = 2;
    private int webSearchMaxQueries = 12;
    private int maxOutputTokens = 8192;
    private boolean debug = false;
    private double temperature = 0.35;
    private int connectTimeoutSeconds = 10;
    private int readTimeoutSeconds = 120;
    private int documentConcurrency = 64;
    private int maxRetries = 2;

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

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public String getResponsesPath() {
        return responsesPath;
    }

    public void setResponsesPath(String responsesPath) {
        this.responsesPath = responsesPath;
    }

    public String getChatPath() {
        return chatPath;
    }

    public void setChatPath(String chatPath) {
        this.chatPath = chatPath;
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

    public String getTextModel() {
        return textModel;
    }

    public void setTextModel(String textModel) {
        this.textModel = textModel;
    }

    public String getReviewModel() {
        return reviewModel;
    }

    public void setReviewModel(String reviewModel) {
        this.reviewModel = reviewModel;
    }

    public String getFallbackModel() {
        return fallbackModel;
    }

    public void setFallbackModel(String fallbackModel) {
        this.fallbackModel = fallbackModel;
    }

    public String getVisionModel() {
        return visionModel;
    }

    public void setVisionModel(String visionModel) {
        this.visionModel = visionModel;
    }

    public String getMechanicalVisionModel() {
        return mechanicalVisionModel;
    }

    public void setMechanicalVisionModel(String mechanicalVisionModel) {
        this.mechanicalVisionModel = mechanicalVisionModel;
    }

    public boolean isWebSearchEnabled() {
        return webSearchEnabled;
    }

    public void setWebSearchEnabled(boolean webSearchEnabled) {
        this.webSearchEnabled = webSearchEnabled;
    }

    public boolean isWebSearchForce() {
        return webSearchForce;
    }

    public void setWebSearchForce(boolean webSearchForce) {
        this.webSearchForce = webSearchForce;
    }

    public String getWebSearchModel() {
        return webSearchModel;
    }

    public void setWebSearchModel(String webSearchModel) {
        this.webSearchModel = webSearchModel;
    }

    public String getResponsesBaseUrl() {
        return responsesBaseUrl;
    }

    public void setResponsesBaseUrl(String responsesBaseUrl) {
        this.responsesBaseUrl = responsesBaseUrl;
    }

    public int getWebSearchTimeoutSeconds() {
        return webSearchTimeoutSeconds;
    }

    public void setWebSearchTimeoutSeconds(int webSearchTimeoutSeconds) {
        this.webSearchTimeoutSeconds = webSearchTimeoutSeconds;
    }

    public int getWebSearchMaxResults() {
        return webSearchMaxResults;
    }

    public void setWebSearchMaxResults(int webSearchMaxResults) {
        this.webSearchMaxResults = webSearchMaxResults;
    }

    public int getWebSearchRetryCount() {
        return webSearchRetryCount;
    }

    public void setWebSearchRetryCount(int webSearchRetryCount) {
        this.webSearchRetryCount = webSearchRetryCount;
    }

    public int getWebSearchMaxQueries() {
        return webSearchMaxQueries;
    }

    public void setWebSearchMaxQueries(int webSearchMaxQueries) {
        this.webSearchMaxQueries = webSearchMaxQueries;
    }

    public int getMaxOutputTokens() {
        return maxOutputTokens;
    }

    public void setMaxOutputTokens(int maxOutputTokens) {
        this.maxOutputTokens = maxOutputTokens;
    }

    public boolean isDebug() {
        return debug;
    }

    public void setDebug(boolean debug) {
        this.debug = debug;
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

    public int getMaxRetries() {
        return maxRetries;
    }

    public void setMaxRetries(int maxRetries) {
        this.maxRetries = maxRetries;
    }
}
