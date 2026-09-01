package com.dropai.rewrite.service.ppt.rendering.layout.v1;

import com.dropai.rewrite.service.ppt.rendering.contract.v1.LayoutIds;
import com.dropai.rewrite.service.ppt.rendering.contract.v1.PptQualityCode;
import com.dropai.rewrite.service.ppt.rendering.contract.v1.enums.PageType;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LayoutCatalogTest {
    private final LayoutCatalogLoader loader = new LayoutCatalogLoader();

    @Test
    void loadsTheFrozenTwentyRecipesInContractOrder() {
        LayoutCatalog catalog = loader.loadAcademicV1();

        assertEquals("academic-layouts-v1", catalog.catalogId());
        assertEquals("1.1.0", catalog.catalogVersion());
        assertEquals(21, catalog.recipes().size());
        assertEquals(LayoutIds.ORDERED, catalog.recipes().stream().map(LayoutRecipe::layoutId).toList());
        assertEquals(LayoutIds.ALL, catalog.recipesById().keySet());
        assertTrue(catalog.catalogHash().matches("sha256:[a-f0-9]{64}"));
        assertThrows(UnsupportedOperationException.class, () -> catalog.recipes().clear());
        assertThrows(UnsupportedOperationException.class, () -> catalog.recipesById().clear());
    }

    @Test
    void allRecipesStayInsideTheFrozenGridAndSlideHeight() {
        LayoutCatalog catalog = loader.loadAcademicV1();

        catalog.recipes().forEach(recipe -> recipe.slots().forEach((name, slot) -> {
            assertTrue(slot.gridColumn() >= 1, recipe.layoutId() + ":" + name);
            assertTrue(slot.lastGridColumn() <= 12, recipe.layoutId() + ":" + name);
            assertTrue(slot.topIn().compareTo(BigDecimal.ZERO) >= 0, recipe.layoutId() + ":" + name);
            assertTrue(slot.bottomIn().compareTo(new BigDecimal("7.5")) <= 0, recipe.layoutId() + ":" + name);
        }));
    }

    @Test
    void everyPageTypeHasAtLeastOneCompatibleRecipe() {
        Set<PageType> covered = EnumSet.noneOf(PageType.class);
        loader.loadAcademicV1().recipes().forEach(recipe -> covered.addAll(recipe.supports().pageTypes()));
        assertEquals(EnumSet.allOf(PageType.class), covered);
    }

    @Test
    void canonicalCatalogAndHashAreStableAcrossRepeatedLoads() {
        LayoutCatalog first = loader.loadAcademicV1();
        LayoutCatalog second = loader.loadAcademicV1();

        assertEquals(first.canonicalDocument(), second.canonicalDocument());
        assertEquals(first.catalogHash(), second.catalogHash());
        assertFalse(first.canonicalDocument().contains("F:\\"));
        assertFalse(first.canonicalDocument().contains("C:\\"));
        assertFalse(first.canonicalDocument().contains("/tmp/"));
    }

    @Test
    void containedTextOverlapAndImageCropPoliciesAreExplicitAndMinimal() {
        LayoutCatalog catalog = loader.loadAcademicV1();
        Set<String> expectedContainedTextLayouts = Set.of(
                LayoutIds.AGENDA_VERTICAL_STEPS,
                LayoutIds.CONTENT_THREE_CARDS,
                LayoutIds.CONTENT_PROCESS_STEPS,
                LayoutIds.CONTENT_COMPARISON_COLUMNS,
                LayoutIds.CONTENT_ARCHITECTURE_LAYERS);

        catalog.recipes().forEach(recipe -> {
            if (expectedContainedTextLayouts.contains(recipe.layoutId())) {
                assertEquals(List.of("keyPointCard"),
                        recipe.constraints().allowedContainedTextComponents(), recipe.layoutId());
            } else {
                assertTrue(recipe.constraints().allowedContainedTextComponents().isEmpty(),
                        recipe.layoutId());
            }
            assertFalse(recipe.constraints().imageFit()
                            == com.dropai.rewrite.service.ppt.rendering.contract.v1.enums.ImageFitMode.COVER,
                    recipe.layoutId() + " cannot crop without frozen safe-crop metadata");
        });
    }

    @Test
    void semanticValidatorRejectsGridOverflowAndFallbackCycles() {
        LayoutCatalog catalog = loader.loadAcademicV1();
        LayoutCatalogValidator validator = new LayoutCatalogValidator();

        List<LayoutRecipe> badGrid = new ArrayList<>(catalog.recipes());
        LayoutRecipe originalCover = badGrid.get(0);
        LinkedHashMap<String, LayoutRecipe.Slot> badSlots = new LinkedHashMap<>(originalCover.slots());
        badSlots.put("title", new LayoutRecipe.Slot(12, 2, new BigDecimal("1"), new BigDecimal("1")));
        badGrid.set(0, copy(originalCover, badSlots, originalCover.fallbacks()));
        LayoutValidationException gridFailure = assertThrows(
                LayoutValidationException.class,
                () -> validator.validateCatalog(badGrid));
        assertEquals(PptQualityCode.SCHEMA_INVALID, gridFailure.qualityCode());
        assertTrue(gridFailure.getMessage().contains("12-column grid"));

        List<LayoutRecipe> cycle = new ArrayList<>(catalog.recipes());
        LayoutRecipe single = cycle.get(LayoutIds.ORDERED.indexOf(LayoutIds.CONTENT_SINGLE_INSIGHT));
        cycle.set(LayoutIds.ORDERED.indexOf(LayoutIds.CONTENT_SINGLE_INSIGHT),
                copy(single, single.slots(), List.of(LayoutIds.CONTENT_THREE_CARDS)));
        LayoutValidationException cycleFailure = assertThrows(
                LayoutValidationException.class,
                () -> validator.validateCatalog(cycle));
        assertTrue(cycleFailure.getMessage().contains("Fallback cycle"));
    }

    private LayoutRecipe copy(
            LayoutRecipe source,
            java.util.Map<String, LayoutRecipe.Slot> slots,
            List<String> fallbacks
    ) {
        return new LayoutRecipe(
                source.schemaVersion(),
                source.layoutId(),
                source.layoutVersion(),
                source.supports(),
                slots,
                source.constraints(),
                fallbacks);
    }
}
