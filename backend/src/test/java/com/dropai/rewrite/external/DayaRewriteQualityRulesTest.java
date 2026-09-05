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
    void detectsReportSevenPolishedProcessAndResponsibilityChains() {
        String polished = "站在施工质量管控立场上，材料对应进场记录，设备对应验收资料，"
                + "项目部负责复核，形成资料—指标—评分—结论的完整路径，确保结果可核对，"
                + "实现证据归档并为后续评价提供支撑。";

        assertThat(DayaRewriteQualityRules.assess(
                "现场资料用于核对施工质量。", polished, "3.3 指标体系").risks())
                .contains(
                        DayaRewriteQualityRules.Risk.POLISHED_META_OPENING,
                        DayaRewriteQualityRules.Risk.ROLE_ACTION_CHAIN,
                        DayaRewriteQualityRules.Risk.OUTCOME_CLAIM_CHAIN,
                        DayaRewriteQualityRules.Risk.SCHEMA_CHAIN);
    }

    @Test
    void contextualRiskMustActuallyFallAndCompressionAloneIsNotAnImprovement() {
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
                original, firstDraft, compressed, "中文摘要")).isFalse();
    }

    @Test
    void detectsReportSevenUnnumberedMappingArgumentAndDataChains() {
        String mapping = "目标输入决定评价范围，资源投入决定现场条件，检测验证反映质量结果，"
                + "组织制度属于管理基础，各项材料用于核对项目事实。";
        String argument = "现有资料看似完整，但部分记录却没有签字。若仅查看汇总表就难以确认现场情况，"
                + "也难以判断差额来源，因此本段只保留能够核实的结论。";
        String data = "表中权重为[[DROP_AI_PROTECTED_1]]，得分为[[DROP_AI_PROTECTED_2]]，"
                + "隶属度为[[DROP_AI_PROTECTED_3]]，排序又给出[[DROP_AI_PROTECTED_4]]，随后逐项解释评价结果。";

        assertThat(DayaRewriteQualityRules.assess("原段。", mapping, "3.3 指标体系").risks())
                .contains(DayaRewriteQualityRules.Risk.ROLE_ACTION_CHAIN);
        assertThat(DayaRewriteQualityRules.assess("原段。", argument, "正文").risks())
                .contains(DayaRewriteQualityRules.Risk.ARGUMENT_CLOSURE_CHAIN);
        assertThat(DayaRewriteQualityRules.assess("原段。", data, "表格长说明").risks())
                .contains(DayaRewriteQualityRules.Risk.RESULT_DATA_CHAIN);
    }

    @Test
    void detectsCommaAndConjunctionEnumerationButLeavesOrdinaryTransitionAlone() {
        assertThat(DayaRewriteQualityRules.hasImplicitEnumeration(
                "研究内容包括投资决策，设计控制以及结算审核。"))
                .isTrue();
        assertThat(DayaRewriteQualityRules.hasImplicitEnumeration(
                "研究内容包括投资决策、设计控制和结算审核。"))
                .isTrue();
        assertThat(DayaRewriteQualityRules.hasImplicitEnumeration(
                "本项目包括BIM技术，现场数据仍以验收记录为准。"))
                .isFalse();
    }

    @Test
    void requiredGateRejectsAnUnchangedConcreteLowRiskParagraph() {
        String original = "雨后两天，监理在北侧基坑发现一处积水。";

        assertThatIllegalStateException()
                .isThrownBy(() -> DayaRewriteQualityRules.validateRequiredRewrite(original, original))
                .withMessageContaining("未重建段落表达");
    }

    @Test
    void riskLabelsDoNotExemptALongConcreteParagraphFromSubstantiveRewrite() {
        String concrete = "六月十二日雨停后，北侧基坑仍有积水，监理在照片上圈出了位置。"
                + "当天的施工记录写明抽水泵从下午两点开始工作，傍晚复查时水位已经下降，"
                + "现场人员把同一组照片和记录放回原档案袋，第二天只补签了缺少的日期。"
                + "南侧道路仍可通行，围挡没有移动。材料车在原定入口停了十分钟，门卫在纸上写下车牌，照片里的天空已经放晴。";

        assertThat(DayaRewriteQualityRules.assess(concrete, concrete, "3.2 现场记录").risks())
                .contains(DayaRewriteQualityRules.Risk.LONG_STRUCTURED_BODY);
        assertThatIllegalStateException()
                .isThrownBy(() -> DayaRewriteQualityRules.validateRequiredRewrite(concrete, concrete))
                .withMessageContaining("未重建段落表达");
    }

    @Test
    void sectionContextMarksACompleteMethodChainForTargetedReview() {
        String original = "本节说明AHP在项目中的使用情况。";
        String tutorial = "AHP用于建立指标模型，判断矩阵计算权重并完成一致性检验。"
                + "权重与隶属度组合后产生风险等级，评价结果用于项目排序。项目据此确认重点风险。";

        assertThat(DayaRewriteQualityRules.assess(
                original, tutorial, "2.2 AHP评价方法").risks())
                .contains(DayaRewriteQualityRules.Risk.METHOD_TUTORIAL_CHAIN);
    }

    @Test
    void finalGateRejectsTwoRedPatternsFromReportSeven() {
        String polishedArgument = "从福建林业职业技术学院已公开的建设资料来看，A楼与B楼已开工建设。"
                + "尽管公开信息能够核验建设背景，但网页并未披露验收结果，因而无法据此推断实体质量等级。";
        String mapping = "图2-1按质量逻辑把指标归入五个领域。B1对应管理责任，B2覆盖材料设备，"
                + "B3对应实体质量，B4反映现场环境，B5用于检测和资料闭环。依托这套指标结构，结果形成责任清单。";

        assertThatIllegalStateException()
                .isThrownBy(() -> DayaRewriteQualityRules.validateFinal(
                        "公开资料只说明项目已经开工，网页没有验收结果。", polishedArgument, "3.1 项目概况"))
                .withMessageContaining("完整报告链");
        assertThatIllegalStateException()
                .isThrownBy(() -> DayaRewriteQualityRules.validateFinal(
                        "图中给出了指标分组。", mapping, "3.3 指标体系"))
                .withMessageContaining("完整报告链");
    }

    @Test
    void finalGateKeepsAnEmpiricallyUnmarkedMethodParagraph() {
        String original = "本段讨论绿色建筑施工质量评价的层级指标、量化与定性判断、施工证据状态，"
                + "并说明AHP和模糊综合评价在本项目中的用途。";
        String unmarked = "绿色建筑施工质量评价覆盖目标层—准则层—指标层的多层级指标体系，"
                + "结构安全、材料性能等可通过实测数据完成量化判定，制度执行、协同管理等内容需要结合定性分析。"
                + "评价证据会随施工推进逐步形成，部分事项仍处于已明确要求但尚未验证的状态。"
                + "AHP用于确定指标的重要程度，模糊综合评价处理等级边界和多源信息。";

        assertThatCode(() -> DayaRewriteQualityRules.validateFinal(
                original, unmarked, "2.1 AHP-模糊综合评价方法"))
                .doesNotThrowAnyException();
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

    @Test
    void finalGateRejectsNearSimilarityAfterTheReviewStages() {
        String original = "县域水利部门依托现场台账建立了造价审核流程，工作人员结合施工图纸、验收记录和签证资料核对工程量，确认结果后保存全部复核依据。";
        String nearSynonym = "县域水利部门依托现场台账建立了造价审核流程，工作人员结合施工图纸、验收记录和签证资料核对工程量，确认结果后保存全部复核材料。";
        String severalSynonyms = "县域水利部门借助现场台账建立起造价审核流程，工作人员结合施工图纸、验收记录和签证资料复核工程量，确认结论后保存全部复核依据。";

        assertThat(DayaRewriteQualityRules.assess(original, nearSynonym).risks())
                .contains(DayaRewriteQualityRules.Risk.HIGH_SIMILARITY);
        assertThatIllegalStateException()
                .isThrownBy(() -> DayaRewriteQualityRules.validateFinal(original, nearSynonym))
                .withMessageContaining("高度相似");
        assertThat(DayaRewriteQualityRules.assess(original, severalSynonyms).risks())
                .contains(DayaRewriteQualityRules.Risk.HIGH_SIMILARITY);
        assertThatIllegalStateException()
                .isThrownBy(() -> DayaRewriteQualityRules.validateFinal(original, severalSynonyms))
                .withMessageContaining("高度相似");
    }

    @Test
    void requiredGateRejectsOneWordReplacementInAShortParagraph() {
        String original = "现场资料归入项目档案，复核意见仍留在台账中。";
        String oneWordReplacement = "现场资料放入项目档案，复核意见仍留在台账中。";
        String rebuilt = "台账留着复核意见，现场材料另存项目档案。";
        String parallelOriginal = "建设单位核对现场资料，监理单位复查台账。";
        String localNounReplacement = "建设方核对现场材料，监理方复查工作台账。";

        assertThatIllegalStateException()
                .isThrownBy(() -> DayaRewriteQualityRules.validateRequiredRewrite(
                        original, oneWordReplacement))
                .withMessageContaining("高度相似");
        assertThatIllegalStateException()
                .isThrownBy(() -> DayaRewriteQualityRules.validateRequiredRewrite(
                        parallelOriginal, localNounReplacement))
                .withMessageContaining("高度相似");
        assertThatCode(() -> DayaRewriteQualityRules.validateRequiredRewrite(original, rebuilt))
                .doesNotThrowAnyException();
    }

    @Test
    void requiredGateAcceptsAShortFactRebuildThatMovesProtectedNumbers() {
        String original = "项目投资5000万元，工期为30天。";
        String rebuilt = "工期定为30天，项目投资仍是5000万元。";

        assertThatCode(() -> DayaRewriteQualityRules.validateRequiredRewrite(original, rebuilt))
                .doesNotThrowAnyException();
    }

    @Test
    void requiredGateRejectsCopyingTheOriginalIntoDifferentLengthText() {
        String original = "监理查看施工图和现场签证，工程量复核意见记在当天台账中，相关凭证仍由项目部保存。";
        String copiedThenExpanded = original + "这些材料以后还可继续查看。";
        String copiedExcerpt = "监理查看施工图和现场签证，工程量复核意见记在当天台账中。";

        assertThatIllegalStateException()
                .isThrownBy(() -> DayaRewriteQualityRules.validateRequiredRewrite(
                        original, copiedThenExpanded))
                .withMessageContaining("高度相似");
        assertThatIllegalStateException()
                .isThrownBy(() -> DayaRewriteQualityRules.validateRequiredRewrite(
                        original, copiedExcerpt))
                .withMessageContaining("高度相似");
    }
}
