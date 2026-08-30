package com.dropai.rewrite.service.ppt.rendering.layout.v1;

import com.fasterxml.jackson.databind.JsonNode;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;

public final class LayoutHasher {
    private final LayoutCanonicalizer canonicalizer;

    public LayoutHasher(LayoutCanonicalizer canonicalizer) {
        this.canonicalizer = Objects.requireNonNull(canonicalizer, "canonicalizer");
    }

    public String hash(JsonNode document) {
        return hashCanonical(canonicalizer.canonicalize(document));
    }

    public String hashCanonical(String canonicalDocument) {
        Objects.requireNonNull(canonicalDocument, "canonicalDocument");
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(canonicalDocument.getBytes(StandardCharsets.UTF_8));
            return "sha256:" + HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }
}
