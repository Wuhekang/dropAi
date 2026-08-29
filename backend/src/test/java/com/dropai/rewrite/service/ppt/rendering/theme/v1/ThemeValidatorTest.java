package com.dropai.rewrite.service.ppt.rendering.theme.v1;

import com.dropai.rewrite.service.ppt.rendering.contract.v1.PptQualityCode;
import com.dropai.rewrite.service.ppt.rendering.contract.v1.enums.QualityStage;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ThemeValidatorTest {
    private final ThemeCanonicalizer canonicalizer = new ThemeCanonicalizer();
    private final ThemeHasher hasher = new ThemeHasher(canonicalizer);
    private final ThemeLoader loader = new ThemeLoader(hasher);
    private final ThemeValidator validator = new ThemeValidator();
    private final ThemeRegistry registry = ThemeRegistry.academicV1();

    @Test
    void rejectsInvalidColorNegativeSpacingAndEmptyFontLists() {
        ObjectNode invalidColor = base();
        ((ObjectNode) invalidColor.path("colors").path("accent")).put("primary", "purple");
        assertSchemaInvalid(invalidColor);

        ObjectNode negativeSpacing = base();
        ((ObjectNode) negativeSpacing.path("spacing")).put("smPt", -1);
        assertSchemaInvalid(negativeSpacing);

        ObjectNode emptyFonts = base();
        ((ArrayNode) emptyFonts.path("typography").path("fontFamilies").path("body")).removeAll();
        assertSchemaInvalid(emptyFonts);
    }

    @Test
    void rejectsMinimumFontSizeAboveDefaultAndUnknownDynamicToken() {
        ObjectNode invalidMinimum = base();
        ((ObjectNode) invalidMinimum.path("typography").path("styles").path("body"))
                .put("minSizePt", 99);
        assertSchemaInvalid(invalidMinimum);

        ObjectNode unknownToken = base();
        ((ObjectNode) unknownToken.path("components").path("bodyText"))
                .put("typographyToken", "typography.styles.notRegistered");
        ThemeValidationException exception = assertThrows(
                ThemeValidationException.class,
                () -> validator.validate(unknownToken));
        assertEquals(PptQualityCode.SCHEMA_INVALID, exception.qualityCode());
    }

    @Test
    void rejectsPurpleWithAnyParentOtherThanFrozenBase() {
        ObjectNode purple = purple();
        ((ArrayNode) purple.path("inherits")).removeAll().add("some-parent@1.0.0");
        assertSchemaInvalid(purple);
    }

    @Test
    void duplicateJsonKeysNeverUseLastWins() {
        String duplicate = "{\"themeId\":\"duplicate-theme\",\"themeVersion\":\"1.0.0\","
                + "\"colors\":{\"accent\":{\"primary\":\"#7257FF\",\"primary\":\"#000000\"}}}";
        ThemeRegistry.Registration registration = new ThemeRegistry.Registration(
                new ThemeCoordinate("duplicate-theme", "1.0.0"),
                "ppt/themes/v1/duplicate-theme.json",
                false);

        ThemeValidationException exception = assertThrows(
                ThemeValidationException.class,
                () -> loader.parse(duplicate.getBytes(StandardCharsets.UTF_8), registration));
        assertEquals(PptQualityCode.DUPLICATE_ID, exception.qualityCode());
    }

    private void assertSchemaInvalid(ObjectNode value) {
        ThemeValidationException exception = assertThrows(
                ThemeValidationException.class,
                () -> validator.validate(value));
        assertEquals(PptQualityCode.SCHEMA_INVALID, exception.qualityCode());
        assertEquals(QualityStage.THEME_RESOLUTION, exception.stage());
    }

    private ObjectNode base() {
        return loader.load(registry.require(
                new ThemeCoordinate(ThemeRegistry.ACADEMIC_BASE, ThemeRegistry.VERSION_1_0_0)))
                .sourceDocument();
    }

    private ObjectNode purple() {
        return loader.load(registry.require(
                new ThemeCoordinate(ThemeRegistry.ACADEMIC_PURPLE, ThemeRegistry.VERSION_1_0_0)))
                .sourceDocument();
    }
}
