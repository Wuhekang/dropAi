package com.dropai.rewrite.service.ppt.rendering.measurement.v1;

import com.dropai.rewrite.service.ppt.rendering.contract.v1.enums.TableKind;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public record TableMetricsRequest(
        TableKind tableKind,
        List<String> headers,
        List<List<String>> rows,
        ResolvedFontProfile fontProfile,
        String fontRole,
        int headerWeight,
        int bodyWeight,
        int defaultFontSizeHundredthPt,
        int minimumFontSizeHundredthPt,
        int lineSpacingPermille,
        long tableWidthEmu,
        long maximumTableHeightEmu,
        long horizontalCellPaddingEmu,
        long verticalCellPaddingEmu,
        long minimumColumnWidthEmu,
        int headerMaxLines,
        int bodyMaxLines
) {
    public TableMetricsRequest {
        Objects.requireNonNull(tableKind, "tableKind");
        headers = List.copyOf(headers);
        Objects.requireNonNull(rows, "rows");
        List<List<String>> rowCopy = new ArrayList<>();
        for (List<String> row : rows) {
            rowCopy.add(List.copyOf(row));
        }
        rows = List.copyOf(rowCopy);
        Objects.requireNonNull(fontProfile, "fontProfile");
        Objects.requireNonNull(fontRole, "fontRole");
        if (headers.isEmpty() || headers.size() > 5) {
            throw new IllegalArgumentException("headers must contain from 1 to 5 columns");
        }
        if (headers.stream().anyMatch(value -> value == null || value.isBlank())) {
            throw new IllegalArgumentException("table headers must not be blank");
        }
        if (rows.isEmpty() || rows.size() > 7) {
            throw new IllegalArgumentException("rows must contain from 1 to 7 rows");
        }
        int columnCount = headers.size();
        if (rows.stream().anyMatch(row -> row.size() != columnCount)) {
            throw new IllegalArgumentException("every row must match the header column count");
        }
        if (rows.stream().flatMap(List::stream).anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("table cells must not be null");
        }
        if (fontRole.isBlank()) {
            throw new IllegalArgumentException("fontRole must not be blank");
        }
        requireWeight(headerWeight, "headerWeight");
        requireWeight(bodyWeight, "bodyWeight");
        if (defaultFontSizeHundredthPt <= 0
                || minimumFontSizeHundredthPt <= 0
                || defaultFontSizeHundredthPt < minimumFontSizeHundredthPt) {
            throw new IllegalArgumentException("font sizes must be positive and default must be at least minimum");
        }
        if (lineSpacingPermille < 1_000) {
            throw new IllegalArgumentException("lineSpacingPermille must be at least 1000");
        }
        if (tableWidthEmu <= 0 || maximumTableHeightEmu <= 0 || minimumColumnWidthEmu <= 0) {
            throw new IllegalArgumentException("table dimensions must be positive");
        }
        if (horizontalCellPaddingEmu < 0 || verticalCellPaddingEmu < 0) {
            throw new IllegalArgumentException("cell padding must not be negative");
        }
        if (headerMaxLines <= 0 || bodyMaxLines <= 0) {
            throw new IllegalArgumentException("cell line limits must be positive");
        }
    }

    private static void requireWeight(int weight, String name) {
        if (weight < 100 || weight > 900 || weight % 100 != 0) {
            throw new IllegalArgumentException(name + " must be from 100 to 900 in increments of 100");
        }
    }
}
