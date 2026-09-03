package com.dropai.rewrite.service.ppt.rendering.compiler.v1;

import com.dropai.rewrite.service.ppt.rendering.theme.v1.ResolvedTheme;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/** Converts already resolved theme tokens into concrete RenderPlan styles. */
final class ThemePlanStyleResolver {
    private final ObjectNode theme;

    ThemePlanStyleResolver(ResolvedTheme resolvedTheme) {
        this.theme = Objects.requireNonNull(resolvedTheme, "resolvedTheme").document();
    }

    ObjectNode textStyle(
            String typographyStyle,
            String component,
            String fontFaceId,
            String fontFamily,
            int fontSizeHundredthPt,
            long lineHeightEmu,
            String horizontalAlign,
            String verticalAlign
    ) {
        JsonNode typography = required(path("typography.styles." + typographyStyle), typographyStyle);
        JsonNode componentStyle = required(path("components." + component), component);
        ObjectNode style = JsonNodeFactory.instance.objectNode();
        style.put("fontFaceId", requireText(fontFaceId, "fontFaceId"));
        style.put("fontFamily", requireText(fontFamily, "fontFamily"));
        style.put("fontSizeHundredthPt", fontSizeHundredthPt);
        style.put("fontWeight", typography.path("weight").asInt());
        style.put("textColor", color(componentStyle.path("textColor").asText(
                path("colors.text.primary").asText())));
        style.put("horizontalAlign", horizontalAlign);
        style.put("verticalAlign", verticalAlign);
        style.put("lineSpacingPermille", Emu.permille(typography.path("lineSpacing").decimalValue()));
        style.put("lineHeightEmu", lineHeightEmu);
        style.put("paragraphSpaceBeforeEmu", 0L);
        style.put("paragraphSpaceAfterEmu", 0L);
        style.put("marginLeftEmu", 0L);
        style.put("marginRightEmu", 0L);
        style.put("marginTopEmu", 0L);
        style.put("marginBottomEmu", 0L);
        return style;
    }

    ObjectNode imageStyle() {
        JsonNode frame = required(path("components.imageFrame"), "imageFrame");
        ObjectNode style = JsonNodeFactory.instance.objectNode();
        style.put("opacityPermille", 1000);
        style.put("borderColor", color(frame.path("borderColor").asText()));
        style.put("borderWidthEmu", Emu.points(path("shape.borderWidthPt.thin").decimalValue()));
        style.put("cornerRadiusEmu", Emu.points(frame.path("radiusPt").decimalValue()));
        addShadow(style, "imageFrame");
        return style;
    }

    /** A theme-derived, effect-free style for a full-slide template surface. */
    ObjectNode templateDecorationStyle() {
        ObjectNode style = imageStyle();
        style.put("borderColor", color(path("colors.surface.canvas").asText()));
        style.put("borderWidthEmu", 0L);
        style.put("cornerRadiusEmu", 0L);
        style.remove("shadow");
        return style;
    }

    ObjectNode shapeStyle(String component) {
        JsonNode componentStyle = required(path("components." + component), component);
        ObjectNode style = JsonNodeFactory.instance.objectNode();
        style.put("fillColor", color(componentStyle.path("fillColor").asText(
                path("colors.surface.card").asText())));
        style.put("borderColor", color(componentStyle.path("borderColor").asText(
                path("colors.border.default").asText())));
        style.put("borderWidthEmu", Emu.points(path("shape.borderWidthPt.thin").decimalValue()));
        style.put("cornerRadiusEmu", componentStyle.has("radiusPt")
                ? Emu.points(componentStyle.path("radiusPt").decimalValue())
                : 0L);
        style.put("opacityPermille", 1000);
        addShadow(style, component);
        return style;
    }

    ObjectNode canvasStyle() {
        ObjectNode style = JsonNodeFactory.instance.objectNode();
        style.put("fillColor", color(path("colors.surface.canvas").asText()));
        style.put("borderColor", color(path("colors.surface.canvas").asText()));
        style.put("borderWidthEmu", 0L);
        style.put("cornerRadiusEmu", 0L);
        style.put("opacityPermille", 1000);
        return style;
    }

