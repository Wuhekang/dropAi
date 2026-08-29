package com.dropai.rewrite.service.ppt.rendering.theme.v1;

import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AcademicThemeResourcesTest {
    private final ThemeCanonicalizer canonicalizer = new ThemeCanonicalizer();
    private final ThemeHasher hasher = new ThemeHasher(canonicalizer);
    private final ThemeLoader loader = new ThemeLoader(hasher);
    private final ThemeValidator validator = new ThemeValidator();
    private final ThemeRegistry registry = ThemeRegistry.academicV1();

    @Test
    void baseAndPurpleConformToFrozenThemeContract() {
        registry.registrations().values().forEach(registration -> {
            LoadedTheme loaded = loader.load(registration);
            ObjectNode source = loaded.sourceDocument();
            validator.validate(source);
            assertEquals("theme.v1", source.path("schemaVersion").asText());
            assertTrue(loaded.sourceHash().matches("sha256:[a-f0-9]{64}"));
        });
    }

    @Test
    void onlyAcademicPurpleIsOfficialAndBaseRemainsInternal() {
        assertEquals(Set.of(ThemeRegistry.ACADEMIC_PURPLE), registry.officialThemeIds());
        ThemeRegistry.Registration base = registry.require(
                new ThemeCoordinate(ThemeRegistry.ACADEMIC_BASE, ThemeRegistry.VERSION_1_0_0));
        ThemeRegistry.Registration purple = registry.require(
                new ThemeCoordinate(ThemeRegistry.ACADEMIC_PURPLE, ThemeRegistry.VERSION_1_0_0));
        assertFalse(base.official());
        assertTrue(purple.official());
    }

    @Test
    void purpleInheritsOnlyTheExactAcademicBaseVersion() {
        ObjectNode purple = loader.load(registry.require(
                new ThemeCoordinate(ThemeRegistry.ACADEMIC_PURPLE, ThemeRegistry.VERSION_1_0_0)))
                .sourceDocument();
        assertEquals(1, purple.path("inherits").size());
        assertEquals("academic-base@1.0.0", purple.path("inherits").get(0).asText());
    }
}
