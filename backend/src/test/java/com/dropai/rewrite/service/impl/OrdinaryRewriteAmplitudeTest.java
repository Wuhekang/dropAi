package com.dropai.rewrite.service.impl;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class OrdinaryRewriteAmplitudeTest {

    private static final String INPUT = "近年来，随着信息技术的快速发展，数字化转型已经成为各行各业提升核心竞争力的重要路径。"
            + "本文旨在通过构建一套系统化的分析框架，深入研究相关技术在实际场景中的应用效果，从而为后续优化提供有益参考。"
            + "研究表明，通过合理配置资源并完善协同机制，可以有效提升整体运行效率，并在一定程度上降低运营成本。"
            + "与此同时，还需要进一步关注数据安全与隐私保护问题，以确保技术方案具备可持续的实践价值。";

    private static final String SAME_SHAPE = INPUT.replace("近年来", "近些年来").replace("本文旨在", "本文希望");

    private static final String SPLIT = "数字化转型被各个行业拿来提高核心竞争力，背景是信息技术这些年发展得很快。"
            + "相关技术在实际场景中的应用效果，才是后续优化要参照的内容。"
            + "分析框架只服务于这件事。"
            + "资源如果配置得当，协同机制又能跟上，整体运行效率会上去。"
            + "运营成本也会在一定程度上降下来。"
            + "数据安全与隐私保护仍然要盯住，技术方案才谈得上可持续的实践价值。";

    @Test
    void sameSentenceCountOnLongProseNeedsAnotherPass() {
        assertThat(OrdinaryRewriteAmplitude.needsStructureRetry(INPUT, SAME_SHAPE)).isTrue();
        assertThat(OrdinaryRewriteAmplitude.needsStructureRetry(INPUT, INPUT)).isTrue();
        assertThat(OrdinaryRewriteAmplitude.needsStructureRetry(INPUT, SPLIT)).isFalse();
        assertThat(OrdinaryRewriteAmplitude.sentences(INPUT)).isEqualTo(4);
        assertThat(OrdinaryRewriteAmplitude.sentences(SPLIT)).isEqualTo(6);
    }

    @Test
    void shortFragmentsAndDeepModesStayOut() {
        String shortText = "该平台负责核对资料，并记录现场处理情况。";
        assertThat(OrdinaryRewriteAmplitude.needsStructureRetry(shortText, shortText)).isFalse();
        assertThat(OrdinaryRewriteAmplitude.applies("humanize")).isTrue();
        assertThat(OrdinaryRewriteAmplitude.applies("humanize@CNKI")).isTrue();
        assertThat(OrdinaryRewriteAmplitude.applies("humanize@PAPERPASS")).isTrue();
        assertThat(OrdinaryRewriteAmplitude.applies("humanize@TURNITIN")).isTrue();
        assertThat(OrdinaryRewriteAmplitude.applies("深度降低AI写作痕迹@CNKI")).isFalse();
        assertThat(OrdinaryRewriteAmplitude.applies("humanize-shared@WEIPU")).isFalse();
        assertThat(OrdinaryRewriteAmplitude.applies("humanize@DAYA")).isFalse();
        assertThat(OrdinaryRewriteAmplitude.applies("rewrite@CNKI")).isFalse();
    }

    @Test
    void lengthReferenceKeepsTheReportedBandAndAStrongerRewrite() {
        assertThat(DocumentRewriteServiceImpl.humanizeSoftReferenceMax(7002)).isEqualTo(10503);
        assertThat(7250).isLessThanOrEqualTo(DocumentRewriteServiceImpl.humanizeSoftReferenceMax(7002));
        assertThat(12000).isGreaterThan(DocumentRewriteServiceImpl.humanizeSoftReferenceMax(7002));
    }
}
