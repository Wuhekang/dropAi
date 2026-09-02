package com.dropai.rewrite.external;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

class DayaRewriteQualityRulesTest {

    @Test
    void rejectsRepeatedPhrasesAndDoublePunctuation() {
        assertThatIllegalStateException()
                .isThrownBy(() -> DayaRewriteQualityRules.validateRewrite(
                        "监理核对签证。", "监理核对所有的所有签证。", false))
                .withMessageContaining("重复");
        assertThatIllegalStateException()
                .isThrownBy(() -> DayaRewriteQualityRules.validateRewrite(
                        "这套理论可用于核算。", "这套这套理论可用于核算。。", false))
                .withMessageContaining("重复");
        assertThatIllegalStateException()
                .isThrownBy(() -> DayaRewriteQualityRules.validateRewrite(
                        "体系已被行业认可。", "体系已被行业认可。。", false))
                .withMessageContaining("标点");
    }

    @Test
    void rejectsNewRunsOfMechanicalShortSentencesOnlyInOrdinaryProse() {
        String original = "项目已经完成现场核查，监理人员逐项看过原始记录，并在当天补齐了缺少的签字和日期。";
        String mechanical = "项目完成核查。监理查看记录。当天补齐签字。日期也已补全。";

        assertThatIllegalStateException()
                .isThrownBy(() -> DayaRewriteQualityRules.validateRewrite(
                        original, mechanical, false))
                .withMessageContaining("连续整齐短句");
        assertThatCode(() -> DayaRewriteQualityRules.validateRewrite(
                original, mechanical, true)).doesNotThrowAnyException();
    }

    @Test
    void acceptsVariedPlainSentencesAndLegitimateRepeatedCharacters() {
        assertThatCode(() -> DayaRewriteQualityRules.validateRewrite(
                "费用按月核对。", "费用由专人按月核对，发现差额后再查看原始凭证。人人都要在记录上签字。", false))
                .doesNotThrowAnyException();
        assertThatCode(() -> DayaRewriteQualityRules.validateRewrite(
                "本工程的工程造价已有记录。", "已有研究的研究对象包括工程造价。", false))
                .doesNotThrowAnyException();
    }

    @Test
    void detectsPunctuationOnlyAndNearSynonymMicroEdits() {
        String original = "县域水利部门依托现场台账建立了造价审核流程，工作人员结合施工图纸、验收记录和签证资料核对工程量，确认结果后保存全部复核依据。";
        String punctuationOnly = "县域水利部门依托现场台账建立了造价审核流程。工作人员结合施工图纸、验收记录和签证资料核对工程量。确认结果后保存全部复核依据。";
        String nearSynonym = "县域水利部门借助现场台账建立起造价审核流程，工作人员结合施工图纸、验收记录和签证资料复核工程量，确认结论后保存全部复核依据。";

        assertThat(DayaRewriteQualityRules.assess(original, punctuationOnly).risks())
                .contains(DayaRewriteQualityRules.Risk.HIGH_SIMILARITY);
        assertThat(DayaRewriteQualityRules.assess(original, nearSynonym).risks())
                .contains(DayaRewriteQualityRules.Risk.HIGH_SIMILARITY);
    }

    @Test
    void detectsImplicitEnumerationAndUniformPredicateSentences() {
        assertThat(DayaRewriteQualityRules.hasImplicitEnumeration(
                "研究内容主要包括投资决策、设计控制、招标管理和结算审核。"))
                .isTrue();
        assertThat(DayaRewriteQualityRules.hasImplicitEnumeration(
                "风险分为三类：重复计价；高套定额；签证资料不全。"))
                .isTrue();
        assertThat(DayaRewriteQualityRules.hasImplicitEnumeration(
                "本项目的模型包括BIM技术，现场数据仍以验收记录为准。"))
                .isFalse();
        assertThat(DayaRewriteQualityRules.hasIsomorphicShortSentences(
                "投资估算需要现场复核。设计限额需要现场复核。签证台账需要现场复核。"))
                .isTrue();
        assertThat(DayaRewriteQualityRules.hasIsomorphicShortSentences(
                "投资估算由造价员复核。发现地质资料存在缺口时，项目负责人会补做现场勘察。签证台账在结算前交给审计人员。"))
                .isFalse();
    }

    @Test
    void detectsOnlyACompleteFormulaicResearchChain() {
        String formulaic = "本文围绕施工阶段风险开展研究，并基于现场记录构建评价模型。"
                + "结果显示管理流程仍有不足，据此提出改进措施，为后续项目提供参考。";

        assertThat(DayaRewriteQualityRules.hasFormulaicResearchChain(formulaic)).isTrue();
        assertThat(DayaRewriteQualityRules.assess("现场记录反映了审批滞后。", formulaic).risks())
                .contains(DayaRewriteQualityRules.Risk.FORMULAIC_RESEARCH_CHAIN);
        assertThat(DayaRewriteQualityRules.hasFormulaicResearchChain(
                "结果显示，雨季停工使现场进度比原计划晚了十二天。"))
                .isFalse();
    }

