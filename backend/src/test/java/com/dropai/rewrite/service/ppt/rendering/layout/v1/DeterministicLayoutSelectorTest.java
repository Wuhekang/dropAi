package com.dropai.rewrite.service.ppt.rendering.layout.v1;

import com.dropai.rewrite.service.ppt.rendering.contract.v1.LayoutIds;
import com.dropai.rewrite.service.ppt.rendering.contract.v1.enums.AssetKind;
import com.dropai.rewrite.service.ppt.rendering.contract.v1.enums.ContentType;
import com.dropai.rewrite.service.ppt.rendering.contract.v1.enums.ImageRole;
import com.dropai.rewrite.service.ppt.rendering.contract.v1.enums.PagePurpose;
import com.dropai.rewrite.service.ppt.rendering.contract.v1.enums.PageType;
import com.dropai.rewrite.service.ppt.rendering.contract.v1.enums.TableKind;
import org.junit.jupiter.api.Test;

import java.lang.reflect.RecordComponent;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DeterministicLayoutSelectorTest {
    private final LayoutCatalog catalog = new LayoutCatalogLoader().loadAcademicV1();
    private final DeterministicLayoutSelector selector = new DeterministicLayoutSelector(catalog);

    @Test
    void selectsAllStructuralFamiliesWithoutReadingTextMeaning() {
        assertLayout(LayoutIds.COVER_CENTERED, noMedia(PageType.COVER, PagePurpose.BACKGROUND, ContentType.NARRATIVE, 3, 80, 0, 0));
        assertLayout(LayoutIds.AGENDA_VERTICAL_STEPS, noMedia(PageType.AGENDA, PagePurpose.METHOD, ContentType.KEY_POINTS, 5, 100, 0, 0));
        assertLayout(LayoutIds.CONTENT_THREE_CARDS, noMedia(PageType.CONTENT, PagePurpose.BACKGROUND, ContentType.KEY_POINTS, 3, 160, 0, 0));
        assertLayout(LayoutIds.CONTENT_SINGLE_INSIGHT, noMedia(PageType.CONTENT, PagePurpose.BACKGROUND, ContentType.NARRATIVE, 1, 180, 0, 0));
        assertLayout(LayoutIds.CONTENT_PROCESS_STEPS, noMedia(PageType.CONTENT, PagePurpose.IMPLEMENTATION, ContentType.PROCESS, 3, 160, 0, 0));
        assertLayout(LayoutIds.CONTENT_COMPARISON_COLUMNS, noMedia(PageType.CONTENT, PagePurpose.RESULT, ContentType.COMPARISON, 2, 180, 0, 0));
        assertLayout(LayoutIds.CONTENT_ARCHITECTURE_LAYERS, noMedia(PageType.CONTENT, PagePurpose.DESIGN, ContentType.ARCHITECTURE, 3, 170, 0, 0));

        assertLayout(LayoutIds.CONTENT_TEXT_VISUAL_SPLIT,
                image(PageType.CONTENT, PagePurpose.DESIGN, ContentType.MIXED, ImageRole.INFORMATION, AssetKind.DIAGRAM, 1_600_000));
        assertLayout(LayoutIds.IMAGE_DIAGRAM_WITH_NOTES,
                image(PageType.IMAGE, PagePurpose.DESIGN, ContentType.FIGURE, ImageRole.INFORMATION, AssetKind.DIAGRAM, 850_000));
        assertLayout(LayoutIds.IMAGE_PROOF_SCREENSHOT_WIDE,
                image(PageType.IMAGE, PagePurpose.IMPLEMENTATION, ContentType.FIGURE, ImageRole.PROOF, AssetKind.SCREENSHOT, 1_777_778));
        assertLayout(LayoutIds.IMAGE_RESULT_CHART_WITH_FINDING,
                image(PageType.IMAGE, PagePurpose.RESULT, ContentType.METRICS, ImageRole.PROOF, AssetKind.SCREENSHOT, 2_000_000));
        assertLayout(LayoutIds.IMAGE_EFFECT_FULL_VISUAL,
                image(PageType.IMAGE, PagePurpose.RESULT, ContentType.FIGURE, ImageRole.EFFECT, AssetKind.DESIGN_RENDER, 1_777_778));

        assertLayout(LayoutIds.TABLE_GENERIC_COMPACT, table(PagePurpose.RESULT, TableKind.GENERIC, 4, 3));
        assertLayout(LayoutIds.TABLE_ENTITY_PURPOSE_CARDS, table(PagePurpose.DATABASE, TableKind.ENTITY_PURPOSE, 4, 2));
        assertLayout(LayoutIds.TABLE_TEST_RESULT_STATUS, table(PagePurpose.TEST, TableKind.TEST_RESULT, 5, 4));

        assertLayout(LayoutIds.SUMMARY_ACHIEVEMENTS,
                noMedia(PageType.SUMMARY, PagePurpose.SUMMARY, ContentType.KEY_POINTS, 3, 170, 1, 2));
        assertLayout(LayoutIds.SUMMARY_FUTURE,
                noMedia(PageType.SUMMARY, PagePurpose.SUMMARY, ContentType.KEY_POINTS, 3, 170, 2, 2));
        assertLayout(LayoutIds.THANKS_CENTERED,
                noMedia(PageType.THANKS, PagePurpose.SUMMARY, ContentType.NARRATIVE, 2, 60, 0, 0));
    }

    @Test
    void repeatedSelectionIsByteForByteStableInOrderingAndScore() {
        PageLayoutFeatures features = image(
                PageType.IMAGE,
                PagePurpose.RESULT,
                ContentType.METRICS,
                ImageRole.PROOF,
                AssetKind.SCREENSHOT,
                2_134_454);
        SelectedLayout expected = selector.select(features);

        for (int attempt = 0; attempt < 100; attempt++) {
            assertEquals(expected, selector.select(features));
        }
    }

    @Test
    void summaryVariantUsesOnlySequencePosition() {
        PageLayoutFeatures first = noMedia(
                PageType.SUMMARY, PagePurpose.SUMMARY, ContentType.KEY_POINTS, 3, 160, 1, 2);
        PageLayoutFeatures last = noMedia(
                PageType.SUMMARY, PagePurpose.SUMMARY, ContentType.KEY_POINTS, 3, 160, 2, 2);

        assertEquals(LayoutIds.SUMMARY_ACHIEVEMENTS, selector.select(first).layoutId());
        assertEquals(LayoutIds.SUMMARY_FUTURE, selector.select(last).layoutId());
    }

    @Test
    void selectorInputExposesLengthsButNoTextValuesOrSemanticCopy() {
        Set<String> componentNames = Arrays.stream(PageLayoutFeatures.class.getRecordComponents())
                .map(RecordComponent::getName)
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        assertTrue(componentNames.containsAll(List.of("titleLength", "descriptionLength", "bodyCharacterCount")));
        assertFalse(componentNames.contains("title"));
        assertFalse(componentNames.contains("description"));
        assertFalse(componentNames.contains("answerQuestion"));
        assertFalse(componentNames.contains("sourceChapter"));
    }

    private void assertLayout(String expected, PageLayoutFeatures features) {
        assertEquals(expected, selector.select(features).layoutId());
    }

    private PageLayoutFeatures noMedia(
            PageType pageType,
            PagePurpose purpose,
            ContentType contentType,
            int keyPoints,
            int bodyChars,
            int summaryOrdinal,
            int summaryCount
    ) {
        return new PageLayoutFeatures(
                pageType, purpose, contentType,
                18, keyPoints, Math.min(bodyChars, 80), bodyChars, 0,
                0, null, null, 0,
                0, 0, null,
                summaryOrdinal, summaryCount);
    }

    private PageLayoutFeatures image(
            PageType pageType,
            PagePurpose purpose,
            ContentType contentType,
            ImageRole role,
            AssetKind kind,
            int aspectRatioMillionths
    ) {
        return new PageLayoutFeatures(
                pageType, purpose, contentType,
                18, 2, 60, 100, 60,
                1, role, kind, aspectRatioMillionths,
                0, 0, null,
                0, 0);
    }

    private PageLayoutFeatures table(PagePurpose purpose, TableKind kind, int rows, int columns) {
        return new PageLayoutFeatures(
                PageType.TABLE, purpose, ContentType.TABULAR,
                18, 3, 60, 240, 0,
                0, null, null, 0,
                rows, columns, kind,
                0, 0);
    }
}
