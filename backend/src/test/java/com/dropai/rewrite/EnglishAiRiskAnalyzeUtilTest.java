package com.dropai.rewrite;

import com.dropai.rewrite.utils.AiRiskAnalyzeUtil;
import com.dropai.rewrite.vo.AiAnalyzeVO;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class EnglishAiRiskAnalyzeUtilTest {

    @Test
    void scoresRepeatedEnglishAcademicTemplatesHigherThanNaturalProse() {
        String templated = """
                This study aims to examine how local transport policy affects daily travel decisions. Furthermore, this study demonstrates how institutional priorities shape the available routes. Moreover, this research highlights the importance of a comprehensive framework. In conclusion, these findings demonstrate the significance of the proposed approach.
                """;
        String natural = """
                The interviews indicate that commuters adjusted their routes when bus frequency declined. Several participants instead travelled earlier, although this response was less common outside the city centre. The evidence is limited to the two districts included in the study.
                """;

        AiAnalyzeVO templatedRisk = AiRiskAnalyzeUtil.analyze(templated);
        AiAnalyzeVO naturalRisk = AiRiskAnalyzeUtil.analyze(natural);

        assertThat(templatedRisk.getScore()).isGreaterThan(naturalRisk.getScore());
        assertThat(templatedRisk.getSuggestions()).anyMatch(value -> value.contains("English"));
    }

    @Test
    void keepsExistingChineseTemplateDetection() {
        AiAnalyzeVO result = AiRiskAnalyzeUtil.analyze("首先分析问题。其次提出方案。最后完成验证。综上所述，该方案具有重要意义。");

        assertThat(result.getScore()).isGreaterThan(8);
        assertThat(result.getSuggestions()).anyMatch(value -> value.contains("首先、其次、最后"));
    }
}
