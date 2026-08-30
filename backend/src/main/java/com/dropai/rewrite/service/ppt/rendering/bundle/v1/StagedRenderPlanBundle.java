package com.dropai.rewrite.service.ppt.rendering.bundle.v1;

import java.nio.file.Path;
import java.util.Objects;

/** Complete immutable revision that is not yet visible through the bundle current pointer. */
public record StagedRenderPlanBundle(
        Path bundleDirectory,
        String revisionName,
        String renderPlanHash,
        String generationManifestHash,
        String assetManifestHash,
        String fontManifestHash,
        int assetCount
) {
    public StagedRenderPlanBundle {
        bundleDirectory = Objects.requireNonNull(bundleDirectory, "bundleDirectory")
                .toAbsolutePath().normalize();
        Objects.requireNonNull(revisionName, "revisionName");
        Objects.requireNonNull(renderPlanHash, "renderPlanHash");
        Objects.requireNonNull(generationManifestHash, "generationManifestHash");
        Objects.requireNonNull(assetManifestHash, "assetManifestHash");
        Objects.requireNonNull(fontManifestHash, "fontManifestHash");
        if (!revisionName.matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")) {
            throw new IllegalArgumentException("revisionName must be a lowercase UUID");
        }
        if (assetCount < 0) {
            throw new IllegalArgumentException("assetCount must not be negative");
        }
    }
}
