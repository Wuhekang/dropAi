package com.dropai.rewrite.service.ppt.rendering.validation.v1;

import com.dropai.rewrite.service.ppt.rendering.measurement.v1.DeterministicTextMetricsService;
import com.dropai.rewrite.service.ppt.rendering.measurement.v1.ResolvedFontProfile;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;

/** External invariants that cannot be reconstructed from render-plan.v1 alone. */
public final class RenderPlanValidationContext {
    private final String expectedPresentationId;
    private final String expectedSourceTreeHash;
    private final String expectedThemeHash;
    private final String expectedLayoutCatalogHash;
    private final String expectedFontProfileHash;
    private final EngineExpectation expectedEngine;
    private final FontProfileExpectation expectedFontProfile;
    private final ResolvedFontProfile resolvedFontProfile;
    private final DeterministicTextMetricsService textMetrics;
    private final long expectedSlideWidthEmu;
    private final long expectedSlideHeightEmu;
    private final List<String> expectedSourcePageIds;
    private final Map<String, PageExpectation> expectedPagesBySourceId;
    private final Set<String> knownLayoutIds;
    private final Map<String, Set<String>> allowedContainedTextComponentsByLayout;
    private final Set<String> knownStyleComponents;
    private final Set<String> knownThemeTokens;
    private final Map<String, String> expectedAssetHashes;
    private final SafeArea safeArea;
    private final StatusStyleExpectation statusStyleExpectation;
    private final Map<String, Integer> minimumFontSizeByComponent;
    private final int defaultMinimumFontSizeHundredthPt;

    public RenderPlanValidationContext(
            String expectedPresentationId,
            String expectedSourceTreeHash,
            String expectedThemeHash,
            String expectedLayoutCatalogHash,
            String expectedFontProfileHash,
            EngineExpectation expectedEngine,
            FontProfileExpectation expectedFontProfile,
            ResolvedFontProfile resolvedFontProfile,
            DeterministicTextMetricsService textMetrics,
            long expectedSlideWidthEmu,
            long expectedSlideHeightEmu,
            List<String> expectedSourcePageIds,
            Map<String, PageExpectation> expectedPagesBySourceId,
            Set<String> knownLayoutIds,
            Map<String, ? extends Set<String>> allowedContainedTextComponentsByLayout,
            Set<String> knownStyleComponents,
            Set<String> knownThemeTokens,
            Map<String, String> expectedAssetHashes,
            SafeArea safeArea,
            StatusStyleExpectation statusStyleExpectation,
            Map<String, Integer> minimumFontSizeByComponent,
            int defaultMinimumFontSizeHundredthPt
    ) {
        this.expectedPresentationId = requireText(expectedPresentationId, "expectedPresentationId");
        this.expectedSourceTreeHash = requireHash(expectedSourceTreeHash, "expectedSourceTreeHash");
        this.expectedThemeHash = requireHash(expectedThemeHash, "expectedThemeHash");
        this.expectedLayoutCatalogHash = requireHash(expectedLayoutCatalogHash,
                "expectedLayoutCatalogHash");
        this.expectedFontProfileHash = requireHash(expectedFontProfileHash, "expectedFontProfileHash");
        this.expectedEngine = Objects.requireNonNull(expectedEngine, "expectedEngine");
        this.expectedFontProfile = Objects.requireNonNull(expectedFontProfile, "expectedFontProfile");
        this.resolvedFontProfile = Objects.requireNonNull(resolvedFontProfile, "resolvedFontProfile");
        this.textMetrics = Objects.requireNonNull(textMetrics, "textMetrics");
        if (!expectedFontProfileHash.equals(resolvedFontProfile.fontProfileHash())) {
            throw new IllegalArgumentException("Resolved font profile hash differs from expectedFontProfileHash");
        }
        if (expectedSlideWidthEmu < 1 || expectedSlideHeightEmu < 1) {
            throw new IllegalArgumentException("Expected slide dimensions must be positive");
        }
        this.expectedSlideWidthEmu = expectedSlideWidthEmu;
        this.expectedSlideHeightEmu = expectedSlideHeightEmu;
        this.expectedSourcePageIds = List.copyOf(Objects.requireNonNull(expectedSourcePageIds,
                "expectedSourcePageIds"));
        this.expectedPagesBySourceId = immutablePageMap(expectedPagesBySourceId);
        if (!new LinkedHashSet<>(this.expectedSourcePageIds)
                .equals(this.expectedPagesBySourceId.keySet())) {
            throw new IllegalArgumentException(
                    "Expected page contracts must match expectedSourcePageIds exactly");
        }
        this.knownLayoutIds = Collections.unmodifiableSet(new LinkedHashSet<>(
                Objects.requireNonNull(knownLayoutIds, "knownLayoutIds").stream().sorted().toList()));
        this.allowedContainedTextComponentsByLayout = immutableSetMap(
                allowedContainedTextComponentsByLayout);
        this.knownStyleComponents = immutableSet(knownStyleComponents, "knownStyleComponents");
        this.knownThemeTokens = immutableSet(knownThemeTokens, "knownThemeTokens");
        this.expectedAssetHashes = immutableMap(expectedAssetHashes);
        this.safeArea = Objects.requireNonNull(safeArea, "safeArea");
        this.statusStyleExpectation = Objects.requireNonNull(
                statusStyleExpectation, "statusStyleExpectation");
        this.minimumFontSizeByComponent = immutableIntMap(minimumFontSizeByComponent);
        if (defaultMinimumFontSizeHundredthPt < 1) {
            throw new IllegalArgumentException("defaultMinimumFontSizeHundredthPt must be positive");
        }
        this.defaultMinimumFontSizeHundredthPt = defaultMinimumFontSizeHundredthPt;
    }

