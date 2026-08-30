package com.dropai.rewrite.service.ppt.rendering.renderability.v1;

import com.dropai.rewrite.service.ppt.rendering.contract.v1.PptQualityCode;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PageRenderabilityValidatorTest {
    private static final String ROOT = "ppt/rendering-fixtures/health-management/v1/";
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private final PageRenderabilityValidator validator = new PageRenderabilityValidator();

    @Test
    void frozenFortyPageFixtureIsRenderableWithoutRewritingIt() {
        ObjectNode tree = object("validated-presentation-tree.json");
        String before = tree.toString();
        PageRenderabilityResult result = validator.validate(
                tree,
                object("fixture-manifest.json"),
                Map.of(
                        "database-purpose", read("tables/database-purpose.json"),
                        "test-results", read("tables/test-results.json")));

        assertTrue(result.renderable(), () -> result.issues().toString());
        assertTrue(result.issues().isEmpty());
        assertTrue(tree.toString().equals(before), "Validator must not mutate the page tree");
    }

    @Test
    void reportsMissingMandatoryImageAndOversizeTableInsteadOfRepairing() {
        ObjectNode tree = object("validated-presentation-tree.json");
        ObjectNode imagePage = (ObjectNode) tree.path("pages").get(5);
        ((ArrayNode) imagePage.path("assets")).removeAll();
        ObjectNode oversizedTable = object("tables/database-purpose.json");
        ArrayNode rows = (ArrayNode) oversizedTable.path("rows");
        while (rows.size() <= 7) {
            rows.addArray().add("x").add("y");
        }

        PageRenderabilityResult result = validator.validate(
                tree,
                object("fixture-manifest.json"),
                Map.of("database-purpose", oversizedTable,
                        "test-results", read("tables/test-results.json")));

        assertFalse(result.renderable());
        assertTrue(result.issues().stream().anyMatch(issue ->
                issue.qualityCode() == PptQualityCode.MANDATORY_ASSET_MISSING
                        && issue.pageIndex() == 6));
        assertTrue(result.issues().stream().anyMatch(issue ->
                issue.qualityCode() == PptQualityCode.TABLE_CAPACITY_EXCEEDED
                        && issue.pageIndex() == 14));
        assertTrue(imagePage.path("assets").isEmpty(), "Validator must not rebind a missing asset");
        assertTrue(rows.size() > 7, "Validator must not delete table rows");
    }

    @Test
    void rejectsInputsThatTheCompilerCannotExecuteWithoutInventingContent() {
        ObjectNode tree = object("validated-presentation-tree.json");
        ((ObjectNode) tree.path("metadata")).remove("date");
        ObjectNode summary = findPage(tree, "SUMMARY");
        summary.remove("description");
        ObjectNode thanks = findPage(tree, "THANKS");
        thanks.remove("description");

        PageRenderabilityResult result = validator.validate(
                tree,
                object("fixture-manifest.json"),
                Map.of(
                        "database-purpose", read("tables/database-purpose.json"),
                        "test-results", read("tables/test-results.json")));

        assertFalse(result.renderable());
        assertTrue(result.issues().stream().anyMatch(issue ->
                issue.message().contains("date")));
        assertTrue(result.issues().stream().anyMatch(issue ->
                issue.message().contains("both key points")));
        assertTrue(result.issues().stream().anyMatch(issue ->
                issue.message().contains("closing description")));
    }

    private ObjectNode findPage(ObjectNode tree, String pageType) {
        for (JsonNode page : tree.path("pages")) {
            if (pageType.equals(page.path("pageType").asText())) {
                return (ObjectNode) page;
            }
        }
        throw new IllegalArgumentException("Missing page type: " + pageType);
    }

    private ObjectNode object(String path) {
        return (ObjectNode) read(path);
    }

    private JsonNode read(String path) {
        try (InputStream input = getClass().getClassLoader().getResourceAsStream(ROOT + path)) {
            if (input == null) {
                throw new IllegalArgumentException("Missing test resource: " + path);
            }
            return MAPPER.readTree(input);
        } catch (IOException exception) {
            throw new IllegalStateException("Cannot read test fixture: " + path, exception);
        }
    }
}