    @Test
    void reportsLowerRiskOnlyAfterARealStructuralRebuild() {
        String original = "县域水利部门依托现场台账建立了造价审核流程，工作人员结合施工图纸、验收记录和签证资料核对工程量，确认结果后保存全部复核依据。";
        String firstDraft = "县域水利部门依托现场台账建立了造价审核流程。工作人员结合施工图纸、验收记录和签证资料核对工程量。确认结果后保存全部复核依据。";
        String synonymDraft = "县域水利部门借助现场台账建立起造价审核流程。工作人员结合施工图纸、验收记录和签证资料复核工程量。确认结论后保存全部复核依据。";
        String rebuilt = "复核依据与台账一并归档。项目负责人回到施工图和验收记录核实工程量，现场签证只用于确认施工事实。县域水利部门据此完成造价审核。";

        assertThat(DayaRewriteQualityRules.hasLowerRisk(original, firstDraft, synonymDraft)).isFalse();
        assertThat(DayaRewriteQualityRules.hasLowerRisk(original, firstDraft, rebuilt)).isTrue();
    }

    @Test
    void usesSectionContextToFlagCompleteDayaModulesForASecondPass() {
        String abstractDraft = "本文以学校改扩建工程为对象，采用AHP和模糊综合评价分析施工风险。"
                + "结果显示现场签证和进度管理仍有不足，据此提出整改建议，为同类项目提供参考。";
        String methodDraft = "AHP先建立指标模型，再通过判断矩阵计算权重，并进行一致性检验。"
                + "随后把权重与隶属度组合，得到各风险等级的评价结果，最后用于项目风险排序。";
        String sopDraft = "项目部明确责任人员并建立台账，现场数据达到阈值后发出预警。"
                + "监理记录问题，施工单位完成整改，复核结果归档后再纳入月度考核。";
        String tableDraft = "表中评分说明了各指标的差异，多个数值依次给出权重和排名。"
                + "数据显示中间档位较为集中，各项均值又被逐一解释，最后说明总体趋势。";

        assertThat(DayaRewriteQualityRules.assess("原摘要。", abstractDraft, "中文摘要").risks())
                .contains(DayaRewriteQualityRules.Risk.ABSTRACT_MODULE_CHAIN);
        assertThat(DayaRewriteQualityRules.assess("原方法。", methodDraft, "2.2 AHP评价方法").risks())
                .contains(DayaRewriteQualityRules.Risk.METHOD_TUTORIAL_CHAIN);
        assertThat(DayaRewriteQualityRules.assess("原措施。", sopDraft, "4.3 风险控制措施").risks())
                .contains(DayaRewriteQualityRules.Risk.SOP_CONTROL_CHAIN);
        assertThat(DayaRewriteQualityRules.assess("原说明。", tableDraft, "表格长说明").risks())
                .contains(DayaRewriteQualityRules.Risk.TABLE_EXPLANATION_CHAIN);
    }

    @Test
    void contextualRiskComparisonPrefersACompressedAbstract() {
        String original = "本文以学校改扩建工程为对象，采用AHP分析施工风险。结果显示现场管理仍有不足，据此提出整改建议，为同类项目提供参考。";
        String firstDraft = "本文以学校改扩建工程为对象，采用AHP评价施工风险。结果表明现场管理存在不足，并提出整改建议，为类似工程提供参考。";
        String reviewed = "现场记录暴露出管理漏洞，AHP只用于核对风险轻重。";

        assertThat(DayaRewriteQualityRules.hasLowerRisk(
                original, firstDraft, reviewed, "中文摘要")).isTrue();
    }

    @Test
    void permitsExpansionOnlyForAConcreteDayaRiskAndHonorsTheBudgetStopMarker() {
        String ordinary = "现场记录由项目负责人核对，确认结果后写入原有台账。";
        String structured = "第一，核对台账。第二，复查现场。第三，记录结果。";
        String abstractChain = "本文以学校改扩建工程为对象，采用AHP分析施工风险。"
                + "结果显示现场管理仍有不足，据此提出整改建议，为同类项目提供参考。";

        assertThat(DayaRewriteQualityRules.assess(ordinary, ordinary, "正文").risks())
                .containsExactly(DayaRewriteQualityRules.Risk.HIGH_SIMILARITY);
        assertThat(DayaRewriteQualityRules.isExpansionEligible(ordinary, "正文")).isFalse();
        assertThat(DayaRewriteQualityRules.isExpansionEligible(structured, "4.2 风险控制")).isTrue();
        assertThat(DayaRewriteQualityRules.isExpansionEligible(abstractChain, "中文摘要")).isTrue();
        assertThat(DayaRewriteQualityRules.isExpansionEligible(
                structured, "4.2 风险控制；扩写预算已满：仅重构句式，不增加字数")).isFalse();
    }

