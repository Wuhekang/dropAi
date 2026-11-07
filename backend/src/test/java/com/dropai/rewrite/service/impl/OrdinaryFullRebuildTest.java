package com.dropai.rewrite.service.impl;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
class OrdinaryFullRebuildTest {
 @Test void preservesOrdinalFamiliesAndAllowsNumericOrderChange() {
  assertThat(OrdinaryFullRebuild.issues("货运增长29%，周转提高35%。", "周转提高35%，货运比上年增长29%。")).isEmpty();
  assertThat(OrdinaryFullRebuild.issues("首先，说明。其次，说明。最后，结束。", "第一，说明。其次，说明。最后，结束。")).anyMatch(x->x.contains("序号"));
 }
 @Test void rejectsDocumentedDriftAndUnchangedProse() {
  assertThat(OrdinaryFullRebuild.issues("可减少约20%的等待时间，效率提高35%。", "实地考察表明节省一半以上，效率达到35%。")).anyMatch(x->x.contains("数量关系"));
  String s="友谊关口岸负责组织进口榴莲的查验和通行，车辆排队与冷链衔接需要协调。".repeat(4);
  assertThat(OrdinaryFullRebuild.issues(s,s)).anyMatch(x->x.contains("密度"));
 }
 @Test void deepAndDoubleRemainOutsideNewRoute() {
  assertThat(OrdinaryRewriteAmplitude.applies("深度降低AI写作痕迹@WEIPU")).isFalse();
  assertThat(OrdinaryRewriteAmplitude.applies("double@WEIPU")).isFalse();
 }
}
