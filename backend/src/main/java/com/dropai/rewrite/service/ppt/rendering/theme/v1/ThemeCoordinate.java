package com.dropai.rewrite.service.ppt.rendering.theme.v1;

import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public record ThemeCoordinate(String themeId, String themeVersion) implements Comparable<ThemeCoordinate> {
    private static final Pattern THEME_ID = Pattern.compile("^[a-z][a-z0-9-]*$");
    private static final Pattern VERSION = Pattern.compile("^[0-9]+\\.[0-9]+\\.[0-9]+$");
    private static final Pattern REFERENCE = Pattern.compile("^([a-z][a-z0-9-]*)@([0-9]+\\.[0-9]+\\.[0-9]+)$");

    public ThemeCoordinate {
        Objects.requireNonNull(themeId, "themeId");
        Objects.requireNonNull(themeVersion, "themeVersion");
        if (!THEME_ID.matcher(themeId).matches()) {
            throw new IllegalArgumentException("Invalid theme id: " + themeId);
        }
        if (!VERSION.matcher(themeVersion).matches()) {
            throw new IllegalArgumentException("Invalid theme version: " + themeVersion);
        }
    }

    public static ThemeCoordinate parse(String reference) {
        Objects.requireNonNull(reference, "reference");
        Matcher matcher = REFERENCE.matcher(reference);
        if (!matcher.matches()) {
            throw new IllegalArgumentException("Invalid theme reference: " + reference);
        }
        return new ThemeCoordinate(matcher.group(1), matcher.group(2));
    }

    public String reference() {
        return themeId + "@" + themeVersion;
    }

    @Override
    public int compareTo(ThemeCoordinate other) {
        return reference().compareTo(other.reference());
    }
}
