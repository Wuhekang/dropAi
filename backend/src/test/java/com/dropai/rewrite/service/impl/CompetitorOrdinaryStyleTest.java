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
