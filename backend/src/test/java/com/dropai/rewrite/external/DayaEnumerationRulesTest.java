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
                "一、核对台账。二、复查现场。三、记录结果。")).isTrue();
        assertThat(DayaEnumerationRules.requiresBreak(
                "本段说明项目现场已经核实的数据和限制条件。")).isFalse();
        assertThat(DayaEnumerationRules.requiresBreak(
                "再生材料需要核验来源，先验参数不得替代实测数据。")).isFalse();
        assertThat(DayaEnumerationRules.requiresBreak(
                "第一章 相关概念与理论基础")).isFalse();
        assertThat(DayaEnumerationRules.requiresBreak(
                "计算结果见公式（1），该编号必须保留。")).isFalse();
    }

    @Test
    void acceptsARebuiltParagraphWithShortSentencesAndNoOrderingMarkers() {
        String original = "第一，明确责任。第二，核对数据。第三，记录结果。";
        String rewritten = "责任已经明确。数据逐项核对。结果当天记录。";

        assertThatCode(() -> DayaEnumerationRules.validateRewrite(original, rewritten))
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
        assertThatIllegalStateException()
                .isThrownBy(() -> DayaEnumerationRules.validateRewrite(
                        original, "责任已经明确。数据与结果一并处理。"))
                .withMessageContaining("每个原编号至少保留一句");
    }

    @Test
    void normalizesOnlyRepeatedNumericListMarkersAndKeepsSingleFormulaNumbers() {
        assertThat(DayaEnumerationRules.normalizeInlineNumericEnumeration(
                "（1）核对台账；（2）复查现场；（3）记录结果。"))
                .isEqualTo("第一项，核对台账；第二项，复查现场；第三项，记录结果。");
        assertThat(DayaEnumerationRules.normalizeInlineNumericEnumeration(
                "参数代入公式（1）后得到结果。")).isEqualTo("参数代入公式（1）后得到结果。");
    }
}
