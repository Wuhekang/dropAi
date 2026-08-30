package com.dropai.rewrite.service.ppt.rendering.bundle.v1;

import com.dropai.rewrite.service.ppt.rendering.canonical.v1.FrozenSlideRenderPlan;
import com.dropai.rewrite.service.ppt.rendering.renderer.v1.AssetBinaryResolver;

import java.nio.file.Path;
import java.util.Objects;

/** Verified production bundle ready for PurePptxRenderer execution. */
public record LoadedRenderPlanBundle(
        Path bundleDirectory,
        FrozenSlideRenderPlan renderPlan,
        String renderPlanHash,
        AssetBinaryResolver assetResolver,
        int assetCount
) {
    public LoadedRenderPlanBundle {
        bundleDirectory = Objects.requireNonNull(bundleDirectory, "bundleDirectory").toAbsolutePath().normalize();
        renderPlan = Objects.requireNonNull(renderPlan, "renderPlan");
        renderPlanHash = Objects.requireNonNull(renderPlanHash, "renderPlanHash");
        assetResolver = Objects.requireNonNull(assetResolver, "assetResolver");
    }
}
