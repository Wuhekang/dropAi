package com.dropai.rewrite.service;

import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class TextStructureProtector {

    private static final String STRUCTURED_PROTECTED_BLOCKS =
            "```.*?```|"
                    + "(?:^\\|.*\\|\\R^\\|[\\s:|\\-]+\\|(?:\\R^\\|.*\\|)+)|"
                    + "(?:^\\s*\\[\\d+]\\s+.*$)";
    private static final String URL_AND_INLINE_CODE_BLOCKS = "https?://\\S+|`[^`\\r\\n]+`";
    private static final Pattern PROTECTED_BLOCKS = Pattern.compile(
            "(?ms)(" + STRUCTURED_PROTECTED_BLOCKS + "|" + URL_AND_INLINE_CODE_BLOCKS + ")"
    );
    private static final Pattern NATIVE_HUMANIZE_PROTECTED_BLOCKS = Pattern.compile(
            "(?ms)(" + STRUCTURED_PROTECTED_BLOCKS + "|"
                    + "(?:^[\\t ]*(?:摘[\\t ]*要|Abstract|ABSTRACT)[\\t ]*[:：])|"
                    + "(?:\\[[0-9０-９][0-9０-９,，、\\-–—\\s]*\\])|"
                    + "(?:(?<![0-9０-９])[-+]?[0-9０-９]+(?:[.,．][0-9０-９]+)*(?:[%％])?(?![0-9０-９]))|"
                    + URL_AND_INLINE_CODE_BLOCKS + ")"
    );

    public ProtectedText protect(String text) {
        return protect(text, false);
    }

    public ProtectedText protect(String text, boolean strictNativeHumanizeProtection) {
        String source = text == null ? "" : text;
        Pattern pattern = strictNativeHumanizeProtection ? NATIVE_HUMANIZE_PROTECTED_BLOCKS : PROTECTED_BLOCKS;
        Matcher matcher = pattern.matcher(source);
        Map<String, String> segments = new LinkedHashMap<>();
        StringBuffer protectedText = new StringBuffer();
        int index = 0;
        while (matcher.find()) {
            String token = "[[DROP_AI_PROTECTED_" + index++ + "]]";
            segments.put(token, matcher.group());
            matcher.appendReplacement(protectedText, Matcher.quoteReplacement(token));
        }
        matcher.appendTail(protectedText);
        return new ProtectedText(protectedText.toString(), segments, strictNativeHumanizeProtection);
    }

    public record ProtectedText(String text, Map<String, String> segments, boolean validateIntegrity) {

        public String restore(String rewrittenText) {
            String restored = rewrittenText == null ? "" : rewrittenText;
            if (validateIntegrity) {
                int previousIndex = -1;
                for (String token : segments.keySet()) {
                    int tokenIndex = restored.indexOf(token);
                    if (tokenIndex < 0) {
                        throw new IllegalStateException("模型输出缺少受保护内容占位符：" + token);
                    }
                    if (restored.indexOf(token, tokenIndex + token.length()) >= 0) {
                        throw new IllegalStateException("模型输出重复了受保护内容占位符：" + token);
                    }
                    if (tokenIndex < previousIndex) {
                        throw new IllegalStateException("模型输出改变了受保护内容占位符顺序");
                    }
                    previousIndex = tokenIndex;
                }
            }
            for (Map.Entry<String, String> entry : segments.entrySet()) {
                restored = restored.replace(entry.getKey(), entry.getValue());
            }
            return restored;
        }

        public int protectedCount() {
            return segments.size();
        }
    }
}
