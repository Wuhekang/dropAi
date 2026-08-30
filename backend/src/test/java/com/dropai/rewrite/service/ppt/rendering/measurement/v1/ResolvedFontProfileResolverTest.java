package com.dropai.rewrite.service.ppt.rendering.measurement.v1;

import com.dropai.rewrite.service.ppt.rendering.contract.v1.PptQualityCode;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ResolvedFontProfileResolverTest {
    @Test
    void resolvesOneActualFamilyForEveryRequiredWeightAndHashesFontBytes() {
        FontFaceInventory inventory = inventory(Map.of(
                key("Microsoft YaHei", 400), List.of(resource("Microsoft YaHei", 400, "regular")),
                key("Microsoft YaHei", 700), List.of(resource("Microsoft YaHei", 700, "bold"))));

        ResolvedFontProfile profile = resolver(inventory).resolve(
                "cjk-academic-v1",
                Map.of("body", List.of("Microsoft YaHei", "Noto Sans CJK SC")),
                Map.of("body", Set.of(400, 700)));

        assertEquals("Microsoft YaHei", profile.selectedFamilies().get("body"));
        assertFalse(profile.fallbackApplied().get("body"));
        assertTrue(profile.fontProfileHash().matches("sha256:[a-f0-9]{64}"));
        assertTrue(profile.requireFace("body", 400).fontFingerprint().matches("sha256:[a-f0-9]{64}"));
        assertEquals("text-metrics-v1", profile.measurementEngineVersion());
    }

    @Test
    void explicitFallbackAndDifferentActualBytesChangeTheResolvedProfileHash() {
        FontFaceInventory first = inventory(Map.of(
                key("Noto Sans CJK SC", 400), List.of(resource("Noto Sans CJK SC", 400, "noto-a"))));
        FontFaceInventory second = inventory(Map.of(
                key("Noto Sans CJK SC", 400), List.of(resource("Noto Sans CJK SC", 400, "noto-b"))));

        ResolvedFontProfile a = resolveFallback(first);
        ResolvedFontProfile b = resolveFallback(second);

        assertTrue(a.fallbackApplied().get("body"));
        assertEquals("Noto Sans CJK SC", a.selectedFamilies().get("body"));
        assertNotEquals(a.requireFace("body", 400).fontFingerprint(), b.requireFace("body", 400).fontFingerprint());
        assertNotEquals(a.fontProfileHash(), b.fontProfileHash());
    }

    @Test
    void fallbackSelectionChangesProfileHashEvenWhenTheUnderlyingBytesMatch() {
        FontFaceResource exactFace = resource("Microsoft YaHei", 400, "shared-font-bytes");
        FontFaceResource fallbackFace = resource("Noto Sans CJK SC", 400, "shared-font-bytes");
        ResolvedFontProfile exact = resolveExact(inventory(Map.of(
                key("Microsoft YaHei", 400), List.of(exactFace))));
        ResolvedFontProfile fallback = resolveFallback(inventory(Map.of(
                key("Noto Sans CJK SC", 400), List.of(fallbackFace))));

        assertEquals(
                exact.requireFace("body", 400).fontFingerprint(),
                fallback.requireFace("body", 400).fontFingerprint());
        assertNotEquals(exact.fontProfileHash(), fallback.fontProfileHash());
        assertTrue(fallback.fallbackApplied().get("body"));
    }

    @Test
    void undeclaredArialOrCalibriCanNeverBecomeASilentFallback() {
        FontFaceInventory defaultsOnly = inventory(Map.of(
                key("Arial", 400), List.of(resource("Arial", 400, "arial")),
                key("Calibri", 400), List.of(resource("Calibri", 400, "calibri"))));

        MeasurementException exception = assertThrows(
                MeasurementException.class,
                () -> resolveFallback(defaultsOnly));

        assertEquals(PptQualityCode.FONT_UNAVAILABLE, exception.qualityCode());
    }

    @Test
    void allWeightsMustComeFromOneDeclaredFamilyWithoutSyntheticMixing() {
        FontFaceInventory split = inventory(Map.of(
                key("Microsoft YaHei", 400), List.of(resource("Microsoft YaHei", 400, "yahei-regular")),
                key("Noto Sans CJK SC", 700), List.of(resource("Noto Sans CJK SC", 700, "noto-bold"))));

        MeasurementException exception = assertThrows(
                MeasurementException.class,
                () -> resolver(split).resolve(
                        "cjk-academic-v1",
                        Map.of("body", List.of("Microsoft YaHei", "Noto Sans CJK SC")),
                        Map.of("body", Set.of(400, 700))));

        assertEquals(PptQualityCode.FONT_UNAVAILABLE, exception.qualityCode());
    }

    @Test
    void ambiguousDifferentBytesForTheSameFaceAreBlocked() {
        FontFaceInventory ambiguous = inventory(Map.of(
                key("Microsoft YaHei", 400), List.of(
                        resource("Microsoft YaHei", 400, "first-file"),
                        resource("Microsoft YaHei", 400, "second-file"))));

        MeasurementException exception = assertThrows(
                MeasurementException.class,
                () -> resolver(ambiguous).resolve(
                        "cjk-academic-v1",
                        Map.of("body", List.of("Microsoft YaHei")),
                        Map.of("body", Set.of(400))));

        assertEquals(PptQualityCode.FONT_UNAVAILABLE, exception.qualityCode());
        assertTrue(exception.getMessage().contains("Ambiguous font bytes"));
    }

    @Test
    void inventoryOrderDoesNotChangeResolutionOrHashWhenBytesAreIdentical() {
        FontFaceResource alpha = resource("Microsoft YaHei", 400, "same-bytes");
        FontFaceResource systemCopy = new FontFaceResource(
                "Microsoft YaHei",
                alpha.postScriptName(),
                400,
                FontSource.SYSTEM,
                alpha.fontBytes());
        FontFaceResource providedCopy = new FontFaceResource(
                "Microsoft YaHei",
                alpha.postScriptName(),
                400,
                FontSource.PROVIDED,
                alpha.fontBytes());
        List<FontFaceResource> forward = List.of(systemCopy, providedCopy);
        List<FontFaceResource> reverse = new ArrayList<>(forward);
        Collections.reverse(reverse);

        ResolvedFontProfile first = resolveExact(inventory(Map.of(key("Microsoft YaHei", 400), forward)));
        ResolvedFontProfile second = resolveExact(inventory(Map.of(key("Microsoft YaHei", 400), reverse)));

        assertEquals(alpha.postScriptName(), first.requireFace("body", 400).postScriptName());
        assertEquals(first.fontProfileHash(), second.fontProfileHash());
    }

    @Test
    void awtBackendNeverFallsBackWhenFingerprintBytesAreNotARealFont() {
        ResolvedFontProfile profile = resolveExact(inventory(Map.of(
                key("Microsoft YaHei", 400), List.of(resource("Microsoft YaHei", 400, "not-a-font")))));

        MeasurementException exception = assertThrows(
                MeasurementException.class,
                () -> new AwtGlyphMetricsModel().textWidthEmu(
                        profile.requireFace("body", 400),
                        1_800,
                        "健康管理"));

        assertEquals(PptQualityCode.FONT_UNAVAILABLE, exception.qualityCode());
    }

    private ResolvedFontProfile resolveExact(FontFaceInventory inventory) {
        return resolver(inventory).resolve(
                "cjk-academic-v1",
                Map.of("body", List.of("Microsoft YaHei")),
                Map.of("body", Set.of(400)));
    }

    private ResolvedFontProfile resolveFallback(FontFaceInventory inventory) {
        return resolver(inventory).resolve(
                "cjk-academic-v1",
                Map.of("body", List.of("Microsoft YaHei", "Noto Sans CJK SC")),
                Map.of("body", Set.of(400)));
    }

    private ResolvedFontProfileResolver resolver(FontFaceInventory inventory) {
        return new ResolvedFontProfileResolver(inventory);
    }

    private FontFaceInventory inventory(Map<String, List<FontFaceResource>> values) {
        return (family, weight) -> values.getOrDefault(key(family, weight), List.of());
    }

    private FontFaceResource resource(String family, int weight, String bytes) {
        return MeasurementTestSupport.resource(family, weight, bytes);
    }

    private static String key(String family, int weight) {
        return family.toLowerCase(java.util.Locale.ROOT) + ':' + weight;
    }
}
