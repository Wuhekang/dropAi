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
 * Golden style check for 降重狗 order 148900, 知网普通模式.
 * 186 characters became 185. Length stays near the source; sentence regroup is allowed.
 */
class CnkiOrdinaryHumanizeSampleFixtureTest {

    static final String INPUT = "近年来，随着信息技术的快速发展，数字化转型已经成为各行各业提升核心竞争力的重要路径。"
            + "本文旨在通过构建一套系统化的分析框架，深入研究相关技术在实际场景中的应用效果，从而为后续优化提供有益参考。"
            + "研究表明，通过合理配置资源并完善协同机制，可以有效提升整体运行效率，并在一定程度上降低运营成本。"
            + "与此同时，还需要进一步关注数据安全与隐私保护问题，以确保技术方案具备可持续的实践价值。";

    static final String COMPETITOR_OUTPUT = "近些年来，由于信息技术迅猛发展，数字化转型成了各个行业提高自身核心竞争力的有效途径。"
            + "本文用建立一个完整的分析体系来研究有关技术在现实情况中运用的效果，给之后的改进给予一些参考。"
            + "从研究可知，通过对各种资源进行合理的分配以及协调机制的完善来提高整个系统的运转效率，在某种程度上也会使企业的运营成本有所下降。"
            + "同时也要重视数据安全和隐私保护的问题，保证技术方案有持续的应用价值。";

    @Test
    void order148900StaysOneCharacterShorterAndSoftensOpenings() {
        assertThat(INPUT).hasSize(186);
        assertThat(COMPETITOR_OUTPUT).hasSize(185);
        assertThat(cnkiOrdinaryIssues(INPUT, COMPETITOR_OUTPUT)).isEmpty();
        assertThat(COMPETITOR_OUTPUT)
                .contains("近些年来", "由于", "本文用", "从研究可知", "各个行业", "有效途径")
                .contains("数字化转型", "运营成本", "数据安全", "隐私保护")
                .doesNotContain("本文旨在", "研究表明", "伴随着");
    }

    @Test
    void expandedOrUnsoftenedTextFailsTheSampleCheck() {
        assertThat(cnkiOrdinaryIssues(INPUT, INPUT + "综上所述。")).contains("expanded", "formal opening");
        assertThat(cnkiOrdinaryIssues(INPUT, INPUT)).contains("formal opening");
    }

    @Test
    void ordinaryOverlayCarriesTheSampleAndDeepStaysSeparate() throws Exception {
        SkillPromptService skills = mock(SkillPromptService.class);
        when(skills.loadSkill(anyString())).thenAnswer(invocation -> new ClassPathResource(
                "skills/" + invocation.getArgument(0) + "/SKILL.md")
                .getContentAsString(StandardCharsets.UTF_8));
        DoubaoProperties properties = new DoubaoProperties();
        DoubaoAiRewriteService service = new DoubaoAiRewriteService(
                properties, new ObjectMapper(), RestClient.builder(), skills, new DoubaoModelRouter(properties));

        String ordinary = systemPrompt(service, "humanize@CNKI");
        String deep = systemPrompt(service, "深度降低AI写作痕迹@CNKI");
        assertThat(ordinary)
                .contains("name: humanize-zh-academic", "profile:cnki-ordinary-v1", "148900", "186", "185")
                .contains("本文用", "从研究可知", "90%–108%")
                .doesNotContain("profile:cnki-deep-v1", "profile:gezida-ai-dedicated-v1",
                        "不整段换序", "不要增删句子", "四句都保留");
        assertThat(deep)
                .contains("profile:cnki-deep-v1", "知网深度改写")
                .doesNotContain("148900", "profile:cnki-ordinary-v1");
        assertThat(userPrompt(service, "humanize@CNKI"))
                .contains("profile:cnki-ordinary-v1", "148900", "从研究可知");
        assertThat(userPrompt(service, "深度降低AI写作痕迹@CNKI")).doesNotContain("148900");
    }

    static List<String> cnkiOrdinaryIssues(String input, String output) {
        List<String> issues = new ArrayList<>();
        if (output.length() > input.length()) {
            issues.add("expanded");
        }
        if (output.length() < Math.floor(input.length() * 0.90)) {
            issues.add("too short");
        }
        if (output.contains("本文旨在") || output.contains("研究表明")) {
            issues.add("formal opening");
        }
        return issues;
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
