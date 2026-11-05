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
 * Golden style check for 降重狗 order 148902, 万方降AI专用.
 * The competitor rewrote 186 characters to 182 with causal colloquial connectors.
 * Its last sentence replaces 可持续的实践价值 with 防止失效; DropAI keeps the rhythm and the original purpose.
 */
class WanfangHumanizeSampleFixtureTest {

    static final String INPUT = "近年来，随着信息技术的快速发展，数字化转型已经成为各行各业提升核心竞争力的重要路径。"
            + "本文旨在通过构建一套系统化的分析框架，深入研究相关技术在实际场景中的应用效果，从而为后续优化提供有益参考。"
            + "研究表明，通过合理配置资源并完善协同机制，可以有效提升整体运行效率，并在一定程度上降低运营成本。"
            + "与此同时，还需要进一步关注数据安全与隐私保护问题，以确保技术方案具备可持续的实践价值。";

    static final String COMPETITOR_OUTPUT = "近些年来，因为信息技术飞速发展，数字化转型成了各个领域提高核心竞争力的主要途径。"
            + "本文试图建立一个系统的分析框架来探究相关技术在具体的环境中所起到的作用，并且给后面改进提供一些启示。"
            + "经过研究可知，在科学合理地安排资源的基础上以及构建起有效的协同机制后，可以大大提高系统的运行效率，并且还可以减少运营成本。"
            + "另外还要特别重视数据安全和隐私保护的问题，防止该技术方案失效。";

    /** Same three-sentence rhythm as order 148902. The ending keeps 可持续的实践价值. */
    static final String PRESERVED_OUTPUT = "近些年来，因为信息技术飞速发展，数字化转型成了各个领域提高核心竞争力的主要途径。"
            + "本文试图建立一个系统的分析框架来探究相关技术在具体的环境中所起到的作用，并且给后面改进提供一些启示。"
            + "经过研究可知，在科学合理地安排资源的基础上以及构建起有效的协同机制后，可以大大提高系统的运行效率，并且还可以减少运营成本。"
            + "另外还要重视数据安全和隐私保护，以确保技术方案具备可持续的实践价值。";

    @Test
    void order148902IsSlightlyShorterWithCausalConnectors() {
        assertThat(INPUT).hasSize(186);
        assertThat(COMPETITOR_OUTPUT).hasSize(182);
        assertThat(COMPETITOR_OUTPUT)
                .contains("近些年来", "因为", "成了", "本文试图", "并且", "经过研究可知", "另外")
                .contains("数字化转型", "分析框架", "协同机制", "数据安全", "隐私保护")
                .doesNotContain("本文旨在", "研究表明")
                .doesNotContain("实践价值")
                .contains("防止该技术方案失效");
        assertThat(wanfangStyleIssues(INPUT, COMPETITOR_OUTPUT)).containsExactly("meaning drift");
    }

    @Test
    void preservedEndingKeepsTheSampleRhythmAndTheOriginalPurpose() {
        assertThat(PRESERVED_OUTPUT).hasSize(185);
        assertThat(PRESERVED_OUTPUT).startsWith(firstSentences(COMPETITOR_OUTPUT, 3));
        assertThat(wanfangStyleIssues(INPUT, PRESERVED_OUTPUT)).isEmpty();
        assertThat(PRESERVED_OUTPUT)
                .contains("因为", "并且", "另外", "可持续的实践价值")
                .doesNotContain("本文旨在", "研究表明", "失效");
    }

    @Test
    void expandedOrUnsoftenedTextFailsTheSampleStyleCheck() {
        String padded = INPUT + "综上所述，这一路径具有重要意义。";
        assertThat(wanfangStyleIssues(INPUT, padded)).isNotEmpty();
        assertThat(wanfangStyleIssues(INPUT, INPUT)).contains("formal opening");
    }

    @Test
    void wanfangSkillAndPromptCarryTheSampleRules() throws Exception {
        String skill = new ClassPathResource("skills/humanize-platform-wanfang/SKILL.md")
                .getContentAsString(StandardCharsets.UTF_8);
        assertThat(skill)
                .contains("148902", "186", "182", "可以略短", "不要扩写", "本文旨在", "研究表明")
                .contains("因为", "并且", "另外", "实践价值", "防止失效")
                .doesNotContain("允许整段大幅重构表达", "不要求原句结构可还原");

        SkillPromptService skills = mock(SkillPromptService.class);
        when(skills.loadSkill(anyString())).thenAnswer(invocation -> new ClassPathResource(
                "skills/" + invocation.getArgument(0) + "/SKILL.md")
                .getContentAsString(StandardCharsets.UTF_8));
        DoubaoProperties properties = new DoubaoProperties();
        DoubaoAiRewriteService service = new DoubaoAiRewriteService(
                properties, new ObjectMapper(), RestClient.builder(), skills, new DoubaoModelRouter(properties));

        String ordinary = systemPrompt(service, "humanize@WANFANG");
        String deep = systemPrompt(service, "深度降低AI写作痕迹@WANFANG");
        assertThat(ordinary).isEqualTo(deep)
                .contains("name: humanize-platform-wanfang", "148902", "可以略短", "不要扩写")
                .contains("profile:wanfang-ai-dedicated-v1", "因为", "实践价值", "防止失效")
                .doesNotContain("name: humanize-zh-academic", "允许整段大幅重构表达");
        assertThat(userPrompt(service, "humanize@WANFANG"))
                .contains("profile:wanfang-ai-dedicated-v1", "不要扩写", "本文旨在", "研究表明")
                .contains("因为", "并且", "另外", "实践价值", "防止失效");
    }

    static List<String> wanfangStyleIssues(String input, String output) {
        List<String> issues = new ArrayList<>();
        if (output.length() > input.length()) {
            issues.add("expanded");
        }
        if (output.length() < Math.floor(input.length() * 0.90)) {
            issues.add("too short");
        }
        if (sentences(output) != sentences(input)) {
            issues.add("sentence count");
        }
        if (output.contains("本文旨在") || output.contains("研究表明")) {
            issues.add("formal opening");
        }
        if ((input.contains("实践价值") && !output.contains("实践价值")) || output.contains("失效")) {
            issues.add("meaning drift");
        }
        return issues;
    }

    private static int sentences(String text) {
        return text.split("。", -1).length - 1;
    }

    private static String firstSentences(String text, int count) {
        int end = -1;
        for (int i = 0; i < count; i++) {
            end = text.indexOf('。', end + 1);
        }
        return text.substring(0, end + 1);
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
