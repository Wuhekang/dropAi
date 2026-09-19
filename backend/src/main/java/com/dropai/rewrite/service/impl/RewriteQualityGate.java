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
 * Deterministic lexical and structural acceptance rules for plagiarism-oriented rewriting.
 *
 * <p>This gate is deliberately independent from the humanize path. It compares only editable
 * natural-language content after masking evidence-bearing tokens, so model numbers, values,
 * citations and other immutable facts do not make an otherwise valid rewrite fail.</p>
 */
final class RewriteQualityGate {
    private static final int ORDINARY_MAX_SHARED_RUN = 12;
    private static final int TECHNICAL_MAX_SHARED_RUN = 18;
    private static final double ORDINARY_MAX_FOUR_GRAM = 0.30;
    private static final double TECHNICAL_MAX_FOUR_GRAM = 0.45;
    private static final Pattern IMMUTABLE_TOKEN = Pattern.compile(
            "(?i)(https?://\\S+|`[^`\\r\\n]+`|\\[[0-9０-９,，、;；\\-–—~～\\s]+]|"
                    + "(?:图|表|公式)\\s*[0-9０-９]+(?:[.．\\-—][0-9０-９]+)*|"
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
        if (!immutableTokenCounts(source).equals(immutableTokenCounts(rewritten))) {
            issues.add("数字、单位、型号、变量、引用或图表公式编号发生变化");
        }

        String sourceHan = editableHan(source);
        String rewrittenHan = editableHan(rewritten);
        boolean technical = isTechnical(source);
        int editableLength = sourceHan.length();
        if (editableLength < 20) {
            if (source.equals(rewritten)) {
                issues.add("可改正文与原文完全相同");
            }
            return new Assessment(issues.isEmpty(), List.copyOf(issues),
                    fourGramJaccard(sourceHan, rewrittenHan),
                    longestSharedRunUpTo(sourceHan, rewrittenHan, TECHNICAL_MAX_SHARED_RUN + 1), technical);
        }

        double lengthRatio = rewrittenHan.length() / (double) Math.max(1, editableLength);
        if (lengthRatio < 0.60) issues.add("改写后可读正文不足原文的60%");
        if (lengthRatio > 1.30) issues.add("改写后可读正文超过原文的130%");
        if (sourceHan.equals(rewrittenHan) && editableLength >= 40) {
            issues.add("长段落只改变了标点、数字或格式");
        }

        int runLimit = technical ? TECHNICAL_MAX_SHARED_RUN : ORDINARY_MAX_SHARED_RUN;
        int sharedRun = longestSharedRunUpTo(sourceHan, rewrittenHan, runLimit + 1);
        if (sharedRun > runLimit) {
            issues.add("仍保留超过" + runLimit + "个连续相同汉字");
        }

        double fourGram = fourGramJaccard(sourceHan, rewrittenHan);
        double gramLimit = technical ? TECHNICAL_MAX_FOUR_GRAM : ORDINARY_MAX_FOUR_GRAM;
        if (editableLength >= 40 && fourGram > gramLimit) {
            issues.add("四字片段重合率过高（" + percent(fourGram) + "）");
        }

        if (editableLength >= 80) {
            int structuralChanges = 0;
            if (!edge(sourceHan, true).equals(edge(rewrittenHan, true))) structuralChanges++;
            if (!edge(sourceHan, false).equals(edge(rewrittenHan, false))) structuralChanges++;
            if (sentenceCount(source) != sentenceCount(rewritten)) structuralChanges++;
            if (structuralChanges < 2) {
                issues.add("长段落的信息入口、收束方式和句子分组变化不足");
            }
        }
        return new Assessment(issues.isEmpty(), List.copyOf(issues), fourGram, sharedRun, technical);
    }

    static String feedback(Assessment assessment) {
        return "上一版未通过降重硬性门禁：" + String.join("；", assessment.issues())
                + "。请从原有事实重新组织整段，改变信息入口、句子分组和叙述顺序；"
                + "禁止逐句近义替换，不得新增数据、结论或引用，所有占位符必须原样保留。";
    }

    private static boolean isTechnical(String text) {
        if (text == null || text.isBlank()) return false;
        long signals = TECHNICAL_SIGNAL.matcher(text).results().count();
        return signals >= 3 || signals * 12 >= text.length();
    }

    private static String editableHan(String text) {
        String masked = IMMUTABLE_TOKEN.matcher(text == null ? "" : text).replaceAll("");
        StringBuilder result = new StringBuilder(masked.length());
        masked.codePoints()
                .filter(codePoint -> Character.UnicodeScript.of(codePoint) == Character.UnicodeScript.HAN)
                .forEach(result::appendCodePoint);
        return result.toString();
    }

    static Map<String, Integer> immutableTokenCounts(String text) {
        Map<String, Integer> counts = new HashMap<>();
        Matcher matcher = IMMUTABLE_TOKEN.matcher(text == null ? "" : text);
        while (matcher.find()) {
            counts.merge(matcher.group(), 1, Integer::sum);
        }
        return counts;
    }

    private static int longestSharedRunUpTo(String left, String right, int stopAt) {
        if (left.isEmpty() || right.isEmpty()) return 0;
        String shorter = left.length() <= right.length() ? left : right;
        String longer = left.length() <= right.length() ? right : left;
        int maximum = Math.min(shorter.length(), stopAt);
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

    private static String edge(String text, boolean start) {
        int width = Math.min(12, text.length());
        return start ? text.substring(0, width) : text.substring(text.length() - width);
    }

    private static int sentenceCount(String text) {
        if (text == null || text.isBlank()) return 0;
        String[] parts = text.split("[。！？!?；;]+", -1);
        int count = 0;
        for (String part : parts) if (!part.isBlank()) count++;
        return count;
    }

    private static String percent(double value) {
        return String.format(java.util.Locale.ROOT, "%.0f%%", value * 100);
    }

    record Assessment(boolean accepted, List<String> issues, double fourGramRatio,
                      int longestSharedRun, boolean technical) {
    }
}
