package com.dropai.rewrite.external;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

class DayaEnumerationRulesTest {

    @Test
    void detectsTextualAndRepeatedNumericEnumerations() {
        assertThat(DayaEnumerationRules.requiresBreak(
                "第一，核对台账。第二，复查现场。第三，记录结果。")).isTrue();
        assertThat(DayaEnumerationRules.requiresBreak(
                "第一阶段核对台账。第二阶段复查现场。第三阶段记录结果。")).isTrue();
        assertThat(DayaEnumerationRules.requiresBreak(
                "（1）核对台账；（2）复查现场；（3）记录结果。")).isTrue();
        assertThat(DayaEnumerationRules.requiresBreak(
                "核对台账；复查现场并记录结果。")).isTrue();
        assertThat(DayaEnumerationRules.requiresBreak(
                "甲".repeat(130) + "；" + "乙".repeat(130))).isTrue();
        assertThat(DayaEnumerationRules.requiresBreak(
                "The source is checked; the result is recorded.")).isFalse();
        assertThat(DayaEnumerationRules.requiresBreak(
                "一、核对台账。二、复查现场。三、记录结果。")).isTrue();
        assertThat(DayaEnumerationRules.requiresBreak(
                "第一个方面核对台账。第二个方面复查现场。")).isTrue();
        assertThat(DayaEnumerationRules.requiresBreak(
                "第1项核对台账。第2项复查现场。")).isTrue();
        assertThat(DayaEnumerationRules.requiresBreak(
                "1）核对台账；2）复查现场；3）记录结果。")).isTrue();
        assertThat(DayaEnumerationRules.requiresBreak(
                "先由监理核实，再经建设单位复核，最后报主管部门审批。")).isFalse();
        assertThat(DayaEnumerationRules.requiresBreak(
                "先收集资料，再分析问题，接着提出措施。")).isFalse();
        assertThat(DayaEnumerationRules.requiresBreak(
                "先复查资料，再确认费用。")).isFalse();
        assertThat(DayaEnumerationRules.requiresReview(
                "先由监理核实，再经建设单位复核，最后报主管部门审批。",
                "监理核实资料，建设单位复核费用，主管部门按权限审批。")).isTrue();
        assertThat(DayaEnumerationRules.requiresBreak(
                "本段说明项目现场已经核实的数据和限制条件。")).isFalse();
        assertThat(DayaEnumerationRules.requiresBreak(
                "再生材料需要核验来源，先验参数不得替代实测数据。")).isFalse();
        assertThat(DayaEnumerationRules.requiresBreak(
                "第一章 相关概念与理论基础")).isFalse();
        assertThat(DayaEnumerationRules.requiresBreak(
                "计算结果见公式（1），该编号必须保留。")).isFalse();
        assertThat(DayaEnumerationRules.requiresBreak(
                "第三方机构核对结果，第二家咨询机构复查费用。")).isFalse();
        assertThat(DayaEnumerationRules.requiresBreak(
                "首先分析项目背景，并说明现场的实际情况。")).isFalse();
        assertThat(DayaEnumerationRules.requiresBreak(
                "先验参数与再生材料均以现场记录为准。")).isFalse();
        assertThat(DayaEnumerationRules.itemCount(
                "第一项核对材料。第二项在验收之后结算。")).isEqualTo(2);
        assertThat(DayaEnumerationRules.requiresReview(
                "首先分析项目背景，并说明现场的实际情况。",
                "项目背景和现场情况已经说明清楚。")).isTrue();
    }

    @Test
    void reviewsOrderingWordsKeptOrIntroducedInOrdinaryDrafts() {
        assertThat(DayaEnumerationRules.requiresReview(
                "文章分析了项目造价。", "文章首先分析项目造价，然后说明处理办法。")).isTrue();
        assertThat(DayaEnumerationRules.requiresReview(
                "文章首先分析项目造价。", "项目造价情况已经分析清楚。")).isTrue();
        assertThat(DayaEnumerationRules.requiresReview(
                "第三方机构复查费用。", "有资质的外部机构复查费用。")).isFalse();

        assertThatIllegalStateException()
                .isThrownBy(() -> DayaEnumerationRules.validateRewrite(
                        "文章分析项目造价。", "文章首先分析项目造价，然后说明处理办法。"))
                .withMessageContaining("文本型顺序标记");
    }

    @Test
    void keepsLoneFactualOrdinalsButReviewsGroupedOrdinalSequences() {
        String[] factualOrdinals = {
                "第二家咨询机构复查费用。",
                "第二轮检测使用同一批样本。",
                "第二次复核发现记录一致。",
                "第二层楼面保留原始标高。",
                "第二阶段施工按计划完成。"
        };
        for (String factualOrdinal : factualOrdinals) {
            assertThat(DayaEnumerationRules.requiresBreak(factualOrdinal)).isFalse();
            assertThat(DayaEnumerationRules.requiresReview(factualOrdinal, factualOrdinal)).isFalse();
            assertThatCode(() -> DayaEnumerationRules.validateRewrite(
                    factualOrdinal, factualOrdinal)).doesNotThrowAnyException();
        }
        String combinedFacts = "第二家咨询机构在第二轮检测中完成第二次复核。";
        assertThat(DayaEnumerationRules.requiresBreak(combinedFacts)).isFalse();
        assertThat(DayaEnumerationRules.requiresReview(combinedFacts, combinedFacts)).isFalse();
        assertThatCode(() -> DayaEnumerationRules.validateRewrite(
                combinedFacts, combinedFacts)).doesNotThrowAnyException();

        String grouped = "第二轮检测核对材料。第三轮检测复查现场。";
        assertThat(DayaEnumerationRules.requiresBreak(grouped)).isTrue();
        assertThat(DayaEnumerationRules.requiresReview(grouped, grouped)).isTrue();
        assertThatIllegalStateException()
                .isThrownBy(() -> DayaEnumerationRules.validateRewrite(grouped, grouped))
                .withMessageContaining("文本型顺序标记");
    }

