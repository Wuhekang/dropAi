package com.dropai.rewrite.service.ppt.rendering.layout.v1;

import com.dropai.rewrite.service.ppt.rendering.contract.v1.enums.AssetKind;
import com.dropai.rewrite.service.ppt.rendering.contract.v1.enums.ContentType;
import com.dropai.rewrite.service.ppt.rendering.contract.v1.enums.ImageFitMode;
import com.dropai.rewrite.service.ppt.rendering.contract.v1.enums.ImageRole;
import com.dropai.rewrite.service.ppt.rendering.contract.v1.enums.PagePurpose;
import com.dropai.rewrite.service.ppt.rendering.contract.v1.enums.PageType;
import com.dropai.rewrite.service.ppt.rendering.contract.v1.enums.TableKind;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public record LayoutRecipe(
        String schemaVersion,
        String layoutId,
        String layoutVersion,
        Supports supports,
        Map<String, Slot> slots,
        Constraints constraints,
        List<String> fallbacks
) {
    public LayoutRecipe {
        schemaVersion = requireText(schemaVersion, "schemaVersion");
        layoutId = requireText(layoutId, "layoutId");
        layoutVersion = requireText(layoutVersion, "layoutVersion");
        supports = Objects.requireNonNull(supports, "supports");
        slots = Map.copyOf(new LinkedHashMap<>(Objects.requireNonNull(slots, "slots")));
        constraints = Objects.requireNonNull(constraints, "constraints");
        fallbacks = List.copyOf(Objects.requireNonNull(fallbacks, "fallbacks"));
    }

    private static String requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value;
    }

    public record Supports(
            List<PageType> pageTypes,
            List<PagePurpose> pagePurposes,
            List<ContentType> contentTypes,
            List<ImageRole> imageRoles,
            List<AssetKind> assetKinds,
            List<TableKind> tableKinds,
            AssetCount assetCount
    ) {
        public Supports {
            pageTypes = List.copyOf(Objects.requireNonNull(pageTypes, "pageTypes"));
            pagePurposes = List.copyOf(Objects.requireNonNull(pagePurposes, "pagePurposes"));
            contentTypes = List.copyOf(Objects.requireNonNull(contentTypes, "contentTypes"));
            imageRoles = List.copyOf(Objects.requireNonNull(imageRoles, "imageRoles"));
            assetKinds = List.copyOf(Objects.requireNonNull(assetKinds, "assetKinds"));
            tableKinds = List.copyOf(Objects.requireNonNull(tableKinds, "tableKinds"));
            assetCount = Objects.requireNonNull(assetCount, "assetCount");
        }
    }

    public record AssetCount(int min, int max) {
    }

    public record Slot(int gridColumn, int gridSpan, BigDecimal topIn, BigDecimal heightIn) {
        public Slot {
            topIn = Objects.requireNonNull(topIn, "topIn");
            heightIn = Objects.requireNonNull(heightIn, "heightIn");
        }

        public BigDecimal bottomIn() {
            return topIn.add(heightIn);
        }

        public int lastGridColumn() {
            return gridColumn + gridSpan - 1;
        }
    }

    public record Constraints(
            int titleMaxLines,
            int bodyMaxChars,
            int captionMaxChars,
            int captionMaxLines,
            ImageFitMode imageFit,
            boolean cropAllowed,
            BigDecimal minImageAreaRatio,
            List<String> allowedContainedTextComponents
    ) {
        public Constraints {
            captionMaxLines = captionMaxLines <= 0 ? 3 : captionMaxLines;
            imageFit = Objects.requireNonNull(imageFit, "imageFit");
            minImageAreaRatio = Objects.requireNonNull(minImageAreaRatio, "minImageAreaRatio");
            allowedContainedTextComponents = allowedContainedTextComponents == null
                    ? List.of()
                    : List.copyOf(allowedContainedTextComponents);
        }
    }
}
