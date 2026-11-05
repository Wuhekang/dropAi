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
 * Golden style check for 降重狗 order 148910, 维普深度改写.
 * 186 characters became 190: four sentences, openings and length variance past ordinary 148908.
 * Facts stay. The ordinary overlay is not rewritten by this sample.
 */
class WeipuDeepHumanizeSampleFixtureTest {

    static final String INPUT = "近年来，随着信息技术的快速发展，数字化转型已经成为各行各业提升核心竞争力的重要路径。"
            + "本文旨在通过构建一套系统化的分析框架，深入研究相关技术在实际场景中的应用效果，从而为后续优化提供有益参考。"
            + "研究表明，通过合理配置资源并完善协同机制，可以有效提升整体运行效率，并在一定程度上降低运营成本。"
            + "与此同时，还需要进一步关注数据安全与隐私保护问题，以确保技术方案具备可持续的实践价值。";

    /** Competitor ordinary order 148908, used only to prove this sample goes further. */
    static final String ORDINARY_148908 = "近几年来，由于信息技术的发展迅速，数字化转型成了各个行业提高自身核心竞争力的一种途径。"
            + "本文主要通过对有关技术在实际应用中所产生的效果进行系统的分析来达到对之加以改进的目的。"
            + "从实际研究中得知，在科学分配资源的基础上建立起良好的合作机制，可以使整个系统的运转更加高效，同时也可以减少运营成本。"
            + "另外也要注意数据安全和隐私保护的问题，保证技术方案有持续使用的必要性。";

    static final String COMPETITOR_OUTPUT = "近些年来，由于信息技术的发展迅速，数字化转型成了各个行业提高自身核心竞争力的一种方式。"
            + "本文主要从建立一个完整的分析体系入手，对有关技术在具体场合下所起到的作用进行探究，进而给之后的改进提供一定的借鉴。"
            + "从研究中得知，对资源进行合理的分配以及改善协同机制，可以提高整个系统的运行效率，在某种程度上也可以减少运营成本。"
            + "另外还要注意数据安全和隐私保护的问题，保证技术方案有持续的实践意义。";

    @Test
    void order148910ChangesOpeningsAndLengthMoreThanOrdinaryWhileKeepingFacts() {
        assertThat(INPUT).hasSize(186);
        assertThat(ORDINARY_148908).hasSize(179);
        assertThat(COMPETITOR_OUTPUT).hasSize(190);
        assertThat(weipuDeepIssues(INPUT, COMPETITOR_OUTPUT)).isEmpty();
        assertThat(COMPETITOR_OUTPUT)
                .contains("近些年来", "本文主要从", "入手", "从研究中得知", "另外还要注意")
                .contains("一种方式", "分析体系", "具体场合", "持续的实践意义")
                .contains("数字化转型", "核心竞争力", "信息技术", "协同机制", "运行效率", "运营成本")
                .contains("在某种程度上", "数据安全", "隐私保护")
                .doesNotContain("本文旨在", "研究表明", "与此同时", "近年来，随着")
                .doesNotContain("近几年来", "本文主要通过", "从实际研究中得知", "另外也要注意", "一种途径")
                .doesNotContain("合作机制", "伴随着");

        List<Integer> ordinaryLengths = sentenceLengths(ORDINARY_148908);
        List<Integer> deepLengths = sentenceLengths(COMPETITOR_OUTPUT);
        assertThat(ordinaryLengths).containsExactly(43, 43, 58, 35);
        assertThat(deepLengths).containsExactly(43, 57, 56, 34);
        assertThat(deepLengths.get(1) - deepLengths.get(0))
                .isGreaterThan(ordinaryLengths.get(1) - ordinaryLengths.get(0));
        assertThat(lengthVariance(deepLengths)).isGreaterThan(lengthVariance(ordinaryLengths));
        assertThat(distinctCount(deepLengths)).isGreaterThan(distinctCount(ordinaryLengths));
    }

    @Test
    void ordinaryRewriteAndUnchangedTextFailTheDeepCheck() {
        assertThat(weipuDeepIssues(INPUT, ORDINARY_148908))
                .contains("ordinary opening", "missing fact")
                .doesNotContain("sentence count");
        assertThat(weipuDeepIssues(INPUT, INPUT)).contains("formal opening");
        assertThat(weipuDeepIssues(INPUT, COMPETITOR_OUTPUT.replace("隐私保护", "保密")))
                .contains("missing fact");
        assertThat(weipuDeepIssues(INPUT, COMPETITOR_OUTPUT.replace("协同机制", "合作机制")))
                .contains("missing fact");
        assertThat(weipuDeepIssues(INPUT, COMPETITOR_OUTPUT.replace("。本文", "，本文")))
                .contains("sentence count");
    }

