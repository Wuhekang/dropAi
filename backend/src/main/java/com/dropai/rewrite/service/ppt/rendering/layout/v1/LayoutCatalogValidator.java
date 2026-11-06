package com.dropai.rewrite.service.ppt.rendering.layout.v1;

import com.dropai.rewrite.service.ppt.rendering.contract.v1.LayoutIds;
import com.dropai.rewrite.service.ppt.rendering.contract.v1.PptQualityCode;
import com.dropai.rewrite.service.ppt.rendering.contract.v1.enums.PageType;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.networknt.schema.Schema;
import com.networknt.schema.SchemaRegistry;
import com.networknt.schema.SpecificationVersion;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class LayoutCatalogValidator {
    static final int GRID_COLUMNS = 12;
    static final BigDecimal SLIDE_HEIGHT_IN = new BigDecimal("7.5");
    private static final String SCHEMA_RESOURCE = "ppt/rendering-contract/v1/layout-recipe.v1.schema.json";

    private final Schema schema;

    public LayoutCatalogValidator() {
        ObjectMapper mapper = new ObjectMapper();
        try (InputStream input = LayoutCatalogValidator.class.getClassLoader().getResourceAsStream(SCHEMA_RESOURCE)) {
            if (input == null) {
                throw new IllegalStateException("Missing layout recipe schema: " + SCHEMA_RESOURCE);
            }
            JsonNode schemaDocument = mapper.readTree(input);
            SchemaRegistry registry = SchemaRegistry.withDefaultDialect(SpecificationVersion.DRAFT_2020_12);
            this.schema = registry.getSchema(schemaDocument);
            this.schema.initializeValidators();
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to load layout recipe schema", exception);
        }
    }

    public void validateSource(ObjectNode source) {
        List<String> violations = schema.validate(source).stream()
                .map(Object::toString)
                .sorted()
                .toList();
        if (!violations.isEmpty()) {
            throw new LayoutValidationException(PptQualityCode.SCHEMA_INVALID, violations);
        }
    }

    public void validateCatalog(List<LayoutRecipe> recipes) {
        List<String> violations = new ArrayList<>();
        validateRegistryParity(recipes, violations);
        recipes.forEach(recipe -> validateRecipe(recipe, violations));
        validateFallbackReferencesAndCycles(recipes, violations);
        validatePageTypeCoverage(recipes, violations);
        if (!violations.isEmpty()) {
            throw new LayoutValidationException(PptQualityCode.SCHEMA_INVALID, violations.stream().sorted().toList());
        }
    }

    private void validateRegistryParity(List<LayoutRecipe> recipes, List<String> violations) {
        List<String> ids = recipes.stream().map(LayoutRecipe::layoutId).toList();
        if (ids.size() != new LinkedHashSet<>(ids).size()) {
            violations.add("Layout catalog contains duplicate layoutId values");
        }
        if (!ids.equals(LayoutIds.ORDERED)) {
            violations.add("Layout catalog must exactly follow LayoutIds.ORDERED");
        }
    }

    private void validateRecipe(LayoutRecipe recipe, List<String> violations) {
        if (!"layout-recipe.v1".equals(recipe.schemaVersion())) {
            violations.add(recipe.layoutId() + ": unsupported schemaVersion " + recipe.schemaVersion());
        }
        if (recipe.supports().assetCount().min() > recipe.supports().assetCount().max()) {
            violations.add(recipe.layoutId() + ": assetCount.min must be <= assetCount.max");
        }
        if (recipe.supports().assetCount().max() == 0
                && (!recipe.supports().imageRoles().isEmpty() || !recipe.supports().assetKinds().isEmpty())) {
            violations.add(recipe.layoutId() + ": zero-asset layout must not declare image roles or asset kinds");
        }
        if (recipe.supports().assetCount().min() > 0
                && (recipe.supports().imageRoles().isEmpty() || recipe.supports().assetKinds().isEmpty())) {
            violations.add(recipe.layoutId() + ": image layout must declare image roles and asset kinds");
        }
        if (recipe.supports().pageTypes().contains(PageType.IMAGE) && !recipe.slots().containsKey("image")) {
            violations.add(recipe.layoutId() + ": IMAGE layout requires an image slot");
        }
        if (recipe.supports().pageTypes().contains(PageType.TABLE) && !recipe.slots().containsKey("table")) {
            violations.add(recipe.layoutId() + ": TABLE layout requires a table slot");
        }
        recipe.slots().forEach((slotName, slot) -> {
            if (slot.lastGridColumn() > GRID_COLUMNS) {
                violations.add(recipe.layoutId() + ": slot " + slotName + " exceeds the 12-column grid");
            }
            if (slot.bottomIn().compareTo(SLIDE_HEIGHT_IN) > 0) {
                violations.add(recipe.layoutId() + ": slot " + slotName + " exceeds the 7.5-inch slide height");
            }
        });
    }

    private void validateFallbackReferencesAndCycles(List<LayoutRecipe> recipes, List<String> violations) {
        Map<String, LayoutRecipe> byId = new HashMap<>();
        recipes.forEach(recipe -> byId.put(recipe.layoutId(), recipe));
        recipes.forEach(recipe -> recipe.fallbacks().forEach(fallback -> {
            if (!byId.containsKey(fallback)) {
                violations.add(recipe.layoutId() + ": unknown fallback " + fallback);
            }
            if (recipe.layoutId().equals(fallback)) {
                violations.add(recipe.layoutId() + ": layout cannot fall back to itself");
            }
        }));

        Set<String> visited = new HashSet<>();
        Set<String> active = new HashSet<>();
        Deque<String> path = new ArrayDeque<>();
        for (String layoutId : byId.keySet().stream().sorted().toList()) {
            detectCycle(layoutId, byId, visited, active, path, violations);
        }
    }

    private void detectCycle(
            String layoutId,
            Map<String, LayoutRecipe> byId,
            Set<String> visited,
            Set<String> active,
            Deque<String> path,
            List<String> violations
    ) {
        if (visited.contains(layoutId) || !byId.containsKey(layoutId)) {
            return;
        }
        if (!active.add(layoutId)) {
            violations.add("Fallback cycle detected at " + String.join(" -> ", path) + " -> " + layoutId);
            return;
        }
        path.addLast(layoutId);
        for (String fallback : byId.get(layoutId).fallbacks()) {
            detectCycle(fallback, byId, visited, active, path, violations);
        }
        path.removeLast();
        active.remove(layoutId);
        visited.add(layoutId);
    }

    private void validatePageTypeCoverage(List<LayoutRecipe> recipes, List<String> violations) {
        Set<PageType> covered = new HashSet<>();
        recipes.forEach(recipe -> covered.addAll(recipe.supports().pageTypes()));
        for (PageType pageType : PageType.values()) {
            if (!covered.contains(pageType)) {
                violations.add("No layout supports pageType " + pageType);
            }
        }
    }
}
