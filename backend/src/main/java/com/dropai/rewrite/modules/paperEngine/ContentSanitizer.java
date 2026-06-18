package com.dropai.rewrite.modules.paperEngine;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

@Component
public class ContentSanitizer {
    private static final List<Pattern> INSTRUCTION_PATTERNS = List.of(
            Pattern.compile("第\\s*\\d+\\s*段\\s*说明"),
            Pattern.compile("该段落需要围绕"),
            Pattern.compile("进一步结合项目参数"),
            Pattern.compile("进一步展开"),
            Pattern.compile("定稿时可根据"),
            Pattern.compile("请扩写"),
            Pattern.compile("参考以下内容"),
            Pattern.compile("生成说明"),
            Pattern.compile("模板说明"),
            Pattern.compile("Prompt内容", Pattern.CASE_INSENSITIVE),
            Pattern.compile("prompt内容", Pattern.CASE_INSENSITIVE),
            Pattern.compile("以下是"),
            Pattern.compile("作为AI")
    );

    private static final Pattern MOJIBAKE_PATTERN = Pattern.compile("[姣璁鎴鍥涓鏂灏烘灦妯熸湁]{4,}");

    public String sanitize(String raw) {
        if (raw == null) {
            return "";
        }
        String normalized = raw
                .replace('\u00A0', ' ')
                .replaceAll("[\\t ]+", " ")
                .replaceAll("\\s*\\n\\s*", "\n")
                .trim();
        if (normalized.isBlank()) {
            return "";
        }
        List<String> kept = new ArrayList<>();
        for (String sentence : splitSentences(normalized)) {
            String item = sentence.trim();
            if (item.isBlank()) {
                continue;
            }
            if (containsInstruction(item)) {
                continue;
            }
            kept.add(item);
        }
        return String.join("", kept)
                .replaceAll("\\s+", " ")
                .trim();
    }

    public boolean containsInstruction(String text) {
        if (text == null || text.isBlank()) {
            return false;
        }
        return INSTRUCTION_PATTERNS.stream().anyMatch(pattern -> pattern.matcher(text).find());
    }

    public boolean containsMojibake(String text) {
        return text != null && MOJIBAKE_PATTERN.matcher(text).find();
    }

    private List<String> splitSentences(String text) {
        List<String> result = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        for (int i = 0; i < text.length(); i++) {
            char ch = text.charAt(i);
            current.append(ch);
            if ("。！？；.!?;".indexOf(ch) >= 0) {
                result.add(current.toString());
                current.setLength(0);
            }
        }
        if (!current.isEmpty()) {
            result.add(current.toString());
        }
        return result;
    }
}
