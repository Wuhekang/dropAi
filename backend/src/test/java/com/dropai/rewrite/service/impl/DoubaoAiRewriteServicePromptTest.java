package com.dropai.rewrite.service.impl;

import com.dropai.rewrite.config.DoubaoProperties;
import com.dropai.rewrite.service.SkillPromptService;
import com.dropai.rewrite.service.ai.DoubaoModelRouter;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class DoubaoAiRewriteServicePromptTest {

    private static final String SKILL_MARKER = "NATIVE_HUMANIZE_SKILL_MARKER";

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
