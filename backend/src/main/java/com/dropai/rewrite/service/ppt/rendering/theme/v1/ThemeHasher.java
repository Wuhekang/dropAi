package com.dropai.rewrite.service.ppt.rendering.theme.v1;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

public final class ThemeHasher {
    private final ThemeCanonicalizer canonicalizer;

    public ThemeHasher(ThemeCanonicalizer canonicalizer) {
        this.canonicalizer = Objects.requireNonNull(canonicalizer, "canonicalizer");
    }

    public String hash(JsonNode value) {
        return hashUtf8(canonicalizer.canonicalize(value));
    }

    public String hashUtf8(String canonicalValue) {
        Objects.requireNonNull(canonicalValue, "canonicalValue");
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] result = digest.digest(canonicalValue.getBytes(StandardCharsets.UTF_8));
            return "sha256:" + HexFormat.of().formatHex(result);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    public String hashNamedValues(Map<String, String> values) {
        Objects.requireNonNull(values, "values");
        ObjectNode document = JsonNodeFactory.instance.objectNode();
        new TreeMap<>(values).forEach(document::put);
        return hash(document);
    }
}
