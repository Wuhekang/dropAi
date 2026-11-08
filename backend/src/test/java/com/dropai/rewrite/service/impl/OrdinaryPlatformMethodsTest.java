package com.dropai.rewrite.service.impl;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class OrdinaryPlatformMethodsTest {
    @Test
    void sevenOrdinaryProfilesSharePatternsWithoutCopyingManuscriptFacts() {
        for (String code : new String[]{"CNKI","WEIPU","WANFANG","GEZIDA","PAPERPASS","PAPERYY","ZHUQUE"}) {
            String prompt = OrdinaryPlatformMethods.instruction("humanize@" + code, "监测环节的数据分散，需要统一记录。");
            assertThat(prompt).contains("六种人工轻调模式", "4 监测问题", "6 标准对策", "只选与本段内容相符")
                    .doesNotContain("友谊关", "550.7", "榴莲", "监测与问题段重点", "每句的谓语");
        }
    }
    @Test
    void nonOrdinaryModesAndDayaDoNotReceivePatternExamples() {
        for (String type : new String[]{"humanize@DAYA", "double@PAPERPASS", "深度降低AI写作痕迹@WEIPU"}) {
            assertThat(OrdinaryPlatformMethods.instruction(type, "需要监测并统一标准。"))
                    .isEqualTo(OrdinaryPlatformMethods.instruction(type));
        }
    }
    @Test
    void lightStyleKeepsFactsAndHasNoLengthAcceptanceGate() {
        assertThat(OrdinaryFullRebuild.instruction("humanize@PAPERPASS"))
                .contains("轻量入口调整 v11", "不为凑比例补话", "不为凑改写密度扩到150%以上", "没有字数上下限", "不新增实体")
                .doesNotContain("保留v7的普通词组展开密度", "每句的谓语、名词链");
        assertThat(OrdinaryFullRebuild.issues("车辆需要等候。", "进口车辆到达之后，要先排队等候办理查验和放行手续。"))
                .isEmpty();
    }
}
