package com.dropai.rewrite.service.impl;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Deterministic acceptance rules for plagiarism-oriented rewriting.
 *
 * <p>This gate is deliberately independent from the humanize path. Evidence-bearing tokens must
 * keep both their values and original order. Lexical overlap in editable text is diagnostic only
 * and never rejects an otherwise valid candidate.</p>
 */
final class RewriteQualityGate {
    private static final int DIAGNOSTIC_SHARED_RUN_CAP = 19;
    private static final Pattern IMMUTABLE_TOKEN = Pattern.compile(
            "(?i)(https?://\\S+|`[^`\\r\\n]+`|\\[[0-9０-９,，、;；\\-–—~～\\s]+]|"
                    + "(?:图|表|公式)\\s*[0-9０-９]+(?:[.．\\-—][0-9０-９]+)*|"
                    + "(?<![A-Za-z0-9_])(?:kN/m[²³]|[A-Za-z]{1,3}[²³]|㎡)(?![A-Za-z0-9_])|"
                    + "(?<![0-9０-９])[-+]?[0-9０-９]+(?:[.,．][0-9０-９]+)*"
                    + "(?:%|％|ms|s|kg|g|mm|cm|m|kw|v|hz|n|℃)?(?![\\p{L}\\p{N}_])|"
                    + "(?<![A-Za-z0-9_])(?=[A-Za-z0-9_.:/\\-]*[A-Za-z])"
                    + "(?=[A-Za-z0-9_.:/\\-]*[0-9])[A-Za-z0-9_][A-Za-z0-9_.:/\\-]*(?![A-Za-z0-9_])|"
                    + "(?<![A-Za-z0-9_])[A-Z]{2,}(?:[0-9]+)?(?![A-Za-z0-9_])|"
                    + "(?<![A-Za-z0-9_])[A-Za-z](?![A-Za-z0-9_]))"
    );
    private static final Pattern TECHNICAL_SIGNAL = Pattern.compile(
            "(?i)(公式|式中|计算|参数|磁路|转矩|转速|功率|电流|电压|效率|仿真|槽数|轴承|齿轮|"
                    + "[=＋+\\-*/×÷≤≥]|[0-9０-９]|[A-Za-z])"
    );

    private RewriteQualityGate() {
    }

    static Assessment assess(String original, String candidate) {
        String source = original == null ? "" : original.trim();
        String rewritten = candidate == null ? "" : candidate.trim();
        List<String> issues = new ArrayList<>();
        if (rewritten.isBlank()) {
            return new Assessment(false, List.of("模型未返回有效正文"), 1.0, 0, false);
        }
        if (!immutableTokens(source).equals(immutableTokens(rewritten))) {
            issues.add("数字、单位、型号、变量、引用或图表公式编号发生变化或顺序改变");
        }

        String sourceLetters = editableLetters(source);
        String rewrittenLetters = editableLetters(rewritten);
        boolean technical = isTechnical(source);
        return new Assessment(issues.isEmpty(), List.copyOf(issues),
                fourGramJaccard(sourceLetters, rewrittenLetters),
                longestSharedRun(sourceLetters, rewrittenLetters), technical);
    }

    static String feedback(Assessment assessment) {
        return "上一版未通过受保护数据验收：" + String.join("；", assessment.issues())
                + "。数字、单位、型号、引用和图表公式编号必须原值原顺序保留；"
                + "可编辑文字可按原文事实改写，也允许保持原样。";
    }

    private static boolean isTechnical(String text) {
        if (text == null || text.isBlank()) return false;
        long signals = TECHNICAL_SIGNAL.matcher(text).results().count();
        return signals >= 3 || signals * 12 >= text.length();
    }

    private static String editableLetters(String text) {
        String masked = IMMUTABLE_TOKEN.matcher(text == null ? "" : text).replaceAll("");
        StringBuilder result = new StringBuilder(masked.length());
        masked.codePoints()
                .filter(Character::isLetter)
                .forEach(result::appendCodePoint);
        return result.toString();
    }

    static List<String> immutableTokens(String text) {
        List<String> tokens = new ArrayList<>();
        Matcher matcher = IMMUTABLE_TOKEN.matcher(text == null ? "" : text);
        while (matcher.find()) tokens.add(matcher.group());
        return tokens;
    }

    static Map<String, Integer> immutableTokenCounts(String text) {
        Map<String, Integer> counts = new HashMap<>();
        Matcher matcher = IMMUTABLE_TOKEN.matcher(text == null ? "" : text);
        while (matcher.find()) {
            counts.merge(matcher.group(), 1, Integer::sum);
        }
        return counts;
    }

    private static int longestSharedRun(String left, String right) {
        if (left.isEmpty() || right.isEmpty()) return 0;
        String shorter = left.length() <= right.length() ? left : right;
        String longer = left.length() <= right.length() ? right : left;
        int maximum = Math.min(shorter.length(), DIAGNOSTIC_SHARED_RUN_CAP);
        for (int size = maximum; size >= 1; size--) {
            Set<String> windows = ngrams(shorter, size);
            for (int index = 0; index + size <= longer.length(); index++) {
                if (windows.contains(longer.substring(index, index + size))) return size;
            }
        }
        return 0;
    }

    private static double fourGramJaccard(String left, String right) {
        Set<String> a = ngrams(left, 4);
        Set<String> b = ngrams(right, 4);
        if (a.isEmpty() && b.isEmpty()) return left.equals(right) ? 1.0 : 0.0;
        Set<String> intersection = new HashSet<>(a);
        intersection.retainAll(b);
        Set<String> union = new HashSet<>(a);
        union.addAll(b);
        return union.isEmpty() ? 0.0 : intersection.size() / (double) union.size();
    }

    private static Set<String> ngrams(String text, int size) {
        Set<String> grams = new HashSet<>();
        if (text == null || text.length() < size) return grams;
        for (int index = 0; index + size <= text.length(); index++) {
            grams.add(text.substring(index, index + size));
        }
        return grams;
    }

    record Assessment(boolean accepted, List<String> issues, double fourGramRatio,
                      int longestSharedRun, boolean technical) {
    }
}
