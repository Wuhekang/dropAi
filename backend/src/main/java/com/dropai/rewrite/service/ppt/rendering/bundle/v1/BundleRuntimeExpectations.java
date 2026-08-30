package com.dropai.rewrite.service.ppt.rendering.bundle.v1;

import java.util.Objects;

/** Exact local/server runtime fingerprints required before a bundle may render. */
public record BundleRuntimeExpectations(
        String engineVersion,
        String rendererVersion,
        String gitCommit,
        String themeId,
        String themeVersion,
        String themeHash,
        String layoutCatalogVersion,
        String layoutCatalogHash,
        ProductionFontInventory fontInventory
) {
    public BundleRuntimeExpectations {
        engineVersion = text(engineVersion, "engineVersion");
        rendererVersion = text(rendererVersion, "rendererVersion");
        gitCommit = text(gitCommit, "gitCommit").toLowerCase(java.util.Locale.ROOT);
        if (!gitCommit.matches("^(?:[a-f0-9]{40}|[a-f0-9]{64})$")) {
            throw new IllegalArgumentException("gitCommit must be a full 40 or 64 character hash");
        }
        themeId = text(themeId, "themeId");
        themeVersion = text(themeVersion, "themeVersion");
        themeHash = hash(themeHash, "themeHash");
        layoutCatalogVersion = text(layoutCatalogVersion, "layoutCatalogVersion");
        layoutCatalogHash = hash(layoutCatalogHash, "layoutCatalogHash");
        fontInventory = Objects.requireNonNull(fontInventory, "fontInventory");
    }

    private static String text(String value, String field) {
        Objects.requireNonNull(value, field);
        if (value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value;
    }

    private static String hash(String value, String field) {
        value = text(value, field);
        if (!value.matches("^sha256:[a-f0-9]{64}$")) {
            throw new IllegalArgumentException(field + " is not a SHA-256 contract hash");
        }
        return value;
    }
}
