package com.dropai.rewrite.external;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Deterministic guardrails for the user-selected Daya enumeration profile.
 * The model gets the same rules in its Skill; this class prevents a malformed
 * response from silently restoring the original symmetric list structure.
 */
final class DayaEnumerationRules {
    private static final String HAN_NUMERALS = "一二三四五六七八九十";
    private static final String ORDINAL = "(?:[" + HAN_NUMERALS + "]+|[0-9０-９]{1,2})";
    private static final String FACTUAL_ORDINAL_MARKER =
            "第" + ORDINAL + "\\s*(?:个\\s*)?(?:阶段|层|家|轮|次)";
    private static final String STRONG_ORDER_MARKER = "(?:"
            + "第" + ORDINAL + "\\s*(?:个\\s*)?(?:[、，,:：]|部分|类别?|类|点|步|项|方面|种|条|环节|步骤|要点|要求|要|措施|方案)"
            + "|(?:首先|其次|再次|然后|最后)\\s*[、，,:：]?"
            + "|(?:一方面|另一方面)\\s*[、，,:：]?"
            + "|[" + HAN_NUMERALS + "]是\\s*[、，,:：]?"
            + "|[" + HAN_NUMERALS + "]\\s*[、，]"
            + "|其[" + HAN_NUMERALS + "]\\s*[、，,:：]?"
            + "|[（(][" + HAN_NUMERALS + "]+[)）]"
            + "|[①②③④⑤⑥⑦⑧⑨⑩]"
            + ")";
    private static final String ORDER_MARKER = "(?:" + STRONG_ORDER_MARKER
            + "|" + FACTUAL_ORDINAL_MARKER + ")";
    private static final Pattern MARKER_PATTERN = Pattern.compile(ORDER_MARKER);
    private static final Pattern STRONG_MARKER_PATTERN = Pattern.compile(STRONG_ORDER_MARKER);
    private static final Pattern STARTING_STRONG_MARKER = Pattern.compile("^\\s*" + STRONG_ORDER_MARKER);
    private static final Pattern FACTUAL_ORDINAL_PATTERN = Pattern.compile(
            "第(" + ORDINAL + ")\\s*(?:个\\s*)?(阶段|层|家|轮|次)");
    private static final Pattern STARTING_MARKER = Pattern.compile("^\\s*" + ORDER_MARKER);
    private static final Pattern NUMERIC_ITEM_MARKER = Pattern.compile(
            "(^|[。！？!?；;，,:：\\r\\n])\\s*(?:[（(]([0-9０-９]{1,2})[)）]"
                    + "|([0-9０-９]{1,2})(?:[)）]|[、]|[.．](?![0-9０-９])))\\s*");
    private static final Pattern LEADING_NUMERIC_ITEM = Pattern.compile(
            "^\\s*(?:[（(][0-9０-９]{1,2}[)）]"
                    + "|[0-9０-９]{1,2}(?:[)）]|[、]|[.．](?![0-9０-９])))\\s*");
    private static final Pattern ARABIC_ORDINAL_SHELL = Pattern.compile(
            "第([0-9０-９]{1,2})\\s*(?:个\\s*)?"
                    + "(?:项|点|部分|类别?|类|步|方面|种|条|环节|步骤|要点|要求|措施|方案)"
                    + "\\s*[、，,:：]?");
    private static final Pattern WEAK_ORDER_MARKER = Pattern.compile(
            "(?:(?<![优率首领抢预事原祖])先(?!验|生|前|进|天)"
                    + "|(?<!一)再(?!生|现|次)|随后|之后|接着)");
    private static final Pattern PURE_ORDERING_MARKER_RUN = Pattern.compile(
            "^\\s*(?:" + ORDER_MARKER
                    + "|第" + ORDINAL + "(?:个)?"
                    + "|[（(](?:[" + HAN_NUMERALS + "]+|[0-9０-９]{1,2})[)）]"
                    + "|[0-9０-９]{1,2}(?:[)）]|[、]|[.．])"
                    + "|[①②③④⑤⑥⑦⑧⑨⑩])"
                    + "[\\s、，,:：.．；;()（）]*$");
    private static final Pattern SENTENCE = Pattern.compile("[^。！？!?；;\\r\\n]+(?:[。！？!?；;]+|$)");

    private DayaEnumerationRules() {
    }

    static boolean requiresBreak(String text) {
        String value = text == null ? "" : text.trim();
        if (value.isEmpty()) return false;
        return strongMarkerCount(value) >= 2
                || hasFactualOrdinalSequence(value)
                || numericMarkerCount(value) >= 2
                || hasChineseSemicolon(value);
    }

