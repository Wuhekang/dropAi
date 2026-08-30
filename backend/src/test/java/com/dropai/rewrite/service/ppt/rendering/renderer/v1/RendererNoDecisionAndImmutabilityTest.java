package com.dropai.rewrite.service.ppt.rendering.renderer.v1;

import com.dropai.rewrite.service.ppt.rendering.canonical.v1.RenderPlanHasher;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

class RendererNoDecisionAndImmutabilityTest {
    @Test
    void layoutAndPageSemanticsDoNotChangeTheExecutedShapeTree() {
        RendererTestSupport.RenderedFixture baseline = RendererTestSupport.renderedFixture();
        var semanticallyMutated = RendererTestSupport.mutateFixture(plan -> {
            int index = 0;
            for (var slide : plan.withArray("slides")) {
                ObjectNode object = (ObjectNode) slide;
                object.put("layoutId", "ignored-layout-" + (++index));
                object.put("pageType", index % 2 == 0 ? "COVER" : "THANKS");
            }
            for (var asset : plan.withArray("assets")) {
                ObjectNode object = (ObjectNode) asset;
                object.put("assetKind", "IGNORED_ASSET_KIND");
                object.put("imageRole", "IGNORED_IMAGE_ROLE");
            }
            return plan;
        });

        RendererTestSupport.RenderedFixture repeated = RendererTestSupport.render(
                semanticallyMutated,
                RendererTestSupport.classpathAssetResolver());

        assertEquals(
                RendererTestSupport.slideXml(baseline.pptx()),
                RendererTestSupport.slideXml(repeated.pptx()));
        assertEquals(mediaHashes(baseline.pptx()), mediaHashes(repeated.pptx()));
    }

    @Test
    void renderingCannotMutateTheFrozenPlanOrItsHash() {
        var plan = RendererTestSupport.frozenFixture();
        byte[] beforeBytes = plan.canonicalBytes();
        String beforeHash = new RenderPlanHasher().hash(plan);

        RendererTestSupport.RenderedFixture rendered = RendererTestSupport.render(
                plan,
                RendererTestSupport.classpathAssetResolver());

        assertArrayEquals(beforeBytes, plan.canonicalBytes());
        assertEquals(beforeHash, new RenderPlanHasher().hash(plan));
        assertEquals(beforeHash, rendered.result().renderPlanHash());
    }

    @Test
    void verifiedAssetBytesDoesNotExposeMutableBinaryState() {
        byte[] source = new byte[]{1, 2, 3};
        VerifiedAssetBytes verified = new VerifiedAssetBytes(
                "asset", "assets/asset.png", RendererTestSupport.sha256(source), "image/png", source);
        source[0] = 9;
        byte[] escaped = verified.bytes();
        escaped[1] = 9;

        assertArrayEquals(new byte[]{1, 2, 3}, verified.bytes());
    }

    private List<String> mediaHashes(byte[] pptx) {
        List<String> hashes = RendererTestSupport.zipEntries(pptx).entrySet().stream()
                .filter(entry -> entry.getKey().startsWith("ppt/media/"))
                .map(Map.Entry::getValue)
                .map(RendererTestSupport::sha256)
                .collect(Collectors.toCollection(ArrayList::new));
        Collections.sort(hashes);
        return hashes;
    }
}
