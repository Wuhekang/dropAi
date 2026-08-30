package com.dropai.rewrite.service.ppt.rendering.bundle.v1;

import java.nio.file.Path;

/** Receipt for an atomically published RenderPlan bundle. */
public record StoredRenderPlanBundle(
        Path bundleDirectory,
        String renderPlanHash,
        String generationManifestHash,
        String assetManifestHash,
        String fontManifestHash,
        int assetCount
) {
}
