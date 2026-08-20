package com.dropai.rewrite.config;

import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

@Component
public class PptProperties {
    private final Environment environment;

    public PptProperties(Environment environment) { this.environment = environment; }

    public boolean enabled() { return Boolean.parseBoolean(value("DOKIAI_PPT_ENABLED", "true")); }
    public String provider() { return value("DOKIAI_PPT_PROVIDER", "kimi_ark"); }
    public String apiKey() { return first("DOKIAI_PPT_ARK_API_KEY", "ARK_API_KEY", "DOUBAO_API_KEY"); }
    public String baseUrl() { return firstOr("https://ark.cn-beijing.volces.com/api/v3", "DOKIAI_PPT_ARK_BASE_URL", "DOKIAI_PPT_BASE_URL", "ARK_BASE_URL"); }
    public String responsesPath() { return value("DOKIAI_PPT_RESPONSES_PATH", "/responses"); }
    public String model() { return first("DOKIAI_PPT_KIMI_MODEL", "DOKIAI_PPT_MODEL", "ARK_MODEL"); }
    public String endpointId() { return first("DOKIAI_PPT_KIMI_ENDPOINT_ID", "ARK_ENDPOINT_ID"); }
    public int timeoutSeconds() { return integer("DOKIAI_PPT_TIMEOUT_SECONDS", 300); }
    public int maxRetries() { return integer("DOKIAI_PPT_MAX_RETRIES", 2); }
    public int maxSlides() { return integer("DOKIAI_PPT_MAX_SLIDES", 40); }
    public boolean configured() { return enabled() && !apiKey().isBlank() && !model().isBlank(); }

    private String first(String... names) {
        for (String name : names) { String result = value(name, ""); if (!result.isBlank()) return result; }
        return "";
    }
    private String firstOr(String fallback,String... names) { String found=first(names); return found.isBlank()?fallback:found; }
    private String value(String name, String fallback) {
        String result = environment.getProperty(name);
        return result == null || result.isBlank() ? fallback : result.trim();
    }
    private int integer(String name, int fallback) {
        try { return Integer.parseInt(value(name, String.valueOf(fallback))); } catch (Exception ignored) { return fallback; }
    }
}
