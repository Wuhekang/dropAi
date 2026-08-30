package com.dropai.rewrite.service.ppt.rendering.canonical.v1;

import com.dropai.rewrite.service.ppt.rendering.validation.v1.ValidatedSlideRenderPlan;

import java.util.Objects;

/** Converts an accepted plan into the immutable canonical renderer input. */
public final class RenderPlanFreezer {
    private final RenderPlanCanonicalizer canonicalizer;

    public RenderPlanFreezer() {
        this(new RenderPlanCanonicalizer());
    }

    public RenderPlanFreezer(RenderPlanCanonicalizer canonicalizer) {
        this.canonicalizer = Objects.requireNonNull(canonicalizer, "canonicalizer");
    }

    public FrozenSlideRenderPlan freeze(ValidatedSlideRenderPlan validated) {
        Objects.requireNonNull(validated, "validated");
        return FrozenSlideRenderPlan.fromCanonicalBytes(
                canonicalizer.canonicalBytes(validated.document()));
    }
}
