package com.dropai.rewrite.service.impl;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
class OrdinaryFullRebuildTest {
 @Test void preservesOrdinalFamiliesAndAllowsNumericOrderChange() {
  assertThat(OrdinaryFullRebuild.issues("货运增长29%，周转提高35%。", "周转提高35%，货运比上年增长29%。")).isEmpty();
  assertThat(OrdinaryFullRebuild.issues("首先，说明。其次，说明。最后，结束。", "第一，说明。其次，说明。最后，结束。")).anyMatch(x->x.contains("序号"));
 }
 @Test void rejectsDocumentedDriftAndUnchangedProse() {
  assertThat(OrdinaryFullRebuild.diagnostics("可减少约20%的等待时间，效率提高35%。", "实地考察表明节省一半以上，效率达到35%。")).anyMatch(x->x.contains("数量关系"));
  String s="友谊关口岸负责组织进口榴莲的查验和通行，车辆排队与冷链衔接需要协调。".repeat(4);
  assertThat(OrdinaryFullRebuild.diagnostics(s,s)).anyMatch(x->x.contains("密度"));
 }
 @Test void workflowPreservesOrdinalWordsAndFailsInsteadOfReturningSource() {
  var ai=org.mockito.Mockito.mock(com.dropai.rewrite.service.AiRewriteService.class);
  String source="首先，检查资料。其次，核对事实。最后，确认结论。";
  org.mockito.Mockito.when(ai.rewriteWithFeedback(org.mockito.ArgumentMatchers.anyString(),org.mockito.ArgumentMatchers.anyString(),org.mockito.ArgumentMatchers.anyInt(),org.mockito.ArgumentMatchers.anyString())).thenAnswer(call -> call.getArgument(0));
  var workflow=new DefaultWorkflowRewriteService(ai,new com.dropai.rewrite.service.TextStructureProtector());
  org.assertj.core.api.Assertions.assertThatThrownBy(()->workflow.execute(source,"humanize@WEIPU")).isInstanceOf(IllegalStateException.class);
  String unchanged="第一，内容。其次，内容。最后，结束。";
  org.mockito.Mockito.when(ai.rewriteWithFeedback(org.mockito.ArgumentMatchers.anyString(),org.mockito.ArgumentMatchers.anyString(),org.mockito.ArgumentMatchers.anyInt(),org.mockito.ArgumentMatchers.anyString())).thenReturn(unchanged);
  assertThat(workflow.execute(source,"humanize@PAPERPASS").getRewrittenText()).isEqualTo("首先，内容。其次，内容。最后，结束。");
 }
 @Test void ordinaryPromptDoesNotLoadLegacySkillAndOrdinalMarkersRoundTrip() throws Exception {
  var skills=org.mockito.Mockito.mock(com.dropai.rewrite.service.SkillPromptService.class);
  var service=new DoubaoAiRewriteService(new com.dropai.rewrite.config.DoubaoProperties(),new com.fasterxml.jackson.databind.ObjectMapper(),org.springframework.web.client.RestClient.builder(),skills,org.mockito.Mockito.mock(com.dropai.rewrite.service.ai.DoubaoModelRouter.class));
  var prompt=DoubaoAiRewriteService.class.getDeclaredMethod("systemPrompt",String.class);prompt.setAccessible(true);
  assertThat((String)prompt.invoke(service,"humanize@WEIPU")).contains("普通整段重构 v4");
  org.mockito.Mockito.verifyNoInteractions(skills);
  var protectedText=new com.dropai.rewrite.service.TextStructureProtector().protectForOrdinary("一是文献研究。二是流程分析。三是案例研究。");
  assertThat(protectedText.text()).doesNotContain("一是","二是","三是");
  assertThat(protectedText.restore(protectedText.text())).isEqualTo("一是文献研究。二是流程分析。三是案例研究。");
 }
 @Test void thirdCandidateWithOnlySoftWarningsIsReturnedWithoutFourthCall() {
  var ai=org.mockito.Mockito.mock(com.dropai.rewrite.service.AiRewriteService.class);
  String source="首先，效率提高35%。其次，减少约20%的等待时间。最后，确认。";
  String accepted="首先，效率达到35%。其次，节省一半以上。最后，确认。";
  org.mockito.Mockito.when(ai.rewriteWithFeedback(org.mockito.ArgumentMatchers.anyString(),org.mockito.ArgumentMatchers.anyString(),org.mockito.ArgumentMatchers.anyInt(),org.mockito.ArgumentMatchers.anyString()))
      .thenReturn("", "", accepted.replace("首先","[[DROP_AI_PROTECTED_0]]").replace("其次","[[DROP_AI_PROTECTED_1]]").replace("最后","[[DROP_AI_PROTECTED_2]]"));
  var workflow=new DefaultWorkflowRewriteService(ai,new com.dropai.rewrite.service.TextStructureProtector());
  assertThat(workflow.execute(source,"humanize@WEIPU").getRewrittenText()).isEqualTo(accepted);
  org.mockito.Mockito.verify(ai,org.mockito.Mockito.times(3)).rewriteWithFeedback(org.mockito.ArgumentMatchers.anyString(),org.mockito.ArgumentMatchers.anyString(),org.mockito.ArgumentMatchers.anyInt(),org.mockito.ArgumentMatchers.anyString());
  assertThat(OrdinaryFullRebuild.issues(source,accepted)).isEmpty();
  assertThat(OrdinaryFullRebuild.diagnostics(source,accepted)).isNotEmpty();
 }
 @Test void ordinaryProseDoesNotInventAnOrdinalFailure() {
  assertThat(OrdinaryFullRebuild.issues("园区实行统一管理，改善运输。", "第二产业与最后一公里运输衔接仍需改善。")).isEmpty();
  assertThat(OrdinaryFullRebuild.repairOrdinals("首先，核对。其次，检查。最后，交付。", "第一，重新核对。第二，仔细检查。第三，交付稿件。"))
      .isEqualTo("首先，重新核对。其次，仔细检查。最后，交付稿件。");
 }
 @Test void sevenPlatformsHaveDistinctOrdinaryInstructions() {
  var codes=java.util.List.of("CNKI","WEIPU","GEZIDA","PAPERPASS","PAPERYY","ZHUQUE","WANFANG");
  assertThat(codes.stream().map(x->OrdinaryPlatformMethods.instruction("humanize@"+x)).distinct().count()).isEqualTo(7);
 }
 @Test void deepAndDoubleRemainOutsideNewRoute() {
  assertThat(OrdinaryRewriteAmplitude.applies("深度降低AI写作痕迹@WEIPU")).isFalse();
  assertThat(OrdinaryRewriteAmplitude.applies("double@WEIPU")).isFalse();
 }
}
