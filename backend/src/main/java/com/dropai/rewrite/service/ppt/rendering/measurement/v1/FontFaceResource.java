package com.dropai.rewrite.service.ppt.rendering.measurement.v1;

import java.util.Arrays;
import java.util.Objects;

public final class FontFaceResource {
    private final String family;
    private final String postScriptName;
    private final int weight;
    private final FontSource source;
    private final byte[] fontBytes;

    public FontFaceResource(
            String family,
            String postScriptName,
            int weight,
            FontSource source,
            byte[] fontBytes
    ) {
        this.family = requireText(family, "family");
        this.postScriptName = requireText(postScriptName, "postScriptName");
        if (weight < 100 || weight > 900 || weight % 100 != 0) {
            throw new IllegalArgumentException("weight must be from 100 to 900 in increments of 100");
        }
        this.weight = weight;
        this.source = Objects.requireNonNull(source, "source");
        Objects.requireNonNull(fontBytes, "fontBytes");
        if (fontBytes.length == 0) {
            throw new IllegalArgumentException("fontBytes must not be empty");
        }
        this.fontBytes = Arrays.copyOf(fontBytes, fontBytes.length);
    }

    public String family() {
        return family;
    }

    public String postScriptName() {
        return postScriptName;
    }

    public int weight() {
        return weight;
    }

    public FontSource source() {
        return source;
    }

    public byte[] fontBytes() {
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
