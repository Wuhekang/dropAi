package com.dropai.rewrite.service.ppt.rendering.renderer.v1;

import com.dropai.rewrite.service.ppt.rendering.canonical.v1.FrozenSlideRenderPlan;

import java.io.OutputStream;

/** Executes an already validated and frozen RenderPlan without making presentation decisions. */
public interface PurePptxRenderer {
    RenderedPptx render(
            FrozenSlideRenderPlan plan,
            AssetBinaryResolver assetResolver,
            OutputStream output
    );
}
