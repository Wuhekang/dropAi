package com.dropai.rewrite.service.ppt.rendering.layout.v1;

import com.dropai.rewrite.service.ppt.rendering.contract.v1.LayoutIds;
import com.dropai.rewrite.service.ppt.rendering.contract.v1.PptQualityCode;
import com.dropai.rewrite.service.ppt.rendering.contract.v1.enums.AssetKind;
import com.dropai.rewrite.service.ppt.rendering.contract.v1.enums.ContentType;
import com.dropai.rewrite.service.ppt.rendering.contract.v1.enums.ImageRole;
import com.dropai.rewrite.service.ppt.rendering.contract.v1.enums.PageType;
import com.dropai.rewrite.service.ppt.rendering.contract.v1.enums.TableKind;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class DeterministicLayoutSelector {
    private static final int EXACT_PREFERENCE_SCORE = 100_000;
    private static final int COMPATIBLE_SCORE = 10_000;
    private final LayoutCatalog catalog;
    private final Map<String, Integer> priorities;

    public DeterministicLayoutSelector(LayoutCatalog catalog) {
        this.catalog = Objects.requireNonNull(catalog, "catalog");
        Map<String, Integer> order = new HashMap<>();
        for (int index = 0; index < LayoutIds.ORDERED.size(); index++) {
            order.put(LayoutIds.ORDERED.get(index), index);
        }
        this.priorities = Map.copyOf(order);
    }

    public SelectedLayout select(PageLayoutFeatures features) {
        Objects.requireNonNull(features, "features");
        String preferred = preferredLayout(features);
        List<ScoredRecipe> candidates = new ArrayList<>();
        for (LayoutRecipe recipe : catalog.recipes()) {
            if (supports(recipe, features)) {
                candidates.add(new ScoredRecipe(recipe, score(recipe, features, preferred)));
            }
        }
        candidates.sort(Comparator
                .comparingInt(ScoredRecipe::score).reversed()
                .thenComparingInt(candidate -> priorities.get(candidate.recipe().layoutId()))
                .thenComparing(candidate -> candidate.recipe().layoutId()));
        if (candidates.isEmpty()) {
            throw new LayoutValidationException(
                    PptQualityCode.UNRENDERABLE_PAGE,
                    "No compatible layout for structural features " + features);
        }
        ScoredRecipe selected = candidates.get(0);
        return new SelectedLayout(
                selected.recipe(),
                selected.score(),
                candidates.stream().map(candidate -> candidate.recipe().layoutId()).toList());
    }

    private boolean supports(LayoutRecipe recipe, PageLayoutFeatures features) {
        LayoutRecipe.Supports supports = recipe.supports();
        if (!supports.pageTypes().contains(features.pageType())
                || !supports.pagePurposes().contains(features.pagePurpose())
                || !supports.contentTypes().contains(features.contentType())) {
            return false;
        }
        if (features.assetCount() < supports.assetCount().min()
                || features.assetCount() > supports.assetCount().max()) {
            return false;
        }
        if (features.assetCount() > 0
                && (!supports.imageRoles().contains(features.imageRole())
                || !supports.assetKinds().contains(features.assetKind()))) {
            return false;
        }
        if (features.hasTable() && !supports.tableKinds().contains(features.tableKind())) {
            return false;
        }
        if (!features.hasTable() && !supports.tableKinds().isEmpty()) {
            return false;
        }
        if (features.bodyCharacterCount() > recipe.constraints().bodyMaxChars()) {
            return false;
        }
        return features.captionCharacterCount() <= recipe.constraints().captionMaxChars();
    }

    private int score(LayoutRecipe recipe, PageLayoutFeatures features, String preferred) {
        int score = recipe.layoutId().equals(preferred) ? EXACT_PREFERENCE_SCORE : COMPATIBLE_SCORE;
        score -= priorities.get(recipe.layoutId());
        score -= Math.max(0, recipe.constraints().bodyMaxChars() - features.bodyCharacterCount()) / 100;
        return score;
    }

    private String preferredLayout(PageLayoutFeatures features) {
        return switch (features.pageType()) {
            case COVER -> LayoutIds.COVER_CENTERED;
            case AGENDA -> LayoutIds.AGENDA_VERTICAL_STEPS;
            case THANKS -> LayoutIds.THANKS_CENTERED;
            case SUMMARY -> features.summaryCount() > 1 && features.summaryOrdinal() == features.summaryCount()
                    ? LayoutIds.SUMMARY_FUTURE
                    : LayoutIds.SUMMARY_ACHIEVEMENTS;
            case TABLE -> preferredTableLayout(features.tableKind());
            case IMAGE -> preferredImageLayout(features);
            case CONTENT -> preferredContentLayout(features);
        };
    }

    private String preferredTableLayout(TableKind tableKind) {
        if (tableKind == TableKind.ENTITY_PURPOSE) {
            return LayoutIds.TABLE_ENTITY_PURPOSE_CARDS;
        }
        if (tableKind == TableKind.TEST_RESULT) {
            return LayoutIds.TABLE_TEST_RESULT_STATUS;
        }
        return LayoutIds.TABLE_GENERIC_COMPACT;
    }

    private String preferredImageLayout(PageLayoutFeatures features) {
        if (features.imageRole() == ImageRole.EFFECT) {
            return LayoutIds.IMAGE_EFFECT_FULL_VISUAL;
        }
        if (features.imageRole() == ImageRole.INFORMATION
                || features.assetKind() == AssetKind.DIAGRAM
                || features.assetKind() == AssetKind.TECHNICAL_DRAWING) {
            return LayoutIds.IMAGE_DIAGRAM_WITH_NOTES;
        }
        if (features.contentType() == ContentType.METRICS || features.assetKind() == AssetKind.CHART) {
            return LayoutIds.IMAGE_RESULT_CHART_WITH_FINDING;
        }
        return LayoutIds.IMAGE_PROOF_SCREENSHOT_WIDE;
    }

    private String preferredContentLayout(PageLayoutFeatures features) {
        if (features.assetCount() > 0) {
            return LayoutIds.CONTENT_TEXT_VISUAL_SPLIT;
        }
        return switch (features.contentType()) {
            case PROCESS -> LayoutIds.CONTENT_PROCESS_STEPS;
            case COMPARISON -> LayoutIds.CONTENT_COMPARISON_COLUMNS;
            case ARCHITECTURE -> LayoutIds.CONTENT_ARCHITECTURE_LAYERS;
            case KEY_POINTS, METRICS -> features.keyPointCount() == 3
                    ? LayoutIds.CONTENT_THREE_CARDS
                    : LayoutIds.CONTENT_SINGLE_INSIGHT;
            default -> LayoutIds.CONTENT_SINGLE_INSIGHT;
        };
    }

    private record ScoredRecipe(LayoutRecipe recipe, int score) {
    }
}
