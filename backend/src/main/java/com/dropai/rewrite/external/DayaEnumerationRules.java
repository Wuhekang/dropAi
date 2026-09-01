package com.dropai.rewrite.external;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Deterministic guardrails for the user-selected Daya enumeration profile.
 * The model gets the same rules in its Skill; this class prevents a malformed
 * response from silently restoring the original symmetric list structure.
 */
final class DayaEnumerationRules {
    private static final String HAN_NUMERALS = "一二三四五六七八九十";
    private static final String ORDER_MARKER = "(?:"
            + "第[" + HAN_NUMERALS + "]+\\s*(?:[、，,:：]|阶段|部分|类别?|类|点|步|项|方面|层|种|条|环节|步骤|先)"
            + "|(?:首先|其次|再次|然后|最后)\\s*[、，,:：]?"
            + "|(?:一方面|另一方面)\\s*[、，,:：]?"
            + "|[" + HAN_NUMERALS + "]是\\s*[、，,:：]?"
            + "|[" + HAN_NUMERALS + "]\\s*[、，]"
            + "|其[" + HAN_NUMERALS + "]\\s*[、，,:：]?"
            + "|[（(][" + HAN_NUMERALS + "]+[)）]"
            + "|[①②③④⑤⑥⑦⑧⑨⑩]"
            + ")";
    private static final Pattern MARKER_PATTERN = Pattern.compile(ORDER_MARKER);
    private static final Pattern STARTING_MARKER = Pattern.compile("^\\s*" + ORDER_MARKER);
    private static final Pattern NUMERIC_ITEM_MARKER = Pattern.compile(
            "(^|[。！？!?；;，,:：\\r\\n])\\s*(?:[（(]([0-9０-９]{1,2})[)）]"
                    + "|([0-9０-９]{1,2})(?:[、]|[.．](?![0-9０-９])))\\s*");
    private static final Pattern LEADING_NUMERIC_ITEM = Pattern.compile(
            "^\\s*(?:[（(][0-9０-９]{1,2}[)）]"
                    + "|[0-9０-９]{1,2}(?:[、]|[.．](?![0-9０-９])))\\s*");
    private static final Pattern SENTENCE = Pattern.compile("[^。！？!?；;\\r\\n]+(?:[。！？!?；;]+|$)");

    private DayaEnumerationRules() {
    }

    static boolean requiresBreak(String text) {
        String value = text == null ? "" : text.trim();
        if (value.isEmpty()) return false;
        if (STARTING_MARKER.matcher(value).find()) return true;
        return markerCount(value) >= 2 || numericMarkerCount(value) >= 2;
    }

    static int markerCount(String text) {
        Matcher matcher = MARKER_PATTERN.matcher(text == null ? "" : text);
        int count = 0;
        while (matcher.find()) count++;
        return count;
    }

    static int itemCount(String text) {
        return Math.max(markerCount(text), numericMarkerCount(text));
    }

    static boolean isLeadingListItem(String text) {
        String value = text == null ? "" : text.trim();
        return STARTING_MARKER.matcher(value).find() || LEADING_NUMERIC_ITEM.matcher(value).find();
    }

    static String mergedItemText(int itemIndex, String text) {
        String value = text == null ? "" : text.trim();
        value = STARTING_MARKER.matcher(value).replaceFirst("");
        value = LEADING_NUMERIC_ITEM.matcher(value).replaceFirst("");
        return syntheticMarker(itemIndex) + value.trim();
    }

    static String normalizeInlineNumericEnumeration(String text) {
        String value = text == null ? "" : text;
        if (numericMarkerCount(value) < 2) return value;
        Matcher matcher = NUMERIC_ITEM_MARKER.matcher(value);
        StringBuffer result = new StringBuffer();
        while (matcher.find()) {
            String prefix = matcher.group(1) == null ? "" : matcher.group(1);
            String digits = matcher.group(2) == null ? matcher.group(3) : matcher.group(2);
            int itemIndex = parseDigits(digits);
            matcher.appendReplacement(result, Matcher.quoteReplacement(prefix + syntheticMarker(itemIndex)));
        }
        matcher.appendTail(result);
        return result.toString();
    }

    static void validateRewrite(String original, String rewritten) {
        if (!requiresBreak(original)) return;
        if (markerCount(rewritten) > 0 || numericMarkerCount(rewritten) >= 2) {
            throw new IllegalStateException("大雅列举段仍含第一、第二、第三等文本型顺序标记");
        }
        Matcher sentences = SENTENCE.matcher(rewritten == null ? "" : rewritten.trim());
        int sentenceCount = 0;
        while (sentences.find()) {
            String sentence = sentences.group().trim();
            if (sentence.isEmpty()) continue;
            sentenceCount++;
            if (countHan(sentence) > 20) {
                throw new IllegalStateException("大雅列举段存在超过 20 个汉字的句子");
            }
        }
        if (sentenceCount == 0) throw new IllegalStateException("大雅列举段改写结果为空");
        if (sentenceCount < itemCount(original)) {
            throw new IllegalStateException("大雅列举段未做到每个原编号至少保留一句");
        }
    }

    private static int numericMarkerCount(String text) {
        Matcher matcher = NUMERIC_ITEM_MARKER.matcher(text == null ? "" : text);
        int count = 0;
        while (matcher.find()) count++;
        return count;
    }

    private static String syntheticMarker(int itemIndex) {
        return "第" + toChineseOrdinal(Math.max(1, itemIndex)) + "项，";
    }

    private static int parseDigits(String digits) {
        StringBuilder normalized = new StringBuilder();
        for (int index = 0; digits != null && index < digits.length(); index++) {
            char value = digits.charAt(index);
            normalized.append(value >= '０' && value <= '９' ? (char) ('0' + value - '０') : value);
        }
        try {
            return Integer.parseInt(normalized.toString());
        } catch (NumberFormatException ignored) {
            return 1;
        }
    }

    private static String toChineseOrdinal(int value) {
        String[] digits = {"零", "一", "二", "三", "四", "五", "六", "七", "八", "九"};
        if (value < 10) return digits[value];
        if (value == 10) return "十";
        if (value < 20) return "十" + digits[value % 10];
        if (value < 100) return digits[value / 10] + "十" + (value % 10 == 0 ? "" : digits[value % 10]);
        return Integer.toString(value);
    }

    static int countHan(String text) {
        if (text == null || text.isEmpty()) return 0;
        return (int) text.codePoints()
                .filter(codePoint -> Character.UnicodeScript.of(codePoint)
                        == Character.UnicodeScript.HAN)
                .count();
    }
}
