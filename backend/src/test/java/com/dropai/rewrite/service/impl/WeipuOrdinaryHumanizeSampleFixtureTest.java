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
 * Golden style check for 降重狗 order 148908, 维普普通模式.
 * 186 characters became 179: four sentences, 通过…实现 skeletons broken, openings softened.
 * The competitor ending drifts 实践价值 to 持续使用的必要性; DropAI keeps the original meaning.
 */
class WeipuOrdinaryHumanizeSampleFixtureTest {

    static final String INPUT = "近年来，随着信息技术的快速发展，数字化转型已经成为各行各业提升核心竞争力的重要路径。"
            + "本文旨在通过构建一套系统化的分析框架，深入研究相关技术在实际场景中的应用效果，从而为后续优化提供有益参考。"
            + "研究表明，通过合理配置资源并完善协同机制，可以有效提升整体运行效率，并在一定程度上降低运营成本。"
            + "与此同时，还需要进一步关注数据安全与隐私保护问题，以确保技术方案具备可持续的实践价值。";

    static final String COMPETITOR_OUTPUT = "近几年来，由于信息技术的发展迅速，数字化转型成了各个行业提高自身核心竞争力的一种途径。"
            + "本文主要通过对有关技术在实际应用中所产生的效果进行系统的分析来达到对之加以改进的目的。"
            + "从实际研究中得知，在科学分配资源的基础上建立起良好的合作机制，可以使整个系统的运转更加高效，同时也可以减少运营成本。"
            + "另外也要注意数据安全和隐私保护的问题，保证技术方案有持续使用的必要性。";

    static final String PREFERRED_OUTPUT = COMPETITOR_OUTPUT.replace("持续使用的必要性", "可持续的实践价值");

    @Test
    void order148908BreaksTongguoSkeletonAndKeepsLength() {
        assertThat(INPUT).hasSize(186);
        assertThat(COMPETITOR_OUTPUT).hasSize(179);
        assertThat(weipuOrdinaryIssues(INPUT, COMPETITOR_OUTPUT)).isEmpty();
        assertThat(COMPETITOR_OUTPUT)
                .contains("近几年来", "由于", "本文主要", "从实际研究中得知", "另外", "一种途径")
                .contains("在科学分配资源的基础上", "可以使")
                .contains("数字化转型", "运营成本", "数据安全", "隐私保护")
                .doesNotContain("本文旨在", "研究表明", "近年来，随着", "与此同时", "通过构建", "可以有效提升");
    }

    @Test
    void meaningDriftIsRecordedAndNotTheTarget() {
        assertThat(COMPETITOR_OUTPUT).contains("持续使用的必要性").doesNotContain("实践价值");
        assertThat(meaningDrift(COMPETITOR_OUTPUT)).contains("持续使用的必要性");
        assertThat(PREFERRED_OUTPUT).hasSize(COMPETITOR_OUTPUT.length());
        assertThat(weipuOrdinaryIssues(INPUT, PREFERRED_OUTPUT)).isEmpty();
        assertThat(meaningDrift(PREFERRED_OUTPUT)).isEmpty();
        assertThat(PREFERRED_OUTPUT).contains("可持续的实践价值").doesNotContain("持续使用的必要性");
    }

    @Test
    void expandedOrUnbrokenSkeletonFailsTheSampleCheck() {
        assertThat(weipuOrdinaryIssues(INPUT, INPUT + "综上所述。"))
                .contains("expanded", "formal opening", "通过实现骨架");
        assertThat(weipuOrdinaryIssues(INPUT, INPUT)).contains("formal opening", "通过实现骨架");
        assertThat(meaningDrift(INPUT)).isEmpty();
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

        String ordinary = systemPrompt(service, "humanize@WEIPU");
        String deep = systemPrompt(service, "深度降低AI写作痕迹@WEIPU");
        assertThat(ordinary)
                .contains("name: humanize-zh-academic", "profile:weipu-ordinary-v1", "148908", "186", "179")
                .contains("近几年来", "从实际研究中得知", "可以有效提升", "实践价值", "持续使用的必要性")
                .doesNotContain("profile:weipu-deep-v1", "profile:cnki-ordinary-v1");
        assertThat(deep)
                .contains("profile:weipu-deep-v1", "维普深度改写")
                .doesNotContain("148908", "profile:weipu-ordinary-v1", "持续使用的必要性");
        assertThat(userPrompt(service, "humanize@WEIPU"))
                .contains("profile:weipu-ordinary-v1", "148908", "实践价值", "从实际研究中得知");
        assertThat(userPrompt(service, "深度降低AI写作痕迹@WEIPU")).doesNotContain("148908");
        assertThat(systemPrompt(service, "rewrite@WEIPU")).doesNotContain("148908", "profile:weipu-ordinary-v1");
        assertThat(systemPrompt(service, "humanize@DAYA")).doesNotContain("148908", "profile:weipu-ordinary-v1");
    }

    static List<String> weipuOrdinaryIssues(String input, String output) {
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
        if (output.contains("本文旨在") || output.contains("研究表明")
                || output.contains("近年来，随着") || output.contains("与此同时")) {
            issues.add("formal opening");
        }
        if (hasTongguoShixianSkeleton(output)) {
            issues.add("通过实现骨架");
        }
        return issues;
    }

    static List<String> meaningDrift(String output) {
        List<String> issues = new ArrayList<>();
        if (output.contains("持续使用的必要性") || !output.contains("实践价值")) {
            issues.add("持续使用的必要性");
        }
        return issues;
    }

    private static boolean hasTongguoShixianSkeleton(String text) {
        for (String sentence : text.split("。", -1)) {
            int through = sentence.indexOf("通过");
            if (through < 0) {
                continue;
            }
            String rest = sentence.substring(through);
            if (rest.contains("实现") || rest.contains("构建") || rest.contains("有效提升")) {
                return true;
            }
        }
        return false;
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
