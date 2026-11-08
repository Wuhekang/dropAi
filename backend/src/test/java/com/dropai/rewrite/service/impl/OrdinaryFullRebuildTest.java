package com.dropai.rewrite.service.impl;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
class OrdinaryFullRebuildTest {
 @Test void preservesOrdinalFamiliesAndAllowsNumericOrderChange() {
  assertThat(OrdinaryFullRebuild.issues("货运增长29%，周转提高35%。", "周转提高35%，货运比上年增长29%。")).isEmpty();
  assertThat(OrdinaryFullRebuild.issues("首先，说明。其次，说明。最后，结束。", "第一，说明。其次，说明。最后，结束。")).anyMatch(x->x.contains("序号"));
 }
 @Test void rejectsDocumentedDriftAndUnchangedProse() {
  assertThat(OrdinaryFullRebuild.diagnostics("可减少约20%的等待时间，效率提高35%。", "实地考察表明节省一半以上，效率达到35%。")).anyMatch(x->x.contains("数值表达"));
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
  assertThat((String)prompt.invoke(service,"humanize@WEIPU")).contains("普通降AI·维普式轻口语重述 v15");
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
 @Test void ordinaryPreservesCitationMarkersWithoutLockingNumbers() {
  var text=new com.dropai.rewrite.service.TextStructureProtector().protectForOrdinary("车位由28个增至76个[13]，货量增长29%[2, 5]。");
  assertThat(text.text()).contains("28","76","29%").doesNotContain("[13]","[2, 5]");
  assertThat(text.restore(text.text())).isEqualTo("车位由28个增至76个[13]，货量增长29%[2, 5]。");
  org.assertj.core.api.Assertions.assertThatThrownBy(()->text.restore("车位增加了。"))
      .isInstanceOf(com.dropai.rewrite.service.TextStructureProtector.ProtectedContentIntegrityException.class);
 }
 @Test void ordinaryDoesNotTreatRivalConnectorsAsRejectionReasons() {
  String source="研究发现，口岸服务持续提高，据此提出建议，以期改善物流。";
  String result="经研究发现，口岸的服务一直都在进步当中，因此本文给出建议，希望能够改善物流。";
  assertThat(OrdinaryFullRebuild.issues(source,result)).isEmpty();
  assertThat(OrdinaryFullRebuild.diagnostics(source,result)).noneMatch(x->x.contains("模板")||x.contains("宣传"));
 }
 @Test void deepAndDoubleRemainOutsideNewRoute() {
  assertThat(OrdinaryRewriteAmplitude.applies("深度降低AI写作痕迹@WEIPU")).isFalse();
  assertThat(OrdinaryRewriteAmplitude.applies("double@WEIPU")).isFalse();
  assertThat(OrdinaryRewriteAmplitude.applies("humanize@DAYA")).isFalse();
 }


 @Test void ordinaryLengthNeverDropsAnActualRewrite() {
  for (String candidate : java.util.List.of("口岸车辆等待放行。", "口岸的运输车辆正在有序排队等待办理查验和放行手续，相关环节仍需协调安排。")) {
   var ai=org.mockito.Mockito.mock(com.dropai.rewrite.service.AiRewriteService.class);
   org.mockito.Mockito.when(ai.rewriteWithFeedback(org.mockito.ArgumentMatchers.anyString(),org.mockito.ArgumentMatchers.anyString(),org.mockito.ArgumentMatchers.anyInt(),org.mockito.ArgumentMatchers.anyString())).thenReturn(candidate);
   var workflow=new DefaultWorkflowRewriteService(ai,new com.dropai.rewrite.service.TextStructureProtector());
   assertThat(workflow.execute("进口车辆在口岸排队。","humanize@PAPERPASS").getRewrittenText()).isEqualTo(candidate);
   org.mockito.Mockito.verify(ai,org.mockito.Mockito.times(OrdinaryFullRebuild.lengthRatio("进口车辆在口岸排队。",candidate)>=1.40 ? 3 : 1)).rewriteWithFeedback(org.mockito.ArgumentMatchers.anyString(),org.mockito.ArgumentMatchers.anyString(),org.mockito.ArgumentMatchers.anyInt(),org.mockito.ArgumentMatchers.anyString());
  }
 }

 @Test void nearSourceIsRetriedButGeneratedFallbackIsNotDropped() {
  String source="系统记录车辆抵达时间和查验结果，管理人员据此安排后续运输。".repeat(4);
  String weak=source.replace("后续运输","后续转运");
  var ai=org.mockito.Mockito.mock(com.dropai.rewrite.service.AiRewriteService.class);
  org.mockito.Mockito.when(ai.rewriteWithFeedback(org.mockito.ArgumentMatchers.anyString(),org.mockito.ArgumentMatchers.anyString(),org.mockito.ArgumentMatchers.anyInt(),org.mockito.ArgumentMatchers.anyString())).thenReturn(weak);
  var workflow=new DefaultWorkflowRewriteService(ai,new com.dropai.rewrite.service.TextStructureProtector());
  assertThat(workflow.execute(source,"humanize@PAPERPASS").getRewrittenText()).isEqualTo(weak);
  org.mockito.Mockito.verify(ai,org.mockito.Mockito.times(3)).rewriteWithFeedback(org.mockito.ArgumentMatchers.eq(source),org.mockito.ArgumentMatchers.anyString(),org.mockito.ArgumentMatchers.anyInt(),org.mockito.ArgumentMatchers.anyString());
  assertThat(OrdinaryFullRebuild.diagnostics(source,weak)).anyMatch(x->x.contains("密度质量不通过"));
 }

 @Test void spokenCandidateBeatsMoreDistantFormalThesaurus() {
  String source="查验车位增加后，通行能力不断提高。";
  String formal="核验工位扩增，通行效率层级攀升。";
  String spoken="查验用的车位多了，通行能力也不断增强。";
  var ai=org.mockito.Mockito.mock(com.dropai.rewrite.service.AiRewriteService.class);
  org.mockito.Mockito.when(ai.rewriteWithFeedback(org.mockito.ArgumentMatchers.anyString(),org.mockito.ArgumentMatchers.anyString(),org.mockito.ArgumentMatchers.anyInt(),org.mockito.ArgumentMatchers.anyString())).thenReturn(formal,spoken);
  var workflow=new DefaultWorkflowRewriteService(ai,new com.dropai.rewrite.service.TextStructureProtector());
  assertThat(workflow.execute(source,"humanize@PAPERPASS").getRewrittenText()).isEqualTo(spoken);
  org.mockito.Mockito.verify(ai,org.mockito.Mockito.times(2)).rewriteWithFeedback(org.mockito.ArgumentMatchers.eq(source),org.mockito.ArgumentMatchers.anyString(),org.mockito.ArgumentMatchers.anyInt(),org.mockito.ArgumentMatchers.anyString());
 }
 @Test void sourceUsedFormalTermsAreExemptButMetricSubstitutionIsNot() {
  assertThat(OrdinaryFullRebuild.formalUpgrades("研究结果显示各个层级已有核验流程。", "研究结果显示，各个层级核验步骤已明确。")).isEmpty();
  assertThat(OrdinaryFullRebuild.formalUpgrades("通行能力改善，温度异常减少。", "通行效率提升，温度超标减少。"))
    .contains("通行效率", "温度超标");
 }

 @Test void registerPreferenceAlsoRetainsAdviceAndDegree() {
  assertThat(OrdinaryFullRebuild.formalUpgrades("鼓励企业完善记录，明确隔离程序。主要不是场地不足，可减少线下提交。", "我们也明确了隔离程序，根本不是场地不足，企业不用再提交。"))
   .contains("建议→已明确", "主要不是→根本不是", "减少→不用再");
 }

 @Test void shorteningNeverOutranksARealLexicalRewrite() {
  String source="车辆等待查验，人员监测温度，部门协同安排运输。".repeat(4);
  String near=source.replace("安排","组织");
  String dense="车子等着检查，工作人员盯着温度，各方配合调度运送。".repeat(4);
  assertThat(OrdinaryRewriteAmplitude.needsLexicalRetry(source,near)).isTrue();
  assertThat(OrdinaryRewriteAmplitude.needsLexicalRetry(source,dense)).isFalse();
  assertThat(OrdinaryFullRebuild.preferCandidate(source,dense,near)).isTrue();
  assertThat(OrdinaryFullRebuild.preferCandidate(source,near,dense)).isFalse();
 }
 @Test void paddingSignalsAreWordingBasedAndSourceTermsAreExempt() {
  String padded="通行能力一直都在提高当中，和上一年同期相比增长了20%，为这些方向提供有用的参考。";
  assertThat(OrdinaryFullRebuild.paddingPhrases("通行能力不断提高，同比增长20%。",padded))
   .contains("一直都在提高当中","和上一年同期相比增长了","有用的参考");
  assertThat(OrdinaryFullRebuild.paddingPhrases(padded,padded)).isEmpty();
  String source="运货车辆需要等候。";
  String longOutput="货车先排队，待工作人员检查、登记单据、协调装卸与后续交接安排完成后通行。";
  assertThat(OrdinaryRewriteAmplitude.needsSpokenRetry(source,longOutput)).isTrue();
  assertThat(OrdinaryFullRebuild.issues(source,longOutput)).isEmpty();
 }

 @Test void rivalLightRebuildBeatsThinTemplateAndMegaPadding() {
  String source="研究发现，数字管理持续提高效率。据此，本文提出改进建议，以期提供参考。";
  String thin="研究发现，数字管理持续提升效率。据此，本文给出调整建议，希望提供参考。";
  String rival="经研究发现，数字手段不断改进作业，并且减少等候。因此本文提出了改进措施，以便为后续安排提供一定的借鉴。";
  String mega="经研究发现，数字管理一直都在改善当中，和上一年同期相比增长了很多，为搭建的相关工作提供有用的参考。";
  assertThat(OrdinaryFullRebuild.templateRemnants(thin)).isNotEmpty();
  assertThat(OrdinaryFullRebuild.templateRemnants(rival)).isEmpty();
  assertThat(OrdinaryFullRebuild.preferCandidate(source,rival,thin)).isTrue();
  assertThat(OrdinaryFullRebuild.preferCandidate(source,rival,mega)).isTrue();
  assertThat(OrdinaryFullRebuild.preferCandidate(source,mega,rival)).isFalse();
  assertThat(OrdinaryFullRebuild.diagnostics(source,thin)).anyMatch(x->x.contains("模板家族残留"));
 }
 @Test void definitionsAndActionTemplatesAlsoNeedRebuildAndAwkwardWordsAreFlagged() {
  assertThat(OrdinaryFullRebuild.templateRemnants("冷链配合是供应链各方围绕温度形成的安排。")).isNotEmpty();
  assertThat(OrdinaryFullRebuild.templateRemnants("紧扣成熟度和通风，整理出一套操作指南。")).isNotEmpty();
  assertThat(OrdinaryFullRebuild.unnaturalSwaps("优化查验顺序和放行速度。","调整查点次序，提高放行进速。"))
   .contains("查点次序","放行进速");
 }
}
