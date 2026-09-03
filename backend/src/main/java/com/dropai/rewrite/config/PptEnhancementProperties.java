package com.dropai.rewrite.config;

import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.nio.file.Path;

@Component
public class PptEnhancementProperties {
    private final Environment environment;

    public PptEnhancementProperties(Environment environment) {
        this.environment = environment;
    }

    public boolean enabled() {
        return Boolean.parseBoolean(firstOr("true", "DOKIAI_PPT_ENHANCEMENT_ENABLED", "ppt-enhancement.enabled"));
    }

    public String provider() {
        return "doubao_ark";
    }

    public String apiKey() {
        return first("DOKIAI_PPT_ENHANCEMENT_ARK_API_KEY", "DOUBAO_API_KEY", "ARK_API_KEY",
            "ai.doubao.api-key");
    }

    public String baseUrl() {
        return firstOr("https://ark.cn-beijing.volces.com/api/v3",
            "DOKIAI_PPT_ENHANCEMENT_BASE_URL", "DOUBAO_RESPONSES_BASE_URL",
            "ai.doubao.responses-base-url", "DOUBAO_BASE_URL", "ai.doubao.base-url", "ARK_BASE_URL");
    }

    public String responsesPath() {
        return firstOr("/responses", "DOKIAI_PPT_ENHANCEMENT_RESPONSES_PATH",
            "ai.doubao.responses-path");
    }

    public String model() {
        return first("DOKIAI_PPT_ENHANCEMENT_MODEL", "ppt-enhancement.model", "DOUBAO_MODEL", "ai.doubao.model", "ARK_MODEL");
    }

    public int timeoutSeconds() {
        return integer("DOKIAI_PPT_ENHANCEMENT_TIMEOUT_SECONDS", 300);
    }

    public int maxRetries() {
        return Math.max(0, Math.min(2, integer("DOKIAI_PPT_ENHANCEMENT_MAX_RETRIES", 1)));
    }

    public Path dataDir() {
        return Path.of(firstOr("storage/ppt", "DOKIAI_PPT_ENHANCEMENT_DATA_DIR", "ppt-enhancement.data-dir"))
            .toAbsolutePath().normalize();
    }

    public boolean configured() {
        return enabled() && !apiKey().isBlank() && !model().isBlank();
    }

    private String first(String... names) {
        for (String name : names) {
            String result = value(name, "");
            if (!result.isBlank()) return result;
        }
        return "";
    }

    private String firstOr(String fallback, String... names) {
        String result = first(names);
        return result.isBlank() ? fallback : result;
    }

    private String value(String name, String fallback) {
        String result = environment.getProperty(name);
        return result == null || result.isBlank() ? fallback : result.trim();
    }

    private int integer(String name, int fallback) {
        try {
            return Integer.parseInt(value(name, String.valueOf(fallback)));
        } catch (Exception ignored) {
            return fallback;
        }
    }
}
