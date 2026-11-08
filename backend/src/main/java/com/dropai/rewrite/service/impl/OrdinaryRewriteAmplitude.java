package com.dropai.rewrite.service.impl;

/** Ordinary-only lexical-retention feedback; never a detector score or length gate. */
final class OrdinaryRewriteAmplitude {
    static final String RETRY_FEEDBACK = "上一候选仍近原文或加入了书面升档用词。请从本次原文重新生成，逐句把内容词换成平常说法，"
            + "像靠着、建起、不过、还需要配合、整个链条这样自然讲明白，不把查验改成核验、分流改成疏解。"
            + "专名、数字、指标含义、引用与原序号保留，不新增事实；意思成立时偏短，不堆解释，不给说明。";

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
        return needsLexicalRetry(original, rewritten) || !OrdinaryFullRebuild.formalUpgrades(original, rewritten).isEmpty();
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
