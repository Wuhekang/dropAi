package com.dropai.rewrite.service.ppt.rendering.renderer.v1.element;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.awt.Color;
import java.awt.geom.Rectangle2D;
import java.util.HexFormat;

/** Strict scalar extraction and unit conversion for already validated plan fields. */
final class PlanElementSupport {
    static final double EMU_PER_POINT = 12_700d;

    private PlanElementSupport() {
    }

    static ObjectNode requiredObject(ObjectNode owner, String field) {
        JsonNode node = owner.path(field);
        if (!node.isObject()) {
            throw new IllegalArgumentException(field + " must be an object");
        }
        return (ObjectNode) node;
    }

    static ArrayNode requiredArray(ObjectNode owner, String field) {
        JsonNode node = owner.path(field);
        if (!node.isArray()) {
            throw new IllegalArgumentException(field + " must be an array");
        }
        return (ArrayNode) node;
    }

    static String requiredText(ObjectNode owner, String field) {
        JsonNode node = owner.path(field);
        if (!node.isTextual() || node.textValue().isBlank()) {
            throw new IllegalArgumentException(field + " must be a non-blank string");
        }
        return node.textValue();
    }

    static long requiredLong(ObjectNode owner, String field) {
        JsonNode node = owner.path(field);
        if (!node.isIntegralNumber() || !node.canConvertToLong()) {
            throw new IllegalArgumentException(field + " must be a long integer");
        }
        return node.longValue();
    }

    static int requiredInt(ObjectNode owner, String field) {
        long value = requiredLong(owner, field);
        if (value < Integer.MIN_VALUE || value > Integer.MAX_VALUE) {
            throw new IllegalArgumentException(field + " is outside integer range");
        }
        return (int) value;
    }

    static boolean requiredBoolean(ObjectNode owner, String field) {
        JsonNode node = owner.path(field);
        if (!node.isBoolean()) {
            throw new IllegalArgumentException(field + " must be boolean");
        }
        return node.booleanValue();
    }

    static Rectangle2D anchor(ObjectNode element) {
        return new Rectangle2D.Double(
                points(requiredLong(element, "xEmu")),
                points(requiredLong(element, "yEmu")),
                points(requiredLong(element, "widthEmu")),
                points(requiredLong(element, "heightEmu"))
        );
    }

    static double points(long emu) {
        return emu / EMU_PER_POINT;
    }

    static Color color(ObjectNode owner, String field) {
        String value = requiredText(owner, field);
        if (!value.matches("^#[A-Fa-f0-9]{6}$")) {
            throw new IllegalArgumentException(field + " is not an RGB color: " + value);
        }
        byte[] rgb = HexFormat.of().parseHex(value.substring(1));
        return new Color(Byte.toUnsignedInt(rgb[0]), Byte.toUnsignedInt(rgb[1]), Byte.toUnsignedInt(rgb[2]));
    }

    static byte[] rgbBytes(ObjectNode owner, String field) {
        String value = requiredText(owner, field);
        if (!value.matches("^#[A-Fa-f0-9]{6}$")) {
            throw new IllegalArgumentException(field + " is not an RGB color: " + value);
        }
        return HexFormat.of().parseHex(value.substring(1));
    }
}