    ObjectNode connectorStyle() {
        ObjectNode style = JsonNodeFactory.instance.objectNode();
        style.put("lineColor", color(path("colors.accent.primary").asText()));
        style.put("lineWidthEmu", Emu.points(path("shape.borderWidthPt.normal").decimalValue()));
        style.put("dashStyle", "SOLID");
        return style;
    }

    ObjectNode tableStyle(
            String fontFamily,
            int fontSizeHundredthPt,
            String headerFontFaceId,
            int headerFontWeight,
            String bodyFontFaceId,
            int bodyFontWeight,
            int lineSpacingPermille,
            long horizontalCellPaddingEmu,
            long verticalCellPaddingEmu
    ) {
        JsonNode body = required(path("components.tableBody"), "tableBody");
        JsonNode header = required(path("components.tableHeader"), "tableHeader");
        ObjectNode style = JsonNodeFactory.instance.objectNode();
        style.put("headerFontFaceId", requireText(headerFontFaceId, "headerFontFaceId"));
        style.put("headerFontWeight", headerFontWeight);
        style.put("bodyFontFaceId", requireText(bodyFontFaceId, "bodyFontFaceId"));
        style.put("bodyFontWeight", bodyFontWeight);
        style.put("fontFamily", requireText(fontFamily, "fontFamily"));
        style.put("fontSizeHundredthPt", fontSizeHundredthPt);
        style.put("fontWeight", bodyFontWeight);
        style.put("textColor", color(body.path("textColor").asText()));
        style.put("headerFillColor", color(header.path("fillColor").asText()));
        style.put("bodyFillColor", color(body.path("fillColor").asText()));
        style.put("borderColor", color(body.path("borderColor").asText()));
        style.put("borderWidthEmu", Emu.points(path("shape.borderWidthPt.thin").decimalValue()));
        style.put("lineSpacingPermille", lineSpacingPermille);
        style.put("paragraphSpaceBeforeEmu", 0L);
        style.put("paragraphSpaceAfterEmu", 0L);
        style.put("cellMarginLeftEmu", horizontalCellPaddingEmu);
        style.put("cellMarginRightEmu", horizontalCellPaddingEmu);
        style.put("cellMarginTopEmu", verticalCellPaddingEmu);
        style.put("cellMarginBottomEmu", verticalCellPaddingEmu);
        return style;
    }

    ObjectNode styleSource(String component, String token) {
        ObjectNode source = JsonNodeFactory.instance.objectNode();
        source.put("component", requireText(component, "component"));
        source.put("themeToken", requireText(token, "themeToken"));
        return source;
    }

    int defaultFontSize(String typographyStyle) {
        return Emu.hundredthPoints(required(path("typography.styles." + typographyStyle), typographyStyle)
                .path("sizePt").decimalValue());
    }

    int minimumFontSize(String typographyStyle) {
        return Emu.hundredthPoints(required(path("typography.styles." + typographyStyle), typographyStyle)
                .path("minSizePt").decimalValue());
    }

    int fontWeight(String typographyStyle) {
        int weight = required(path("typography.styles." + typographyStyle), typographyStyle)
                .path("weight").asInt(0);
        if (weight < 100 || weight > 900 || weight % 100 != 0) {
            throw new IllegalArgumentException("Resolved theme has invalid font weight for " + typographyStyle);
        }
        return weight;
    }

    String componentTypographyStyle(String component) {
        JsonNode componentStyle = required(path("components." + component), component);
        JsonNode resolvedTypography = componentStyle.path("typographyStyle");
        if (resolvedTypography.isObject()) {
            List<String> matches = new ArrayList<>();
            theme.path("typography").path("styles").fields().forEachRemaining(entry -> {
                if (entry.getValue().equals(resolvedTypography)) {
                    matches.add(entry.getKey());
                }
            });
            Collections.sort(matches);
            if (matches.size() == 1) {
                return matches.get(0);
            }
            throw new IllegalArgumentException(
                    "Resolved theme component typographyStyle is not uniquely registered: " + component);
        }
        String token = componentStyle.path("typographyToken").asText();
        String prefix = "typography.styles.";
        if (token.startsWith(prefix) && token.length() > prefix.length()) {
            return token.substring(prefix.length());
        }
        throw new IllegalArgumentException(
                "Resolved theme component has invalid typography style: " + component);
    }

