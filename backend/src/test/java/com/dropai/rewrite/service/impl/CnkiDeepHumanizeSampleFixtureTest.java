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
 * Golden style check for 降重狗 order 148909, 知网深度改写.
 * 186 characters became 198: four sentences, deeper reopenings than ordinary,
 * closing 意义 cadence dropped. Facts stay. Ordinary still rejects this longer rewrite.
 */
class CnkiDeepHumanizeSampleFixtureTest {

    static final String INPUT = "近年来，随着信息技术的快速发展，数字化转型已经成为各行各业提升核心竞争力的重要路径。"
            + "本文旨在通过构建一套系统化的分析框架，深入研究相关技术在实际场景中的应用效果，从而为后续优化提供有益参考。"
            + "研究表明，通过合理配置资源并完善协同机制，可以有效提升整体运行效率，并在一定程度上降低运营成本。"
            + "与此同时，还需要进一步关注数据安全与隐私保护问题，以确保技术方案具备可持续的实践价值。";

    static final String COMPETITOR_OUTPUT = "近些年来，由于信息技术的迅猛发展，数字化转型成了各个行业提高自身核心竞争力的一种方式。"
            + "本文主要从建立一个完整的分析体系入手，对有关技术在具体环境中所起到的作用进行详细的探究，进而给以后的改进工作给予一定的借鉴。"
            + "从研究结果可以看出，合理地调配资源以及建立有效的协同机制，可以提高整个系统的运行效率，在一定程度上也可以降低运营成本。"
            + "另外还要注意数据安全和隐私保护的问题，保证技术方案有持续发展的可能。";

    @Test
    void order148909KeepsFactsAndBreaksTheTemplateDeeperThanOrdinary() {
        assertThat(INPUT).isEqualTo(CnkiOrdinaryHumanizeSampleFixtureTest.INPUT).hasSize(186);
        assertThat(COMPETITOR_OUTPUT).hasSize(198);
        assertThat(cnkiDeepIssues(INPUT, COMPETITOR_OUTPUT)).isEmpty();
        assertThat(COMPETITOR_OUTPUT)
                .contains("本文主要从", "入手", "从研究结果可以看出", "另外还要注意")
                .contains("一种方式", "借鉴", "持续发展的可能")
                .contains("数字化转型", "核心竞争力", "分析体系", "协同机制", "运行效率", "运营成本", "数据安全", "隐私保护")
                .doesNotContain("本文旨在", "研究表明", "与此同时", "本文用", "从研究可知")
                .doesNotContain("有效途径", "应用价值", "实践价值", "参考", "意义");
        assertThat(CnkiOrdinaryHumanizeSampleFixtureTest.cnkiOrdinaryIssues(INPUT, COMPETITOR_OUTPUT))
                .contains("expanded");
    }

    @Test
    void ordinaryRewriteAndUnchangedTextFailTheDeepCheck() {
        assertThat(cnkiDeepIssues(INPUT, CnkiOrdinaryHumanizeSampleFixtureTest.COMPETITOR_OUTPUT))
                .contains("ordinary opening", "意义 cadence")
                .doesNotContain("missing fact", "sentence count");
        assertThat(CnkiOrdinaryHumanizeSampleFixtureTest.cnkiOrdinaryIssues(
                CnkiOrdinaryHumanizeSampleFixtureTest.INPUT,
                CnkiOrdinaryHumanizeSampleFixtureTest.COMPETITOR_OUTPUT)).isEmpty();
        assertThat(cnkiDeepIssues(INPUT, INPUT)).contains("formal opening", "意义 cadence");
        assertThat(cnkiDeepIssues(INPUT, COMPETITOR_OUTPUT.replace("隐私保护", "保密")))
                .contains("missing fact");
        assertThat(cnkiDeepIssues(INPUT, COMPETITOR_OUTPUT.replace("。本文", "，本文")))
                .contains("sentence count");
    }

