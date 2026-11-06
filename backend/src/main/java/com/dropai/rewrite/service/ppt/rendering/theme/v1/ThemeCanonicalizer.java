package com.dropai.rewrite.service.ppt.rendering.theme.v1;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class ThemeCanonicalizer {
    private static final ObjectMapper STRING_ENCODER = new ObjectMapper();

    public String canonicalize(JsonNode value) {
        if (value == null) {
            throw new IllegalArgumentException("Canonical JSON value must not be null");
        }
        StringBuilder target = new StringBuilder();
        append(value, target);
        target.append('\n');
        return target.toString();
    }

    private void append(JsonNode value, StringBuilder target) {
        if (value.isObject()) {
            appendObject(value, target);
        } else if (value.isArray()) {
            appendArray(value, target);
        } else if (value.isTextual()) {
            appendString(normalizeString(value.textValue()), target);
        } else if (value.isNumber()) {
            appendNumber(value.decimalValue(), target);
        } else if (value.isBoolean()) {
            target.append(value.booleanValue());
        } else if (value.isNull()) {
            target.append("null");
        } else {
            throw new IllegalArgumentException("Unsupported JSON node in theme canonicalization: " + value.getNodeType());
        }
    }

    private void appendObject(JsonNode object, StringBuilder target) {
        List<Map.Entry<String, JsonNode>> fields = new ArrayList<>();
        object.fields().forEachRemaining(fields::add);
        fields.sort(Map.Entry.comparingByKey());

        target.append('{');
        for (int index = 0; index < fields.size(); index++) {
            if (index > 0) {
                target.append(',');
            }
            Map.Entry<String, JsonNode> field = fields.get(index);
            appendString(field.getKey(), target);
            target.append(':');
            append(field.getValue(), target);
        }
        target.append('}');
    }

    private void appendArray(JsonNode array, StringBuilder target) {
        target.append('[');
        for (int index = 0; index < array.size(); index++) {
            if (index > 0) {
                target.append(',');
            }
            append(array.get(index), target);
        }
        target.append(']');
    }

    private void appendString(String value, StringBuilder target) {
        try {
            target.append(STRING_ENCODER.writeValueAsString(value));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Unable to encode canonical theme string", exception);
        }
    }

    private void appendNumber(BigDecimal value, StringBuilder target) {
        if (value.compareTo(BigDecimal.ZERO) == 0) {
            target.append('0');
            return;
        }
        BigDecimal normalized = value.stripTrailingZeros();
        if (normalized.scale() < 0) {
            normalized = normalized.setScale(0);
        }
        target.append(normalized.toPlainString());
    }

    private String normalizeString(String value) {
        String normalized = Normalizer.normalize(value, Normalizer.Form.NFC);
        if (normalized.matches("^#[A-Fa-f0-9]{6}$")) {
            return normalized.toUpperCase(Locale.ROOT);
        }
        return normalized;
    }
}
