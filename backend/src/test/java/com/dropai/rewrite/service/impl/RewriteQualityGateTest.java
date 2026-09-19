package com.dropai.rewrite.service.impl;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RewriteQualityGateTest {

    @Test
    void rejectsSynonymOnlyLongRewrite() {
        String original = "带式输送机主要由驱动装置、传动滚筒、改向滚筒、托辊、机架和张紧装置组成，各部分共同完成物料的连续输送。";
        String candidate = "带式输送机主要由驱动设备、传动滚筒、改向滚筒、托辊、机架和张紧装置构成，各部分共同完成物料的连续运输。";

        RewriteQualityGate.Assessment result = RewriteQualityGate.assess(original, candidate);

        assertThat(result.accepted()).isFalse();
        assertThat(result.issues()).anyMatch(issue -> issue.contains("连续相同") || issue.contains("重合率"));
    }

    @Test
    void acceptsProjectSpecificStructuralRewrite() {
        String original = "带式输送机主要由驱动装置、传动滚筒、改向滚筒、托辊、机架和张紧装置组成，各部分共同完成物料的连续输送。";
        String candidate = "动力经减速器送至主动滚筒，输送带随之循环。托辊承接带体及物料载荷，改向滚筒调整回程方向，张紧装置补偿运行伸长。";

        RewriteQualityGate.Assessment result = RewriteQualityGate.assess(original, candidate);

        assertThat(result.accepted()).as("%s source=%s candidate=%s", result.issues(),
                RewriteQualityGate.immutableTokenCounts(original),
                RewriteQualityGate.immutableTokenCounts(candidate)).isTrue();
        assertThat(result.fourGramRatio()).isLessThanOrEqualTo(0.30);
    }

    @Test
    void masksTechnicalValuesWhenMeasuringOverlap() {
        String original = "YX132M1-6电机采用380V、50Hz和4kW参数，额定效率为88.27%，功率因数为0.781。";
        String candidate = "YX132M1-6的额定工况计算采用4kW输出功率。供电条件保持380V和50Hz，所得效率为88.27%，对应功率因数0.781。";

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
}
