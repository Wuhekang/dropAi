package com.dropai.rewrite.service.ppt.rendering.production.v1;

import com.dropai.rewrite.service.ppt.rendering.bundle.v1.ProductionFontInventory;
import com.dropai.rewrite.service.ppt.rendering.canonical.v1.FrozenSlideRenderPlan;
import com.dropai.rewrite.service.ppt.rendering.renderer.v1.AssetBinaryResolver;

public record ProductionRenderPlanPackage(
        FrozenSlideRenderPlan plan,
        String renderPlanHash,
        AssetBinaryResolver sourceAssets,
        ProductionFontInventory actualFonts
) {}
