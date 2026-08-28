package com.dropai.rewrite.service.ppt.rendering.fixture;

import com.fasterxml.jackson.databind.JsonNode;
import com.dropai.rewrite.service.ppt.rendering.contract.v1.enums.PageType;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HealthManagementFixtureIntegrityTest {
    private static final Set<String> REQUIRED_METADATA = Set.of(
            "title", "presenter", "major", "advisor", "studentNumber", "institution"
    );
    private static final Set<String> ALLOWED_METADATA = Set.of(
            "title", "englishTitle", "presenter", "major", "advisor", "studentNumber", "institution", "date"
    );

    @Test
    void manifestAndTreeFreezeOneFullPresentationIdentity() {
        JsonNode manifest = HealthManagementFixtureTestSupport.manifest();
        JsonNode tree = HealthManagementFixtureTestSupport.tree();

        assertEquals("health-management-fixture.v1",
                HealthManagementFixtureTestSupport.requiredText(manifest, "fixtureSchemaVersion", "manifest"));
        String fixtureId = HealthManagementFixtureTestSupport.requiredText(manifest, "fixtureId", "manifest");
        assertEquals("health-management-defense-v1", fixtureId);
        assertFalse(HealthManagementFixtureTestSupport.requiredText(manifest, "description", "manifest").isBlank());

        assertEquals("validated-presentation-tree.v1",
                HealthManagementFixtureTestSupport.requiredText(tree, "fixtureSchemaVersion", "tree"));
        assertEquals(fixtureId, HealthManagementFixtureTestSupport.requiredText(tree, "fixtureId", "tree"));
        assertEquals("FULL_PRESENTATION_TREE",
                HealthManagementFixtureTestSupport.requiredText(tree, "treeType", "tree"));

        JsonNode treeReference = HealthManagementFixtureTestSupport.requiredObject(
                manifest, "presentationTree", "manifest");
        assertEquals(HealthManagementFixtureTestSupport.TREE_FILE,
                HealthManagementFixtureTestSupport.requiredText(treeReference, "path", "manifest.presentationTree"));
        assertTrue(HealthManagementFixtureTestSupport.requiredText(
                        treeReference, "sha256", "manifest.presentationTree")
                        .matches("sha256:[0-9a-f]{64}"),
                "Presentation tree hash must be a lowercase prefixed SHA-256");
    }

    @Test
    void pagesHaveStableContinuousIdentityAndRequiredPresentationShape() {
        JsonNode manifest = HealthManagementFixtureTestSupport.manifest();
        JsonNode expectations = HealthManagementFixtureTestSupport.requiredObject(
                manifest, "expectations", "manifest");
        JsonNode pages = HealthManagementFixtureTestSupport.requiredArray(
                HealthManagementFixtureTestSupport.tree(), "pages", "tree");
        int expectedPageCount = HealthManagementFixtureTestSupport.requiredPositiveInt(
                expectations, "pageCount", "manifest.expectations");

        assertEquals(expectedPageCount, pages.size());
        Set<String> pageIds = new HashSet<>();
        var pageTypes = new ArrayList<String>();
        for (int offset = 0; offset < pages.size(); offset++) {
            JsonNode page = pages.get(offset);
            String context = "tree.pages[" + offset + "]";
            int expectedIndex = offset + 1;
            assertEquals(expectedIndex, page.path("index").asInt(-1), context + " index must be continuous");

            String expectedPageId = "health-management-page-%03d".formatted(expectedIndex);
            String sourcePageId = HealthManagementFixtureTestSupport.requiredText(page, "sourcePageId", context);
            assertEquals(expectedPageId, sourcePageId, context + " sourcePageId must be stable");
            assertTrue(pageIds.add(sourcePageId), "Duplicate sourcePageId: " + sourcePageId);

            String pageType = HealthManagementFixtureTestSupport.requiredText(page, "pageType", context);
            assertNotNull(PageType.valueOf(pageType));
            pageTypes.add(pageType);

            HealthManagementFixtureTestSupport.requiredText(page, "pagePurpose", context);
            HealthManagementFixtureTestSupport.requiredText(page, "contentType", context);
            HealthManagementFixtureTestSupport.requiredText(page, "answerQuestion", context);
            HealthManagementFixtureTestSupport.requiredText(page, "title", context);
            JsonNode description = page.get("description");
            assertTrue(description != null && description.isTextual(), context + ".description must be text");
            HealthManagementFixtureTestSupport.requiredArray(page, "keyPoints", context);
            JsonNode assets = HealthManagementFixtureTestSupport.requiredArray(page, "assets", context);
            JsonNode tables = HealthManagementFixtureTestSupport.requiredArray(page, "tables", context);
            if (PageType.IMAGE.name().equals(pageType)) {
                assertFalse(assets.isEmpty(), context + " IMAGE page must bind an asset");
            }
            if (PageType.TABLE.name().equals(pageType)) {
                assertFalse(tables.isEmpty(), context + " TABLE page must bind a structured table");
            }
        }

        assertEquals(HealthManagementFixtureTestSupport.requiredText(
                        expectations, "firstPageType", "manifest.expectations"),
                pageTypes.get(0));
        assertEquals(HealthManagementFixtureTestSupport.requiredText(
                        expectations, "lastPageType", "manifest.expectations"),
                pageTypes.get(pageTypes.size() - 1));
        assertEquals(1, pageTypes.stream().filter(PageType.COVER.name()::equals).count());
        assertEquals(1, pageTypes.stream().filter(PageType.AGENDA.name()::equals).count());
        assertEquals(1, pageTypes.stream().filter(PageType.THANKS.name()::equals).count());
        assertTrue(pageTypes.contains(PageType.SUMMARY.name()));
        assertEquals(PageType.COVER.name(), pageTypes.get(0));
        assertEquals(PageType.AGENDA.name(), pageTypes.get(1));
        assertEquals(PageType.THANKS.name(), pageTypes.get(pageTypes.size() - 1));
    }

    @Test
    void coverMetadataAndAgendaSectionsAreCompleteAndReferenceEveryBodyPageOnce() {
        JsonNode tree = HealthManagementFixtureTestSupport.tree();
        JsonNode metadata = HealthManagementFixtureTestSupport.requiredObject(tree, "metadata", "tree");
        Set<String> metadataKeys = new HashSet<>();
        metadata.fieldNames().forEachRemaining(metadataKeys::add);
        assertTrue(metadataKeys.containsAll(REQUIRED_METADATA), "Missing required cover metadata: " + metadataKeys);
        assertTrue(ALLOWED_METADATA.containsAll(metadataKeys), "Unexpected cover metadata field: " + metadataKeys);
        for (String field : metadataKeys) {
            HealthManagementFixtureTestSupport.requiredText(metadata, field, "tree.metadata");
        }

        JsonNode pages = HealthManagementFixtureTestSupport.requiredArray(tree, "pages", "tree");
        List<String> expectedAgendaPageIds = new ArrayList<>();
        for (JsonNode page : pages) {
            String pageType = page.path("pageType").asText();
            if (!Set.of(PageType.COVER.name(), PageType.AGENDA.name(), PageType.THANKS.name())
                    .contains(pageType)) {
                expectedAgendaPageIds.add(page.path("sourcePageId").asText());
            }
        }

        JsonNode sections = HealthManagementFixtureTestSupport.requiredArray(
                tree, "agendaSections", "tree");
        assertFalse(sections.isEmpty(), "Agenda sections must not be empty");
        Set<String> sectionIds = new HashSet<>();
        Set<String> uniqueAgendaPageIds = new LinkedHashSet<>();
        List<String> actualAgendaPageIds = new ArrayList<>();
        for (int index = 0; index < sections.size(); index++) {
            JsonNode section = sections.get(index);
            String context = "tree.agendaSections[" + index + "]";
            String sectionId = HealthManagementFixtureTestSupport.requiredText(section, "sectionId", context);
            assertTrue(sectionIds.add(sectionId), "Duplicate agenda sectionId: " + sectionId);
            HealthManagementFixtureTestSupport.requiredText(section, "title", context);
            JsonNode referencedIds = HealthManagementFixtureTestSupport.requiredArray(
                    section, "sourcePageIds", context);
            assertFalse(referencedIds.isEmpty(), context + " must reference at least one page");
            for (JsonNode pageId : referencedIds) {
                assertTrue(pageId.isTextual() && !pageId.textValue().isBlank(),
                        context + ".sourcePageIds must contain non-blank strings");
                assertTrue(uniqueAgendaPageIds.add(pageId.textValue()),
                        "Agenda page is assigned more than once: " + pageId.textValue());
                actualAgendaPageIds.add(pageId.textValue());
            }
        }
        assertEquals(expectedAgendaPageIds, actualAgendaPageIds,
                "Agenda sections must cover every non-cover/agenda/thanks page exactly once and preserve order");
    }

    @Test
    void expectedPageSequenceExactlyMatchesTheFrozenTree() {
        JsonNode treePages = HealthManagementFixtureTestSupport.requiredArray(
                HealthManagementFixtureTestSupport.tree(), "pages", "tree");
        JsonNode expected = HealthManagementFixtureTestSupport.readJson(
                HealthManagementFixtureTestSupport.PAGE_SEQUENCE_FILE);
        JsonNode expectedPages = HealthManagementFixtureTestSupport.requiredArray(
                expected, "pages", "expected-page-sequence");

        assertEquals(treePages.size(), expected.path("pageCount").asInt(-1));
        assertEquals(treePages.size(), expectedPages.size());
        for (int offset = 0; offset < treePages.size(); offset++) {
            JsonNode actualPage = treePages.get(offset);
            JsonNode expectedPage = expectedPages.get(offset);
            assertEquals(actualPage.path("index"), expectedPage.path("index"), "index mismatch at " + offset);
            assertEquals(actualPage.path("sourcePageId"), expectedPage.path("sourcePageId"),
                    "sourcePageId mismatch at " + offset);
            assertEquals(actualPage.path("pageType"), expectedPage.path("pageType"),
                    "pageType mismatch at " + offset);
        }
    }

    @Test
    void renderPlanStructureSnapshotFreezesOnlyStablePageAndAssetInvariants() {
        JsonNode tree = HealthManagementFixtureTestSupport.tree();
        JsonNode treePages = HealthManagementFixtureTestSupport.requiredArray(tree, "pages", "tree");
        JsonNode snapshot = HealthManagementFixtureTestSupport.readJson(
                HealthManagementFixtureTestSupport.RENDER_PLAN_STRUCTURE_FILE);

        assertEquals("render-plan-structure.v1",
                HealthManagementFixtureTestSupport.requiredText(snapshot, "snapshotVersion", "snapshot"));
        assertEquals(tree.path("fixtureId"), snapshot.path("fixtureId"));
        JsonNode slides = HealthManagementFixtureTestSupport.requiredArray(snapshot, "slides", "snapshot");
        assertEquals(treePages.size(), slides.size());

        Set<String> forbiddenPrematureFields = Set.of(
                "layoutId", "elements", "resolvedStyle", "themeHash", "layoutCatalogHash",
                "fontProfileHash", "renderPlanHash", "normalizedRenderPlanSha256",
                "x", "y", "width", "height", "zIndex", "fontFamily", "fontSize"
        );
        for (int offset = 0; offset < treePages.size(); offset++) {
            JsonNode page = treePages.get(offset);
            JsonNode slide = slides.get(offset);
            String context = "snapshot.slides[" + offset + "]";
            assertEquals(page.path("index"), slide.path("index"), context + " index mismatch");
            assertEquals(page.path("sourcePageId"), slide.path("sourcePageId"),
                    context + " sourcePageId mismatch");
            assertEquals(page.path("pageType"), slide.path("pageType"), context + " pageType mismatch");

            List<String> actualAssetIds = new ArrayList<>();
            for (JsonNode asset : HealthManagementFixtureTestSupport.requiredArray(page, "assets", "page")) {
                actualAssetIds.add(HealthManagementFixtureTestSupport.requiredText(asset, "assetId", "page.asset"));
            }
            List<String> expectedAssetIds = new ArrayList<>();
            for (JsonNode assetId : HealthManagementFixtureTestSupport.requiredArray(
                    slide, "expectedAssetIds", context)) {
                assertTrue(assetId.isTextual(), context + ".expectedAssetIds must be strings");
                expectedAssetIds.add(assetId.asText());
            }
            assertEquals(actualAssetIds, expectedAssetIds, context + " asset order mismatch");

            List<String> actualTableIds = new ArrayList<>();
            for (JsonNode table : HealthManagementFixtureTestSupport.requiredArray(page, "tables", "page")) {
                actualTableIds.add(HealthManagementFixtureTestSupport.requiredText(table, "tableId", "page.table"));
            }
            List<String> expectedTableIds = new ArrayList<>();
            for (JsonNode tableId : HealthManagementFixtureTestSupport.requiredArray(
                    slide, "expectedTableIds", context)) {
                assertTrue(tableId.isTextual(), context + ".expectedTableIds must be strings");
                expectedTableIds.add(tableId.asText());
            }
            assertEquals(actualTableIds, expectedTableIds, context + " table order mismatch");

            for (String forbidden : forbiddenPrematureFields) {
                assertFalse(slide.has(forbidden), context + " must not pre-freeze " + forbidden);
            }
        }
    }
}