    @Test
    void deepOverlayCarriesTheSampleWithoutWeakeningOrdinary() throws Exception {
        String ordinary = NativeHumanizeProfiles.profileInstruction("humanize", "WEIPU");
        String deep = NativeHumanizeProfiles.profileInstruction("深度降低AI写作痕迹", "WEIPU");
        assertThat(ordinary)
                .contains("profile:weipu-ordinary-v1", "维普普通模式", "沿用通用 Skill")
                .contains("不要求每段更换信息入口", "已经自然的句子可以保留")
                .doesNotContain("148910", "190", "profile:weipu-deep-v1")
                .doesNotContain("本文主要从", "近些年来", "具体场合", "实践意义", "一种方式", "43、57、56、34");
        assertThat(deep)
                .contains("profile:weipu-deep-v1", "维普深度改写", "148910", "186", "190")
                .contains("43、57、56、34", "本文主要从", "从研究中得知", "另外还要注意", "一种方式")
                .contains("持续的实践意义", "协同机制不要写成合作机制", "运行效率不要写成运转")
                .contains("事实保留", "不新增案例", "不要把前两句收成一样长")
                .doesNotContain("148908", "profile:weipu-ordinary-v1", "持续使用的必要性")
                .doesNotContain("不要求每段更换信息入口", "不要扩写", "可以略短", "只打散明显的句式规律");
        assertThat(deep).isNotEqualTo(ordinary);

        SkillPromptService skills = mock(SkillPromptService.class);
        when(skills.loadSkill(anyString())).thenAnswer(invocation -> new ClassPathResource(
                "skills/" + invocation.getArgument(0) + "/SKILL.md")
                .getContentAsString(StandardCharsets.UTF_8));
        DoubaoProperties properties = new DoubaoProperties();
        DoubaoAiRewriteService service = new DoubaoAiRewriteService(
                properties, new ObjectMapper(), RestClient.builder(), skills, new DoubaoModelRouter(properties));

        assertThat(systemPrompt(service, "深度降低AI写作痕迹@WEIPU"))
                .contains("name: humanize-zh-academic", "profile:weipu-deep-v1", "148910", "190")
                .doesNotContain("148908", "profile:weipu-ordinary-v1");
        assertThat(systemPrompt(service, "humanize@WEIPU"))
                .contains("profile:weipu-ordinary-v1", "不要求每段更换信息入口")
                .doesNotContain("148910", "profile:weipu-deep-v1");
        assertThat(userPrompt(service, "深度降低AI写作痕迹@WEIPU"))
                .contains("profile:weipu-deep-v1", "148910", "本文主要从", "持续的实践意义")
                .doesNotContain("148908", "profile:weipu-ordinary-v1");
        assertThat(userPrompt(service, "humanize@WEIPU"))
                .contains("profile:weipu-ordinary-v1")
                .doesNotContain("148910", "本文主要从");
        assertThat(systemPrompt(service, "rewrite@WEIPU")).doesNotContain("148910", "profile:weipu-deep-v1");
        assertThat(userPrompt(service, "rewrite@WEIPU")).doesNotContain("148910", "profile:weipu-deep-v1");
        assertThat(systemPrompt(service, "humanize@DAYA")).doesNotContain("148910", "profile:weipu-deep-v1");
        assertThat(userPrompt(service, "humanize@DAYA")).doesNotContain("148910", "profile:weipu-deep-v1");
    }

    static List<String> weipuDeepIssues(String input, String output) {
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
        if (output.contains("本文旨在") || output.contains("研究表明") || output.contains("与此同时")
                || output.contains("近年来，随着")) {
            issues.add("formal opening");
        }
        if (output.contains("近几年来") || output.contains("本文主要通过")
                || output.contains("从实际研究中得知") || output.contains("另外也要注意")
                || output.contains("一种途径")) {
            issues.add("ordinary opening");
        }
        if (missingFact(output)) {
            issues.add("missing fact");
        }
        return issues;
    }

    private static boolean missingFact(String output) {
        if (!output.contains("数字化转型") || !output.contains("核心竞争力")
                || !output.contains("信息技术") || !output.contains("数据安全")
                || !output.contains("隐私保护") || !output.contains("运营成本")
                || !output.contains("运行效率") || !output.contains("协同机制")) {
            return true;
        }
        if (output.contains("合作机制")) {
            return true;
        }
        if (!output.contains("分析框架") && !output.contains("分析体系")) {
            return true;
        }
        return !output.contains("实践意义") && !output.contains("实践价值");
    }

    private static List<Integer> sentenceLengths(String text) {
        List<Integer> lengths = new ArrayList<>();
        for (String sentence : text.split("。", -1)) {
            if (!sentence.isEmpty()) {
                lengths.add(sentence.length() + 1);
            }
        }
        return lengths;
    }

    private static double lengthVariance(List<Integer> lengths) {
        double mean = lengths.stream().mapToInt(Integer::intValue).average().orElseThrow();
        double sum = 0;
        for (int length : lengths) {
            double delta = length - mean;
            sum += delta * delta;
        }
        return sum / lengths.size();
    }

    private static int distinctCount(List<Integer> lengths) {
        return (int) lengths.stream().distinct().count();
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
