package com.dropai.rewrite.service.ppt.rendering.validation.v1;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.Objects;

/**
 * Immutable type-state created only by the validation package after a draft
 * has passed every RenderPlan gate.
 */
public final class ValidatedSlideRenderPlan {
    private final ObjectNode document;

    ValidatedSlideRenderPlan(JsonNode document) {
        Objects.requireNonNull(document, "document");
        if (!document.isObject()) {
            throw new IllegalArgumentException("A validated slide RenderPlan must be a JSON object");
        }
        this.document = ((ObjectNode) document).deepCopy();
    }

    public ObjectNode document() {
        return document.deepCopy();
    }
}
