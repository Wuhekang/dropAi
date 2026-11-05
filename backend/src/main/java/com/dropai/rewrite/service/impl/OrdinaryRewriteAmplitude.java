package com.dropai.rewrite.service.impl;

/**
 * Detects ordinary 降AI drafts that still keep the source sentence skeleton.
 *
 * <p>Character n-grams miss Chinese synonym swaps, so a multi-sentence paragraph
 * that comes back with the same sentence count is treated as unfinished. Short
 * fragments stay untouched. Deep, 双降 shared humanize and 大雅 are out of scope.
 */
final class OrdinaryRewriteAmplitude {

    static final String RETRY_FEEDBACK = "上一版仍沿原句数量或原句顺序换词，检测器仍会看成同一套论文骨架。"
            + "请保留全部年份、吨位、百分比、专名、引用编号、图表结构和原文结论的原值及相对顺序，"
            + "从已有对象、数据或条件重排信息入口，并至少拆开或合并一处句子。"
            + "不要新增事实，不要为了压到130%以内改回近义版或原文。不要解释，只输出正文。";

    private OrdinaryRewriteAmplitude() {
    }

    static boolean applies(String rewriteType) {
        String base = NativeHumanizeProfiles.baseRewriteType(rewriteType);
        if (!NativeHumanizeProfiles.isHumanizeType(rewriteType)) {
            return false;
        }
        if (NativeHumanizeProfiles.SHARED_HUMANIZE.equals(base) || NativeHumanizeProfiles.isDeep(rewriteType)) {
            return false;
        }
        String platform = platformSuffix(rewriteType);
        if (platform.isEmpty()) {
            platform = NativeHumanizeProfiles.GENERAL;
        }
        if ("DAYA".equals(platform) || "大雅".equals(platform)) {
            return false;
        }
        return NativeHumanizeProfiles.usesNativeSkill(NativeHumanizeProfiles.normalize(platform));
    }

    /**
     * Long ordinary prose that is unchanged, or that still has the same sentence count.
     * One-sentence and short fragments return false so a completed short call is not retried.
     */
    static boolean needsStructureRetry(String original, String rewritten) {
        if (!longEnough(original) || rewritten == null || rewritten.isBlank()) {
            return false;
        }
        if (comparable(original).equals(comparable(rewritten))) {
            return true;
        }
        int sourceSentences = sentences(original);
        return sourceSentences >= 3 && sourceSentences == sentences(rewritten);
    }

    static String retryFeedback() {
        return RETRY_FEEDBACK;
    }

    static boolean sameLetters(String left, String right) {
        return comparable(left).equals(comparable(right));
    }

    static int sentences(String text) {
        if (text == null || text.isBlank()) {
            return 0;
        }
        int count = 0;
        for (int index = 0; index < text.length(); index++) {
            char current = text.charAt(index);
            if (current == '。' || current == '！' || current == '？') {
                count++;
                continue;
            }
            if (current != '.' && current != '!' && current != '?') {
                continue;
            }
            char previous = index > 0 ? text.charAt(index - 1) : ' ';
            char next = index + 1 < text.length() ? text.charAt(index + 1) : ' ';
            if (Character.isDigit(previous) && Character.isDigit(next)) {
                continue;
            }
            if (current == '.' && index + 1 < text.length() && next != ' ' && next != '\n' && next != '\r') {
                continue;
            }
            count++;
        }
        return count;
    }

    private static boolean longEnough(String text) {
        int han = hanCount(text);
        if (han >= 80) {
            return true;
        }
        return han == 0 && words(text) >= 70;
    }

    private static int hanCount(String text) {
        if (text == null || text.isEmpty()) {
            return 0;
        }
        int count = 0;
        for (int index = 0; index < text.length(); ) {
            int codePoint = text.codePointAt(index);
            if (Character.UnicodeScript.of(codePoint) == Character.UnicodeScript.HAN) {
                count++;
            }
            index += Character.charCount(codePoint);
        }
        return count;
    }

    private static int words(String text) {
        String trimmed = text == null ? "" : text.trim();
        if (trimmed.isEmpty()) {
            return 0;
        }
        return trimmed.split("\\s+").length;
    }

    private static String comparable(String text) {
        if (text == null || text.isEmpty()) {
            return "";
        }
        StringBuilder letters = new StringBuilder(text.length());
        text.codePoints().filter(codePoint -> Character.isLetter(codePoint)
                        || Character.UnicodeScript.of(codePoint) == Character.UnicodeScript.HAN)
                .forEach(letters::appendCodePoint);
        return letters.toString();
    }

    private static String platformSuffix(String rewriteType) {
        if (rewriteType == null) {
            return "";
        }
        int index = rewriteType.indexOf('@');
        if (index < 0 || index == rewriteType.length() - 1) {
            return "";
        }
        return rewriteType.substring(index + 1).trim().toUpperCase(java.util.Locale.ROOT);
    }
}
