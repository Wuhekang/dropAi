package com.dropai.rewrite.service.ppt.rendering.theme.v1;

import com.dropai.rewrite.service.ppt.rendering.contract.v1.PptQualityCode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ThemeResolverTest {
    @Test
    void resolvesFullyConcreteImmutableAcademicPurple() {
        ResolvedTheme resolved = engine(Set.of("Microsoft YaHei"))
                .resolve(ThemeResolutionRequest.academicPurpleV1());

        assertEquals("academic-purple", resolved.themeId());
        assertEquals("1.0.0", resolved.themeVersion());
        assertEquals(
                List.of("academic-base@1.0.0", "academic-purple@1.0.0"),
                resolved.inheritanceChain());
        assertEquals("#7257FF", resolved.document().path("colors").path("accent").path("primary").asText());
        assertEquals(
                "#7257FF",
                resolved.document().path("components").path("tableHeader").path("fillColor").asText());
        assertFalse(resolved.document().path("components").path("tableHeader").has("fillToken"));
        assertEquals("Microsoft YaHei", resolved.document()
                .path("typography").path("resolvedFamilies").path("display").asText());
        assertTrue(resolved.hashManifest().values().stream()
                .allMatch(hash -> hash.matches("sha256:[a-f0-9]{64}")));
        ThemeHasher hasher = new ThemeHasher(new ThemeCanonicalizer());
        assertEquals(hasher.hashNamedValues(resolved.sourceHashes()), resolved.themeSourceHash());
        assertNotEquals(
                resolved.sourceHashes().get("academic-purple@1.0.0"),
                resolved.themeSourceHash());

        ObjectNode escaped = resolved.document();
        ((ObjectNode) escaped.path("colors").path("accent")).put("primary", "#000000");
        assertEquals("#7257FF", resolved.document().path("colors").path("accent").path("primary").asText());
    }

    @Test
    void sameInputsProduceIdenticalCanonicalThemeAndAllHashes() {
        ThemeEngine engine = engine(Set.of("Microsoft YaHei", "Noto Sans CJK SC"));
        ResolvedTheme first = engine.resolve(ThemeResolutionRequest.academicPurpleV1());
        ResolvedTheme second = engine.resolve(ThemeResolutionRequest.academicPurpleV1());

        assertEquals(first.canonicalDocument(), second.canonicalDocument());
        assertEquals(first.sourceHashes(), second.sourceHashes());
        assertEquals(first.hashManifest(), second.hashManifest());
        assertEquals(first.contrastResults(), second.contrastResults());
    }

    @Test
    void aggregateThemeSourceHashChangesWhenEitherParentOrChildSourceChanges() {
        ThemeHasher hasher = new ThemeHasher(new ThemeCanonicalizer());
        String original = hasher.hashNamedValues(Map.of(
                "academic-base@1.0.0", "sha256:" + "a".repeat(64),
                "academic-purple@1.0.0", "sha256:" + "b".repeat(64)));
        String parentChanged = hasher.hashNamedValues(Map.of(
                "academic-base@1.0.0", "sha256:" + "c".repeat(64),
                "academic-purple@1.0.0", "sha256:" + "b".repeat(64)));
        String childChanged = hasher.hashNamedValues(Map.of(
                "academic-base@1.0.0", "sha256:" + "a".repeat(64),
                "academic-purple@1.0.0", "sha256:" + "d".repeat(64)));
        assertNotEquals(original, parentChanged);
        assertNotEquals(original, childChanged);
    }

    @Test
    void actualDeclaredFallbackChangesFontAndResolvedThemeHashes() {
        ResolvedTheme exact = engine(Set.of("Microsoft YaHei"))
                .resolve(ThemeResolutionRequest.academicPurpleV1());
        ResolvedTheme fallback = engine(Set.of("Noto Sans CJK SC"))
                .resolve(ThemeResolutionRequest.academicPurpleV1());

        assertNotEquals(exact.fontProfile().profileHash(), fallback.fontProfile().profileHash());
        assertNotEquals(exact.resolvedThemeHash(), fallback.resolvedThemeHash());
        assertTrue(fallback.fontProfile().usedFallback("display"));
    }

    @Test
    void unsupportedVersionUnknownParentAndCircularInheritanceFailExplicitly() {
        ThemeValidationException version = assertThrows(
                ThemeValidationException.class,
                () -> engine(Set.of("Microsoft YaHei")).resolve(
                        new ThemeResolutionRequest("academic-purple", "9.9.9", FontProfile.CJK_ACADEMIC_V1)));
        assertEquals(PptQualityCode.UNSUPPORTED_SCHEMA_VERSION, version.qualityCode());

        ThemeRegistry unknownParent = new ThemeRegistry(List.of(
                new ThemeRegistry.Registration(
                        new ThemeCoordinate("unknown-parent", "1.0.0"),
                        "ppt/themes/v1/unknown-parent.json",
                        true)));
        ThemeValidationException unknown = assertThrows(
                ThemeValidationException.class,
                () -> resolver(unknownParent, Set.of("Microsoft YaHei")).resolve(
                        new ThemeResolutionRequest("unknown-parent", "1.0.0", FontProfile.CJK_ACADEMIC_V1)));
        assertEquals(PptQualityCode.INVALID_REFERENCE, unknown.qualityCode());
        assertTrue(unknown.getMessage().contains("missing-parent@1.0.0"));

        ThemeRegistry cycle = new ThemeRegistry(List.of(
                new ThemeRegistry.Registration(
                        new ThemeCoordinate("cycle-a", "1.0.0"),
                        "ppt/themes/v1/cycle-a.json",
                        true),
                new ThemeRegistry.Registration(
                        new ThemeCoordinate("cycle-b", "1.0.0"),
                        "ppt/themes/v1/cycle-b.json",
                        false)));
        ThemeValidationException circular = assertThrows(
                ThemeValidationException.class,
                () -> resolver(cycle, Set.of("Microsoft YaHei")).resolve(
                        new ThemeResolutionRequest("cycle-a", "1.0.0", FontProfile.CJK_ACADEMIC_V1)));
        assertEquals(PptQualityCode.INVALID_REFERENCE, circular.qualityCode());
        assertTrue(circular.getMessage().contains("Circular theme inheritance"));
    }

    @Test
    void malformedThemeIdAndVersionRemainTypedThemeResolutionFailures() {
        ThemeEngine engine = engine(Set.of("Microsoft YaHei"));
        ThemeValidationException badId = assertThrows(
                ThemeValidationException.class,
                () -> engine.resolve(new ThemeResolutionRequest(
                        "Academic Purple",
                        "1.0.0",
                        FontProfile.CJK_ACADEMIC_V1)));
        assertEquals(PptQualityCode.INVALID_REFERENCE, badId.qualityCode());

        ThemeValidationException badVersion = assertThrows(
                ThemeValidationException.class,
                () -> engine.resolve(new ThemeResolutionRequest(
                        "academic-purple",
                        "v1",
                        FontProfile.CJK_ACADEMIC_V1)));
        assertEquals(PptQualityCode.UNSUPPORTED_SCHEMA_VERSION, badVersion.qualityCode());
    }

    private ThemeEngine engine(Set<String> fonts) {
        return ThemeEngine.academicV1(fonts);
    }

    private ThemeResolver resolver(ThemeRegistry registry, Set<String> fonts) {
        ThemeCanonicalizer canonicalizer = new ThemeCanonicalizer();
        ThemeHasher hasher = new ThemeHasher(canonicalizer);
        return new ThemeResolver(
                registry,
                new ThemeLoader(hasher),
                new ThemeValidator(),
                new FontAvailabilityChecker(fonts, hasher),
                new ColorContrastChecker(),
                canonicalizer,
                hasher);
    }
}
