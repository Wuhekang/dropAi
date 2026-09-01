package com.dropai.rewrite.external;

import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Protects evidence-bearing tokens before a paragraph is sent to the platform Skill. */
@Component
public class PlatformDocumentTextProtector {
    private static final Pattern PROTECTED = Pattern.compile(
            "(?i)(\\[\\[DROP_STYLE_PROTECTED_[0-9]+]]|https?://\\S+|`[^`\\r\\n]+`|\\[[0-9０-９,，;；\\-–—~～\\s]+]|"
                    + "(?:图|表|公式)\\s*[0-9０-９]+(?:[.．\\-—][0-9０-９]+)*|"
                    + "(?<![\\p{L}\\p{N}_])[0-9０-９]+(?:[.．][0-9０-９]+)*(?:%|％|ms|s|kg|g|mm|cm|m|KB|MB|GB|℃)?(?![\\p{L}\\p{N}_])|"
                    + "@[A-Za-z_][A-Za-z0-9_]*|"
                    + "(?<![A-Za-z0-9_])[A-Za-z_][A-Za-z0-9_.:/\\-]*(?:\\([^\\r\\n)]*\\))?(?![A-Za-z0-9_]))"
    );

    public ProtectedText protect(String source, AtomicInteger sequence) {
        String text = source == null ? "" : source;
        AtomicInteger ids = sequence == null ? new AtomicInteger() : sequence;
        Map<String, String> segments = new LinkedHashMap<>();
        Matcher matcher = PROTECTED.matcher(text);
        StringBuffer result = new StringBuffer();
        while (matcher.find()) {
            String token = "[[DROP_AI_PROTECTED_" + ids.getAndIncrement() + "]]";
            segments.put(token, matcher.group());
            matcher.appendReplacement(result, Matcher.quoteReplacement(token));
        }
        matcher.appendTail(result);
        return new ProtectedText(result.toString(), segments);
    }

    public record ProtectedText(String text, Map<String, String> segments) {
        public String validateAndRestore(String rewritten) {
            String value = rewritten == null ? "" : rewritten.trim();
            if (value.isBlank()) throw new IllegalStateException("平台 Skill 返回了空段落");
            for (Map.Entry<String, String> entry : segments.entrySet()) {
                if (occurrences(value, entry.getKey()) != 1) {
                    throw new IllegalStateException("平台 Skill 未完整保留结构占位符");
                }
                value = value.replace(entry.getKey(), entry.getValue());
            }
            return value.trim();
        }

        private static int occurrences(String value, String token) {
            int count = 0;
            int cursor = 0;
            while ((cursor = value.indexOf(token, cursor)) >= 0) {
                count++;
                cursor += token.length();
            }
            return count;
        }
    }
}
