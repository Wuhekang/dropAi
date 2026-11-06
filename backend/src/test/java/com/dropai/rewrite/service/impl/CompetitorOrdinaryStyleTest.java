package com.dropai.rewrite.service.impl;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class CompetitorOrdinaryStyleTest {
    @Test
    void detectsTemplateEvenAfterSentenceCountChanges() {
        String original = "友谊关口岸的研究需要同时关注通行能力与全链条运行的稳定性。".repeat(5);
        String rewritten = "研究发现，口岸通行能力提升。信息仍需协同。";
        assertThat(OrdinaryRewriteAmplitude.needsStructureRetry(original, rewritten)).isTrue();
        assertThat(OrdinaryRewriteAmplitude.hasTemplateSkeleton("经过研究得知，现有资料支持这一判断。")).isFalse();
    }

    @Test
    void catchesReplacementBrochureSkeletonWithoutRejectingPlainAlternates() {
        assertThat(OrdinaryRewriteAmplitude.hasTemplateSkeleton("口岸能力得到直接提升。")).isTrue();
        assertThat(OrdinaryRewriteAmplitude.hasTemplateSkeleton("针对这些短板，研究从预约等方向提出优化思路，供管理参考。")).isTrue();
        assertThat(OrdinaryRewriteAmplitude.hasTemplateSkeleton("从结果看，等候时间可以减少约20%。因此本文提出相应的改善措施。")).isFalse();
        for (String platform : new String[]{"PAPERPASS", "WEIPU"}) {
            assertThat(NativeHumanizeProfiles.profileInstruction("humanize", platform))
                .contains("v3", "月度同比不能改成上一年", "没有实际调研不能声称调查", "针对这些短板");
        }
    }

    @Test
    void safeCadenceRetainsQuantitativeRelationshipsAndAllowsObservedConnector() {
        assertThat(OrdinaryRewriteAmplitude.hasTemplateSkeleton("经研究发现，等候时间可以减少约20%。")).isFalse();
        assertThat(OrdinaryRewriteAmplitude.hasTemplateSkeleton("研究发现，通行能力持续提高。")).isTrue();
        assertThat(OrdinaryRewriteAmplitude.safeCadenceFeedback()).contains("约20%", "提高而非达到", "保留基期", "禁止新增");
    }

    @Test
    void targetedProfilesKeepQualifiersAndMetricMeaning() {
        for (String platform : new String[]{"PAPERPASS", "WEIPU"}) {
            assertThat(NativeHumanizeProfiles.profileInstruction("humanize", platform))
                .contains("竞品样本A", "年份、吨位、百分比", "保留可、约、超过", "指标定义不明时保留原术语", "不增加方法、类别或步骤");
        }
        assertThat(NativeHumanizeProfiles.profileInstruction("深度降低AI写作痕迹", "WEIPU"))
            .doesNotContain("竞品样本A");
        assertThat(OrdinaryRewriteAmplitude.applies("深度降低AI写作痕迹@WEIPU")).isFalse();
        assertThat(OrdinaryRewriteAmplitude.applies("double@PAPERPASS")).isFalse();
    }
}
