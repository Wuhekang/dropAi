package com.dropai.rewrite.modules.paperEngine;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Component
public class DocQualityChecker {
    private static final int MIN正文字数 = 8000;
    private final ContentSanitizer sanitizer;

    public DocQualityChecker(ContentSanitizer sanitizer) {
        this.sanitizer = sanitizer;
    }

    public QualityReport checkPaper(String text) {
        String normalized = text == null ? "" : text.replaceAll("\\s+", "");
        List<String> errors = new ArrayList<>();
        for (String chapter : requiredChapters()) {
            if (!normalized.contains(chapter.replaceAll("\\s+", ""))) {
                errors.add("缺少章节：" + chapter);
            }
        }
        if (sanitizer.containsInstruction(text)) {
            errors.add("论文正文存在生成提示词残留");
        }
        if (sanitizer.containsMojibake(text)) {
            errors.add("论文正文存在疑似中文乱码");
        }
        List<String> placeholders = List.of("XXX", "xxx", "待填写", "待补充", "{{", "}}", "[项目名称]", "[设备名称]");
        for (String placeholder : placeholders) {
            if (text != null && text.contains(placeholder)) {
                errors.add("存在占位符：" + placeholder);
            }
        }
        if (normalized.length() < MIN正文字数) {
            errors.add("论文正文低于最低完整度要求：" + normalized.length() + "/" + MIN正文字数);
        }
        double duplicateRate = duplicateRate(text);
        if (duplicateRate > 0.38) {
            errors.add("段落重复率过高：" + String.format("%.2f", duplicateRate));
        }
        return new QualityReport(errors.isEmpty(), errors, duplicateRate, normalized.length());
    }

    private List<String> requiredChapters() {
        return List.of(
                "摘要",
                "关键词",
                "第1章 绪论",
                "第2章 总体方案设计",
                "第3章 结构设计与计算",
                "第4章 标准件选型",
                "第5章 CAD与建模说明",
                "第6章 总结",
                "参考文献",
                "致谢"
        );
    }

    private double duplicateRate(String text) {
        if (text == null || text.isBlank()) {
            return 1.0;
        }
        String[] paragraphs = text.split("\\R+");
        int meaningful = 0;
        int duplicates = 0;
        Set<String> seen = new HashSet<>();
        for (String paragraph : paragraphs) {
            String item = paragraph.replaceAll("\\s+", "");
            if (item.length() < 30) {
                continue;
            }
            meaningful++;
            if (!seen.add(item)) {
                duplicates++;
            }
        }
        if (meaningful == 0) {
            return 1.0;
        }
        return duplicates * 1.0 / meaningful;
    }

    public record QualityReport(boolean passed, List<String> errors, double duplicateRate, int charCount) {
    }
}
