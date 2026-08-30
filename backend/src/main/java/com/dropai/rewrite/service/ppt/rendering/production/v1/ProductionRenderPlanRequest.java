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
        PptContentPlannerV2.PlannerInput plannerInput
) {
    public ProductionRenderPlanRequest {
        metadata = Map.copyOf(metadata);
        sourceBlocks = List.copyOf(sourceBlocks);
    }
}
