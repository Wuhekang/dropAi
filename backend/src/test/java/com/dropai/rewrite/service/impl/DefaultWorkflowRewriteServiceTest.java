package com.dropai.rewrite.service.impl;

import com.dropai.rewrite.service.AiRewriteService;
import com.dropai.rewrite.service.TextStructureProtector;
import com.dropai.rewrite.service.WorkflowRewriteService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DefaultWorkflowRewriteServiceTest {

    private static final String ORIGINAL = "该平台负责核对资料，并记录现场处理情况。";

    @Test
    void nativeHumanizeRetriesOnceWhenFirstDraftEqualsOriginal() {
        AiRewriteService aiRewriteService = mock(AiRewriteService.class);
        when(aiRewriteService.rewriteWithFeedback(anyString(), anyString(), anyInt(), anyString()))
                .thenReturn(ORIGINAL, "资料由平台复核，现场处理情况随后登记。");
        DefaultWorkflowRewriteService service = service(aiRewriteService);

        WorkflowRewriteService.WorkflowRewriteResult result = service.execute(ORIGINAL, "humanize");

        assertThat(result.getRewrittenText()).isEqualTo("资料由平台复核，现场处理情况随后登记。");
        ArgumentCaptor<String> feedback = ArgumentCaptor.forClass(String.class);
        verify(aiRewriteService, times(2))
                .rewriteWithFeedback(anyString(), anyString(), anyInt(), feedback.capture());
        assertThat(feedback.getAllValues().get(0)).isEmpty();
        assertThat(feedback.getAllValues().get(1))
                .contains("与原文相同", "至少重组一处", "仅改标点和空白不算完成");
        assertThat(result.getWorkflowSteps())
                .extracting(step -> step.getNodeType())
                .contains("UNCHANGED_RETRY");
    }

    @Test
    void nativeHumanizeRetriesWhenFirstDraftOnlyChangesPunctuationAndWhitespace() {
        AiRewriteService aiRewriteService = mock(AiRewriteService.class);
        when(aiRewriteService.rewriteWithFeedback(anyString(), anyString(), anyInt(), anyString()))
                .thenReturn("该平台负责核对资料 并记录现场处理情况", "平台复核资料，现场情况另行记录。");
        DefaultWorkflowRewriteService service = service(aiRewriteService);

        assertThat(service.execute(ORIGINAL, "humanize@WEIPU").getRewrittenText())
                .isEqualTo("平台复核资料，现场情况另行记录。");
        verify(aiRewriteService, times(2))
                .rewriteWithFeedback(anyString(), anyString(), anyInt(), anyString());
    }

    @Test
    void nativeHumanizeFailsWhenRetryStillHasNoSubstantiveChange() {
        AiRewriteService aiRewriteService = mock(AiRewriteService.class);
        when(aiRewriteService.rewriteWithFeedback(anyString(), anyString(), anyInt(), anyString()))
                .thenReturn(ORIGINAL, "该平台负责核对资料 并记录现场处理情况");
        DefaultWorkflowRewriteService service = service(aiRewriteService);

        assertThatThrownBy(() -> service.execute(ORIGINAL, "humanize"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("连续两次未产生真实文字变化");
        verify(aiRewriteService, times(2))
                .rewriteWithFeedback(anyString(), anyString(), anyInt(), anyString());
    }

    @Test
    void nativeHumanizeAcceptsHighSimilarityWhenRealWordsChanged() {
        AiRewriteService aiRewriteService = mock(AiRewriteService.class);
        String changed = "该平台负责复核资料，并记录现场处理情况。";
        when(aiRewriteService.rewriteWithFeedback(anyString(), anyString(), anyInt(), anyString()))
                .thenReturn(changed);
        DefaultWorkflowRewriteService service = service(aiRewriteService);

        assertThat(service.execute(ORIGINAL, "humanize@CNKI").getRewrittenText()).isEqualTo(changed);
        verify(aiRewriteService, times(1))
                .rewriteWithFeedback(anyString(), anyString(), anyInt(), anyString());
    }

    @Test
    void nativeHumanizeRejectsAResponseThatChangesProtectedNumbers() {
        AiRewriteService aiRewriteService = mock(AiRewriteService.class);
        String original = "2025年该平台核对了36份资料。";
        when(aiRewriteService.rewriteWithFeedback(anyString(), anyString(), anyInt(), anyString()))
                .thenReturn("2026年该平台复核了36份资料。");
        DefaultWorkflowRewriteService service = service(aiRewriteService);

        assertThatThrownBy(() -> service.execute(original, "humanize"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("缺少受保护内容占位符");
        verify(aiRewriteService, times(1))
                .rewriteWithFeedback(anyString(), anyString(), anyInt(), anyString());
    }

    @Test
    void dayaRouteDoesNotUseTheNativeUnchangedRetryGate() {
        AiRewriteService aiRewriteService = mock(AiRewriteService.class);
        when(aiRewriteService.rewriteWithFeedback(anyString(), anyString(), anyInt(), anyString()))
                .thenReturn(ORIGINAL);
        DefaultWorkflowRewriteService service = service(aiRewriteService);

        assertThat(service.execute(ORIGINAL, "humanize@DAYA").getRewrittenText()).isEqualTo(ORIGINAL);
        verify(aiRewriteService, times(1))
                .rewriteWithFeedback(anyString(), anyString(), anyInt(), anyString());
    }

    @Test
    void rewriteModeKeepsItsExistingSingleCallFlow() {
        AiRewriteService aiRewriteService = mock(AiRewriteService.class);
        when(aiRewriteService.rewriteWithFeedback(anyString(), anyString(), anyInt(), anyString()))
                .thenReturn(ORIGINAL);
        DefaultWorkflowRewriteService service = service(aiRewriteService);

        assertThat(service.execute(ORIGINAL, "rewrite").getRewrittenText()).isEqualTo(ORIGINAL);
        verify(aiRewriteService, times(1))
                .rewriteWithFeedback(anyString(), anyString(), anyInt(), anyString());
    }

    private DefaultWorkflowRewriteService service(AiRewriteService aiRewriteService) {
        return new DefaultWorkflowRewriteService(aiRewriteService, new TextStructureProtector());
    }
}
