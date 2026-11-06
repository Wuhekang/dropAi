package com.dropai.rewrite.service.ppt.rendering.production.v1;

import com.dropai.rewrite.service.ppt.PptContentPlannerV2;
import com.dropai.rewrite.service.ppt.PptOutlineValidatorV1;

import java.util.List;
import java.util.Map;

/** Complete, in-memory production input. It must never be reconstructed from ppt_slide. */
public record ProductionRenderPlanRequest(
        String projectId,
        Map<String, String> metadata,
        List<String> sourceBlocks,
        PptOutlineValidatorV1.ValidationResult validatedTree,
        PptContentPlannerV2.PlannerInput plannerInput,
        String templatePackId,
        String visualSeed
) {
    public ProductionRenderPlanRequest(
            String projectId,
            Map<String, String> metadata,
            List<String> sourceBlocks,
            PptOutlineValidatorV1.ValidationResult validatedTree,
            PptContentPlannerV2.PlannerInput plannerInput,
            String templatePackId
    ) {
        this(projectId, metadata, sourceBlocks, validatedTree, plannerInput, templatePackId, "");
    }

    /** Compatibility constructor for the frozen academic-purple baseline. */
    public ProductionRenderPlanRequest(
            String projectId,
            Map<String, String> metadata,
            List<String> sourceBlocks,
            PptOutlineValidatorV1.ValidationResult validatedTree,
            PptContentPlannerV2.PlannerInput plannerInput
    ) {
        this(projectId, metadata, sourceBlocks, validatedTree, plannerInput, "academic-purple", "");
    }

    public ProductionRenderPlanRequest {
        metadata = Map.copyOf(metadata);
        sourceBlocks = List.copyOf(sourceBlocks);
        if (templatePackId == null || templatePackId.isBlank()) {
            templatePackId = "academic-purple";
        } else {
            templatePackId = templatePackId.strip();
        }
        visualSeed = visualSeed == null ? "" : visualSeed.strip();
        if (!visualSeed.isEmpty() && !visualSeed.matches("[a-f0-9]{64}")) {
            throw new IllegalArgumentException("visualSeed must be a lower-case SHA-256 hex string");
        }
    }
}
