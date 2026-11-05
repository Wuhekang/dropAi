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
 * Golden style check for 降重狗 order 148898, 格子达降AI专用.
 * The competitor rewrote 186 characters to 175: same four sentences, colloquial paraphrase, no padding.
 */
class GezidaHumanizeSampleFixtureTest {

    static final String INPUT = "近年来，随着信息技术的快速发展，数字化转型已经成为各行各业提升核心竞争力的重要路径。"
            + "本文旨在通过构建一套系统化的分析框架，深入研究相关技术在实际场景中的应用效果，从而为后续优化提供有益参考。"
            + "研究表明，通过合理配置资源并完善协同机制，可以有效提升整体运行效率，并在一定程度上降低运营成本。"
            + "与此同时，还需要进一步关注数据安全与隐私保护问题，以确保技术方案具备可持续的实践价值。";

    static final String COMPETITOR_OUTPUT = "近些年来，伴随着信息技术的迅猛发展，数字化转型成了各个领域提高自身核心竞争力的主要途径。"
            + "本文主要通过建立一个分析框架，对技术的实际情况进行系统的分析，进而给后续优化提供有益的借鉴。"
            + "经过合理分配资源并且健全协同机制，可以提高整个运行效率，在一定范围内也可以缩减运营费用。"
            + "与此同时，还要继续探究数据安全、隐私保护等问题，保证该种技术方案有较好的实践意义。";

    @Test
    void order148898IsASlightlyShorterColloquialParaphrase() {
        assertThat(INPUT).hasSize(186);
        assertThat(COMPETITOR_OUTPUT).hasSize(175);
        assertThat(gezidaStyleIssues(INPUT, COMPETITOR_OUTPUT)).isEmpty();
        assertThat(COMPETITOR_OUTPUT)
                .contains("近些年来", "伴随着", "成了", "本文主要", "经过", "并且")
                .contains("数字化转型", "分析框架", "协同机制", "数据安全", "隐私保护")
                .doesNotContain("本文旨在", "研究表明", "旨在", "深入研究");
    }

    @Test
    void expandedOrFormalRewritesFailTheSampleStyleCheck() {
        String padded = INPUT + "综上所述，这一路径具有重要意义。";
        assertThat(gezidaStyleIssues(INPUT, padded)).isNotEmpty();
        assertThat(gezidaStyleIssues(INPUT, INPUT)).contains("formal opening");
    }

    @Test
    void gezidaSkillAndPromptCarryTheSampleRules() throws Exception {
        String skill = new ClassPathResource("skills/humanize-platform-gezida/SKILL.md")
                .getContentAsString(StandardCharsets.UTF_8);
        assertThat(skill)
                .contains("148898", "186", "175", "可以略短", "不要扩写", "本文旨在", "研究表明", "口语")
                .doesNotContain("允许整段大幅重构表达", "不要求原句结构可还原");

        SkillPromptService skills = mock(SkillPromptService.class);
        when(skills.loadSkill(anyString())).thenAnswer(invocation -> new ClassPathResource(
                "skills/" + invocation.getArgument(0) + "/SKILL.md")
                .getContentAsString(StandardCharsets.UTF_8));
        DoubaoProperties properties = new DoubaoProperties();
        DoubaoAiRewriteService service = new DoubaoAiRewriteService(
                properties, new ObjectMapper(), RestClient.builder(), skills, new DoubaoModelRouter(properties));

        String ordinary = systemPrompt(service, "humanize@GEZIDA");
        String deep = systemPrompt(service, "深度降低AI写作痕迹@GEZIDA");
        assertThat(ordinary).isEqualTo(deep)
                .contains("name: humanize-platform-gezida", "148898", "可以略短", "不要扩写")
                .contains("profile:gezida-ai-dedicated-v1")
                .doesNotContain("name: humanize-zh-academic", "允许整段大幅重构表达");
        assertThat(userPrompt(service, "humanize@GEZIDA"))
                .contains("profile:gezida-ai-dedicated-v1", "不要扩写", "本文旨在", "研究表明");
    }

    static List<String> gezidaStyleIssues(String input, String output) {
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
