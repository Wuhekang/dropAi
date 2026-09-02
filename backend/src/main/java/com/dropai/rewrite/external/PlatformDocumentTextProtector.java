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
                    + "(?<![A-Za-z0-9_])[0-9０-９]+(?:[.．][0-9０-９]+)*(?:%|％|ms|s|kg|g|mm|cm|m|KB|MB|GB|℃)?(?![A-Za-z0-9_])|"
                    + "@[A-Za-z_][A-Za-z0-9_]*|"
                    + "(?<![A-Za-z0-9_])[A-Za-z_][A-Za-z0-9_.:/\\-]*(?:\\([^\\r\\n)]*\\))?(?![A-Za-z0-9_]))"
    );
    /**
     * Daya's English prose profile must leave ordinary words visible to the model. Unlike
     * {@link #PROTECTED}, this pattern is intentionally not globally case-insensitive: a global
     * {@code (?i)} would make the all-capital abbreviation branch match every English word.
     */
    private static final Pattern DAYA_ENGLISH_PROTECTED = Pattern.compile(
            "(\\[\\[DROP_STYLE_PROTECTED_[0-9]+]]|(?i:https?://\\S+)|`[^`\\r\\n]+`|"
                    + "\\[[0-9０-９,，;；\\-–—~～\\s]+]|"
                    + "(?:图|表|公式)\\s*[0-9０-９]+(?:[.．\\-—][0-9０-９]+)*|"
                    + "(?i:\\b(?:zero|one|two|three|four|five|six|seven|eight|nine|ten|"
                    + "eleven|twelve|thirteen|fourteen|fifteen|sixteen|seventeen|eighteen|"
                    + "nineteen|twenty|thirty|forty|fifty|sixty|seventy|eighty|ninety|"
                    + "hundred|thousand|million|billion|first|second|third|fourth|fifth|"
                    + "sixth|seventh|eighth|ninth|tenth|eleventh|twelfth|thirteenth|"
                    + "fourteenth|fifteenth|sixteenth|seventeenth|eighteenth|nineteenth|"
                    + "twentieth|thirtieth|fortieth|fiftieth|sixtieth|seventieth|"
                    + "eightieth|ninetieth|hundredth|thousandth|millionth|billionth)\\b)|"
                    + "(?<![A-Za-z0-9_])(?:[pPnN]\\s*[<>=≤≥]\\s*[0-9０-９]+"
                    + "(?:[.．][0-9０-９]+)?|[rR](?:²|2)?\\s*[<>=≤≥]\\s*"
                    + "[0-9０-９]+(?:[.．][0-9０-９]+)?|[rR]²)(?![A-Za-z0-9_])|"
                    + "(?<![A-Za-z0-9_])[0-9０-９]+(?:[.．][0-9０-９]+)*"
                    + "(?:(?i:ms|kg|mm|cm|kb|mb|gb|s|g|m)|%|％|℃)?"
                    + "(?![A-Za-z0-9_])|"
                    + "@[A-Za-z_][A-Za-z0-9_]*|"
                    + "(?<![A-Za-z0-9_])(?:"
                    + "[A-Z]{2,}(?:[0-9]+)?(?:[/._\\-][A-Z0-9]+)*|"
                    + "[A-Za-z_][A-Za-z0-9_]*(?:\\.[A-Za-z_][A-Za-z0-9_]*)+"
                    + "(?:\\([^\\r\\n)]*\\))?|"
                    + "[A-Za-z_][A-Za-z0-9_]*\\([^\\r\\n)]*\\)|"
                    + "[A-Za-z0-9_]*[a-z][A-Z][A-Za-z0-9_]*|"
                    + "(?=[A-Za-z0-9_.:/\\-]*[A-Za-z])"
                    + "(?=[A-Za-z0-9_.:/\\-]*[0-9])"
                    + "[A-Za-z0-9_][A-Za-z0-9_.:/\\-]*"
                    + ")(?![A-Za-z0-9_]))"
    );

    public ProtectedText protect(String source, AtomicInteger sequence) {
        return protect(source, sequence, PROTECTED);
    }

    /**
     * Protects immutable evidence in Daya English natural-language paragraphs while allowing
     * ordinary English words and ordinary hyphenated prose to be rewritten.
     */
    public ProtectedText protectDayaEnglishProse(String source, AtomicInteger sequence) {
        return protect(source, sequence, DAYA_ENGLISH_PROTECTED);
    }

    private ProtectedText protect(String source, AtomicInteger sequence, Pattern protectedPattern) {
        String text = source == null ? "" : source;
        AtomicInteger ids = sequence == null ? new AtomicInteger() : sequence;
        Map<String, String> segments = new LinkedHashMap<>();
        Matcher matcher = protectedPattern.matcher(text);
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
            int orderedCursor = 0;
            for (Map.Entry<String, String> entry : segments.entrySet()) {
                if (occurrences(value, entry.getKey()) != 1) {
                    throw new IllegalStateException("平台 Skill 未完整保留结构占位符");
                }
                int tokenIndex = value.indexOf(entry.getKey());
                if (tokenIndex < orderedCursor) {
                    throw new IllegalStateException("平台 Skill 调换了结构占位符顺序");
                }
                orderedCursor = tokenIndex + entry.getKey().length();
            }
            for (Map.Entry<String, String> entry : segments.entrySet()) {
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
