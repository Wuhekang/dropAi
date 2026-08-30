package com.dropai.rewrite.service.ppt.rendering.compiler.v1;

import com.dropai.rewrite.service.ppt.rendering.contract.v1.PptQualityCode;
import com.dropai.rewrite.service.ppt.rendering.contract.v1.enums.AssetKind;
import com.dropai.rewrite.service.ppt.rendering.contract.v1.enums.ContentType;
import com.dropai.rewrite.service.ppt.rendering.contract.v1.enums.ImageRole;
import com.dropai.rewrite.service.ppt.rendering.contract.v1.enums.PagePurpose;
import com.dropai.rewrite.service.ppt.rendering.contract.v1.enums.PageType;
import com.dropai.rewrite.service.ppt.rendering.contract.v1.enums.TableKind;
import com.dropai.rewrite.service.ppt.rendering.layout.v1.PageLayoutFeatures;
import com.fasterxml.jackson.databind.JsonNode;

import java.math.BigInteger;

/** Extracts structural features only; no title or paragraph value escapes this class. */
final class PageLayoutFeatureExtractor {
    PageLayoutFeatures extract(
            JsonNode page,
            RenderingAssetBundle bundle,
            int summaryOrdinal,
            int summaryCount
    ) {
        PageType pageType = enumValue(PageType.class, page, "pageType");
        PagePurpose purpose = enumValue(PagePurpose.class, page, "pagePurpose");
        ContentType contentType = enumValue(ContentType.class, page, "contentType");
        JsonNode bindings = page.path("assets");
        int assetCount = bindings.isArray() ? bindings.size() : 0;
        ImageRole imageRole = null;
        AssetKind assetKind = null;
        int aspect = 0;
        if (assetCount > 0) {
            JsonNode binding = bindings.get(0);
            ObjectNodeLike asset = ObjectNodeLike.of(bundle.requireAsset(requiredText(binding, "assetId")));
            imageRole = enumValue(ImageRole.class, asset.node(), "imageRole");
            assetKind = enumValue(AssetKind.class, asset.node(), "assetKind");
            aspect = ratioMillionths(asset.intValue("widthPx"), asset.intValue("heightPx"));
        }

        int tableRows = 0;
        int tableColumns = 0;
        TableKind tableKind = null;
        JsonNode tableBindings = page.path("tables");
        if (tableBindings.isArray() && !tableBindings.isEmpty()) {
            JsonNode binding = tableBindings.get(0);
            JsonNode table = bundle.requireTable(requiredText(binding, "tableId"));
            tableRows = table.path("rows").size();
            tableColumns = table.path("columns").size();
            tableKind = enumValue(TableKind.class, table, "tableKind");
        }

        int keyPointCharacters = textArrayCharacters(page.path("keyPoints"));
        int descriptionLength = length(page.path("description").asText(""));
        return new PageLayoutFeatures(
                pageType,
                purpose,
                contentType,
                length(requiredText(page, "title")),
                page.path("keyPoints").isArray() ? page.path("keyPoints").size() : 0,
                descriptionLength,
                keyPointCharacters + descriptionLength,
                pageType == PageType.IMAGE || pageType == PageType.CONTENT && assetCount > 0
                        ? descriptionLength
                        : 0,
                assetCount,
                imageRole,
                assetKind,
                aspect,
                tableRows,
                tableColumns,
                tableKind,
                pageType == PageType.SUMMARY ? summaryOrdinal : 0,
                pageType == PageType.SUMMARY ? summaryCount : 0);
    }

    private int textArrayCharacters(JsonNode values) {
        int count = 0;
        if (values.isArray()) {
            for (JsonNode value : values) {
                if (value.isTextual()) {
                    count += length(value.textValue());
                }
            }
        }
        return count;
    }

    private int ratioMillionths(int width, int height) {
        if (width < 1 || height < 1) {
            throw new RenderPlanCompilationException(
                    PptQualityCode.INVALID_REFERENCE,
                    "Asset dimensions must be positive");
        }
        BigInteger numerator = BigInteger.valueOf(width).multiply(BigInteger.valueOf(1_000_000L));
        BigInteger[] quotient = numerator.divideAndRemainder(BigInteger.valueOf(height));
        if (quotient[1].multiply(BigInteger.TWO).compareTo(BigInteger.valueOf(height)) >= 0) {
            quotient[0] = quotient[0].add(BigInteger.ONE);
        }
        return quotient[0].intValueExact();
    }

    private int length(String value) {
        return value.codePointCount(0, value.length());
    }

    private <E extends Enum<E>> E enumValue(Class<E> type, JsonNode owner, String field) {
        String value = requiredText(owner, field);
        try {
            return Enum.valueOf(type, value);
        } catch (IllegalArgumentException exception) {
            throw new RenderPlanCompilationException(
                    PptQualityCode.UNKNOWN_ENUM_VALUE,
                    "Unknown " + field + ": " + value,
                    exception);
        }
    }

    private String requiredText(JsonNode owner, String field) {
        JsonNode value = owner.path(field);
        if (!value.isTextual() || value.textValue().isBlank()) {
            throw new RenderPlanCompilationException(
                    PptQualityCode.UNRENDERABLE_PAGE,
                    field + " must be a non-blank string");
        }
        return value.textValue();
    }

    private record ObjectNodeLike(JsonNode node) {
        static ObjectNodeLike of(JsonNode node) {
            return new ObjectNodeLike(node);
        }

        int intValue(String field) {
            int value = node.path(field).asInt(0);
            if (value < 1) {
                throw new RenderPlanCompilationException(
                        PptQualityCode.INVALID_REFERENCE,
                        field + " must be positive");
            }
            return value;
        }
    }
}
