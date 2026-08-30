package com.dropai.rewrite.service.ppt.rendering.layout.v1;

import com.dropai.rewrite.service.ppt.rendering.contract.v1.PptQualityCode;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class LayoutCatalog {
    private final String catalogId;
    private final String catalogVersion;
    private final List<LayoutRecipe> recipes;
    private final Map<String, LayoutRecipe> recipesById;
    private final String canonicalDocument;
    private final String catalogHash;

    LayoutCatalog(
            String catalogId,
            String catalogVersion,
            List<LayoutRecipe> recipes,
            String canonicalDocument,
            String catalogHash
    ) {
        this.catalogId = Objects.requireNonNull(catalogId, "catalogId");
        this.catalogVersion = Objects.requireNonNull(catalogVersion, "catalogVersion");
        this.recipes = List.copyOf(recipes);
        LinkedHashMap<String, LayoutRecipe> byId = new LinkedHashMap<>();
        this.recipes.forEach(recipe -> byId.put(recipe.layoutId(), recipe));
        this.recipesById = Collections.unmodifiableMap(byId);
        this.canonicalDocument = Objects.requireNonNull(canonicalDocument, "canonicalDocument");
        this.catalogHash = Objects.requireNonNull(catalogHash, "catalogHash");
    }

    public String catalogId() {
        return catalogId;
    }

    public String catalogVersion() {
        return catalogVersion;
    }

    public List<LayoutRecipe> recipes() {
        return recipes;
    }

    public Map<String, LayoutRecipe> recipesById() {
        return recipesById;
    }

    public LayoutRecipe require(String layoutId) {
        LayoutRecipe recipe = recipesById.get(layoutId);
        if (recipe == null) {
            throw new LayoutValidationException(PptQualityCode.UNKNOWN_LAYOUT, "Unknown layout: " + layoutId);
        }
        return recipe;
    }

    public String canonicalDocument() {
        return canonicalDocument;
    }

    public String catalogHash() {
        return catalogHash;
    }
}
