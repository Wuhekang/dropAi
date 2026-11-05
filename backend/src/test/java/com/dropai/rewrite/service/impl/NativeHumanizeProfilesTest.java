package com.dropai.rewrite.service.impl;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class NativeHumanizeProfilesTest {

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "unknown", "TEST", "DAYA", "daya", "大雅", "HUACHEN"})
    void blankUnknownAndDayaStayGeneral(String platform) {
        assertThat(NativeHumanizeProfiles.normalize(platform)).isEqualTo("GENERAL");
        assertThat(NativeHumanizeProfiles.kind(NativeHumanizeProfiles.normalize(platform)))
                .isEqualTo(NativeHumanizeProfiles.Kind.GENERAL);
        assertThat(NativeHumanizeProfiles.skillName(NativeHumanizeProfiles.normalize(platform)))
                .isEqualTo(NativeHumanizeProfiles.BASE_SKILL);
    }

    @Test
    void acceptsCompetitorPlatformCodesAndAliases() {
        assertThat(NativeHumanizeProfiles.normalize("cnki")).isEqualTo("CNKI");
        assertThat(NativeHumanizeProfiles.normalize("知网")).isEqualTo("CNKI");
        assertThat(NativeHumanizeProfiles.normalize("ZHIWANG")).isEqualTo("CNKI");
        assertThat(NativeHumanizeProfiles.normalize("维普")).isEqualTo("WEIPU");
        assertThat(NativeHumanizeProfiles.normalize("cqvip")).isEqualTo("WEIPU");
        assertThat(NativeHumanizeProfiles.normalize("格子达")).isEqualTo("GEZIDA");
        assertThat(NativeHumanizeProfiles.normalize("万方")).isEqualTo("WANFANG");
        assertThat(NativeHumanizeProfiles.normalize("Paper-Pass")).isEqualTo("PAPERPASS");
        assertThat(NativeHumanizeProfiles.normalize("paper_yy")).isEqualTo("PAPERYY");
        assertThat(NativeHumanizeProfiles.normalize("朱雀")).isEqualTo("ZHUQUE");
        assertThat(NativeHumanizeProfiles.normalize("Turnitin")).isEqualTo("TURNITIN");
    }

    @Test
    void splitsDepthPairPlatformsFromDedicatedProfiles() {
        for (String platform : new String[]{"CNKI", "WEIPU", "PAPERYY", "ZHUQUE"}) {
            assertThat(NativeHumanizeProfiles.kind(platform)).isEqualTo(NativeHumanizeProfiles.Kind.DEPTH_PAIR);
            assertThat(NativeHumanizeProfiles.skillName(platform)).isEqualTo(NativeHumanizeProfiles.BASE_SKILL);
            String ordinary = NativeHumanizeProfiles.profileInstruction("humanize", platform);
            String deep = NativeHumanizeProfiles.profileInstruction("深度降低AI写作痕迹", platform);
            assertThat(ordinary).isNotEqualTo(deep).doesNotContain("降AI专用");
            assertThat(deep).contains("深度改写");
            assertThat(NativeHumanizeProfiles.profileInstruction("降低AI写作痕迹", platform)).isEqualTo(ordinary);
            assertThat(NativeHumanizeProfiles.profileInstruction("智能降AI", platform)).isEqualTo(ordinary);
        }
        for (String platform : new String[]{"GEZIDA", "PAPERPASS", "WANFANG", "TURNITIN"}) {
            assertThat(NativeHumanizeProfiles.kind(platform)).isEqualTo(NativeHumanizeProfiles.Kind.DEDICATED);
            assertThat(NativeHumanizeProfiles.skillName(platform)).startsWith("humanize-platform-");
            assertThat(NativeHumanizeProfiles.profileInstruction("humanize", platform))
                    .isEqualTo(NativeHumanizeProfiles.profileInstruction("深度降低AI写作痕迹", platform))
                    .contains("降AI专用");
        }
    }

    @Test
    void generalHasNoPlatformOverlayAndLegacyRewriteCodesStayNarrow() {
        assertThat(NativeHumanizeProfiles.profileInstruction("深度降低AI写作痕迹", "GENERAL")).isEmpty();
        assertThat(NativeHumanizeProfiles.profileInstruction("humanize", "DAYA")).isEmpty();
        assertThat(NativeHumanizeProfiles.legacyRewriteCode("PaperPass")).isEqualTo("GENERAL");
        assertThat(NativeHumanizeProfiles.legacyRewriteCode("Turnitin")).isEqualTo("GENERAL");
        assertThat(NativeHumanizeProfiles.legacyRewriteCode("知网")).isEqualTo("GENERAL");
        assertThat(NativeHumanizeProfiles.legacyRewriteCode("cnki")).isEqualTo("CNKI");
        assertThat(NativeHumanizeProfiles.legacyRewriteCode("WEIPU")).isEqualTo("WEIPU");
        assertThat(NativeHumanizeProfiles.legacyRewriteCode(null)).isEqualTo("GENERAL");
    }

    @Test
    void documentDepthUsesTheExistingDeepRewriteType() {
        assertThat(NativeHumanizeProfiles.humanizeBase(null)).isEqualTo("humanize");
        assertThat(NativeHumanizeProfiles.humanizeBase("FULL_AI_REDUCE")).isEqualTo("humanize");
        assertThat(NativeHumanizeProfiles.humanizeBase("PRECISE_AI_REDUCE")).isEqualTo("humanize");
        assertThat(NativeHumanizeProfiles.humanizeBase("humanize")).isEqualTo("humanize");
        assertThat(NativeHumanizeProfiles.humanizeBase("深度降低AI写作痕迹")).isEqualTo("深度降低AI写作痕迹");
        assertThat(NativeHumanizeProfiles.workflowType("humanize", "GENERAL")).isEqualTo("humanize");
        assertThat(NativeHumanizeProfiles.workflowType("深度降低AI写作痕迹", "CNKI"))
                .isEqualTo("深度降低AI写作痕迹@CNKI");
    }

    @Test
    void everyNativeProfileAllowsRebuildAndRefusesExpansion() {
        for (String platform : new String[]{"CNKI", "WEIPU", "PAPERYY", "ZHUQUE"}) {
            String ordinary = NativeHumanizeProfiles.profileInstruction("humanize", platform);
            String deep = NativeHumanizeProfiles.profileInstruction("深度降低AI写作痕迹", platform);
            assertThat(ordinary)
                    .contains("重组句组", "90%—108%", "优先不超过105%", "不要扩写")
                    .doesNotContain("不整段换序", "不要增删句子", "已经自然的句子可以保留", "85%—130%");
            assertThat(deep)
                    .contains("深度改写", "90%—108%", "不要扩写", "深度不得靠加长完成")
                    .doesNotContain("85%—130%");
        }
        for (String platform : new String[]{"GEZIDA", "PAPERPASS", "WANFANG", "TURNITIN"}) {
            String dedicated = NativeHumanizeProfiles.profileInstruction("humanize", platform);
            assertThat(dedicated)
                    .contains("重组句组", "90%—108%", "不要扩写")
                    .doesNotContain("不整段换序", "不要增删句子", "句子数量不变");
            assertThat(NativeHumanizeProfiles.profileInstruction("深度降低AI写作痕迹", platform))
                    .isEqualTo(dedicated);
        }
    }

    @Test
    void displayNamesCoverTheNativeSetAndDoNotLabelDaya() {
        assertThat(NativeHumanizeProfiles.displayName("CNKI")).isEqualTo("知网");
        assertThat(NativeHumanizeProfiles.displayName("WEIPU")).isEqualTo("维普");
        assertThat(NativeHumanizeProfiles.displayName("GEZIDA")).isEqualTo("格子达");
        assertThat(NativeHumanizeProfiles.displayName("WANFANG")).isEqualTo("万方");
        assertThat(NativeHumanizeProfiles.displayName("PAPERPASS")).isEqualTo("PaperPass");
        assertThat(NativeHumanizeProfiles.displayName("PAPERYY")).isEqualTo("PaperYY");
        assertThat(NativeHumanizeProfiles.displayName("ZHUQUE")).isEqualTo("朱雀");
        assertThat(NativeHumanizeProfiles.displayName("TURNITIN")).isEqualTo("Turnitin");
        assertThat(NativeHumanizeProfiles.displayName("DAYA")).isEqualTo("通用");
        assertThat(NativeHumanizeProfiles.displayName("GENERAL")).isEqualTo("通用");
    }
}
