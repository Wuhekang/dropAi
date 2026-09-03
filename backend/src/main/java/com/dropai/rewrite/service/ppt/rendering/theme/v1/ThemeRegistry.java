package com.dropai.rewrite.service.ppt.rendering.theme.v1;

import com.dropai.rewrite.service.ppt.rendering.contract.v1.PptQualityCode;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

public final class ThemeRegistry {
    public static final String ACADEMIC_PURPLE = "academic-purple";
    public static final String SMALL_BEAR_WATERCOLOR_BLUE_V1 = "small-bear-watercolor-blue-v1";
    public static final String ACADEMIC_BASE = "academic-base";
    public static final String VERSION_1_0_0 = "1.0.0";

    private final Map<ThemeCoordinate, Registration> registrations;
    private final Set<String> officialThemeIds;

    public ThemeRegistry(Collection<Registration> registrations) {
        Objects.requireNonNull(registrations, "registrations");
        TreeMap<ThemeCoordinate, Registration> byCoordinate = new TreeMap<>();
        TreeSet<String> officialIds = new TreeSet<>();
        for (Registration registration : registrations) {
            Registration existing = byCoordinate.putIfAbsent(registration.coordinate(), registration);
            if (existing != null) {
                throw new ThemeValidationException(
                        PptQualityCode.DUPLICATE_ID,
                        "Duplicate theme registration: " + registration.coordinate().reference());
            }
            if (registration.official()) {
                officialIds.add(registration.coordinate().themeId());
            }
        }
        if (byCoordinate.isEmpty()) {
            throw new IllegalArgumentException("Theme registry must not be empty");
        }
        this.registrations = Collections.unmodifiableMap(new LinkedHashMap<>(byCoordinate));
        this.officialThemeIds = Collections.unmodifiableSet(officialIds);
    }

    public static ThemeRegistry academicV1() {
        return new ThemeRegistry(Set.of(
                new Registration(
                        new ThemeCoordinate(ACADEMIC_BASE, VERSION_1_0_0),
                        "ppt/themes/v1/academic-base.json",
                        false),
                new Registration(
                        new ThemeCoordinate(ACADEMIC_PURPLE, VERSION_1_0_0),
                        "ppt/themes/v1/academic-purple.json",
                        true),
                new Registration(
                        new ThemeCoordinate(SMALL_BEAR_WATERCOLOR_BLUE_V1, VERSION_1_0_0),
                        "ppt/themes/v1/small-bear-watercolor-blue-v1.json",
                        true)
        ));
    }

    public Registration require(ThemeCoordinate coordinate) {
        Objects.requireNonNull(coordinate, "coordinate");
        Registration registration = registrations.get(coordinate);
        if (registration == null) {
            throw new ThemeValidationException(
                    PptQualityCode.INVALID_REFERENCE,
                    "Unknown theme reference: " + coordinate.reference());
        }
        return registration;
    }

    public Registration requireOfficial(String themeId, String expectedVersion) {
        if (!officialThemeIds.contains(themeId)) {
            throw new ThemeValidationException(
                    PptQualityCode.INVALID_REFERENCE,
                    "Theme is not an official selectable theme: " + themeId);
        }
        ThemeCoordinate requested;
        try {
            requested = new ThemeCoordinate(themeId, expectedVersion);
        } catch (IllegalArgumentException exception) {
            throw new ThemeValidationException(
                    PptQualityCode.UNSUPPORTED_SCHEMA_VERSION,
                    "Unsupported theme version for " + themeId + ": " + expectedVersion);
        }
        Registration registration = registrations.get(requested);
        if (registration == null) {
            throw new ThemeValidationException(
                    PptQualityCode.UNSUPPORTED_SCHEMA_VERSION,
                    "Unsupported theme version: " + requested.reference());
        }
        return registration;
    }

    public Set<String> officialThemeIds() {
        return officialThemeIds;
    }

    public Map<ThemeCoordinate, Registration> registrations() {
        return registrations;
    }

    public record Registration(ThemeCoordinate coordinate, String classpathResource, boolean official) {
        public Registration {
            Objects.requireNonNull(coordinate, "coordinate");
            Objects.requireNonNull(classpathResource, "classpathResource");
            if (classpathResource.isBlank() || classpathResource.startsWith("/")
                    || classpathResource.contains("\\") || classpathResource.contains("..")) {
                throw new IllegalArgumentException("Invalid theme classpath resource: " + classpathResource);
            }
        }
    }
}
