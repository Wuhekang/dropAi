package com.dropai.rewrite.service.ppt.rendering.contract.v1;

import com.fasterxml.jackson.databind.JsonNode;
import com.dropai.rewrite.service.ppt.rendering.contract.v1.enums.AssetKind;
import com.dropai.rewrite.service.ppt.rendering.contract.v1.enums.ContentType;
import com.dropai.rewrite.service.ppt.rendering.contract.v1.enums.ImageFitMode;
import com.dropai.rewrite.service.ppt.rendering.contract.v1.enums.ImageRole;
import com.dropai.rewrite.service.ppt.rendering.contract.v1.enums.PagePurpose;
import com.dropai.rewrite.service.ppt.rendering.contract.v1.enums.PageType;
import com.dropai.rewrite.service.ppt.rendering.contract.v1.enums.QualitySeverity;
import com.dropai.rewrite.service.ppt.rendering.contract.v1.enums.QualityStage;
import com.dropai.rewrite.service.ppt.rendering.contract.v1.enums.QualityStatus;
import com.dropai.rewrite.service.ppt.rendering.contract.v1.enums.RenderElementType;
import com.dropai.rewrite.service.ppt.rendering.contract.v1.enums.TableKind;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class RenderingEnumSchemaParityTest {
    private static final JsonNode LAYOUT_SCHEMA = RenderingContractTestSupport.schemaNode("layout-recipe.v1.schema.json");
    private static final JsonNode RENDER_SCHEMA = RenderingContractTestSupport.schemaNode("render-plan.v1.schema.json");
    private static final JsonNode QUALITY_SCHEMA = RenderingContractTestSupport.schemaNode("quality-report.v1.schema.json");

    @Test
    void languageEnumsExactlyMatchSchemaEnums() {
        assertEnum(PageType.class, LAYOUT_SCHEMA, "pageType");
        assertEnum(PageType.class, RENDER_SCHEMA, "pageType");
        assertEnum(PagePurpose.class, LAYOUT_SCHEMA, "pagePurpose");
        assertEnum(ContentType.class, LAYOUT_SCHEMA, "contentType");
        assertEnum(ImageRole.class, LAYOUT_SCHEMA, "imageRole");
        assertEnum(ImageRole.class, RENDER_SCHEMA, "imageRole");
        assertEnum(AssetKind.class, LAYOUT_SCHEMA, "assetKind");
        assertEnum(AssetKind.class, RENDER_SCHEMA, "assetKind");
        assertEnum(TableKind.class, LAYOUT_SCHEMA, "tableKind");
        assertEnum(TableKind.class, RENDER_SCHEMA, "tableKind");
        assertEnum(RenderElementType.class, RENDER_SCHEMA, "renderElementType");
        assertEnum(ImageFitMode.class, RENDER_SCHEMA, "imageFitMode");
        assertEquals(
                Arrays.stream(ImageFitMode.values()).map(Enum::name).collect(Collectors.toCollection(LinkedHashSet::new)),
                new LinkedHashSet<>(inlineEnumValues(
                        LAYOUT_SCHEMA.path("$defs").path("constraints").path("properties").path("imageFit")))
        );
        assertEnum(QualityStatus.class, QUALITY_SCHEMA, "qualityStatus");
        assertEnum(QualitySeverity.class, QUALITY_SCHEMA, "qualitySeverity");
        assertEnum(QualityStage.class, QUALITY_SCHEMA, "qualityStage");
    }

    @Test
    void layoutRegistryExactlyMatchesBothSchemasAndKeepsOrder() {
        List<String> layoutRecipeIds = enumValuesInOrder(LAYOUT_SCHEMA, "layoutId");
        List<String> renderPlanIds = enumValuesInOrder(RENDER_SCHEMA, "layoutId");
        assertEquals(21, LayoutIds.ORDERED.size());
        assertEquals(LayoutIds.ORDERED, layoutRecipeIds);
        assertEquals(LayoutIds.ORDERED, renderPlanIds);
        assertEquals(Set.copyOf(LayoutIds.ORDERED), LayoutIds.ALL);
    }

    @Test
    void qualityCodeRegistryExactlyMatchesSchema() {
        assertEquals(38, PptQualityCode.values().length);
        assertEquals(PptQualityCode.codes(), new LinkedHashSet<>(enumValuesInOrder(QUALITY_SCHEMA, "qualityCode")));
        assertEquals(QualitySeverity.WARNING, PptQualityCode.IMAGE_RESOLUTION_LOW.defaultSeverity());
        assertFalse(PptQualityCode.IMAGE_RESOLUTION_LOW.deprecated());
        Arrays.stream(PptQualityCode.values()).forEach(code -> {
            assertFalse(code.code().isBlank());
            assertFalse(code.deprecated());
            assertNotNull(code.defaultStage());
        });
    }

    @Test
    void schemaVersionConstantsExactlyMatchSchemaConsts() {
        assertEquals(RenderingContractVersion.THEME,
                RenderingContractTestSupport.schemaNode("theme.v1.schema.json").path("properties").path("schemaVersion").path("const").asText());
        assertEquals(RenderingContractVersion.LAYOUT_RECIPE,
                LAYOUT_SCHEMA.path("properties").path("schemaVersion").path("const").asText());
        assertEquals(RenderingContractVersion.RENDER_PLAN,
                RENDER_SCHEMA.path("properties").path("schemaVersion").path("const").asText());
        assertEquals(RenderingContractVersion.QUALITY_REPORT,
                QUALITY_SCHEMA.path("properties").path("schemaVersion").path("const").asText());
    }

    private static <E extends Enum<E>> void assertEnum(Class<E> enumClass, JsonNode schema, String definition) {
        Set<String> javaValues = Arrays.stream(enumClass.getEnumConstants())
                .map(Enum::name)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        assertEquals(javaValues, new LinkedHashSet<>(enumValuesInOrder(schema, definition)),
                () -> enumClass.getSimpleName() + " differs from schema definition " + definition);
    }

    private static List<String> enumValuesInOrder(JsonNode schema, String definition) {
        return inlineEnumValues(schema.path("$defs").path(definition));
    }

    private static List<String> inlineEnumValues(JsonNode enumOwner) {
        JsonNode values = enumOwner.path("enum");
        return java.util.stream.StreamSupport.stream(values.spliterator(), false)
                .map(JsonNode::asText)
                .toList();
    }
}
