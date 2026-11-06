package com.dropai.rewrite.service.ppt;

import java.util.List;

/** Detects opening-proposal documents without interpreting or rewriting their content. */
final class PptOpeningProposalDetector {
    private static final List<String> STRONG_MARKERS = List.of(
            "开题报告", "开题答辩", "开题汇报", "课题开题");
    private static final List<String> CONTENT_MARKERS = List.of(
            "选题目的及意义", "选题依据", "研究背景及意义", "国内外研究现状",
            "研究内容", "研究方法", "技术路线", "研究可行性", "可行性分析",
            "进度安排", "工作进度安排", "预期成果", "拟解决的关键问题");

    private PptOpeningProposalDetector() {}

    static boolean isOpeningProposal(List<String> blocks) {
        return isOpeningProposal(blocks == null ? "" : String.join(" ", blocks));
    }

    static boolean isOpeningProposal(String text) {
        String compact = PptDocumentParser.clean(text).replaceAll("\\s+", "");
        if (compact.isBlank()) return false;
        if (STRONG_MARKERS.stream().anyMatch(compact::contains)) return true;
        long markerHits = CONTENT_MARKERS.stream().filter(compact::contains).count();
        boolean hasPlan = compact.contains("进度") || compact.contains("计划");
        boolean hasMethod = compact.contains("研究方法") || compact.contains("技术路线");
        return markerHits >= 4 && hasPlan && hasMethod;
    }
}
