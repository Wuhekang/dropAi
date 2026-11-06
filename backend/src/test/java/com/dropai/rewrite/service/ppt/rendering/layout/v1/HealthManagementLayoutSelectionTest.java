package com.dropai.rewrite.service.ppt.rendering.layout.v1;

import com.dropai.rewrite.service.ppt.rendering.contract.v1.LayoutIds;
import com.dropai.rewrite.service.ppt.rendering.contract.v1.enums.AssetKind;
import com.dropai.rewrite.service.ppt.rendering.contract.v1.enums.ContentType;
import com.dropai.rewrite.service.ppt.rendering.contract.v1.enums.ImageRole;
import com.dropai.rewrite.service.ppt.rendering.contract.v1.enums.PagePurpose;
import com.dropai.rewrite.service.ppt.rendering.contract.v1.enums.PageType;
import com.dropai.rewrite.service.ppt.rendering.contract.v1.enums.TableKind;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class HealthManagementLayoutSelectionTest {
    private static final String ROOT = "ppt/rendering-fixtures/health-management/v1/";
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void frozenFortyPageFixtureMapsToStructuralLayoutsDeterministically() throws IOException {
        JsonNode tree = read(ROOT + "validated-presentation-tree.json");
        JsonNode manifest = read(ROOT + "fixture-manifest.json");
        Map<String, JsonNode> assets = new HashMap<>();
        manifest.path("assets").forEach(asset -> assets.put(asset.path("assetId").asText(), asset));

        int summaryCount = 0;
        for (JsonNode page : tree.path("pages")) {
            if ("SUMMARY".equals(page.path("pageType").asText())) {
                summaryCount++;
            }
        }
        int summaryOrdinal = 0;
        DeterministicLayoutSelector selector = new DeterministicLayoutSelector(
                new LayoutCatalogLoader().loadAcademicV1());
        List<String> actual = new ArrayList<>();
        for (JsonNode page : tree.path("pages")) {
            PageType pageType = PageType.valueOf(page.path("pageType").asText());
            int ordinal = pageType == PageType.SUMMARY ? ++summaryOrdinal : 0;
            int count = pageType == PageType.SUMMARY ? summaryCount : 0;
            actual.add(selector.select(features(page, assets, ordinal, count)).layoutId());
        }

        assertEquals(expectedLayouts(), actual);
    }

    private PageLayoutFeatures features(
            JsonNode page,
            Map<String, JsonNode> assets,
            int summaryOrdinal,
            int summaryCount
    ) throws IOException {
        JsonNode assetRef = page.path("assets").isEmpty() ? null : page.path("assets").get(0);
        JsonNode asset = assetRef == null ? null : assets.get(assetRef.path("assetId").asText());
        JsonNode tableRef = page.path("tables").isEmpty() ? null : page.path("tables").get(0);
        JsonNode table = tableRef == null ? null : read(ROOT + "tables/" + tableRef.path("tableId").asText() + ".json");
        int bodyCharacters = page.path("description").asText().length();
        for (JsonNode point : page.path("keyPoints")) {
            bodyCharacters += point.asText().length();
        }
        if (table != null) {
            for (JsonNode column : table.path("columns")) {
                bodyCharacters += column.asText().length();
            }
            for (JsonNode row : table.path("rows")) {
                for (JsonNode cell : row) {
                    bodyCharacters += cell.asText().length();
                }
            }
        }
        int width = asset == null ? 0 : asset.path("widthPx").asInt();
        int height = asset == null ? 0 : asset.path("heightPx").asInt();
        int aspect = asset == null ? 0 : (int) Math.floorDiv((long) width * 1_000_000L, height);
        return new PageLayoutFeatures(
                PageType.valueOf(page.path("pageType").asText()),
                PagePurpose.valueOf(page.path("pagePurpose").asText()),
                ContentType.valueOf(page.path("contentType").asText()),
                page.path("title").asText().length(),
                page.path("keyPoints").size(),
                page.path("description").asText().length(),
                bodyCharacters,
                asset == null ? 0 : page.path("description").asText().length(),
                page.path("assets").size(),
                asset == null ? null : ImageRole.valueOf(asset.path("imageRole").asText()),
                asset == null ? null : AssetKind.valueOf(asset.path("assetKind").asText()),
                aspect,
                table == null ? 0 : table.path("rows").size(),
                table == null ? 0 : table.path("columns").size(),
                table == null ? null : TableKind.valueOf(table.path("tableKind").asText()),
                summaryOrdinal,
                summaryCount);
    }

    private List<String> expectedLayouts() {
        return List.of(
                LayoutIds.COVER_CENTERED,
                LayoutIds.AGENDA_VERTICAL_STEPS,
                LayoutIds.CONTENT_THREE_CARDS,
                LayoutIds.CONTENT_THREE_CARDS,
                LayoutIds.CONTENT_ARCHITECTURE_LAYERS,
                LayoutIds.IMAGE_DIAGRAM_WITH_NOTES,
                LayoutIds.IMAGE_DIAGRAM_WITH_NOTES,
                LayoutIds.IMAGE_DIAGRAM_WITH_NOTES,
                LayoutIds.IMAGE_DIAGRAM_WITH_NOTES,
                LayoutIds.CONTENT_ARCHITECTURE_LAYERS,
                LayoutIds.IMAGE_DIAGRAM_WITH_NOTES,
                LayoutIds.CONTENT_ARCHITECTURE_LAYERS,
                LayoutIds.IMAGE_DIAGRAM_WITH_NOTES,
                LayoutIds.TABLE_ENTITY_PURPOSE_CARDS,
                LayoutIds.IMAGE_DIAGRAM_WITH_NOTES,
                LayoutIds.CONTENT_PROCESS_STEPS,
                LayoutIds.IMAGE_PROOF_SCREENSHOT_WIDE,
                LayoutIds.IMAGE_PROOF_SCREENSHOT_WIDE,
                LayoutIds.IMAGE_PROOF_SCREENSHOT_WIDE,
                LayoutIds.IMAGE_PROOF_SCREENSHOT_WIDE,
                LayoutIds.IMAGE_PROOF_SCREENSHOT_WIDE,
                LayoutIds.IMAGE_PROOF_SCREENSHOT_WIDE,
                LayoutIds.IMAGE_PROOF_SCREENSHOT_WIDE,
                LayoutIds.IMAGE_PROOF_SCREENSHOT_WIDE,
                LayoutIds.IMAGE_PROOF_SCREENSHOT_WIDE,
                LayoutIds.CONTENT_THREE_CARDS,
                LayoutIds.IMAGE_RESULT_CHART_WITH_FINDING,
                LayoutIds.CONTENT_PROCESS_STEPS,
                LayoutIds.IMAGE_PROOF_SCREENSHOT_WIDE,
                LayoutIds.IMAGE_PROOF_SCREENSHOT_WIDE,
                LayoutIds.IMAGE_PROOF_SCREENSHOT_WIDE,
                LayoutIds.IMAGE_PROOF_SCREENSHOT_WIDE,
                LayoutIds.IMAGE_PROOF_SCREENSHOT_WIDE,
                LayoutIds.IMAGE_PROOF_SCREENSHOT_WIDE,
                LayoutIds.IMAGE_PROOF_SCREENSHOT_WIDE,
                LayoutIds.IMAGE_PROOF_SCREENSHOT_WIDE,
                LayoutIds.TABLE_TEST_RESULT_STATUS,
                LayoutIds.SUMMARY_ACHIEVEMENTS,
                LayoutIds.SUMMARY_FUTURE,
                LayoutIds.THANKS_CENTERED);
    }

    private JsonNode read(String resource) throws IOException {
        try (InputStream input = HealthManagementLayoutSelectionTest.class.getClassLoader().getResourceAsStream(resource)) {
            if (input == null) {
                throw new IOException("Missing test resource " + resource);
            }
            return mapper.readTree(input);
        }
    }
}
