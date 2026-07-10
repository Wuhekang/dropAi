package com.dropai.rewrite.service.image;

import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;

@Service
public class WanliangImageProvider implements ImageGenerationProvider {
    private final Environment environment;

    public WanliangImageProvider(Environment environment) {
        this.environment = environment;
    }

    @Override
    public ImageGenerationResult generate(ImageGenerationRequest request) {
        ImageGenerationResult result = health();
        if (!result.isEnabled()) {
            result.setStatus("disabled");
            result.setMessage("WANLIANG_IMAGE_ENABLED is not true; image task skipped without blocking CAD deliverables.");
            return result;
        }
        if (!result.isApiKeyConfigured()) {
            result.setStatus("failed");
            result.setMessage("WANLIANG_API_KEY is not configured.");
            return result;
        }
        result.setStatus("failed");
        result.setMessage("Wanliang image request adapter is configured but no provider-specific request contract is enabled yet.");
        return result;
    }

    @Override
    public ImageGenerationResult health() {
        ImageGenerationResult result = new ImageGenerationResult();
        result.setEnabled(Boolean.parseBoolean(value("WANLIANG_IMAGE_ENABLED", "false")));
        result.setApiKeyConfigured(!value("WANLIANG_API_KEY", "").isBlank());
        result.setEndpoint(value("WANLIANG_IMAGE_ENDPOINT", value("WANLIANG_BASE_URL", "")));
        result.setModel(value("WANLIANG_IMAGE_MODEL", ""));
        result.setStatus(result.isEnabled() && result.isApiKeyConfigured() ? "ready" : "disabled");
        result.setMessage(result.isEnabled()
                ? "Image provider configuration loaded; API key is kept server-side."
                : "Image generation is disabled and will not block mechanical deliverables.");
        return result;
    }

    private String value(String key, String fallback) {
        String value = environment.getProperty(key);
        return value == null || value.isBlank() ? fallback : value.trim();
    }
}
