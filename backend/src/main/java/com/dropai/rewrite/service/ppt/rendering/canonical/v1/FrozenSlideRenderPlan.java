package com.dropai.rewrite.service.ppt.rendering.canonical.v1;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Objects;

/** Canonical, immutable RenderPlan accepted by the later pure Renderer. */
public final class FrozenSlideRenderPlan {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final byte[] canonicalBytes;

    private FrozenSlideRenderPlan(byte[] canonicalBytes) {
        this.canonicalBytes = Arrays.copyOf(canonicalBytes, canonicalBytes.length);
    }

    static FrozenSlideRenderPlan fromCanonicalBytes(byte[] canonicalBytes) {
        Objects.requireNonNull(canonicalBytes, "canonicalBytes");
        if (canonicalBytes.length == 0 || canonicalBytes[canonicalBytes.length - 1] != '\n') {
            throw new IllegalArgumentException("Canonical RenderPlan must end with LF");
        }
        try {
            if (!MAPPER.readTree(canonicalBytes).isObject()) {
                throw new IllegalArgumentException("Canonical RenderPlan must contain a JSON object");
            }
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("Canonical RenderPlan is not valid JSON", exception);
        } catch (java.io.IOException exception) {
            throw new IllegalStateException("Unexpected byte-array read failure", exception);
        }
        return new FrozenSlideRenderPlan(canonicalBytes);
    }

    public byte[] canonicalBytes() {
        return Arrays.copyOf(canonicalBytes, canonicalBytes.length);
    }

    public String canonicalDocument() {
        return new String(canonicalBytes, StandardCharsets.UTF_8);
    }

    public ObjectNode document() {
        try {
            return (ObjectNode) MAPPER.readTree(canonicalBytes);
        } catch (java.io.IOException exception) {
            throw new IllegalStateException("Stored frozen RenderPlan is invalid", exception);
        }
    }
}
