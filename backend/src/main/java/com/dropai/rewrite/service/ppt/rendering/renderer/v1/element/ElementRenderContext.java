package com.dropai.rewrite.service.ppt.rendering.renderer.v1.element;

import com.dropai.rewrite.service.ppt.rendering.contract.v1.PptQualityCode;
import com.dropai.rewrite.service.ppt.rendering.renderer.v1.AssetBinaryResolver;
import com.dropai.rewrite.service.ppt.rendering.renderer.v1.RendererExecutionException;
import com.dropai.rewrite.service.ppt.rendering.renderer.v1.VerifiedAssetBytes;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.apache.poi.xslf.usermodel.XMLSlideShow;
import org.apache.poi.xslf.usermodel.XSLFSlide;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Collections;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/** Infrastructure supplied to element executors; it contains no planning services. */
public final class ElementRenderContext {
    private final XMLSlideShow document;
    private final XSLFSlide slide;
    private final String slideId;
    private final Map<String, ObjectNode> assets;
    private final AssetBinaryResolver assetResolver;

    public ElementRenderContext(
            XMLSlideShow document,
            XSLFSlide slide,
            String slideId,
            Map<String, ObjectNode> assets,
            AssetBinaryResolver assetResolver
    ) {
        this.document = Objects.requireNonNull(document, "document");
        this.slide = Objects.requireNonNull(slide, "slide");
        this.slideId = Objects.requireNonNull(slideId, "slideId");
        Objects.requireNonNull(assets, "assets");
        LinkedHashMap<String, ObjectNode> copy = new LinkedHashMap<>();
        assets.forEach((key, value) -> copy.put(key, value.deepCopy()));
        this.assets = Collections.unmodifiableMap(copy);
        this.assetResolver = Objects.requireNonNull(assetResolver, "assetResolver");
    }

    public XMLSlideShow document() {
        return document;
    }

    public XSLFSlide slide() {
        return slide;
    }

    public String slideId() {
        return slideId;
    }

    public ObjectNode requireAsset(String assetId, String elementId) {
        ObjectNode asset = assets.get(assetId);
        if (asset == null) {
            throw failure(PptQualityCode.MANDATORY_ASSET_MISSING,
                    "RenderPlan asset is missing: " + assetId, elementId, null);
        }
        return asset.deepCopy();
    }

    public VerifiedAssetBytes resolveAndVerifyAsset(ObjectNode asset, String elementId) {
        String assetId = PlanElementSupport.requiredText(asset, "assetId");
        String bundlePath = PlanElementSupport.requiredText(asset, "bundlePath");
        String expectedSha256 = PlanElementSupport.requiredText(asset, "sha256");
        VerifiedAssetBytes resolved;
        try {
            resolved = assetResolver.resolve(assetId, bundlePath, expectedSha256);
        } catch (RendererExecutionException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw failure(PptQualityCode.MANDATORY_ASSET_MISSING,
                    "Asset resolver failed for " + assetId, elementId, exception);
        }
        if (resolved == null) {
            throw failure(PptQualityCode.MANDATORY_ASSET_MISSING,
                    "Asset resolver returned no bytes for " + assetId, elementId, null);
        }
        byte[] bytes = resolved.bytes();
        String actualSha256 = sha256(bytes);
        boolean identityMatches = assetId.equals(resolved.assetId())
                && bundlePath.equals(resolved.bundlePath())
                && expectedSha256.equals(resolved.sha256());
        boolean mimeMatches = resolved.mimeType() == null
                || PlanElementSupport.requiredText(asset, "mimeType").equals(resolved.mimeType());
        if (!identityMatches || !mimeMatches || !expectedSha256.equals(actualSha256)) {
            throw failure(PptQualityCode.ASSET_HASH_MISMATCH,
                    "Asset identity or SHA-256 mismatch for " + assetId, elementId, null);
        }
        return resolved;
    }

    public RendererExecutionException failure(
            PptQualityCode qualityCode,
            String message,
            String elementId,
            Throwable cause
    ) {
        return new RendererExecutionException(qualityCode, message, slideId, elementId, cause);
    }

    private static String sha256(byte[] bytes) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(bytes);
            return "sha256:" + HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }
}
