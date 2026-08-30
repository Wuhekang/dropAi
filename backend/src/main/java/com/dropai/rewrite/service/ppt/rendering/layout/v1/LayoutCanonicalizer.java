package com.dropai.rewrite.service.ppt.rendering.layout.v1;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class LayoutCanonicalizer {
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
            List<Map.Entry<String, JsonNode>> fields = new ArrayList<>();
            value.fields().forEachRemaining(fields::add);
            fields.sort(Map.Entry.comparingByKey());
            target.append('{');
            for (int index = 0; index < fields.size(); index++) {
                if (index > 0) {
                    target.append(',');
                }
                appendString(fields.get(index).getKey(), target);
                target.append(':');
                append(fields.get(index).getValue(), target);
            }
            target.append('}');
        } else if (value.isArray()) {
            target.append('[');
            for (int index = 0; index < value.size(); index++) {
                if (index > 0) {
                    target.append(',');
                }
                append(value.get(index), target);
            }
            target.append(']');
        } else if (value.isTextual()) {
            appendString(Normalizer.normalize(value.textValue(), Normalizer.Form.NFC), target);
        } else if (value.isNumber()) {
            appendNumber(value.decimalValue(), target);
        } else if (value.isBoolean()) {
            target.append(value.booleanValue());
        } else if (value.isNull()) {
            target.append("null");
        } else {
            throw new IllegalArgumentException("Unsupported JSON node: " + value.getNodeType());
        }
    }

    private void appendString(String value, StringBuilder target) {
        try {
            target.append(STRING_ENCODER.writeValueAsString(value));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Unable to encode canonical layout string", exception);
        }
    }

    private void appendNumber(BigDecimal value, StringBuilder target) {
        if (value.compareTo(BigDecimal.ZERO) == 0) {
            target.append('0');
            return;
        }
        BigDecimal normalized = value.stripTrailingZeros();
        target.append(normalized.scale() < 0 ? normalized.setScale(0).toPlainString() : normalized.toPlainString());
    }
}
