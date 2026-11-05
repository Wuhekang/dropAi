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
 * Golden style check for 降重狗 order c4a1eb7802ab4afdab69ce380445f171, Turnitin降AI专用.
 * The competitor kept four English sentences and moved 100 words to 101, but replaced
 * operational efficiency with "economic vitality of a region". DropAI must reject that drift.
 * Sentence count is no longer locked.
 */
class TurnitinHumanizeSampleFixtureTest {

    static final String ORDER_ID = "c4a1eb7802ab4afdab69ce380445f171";

    static final String INPUT = "In recent years, the rapid development of information technology has made "
            + "digital transformation an important path for industries to enhance core competitiveness. "
            + "This paper aims to construct a systematic analytical framework to study the application effects "
            + "of related technologies in real scenarios, thereby providing useful references for subsequent "
            + "optimization. Research shows that by reasonably allocating resources and improving collaborative "
            + "mechanisms, overall operational efficiency can be effectively improved and operating costs can be "
            + "reduced to a certain extent. At the same time, data security and privacy protection still require "
            + "further attention to ensure that the technical solution has sustainable practical value.";

    static final String COMPETITOR_OUTPUT = "With the development of information technology in recent years, "
            + "many enterprises have been motivated to improve their competitiveness through digitalisation. "
            + "This paper seeks to build an organized analysis system for the application results of related "
            + "technologies in actual situations and offer a reference for later optimisation. According to the "
            + "above research, a reasonable allocation of resources and the strengthening of cooperation among "
            + "enterprises can boost the overall economic vitality of a region and lower operating costs. "
            + "At the same time, the security and privacy of the data also need to be guaranteed to ensure the "
            + "long-term applicability of the technical solution.";

    static final String ACCEPTABLE_OUTPUT = "With the development of information technology in recent years, "
            + "digital transformation has become an important path for industries to enhance core competitiveness. "
            + "This paper seeks to build an organized analysis system to study the application effects of related "
            + "technologies in real scenarios and offer a reference for subsequent optimisation. According to the "
            + "above research, a reasonable allocation of resources and the strengthening of collaborative "
            + "mechanisms can improve overall operational efficiency and reduce operating costs to a certain extent. "
            + "At the same time, data security and privacy protection still need further attention to ensure that "
            + "the technical solution has sustainable practical value.";

    @Test
    void orderC4a1KeepsFourEnglishSentencesAndMovesOneWord() {
        assertThat(words(INPUT)).isEqualTo(100);
        assertThat(words(COMPETITOR_OUTPUT)).isEqualTo(101);
        assertThat(words(ACCEPTABLE_OUTPUT)).isEqualTo(101);
        assertThat(sentences(INPUT)).isEqualTo(4);
        assertThat(sentences(COMPETITOR_OUTPUT)).isEqualTo(4);
        assertThat(sentences(ACCEPTABLE_OUTPUT)).isEqualTo(4);
        assertThat(COMPETITOR_OUTPUT)
                .contains("digitalisation", "optimisation", "seeks to build", "According to the above research")
                .contains("economic vitality of a region");
    }

    @Test
    void factPreservingRhythmPassesAndCompetitorDriftFails() {
        assertThat(turnitinStyleIssues(INPUT, ACCEPTABLE_OUTPUT)).isEmpty();
        assertThat(ACCEPTABLE_OUTPUT)
                .startsWith("With the development of information technology in recent years")
                .contains("operational efficiency", "to a certain extent", "further attention", "industries")
                .doesNotContain("economic vitality", "of a region", "many enterprises");
        assertThat(turnitinStyleIssues(INPUT, COMPETITOR_OUTPUT))
                .contains("meaning drift", "lost efficiency", "dropped hedge", "invented actor", "upgraded security")
                .doesNotContain("not english", "sentence count", "expanded");
    }

    @Test
    void chineseTranslationAndPaddingFailTheSampleCheck() {
        String translated = "近年来，信息技术的快速发展使数字化转型成为行业提升核心竞争力的重要路径。";
        assertThat(turnitinStyleIssues(INPUT, translated)).contains("not english", "too short");
        String padded = ACCEPTABLE_OUTPUT + " This extra sentence invents a regional growth effect for the study.";
        assertThat(turnitinStyleIssues(INPUT, padded)).contains("meaning drift");
    }

