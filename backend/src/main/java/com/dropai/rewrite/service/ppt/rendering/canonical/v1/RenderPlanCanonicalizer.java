package com.dropai.rewrite.service.ppt.rendering.canonical.v1;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

/**
 * Canonical JSON for RenderPlan hashing: UTF-8, LF, lexicographic object keys,
 * preserved array order and stable decimal spelling.
 */
public final class RenderPlanCanonicalizer {
    private static final ObjectMapper STRING_ENCODER = new ObjectMapper();

    public byte[] canonicalBytes(JsonNode document) {
        Objects.requireNonNull(document, "document");
        StringBuilder output = new StringBuilder();
        append(document, output);
        output.append('\n');
        return output.toString().getBytes(StandardCharsets.UTF_8);
    }

    public String canonicalize(JsonNode document) {
        return new String(canonicalBytes(document), StandardCharsets.UTF_8);
    }

    private void append(JsonNode node, StringBuilder output) {
        if (node == null || node.isNull()) {
            output.append("null");
            return;
        }
        if (node.isObject()) {
            output.append('{');
            Map<String, JsonNode> sorted = new TreeMap<>();
            node.fields().forEachRemaining(entry -> sorted.put(entry.getKey(), entry.getValue()));
            boolean first = true;
            for (Map.Entry<String, JsonNode> entry : sorted.entrySet()) {
                if (!first) {
                    output.append(',');
                }
                appendQuoted(entry.getKey(), output);
                output.append(':');
                append(entry.getValue(), output);
                first = false;
            }
            output.append('}');
            return;
        }
        if (node.isArray()) {
            output.append('[');
            for (int index = 0; index < node.size(); index++) {
                if (index > 0) {
                    output.append(',');
                }
                append(node.get(index), output);
            }
            output.append(']');
            return;
        }
        if (node.isTextual()) {
            appendQuoted(node.textValue(), output);
            return;
        }
        if (node.isIntegralNumber()) {
            output.append(node.bigIntegerValue());
            return;
        }
        if (node.isFloatingPointNumber()) {
            throw new IllegalArgumentException(
                    "RenderPlan numeric values must be contract integers (EMU and scaled units)");
        }
        if (node.isBoolean()) {
            output.append(node.booleanValue());
            return;
        }
        throw new IllegalArgumentException("Unsupported JSON value: " + node.getNodeType());
    }

    private void appendQuoted(String value, StringBuilder output) {
        try {
            output.append(STRING_ENCODER.writeValueAsString(value));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Cannot JSON-escape canonical value", exception);
        }
    }
}
