package com.dropai.rewrite.service.ppt.rendering.renderability.v1;

import com.dropai.rewrite.service.ppt.rendering.contract.v1.PptQualityCode;
import com.dropai.rewrite.service.ppt.rendering.contract.v1.enums.AssetKind;
import com.dropai.rewrite.service.ppt.rendering.contract.v1.enums.ContentType;
import com.dropai.rewrite.service.ppt.rendering.contract.v1.enums.ImageRole;
import com.dropai.rewrite.service.ppt.rendering.contract.v1.enums.PagePurpose;
import com.dropai.rewrite.service.ppt.rendering.contract.v1.enums.PageType;
import com.dropai.rewrite.service.ppt.rendering.contract.v1.enums.TableKind;
import com.fasterxml.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Read-only gate between the validated presentation tree and visual planning.
 * It reports missing inputs and never rewrites, deletes, splits or rebinds a page.
 */
public final class PageRenderabilityValidator {
    private static final List<String> COVER_METADATA = List.of(
            "title", "presenter", "major", "advisor", "studentNumber", "institution", "date");

    public PageRenderabilityResult validate(
            JsonNode presentationTree,
            JsonNode fixtureManifest,
            Map<String, ? extends JsonNode> tableModels
    ) {
        Objects.requireNonNull(presentationTree, "presentationTree");
        Objects.requireNonNull(fixtureManifest, "fixtureManifest");
        Objects.requireNonNull(tableModels, "tableModels");

        List<PageRenderabilityIssue> issues = new ArrayList<>();
        Map<String, JsonNode> assets = indexBy(fixtureManifest.path("assets"), "assetId");
        Map<String, JsonNode> tables = indexBy(fixtureManifest.path("tables"), "tableId");
        JsonNode pages = presentationTree.path("pages");
        if (!pages.isArray() || pages.isEmpty()) {
            issues.add(issue(PptQualityCode.UNRENDERABLE_PAGE, 0, null,
                    "Presentation tree must contain at least one page"));
            return PageRenderabilityResult.of(issues);
        }

        int expectedIndex = 1;
        for (JsonNode page : pages) {
            int pageIndex = page.path("index").asInt(expectedIndex);
            String pageId = textOrNull(page, "sourcePageId");
            if (pageIndex != expectedIndex) {
                issues.add(issue(PptQualityCode.SLIDE_ORDER_MISMATCH, pageIndex, pageId,
                        "Presentation page indices must be continuous",
                        Map.of("expectedIndex", expectedIndex, "actualIndex", pageIndex)));
            }
            validatePage(page, presentationTree, assets, tables, tableModels, pageIndex, pageId, issues);
            expectedIndex++;
        }
        return PageRenderabilityResult.of(issues);
    }

    private void validatePage(
            JsonNode page,
            JsonNode tree,
            Map<String, JsonNode> assets,
            Map<String, JsonNode> tableRegistry,
            Map<String, ? extends JsonNode> tableModels,
            int pageIndex,
            String pageId,
            List<PageRenderabilityIssue> issues
    ) {
        PageType pageType = parseEnum(PageType.class, page.path("pageType"), "pageType",
                pageIndex, pageId, issues);
        parseEnum(PagePurpose.class, page.path("pagePurpose"), "pagePurpose",
                pageIndex, pageId, issues);
        ContentType contentType = parseEnum(ContentType.class, page.path("contentType"), "contentType",
                pageIndex, pageId, issues);
        if (pageId == null) {
            issues.add(issue(PptQualityCode.UNRENDERABLE_PAGE, pageIndex, null,
                    "Page is missing sourcePageId"));
        }
        if (!hasText(page, "title")) {
            issues.add(issue(PptQualityCode.UNRENDERABLE_PAGE, pageIndex, pageId,
                    "Page is missing a display title"));
        }
        if (pageType == null) {
            return;
        }

        switch (pageType) {
            case COVER -> validateCover(tree, page, pageIndex, pageId, issues);
            case AGENDA -> validateAgenda(tree, pageIndex, pageId, issues);
            case CONTENT -> {
                validateContent(page, contentType, pageIndex, pageId, issues);
                validateOptionalImageBindings(page, assets, pageIndex, pageId, issues);
            }
            case IMAGE -> validateImagePage(page, assets, pageIndex, pageId, issues);
            case TABLE -> validateTablePage(page, tableRegistry, tableModels, pageIndex, pageId, issues);
            case SUMMARY -> {
                if (!hasNonBlankText(page.path("keyPoints")) || !hasText(page, "description")) {
                    issues.add(issue(PptQualityCode.UNRENDERABLE_PAGE, pageIndex, pageId,
                            "Summary page must contain both key points and a complete description"));
                }
            }
            case THANKS -> {
                if (!hasText(page, "description")) {
                    issues.add(issue(PptQualityCode.UNRENDERABLE_PAGE, pageIndex, pageId,
                            "Thanks page must contain stable closing description text"));
                }
            }
        }
    }

