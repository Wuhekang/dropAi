package com.dropai.rewrite.service.impl;

import com.dropai.rewrite.config.DoubaoProperties;
import com.dropai.rewrite.service.SkillPromptService;
import com.dropai.rewrite.service.ai.DoubaoModelRouter;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.web.client.RestClient;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Golden style check for 降重狗 order 148907, 朱雀普通模式.
 * 186 characters became 194: four sentences, softened openings, less stock fluent phrasing.
 * Keep 运行效率 and 运营成本; the competitor swapped them for 综合运作水平 and 开支.
 */
class ZhuqueOrdinaryHumanizeSampleFixtureTest {

    static final String INPUT = "近年来，随着信息技术的快速发展，数字化转型已经成为各行各业提升核心竞争力的重要路径。"
            + "本文旨在通过构建一套系统化的分析框架，深入研究相关技术在实际场景中的应用效果，从而为后续优化提供有益参考。"
            + "研究表明，通过合理配置资源并完善协同机制，可以有效提升整体运行效率，并在一定程度上降低运营成本。"
            + "与此同时，还需要进一步关注数据安全与隐私保护问题，以确保技术方案具备可持续的实践价值。";

    static final String COMPETITOR_OUTPUT = "近些年来，由于信息技术的迅速发展，数字化转型成了各个行业提高自身核心竞争力的一种途径。"
            + "本文主要是为了建立一个完整的分析体系，对相关的技术在实际的应用中产生的效果进行探究，为之后的改进提供一定的参考依据。"
            + "从已有研究可知，在合理安排资源、改善协作关系之后，可以提高企业的综合运作水平，并且也能够一定程度地削减企业的开支。"
            + "另外还要继续注意数据安全和隐私保护的问题，保证技术方案有持久的应用价值。";

    static final String PREFERRED_OUTPUT = "近些年来，由于信息技术的迅速发展，数字化转型成了各个行业提高自身核心竞争力的一种途径。"
            + "本文主要是为了建立一个完整的分析体系，对相关的技术在实际的应用中产生的效果进行探究，为之后的改进提供一定的参考依据。"
            + "从已有研究可知，在合理安排资源、改善协作关系之后，可以提高企业的运行效率，并且也能够一定程度地削减企业的运营成本。"
            + "另外还要继续注意数据安全和隐私保护的问题，保证技术方案有持久的应用价值。";

    @Test
    void order148907SoftensOpeningsAndKeepsEfficiencyFacts() {
        assertThat(INPUT).hasSize(186);
        assertThat(COMPETITOR_OUTPUT).hasSize(194);
        assertThat(PREFERRED_OUTPUT).hasSize(194);
        assertThat(zhuqueOrdinaryIssues(INPUT, PREFERRED_OUTPUT)).isEmpty();
        assertThat(zhuqueOrdinaryIssues(INPUT, COMPETITOR_OUTPUT)).containsExactly("fact drift");
        assertThat(PREFERRED_OUTPUT)
                .contains("近些年来", "由于", "本文主要是为了", "从已有研究可知", "另外还要", "一种途径")
                .contains("运行效率", "运营成本", "数字化转型", "数据安全", "隐私保护")
                .doesNotContain("本文旨在", "研究表明", "与此同时", "有效提升", "重要路径", "综合运作水平", "开支");
        assertThat(COMPETITOR_OUTPUT)
                .contains("综合运作水平", "开支")
                .doesNotContain("运行效率", "运营成本");
    }

    @Test
    void expandedFormalOrDriftedTextFailsTheSampleCheck() {
        assertThat(zhuqueOrdinaryIssues(INPUT, INPUT))
                .contains("formal opening", "stock phrasing");
        assertThat(zhuqueOrdinaryIssues(INPUT, INPUT + "综上所述，这一路径具有重要意义。"))
                .contains("expanded", "formal opening");
        String tiny = "运行效率下来了。运营成本也降了。第三句。第四句。";
        assertThat(zhuqueOrdinaryIssues(INPUT, tiny)).contains("too short");
    }

