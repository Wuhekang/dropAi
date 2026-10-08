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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class DoubaoAiRewriteServicePromptTest {

    private static final String SKILL_MARKER = "NATIVE_HUMANIZE_SKILL_MARKER";

    @ParameterizedTest
    @ValueSource(strings = {"GENERAL", "CNKI", "WEIPU", "WANFANG", "GEZIDA"})
    void allNativePlatformsReceiveDeepRebuildAndSoftLengthRules(String platform) throws Exception {
        var skills = mock(SkillPromptService.class);
        String actualSkill = new ClassPathResource("skills/humanize-zh-academic/SKILL.md")
                .getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        when(skills.loadSkill("humanize-zh-academic")).thenReturn(actualSkill);
        var service = service(skills);
        for (String mode : new String[]{"humanize", "double", "深度降低AI写作痕迹"}) {
            String prompt = systemPrompt(service, mode + "@" + platform);
            assertThat(prompt).contains("85%—130%", "软参考", "允许整段大幅重构表达", "不要求原句结构可还原")
                    .doesNotContain("85% 到 115%", "再做轻微语序调整", "原文本身较短时，只做轻改。", "通常不超过原文 105%");
            var user = DoubaoAiRewriteService.class.getDeclaredMethod(
                    "userPrompt", String.class, String.class, int.class, String.class);
            user.setAccessible(true);
            assertThat((String) user.invoke(service, "原文正文。", mode + "@" + platform, 70, ""))
                    .contains("不是第三方实测分数")
                    .doesNotContain("85% 到 115%", "采用轻量改写");
        }
    }

    @Test
    void nativeHumanizeLoadsTheResourceSkillAndAllowsAbstractBodies() throws Exception {
        SkillPromptService skillPromptService = mock(SkillPromptService.class);
        when(skillPromptService.loadSkill("humanize-zh-academic")).thenReturn(SKILL_MARKER);
        DoubaoAiRewriteService service = service(skillPromptService);

        String prompt = systemPrompt(service, "humanize@WEIPU");

        assertThat(prompt)
                .contains(SKILL_MARKER)
                .contains("中文摘要正文、英文摘要正文")
                .contains("每个送入的段落都必须完整阅读", "处理后的有效正文可以与原文相同")
                .doesNotContain("必须产生真实文字变化", "不得直接返回原文");
        verify(skillPromptService).loadSkill("humanize-zh-academic");
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
