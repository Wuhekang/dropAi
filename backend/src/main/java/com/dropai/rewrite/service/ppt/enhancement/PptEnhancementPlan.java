package com.dropai.rewrite.service.ppt.enhancement;

import java.util.List;

public record PptEnhancementPlan(
    String schemaVersion,
    String sourcePptxSha256,
    String skillName,
    String skillVersion,
    String skillHash,
    String mode,
    String profile,
    String textPolicy,
    String provider,
    String model,
    boolean providerInvoked,
    String providerStatus,
    List<SlidePlan> slides
) {
    public record SlidePlan(
        int slideNumber,
        int sourceSlideNumber,
        String archetype,
        String recipeId,
        String focalEnhancement,
        List<String> microDetails,
        boolean backgroundOnly,
        List<Addition> additions
    ) {
        public SlidePlan(
            int slideNumber,
            int sourceSlideNumber,
            String archetype,
            String recipeId,
            String focalEnhancement,
            List<String> microDetails,
            List<Addition> additions
        ) {
            this(slideNumber, sourceSlideNumber, archetype, recipeId, focalEnhancement, microDetails,
                "image".equals(archetype) && "IMAGE_BACKGROUND".equals(recipeId), additions);
        }
    }

    public record Addition(
        String type,
        String purpose,
        String boundedZone,
        boolean mustNotOverlapInherited,
        boolean textBearing
    ) {}
}