    @Test
    void ordinaryOverlayCarriesTheSampleAndOtherModesStaySeparate() throws Exception {
        SkillPromptService skills = mock(SkillPromptService.class);
        when(skills.loadSkill(anyString())).thenAnswer(invocation -> new ClassPathResource(
                "skills/" + invocation.getArgument(0) + "/SKILL.md")
                .getContentAsString(StandardCharsets.UTF_8));
        DoubaoProperties properties = new DoubaoProperties();
        DoubaoAiRewriteService service = new DoubaoAiRewriteService(
                properties, new ObjectMapper(), RestClient.builder(), skills, new DoubaoModelRouter(properties));

        String ordinary = systemPrompt(service, "humanize@ZHUQUE");
        String deep = systemPrompt(service, "深度降低AI写作痕迹@ZHUQUE");
        assertThat(ordinary)
                .contains("name: humanize-zh-academic", "profile:zhuque-ordinary-v1", "148907", "186", "194")
                .contains("本文主要是为了", "从已有研究可知", "另外还要", "运行效率", "运营成本", "综合运作水平", "开支")
                .doesNotContain("profile:zhuque-deep-v1", "profile:gezida-ai-dedicated-v1", "profile:cnki-ordinary-v1");
        assertThat(deep)
                .contains("profile:zhuque-deep-v1", "朱雀深度改写")
                .doesNotContain("148907", "profile:zhuque-ordinary-v1", "综合运作水平");
        assertThat(userPrompt(service, "humanize@ZHUQUE"))
                .contains("profile:zhuque-ordinary-v1", "148907", "运行效率", "不要改成综合运作水平、开支");
        assertThat(userPrompt(service, "深度降低AI写作痕迹@ZHUQUE")).doesNotContain("148907");
        assertThat(systemPrompt(service, "humanize@CNKI")).doesNotContain("148907");
        assertThat(systemPrompt(service, "humanize@DAYA")).doesNotContain("148907", "profile:zhuque-ordinary-v1");
        assertThat(systemPrompt(service, "rewrite@ZHUQUE")).doesNotContain("148907", "profile:zhuque-ordinary-v1");
        assertThat(userPrompt(service, "rewrite@ZHUQUE")).doesNotContain("148907");
        assertThat(systemPrompt(service, "double@ZHUQUE")).doesNotContain("148907", "profile:zhuque-ordinary-v1");
        assertThat(systemPrompt(service, "humanize-shared@ZHUQUE")).doesNotContain("148907", "profile:zhuque-ordinary-v1");
    }

    static List<String> zhuqueOrdinaryIssues(String input, String output) {
        List<String> issues = new ArrayList<>();
        if (output.length() * 100L > input.length() * 108L) {
            issues.add("expanded");
        }
        if (output.length() < Math.floor(input.length() * 0.90)) {
            issues.add("too short");
        }
        if (sentences(output) != sentences(input)) {
            issues.add("sentence count");
        }
        if (output.contains("本文旨在") || output.contains("研究表明") || output.contains("与此同时")) {
            issues.add("formal opening");
        }
        if (output.contains("有效提升") || output.contains("重要路径") || output.contains("以确保")) {
            issues.add("stock phrasing");
        }
        boolean droppedEfficiency = input.contains("运行效率") && !output.contains("运行效率");
        boolean droppedCost = input.contains("运营成本") && !output.contains("运营成本");
        if (droppedEfficiency || droppedCost) {
            issues.add("fact drift");
        }
        return issues;
    }

    private static int sentences(String text) {
        return text.split("。", -1).length - 1;
    }

    private static String systemPrompt(DoubaoAiRewriteService service, String rewriteType) throws Exception {
        var method = DoubaoAiRewriteService.class.getDeclaredMethod("systemPrompt", String.class);
        method.setAccessible(true);
        return (String) method.invoke(service, rewriteType);
    }

    private static String userPrompt(DoubaoAiRewriteService service, String rewriteType) throws Exception {
        var method = DoubaoAiRewriteService.class.getDeclaredMethod(
                "userPrompt", String.class, String.class, int.class, String.class);
        method.setAccessible(true);
        return (String) method.invoke(service, INPUT, rewriteType, 0, "");
    }
}
