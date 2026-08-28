package com.dropai.rewrite.service.ppt.rendering.fixture;

import com.fasterxml.jackson.databind.JsonNode;
import com.dropai.rewrite.service.ppt.rendering.contract.v1.enums.AssetKind;
import com.dropai.rewrite.service.ppt.rendering.contract.v1.enums.ContentType;
import com.dropai.rewrite.service.ppt.rendering.contract.v1.enums.ImageRole;
import com.dropai.rewrite.service.ppt.rendering.contract.v1.enums.PagePurpose;
import com.dropai.rewrite.service.ppt.rendering.contract.v1.enums.PageType;
import com.dropai.rewrite.service.ppt.rendering.contract.v1.enums.TableKind;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HealthManagementFixtureContractTest {
    private static final Set<String> REQUIRED_FORBIDDEN_TEXTS = Set.of(
            "pagePurpose",
            "answerQuestion",
            "sourceChapter",
            "mandatoryAsset",
            "semanticAlignmentScore",
            "sourceRefs",
            "contentScore",
            "answerScore",
            "duplicateScore",
            "finalScore",
            "Click to edit Master text styles",
            "Second level",
            "Third level",
            "Fourth level",
            "Fifth level",
            "未填写"
    );

    @Test
    void everyFrozenClassificationIsCoveredByTheRenderingV1Enums() {
        JsonNode tree = HealthManagementFixtureTestSupport.tree();
        for (JsonNode page : HealthManagementFixtureTestSupport.requiredArray(tree, "pages", "tree")) {
            String pageId = page.path("sourcePageId").asText("unknown-page");
            assertEnum(PageType.class,
                    HealthManagementFixtureTestSupport.requiredText(page, "pageType", pageId), pageId + ".pageType");
            assertEnum(PagePurpose.class,
                    HealthManagementFixtureTestSupport.requiredText(page, "pagePurpose", pageId),
                    pageId + ".pagePurpose");
            assertEnum(ContentType.class,
                    HealthManagementFixtureTestSupport.requiredText(page, "contentType", pageId),
                    pageId + ".contentType");
            for (JsonNode asset : HealthManagementFixtureTestSupport.requiredArray(page, "assets", pageId)) {
                assertEnum(ImageRole.class,
                        HealthManagementFixtureTestSupport.requiredText(asset, "imageRole", pageId + ".asset"),
                        pageId + ".asset.imageRole");
                assertEnum(AssetKind.class,
                        HealthManagementFixtureTestSupport.requiredText(asset, "assetKind", pageId + ".asset"),
                        pageId + ".asset.assetKind");
            }
            for (JsonNode table : HealthManagementFixtureTestSupport.requiredArray(page, "tables", pageId)) {
                assertEnum(TableKind.class,
                        HealthManagementFixtureTestSupport.requiredText(table, "tableKind", pageId + ".table"),
                        pageId + ".table.tableKind");
            }
        }

        JsonNode manifest = HealthManagementFixtureTestSupport.manifest();
        for (JsonNode asset : HealthManagementFixtureTestSupport.requiredArray(manifest, "assets", "manifest")) {
            String assetId = asset.path("assetId").asText("unknown-asset");
            assertEnum(ImageRole.class,
                    HealthManagementFixtureTestSupport.requiredText(asset, "imageRole", assetId),
                    assetId + ".imageRole");
            assertEnum(AssetKind.class,
                    HealthManagementFixtureTestSupport.requiredText(asset, "assetKind", assetId),
                    assetId + ".assetKind");
        }
        for (JsonNode table : HealthManagementFixtureTestSupport.requiredArray(manifest, "tables", "manifest")) {
            String tableId = table.path("tableId").asText("unknown-table");
            String tableKind = HealthManagementFixtureTestSupport.requiredText(table, "tableKind", tableId);
            assertEnum(TableKind.class, tableKind, tableId + ".tableKind");
            JsonNode model = HealthManagementFixtureTestSupport.readJson(
                    HealthManagementFixtureTestSupport.requiredText(table, "bundlePath", tableId));
            assertEquals(tableKind,
                    HealthManagementFixtureTestSupport.requiredText(model, "tableKind", tableId + ".model"));
            assertEnum(TableKind.class, model.path("tableKind").asText(), tableId + ".model.tableKind");
        }
    }

    @Test
    void allPageReferencesResolveByExactStableIdWithoutFuzzyMatching() {
        JsonNode manifest = HealthManagementFixtureTestSupport.manifest();
        Set<String> assetIds = collectUniqueIds(
                HealthManagementFixtureTestSupport.requiredArray(manifest, "assets", "manifest"), "assetId");
        Set<String> tableIds = collectUniqueIds(
                HealthManagementFixtureTestSupport.requiredArray(manifest, "tables", "manifest"), "tableId");

        JsonNode pages = HealthManagementFixtureTestSupport.requiredArray(
                HealthManagementFixtureTestSupport.tree(), "pages", "tree");
        for (JsonNode page : pages) {
            String pageId = page.path("sourcePageId").asText("unknown-page");
            for (JsonNode asset : HealthManagementFixtureTestSupport.requiredArray(page, "assets", pageId)) {
                String assetId = HealthManagementFixtureTestSupport.requiredText(asset, "assetId", pageId + ".asset");
                assertTrue(assetIds.contains(assetId), pageId + " references unknown assetId " + assetId);
            }
            for (JsonNode table : HealthManagementFixtureTestSupport.requiredArray(page, "tables", pageId)) {
                String tableId = HealthManagementFixtureTestSupport.requiredText(table, "tableId", pageId + ".table");
                assertTrue(tableIds.contains(tableId), pageId + " references unknown tableId " + tableId);
            }
        }
    }

    @Test
    void structuredForbiddenTextRulesFreezeAllInternalAndTemplateLeakageTerms() {
        JsonNode forbidden = HealthManagementFixtureTestSupport.readJson(
                HealthManagementFixtureTestSupport.FORBIDDEN_TEXTS_FILE);
        assertEquals("forbidden-texts.v1",
                HealthManagementFixtureTestSupport.requiredText(forbidden, "version", "forbidden-texts"));
        JsonNode rules = HealthManagementFixtureTestSupport.requiredArray(forbidden, "rules", "forbidden-texts");
        assertFalse(rules.isEmpty());

        Set<String> ids = new HashSet<>();
        Map<String, JsonNode> rulesByValue = new LinkedHashMap<>();
        for (int index = 0; index < rules.size(); index++) {
            JsonNode rule = rules.get(index);
            String context = "forbidden-texts.rules[" + index + "]";
            String id = HealthManagementFixtureTestSupport.requiredText(rule, "id", context);
            assertTrue(ids.add(id), "Duplicate forbidden rule id: " + id);
            assertEquals("LITERAL", HealthManagementFixtureTestSupport.requiredText(rule, "matchType", context));
            String value = HealthManagementFixtureTestSupport.requiredText(rule, "value", context);
            assertTrue(rulesByValue.putIfAbsent(value, rule) == null, "Duplicate forbidden literal: " + value);
            HealthManagementFixtureTestSupport.requiredBoolean(rule, "caseSensitive", context);
        }

        assertTrue(rulesByValue.keySet().containsAll(REQUIRED_FORBIDDEN_TEXTS),
                "Missing frozen forbidden literals: " + difference(REQUIRED_FORBIDDEN_TEXTS, rulesByValue.keySet()));
    }

    @Test
    void sequenceAndStructureSnapshotsUseOnlyKnownPageTypesAndStableSourceIds() {
        for (String file : Set.of(
                HealthManagementFixtureTestSupport.PAGE_SEQUENCE_FILE,
                HealthManagementFixtureTestSupport.RENDER_PLAN_STRUCTURE_FILE)) {
            JsonNode root = HealthManagementFixtureTestSupport.readJson(file);
            JsonNode entries = file.equals(HealthManagementFixtureTestSupport.PAGE_SEQUENCE_FILE)
                    ? HealthManagementFixtureTestSupport.requiredArray(root, "pages", file)
                    : HealthManagementFixtureTestSupport.requiredArray(root, "slides", file);
            for (int offset = 0; offset < entries.size(); offset++) {
                JsonNode entry = entries.get(offset);
                assertEquals(offset + 1, entry.path("index").asInt(-1));
                assertEquals("health-management-page-%03d".formatted(offset + 1),
                        HealthManagementFixtureTestSupport.requiredText(entry, "sourcePageId", file));
                assertEnum(PageType.class,
                        HealthManagementFixtureTestSupport.requiredText(entry, "pageType", file),
                        file + " pageType");
            }
        }
    }

    private static Set<String> collectUniqueIds(JsonNode values, String field) {
        Set<String> ids = new HashSet<>();
        for (int index = 0; index < values.size(); index++) {
            String id = HealthManagementFixtureTestSupport.requiredText(
                    values.get(index), field, field + "[" + index + "]");
            assertTrue(ids.add(id), "Duplicate " + field + ": " + id);
        }
        return ids;
    }

    private static <E extends Enum<E>> void assertEnum(Class<E> enumType, String value, String context) {
        assertDoesNotThrow(() -> Enum.valueOf(enumType, value),
                () -> context + " uses unknown " + enumType.getSimpleName() + " value " + value);
    }

    private static Set<String> difference(Set<String> expected, Set<String> actual) {
        Set<String> difference = new HashSet<>(expected);
        difference.removeAll(actual);
        return difference;
    }
}