    private void validateCover(
            JsonNode tree,
            JsonNode page,
            int pageIndex,
            String pageId,
            List<PageRenderabilityIssue> issues
    ) {
        JsonNode metadata = tree.path("metadata");
        for (String field : COVER_METADATA) {
            if (!hasText(metadata, field)) {
                issues.add(issue(PptQualityCode.UNRENDERABLE_PAGE, pageIndex, pageId,
                        "Cover metadata is missing " + field, Map.of("field", field)));
            }
        }
        if (!hasText(metadata, "englishTitle") && !hasText(page, "description")) {
            issues.add(issue(PptQualityCode.UNRENDERABLE_PAGE, pageIndex, pageId,
                    "Cover must contain an English title or a stable subtitle description"));
        }
    }

    private void validateContent(
            JsonNode page,
            ContentType contentType,
            int pageIndex,
            String pageId,
            List<PageRenderabilityIssue> issues
    ) {
        requireNarrativeContent(page, pageIndex, pageId, issues);
        if ((contentType == ContentType.ARCHITECTURE || contentType == ContentType.PROCESS)
                && !hasNonBlankText(page.path("keyPoints"))) {
            issues.add(issue(PptQualityCode.UNRENDERABLE_PAGE, pageIndex, pageId,
                    contentType + " content requires structured key points"));
        }
        if (contentType == ContentType.COMPARISON
                && (!hasText(page, "description") || !hasNonBlankText(page.path("keyPoints")))) {
            issues.add(issue(PptQualityCode.UNRENDERABLE_PAGE, pageIndex, pageId,
                    "COMPARISON content requires both a description and structured key points"));
        }
    }

    private void validateAgenda(
            JsonNode tree,
            int pageIndex,
            String pageId,
            List<PageRenderabilityIssue> issues
    ) {
        JsonNode sections = tree.path("agendaSections");
        if (!sections.isArray() || sections.size() < 3 || sections.size() > 5) {
            issues.add(issue(PptQualityCode.UNRENDERABLE_PAGE, pageIndex, pageId,
                    "Agenda must contain between three and five sections",
                    Map.of("sectionCount", sections.isArray() ? sections.size() : 0)));
            return;
        }
        for (JsonNode section : sections) {
            if (!hasText(section, "sectionId") || !hasText(section, "title")) {
                issues.add(issue(PptQualityCode.UNRENDERABLE_PAGE, pageIndex, pageId,
                        "Every agenda section must have a stable id and title"));
            }
        }
    }

    private void requireNarrativeContent(
            JsonNode page,
            int pageIndex,
            String pageId,
            List<PageRenderabilityIssue> issues
    ) {
        if (!hasText(page, "description") && !hasNonBlankText(page.path("keyPoints"))) {
            issues.add(issue(PptQualityCode.UNRENDERABLE_PAGE, pageIndex, pageId,
                    "Page must contain key points or a description"));
        }
    }

    private void validateImagePage(
            JsonNode page,
            Map<String, JsonNode> assets,
            int pageIndex,
            String pageId,
            List<PageRenderabilityIssue> issues
    ) {
        JsonNode bindings = page.path("assets");
        if (!bindings.isArray() || bindings.isEmpty()) {
            issues.add(issue(PptQualityCode.MANDATORY_ASSET_MISSING, pageIndex, pageId,
                    "Image page has no bound asset"));
            return;
        }
        if (bindings.size() != 1) {
            issues.add(issue(PptQualityCode.UNRENDERABLE_PAGE, pageIndex, pageId,
                    "Rendering V1 requires exactly one bound asset on an image page",
                    Map.of("assetCount", bindings.size())));
        }
        validateImageBindings(bindings, assets, pageIndex, pageId, issues);
    }

    private void validateOptionalImageBindings(
            JsonNode page,
            Map<String, JsonNode> assets,
            int pageIndex,
            String pageId,
            List<PageRenderabilityIssue> issues
    ) {
        JsonNode bindings = page.path("assets");
        if (!bindings.isArray()) {
            issues.add(issue(PptQualityCode.UNRENDERABLE_PAGE, pageIndex, pageId,
                    "Content page assets must be an array"));
            return;
        }
        if (bindings.isEmpty()) {
            return;
        }
        if (bindings.size() != 1) {
            issues.add(issue(PptQualityCode.UNRENDERABLE_PAGE, pageIndex, pageId,
                    "Rendering V1 supports at most one bound asset on a content page",
                    Map.of("assetCount", bindings.size())));
        }
        validateImageBindings(bindings, assets, pageIndex, pageId, issues);
    }