    @Test
    void acceptsARebuiltParagraphWithShortSentencesAndNoOrderingMarkers() {
        String original = "第一，明确责任。第二，核对数据。第三，记录结果。";
        String rewritten = "责任已经明确。数据逐项核对。结果当天记录。";

        assertThatCode(() -> DayaEnumerationRules.validateRewrite(original, rewritten))
                .doesNotThrowAnyException();
        assertThatCode(() -> DayaEnumerationRules.validateRewrite(
                original, "责任和记录已经合并处理。"))
                .doesNotThrowAnyException();
        assertThatCode(() -> DayaEnumerationRules.validateRewrite(
                "先由监理核实，再经建设单位复核，最后报主管部门审批。",
                "监理核实原始资料并标出缺项，建设单位据此复核费用，主管部门按权限完成审批。"))
                .doesNotThrowAnyException();
        assertThatCode(() -> DayaEnumerationRules.validateRewrite(
                "先由监理核实，再经建设单位复核，最后报主管部门审批。",
                "监理核实资料，建设单位和主管部门分别完成复核与审批。"))
                .doesNotThrowAnyException();
    }

    @Test
    void rejectsResidualOrderingMarkersOrChineseSentencesOverTwentyCharacters() {
        String original = "一是明确责任，二是核对数据，三是记录结果。";

        assertThatIllegalStateException()
                .isThrownBy(() -> DayaEnumerationRules.validateRewrite(
                        original, "首先明确责任。随后核对数据。结果当天记录。"))
                .withMessageContaining("文本型顺序标记");
        assertThatIllegalStateException()
                .isThrownBy(() -> DayaEnumerationRules.validateRewrite(
                        original, "项目经理每天在现场逐项核对全部台账并记录所有处理结果。"))
                .withMessageContaining("超过 20 个汉字");
        assertThatCode(() -> DayaEnumerationRules.validateRewrite(
                original, "责任已经明确。数据与结果一并处理。"))
                .doesNotThrowAnyException();
        assertThatIllegalStateException()
                .isThrownBy(() -> DayaEnumerationRules.validateRewrite(
                        "核对台账；复查现场并记录结果。", "台账已核对；现场结果也有记录。"))
                .withMessageContaining("文本型顺序标记");
    }

    @Test
    void recognizesStyledOrderingRunsWithoutTreatingRealFactsAsMarkers() {
        assertThat(DayaEnumerationRules.isPureOrderingMarkerRun("第一，")).isTrue();
        assertThat(DayaEnumerationRules.isPureOrderingMarkerRun("（2）")).isTrue();
        assertThat(DayaEnumerationRules.isPureOrderingMarkerRun("其次：")).isTrue();
        assertThat(DayaEnumerationRules.isPureOrderingMarkerRun("第一章")).isFalse();
        assertThat(DayaEnumerationRules.isPureOrderingMarkerRun("三层建筑")).isFalse();
    }

    @Test
    void normalizesOnlyRepeatedNumericListMarkersAndKeepsSingleFormulaNumbers() {
        assertThat(DayaEnumerationRules.normalizeInlineNumericEnumeration(
                "（1）核对台账；（2）复查现场；（3）记录结果。"))
                .isEqualTo("第一项，核对台账；第二项，复查现场；第三项，记录结果。");
        assertThat(DayaEnumerationRules.normalizeInlineNumericEnumeration(
                "第1项核对台账。第2项复查现场。"))
                .isEqualTo("第一项，核对台账。第二项，复查现场。");
        assertThat(DayaEnumerationRules.normalizeInlineNumericEnumeration(
                "第1项核对台账。"))
                .isEqualTo("第一项，核对台账。");
        assertThat(DayaEnumerationRules.requiresReview(
                "项目需要核对台账。", "第一项，核对台账。")).isTrue();
        assertThatIllegalStateException()
                .isThrownBy(() -> DayaEnumerationRules.validateRewrite(
                        "项目需要核对台账。", "第一项，核对台账。"))
                .withMessageContaining("文本型顺序标记");
        assertThat(DayaEnumerationRules.normalizeInlineNumericEnumeration(
                "参数代入公式（1）后得到结果。")).isEqualTo("参数代入公式（1）后得到结果。");
        assertThat(DayaEnumerationRules.normalizeInlineNumericEnumeration(
                "第3层楼面保留原始标高。")).isEqualTo("第3层楼面保留原始标高。");
        assertThat(DayaEnumerationRules.normalizeInlineNumericEnumeration(
                "第2阶段施工按计划完成。")).isEqualTo("第2阶段施工按计划完成。");
    }
}
