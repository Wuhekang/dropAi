package com.dropai.rewrite.service.ppt.rendering.bundle.v1;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;

/** Exact production font-face identity recorded with a generated RenderPlan. */
public record ProductionFontFace(
        String fontFaceId,
        String role,
        int weight,
        String requestedFamily,
        String resolvedFamily,
        String postScriptName,
        String fontSource,
        String fontFingerprint,
        boolean fallbackApplied
) {
    public ProductionFontFace {
        fontFaceId = text(fontFaceId, "fontFaceId");
        role = text(role, "role");
        requestedFamily = text(requestedFamily, "requestedFamily");
        resolvedFamily = text(resolvedFamily, "resolvedFamily");
        postScriptName = text(postScriptName, "postScriptName");
        fontSource = text(fontSource, "fontSource");
        fontFingerprint = hash(fontFingerprint, "fontFingerprint");
        if (weight < 100 || weight > 900 || weight % 100 != 0) {
            throw new IllegalArgumentException("weight must be 100..900 in increments of 100");
        }
        if (!requestedFamily.equals(resolvedFamily) && !fallbackApplied) {
            throw new IllegalArgumentException(
                    "A resolved family change must be explicitly marked fallbackApplied");
        }
        if (!fontSource.matches("SYSTEM|BUNDLED|PROVIDED")) {
            throw new IllegalArgumentException("Unknown fontSource: " + fontSource);
        }
    }

    public static ProductionFontFace fingerprinted(
            String fontFaceId,
            String role,
            int weight,
            String requestedFamily,
            String resolvedFamily,
            String postScriptName,
            String fontSource,
            boolean fallbackApplied,
            byte[] actualFontBytes
    ) {
        Objects.requireNonNull(actualFontBytes, "actualFontBytes");
        if (actualFontBytes.length == 0) {
            throw new IllegalArgumentException("actualFontBytes must not be empty");
        }
        return new ProductionFontFace(
                fontFaceId,
                role,
                weight,
                requestedFamily,
                resolvedFamily,
                postScriptName,
                fontSource,
                sha256(actualFontBytes),
                fallbackApplied);
    }

    private static String sha256(byte[] bytes) {
        try {
            return "sha256:" + HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
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
