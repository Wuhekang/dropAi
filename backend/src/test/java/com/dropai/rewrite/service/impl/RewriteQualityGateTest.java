package com.dropai.rewrite.service.impl;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RewriteQualityGateTest {

    @Test
    void acceptsChangedEditableWordsWithoutAnOverlapThreshold() {
        String original = "带式输送机主要由驱动装置、传动滚筒、改向滚筒、托辊、机架和张紧装置组成，各部分共同完成物料的连续输送。";
        String candidate = "带式输送机主要由驱动设备、传动滚筒、改向滚筒、托辊、机架和张紧装置构成，各部分共同完成物料的连续运输。";

        RewriteQualityGate.Assessment result = RewriteQualityGate.assess(original, candidate);

        assertThat(result.accepted()).isTrue();
        assertThat(result.fourGramRatio()).isGreaterThan(0.45);
    }

    @Test
    void acceptsProjectSpecificStructuralRewrite() {
        String original = "带式输送机主要由驱动装置、传动滚筒、改向滚筒、托辊、机架和张紧装置组成，各部分共同完成物料的连续输送。";
        String candidate = "动力经减速器送至主动滚筒，输送带随之循环。托辊承接带体及物料载荷，改向滚筒调整回程方向，张紧装置补偿运行伸长。";

        RewriteQualityGate.Assessment result = RewriteQualityGate.assess(original, candidate);

        assertThat(result.accepted()).as("%s source=%s candidate=%s", result.issues(),
                RewriteQualityGate.immutableTokenCounts(original),
                RewriteQualityGate.immutableTokenCounts(candidate)).isTrue();
    }

    @Test
    void masksTechnicalValuesWhenMeasuringOverlap() {
        String original = "YX132M1-6电机采用380V、50Hz和4kW参数，额定效率为88.27%，功率因数为0.781。";
        String candidate = "YX132M1-6电机在380V、50Hz供电下计算，4kW为额定输出功率。所得效率为88.27%，对应功率因数0.781。";

        RewriteQualityGate.Assessment result = RewriteQualityGate.assess(original, candidate);

        assertThat(result.technical()).isTrue();
        assertThat(result.accepted()).as("%s source=%s candidate=%s", result.issues(),
                RewriteQualityGate.immutableTokenCounts(original),
                RewriteQualityGate.immutableTokenCounts(candidate)).isTrue();
    }

    @Test
    void rejectsCandidateThatChangesProtectedEngineeringEvidence() {
        String original = "YX132M1-6电机在380V条件下进行计算，结果见表3-2，变量n表示转速。";
        String candidate = "计算对象改为YX132M1-6电机，供电条件取220V；转速用n表示，结果列于表3-2。";

        RewriteQualityGate.Assessment result = RewriteQualityGate.assess(original, candidate);

        assertThat(result.accepted()).isFalse();
        assertThat(result.issues()).anyMatch(issue -> issue.contains("发生变化"));
    }

    @Test
    void rejectsChangedOrderEvenWhenAllValuesArePresent() {
        String original = "总面积3766.23 m²，其中教学楼3741.15 m²。";
        String candidate = "教学楼面积3741.15 m²，占项目总面积3766.23 m²的大部分。";

        RewriteQualityGate.Assessment result = RewriteQualityGate.assess(original, candidate);

        assertThat(result.accepted()).isFalse();
        assertThat(result.issues()).anyMatch(issue -> issue.contains("顺序改变"));
    }

    @Test
    void acceptsSmallProseChangeAroundUnchangedOrderedValues() {
        String original = "项目总面积3766.23 m²，其中教学楼3741.15 m²，面积据施工图统计。";
        String candidate = "项目占地总量为3766.23 m²，其中教学楼占3741.15 m²，数值按施工图核算。";

        assertThat(RewriteQualityGate.assess(original, candidate).accepted()).isTrue();
    }

    @Test
    void acceptsUnchangedAndPunctuationOnlyTextWhenFactsAreIntact() {
        String original = "工程量清单依据施工图编制，材料价格采用同期市场信息。";
        String candidate = "工程量清单依据施工图编制；材料价格采用同期市场信息。";

        assertThat(RewriteQualityGate.assess(original, candidate).accepted()).isTrue();
        assertThat(RewriteQualityGate.assess(original, original).accepted()).isTrue();
    }

    @Test
    void allowsUnchangedDataParagraphButRejectsChangedUnit() {
        String original = "C30：97.51 m³；C20：15.80 m³。";

        assertThat(RewriteQualityGate.assess(original, original).accepted()).isTrue();
        assertThat(RewriteQualityGate.assess(original, "C30：97.51 m²；C20：15.80 m³。").accepted())
                .isFalse();
    }

    @Test
    void rejectsBlankOutput() {
        assertThat(RewriteQualityGate.assess("施工图确定工程量。", " ").accepted()).isFalse();
    }
}
