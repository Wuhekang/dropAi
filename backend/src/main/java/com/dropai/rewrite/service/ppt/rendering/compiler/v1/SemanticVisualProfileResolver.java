package com.dropai.rewrite.service.ppt.rendering.compiler.v1;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.Locale;

/**
 * Maps validated page semantics to a deterministic, theme-token-based visual accent.
 * It never changes content, page order, layout selection or asset binding.
 */
final class SemanticVisualProfileResolver {
    VisualProfile resolve(JsonNode page) {
        String text = searchableText(page);
        String purpose = page.path("pagePurpose").asText("").toUpperCase(Locale.ROOT);

        if (containsAny(text, "对比", "比较", "差异", "国内外", "优劣", "竞品")) {
            return new VisualProfile("COMPARISON", "colors.accent.primary",
                    "colors.accent.secondary", "ELLIPSE");
        }
        if (containsAny(text, "问题", "不足", "风险", "挑战", "痛点", "障碍", "困境")
                || "PROBLEM".equals(purpose)) {
            return new VisualProfile("RISK", "colors.state.danger",
                    "colors.state.warning", "RECTANGLE");
        }
        if (containsAny(text, "结果", "成果", "测试", "验证", "效果", "成效", "证明")
                || containsAny(purpose, "RESULT", "TEST")) {
            return new VisualProfile("EVIDENCE", "colors.state.success",
                    "colors.accent.primary", "ELLIPSE");
        }
        if (containsAny(text, "展望", "计划", "进度", "未来", "预期", "安排", "里程碑")
                || "SUMMARY".equals(purpose)) {
            return new VisualProfile("FUTURE", "colors.state.warning",
                    "colors.accent.secondary", "ROUNDED_RECTANGLE");
        }
        if (containsAny(text, "方法", "路径", "流程", "步骤", "技术", "设计", "架构", "模型", "机制")
                || containsAny(purpose, "METHOD", "DESIGN", "IMPLEMENTATION")) {
            return new VisualProfile("METHOD", "colors.state.info",
                    "colors.accent.secondary", "ROUNDED_RECTANGLE");
        }
        if (containsAny(text, "背景", "意义", "依据", "需求", "价值", "现状")
                || containsAny(purpose, "BACKGROUND", "CONTEXT")) {
            return new VisualProfile("CONTEXT", "colors.accent.secondary",
                    "colors.accent.primary", "ELLIPSE");
        }

        int bucket = Math.floorMod(text.hashCode(), 4);
        return switch (bucket) {
            case 0 -> new VisualProfile("GENERAL_PRIMARY", "colors.accent.primary",
                    "colors.accent.secondary", "ROUNDED_RECTANGLE");
            case 1 -> new VisualProfile("GENERAL_INFO", "colors.state.info",
                    "colors.accent.secondary", "RECTANGLE");
            case 2 -> new VisualProfile("GENERAL_SUCCESS", "colors.state.success",
                    "colors.accent.primary", "ELLIPSE");
            default -> new VisualProfile("GENERAL_SECONDARY", "colors.accent.secondary",
                    "colors.accent.primary", "ROUNDED_RECTANGLE");
        };
    }

    private String searchableText(JsonNode page) {
        StringBuilder text = new StringBuilder();
        append(text, page.path("title").asText(""));
        append(text, page.path("description").asText(""));
        JsonNode keyPoints = page.path("keyPoints");
        if (keyPoints.isArray()) {
            keyPoints.forEach(value -> append(text, value.asText("")));
        }
        return text.toString().toLowerCase(Locale.ROOT);
    }

    private void append(StringBuilder target, String value) {
        if (value != null && !value.isBlank()) {
            if (!target.isEmpty()) target.append('|');
            target.append(value.strip());
        }
    }

    private boolean containsAny(String value, String... tokens) {
        for (String token : tokens) {
            if (value.contains(token.toLowerCase(Locale.ROOT))) return true;
        }
        return false;
    }

    record VisualProfile(
            String profileId,
            String primaryColorToken,
            String secondaryColorToken,
            String markerShape
    ) {}
}
