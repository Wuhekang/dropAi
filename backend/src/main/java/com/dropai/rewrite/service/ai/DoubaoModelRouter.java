package com.dropai.rewrite.service.ai;

import com.dropai.rewrite.config.DoubaoProperties;
import org.springframework.stereotype.Service;

@Service
public class DoubaoModelRouter {
    public static final String REQUIRED_MECHANICAL_VISION_MODEL = "doubao-seed-2-1-turbo-260628";

    private final DoubaoProperties properties;

    public DoubaoModelRouter(DoubaoProperties properties) {
        this.properties = properties;
    }

    public String resolveModel(AiRequestType requestType) {
        return switch (requestType) {
            case TEXT -> firstPresent(properties.getTextModel(), properties.getModel());
            case GENERAL_VISION -> firstPresent(properties.getVisionModel(), properties.getTextModel(), properties.getModel());
            case MECHANICAL_VISION -> resolveMechanicalVisionModel();
        };
    }

    public String resolveMechanicalVisionModel() {
        String model = normalize(properties.getMechanicalVisionModel());
        if (model.isBlank()) {
            throw new IllegalStateException("Missing DOUBAO_MECHANICAL_VISION_MODEL");
        }
        if (!REQUIRED_MECHANICAL_VISION_MODEL.equals(model)) {
            throw new IllegalStateException("DOUBAO_MECHANICAL_VISION_MODEL must be "
                    + REQUIRED_MECHANICAL_VISION_MODEL + ", current=" + model);
        }
        return model;
    }

    public String currentTextModel() {
        return resolveModel(AiRequestType.TEXT);
    }

    private String firstPresent(String... values) {
        for (String value : values) {
            String normalized = normalize(value);
            if (!normalized.isBlank()) {
                return normalized;
            }
        }
        throw new IllegalStateException("No Doubao model configured");
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim();
    }
}
