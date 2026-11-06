package com.dropai.rewrite.service.ppt.rendering.measurement.v1;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public record TableMetricsResult(
        TableFitStatus status,
        int fontSizeHundredthPt,
        String fontFamily,
        String fontFingerprint,
        List<Long> columnWidthsEmu,
        long headerRowHeightEmu,
        long bodyRowHeightEmu,
        long totalHeightEmu,
        List<String> renderedHeaders,
        List<List<String>> renderedRows
) {
    public TableMetricsResult {
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(fontFamily, "fontFamily");
        Objects.requireNonNull(fontFingerprint, "fontFingerprint");
        columnWidthsEmu = List.copyOf(columnWidthsEmu);
        renderedHeaders = List.copyOf(renderedHeaders);
        List<List<String>> copiedRows = new ArrayList<>();
        renderedRows.forEach(row -> copiedRows.add(List.copyOf(row)));
        renderedRows = List.copyOf(copiedRows);
        if (fontSizeHundredthPt <= 0
                || headerRowHeightEmu <= 0
                || bodyRowHeightEmu <= 0
                || totalHeightEmu <= 0) {
            throw new IllegalArgumentException("font size and table dimensions must be positive");
        }
    }
}
