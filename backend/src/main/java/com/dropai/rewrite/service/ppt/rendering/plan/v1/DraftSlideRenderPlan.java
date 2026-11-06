package com.dropai.rewrite.service.ppt.rendering.plan.v1;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.Objects;

/**
 * An immutable ownership boundary around a newly compiled RenderPlan document.
 * The contained JSON is never exposed directly.
 */
public final class DraftSlideRenderPlan {
    private final ObjectNode document;

    private DraftSlideRenderPlan(ObjectNode document) {
        this.document = document.deepCopy();
    }

    public static DraftSlideRenderPlan of(JsonNode document) {
        Objects.requireNonNull(document, "document");
        if (!document.isObject()) {
            throw new IllegalArgumentException("A slide RenderPlan must be a JSON object");
        }
        return new DraftSlideRenderPlan((ObjectNode) document);
    }

    public ObjectNode document() {
        return document.deepCopy();
    }
}