    @Test
    void turnitinSkillAndPromptCarryTheSampleRulesWithoutRewriteOrDaya() throws Exception {
        String skill = new ClassPathResource("skills/humanize-platform-turnitin/SKILL.md")
                .getContentAsString(StandardCharsets.UTF_8);
        assertThat(skill)
                .contains(ORDER_ID, "100", "101", "不要译成中文", "operational efficiency",
                        "economic vitality of a region", "digitalisation", "optimisation", "重排信息入口")
                .doesNotContain("允许整段大幅重构表达", "不要求原句结构可还原", "句子数量与原文一致");

        SkillPromptService skills = mock(SkillPromptService.class);
        when(skills.loadSkill(anyString())).thenAnswer(invocation -> new ClassPathResource(
                "skills/" + invocation.getArgument(0) + "/SKILL.md")
                .getContentAsString(StandardCharsets.UTF_8));
        DoubaoProperties properties = new DoubaoProperties();
        DoubaoAiRewriteService service = new DoubaoAiRewriteService(
                properties, new ObjectMapper(), RestClient.builder(), skills, new DoubaoModelRouter(properties));

        String ordinary = systemPrompt(service, "humanize@TURNITIN");
        String deep = systemPrompt(service, "深度降低AI写作痕迹@TURNITIN");
        assertThat(ordinary).isEqualTo(deep)
                .contains("name: humanize-platform-turnitin", "profile:turnitin-ai-dedicated-v2")
                .contains(ORDER_ID, "100", "101", "不要译成中文", "economic vitality of a region", "重排信息入口")
                .doesNotContain("name: humanize-zh-academic", "允许整段大幅重构表达");
        assertThat(userPrompt(service, "humanize@TURNITIN"))
                .isEqualTo(userPrompt(service, "深度降低AI写作痕迹@TURNITIN"))
                .contains("profile:turnitin-ai-dedicated-v2", "不要译成中文", "专用 Skill",
                        "operational efficiency", "economic vitality of a region");
        assertThat(systemPrompt(service, "rewrite@TURNITIN"))
                .doesNotContain(ORDER_ID, "economic vitality of a region", "profile:turnitin-ai-dedicated-v2");
        assertThat(userPrompt(service, "rewrite@TURNITIN"))
                .contains("通用")
                .doesNotContain("Turnitin", "profile:", ORDER_ID);
        assertThat(systemPrompt(service, "humanize@DAYA"))
                .doesNotContain(ORDER_ID, "turnitin-ai-dedicated-v2", "name: humanize-platform-turnitin");
    }

    static List<String> turnitinStyleIssues(String input, String output) {
        List<String> issues = new ArrayList<>();
        if (containsHan(output)) {
            issues.add("not english");
        }
        int inputWords = words(input);
        int outputWords = words(output);
        if (outputWords > inputWords + 40) {
            issues.add("expanded");
        }
        if (outputWords < inputWords - 25) {
            issues.add("too short");
        }
        String lowered = output.toLowerCase();
        if (lowered.contains("economic vitality") || lowered.contains("of a region") || lowered.contains("regional")) {
            issues.add("meaning drift");
        }
        if (input.toLowerCase().contains("operational efficiency")
                && !lowered.contains("operational efficiency")
                && !lowered.contains("operating efficiency")) {
            issues.add("lost efficiency");
        }
        if (input.toLowerCase().contains("to a certain extent") && !lowered.contains("to a certain extent")) {
            issues.add("dropped hedge");
        }
        if ((!input.toLowerCase().contains("many enterprises") && lowered.contains("many enterprises"))
                || (!input.toLowerCase().contains("among enterprises") && lowered.contains("among enterprises"))) {
            issues.add("invented actor");
        }
        if (input.toLowerCase().contains("further attention") && !lowered.contains("attention")) {
            issues.add("upgraded security");
        }
        return issues;
    }

    private static int words(String text) {
        String trimmed = text.trim();
        if (trimmed.isEmpty()) {
            return 0;
        }
        return trimmed.split("\\s+").length;
    }

    private static int sentences(String text) {
        String trimmed = text.trim();
        if (trimmed.isEmpty()) {
            return 0;
        }
        return trimmed.split("(?<=[.!?])\\s+").length;
    }

    private static boolean containsHan(String text) {
        return text.codePoints().anyMatch(cp -> Character.UnicodeScript.of(cp) == Character.UnicodeScript.HAN);
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
