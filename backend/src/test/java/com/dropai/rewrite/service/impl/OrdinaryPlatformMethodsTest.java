package com.dropai.rewrite.service.impl;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
class OrdinaryPlatformMethodsTest {
 @Test void allOrdinaryParagraphsReceiveDenseLexicalGuidance() {
  for (String code : new String[]{"CNKI","WEIPU","WANFANG","GEZIDA","PAPERPASS","PAPERYY","ZHUQUE"}) {
   assertThat(OrdinaryPlatformMethods.instruction("humanize@"+code,"本文研究监测与风险。"))
    .contains("所有摘要与正文必须改写", "平常说法", "不能只换段首").doesNotContain("六种人工轻调模式", "友谊关", "550.7");
  }
 }
 @Test void deepDoubleAndDayaDoNotReceiveOrdinaryExtraGuidance() {
  for(String type:new String[]{"humanize@DAYA","double@PAPERPASS","深度降低AI写作痕迹@WEIPU"}) {
   assertThat(OrdinaryPlatformMethods.instruction(type,"本文研究监测。")).isEqualTo(OrdinaryPlatformMethods.instruction(type));
  }
 }
 @Test void rivalLightGuidanceBreaksFamilyWithoutInventingResearch() {
  assertThat(OrdinaryFullRebuild.INSTRUCTION).contains("维普式轻口语重述 v15", "约112%—120%", "仅字数不构成硬拒绝", "原文未做调查，不得写经过调查发现")
   .doesNotContain("100%", "130%", "例如“持续提高”可说“一直都在进步当中”");
  assertThat(OrdinaryRewriteAmplitude.retryFeedback()).contains("先改开场与收尾串法", "不编造调查").doesNotContain("130%", "至少拆开");
 }
}
