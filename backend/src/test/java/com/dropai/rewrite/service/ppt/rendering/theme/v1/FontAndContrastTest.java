package com.dropai.rewrite.service.ppt.rendering.theme.v1;

import com.dropai.rewrite.service.ppt.rendering.contract.v1.PptQualityCode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FontAndContrastTest {
    private final ThemeCanonicalizer canonicalizer = new ThemeCanonicalizer();
    private final ThemeHasher hasher = new ThemeHasher(canonicalizer);
    private final ThemeLoader loader = new ThemeLoader(hasher);
    private final ThemeRegistry registry = ThemeRegistry.academicV1();

    @Test
    void exactDeclaredFontIsRecordedWithoutFallback() {
        FontAvailabilityChecker checker = new FontAvailabilityChecker(List.of("Microsoft YaHei"), hasher);
        FontProfile profile = checker.resolve(FontProfile.CJK_ACADEMIC_V1, purple().path("typography"));

        assertEquals("Microsoft YaHei", profile.resolvedFamilies().get("display"));
        assertFalse(profile.usedFallback("display"));
        assertTrue(profile.profileHash().matches("sha256:[a-f0-9]{64}"));
        assertTrue(profile.fontConfigurationHashes().values().stream()
                .allMatch(hash -> hash.matches("sha256:[a-f0-9]{64}")));
    }

    @Test
    void onlyExplicitFallbackCanBeSelectedAndItIsNeverSilent() {
        FontAvailabilityChecker checker = new FontAvailabilityChecker(List.of("Noto Sans CJK SC"), hasher);
        FontProfile profile = checker.resolve(FontProfile.CJK_ACADEMIC_V1, purple().path("typography"));

        assertEquals(List.of("Microsoft YaHei"), profile.declaredFamilies().get("body"));
        assertEquals(List.of("Noto Sans CJK SC"), profile.allowedFallbackFamilies().get("body"));
        assertEquals("Noto Sans CJK SC", profile.resolvedFamilies().get("body"));
        assertTrue(profile.usedFallback("body"));
    }

    @Test
    void undeclaredSystemDefaultsAreRejectedAndExactPolicyDoesNotFallback() {
        FontAvailabilityChecker defaultsOnly = new FontAvailabilityChecker(List.of("Arial", "Calibri"), hasher);
        ThemeValidationException unavailable = assertThrows(
                ThemeValidationException.class,
                () -> defaultsOnly.resolve(FontProfile.CJK_ACADEMIC_V1, purple().path("typography")));
        assertEquals(PptQualityCode.FONT_UNAVAILABLE, unavailable.qualityCode());

        ObjectNode exactTypography = (ObjectNode) purple().path("typography");
        exactTypography.put("fontPolicy", "REQUIRE_EXACT");
        FontAvailabilityChecker fallbackOnly = new FontAvailabilityChecker(List.of("Noto Sans CJK SC"), hasher);
        ThemeValidationException exactFailure = assertThrows(
                ThemeValidationException.class,
                () -> fallbackOnly.resolve(FontProfile.CJK_ACADEMIC_V1, exactTypography));
        assertEquals(PptQualityCode.FONT_UNAVAILABLE, exactFailure.qualityCode());
    }

    @Test
    void academicPurplePassesEveryRequiredAndComponentContrastPair() {
        ColorContrastChecker checker = new ColorContrastChecker();
        List<ColorContrastChecker.ContrastResult> results = checker.evaluate(purple());
        assertFalse(results.isEmpty());
        assertTrue(results.stream().allMatch(ColorContrastChecker.ContrastResult::passed));
        checker.requireReadable(results);

        ColorContrastChecker.ContrastResult purpleButton = checker.evaluate(
                "colors.text.inverse",
                "colors.accent.primary",
                "#FFFFFF",
                "#7257FF",
                4.5d);
        assertTrue(purpleButton.passed());
        assertEquals(4.6432d, purpleButton.actualRatio(), 0.0001d);
    }

    @Test
    void insufficientContrastProducesAnExplicitBlockingResult() {
        ColorContrastChecker checker = new ColorContrastChecker();
        ColorContrastChecker.ContrastResult result = checker.evaluate(
                "colors.text.inverse",
                "colors.accent.secondary",
                "#FFFFFF",
                "#E85BB5",
                4.5d);
        assertFalse(result.passed());
        ThemeValidationException exception = assertThrows(
                ThemeValidationException.class,
                () -> checker.requireReadable(List.of(result)));
        assertTrue(exception.getMessage().contains("COLOR_CONTRAST_INSUFFICIENT"));
    }

    private ObjectNode purple() {
        return loader.load(registry.require(
                new ThemeCoordinate(ThemeRegistry.ACADEMIC_PURPLE, ThemeRegistry.VERSION_1_0_0)))
                .sourceDocument();
    }
}
