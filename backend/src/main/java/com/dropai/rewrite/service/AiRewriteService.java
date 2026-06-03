package com.dropai.rewrite.service;

public interface AiRewriteService {

    String rewrite(String originalText, String rewriteType);

    default String rewriteWithFeedback(String originalText, String rewriteType, int beforeScore, String feedback) {
        return rewrite(originalText, rewriteType);
    }

    default String providerName() {
        return "AI服务";
    }

    default String modelName() {
        return "";
    }

    default String lastCallProvider() {
        return providerName();
    }

    default boolean apiKeyConfigured() {
        return true;
    }

    default String endpoint() {
        return "";
    }
}