    @Test
    void contextualTieBreakerAcceptsASecondMeaningfulCompressionButNotASynonymSwap() {
        String original = "本文以学校改扩建工程为对象，项目资料来自施工记录、签证台账和验收文件。"
                + "研究采用AHP评价施工风险，结果显示进度和现场管理仍有明显不足，随后提出整改建议。"
                + "这些内容用于说明项目当前情况，并为同类工程提供参考。";
        String firstDraft = "本研究以学校改扩建工程为对象，资料取自施工记录、签证台账和验收文件。"
                + "采用AHP评价施工风险，结果表明进度和现场管理存在明显不足，据此给出整改建议。"
                + "相关内容说明了项目现状，也能为类似工程提供参考。";
        String synonymDraft = "本研究以学校改扩建项目为对象，资料来自施工记录、签证台账和验收文件。"
                + "采用AHP评估施工风险，结果表明进度与现场管理存在明显不足，据此给出整改建议。"
                + "相关内容说明项目现状，也可为类似工程提供参考。";
        String compressed = "本研究记录学校改扩建工程的施工情况，并用AHP核对风险。"
                + "结果显示进度和现场管理仍有不足，整改建议只针对这些现场问题，相关资料供同类工程参考。";

        assertThat(DayaRewriteQualityRules.hasLowerRisk(
                original, firstDraft, synonymDraft, "中文摘要")).isFalse();
        assertThat(DayaRewriteQualityRules.assess(original, firstDraft, "中文摘要").risks())
                .contains(DayaRewriteQualityRules.Risk.ABSTRACT_MODULE_CHAIN);
        assertThat(DayaRewriteQualityRules.assess(original, compressed, "中文摘要").risks())
                .contains(DayaRewriteQualityRules.Risk.ABSTRACT_MODULE_CHAIN);
        assertThat(DayaRewriteQualityRules.hasLowerRisk(
                original, firstDraft, compressed, "中文摘要")).isTrue();
    }

    @Test
    void detectsFiveIndependentShortSentencesWithoutMatchingPredicates() {
        String fragmented = "雨季延误持续十二天。现场签证缺少日期。材料价格仍有波动。"
                + "监理意见尚未归档。结算数据尚待复核。";

        assertThat(DayaRewriteQualityRules.assess(
                "现场记录反映了项目问题。", fragmented, "第三章 风险分析").risks())
                .contains(DayaRewriteQualityRules.Risk.FRAGMENTED_LINE_CHAIN);
    }

    @Test
    void rejectsInternalLineBreaksTabsAndUnicodeSoftBreaks() {
        assertThatIllegalStateException()
                .isThrownBy(() -> DayaRewriteQualityRules.validateRewrite(
                        "项目资料已经复核。", "项目资料已经复核。\n结果当天记录。", false))
                .withMessageContaining("换行");
        assertThatIllegalStateException()
                .isThrownBy(() -> DayaRewriteQualityRules.validateFinal(
                        "项目资料已经复核。", "资料由项目负责人核对。\u2028结果当天记录。"))
                .withMessageContaining("换行");
        assertThat(DayaRewriteQualityRules.assess(
                "项目资料已经复核。", "资料已经复核。\t结果当天记录。", "正文").risks())
                .contains(DayaRewriteQualityRules.Risk.FRAGMENTED_LINE_CHAIN);
    }

    @Test
    void finalGateRejectsFakeEditsAndAcceptsARealRebuild() {
        String original = "项目负责人依据施工图核对现场签证，工程量还要结合验收记录复查，确认无误后写入台账并保存对应凭证，所有材料均由专人归档。";
        String punctuationOnly = "项目负责人依据施工图核对现场签证。工程量还要结合验收记录复查。确认无误后写入台账并保存对应凭证。所有材料均由专人归档。";
        String rebuilt = "现场签证由项目负责人核实。施工图和验收记录是工程量的复查依据，台账写明确认结果，相关凭证交由专人归档。";

        assertThatIllegalStateException()
                .isThrownBy(() -> DayaRewriteQualityRules.validateFinal(original, punctuationOnly))
                .withMessageContaining("标点或空白");
        assertThatCode(() -> DayaRewriteQualityRules.validateFinal(original, rebuilt))
                .doesNotThrowAnyException();
    }
}
