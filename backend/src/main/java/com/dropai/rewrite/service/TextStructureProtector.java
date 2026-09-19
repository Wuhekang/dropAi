package com.dropai.rewrite.service;

import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class TextStructureProtector {

    private static final Pattern UNRESOLVED_PLACEHOLDER = Pattern.compile(
            "\\[\\[DROP_(?:AI|STYLE)_PROTECTED_[0-9]+]]"
    );

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
    private static final Pattern REWRITE_PROTECTED_BLOCKS = Pattern.compile(
            "(?ms)(" + STRUCTURED_PROTECTED_BLOCKS + "|"
                    + "(?:\\[[0-9０-９][0-9０-９,，、\\-–—\\s]*\\])|"
                    + "(?:(?:图|表|公式)\\s*[0-9０-９]+(?:[.．\\-—][0-9０-９]+)*)|"
                    + "(?:(?<![0-9０-９])[-+]?[0-9０-９]+(?:[.,．][0-9０-９]+)*"
                    + "(?:[%％]|ms|s|kg|g|mm|cm|m|kW|KW|V|Hz|HZ|N|℃)?(?![\\p{L}\\p{N}_]))|"
                    + "(?<![A-Za-z0-9_])(?=[A-Za-z0-9_.:/\\-]*[A-Za-z])"
                    + "(?=[A-Za-z0-9_.:/\\-]*[0-9])[A-Za-z0-9_][A-Za-z0-9_.:/\\-]*(?![A-Za-z0-9_])|"
                    + "(?<![A-Za-z0-9_])[A-Z]{2,}(?:[0-9]+)?(?![A-Za-z0-9_])|"
                    + "(?<![A-Za-z0-9_])[A-Za-z](?![A-Za-z0-9_])|"
                    + URL_AND_INLINE_CODE_BLOCKS + ")"
    );

    public ProtectedText protect(String text) {
        return protect(text, false);
    }

    public ProtectedText protect(String text, boolean strictNativeHumanizeProtection) {
        String source = text == null ? "" : text;
        rejectLeakedPlaceholder(source);
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

    /** Protects immutable evidence for plagiarism rewriting without changing humanize protection. */
    public ProtectedText protectForRewrite(String text) {
        String source = text == null ? "" : text;
        rejectLeakedPlaceholder(source);
        Matcher matcher = REWRITE_PROTECTED_BLOCKS.matcher(source);
        Map<String, String> segments = new LinkedHashMap<>();
        StringBuffer protectedText = new StringBuffer();
        int index = 0;
        while (matcher.find()) {
            String token = "[[DROP_AI_PROTECTED_" + index++ + "]]";
            segments.put(token, matcher.group());
            matcher.appendReplacement(protectedText, Matcher.quoteReplacement(token));
        }
        matcher.appendTail(protectedText);
        return new ProtectedText(protectedText.toString(), segments, true);
    }

    public static boolean containsUnresolvedPlaceholder(String text) {
        return text != null && UNRESOLVED_PLACEHOLDER.matcher(text).find();
    }

    private static void rejectLeakedPlaceholder(String source) {
        if (containsUnresolvedPlaceholder(source)) {
            throw new ProtectedContentIntegrityException("输入正文包含未还原的保护占位符");
        }
    }

    public record ProtectedText(String text, Map<String, String> segments, boolean validateIntegrity) {

        public String restore(String rewrittenText) {
            String restored = rewrittenText == null ? "" : rewrittenText;
            if (validateIntegrity) {
                int previousIndex = -1;
                for (String token : segments.keySet()) {
                    int tokenIndex = restored.indexOf(token);
                    if (tokenIndex < 0) {
                        throw new ProtectedContentIntegrityException("模型输出缺少受保护内容占位符：" + token);
                    }
                    if (restored.indexOf(token, tokenIndex + token.length()) >= 0) {
                        throw new ProtectedContentIntegrityException("模型输出重复了受保护内容占位符：" + token);
                    }
                    if (tokenIndex < previousIndex) {
                        throw new ProtectedContentIntegrityException("模型输出改变了受保护内容占位符顺序");
                    }
                    previousIndex = tokenIndex;
                }
            }
            for (Map.Entry<String, String> entry : segments.entrySet()) {
                restored = restored.replace(entry.getKey(), entry.getValue());
            }
            if (containsUnresolvedPlaceholder(restored)) {
                throw new ProtectedContentIntegrityException("模型输出仍包含未还原的保护占位符");
            }
            return restored;
        }

        public int protectedCount() {
            return segments.size();
        }
    }

    public static class ProtectedContentIntegrityException extends IllegalStateException {

        public ProtectedContentIntegrityException(String message) {
            super(message);
        }
    }
}