    public String expectedPresentationId() {
        return expectedPresentationId;
    }

    public String expectedSourceTreeHash() {
        return expectedSourceTreeHash;
    }

    public String expectedThemeHash() {
        return expectedThemeHash;
    }

    public String expectedLayoutCatalogHash() {
        return expectedLayoutCatalogHash;
    }

    public String expectedFontProfileHash() {
        return expectedFontProfileHash;
    }

    public EngineExpectation expectedEngine() {
        return expectedEngine;
    }

    public FontProfileExpectation expectedFontProfile() {
        return expectedFontProfile;
    }

    public ResolvedFontProfile resolvedFontProfile() {
        return resolvedFontProfile;
    }

    public DeterministicTextMetricsService textMetrics() {
        return textMetrics;
    }

    public long expectedSlideWidthEmu() {
        return expectedSlideWidthEmu;
    }

    public long expectedSlideHeightEmu() {
        return expectedSlideHeightEmu;
    }

    public List<String> expectedSourcePageIds() {
        return expectedSourcePageIds;
    }

    public PageExpectation expectedPage(String sourcePageId) {
        return sourcePageId == null ? null : expectedPagesBySourceId.get(sourcePageId);
    }

    public Set<String> knownLayoutIds() {
        return knownLayoutIds;
    }

    public Set<String> allowedContainedTextComponents(String layoutId) {
        return allowedContainedTextComponentsByLayout.getOrDefault(layoutId, Set.of());
    }

    public Set<String> knownStyleComponents() {
        return knownStyleComponents;
    }

    public Set<String> knownThemeTokens() {
        return knownThemeTokens;
    }

    public Map<String, String> expectedAssetHashes() {
        return expectedAssetHashes;
    }

    public SafeArea safeArea() {
        return safeArea;
    }

    public StatusStyleExpectation statusStyleExpectation() {
        return statusStyleExpectation;
    }

    public int minimumFontSize(String component) {
        return component == null
                ? defaultMinimumFontSizeHundredthPt
                : minimumFontSizeByComponent.getOrDefault(component, defaultMinimumFontSizeHundredthPt);
    }

    private static Map<String, String> immutableMap(Map<String, String> source) {
        Objects.requireNonNull(source, "source");
        return Collections.unmodifiableMap(new LinkedHashMap<>(new TreeMap<>(source)));
    }

    private static Set<String> immutableSet(Set<String> source, String field) {
        Objects.requireNonNull(source, field);
        if (source.isEmpty() || source.stream().anyMatch(value -> value == null || value.isBlank())) {
            throw new IllegalArgumentException(field + " must contain non-blank values");
        }
        return Collections.unmodifiableSet(new LinkedHashSet<>(source.stream().sorted().toList()));
    }

    private static Map<String, Integer> immutableIntMap(Map<String, Integer> source) {
        Objects.requireNonNull(source, "source");
        TreeMap<String, Integer> sorted = new TreeMap<>();
        source.forEach((key, value) -> {
            if (key == null || key.isBlank() || value == null || value < 1) {
                throw new IllegalArgumentException("Minimum font map must contain non-blank keys and positive values");
            }
            sorted.put(key, value);
        });
        return Collections.unmodifiableMap(new LinkedHashMap<>(sorted));
    }

    private static Map<String, Set<String>> immutableSetMap(
            Map<String, ? extends Set<String>> source
    ) {
        Objects.requireNonNull(source, "allowedContainedTextComponentsByLayout");
        TreeMap<String, Set<String>> sorted = new TreeMap<>();
        source.forEach((layoutId, components) -> {
            String id = requireText(layoutId, "layoutId");
            Objects.requireNonNull(components, "allowed overlap components");
            sorted.put(id, Collections.unmodifiableSet(new LinkedHashSet<>(
                    components.stream().map(component -> requireText(component, "component"))
                            .sorted().toList())));
        });
        return Collections.unmodifiableMap(new LinkedHashMap<>(sorted));
    }

