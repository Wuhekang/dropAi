package com.dropai.rewrite.service.ppt.rendering.canonical.v1;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.Arrays;
import java.util.Objects;

/** Narrow persistence boundary for loading canonical bytes from a verified RenderPlan bundle. */
public final class FrozenRenderPlanCodec {
    private final RenderPlanCanonicalizer canonicalizer = new RenderPlanCanonicalizer();

    public FrozenSlideRenderPlan decodeCanonical(byte[] bytes) {
        Objects.requireNonNull(bytes, "bytes");
        FrozenSlideRenderPlan frozen = FrozenSlideRenderPlan.fromCanonicalBytes(bytes);
        byte[] canonical = canonicalizer.canonicalBytes(frozen.document());
        if (!Arrays.equals(bytes, canonical)) {
            throw new IllegalArgumentException("Persisted RenderPlan is not canonical render-plan.v1 JSON");
        }
        return frozen;
    }
}
