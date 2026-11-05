package com.dropai.rewrite.service.impl;

import com.dropai.rewrite.config.DoubaoProperties;
import com.dropai.rewrite.service.SkillPromptService;
import com.dropai.rewrite.service.ai.DoubaoModelRouter;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.web.client.RestClient;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class DoubaoAiRewriteServicePromptTest {

    private static final String SKILL_MARKER = "NATIVE_HUMANIZE_SKILL_MARKER";

    @ParameterizedTest
    @ValueSource(strings = {"GENERAL", "CNKI", "WEIPU", "PAPERYY", "ZHUQUE"})
    void depthPairPlatformsKeepTheSharedSkillAndSoftLengthRules(String platform) throws Exception {
        var skills = classpathSkills();
        var service = service(skills);
        for (String mode : new String[]{"humanize", "深度降低AI写作痕迹"}) {
            String prompt = systemPrompt(service, mode + "@" + platform);
            assertThat(prompt).contains("name: humanize-zh-academic", "85%—130%", "软参考",
                            "允许整段大幅重构表达", "不要求原句结构可还原")
                    .doesNotContain("85% 到 115%", "再做轻微语序调整", "原文本身较短时，只做轻改。", "通常不超过原文 105%");
            assertThat(userPrompt(service, mode + "@" + platform))
                    .contains("不是第三方实测分数")
                    .doesNotContain("85% 到 115%", "采用轻量改写");
        }
        verify(skills, never()).loadSkill("humanize-platform-gezida");
    }

    @ParameterizedTest
    @ValueSource(strings = {"PAPERPASS", "TURNITIN"})
    void dedicatedPlatformsLoadTheirOwnSkillForBothIntensities(String platform) throws Exception {
        var skills = classpathSkills();
        var service = service(skills);
        String ordinary = systemPrompt(service, "humanize@" + platform);
        String deep = systemPrompt(service, "深度降低AI写作痕迹@" + platform);
        String skillName = NativeHumanizeProfiles.skillName(platform);
        assertThat(ordinary).isEqualTo(deep)
                .contains("name: " + skillName, "profile-id: ")
                .contains("85%—130%", "软参考", "允许整段大幅重构表达", "不要求原句结构可还原")
                .doesNotContain("name: humanize-zh-academic");
        assertThat(userPrompt(service, "humanize@" + platform))
                .isEqualTo(userPrompt(service, "深度降低AI写作痕迹@" + platform))
                .contains("profile:" + platform.toLowerCase() + "-ai-dedicated-v1")
                .doesNotContain("profile:cnki-ordinary-v1", "profile:cnki-deep-v1");
        verify(skills, times(2)).loadSkill(skillName);
    }

    @Test
    void nativeHumanizeLoadsTheResourceSkillAndAllowsAbstractBodies() throws Exception {
        SkillPromptService skillPromptService = mock(SkillPromptService.class);
        when(skillPromptService.loadSkill("humanize-zh-academic")).thenReturn(SKILL_MARKER);
        DoubaoAiRewriteService service = service(skillPromptService);

        String prompt = systemPrompt(service, "humanize@WEIPU");

        assertThat(prompt)
                .contains(SKILL_MARKER)
                .contains("profile:weipu-ordinary-v1")
                .contains("中文摘要正文、英文摘要正文")
                .contains("每个送入的段落都必须完整阅读", "处理后的有效正文可以与原文相同")
                .doesNotContain("必须产生真实文字变化", "不得直接返回原文", "profile:weipu-deep-v1");
        verify(skillPromptService).loadSkill("humanize-zh-academic");
    }

    @Test
    void depthPairPlatformsUseDifferentOrdinaryAndDeepProfiles() throws Exception {
        DoubaoAiRewriteService service = service(classpathSkills());
        assertThat(systemPrompt(service, "humanize@CNKI"))
                .contains("profile:cnki-ordinary-v1")
                .doesNotContain("profile:cnki-deep-v1", "profile:weipu-ordinary-v1");
        assertThat(systemPrompt(service, "深度降低AI写作痕迹@CNKI"))
                .contains("profile:cnki-deep-v1", "知网深度改写")
                .doesNotContain("profile:cnki-ordinary-v1");
        assertThat(systemPrompt(service, "降低AI写作痕迹@PAPERYY"))
                .contains("profile:paperyy-ordinary-v1", "name: humanize-zh-academic");
        assertThat(systemPrompt(service, "智能降AI@ZHUQUE"))
                .contains("profile:zhuque-ordinary-v1");
        assertThat(systemPrompt(service, "深度降低AI写作痕迹@ZHUQUE"))
                .contains("profile:zhuque-deep-v1")
                .isNotEqualTo(systemPrompt(service, "humanize@ZHUQUE"));
        assertThat(systemPrompt(service, "humanize@GENERAL"))
                .contains("name: humanize-zh-academic")
                .doesNotContain("profile:");
        assertThat(systemPrompt(service, "深度降低AI写作痕迹@GENERAL"))
                .isEqualTo(systemPrompt(service, "humanize@GENERAL"));
    }

    @Test
    void rewriteAndDoubleDoNotReceiveNativePlatformProfiles() throws Exception {
        SkillPromptService skills = classpathSkills();
        DoubaoAiRewriteService service = service(skills);

        assertThat(systemPrompt(service, "rewrite@CNKI")).contains("毕业论文查重降重专家").doesNotContain("profile:");
        assertThat(systemPrompt(service, "rewrite@PAPERPASS")).contains("毕业论文查重降重专家").doesNotContain("profile:");
        assertThat(userPrompt(service, "rewrite@WANFANG")).contains("万方").doesNotContain("profile:");
        assertThat(userPrompt(service, "rewrite@TURNITIN")).contains("通用").doesNotContain("Turnitin", "profile:");

        String doublePrompt = systemPrompt(service, "double@GEZIDA");
        assertThat(doublePrompt)
                .contains("name: humanize-zh-academic")
                .doesNotContain("profile:gezida-ai-dedicated-v1", "name: humanize-platform-gezida");
        assertThat(systemPrompt(service, "humanize-shared@GEZIDA"))
                .contains("name: humanize-zh-academic")
                .doesNotContain("profile:");
        assertThat(systemPrompt(service, "humanize-shared@WANFANG"))
                .doesNotContain("name: humanize-platform-wanfang", "profile:wanfang-ai-dedicated-v1");
        assertThat(userPrompt(service, "humanize-shared@CNKI"))
                .contains("知网")
                .doesNotContain("profile:");
        verify(skills, never()).loadSkill("humanize-platform-gezida");
        verify(skills, never()).loadSkill("humanize-platform-wanfang");
    }

    @Test
    void nativeDoubleAlsoLoadsTheResourceSkillWithoutTheV6Prompt() throws Exception {
        SkillPromptService skillPromptService = mock(SkillPromptService.class);
        when(skillPromptService.loadSkill("humanize-zh-academic")).thenReturn(SKILL_MARKER);
        DoubaoAiRewriteService service = service(skillPromptService);

        String prompt = systemPrompt(service, "double@GENERAL");

        assertThat(prompt)
                .contains(SKILL_MARKER, "处理后的有效正文可以与原文相同")
                .doesNotContain("DropAI V6", "必须产生真实文字变化", "不得直接返回原文");
        verify(skillPromptService).loadSkill("humanize-zh-academic");
    }

    @Test
    void dayaFallbackDoesNotLoadOrReceiveTheNativeSkill() throws Exception {
        SkillPromptService skillPromptService = mock(SkillPromptService.class);
        DoubaoAiRewriteService service = service(skillPromptService);

        String prompt = systemPrompt(service, "humanize@DAYA");

        assertThat(prompt).contains("DropAI V6").doesNotContain(SKILL_MARKER);
        verifyNoInteractions(skillPromptService);
    }

    @Test
    void rewriteModeDoesNotLoadTheHumanizeSkill() throws Exception {
        SkillPromptService skillPromptService = mock(SkillPromptService.class);
        DoubaoAiRewriteService service = service(skillPromptService);

        String prompt = systemPrompt(service, "rewrite@GENERAL");

        assertThat(prompt).contains("毕业论文查重降重专家").doesNotContain(SKILL_MARKER);
        verifyNoInteractions(skillPromptService);
    }

    private SkillPromptService classpathSkills() throws Exception {
        SkillPromptService skills = mock(SkillPromptService.class);
        when(skills.loadSkill(anyString())).thenAnswer(invocation -> new ClassPathResource(
                "skills/" + invocation.getArgument(0) + "/SKILL.md")
                .getContentAsString(java.nio.charset.StandardCharsets.UTF_8));
        return skills;
    }

    private String userPrompt(DoubaoAiRewriteService service, String rewriteType) throws Exception {
        Method method = DoubaoAiRewriteService.class.getDeclaredMethod(
                "userPrompt", String.class, String.class, int.class, String.class);
        method.setAccessible(true);
        return (String) method.invoke(service, "原文正文。", rewriteType, 70, "");
    }

    private DoubaoAiRewriteService service(SkillPromptService skillPromptService) {
        DoubaoProperties properties = new DoubaoProperties();
        return new DoubaoAiRewriteService(
                properties,
                new ObjectMapper(),
                RestClient.builder(),
                skillPromptService,
                new DoubaoModelRouter(properties)
        );
    }

    private String systemPrompt(DoubaoAiRewriteService service, String rewriteType) throws Exception {
        Method method = DoubaoAiRewriteService.class.getDeclaredMethod("systemPrompt", String.class);
        method.setAccessible(true);
        return (String) method.invoke(service, rewriteType);
    }
}
