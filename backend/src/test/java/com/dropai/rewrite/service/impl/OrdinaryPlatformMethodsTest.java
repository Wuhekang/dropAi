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
 @Test void noLengthBandsAndNoSoftKeep() {
  assertThat(OrdinaryFullRebuild.INSTRUCTION).contains("v7f口语密集展开 v13", "都必须逐句实际改写", "意思成立时偏向较短", "不设字数或字符比例目标")
   .doesNotContain("100%", "130%", "150%", "原有顺序已经自然时可保留");
  assertThat(OrdinaryRewriteAmplitude.retryFeedback()).contains("逐句把内容词换成平常说法").doesNotContain("130%", "至少拆开");
 }
}
