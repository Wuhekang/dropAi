package com.dropai.rewrite.service.impl;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
class OrdinaryPlatformMethodsTest {
 @Test void allOrdinaryParagraphsReceiveDenseLexicalGuidance() {
  for (String code : new String[]{"CNKI","WEIPU","WANFANG","GEZIDA","PAPERPASS","PAPERYY","ZHUQUE"}) {
   assertThat(OrdinaryPlatformMethods.instruction("humanize@"+code,"本文研究监测与风险。"))
    .contains("无摘要或正文豁免", "表达家族", "只换入口不算完成").doesNotContain("六种人工轻调模式", "友谊关", "550.7");
  }
 }
 @Test void deepDoubleAndDayaDoNotReceiveOrdinaryExtraGuidance() {
  for(String type:new String[]{"humanize@DAYA","double@PAPERPASS","深度降低AI写作痕迹@WEIPU"}) {
   assertThat(OrdinaryPlatformMethods.instruction(type,"本文研究监测。")).isEqualTo(OrdinaryPlatformMethods.instruction(type));
  }
 }
 @Test void noLengthBandsAndNoSoftKeep() {
  assertThat(OrdinaryFullRebuild.INSTRUCTION).contains("全段密集换词 v12", "每一句都必须实际改写", "意思成立时以少写为先", "没有任何字数或字符比例目标")
   .doesNotContain("100%", "130%", "150%", "原有顺序已经自然时可保留");
  assertThat(OrdinaryRewriteAmplitude.retryFeedback()).contains("逐句替换").doesNotContain("130%", "至少拆开");
 }
}
