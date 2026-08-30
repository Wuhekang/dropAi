package com.dropai.rewrite.service.ppt.rendering.renderer.v1;

import com.dropai.rewrite.service.ppt.rendering.contract.v1.PptQualityCode;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RendererAssetFailureTest {
    @Test
    void resolvesEveryPlannedImageExactlyOnceByExactIdentityAndHash() {
        AssetBinaryResolver delegate = RendererTestSupport.classpathAssetResolver();
        List<String> calls = new ArrayList<>();
        AssetBinaryResolver tracking = (assetId, bundlePath, expectedSha256) -> {
            calls.add(assetId + "|" + bundlePath + "|" + expectedSha256);
            return delegate.resolve(assetId, bundlePath, expectedSha256);
        };

        RendererTestSupport.render(RendererTestSupport.frozenFixture(), tracking);

        assertEquals(25, calls.size());
        assertEquals(25, new HashSet<>(calls).size());
    }

    @Test
    void rejectsMissingAssetsWithoutGeneratingAPlaceholder() {
        RendererExecutionException failure = assertThrows(
                RendererExecutionException.class,
                () -> RendererTestSupport.render(RendererTestSupport.frozenFixture(),
                        (assetId, bundlePath, expectedSha256) -> null));

        assertEquals(PptQualityCode.MANDATORY_ASSET_MISSING, failure.qualityCode());
        assertEquals("slide-006", failure.slideId());
        assertEquals("slide-006-image-figure_2_01", failure.elementId());
    }

    @Test
    void recalculatesTheAssetHashAndRejectsLyingResolverMetadata() {
        RendererExecutionException failure = assertThrows(
                RendererExecutionException.class,
                () -> RendererTestSupport.render(RendererTestSupport.frozenFixture(),
                        (assetId, bundlePath, expectedSha256) -> new VerifiedAssetBytes(
                                assetId,
                                bundlePath,
                                expectedSha256,
                                "image/png",
                                new byte[]{1, 2, 3})));

        assertEquals(PptQualityCode.ASSET_HASH_MISMATCH, failure.qualityCode());
        assertEquals("slide-006", failure.slideId());
        assertEquals("slide-006-image-figure_2_01", failure.elementId());
    }

    @Test
    void rejectsResolverOutputForAnotherAssetEvenWhenBytesAreCorrect() {
        AssetBinaryResolver delegate = RendererTestSupport.classpathAssetResolver();
        RendererExecutionException failure = assertThrows(
                RendererExecutionException.class,
                () -> RendererTestSupport.render(RendererTestSupport.frozenFixture(),
                        (assetId, bundlePath, expectedSha256) -> {
                            VerifiedAssetBytes exact = delegate.resolve(assetId, bundlePath, expectedSha256);
                            return new VerifiedAssetBytes(
                                    assetId + "-other",
                                    bundlePath,
                                    expectedSha256,
                                    exact.mimeType(),
                                    exact.bytes());
                        }));

        assertEquals(PptQualityCode.ASSET_HASH_MISMATCH, failure.qualityCode());
    }
}
