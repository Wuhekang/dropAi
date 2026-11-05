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
 * Golden style check for 降重狗 order 148904, PaperYY 普通模式.
 * 186 characters became 207: four sentences, softened 本文旨在 and 研究表明,
 * and no 本研究 / 本文 research-claim openings.
 */
class PaperyyOrdinaryHumanizeSampleFixtureTest {

    static final String INPUT = "近年来，随着信息技术的快速发展，数字化转型已经成为各行各业提升核心竞争力的重要路径。"
            + "本文旨在通过构建一套系统化的分析框架，深入研究相关技术在实际场景中的应用效果，从而为后续优化提供有益参考。"
            + "研究表明，通过合理配置资源并完善协同机制，可以有效提升整体运行效率，并在一定程度上降低运营成本。"
            + "与此同时，还需要进一步关注数据安全与隐私保护问题，以确保技术方案具备可持续的实践价值。";

    static final String COMPETITOR_OUTPUT = "近几年来，由于信息技术的发展迅速，数字化转型已经成为了各个行业提高自身核心竞争力的一种方式。"
            + "本文主要从建立一个系统的分析框架入手，对有关技术在具体的应用中所产生的效果进行详细的分析，进而给之后的改进工作提供一定的借鉴意义。"
            + "经过研究发现，对资源进行合理的分配和建立完善的协同机制，可以使整个系统的运行效率得到提高，在某种程度上也能降低企业的运营成本。"
            + "另外还要对数据安全和隐私保护进行重视，保证技术方案有持续的可行性。";

    @Test
    void order148904StaysNearTheSameLengthAndSoftensOpenings() {
        assertThat(INPUT).hasSize(186);
        assertThat(COMPETITOR_OUTPUT).hasSize(207);
        assertThat(paperyyOrdinaryIssues(INPUT, COMPETITOR_OUTPUT)).isEmpty();
        assertThat(COMPETITOR_OUTPUT)
                .contains("近几年来", "由于", "本文主要从", "入手", "经过研究发现", "各个行业", "一种方式")
                .contains("数字化转型", "运营成本", "数据安全", "隐私保护", "持续的可行性")
                .doesNotContain("本文旨在", "研究表明", "本研究", "本文通过", "从研究可知", "伴随着");
    }

    @Test
    void expandedOrResearchClaimOpeningsFailTheSampleCheck() {
        String padded = INPUT + "综上所述，这一路径具有重要的理论意义和实践价值，并为后续研究提供了充分参考。";
        assertThat(paperyyOrdinaryIssues(INPUT, padded)).contains("expanded", "sentence count", "research-claim opening");
        assertThat(paperyyOrdinaryIssues(INPUT, INPUT)).contains("research-claim opening")
                .doesNotContain("expanded", "sentence count", "too short");
        String claim = COMPETITOR_OUTPUT.replace("本文主要从", "本研究通过");
        assertThat(claim).hasSize(COMPETITOR_OUTPUT.length());
        assertThat(paperyyOrdinaryIssues(INPUT, claim)).contains("research-claim opening")
                .doesNotContain("expanded", "sentence count", "too short");
        assertThat(paperyyOrdinaryIssues(INPUT, "近几年来，数字化转型已经成为了各个行业提高自身核心竞争力的一种方式。"))
                .contains("too short", "sentence count");
    }

    @Test
    void ordinaryOverlayCarriesTheSampleAndOtherPathsStaySeparate() throws Exception {
        SkillPromptService skills = mock(SkillPromptService.class);
        when(skills.loadSkill(anyString())).thenAnswer(invocation -> new ClassPathResource(
                "skills/" + invocation.getArgument(0) + "/SKILL.md")
                .getContentAsString(StandardCharsets.UTF_8));
        DoubaoProperties properties = new DoubaoProperties();
        DoubaoAiRewriteService service = new DoubaoAiRewriteService(
                properties, new ObjectMapper(), RestClient.builder(), skills, new DoubaoModelRouter(properties));

        String ordinary = systemPrompt(service, "humanize@PAPERYY");
        String deep = systemPrompt(service, "深度降低AI写作痕迹@PAPERYY");
        assertThat(ordinary)
                .contains("name: humanize-zh-academic", "profile:paperyy-ordinary-v1", "148904", "186", "207")
                .contains("本文主要从", "经过研究发现", "本研究", "本文通过")
                .doesNotContain("profile:paperyy-deep-v1", "profile:cnki-ordinary-v1", "profile:gezida-ai-dedicated-v1");
        assertThat(systemPrompt(service, "降低AI写作痕迹@PAPERYY")).contains("148904", "profile:paperyy-ordinary-v1");
        assertThat(deep)
                .contains("profile:paperyy-deep-v1", "PaperYY 深度改写")
                .doesNotContain("148904", "profile:paperyy-ordinary-v1", "本文主要从", "经过研究发现");
        assertThat(userPrompt(service, "humanize@PAPERYY"))
                .contains("profile:paperyy-ordinary-v1", "148904", "本文主要从", "经过研究发现");
        assertThat(userPrompt(service, "深度降低AI写作痕迹@PAPERYY")).doesNotContain("148904");

        assertThat(systemPrompt(service, "humanize@CNKI")).doesNotContain("148904", "profile:paperyy-ordinary-v1");
        assertThat(systemPrompt(service, "rewrite@PAPERYY")).doesNotContain("148904", "profile:paperyy-ordinary-v1");
        assertThat(userPrompt(service, "rewrite@PAPERYY")).doesNotContain("148904", "profile:paperyy-ordinary-v1");
        assertThat(systemPrompt(service, "double@PAPERYY")).doesNotContain("148904", "profile:paperyy-ordinary-v1");
        assertThat(userPrompt(service, "double@PAPERYY")).doesNotContain("148904", "profile:paperyy-ordinary-v1");
        assertThat(systemPrompt(service, "humanize@DAYA")).doesNotContain("148904", "profile:paperyy-ordinary-v1");
        assertThat(userPrompt(service, "humanize@DAYA")).doesNotContain("148904", "profile:paperyy-ordinary-v1");
    }

    static List<String> paperyyOrdinaryIssues(String input, String output) {
        List<String> issues = new ArrayList<>();
        if (output.length() > Math.ceil(input.length() * 1.15d)) {
            issues.add("expanded");
        }
        if (output.length() < Math.floor(input.length() * 0.90d)) {
            issues.add("too short");
        }
        if (sentences(output) != sentences(input)) {
            issues.add("sentence count");
        }
        if (output.contains("本文旨在") || output.contains("研究表明")
                || output.contains("本研究") || output.contains("本文通过")) {
            issues.add("research-claim opening");
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