    @Test
    void deepOverlayCarriesTheSampleWithoutWeakeningOrdinary() throws Exception {
        String ordinary = NativeHumanizeProfiles.profileInstruction("humanize", "CNKI");
        String deep = NativeHumanizeProfiles.profileInstruction("深度降低AI写作痕迹", "CNKI");
        assertThat(ordinary)
                .contains("profile:cnki-ordinary-v1", "148900", "186", "185")
                .contains("只做贴近原文的书面近义改写", "不整段换序", "可以略短一两字，不要扩写", "不要增删句子")
                .contains("本文用", "从研究可知", "有效途径")
                .doesNotContain("148909", "198", "本文主要从", "从研究结果可以看出", "持续发展的可能", "profile:cnki-deep-v1");
        assertThat(deep)
                .contains("profile:cnki-deep-v1", "知网深度改写", "148909", "186", "198")
                .contains("本文主要从", "从研究结果可以看出", "另外还要注意", "一种方式", "持续发展的可能")
                .contains("不要写成有效途径", "不要留“参考”", "事实保留", "不新增案例")
                .doesNotContain("148900", "profile:cnki-ordinary-v1", "可以略短一两字", "不要扩写", "不整段换序");
        assertThat(deep).isNotEqualTo(ordinary);

        SkillPromptService skills = mock(SkillPromptService.class);
        when(skills.loadSkill(anyString())).thenAnswer(invocation -> new ClassPathResource(
                "skills/" + invocation.getArgument(0) + "/SKILL.md")
                .getContentAsString(StandardCharsets.UTF_8));
        DoubaoProperties properties = new DoubaoProperties();
        DoubaoAiRewriteService service = new DoubaoAiRewriteService(
                properties, new ObjectMapper(), RestClient.builder(), skills, new DoubaoModelRouter(properties));

        assertThat(systemPrompt(service, "深度降低AI写作痕迹@CNKI"))
                .contains("name: humanize-zh-academic", "profile:cnki-deep-v1", "148909", "198")
                .doesNotContain("148900", "profile:cnki-ordinary-v1");
        assertThat(systemPrompt(service, "humanize@CNKI"))
                .contains("profile:cnki-ordinary-v1", "148900", "不要扩写")
                .doesNotContain("148909", "profile:cnki-deep-v1");
        assertThat(userPrompt(service, "深度降低AI写作痕迹@CNKI"))
                .contains("profile:cnki-deep-v1", "148909", "本文主要从", "持续发展的可能")
                .doesNotContain("148900");
        assertThat(userPrompt(service, "humanize@CNKI"))
                .contains("profile:cnki-ordinary-v1", "148900", "从研究可知")
                .doesNotContain("148909");
        assertThat(systemPrompt(service, "rewrite@CNKI")).doesNotContain("148909", "profile:cnki-deep-v1");
        assertThat(userPrompt(service, "rewrite@CNKI")).doesNotContain("148909", "profile:cnki-deep-v1");
        assertThat(systemPrompt(service, "humanize@DAYA")).doesNotContain("148909", "profile:cnki-deep-v1");
        assertThat(userPrompt(service, "humanize@DAYA")).doesNotContain("148909", "profile:cnki-deep-v1");
    }

    static List<String> cnkiDeepIssues(String input, String output) {
        List<String> issues = new ArrayList<>();
        if (output.length() * 100 > input.length() * 130) {
            issues.add("padded");
        }
        if (output.length() * 100 < input.length() * 90) {
            issues.add("too short");
        }
        if (sentences(output) != sentences(input)) {
            issues.add("sentence count");
        }
        if (output.contains("本文旨在") || output.contains("研究表明") || output.contains("与此同时")) {
            issues.add("formal opening");
        }
        if (output.contains("本文用") || output.contains("从研究可知")) {
            issues.add("ordinary opening");
        }
        if (output.contains("有效途径") || output.contains("应用价值") || output.contains("实践价值")
                || output.contains("重要路径") || output.contains("参考") || output.contains("意义")) {
            issues.add("意义 cadence");
        }
        if (missingFact(output)) {
            issues.add("missing fact");
        }
        return issues;
    }

    private static boolean missingFact(String output) {
        if (!output.contains("数字化转型") || !output.contains("核心竞争力")
                || !output.contains("数据安全") || !output.contains("隐私保护")
                || !output.contains("运营成本")) {
            return true;
        }
        if (!output.contains("运行效率") && !output.contains("运转效率")) {
            return true;
        }
        if (!output.contains("协同机制") && !output.contains("协调机制")) {
            return true;
        }
        return !output.contains("分析框架") && !output.contains("分析体系");
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