    int lineSpacingPermille(String typographyStyle) {
        return Emu.permille(required(path("typography.styles." + typographyStyle), typographyStyle)
                .path("lineSpacing").decimalValue());
    }

    int configuredMaxLines(String typographyStyle, int fallback) {
        JsonNode style = required(path("typography.styles." + typographyStyle), typographyStyle);
        int value = style.path("maxLines").asInt(fallback);
        return value > 0 ? value : fallback;
    }

    long safeTopEmu() {
        return Emu.inches(path("slide.safeArea.topIn").decimalValue());
    }

    long safeBottomEmu() {
        return Emu.inches(path("slide.safeArea.bottomIn").decimalValue());
    }

    long slideWidthEmu() {
        return Emu.inches(path("slide.widthIn").decimalValue());
    }

    long slideHeightEmu() {
        return Emu.inches(path("slide.heightIn").decimalValue());
    }

    int gridColumns() {
        return path("slide.grid.columns").asInt();
    }

    BigDecimal safeLeftIn() {
        return path("slide.safeArea.leftIn").decimalValue();
    }

    BigDecimal safeRightIn() {
        return path("slide.safeArea.rightIn").decimalValue();
    }

    BigDecimal gutterIn() {
        return path("slide.grid.gutterIn").decimalValue();
    }

    long spacingEmu(String spacingToken) {
        return Emu.points(required(path("spacing." + spacingToken), spacingToken).decimalValue());
    }

    String colorToken(String token) {
        return color(required(path(token), token).asText());
    }

    long pillRadiusEmu() {
        return Emu.points(required(path("shape.radiusPt.pill"), "shape.radiusPt.pill").decimalValue());
    }

    private void addShadow(ObjectNode style, String component) {
        JsonNode componentStyle = required(path("components." + component), component);
        JsonNode shadow = componentStyle.path("shadowStyle");
        if (!shadow.isObject()) {
            String shadowToken = componentStyle.path("shadowToken").asText("");
            if (!shadowToken.isBlank()) {
                shadow = required(path(shadowToken), shadowToken);
            }
        }
        if (!shadow.isObject()) {
            return;
        }
        ObjectNode resolved = style.putObject("shadow");
        resolved.put("color", color(path("colors.text.primary").asText()));
        resolved.put("opacityPermille", Emu.permille(shadow.path("opacity").decimalValue()));
        resolved.put("blurRadiusEmu", Emu.points(shadow.path("blurPt").decimalValue()));
        resolved.put("distanceEmu", Emu.points(shadow.path("distancePt").decimalValue()));
        resolved.put("angleThousandthDegree", shadow.path("angleDeg").decimalValue()
                .multiply(BigDecimal.valueOf(1000L))
                .setScale(0, java.math.RoundingMode.HALF_UP)
                .intValueExact());
    }

    private JsonNode path(String dottedPath) {
        JsonNode current = theme;
        for (String segment : dottedPath.split("\\.")) {
            current = current.path(segment);
        }
        return current;
    }

    private static JsonNode required(JsonNode value, String name) {
        if (value == null || value.isMissingNode() || value.isNull()) {
            throw new IllegalArgumentException("Resolved theme is missing " + name);
        }
        return value;
    }

    private static String color(String value) {
        if (value == null || !value.matches("^#[A-Fa-f0-9]{6}$")) {
            throw new IllegalArgumentException("Resolved theme contains an invalid color: " + value);
        }
        return value.toUpperCase(java.util.Locale.ROOT);
    }

    private static String requireText(String value, String name) {
        Objects.requireNonNull(value, name);
        if (value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value;
    }
}
