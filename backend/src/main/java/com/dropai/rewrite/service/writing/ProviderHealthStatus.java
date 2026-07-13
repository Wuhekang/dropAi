package com.dropai.rewrite.service.writing;

import java.time.LocalDateTime;

public record ProviderHealthStatus(
        String provider,
        boolean enabled,
        boolean configured,
        boolean available,
        String authorizationMode,
        String model,
        String apiType,
        boolean webSearchEnabled,
        String endpoint,
        LocalDateTime checkedAt,
        long latencyMs,
        String message,
        String errorCode
) {
    public static ProviderHealthStatus of(String provider, boolean enabled, boolean configured, boolean available,
                                          String authorizationMode, String endpoint, String errorCode,
                                          String message, long latencyMs) {
        return of(provider, enabled, configured, available, authorizationMode, "", authorizationMode, false, endpoint,
                errorCode, message, latencyMs);
    }

    public static ProviderHealthStatus of(String provider, boolean enabled, boolean configured, boolean available,
                                          String authorizationMode, String model, String apiType, boolean webSearchEnabled,
                                          String endpoint, String errorCode, String message, long latencyMs) {
        return new ProviderHealthStatus(provider, enabled, configured, available, authorizationMode, model, apiType, webSearchEnabled, endpoint,
                LocalDateTime.now(), latencyMs, message, errorCode);
    }
}
