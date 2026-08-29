package com.dropai.rewrite.service.ppt.rendering.theme.v1;

import com.dropai.rewrite.service.ppt.rendering.contract.v1.PptQualityCode;
import com.fasterxml.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Locale;
import java.util.TreeMap;

public final class ColorContrastChecker {
    public static final double NORMAL_TEXT_MINIMUM = 4.5d;

    private static final List<TokenPair> REQUIRED_PAIRS = List.of(
            new TokenPair("colors.text.primary", "colors.surface.canvas"),
            new TokenPair("colors.text.primary", "colors.surface.card"),
            new TokenPair("colors.text.secondary", "colors.surface.canvas"),
            new TokenPair("colors.text.secondary", "colors.surface.card"),
            new TokenPair("colors.text.inverse", "colors.surface.dark"),
            new TokenPair("colors.text.inverse", "colors.accent.primary")
    );

    public List<ContrastResult> evaluate(JsonNode theme) {
        TreeMap<String, TokenPair> pairs = new TreeMap<>();
        REQUIRED_PAIRS.forEach(pair -> pairs.put(pair.key(), pair));

        Iterator<Map.Entry<String, JsonNode>> components = theme.path("components").fields();
        while (components.hasNext()) {
            Map.Entry<String, JsonNode> component = components.next();
            JsonNode style = component.getValue();
            if (style.hasNonNull("fillToken") && style.hasNonNull("textToken")) {
                TokenPair pair = new TokenPair(
                        style.path("textToken").asText(),
                        style.path("fillToken").asText());
                pairs.put(pair.key(), pair);
            }
        }

        List<ContrastResult> results = new ArrayList<>();
        pairs.values().forEach(pair -> results.add(evaluatePair(theme, pair)));
        return List.copyOf(results);
    }

    public void requireReadable(Collection<ContrastResult> results) {
        List<String> failures = results.stream()
                .filter(result -> !result.passed())
                .map(result -> "COLOR_CONTRAST_INSUFFICIENT " + result.foregroundToken()
                        + " on " + result.backgroundToken()
                        + " requires " + format(result.minimumRatio())
                        + " but was " + format(result.actualRatio()))
                .sorted()
                .toList();
        if (!failures.isEmpty()) {
            throw new ThemeValidationException(PptQualityCode.SCHEMA_INVALID, failures);
        }
    }

    public ContrastResult evaluate(
            String foregroundToken,
            String backgroundToken,
            String foregroundHex,
            String backgroundHex,
            double minimumRatio
    ) {
        double rawRatio = contrastRatio(foregroundHex, backgroundHex);
        double reportedRatio = Math.round(rawRatio * 10_000d) / 10_000d;
        return new ContrastResult(
                foregroundToken,
                backgroundToken,
                foregroundHex.toUpperCase(Locale.ROOT),
                backgroundHex.toUpperCase(Locale.ROOT),
                minimumRatio,
                reportedRatio,
                rawRatio >= minimumRatio);
    }

    private ContrastResult evaluatePair(JsonNode theme, TokenPair pair) {
        String foreground = resolve(theme, pair.foregroundToken()).asText();
        String background = resolve(theme, pair.backgroundToken()).asText();
        return evaluate(
                pair.foregroundToken(),
                pair.backgroundToken(),
                foreground,
                background,
                NORMAL_TEXT_MINIMUM);
    }

    private double contrastRatio(String first, String second) {
        double firstLuminance = relativeLuminance(first);
        double secondLuminance = relativeLuminance(second);
        return (Math.max(firstLuminance, secondLuminance) + 0.05d)
                / (Math.min(firstLuminance, secondLuminance) + 0.05d);
    }

    private double relativeLuminance(String hex) {
        if (hex == null || !hex.matches("^#[A-Fa-f0-9]{6}$")) {
            throw new ThemeValidationException(PptQualityCode.SCHEMA_INVALID, "Invalid contrast color: " + hex);
        }
        double red = linearChannel(Integer.parseInt(hex.substring(1, 3), 16) / 255d);
        double green = linearChannel(Integer.parseInt(hex.substring(3, 5), 16) / 255d);
        double blue = linearChannel(Integer.parseInt(hex.substring(5, 7), 16) / 255d);
        return 0.2126d * red + 0.7152d * green + 0.0722d * blue;
    }

    private double linearChannel(double channel) {
        return channel <= 0.04045d
                ? channel / 12.92d
                : Math.pow((channel + 0.055d) / 1.055d, 2.4d);
    }

    private JsonNode resolve(JsonNode theme, String dottedPath) {
        JsonNode current = theme;
        for (String segment : dottedPath.split("\\.")) {
            current = current.path(segment);
        }
        if (current.isMissingNode()) {
            throw new ThemeValidationException(
                    PptQualityCode.INVALID_REFERENCE,
                    "Unknown color token: " + dottedPath);
        }
        return current;
    }

    private String format(double value) {
        return String.format(java.util.Locale.ROOT, "%.4f", value);
    }

    private record TokenPair(String foregroundToken, String backgroundToken) {
        private String key() {
            return foregroundToken + "|" + backgroundToken;
        }
    }

    public record ContrastResult(
            String foregroundToken,
            String backgroundToken,
            String foregroundHex,
            String backgroundHex,
            double minimumRatio,
            double actualRatio,
            boolean passed
    ) {
    }
}
