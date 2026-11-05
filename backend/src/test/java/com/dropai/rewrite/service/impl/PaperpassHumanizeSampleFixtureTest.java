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
 * Golden style check for 降重狗 order 148903, PaperPass降AI专用.
 * The competitor shook clause rhythm (186 characters to 195) and then drifted:
 * 可持续的实践价值 became 不足之处有待探究. DropAI keeps the rhythm and the claim.
 */
class PaperpassHumanizeSampleFixtureTest {

    static final String INPUT = "近年来，随着信息技术的快速发展，数字化转型已经成为各行各业提升核心竞争力的重要路径。"
            + "本文旨在通过构建一套系统化的分析框架，深入研究相关技术在实际场景中的应用效果，从而为后续优化提供有益参考。"
            + "研究表明，通过合理配置资源并完善协同机制，可以有效提升整体运行效率，并在一定程度上降低运营成本。"
            + "与此同时，还需要进一步关注数据安全与隐私保护问题，以确保技术方案具备可持续的实践价值。";

    static final String COMPETITOR_OUTPUT = "近几年来，信息技术迅猛发展，数字化转型成为各个行业企业提高自身核心竞争力的行之有效的办法。"
            + "本文试图创建起一套系统的分析架构，对技术在真实运用里产生的效果展开探究，以此来给之后的改良给予一些参照。"
            + "经过合理调配资源、健全协同机制之后，可有效地提高整个运行的效率，在某种程度上也能削减运行的成本。"
            + "数据安全、隐私保护这些技术手段的运用到本项目的安全防护技术分析当中还存在着诸多不足之处，有待于探究。";

    static final String KEPT_MEANING = "近几年来，信息技术迅猛发展，数字化转型成了各个行业提升核心竞争力的一条路径。"
            + "本文试图建起一套系统化的分析框架，把相关技术在实际场景中的应用效果摊开来看，以此来给后续优化提供参考。"
            + "经过合理配置资源、完善协同机制之后，整体运行效率可以提上来，运营成本也会在一定程度上降下去。"
            + "数据安全与隐私保护还要继续关注，这样技术方案才具备可持续的实践价值。";

    @Test
    void order148903ShakesRhythmAndThenInventsAShortcoming() {
        assertThat(INPUT).hasSize(186);
        assertThat(COMPETITOR_OUTPUT).hasSize(195);
        assertThat(COMPETITOR_OUTPUT)
                .contains("近几年来", "本文试图", "以此来", "经过", "之后")
                .contains("不足之处", "有待于探究", "本项目", "安全防护")
                .doesNotContain("随着", "本文旨在", "研究表明", "与此同时", "实践价值", "技术方案");
        assertThat(paperpassStyleIssues(INPUT, COMPETITOR_OUTPUT))
                .contains("meaning drift", "dropped claim")
                .doesNotContain("synonym chain");
    }

    @Test
    void synonymChainAndDriftFailWhileAShakenClaimPasses() {
        String synonymChain = "近年来，随着信息技术的迅猛发展，数字化转型已经成为各个行业提升核心竞争力的重要途径。"
                + "本文旨在通过建立一套系统化的分析框架，深入研究相关技术在实际场景中的应用效果，从而为后续优化提供有益借鉴。"
                + "研究表明，通过合理调配资源并完善协同机制，可以有效提升整体运行效率，并在一定程度上降低运营成本。"
                + "与此同时，还需要进一步关注数据安全与隐私保护问题，以确保技术方案具备可持续的实践价值。";
        assertThat(paperpassStyleIssues(INPUT, synonymChain))
                .contains("synonym chain")
                .doesNotContain("meaning drift", "dropped claim");
        assertThat(paperpassStyleIssues(INPUT, INPUT)).contains("synonym chain");
        assertThat(paperpassStyleIssues(INPUT, KEPT_MEANING)).isEmpty();
        assertThat(KEPT_MEANING)
                .contains("近几年来", "本文试图", "以此来", "经过", "可持续的实践价值")
                .doesNotContain("不足之处", "有待探究", "本项目", "安全防护", "本文旨在", "研究表明");
    }

    @Test
    void paperpassSkillAndPromptCarryTheSampleRules() throws Exception {
        String skill = new ClassPathResource("skills/humanize-platform-paperpass/SKILL.md")
                .getContentAsString(StandardCharsets.UTF_8);
        assertThat(skill)
                .contains("148903", "186", "195", "不足之处有待探究", "可持续的实践价值")
                .contains("纯连续同义词链", "不得只做同义词替换", "沿原句逐句换词")
                .contains("允许整段大幅重构表达", "不要求原句结构可还原", "85%—130%", "软参考")
                .contains("本项目", "安全防护")
                .doesNotContain("name: humanize-zh-academic");

        SkillPromptService skills = mock(SkillPromptService.class);
        when(skills.loadSkill(anyString())).thenAnswer(invocation -> new ClassPathResource(
                "skills/" + invocation.getArgument(0) + "/SKILL.md")
                .getContentAsString(StandardCharsets.UTF_8));
        DoubaoProperties properties = new DoubaoProperties();
        DoubaoAiRewriteService service = new DoubaoAiRewriteService(
                properties, new ObjectMapper(), RestClient.builder(), skills, new DoubaoModelRouter(properties));

        String ordinary = systemPrompt(service, "humanize@PAPERPASS");
        String deep = systemPrompt(service, "深度降低AI写作痕迹@PAPERPASS");
        assertThat(ordinary).isEqualTo(deep)
                .contains("name: humanize-platform-paperpass", "148903", "不足之处有待探究")
                .contains("profile:paperpass-ai-dedicated-v1", "允许整段大幅重构表达")
                .doesNotContain("name: humanize-zh-academic", "profile:gezida-ai-dedicated-v1");
        assertThat(userPrompt(service, "humanize@PAPERPASS"))
                .isEqualTo(userPrompt(service, "深度降低AI写作痕迹@PAPERPASS"))
                .contains("profile:paperpass-ai-dedicated-v1", "148903", "不足之处有待探究", "实践价值", "本项目");
    }

    static List<String> paperpassStyleIssues(String input, String output) {
        List<String> issues = new ArrayList<>();
        if (inventsClaim(input, output)) {
            issues.add("meaning drift");
        }
        if (input.contains("实践价值") && !output.contains("实践价值")
                || input.contains("技术方案") && !output.contains("技术方案")) {
            issues.add("dropped claim");
        }
        if (input.contains("数据安全") && !output.contains("数据安全")
                || input.contains("隐私保护") && !output.contains("隐私保护")) {
            issues.add("dropped fact");
        }
        int scaffolds = 0;
        if (output.contains("随着")) {
            scaffolds++;
        }
        if (output.contains("本文旨在") || output.contains("旨在通过")) {
            scaffolds++;
        }
        if (output.contains("研究表明")) {
            scaffolds++;
        }
        if (output.contains("与此同时")) {
            scaffolds++;
        }
        if (scaffolds >= 3) {
            issues.add("synonym chain");
        }
        return issues;
    }

    private static boolean inventsClaim(String input, String output) {
        for (String invented : List.of("不足之处", "有待探究", "有待于", "本项目", "安全防护")) {
            if (output.contains(invented) && !input.contains(invented)) {
                return true;
            }
        }
        return false;
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
