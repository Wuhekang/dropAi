package com.dropai.rewrite.service.ppt.rendering.renderer.v1;

/** Minimal execution receipt. Package inspection and quality state belong to Commit 7. */
public record RenderedPptx(
        String rendererVersion,
        String renderPlanHash,
        int slideCount,
        long writtenBytes
) {
}
