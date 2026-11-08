package com.dropai.rewrite.service.impl;

/** Ordinary-only lexical-retention feedback; never a detector score or length gate. */
final class OrdinaryRewriteAmplitude {
    static final String RETRY_FEEDBACK = "上一候选仍有模板家族、近原文贴词、重水包装或升档/生硬词。请从原文重新轻口语重述："
            + "先改开场与收尾串法，再把中间每句的内容词密换，用并且、从而、使得等少量承接串清原义。"
            + "不要保留研究发现/据此/以期旧串法，不用一直都在……当中或和上一年同期相比增长了；不编造调查，"
            + "不把主要不是加强成根本不是，不把减少写成不用再，数字、指标、专名、条件和序号保留。只输出正文。";

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

    /** A bounded retry signal based on retained source trigrams, independent of sentence count. */
    static boolean needsLexicalRetry(String original, String rewritten) {
        if (original == null || original.isBlank()) return false;
        if (rewritten == null || rewritten.isBlank()) return true;
        return sameLetters(original, rewritten) || OrdinaryFullRebuild.overlap(original, rewritten) > 0.65;
    }
    static boolean needsSpokenRetry(String original, String rewritten) {
        return needsLexicalRetry(original, rewritten)
                || !OrdinaryFullRebuild.templateRemnants(rewritten).isEmpty()
                || !OrdinaryFullRebuild.formalUpgrades(original, rewritten).isEmpty()
                || !OrdinaryFullRebuild.paddingPhrases(original, rewritten).isEmpty()
                || !OrdinaryFullRebuild.unnaturalSwaps(original, rewritten).isEmpty()
                || OrdinaryFullRebuild.lengthRatio(original, rewritten) >= 1.40
                || (OrdinaryFullRebuild.lengthRatio(original, rewritten) < 1.08
                    && OrdinaryFullRebuild.overlap(original, rewritten) > 0.45);
    }
    static boolean needsStructureRetry(String original, String rewritten) {
        return needsLexicalRetry(original, rewritten);
    }

    static boolean hasTemplateSkeleton(String text) {
        if (text == null) return false;
        return java.util.regex.Pattern.compile("研究发现|据此|以期|综合运用|持续提高|尚未完全转化为|得到直接提升|针对这些短板[，,]?研究从[^。！？]{0,160}提出优化思路|提出优化思路[，,]?供[^。！？]{0,160}参考|本文从[^。！？]{0,160}等方面提出改进建议")
                .matcher(text).find();
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
