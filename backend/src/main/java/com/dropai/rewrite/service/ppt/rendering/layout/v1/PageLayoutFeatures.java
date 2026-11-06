package com.dropai.rewrite.service.ppt.rendering.layout.v1;

import com.dropai.rewrite.service.ppt.rendering.contract.v1.enums.AssetKind;
import com.dropai.rewrite.service.ppt.rendering.contract.v1.enums.ContentType;
import com.dropai.rewrite.service.ppt.rendering.contract.v1.enums.ImageRole;
import com.dropai.rewrite.service.ppt.rendering.contract.v1.enums.PagePurpose;
import com.dropai.rewrite.service.ppt.rendering.contract.v1.enums.PageType;
import com.dropai.rewrite.service.ppt.rendering.contract.v1.enums.TableKind;

import java.util.Objects;

/**
 * Structural-only features used for deterministic layout selection. This type intentionally carries
 * text lengths but never text values, so a selector cannot become a second content planner.
 */
public record PageLayoutFeatures(
        PageType pageType,
        PagePurpose pagePurpose,
        ContentType contentType,
        int titleLength,
        int keyPointCount,
        int descriptionLength,
        int bodyCharacterCount,
        int captionCharacterCount,
        int assetCount,
        ImageRole imageRole,
        AssetKind assetKind,
        int assetAspectRatioMillionths,
        int tableRows,
        int tableColumns,
        TableKind tableKind,
        int summaryOrdinal,
        int summaryCount
) {
    public PageLayoutFeatures {
        pageType = Objects.requireNonNull(pageType, "pageType");
        pagePurpose = Objects.requireNonNull(pagePurpose, "pagePurpose");
        contentType = Objects.requireNonNull(contentType, "contentType");
        requireNonNegative("titleLength", titleLength);
        requireNonNegative("keyPointCount", keyPointCount);
        requireNonNegative("descriptionLength", descriptionLength);
        requireNonNegative("bodyCharacterCount", bodyCharacterCount);
        requireNonNegative("captionCharacterCount", captionCharacterCount);
        requireNonNegative("assetCount", assetCount);
        requireNonNegative("assetAspectRatioMillionths", assetAspectRatioMillionths);
        requireNonNegative("tableRows", tableRows);
        requireNonNegative("tableColumns", tableColumns);
        requireNonNegative("summaryOrdinal", summaryOrdinal);
        requireNonNegative("summaryCount", summaryCount);

        if (assetCount == 0 && (imageRole != null || assetKind != null || assetAspectRatioMillionths != 0)) {
            throw new IllegalArgumentException("Zero-asset page must not declare image features");
        }
        if (assetCount > 0 && (imageRole == null || assetKind == null || assetAspectRatioMillionths < 1)) {
            throw new IllegalArgumentException("Asset page requires imageRole, assetKind and positive aspect ratio");
        }
        boolean hasTableShape = tableRows > 0 || tableColumns > 0 || tableKind != null;
        if (hasTableShape && (tableRows < 1 || tableColumns < 1 || tableKind == null)) {
            throw new IllegalArgumentException("Table features require rows, columns and tableKind");
        }
        if (!hasTableShape && tableKind != null) {
            throw new IllegalArgumentException("Non-table features must not declare tableKind");
        }
        if (pageType == PageType.SUMMARY) {
            if (summaryOrdinal < 1 || summaryCount < summaryOrdinal) {
                throw new IllegalArgumentException("SUMMARY page requires a valid sequence ordinal and count");
            }
        } else if (summaryOrdinal != 0 || summaryCount != 0) {
            throw new IllegalArgumentException("Only SUMMARY pages may declare summary sequence features");
        }
    }

    public boolean hasTable() {
        return tableKind != null;
    }

    private static void requireNonNegative(String name, int value) {
        if (value < 0) {
            throw new IllegalArgumentException(name + " must be >= 0");
        }
    }
}
