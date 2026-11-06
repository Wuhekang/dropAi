package com.dropai.rewrite.service.ppt.rendering.renderer.v1;

/**
 * Exact binary lookup boundary for a RenderPlan asset. Implementations must not scan,
 * fuzzy-match, download, replace, or synthesize assets.
 */
@FunctionalInterface
public interface AssetBinaryResolver {
    VerifiedAssetBytes resolve(String assetId, String bundlePath, String expectedSha256);
}