    private static Map<String, PageExpectation> immutablePageMap(
            Map<String, PageExpectation> source
    ) {
        Objects.requireNonNull(source, "expectedPagesBySourceId");
        LinkedHashMap<String, PageExpectation> ordered = new LinkedHashMap<>();
        source.forEach((pageId, expectation) -> ordered.put(
                requireText(pageId, "sourcePageId"),
                Objects.requireNonNull(expectation, "pageExpectation")));
        return Collections.unmodifiableMap(ordered);
    }

    private static String requireText(String value, String field) {
        Objects.requireNonNull(value, field);
        if (value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value;
    }

    private static String requireHash(String value, String field) {
        String hash = requireText(value, field);
        if (!hash.matches("^sha256:[a-f0-9]{64}$")) {
            throw new IllegalArgumentException(field + " must be a prefixed lowercase SHA-256");
        }
        return hash;
    }

    private static String requireColor(String value, String field) {
        String color = requireText(value, field).toUpperCase(java.util.Locale.ROOT);
        if (!color.matches("^#[A-F0-9]{6}$")) {
            throw new IllegalArgumentException(field + " must be a six-digit RGB color");
        }
        return color;
    }

    public record SafeArea(long leftEmu, long topEmu, long rightEmu, long bottomEmu) {
        public SafeArea {
            if (leftEmu < 0 || topEmu < 0 || rightEmu < 0 || bottomEmu < 0) {
                throw new IllegalArgumentException("Safe-area insets must be non-negative");
            }
        }

        public static SafeArea none() {
            return new SafeArea(0, 0, 0, 0);
        }
    }

    public record PageExpectation(String pageType, String layoutId) {
        public PageExpectation {
            pageType = requireText(pageType, "pageType");
            layoutId = requireText(layoutId, "layoutId");
        }
    }

    public record StatusStyleExpectation(
            String successFillColor,
            String warningFillColor,
            String dangerFillColor,
            String textColor
    ) {
        public StatusStyleExpectation {
            successFillColor = requireColor(successFillColor, "successFillColor");
            warningFillColor = requireColor(warningFillColor, "warningFillColor");
            dangerFillColor = requireColor(dangerFillColor, "dangerFillColor");
            textColor = requireColor(textColor, "textColor");
        }

        public String expectedFillColor(String value) {
            String normalized = requireText(value, "status value")
                    .strip().toUpperCase(java.util.Locale.ROOT);
            if (Set.of("通过", "正常", "成功", "PASS", "PASSED", "OK").contains(normalized)) {
                return successFillColor;
            }
            if (Set.of("失败", "异常", "未通过", "FAIL", "FAILED", "ERROR").contains(normalized)) {
                return dangerFillColor;
            }
            return warningFillColor;
        }
    }

    public record FontProfileExpectation(
            String profileId,
            String measurementEngineVersion,
            Map<String, FontFaceExpectation> faces
    ) {
        public FontProfileExpectation {
            profileId = requireText(profileId, "profileId");
            measurementEngineVersion = requireText(measurementEngineVersion, "measurementEngineVersion");
            Objects.requireNonNull(faces, "faces");
            if (faces.isEmpty()) {
                throw new IllegalArgumentException("Expected font profile must contain at least one face");
            }
            TreeMap<String, FontFaceExpectation> sorted = new TreeMap<>();
            faces.forEach((id, face) -> {
                String normalizedId = requireText(id, "fontFaceId");
                if (!normalizedId.matches("^[A-Za-z0-9][A-Za-z0-9._-]*$")) {
                    throw new IllegalArgumentException("Invalid fontFaceId: " + normalizedId);
                }
                sorted.put(normalizedId, Objects.requireNonNull(face, "fontFace"));
            });
            faces = Collections.unmodifiableMap(new LinkedHashMap<>(sorted));
        }
    }

    public record EngineExpectation(
            String engineVersion,
            String themeId,
            String themeVersion,
            String layoutCatalogVersion
    ) {
        public EngineExpectation {
            engineVersion = requireText(engineVersion, "engineVersion");
            themeId = requireText(themeId, "themeId");
            themeVersion = requireText(themeVersion, "themeVersion");
            layoutCatalogVersion = requireText(layoutCatalogVersion, "layoutCatalogVersion");
        }
    }

    public record FontFaceExpectation(
            String role,
            int weight,
            String selectedFamily,
            String postScriptName,
            String fontSource,
            String fontFingerprint,
            boolean fallbackApplied
    ) {
        public FontFaceExpectation {
            role = requireText(role, "role");
            if (weight < 100 || weight > 900 || weight % 100 != 0) {
                throw new IllegalArgumentException("Font weight must be from 100 to 900 in increments of 100");
            }
            selectedFamily = requireText(selectedFamily, "selectedFamily");
            postScriptName = requireText(postScriptName, "postScriptName");
            fontSource = requireText(fontSource, "fontSource");
            if (!Set.of("SYSTEM", "BUNDLED", "PROVIDED").contains(fontSource)) {
                throw new IllegalArgumentException("Unsupported font source: " + fontSource);
            }
            fontFingerprint = requireHash(fontFingerprint, "fontFingerprint");
        }
    }
}