    private void validateImageBindings(
            JsonNode bindings,
            Map<String, JsonNode> assets,
            int pageIndex,
            String pageId,
            List<PageRenderabilityIssue> issues
    ) {
        for (JsonNode binding : bindings) {
            String assetId = textOrNull(binding, "assetId");
            if (assetId == null || !assets.containsKey(assetId)) {
                issues.add(issue(PptQualityCode.MANDATORY_ASSET_MISSING, pageIndex, pageId,
                        "Bound image asset is not present in the manifest",
                        assetId == null ? Map.of() : Map.of("assetId", assetId)));
                continue;
            }
            parseEnum(ImageRole.class, binding.path("imageRole"), "imageRole",
                    pageIndex, pageId, issues);
            parseEnum(AssetKind.class, binding.path("assetKind"), "assetKind",
                    pageIndex, pageId, issues);
            JsonNode manifestAsset = assets.get(assetId);
            if (binding.path("mandatory").asBoolean(false)
                    && !manifestAsset.path("mandatory").asBoolean(false)) {
                issues.add(issue(PptQualityCode.INVALID_REFERENCE, pageIndex, pageId,
                        "Mandatory page binding does not match manifest asset policy",
                        Map.of("assetId", assetId)));
            }
        }
    }

    private void validateTablePage(
            JsonNode page,
            Map<String, JsonNode> tableRegistry,
            Map<String, ? extends JsonNode> tableModels,
            int pageIndex,
            String pageId,
            List<PageRenderabilityIssue> issues
    ) {
        JsonNode bindings = page.path("tables");
        if (!bindings.isArray() || bindings.isEmpty()) {
            issues.add(issue(PptQualityCode.UNRENDERABLE_PAGE, pageIndex, pageId,
                    "Table page has no structured table binding"));
            return;
        }
        if (bindings.size() != 1) {
            issues.add(issue(PptQualityCode.UNRENDERABLE_PAGE, pageIndex, pageId,
                    "Rendering V1 requires exactly one structured table binding",
                    Map.of("tableCount", bindings.size())));
        }
        for (JsonNode binding : bindings) {
            String tableId = textOrNull(binding, "tableId");
            if (tableId == null || !tableRegistry.containsKey(tableId) || !tableModels.containsKey(tableId)) {
                issues.add(issue(PptQualityCode.INVALID_REFERENCE, pageIndex, pageId,
                        "Structured table binding cannot be resolved",
                        tableId == null ? Map.of() : Map.of("tableId", tableId)));
                continue;
            }
            parseEnum(TableKind.class, binding.path("tableKind"), "tableKind",
                    pageIndex, pageId, issues);
            JsonNode model = tableModels.get(tableId);
            JsonNode columns = model.path("columns");
            JsonNode rows = model.path("rows");
            if (!columns.isArray() || columns.isEmpty() || !rows.isArray() || rows.isEmpty()) {
                issues.add(issue(PptQualityCode.UNRENDERABLE_PAGE, pageIndex, pageId,
                        "Structured table must contain columns and rows", Map.of("tableId", tableId)));
            } else if (columns.size() > 5 || rows.size() > 7) {
                issues.add(issue(PptQualityCode.TABLE_CAPACITY_EXCEEDED, pageIndex, pageId,
                        "Structured table exceeds the V1 table capacity",
                        Map.of("columnCount", columns.size(), "rowCount", rows.size(), "tableId", tableId)));
            }
        }
    }

    private <E extends Enum<E>> E parseEnum(
            Class<E> type,
            JsonNode value,
            String field,
            int pageIndex,
            String pageId,
            List<PageRenderabilityIssue> issues
    ) {
        if (!value.isTextual()) {
            issues.add(issue(PptQualityCode.UNKNOWN_ENUM_VALUE, pageIndex, pageId,
                    "Missing or invalid " + field));
            return null;
        }
        try {
            return Enum.valueOf(type, value.textValue());
        } catch (IllegalArgumentException exception) {
            issues.add(issue(PptQualityCode.UNKNOWN_ENUM_VALUE, pageIndex, pageId,
                    "Unknown " + field + ": " + value.textValue(), Map.of("field", field)));
            return null;
        }
    }

    private Map<String, JsonNode> indexBy(JsonNode values, String key) {
        Map<String, JsonNode> result = new HashMap<>();
        if (values.isArray()) {
            values.forEach(value -> {
                String id = textOrNull(value, key);
                if (id != null) {
                    result.put(id, value);
                }
            });
        }
        return result;
    }

    private boolean hasNonBlankText(JsonNode array) {
        if (!array.isArray()) {
            return false;
        }
        for (JsonNode value : array) {
            if (value.isTextual() && !value.textValue().isBlank()) {
                return true;
            }
        }
        return false;
    }

    private boolean hasText(JsonNode owner, String field) {
        return textOrNull(owner, field) != null;
    }

    private String textOrNull(JsonNode owner, String field) {
        JsonNode value = owner == null ? null : owner.get(field);
        return value != null && value.isTextual() && !value.textValue().isBlank()
                ? value.textValue()
                : null;
    }

    private PageRenderabilityIssue issue(
            PptQualityCode code,
            int pageIndex,
            String pageId,
            String message
    ) {
        return issue(code, pageIndex, pageId, message, Map.of());
    }

    private PageRenderabilityIssue issue(
            PptQualityCode code,
            int pageIndex,
            String pageId,
            String message,
            Map<String, Object> metrics
    ) {
        return new PageRenderabilityIssue(code, Math.max(pageIndex, 0), pageId, message, metrics);
    }
}