    static boolean requiresReview(String original, String rewritten) {
        return requiresBreak(original)
                || containsOrderingRisk(original)
                || containsOrderingRisk(rewritten);
    }

    static boolean containsOrderingRisk(String text) {
        String value = text == null ? "" : text.trim();
        if (value.isEmpty()) return false;
        return STRONG_MARKER_PATTERN.matcher(value).find()
                || hasFactualOrdinalSequence(value)
                || numericMarkerCount(value) >= 2
                || weakMarkerCount(value) >= 2
                || hasChineseSemicolon(value);
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

    static boolean isPureOrderingMarkerRun(String text) {
        String value = text == null ? "" : text.trim();
        return !value.isEmpty() && PURE_ORDERING_MARKER_RUN.matcher(value).matches();
    }

    static int leadingEditableMarkerEnd(String text) {
        String value = text == null ? "" : text;
        int end = leadingMarkerEnd(value, STARTING_STRONG_MARKER);
        if (end == 0) end = leadingMarkerEnd(value, LEADING_NUMERIC_ITEM);
        return consumeMarkerPunctuation(value, end);
    }

    static String mergedItemText(int itemIndex, String text) {
        String value = text == null ? "" : text.trim();
        int end = leadingMarkerEnd(value, STARTING_MARKER);
        if (end == 0) end = leadingMarkerEnd(value, LEADING_NUMERIC_ITEM);
        end = consumeMarkerPunctuation(value, end);
        if (end > 0) value = value.substring(end);
        return syntheticMarker(itemIndex) + value.trim();
    }

    private static int leadingMarkerEnd(String text, Pattern pattern) {
        Matcher matcher = pattern.matcher(text == null ? "" : text);
        return matcher.find() ? matcher.end() : 0;
    }

    private static int consumeMarkerPunctuation(String text, int start) {
        int end = start;
        while (end > 0 && end < text.length()) {
            char value = text.charAt(end);
            if (!Character.isWhitespace(value) && "、，,:：.．；;".indexOf(value) < 0) break;
            end++;
        }
        return end;
    }

    static String normalizeInlineNumericEnumeration(String text) {
        String value = normalizeArabicOrdinalShells(text == null ? "" : text);
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

    private static String normalizeArabicOrdinalShells(String text) {
        Matcher matcher = ARABIC_ORDINAL_SHELL.matcher(text);
        StringBuffer result = new StringBuffer();
        while (matcher.find()) {
            matcher.appendReplacement(result, Matcher.quoteReplacement(
                    syntheticMarker(parseDigits(matcher.group(1)))));
        }
        matcher.appendTail(result);
        return result.toString();
    }

    static void validateRewrite(String original, String rewritten) {
        boolean enumeration = requiresBreak(original);
        if (containsOrderingRisk(rewritten)) {
            throw new IllegalStateException("大雅列举段仍含第一、第二、第三等文本型顺序标记");
        }
        if (!enumeration) return;
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
    }

    private static int numericMarkerCount(String text) {
        Matcher matcher = NUMERIC_ITEM_MARKER.matcher(text == null ? "" : text);
        int count = 0;
        while (matcher.find()) count++;
        return count;
    }

    private static int strongMarkerCount(String text) {
        Matcher matcher = STRONG_MARKER_PATTERN.matcher(text == null ? "" : text);
        int count = 0;
        while (matcher.find()) count++;
        return count;
    }

    private static boolean hasFactualOrdinalSequence(String text) {
        Matcher matcher = FACTUAL_ORDINAL_PATTERN.matcher(text == null ? "" : text);
        Map<String, String> ordinalBySuffix = new HashMap<>();
        while (matcher.find()) {
            String ordinal = matcher.group(1);
            String suffix = matcher.group(2);
            String firstOrdinal = ordinalBySuffix.putIfAbsent(suffix, ordinal);
            if (firstOrdinal != null && !firstOrdinal.equals(ordinal)) return true;
        }
        return false;
    }

    private static int weakMarkerCount(String text) {
        Matcher matcher = WEAK_ORDER_MARKER.matcher(text == null ? "" : text);
        int count = 0;
        while (matcher.find()) count++;
        return count;
    }

    private static boolean hasChineseSemicolon(String text) {
        return countHan(text) > 0 && (text.indexOf('；') >= 0 || text.indexOf(';') >= 0);
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
