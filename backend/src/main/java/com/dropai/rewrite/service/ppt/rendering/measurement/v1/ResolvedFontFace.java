package com.dropai.rewrite.service.ppt.rendering.measurement.v1;

import java.util.Arrays;
import java.util.Objects;

public final class ResolvedFontFace {
    private final String role;
    private final int weight;
    private final String selectedFamily;
    private final String postScriptName;
    private final FontSource fontSource;
    private final String fontFingerprint;
    private final boolean fallbackApplied;
    private final byte[] fontBytes;

    ResolvedFontFace(
            String role,
            int weight,
            String selectedFamily,
            String postScriptName,
            FontSource fontSource,
            String fontFingerprint,
            boolean fallbackApplied,
            byte[] fontBytes
    ) {
        this.role = requireText(role, "role");
        this.weight = weight;
        this.selectedFamily = requireText(selectedFamily, "selectedFamily");
        this.postScriptName = requireText(postScriptName, "postScriptName");
        this.fontSource = Objects.requireNonNull(fontSource, "fontSource");
        this.fontFingerprint = requireText(fontFingerprint, "fontFingerprint");
        this.fallbackApplied = fallbackApplied;
        this.fontBytes = Arrays.copyOf(Objects.requireNonNull(fontBytes, "fontBytes"), fontBytes.length);
    }

    public String role() {
        return role;
    }

    public int weight() {
        return weight;
    }

    public String selectedFamily() {
        return selectedFamily;
    }

    public String postScriptName() {
        return postScriptName;
    }

    public FontSource fontSource() {
        return fontSource;
    }

    public String fontFingerprint() {
        return fontFingerprint;
    }

    public boolean fallbackApplied() {
        return fallbackApplied;
    }

    byte[] fontBytes() {
        return Arrays.copyOf(fontBytes, fontBytes.length);
    }

    private static String requireText(String value, String name) {
        Objects.requireNonNull(value, name);
        if (value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value;
    }
}
