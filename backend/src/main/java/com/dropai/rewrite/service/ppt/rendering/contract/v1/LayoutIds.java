package com.dropai.rewrite.service.ppt.rendering.contract.v1;

import java.util.List;
import java.util.Set;

public final class LayoutIds {
    public static final String COVER_CENTERED = "cover-centered.v1";
    public static final String COVER_CENTERED_LONG_TITLE = "cover-centered-long-title.v1";
    public static final String AGENDA_VERTICAL_STEPS = "agenda-vertical-steps.v1";
    public static final String SECTION_CENTERED = "section-centered.v1";
    public static final String CONTENT_SINGLE_INSIGHT = "content-single-insight.v1";
    public static final String CONTENT_THREE_CARDS = "content-three-cards.v1";
    public static final String CONTENT_TEXT_VISUAL_SPLIT = "content-text-visual-split.v1";
    public static final String CONTENT_PROCESS_STEPS = "content-process-steps.v1";
    public static final String CONTENT_COMPARISON_COLUMNS = "content-comparison-columns.v1";
    public static final String CONTENT_ARCHITECTURE_LAYERS = "content-architecture-layers.v1";
    public static final String IMAGE_PROOF_SCREENSHOT_WIDE = "image-proof-screenshot-wide.v1";
    public static final String IMAGE_DIAGRAM_WITH_NOTES = "image-diagram-with-notes.v1";
    public static final String IMAGE_RESULT_CHART_WITH_FINDING = "image-result-chart-with-finding.v1";
    public static final String IMAGE_EFFECT_FULL_VISUAL = "image-effect-full-visual.v1";
    public static final String IMAGE_CAPTION_SIDE_COMPACT = "image-caption-side-compact.v1";
    public static final String IMAGE_CENTERED_CAPTION_BOTTOM = "image-centered-caption-bottom.v1";
    public static final String TABLE_GENERIC_COMPACT = "table-generic-compact.v1";
    public static final String TABLE_ENTITY_PURPOSE_CARDS = "table-entity-purpose-cards.v1";
    public static final String TABLE_TEST_RESULT_STATUS = "table-test-result-status.v1";
    public static final String SUMMARY_ACHIEVEMENTS = "summary-achievements.v1";
    public static final String SUMMARY_FUTURE = "summary-future.v1";
    public static final String THANKS_CENTERED = "thanks-centered.v1";

    public static final List<String> ORDERED = List.of(
            COVER_CENTERED,
            COVER_CENTERED_LONG_TITLE,
            AGENDA_VERTICAL_STEPS,
            SECTION_CENTERED,
            CONTENT_SINGLE_INSIGHT,
            CONTENT_THREE_CARDS,
            CONTENT_TEXT_VISUAL_SPLIT,
            CONTENT_PROCESS_STEPS,
            CONTENT_COMPARISON_COLUMNS,
            CONTENT_ARCHITECTURE_LAYERS,
            IMAGE_PROOF_SCREENSHOT_WIDE,
            IMAGE_DIAGRAM_WITH_NOTES,
            IMAGE_RESULT_CHART_WITH_FINDING,
            IMAGE_EFFECT_FULL_VISUAL,
            IMAGE_CAPTION_SIDE_COMPACT,
            IMAGE_CENTERED_CAPTION_BOTTOM,
            TABLE_GENERIC_COMPACT,
            TABLE_ENTITY_PURPOSE_CARDS,
            TABLE_TEST_RESULT_STATUS,
            SUMMARY_ACHIEVEMENTS,
            SUMMARY_FUTURE,
            THANKS_CENTERED
    );

    public static final Set<String> ALL = Set.copyOf(ORDERED);

    private LayoutIds() {
    }
}
