package com.dropai.rewrite.service;

import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class TextStructureProtector {

    private static final Pattern PROTECTED_BLOCKS = Pattern.compile(
            "(?ms)(```.*?```|(?:^\\|.*\\|\\R^\\|[\\s:|\\-]+\\|(?:\\R^\\|.*\\|)+)|"
                    + "(?:^\\s*\\[\\d+]\\s+.*$)|https?://\\S+|`[^`\\r\\n]+`)"
    );

    public ProtectedText protect(String text) {
        String source = text == null ? "" : text;
        Matcher matcher = PROTECTED_BLOCKS.matcher(source);
        Map<String, String> segments = new LinkedHashMap<>();
        StringBuffer protectedText = new StringBuffer();
        int index = 0;
        while (matcher.find()) {
            String token = "[[DROP_AI_PROTECTED_" + index++ + "]]";
            segments.put(token, matcher.group());
            matcher.appendReplacement(protectedText, Matcher.quoteReplacement(token));
        }
        matcher.appendTail(protectedText);
        return new ProtectedText(protectedText.toString(), segments);
    }

    public record ProtectedText(String text, Map<String, String> segments) {

        public String restore(String rewrittenText) {
            String restored = rewrittenText == null ? "" : rewrittenText;
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
