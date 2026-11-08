package com.dropai.rewrite.service.impl;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class OrdinaryPlatformMethodsTest {
    @Test
    void contextualGuidanceIsLimitedToMeasuredOrdinaryProfiles() {
        String prose = "冷链协同是指各方统一温控要求，需要完善监测。";
        for (String code : new String[]{"PAPERPASS", "WEIPU"}) {
            assertThat(OrdinaryPlatformMethods.instruction("humanize@" + code, prose))
                    .contains("定义和理论说明", "问题和对策段", "不强制拆句换骨架");
        }
        for (String type : new String[]{"humanize@DAYA", "humanize@CNKI", "double@PAPERPASS", "深度降低AI写作痕迹@WEIPU"}) {
            assertThat(OrdinaryPlatformMethods.instruction(type, prose))
                    .isEqualTo(OrdinaryPlatformMethods.instruction(type));
        }
    }

    @Test
    void studySummaryKeepsMeasuredInstruction() {
        String abstractText = "研究发现，货运量增长29%。本文提出统一温控的建议。";
        assertThat(OrdinaryPlatformMethods.instruction("humanize@PAPERPASS", abstractText))
                .isEqualTo(OrdinaryPlatformMethods.instruction("humanize@PAPERPASS"));
    }

    @Test
    void statisticsKeepMetricSemanticsAndUnrelatedProseGetsNoExtraInstruction() {
        assertThat(OrdinaryPlatformMethods.instruction("humanize@PAPERPASS", "2024年货运量增长29%。"))
                .contains("同比基期", "提高多少不能写成达到多少").doesNotContain("【本段类型补充】定义");
        assertThat(OrdinaryPlatformMethods.instruction("humanize@WEIPU", "这批货物从仓库发出。"))
                .isEqualTo(OrdinaryPlatformMethods.instruction("humanize@WEIPU"));
    }

    @Test
    void paperPassWeakParagraphGuidanceDoesNotLeakToOtherProfilesOrAbstract() {
        String weak = "全链条监测缺乏时间戳与温度记录，需统一操作指引和温湿度记录标准。";
        assertThat(OrdinaryPlatformMethods.instruction("humanize@PAPERPASS", weak))
                .contains("监测与问题段重点", "标准与对策段重点", "不新增主体、步骤");
        assertThat(OrdinaryPlatformMethods.instruction("humanize@WEIPU", weak))
                .doesNotContain("监测与问题段重点", "标准与对策段重点");
        assertThat(OrdinaryPlatformMethods.instruction("humanize@PAPERPASS", "本文研究全链条监测与温控标准。"))
                .doesNotContain("监测与问题段重点", "标准与对策段重点");
        assertThat(OrdinaryFullRebuild.instruction("humanize@PAPERPASS"))
                .contains("保留v7", "没有字数上下限").doesNotContain("篇幅参考原文的120%", "减少重复解释、同义复述");
        for (String type : new String[]{"humanize@WEIPU", "humanize@CNKI", "humanize@DAYA", "double@PAPERPASS"}) {
            assertThat(OrdinaryFullRebuild.instruction(type)).isEqualTo(OrdinaryFullRebuild.INSTRUCTION);
        }
    }
}
