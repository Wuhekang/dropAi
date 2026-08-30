package com.dropai.rewrite.service.ppt.rendering.renderer.v1.element;

import com.dropai.rewrite.service.ppt.rendering.contract.v1.PptQualityCode;
import com.dropai.rewrite.service.ppt.rendering.renderer.v1.RendererExecutionException;
import com.dropai.rewrite.service.ppt.rendering.renderer.v1.VerifiedAssetBytes;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.apache.poi.sl.usermodel.PictureData;
import org.apache.poi.xslf.usermodel.XSLFPictureData;
import org.apache.poi.xslf.usermodel.XSLFPictureShape;

/** Inserts the exact verified image bytes at the final box and crop supplied by the plan. */
public final class ImageElementRenderer implements ElementRenderer {
    @Override
    public String elementType() {
        return "IMAGE";
    }

    @Override
    public void render(ObjectNode element, ElementRenderContext context) {
        String elementId = PlanElementSupport.requiredText(element, "elementId");
        try {
            String assetId = PlanElementSupport.requiredText(element, "assetId");
            ObjectNode asset = context.requireAsset(assetId, elementId);
            VerifiedAssetBytes verified = context.resolveAndVerifyAsset(asset, elementId);
            PictureData.PictureType pictureType = pictureType(
                    PlanElementSupport.requiredText(asset, "mimeType"));
            XSLFPictureData pictureData = context.document().addPicture(verified.bytes(), pictureType);
            XSLFPictureShape picture = context.slide().createPicture(pictureData);
            picture.setAnchor(PlanElementSupport.anchor(element));

            String fitMode = PlanElementSupport.requiredText(element, "fitMode");
            boolean cropAllowed = PlanElementSupport.requiredBoolean(element, "cropAllowed");
            if ("COVER".equals(fitMode)) {
                if (!cropAllowed || !element.path("sourceCrop").isObject()) {
                    throw context.failure(PptQualityCode.CROP_NOT_ALLOWED,
                            "COVER image is missing its planned source crop", elementId, null);
                }
                PoiElementSupport.applyCrop(picture, (ObjectNode) element.path("sourceCrop"));
            } else if (!"CONTAIN".equals(fitMode)) {
                throw new IllegalArgumentException("Unsupported fitMode: " + fitMode);
            } else if (cropAllowed || element.has("sourceCrop")) {
                throw context.failure(PptQualityCode.CROP_NOT_ALLOWED,
                        "CONTAIN image must not carry crop execution data", elementId, null);
            }

            ObjectNode style = PlanElementSupport.requiredObject(element, "resolvedStyle");
            PoiElementSupport.applyImageOpacity(picture,
                    PlanElementSupport.requiredInt(style, "opacityPermille"));
            long borderWidthEmu = PlanElementSupport.requiredLong(style, "borderWidthEmu");
            if (borderWidthEmu == 0) {
                picture.setLineColor(null);
            } else {
                picture.setLineColor(PlanElementSupport.color(style, "borderColor"));
                picture.setLineWidth(PlanElementSupport.points(borderWidthEmu));
            }
            PoiElementSupport.applyRoundedGeometry(
                    PoiElementSupport.shapeProperties(picture),
                    PlanElementSupport.requiredLong(style, "cornerRadiusEmu"),
                    PlanElementSupport.requiredLong(element, "widthEmu"),
                    PlanElementSupport.requiredLong(element, "heightEmu"));
            if (style.path("shadow").isObject()) {
                PoiElementSupport.applyShadow(
                        PoiElementSupport.shapeProperties(picture),
                        (ObjectNode) style.path("shadow"));
            }
        } catch (RuntimeException exception) {
            if (exception instanceof RendererExecutionException renderer) {
                throw renderer;
            }
            throw context.failure(PptQualityCode.OOXML_PACKAGE_INVALID,
                    "Failed to execute IMAGE element " + elementId, elementId, exception);
        }
    }

    private static PictureData.PictureType pictureType(String mimeType) {
        return switch (mimeType) {
            case "image/png" -> PictureData.PictureType.PNG;
            case "image/jpeg" -> PictureData.PictureType.JPEG;
            case "image/webp" -> throw new IllegalStateException(
                    "Apache POI 5.3 cannot preserve WebP bytes as a native picture part");
            default -> throw new IllegalArgumentException("Unsupported image MIME type: " + mimeType);
        };
    }
}
