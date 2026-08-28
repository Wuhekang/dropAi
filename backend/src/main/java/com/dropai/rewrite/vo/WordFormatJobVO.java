package com.dropai.rewrite.vo;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public record WordFormatJobVO(
        String id,
        String status,
        int progress,
        String stage,
        String message,
        String sourceName,
        String templateName,
        String outputName,
        boolean useDoubao,
        int changedCount,
        List<String> warnings,
        List<String> templateNotes,
        Map<String, Object> result,
        String downloadUrl,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
