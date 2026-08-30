package com.dropai.rewrite.service.ppt.rendering.renderer.v1;

import java.util.Arrays;
import java.util.Objects;

/** Binary resolver response. The Renderer independently re-verifies every field and byte hash. */
public record VerifiedAssetBytes(
        String assetId,
        String bundlePath,
        String sha256,
        String mimeType,
        byte[] bytes
) {
    public VerifiedAssetBytes {
        assetId = requireText(assetId, "assetId");
        bundlePath = requireText(bundlePath, "bundlePath");
        sha256 = requireText(sha256, "sha256");
        bytes = Arrays.copyOf(Objects.requireNonNull(bytes, "bytes"), bytes.length);
    }

    public VerifiedAssetBytes(String assetId, String bundlePath, String sha256, byte[] bytes) {
        this(assetId, bundlePath, sha256, null, bytes);
    }

    @Override
    public byte[] bytes() {
        return Arrays.copyOf(bytes, bytes.length);
    }

    private static String requireText(String value, String field) {
        Objects.requireNonNull(value, field);
        if (value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value;
    }
}
