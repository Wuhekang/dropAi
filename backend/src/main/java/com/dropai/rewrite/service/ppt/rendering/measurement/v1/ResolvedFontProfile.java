package com.dropai.rewrite.service.ppt.rendering.measurement.v1;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.SortedMap;
import java.util.TreeMap;

public final class ResolvedFontProfile {
    public static final String MEASUREMENT_ENGINE_VERSION = "text-metrics-v1";

    private final String profileId;
    private final Map<String, List<String>> requestedFamilies;
    private final Map<String, String> selectedFamilies;
    private final Map<String, Boolean> fallbackApplied;
    private final Map<String, SortedMap<Integer, ResolvedFontFace>> faces;
    private final String measurementEngineVersion;
    private final String fontProfileHash;

    ResolvedFontProfile(
            String profileId,
            Map<String, List<String>> requestedFamilies,
            Map<String, String> selectedFamilies,
            Map<String, Boolean> fallbackApplied,
            Map<String, ? extends Map<Integer, ResolvedFontFace>> faces,
            String measurementEngineVersion,
            String fontProfileHash
    ) {
        this.profileId = requireText(profileId, "profileId");
        this.requestedFamilies = immutableListMap(requestedFamilies);
        this.selectedFamilies = sortedImmutableMap(selectedFamilies);
        this.fallbackApplied = sortedImmutableMap(fallbackApplied);
        this.faces = immutableFaceMap(faces);
        this.measurementEngineVersion = requireText(measurementEngineVersion, "measurementEngineVersion");
        this.fontProfileHash = requireText(fontProfileHash, "fontProfileHash");
    }

    public String profileId() {
        return profileId;
    }

    public Map<String, List<String>> requestedFamilies() {
        return requestedFamilies;
    }

    public Map<String, String> selectedFamilies() {
        return selectedFamilies;
    }

    public Map<String, Boolean> fallbackApplied() {
        return fallbackApplied;
    }

    public Map<String, SortedMap<Integer, ResolvedFontFace>> faces() {
        return faces;
    }

    public ResolvedFontFace requireFace(String role, int weight) {
        Map<Integer, ResolvedFontFace> byWeight = faces.get(role);
        if (byWeight == null || !byWeight.containsKey(weight)) {
            throw new MeasurementException(
                    com.dropai.rewrite.service.ppt.rendering.contract.v1.PptQualityCode.FONT_UNAVAILABLE,
                    "No resolved font face for role " + role + " at weight " + weight);
        }
        return byWeight.get(weight);
    }

    public String measurementEngineVersion() {
        return measurementEngineVersion;
    }

    public String fontProfileHash() {
        return fontProfileHash;
    }

    private static Map<String, List<String>> immutableListMap(Map<String, List<String>> source) {
        Objects.requireNonNull(source, "source");
        TreeMap<String, List<String>> copy = new TreeMap<>();
        source.forEach((key, value) -> copy.put(requireText(key, "role"), List.copyOf(value)));
        return Collections.unmodifiableMap(new LinkedHashMap<>(copy));
    }

    private static <T> Map<String, T> sortedImmutableMap(Map<String, T> source) {
        Objects.requireNonNull(source, "source");
        return Collections.unmodifiableMap(new LinkedHashMap<>(new TreeMap<>(source)));
    }

    private static Map<String, SortedMap<Integer, ResolvedFontFace>> immutableFaceMap(
            Map<String, ? extends Map<Integer, ResolvedFontFace>> source
    ) {
        Objects.requireNonNull(source, "source");
        List<String> roles = new ArrayList<>(source.keySet());
        Collections.sort(roles);
        LinkedHashMap<String, SortedMap<Integer, ResolvedFontFace>> copy = new LinkedHashMap<>();
        for (String role : roles) {
            TreeMap<Integer, ResolvedFontFace> weights = new TreeMap<>(source.get(role));
            copy.put(role, Collections.unmodifiableSortedMap(weights));
        }
        return Collections.unmodifiableMap(copy);
    }

    private static String requireText(String value, String name) {
        Objects.requireNonNull(value, name);
        if (value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value;
    }
}
