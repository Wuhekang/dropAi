package com.dropai.rewrite.service.ppt.rendering.layout.v1;

import java.util.List;
import java.util.Objects;

public record SelectedLayout(LayoutRecipe recipe, int finalScore, List<String> compatibleLayoutIds) {
    public SelectedLayout {
        recipe = Objects.requireNonNull(recipe, "recipe");
        compatibleLayoutIds = List.copyOf(Objects.requireNonNull(compatibleLayoutIds, "compatibleLayoutIds"));
        if (compatibleLayoutIds.isEmpty() || !compatibleLayoutIds.contains(recipe.layoutId())) {
            throw new IllegalArgumentException("Compatible layout IDs must contain selected recipe");
        }
    }

    public String layoutId() {
        return recipe.layoutId();
    }
}
