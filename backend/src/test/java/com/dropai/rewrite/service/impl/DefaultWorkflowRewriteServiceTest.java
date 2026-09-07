package com.dropai.rewrite.service.impl;

import com.dropai.rewrite.service.AiRewriteService;
import com.dropai.rewrite.service.TextStructureProtector;
import com.dropai.rewrite.service.WorkflowRewriteService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
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
    void nativeHumanizeAcceptsIdenticalValidModelOutputWithoutRetry() {
        AiRewriteService aiRewriteService = mock(AiRewriteService.class);
        when(aiRewriteService.rewriteWithFeedback(anyString(), anyString(), anyInt(), anyString()))
                .thenReturn(ORIGINAL, "资料由平台复核，现场处理情况随后登记。");
        DefaultWorkflowRewriteService service = service(aiRewriteService);

        WorkflowRewriteService.WorkflowRewriteResult result = service.execute(ORIGINAL, "humanize");

        assertThat(result.getRewrittenText()).isEqualTo(ORIGINAL);
        ArgumentCaptor<String> feedback = ArgumentCaptor.forClass(String.class);
        verify(aiRewriteService, times(1))
                .rewriteWithFeedback(anyString(), anyString(), anyInt(), feedback.capture());
        assertThat(feedback.getAllValues().get(0)).isEmpty();
        assertThat(result.getWorkflowSteps())
                .extracting(step -> step.getNodeType())
                .doesNotContain("UNCHANGED_RETRY", "PROTECTED_CONTENT_RETRY");
    }

    @Test
    void nativeHumanizeAcceptsValidOutputThatOnlyChangesPunctuationAndWhitespace() {
        AiRewriteService aiRewriteService = mock(AiRewriteService.class);
        when(aiRewriteService.rewriteWithFeedback(anyString(), anyString(), anyInt(), anyString()))
                .thenReturn("该平台负责核对资料 并记录现场处理情况", "平台复核资料，现场情况另行记录。");
        DefaultWorkflowRewriteService service = service(aiRewriteService);

        assertThat(service.execute(ORIGINAL, "humanize@WEIPU").getRewrittenText())
                .isEqualTo("该平台负责核对资料 并记录现场处理情况");
        verify(aiRewriteService, times(1))
                .rewriteWithFeedback(anyString(), anyString(), anyInt(), anyString());
    }

    @Test
    void nativeHumanizeAcceptsValidOutputWithoutMeasuringSubstantiveDifference() {
        AiRewriteService aiRewriteService = mock(AiRewriteService.class);
        when(aiRewriteService.rewriteWithFeedback(anyString(), anyString(), anyInt(), anyString()))
                .thenReturn(ORIGINAL + "✅", "平台复核资料，现场情况另行记录。");
        DefaultWorkflowRewriteService service = service(aiRewriteService);

        assertThat(service.execute(ORIGINAL, "humanize").getRewrittenText())
                .isEqualTo(ORIGINAL + "✅");
        verify(aiRewriteService, times(1))
                .rewriteWithFeedback(anyString(), anyString(), anyInt(), anyString());
    }

    @Test
    void nativeHumanizeDoesNotRetryACompletedModelCallSolelyToForceChanges() {
        AiRewriteService aiRewriteService = mock(AiRewriteService.class);
        when(aiRewriteService.rewriteWithFeedback(anyString(), anyString(), anyInt(), anyString()))
                .thenReturn(ORIGINAL, "该平台负责核对资料 并记录现场处理情况");
        DefaultWorkflowRewriteService service = service(aiRewriteService);

        assertThat(service.execute(ORIGINAL, "humanize").getRewrittenText()).isEqualTo(ORIGINAL);
        verify(aiRewriteService, times(1))
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
    void nativeHumanizeAcceptsWhenAcademicPolishTurnsTheDraftBackIntoTheOriginal() {
        AiRewriteService aiRewriteService = mock(AiRewriteService.class);
        String original = "该平台记录了较多资料。";
        when(aiRewriteService.rewriteWithFeedback(anyString(), anyString(), anyInt(), anyString()))
                .thenReturn("该平台记录了很多资料。", "较多资料由该平台记录。 ");
        DefaultWorkflowRewriteService service = service(aiRewriteService);

        WorkflowRewriteService.WorkflowRewriteResult result = service.execute(original, "humanize");

        assertThat(result.getRewrittenText()).isEqualTo(original);
        ArgumentCaptor<String> feedback = ArgumentCaptor.forClass(String.class);
        verify(aiRewriteService, times(1))
                .rewriteWithFeedback(anyString(), anyString(), anyInt(), feedback.capture());
        assertThat(feedback.getValue()).isEmpty();
        assertThat(result.getWorkflowSteps())
                .extracting(step -> step.getNodeType())
                .doesNotContain("UNCHANGED_RETRY");
    }

    @Test
    void nativeHumanizeAcceptsWhenTemplateCleanupReturnsTheOriginal() {
        AiRewriteService aiRewriteService = mock(AiRewriteService.class);
        String original = "该平台记录了较多资料。";
        when(aiRewriteService.rewriteWithFeedback(anyString(), anyString(), anyInt(), anyString()))
                .thenReturn("首先，该平台记录了较多资料。");
        DefaultWorkflowRewriteService service = service(aiRewriteService);

        assertThat(service.execute(original, "humanize").getRewrittenText()).isEqualTo(original);
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
        verify(aiRewriteService, times(2))
                .rewriteWithFeedback(anyString(), anyString(), anyInt(), anyString());
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "[[DROP_AI_PROTECTED_0]]年共核对资料。",
            "[[DROP_AI_PROTECTED_0]][[DROP_AI_PROTECTED_0]]年共核对[[DROP_AI_PROTECTED_1]]份资料。",
            "共核对[[DROP_AI_PROTECTED_1]]份资料，年份为[[DROP_AI_PROTECTED_0]]。"
    })
    void nativeHumanizeRetriesProtectedContentIntegrityOnceAndAcceptsIdenticalResult(String invalidDraft) {
        AiRewriteService aiRewriteService = mock(AiRewriteService.class);
        String original = "2025年共核对36份资料。";
        when(aiRewriteService.rewriteWithFeedback(anyString(), anyString(), anyInt(), anyString()))
                .thenReturn(invalidDraft)
                .thenAnswer(invocation -> invocation.getArgument(0));

        WorkflowRewriteService.WorkflowRewriteResult result = service(aiRewriteService).execute(original, "humanize");

        assertThat(result.getRewrittenText()).isEqualTo(original);
        ArgumentCaptor<String> feedback = ArgumentCaptor.forClass(String.class);
        verify(aiRewriteService, times(2))
                .rewriteWithFeedback(anyString(), anyString(), anyInt(), feedback.capture());
        assertThat(feedback.getAllValues().get(0)).isEmpty();
        assertThat(feedback.getAllValues().get(1))
                .contains("未通过受保护内容完整性检查", "每个恰好出现一次且保持原顺序");
        assertThat(result.getWorkflowSteps()).extracting(step -> step.getNodeType())
                .contains("PROTECTED_CONTENT_RETRY").doesNotContain("UNCHANGED_RETRY");
    }

    @Test
    void scheduleParagraphIsActuallySubmittedAndCanStayIdenticalAfterMissingMinuteTokenRetry() {
        AiRewriteService aiRewriteService = mock(AiRewriteService.class);
        String original = "（3）中午11:30-13:30午间餐饮休闲";
        when(aiRewriteService.rewriteWithFeedback(anyString(), anyString(), anyInt(), anyString()))
                .thenAnswer(invocation -> ((String) invocation.getArgument(0)).replace("[[DROP_AI_PROTECTED_4]]", ""))
                .thenAnswer(invocation -> invocation.getArgument(0));

        assertThat(service(aiRewriteService).execute(original, "humanize").getRewrittenText())
                .isEqualTo(original);
        ArgumentCaptor<String> text = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> feedback = ArgumentCaptor.forClass(String.class);
        verify(aiRewriteService, times(2))
                .rewriteWithFeedback(text.capture(), anyString(), anyInt(), feedback.capture());
        assertThat(text.getAllValues().get(0)).contains("中午", "午间餐饮休闲", "[[DROP_AI_PROTECTED_4]]");
        assertThat(text.getAllValues().get(1)).isEqualTo(text.getAllValues().get(0));
        assertThat(feedback.getAllValues().get(1)).contains("缺少受保护内容占位符：[[DROP_AI_PROTECTED_4]]");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "\n\t", "【humanize】优化结果：", "值得注意的是", "首先"})
    void nativeHumanizeRejectsBlankOrCleanupOnlyModelOutput(String draft) {
        AiRewriteService aiRewriteService = mock(AiRewriteService.class);
        when(aiRewriteService.rewriteWithFeedback(anyString(), anyString(), anyInt(), anyString()))
                .thenReturn(draft);

        assertThatThrownBy(() -> service(aiRewriteService).execute(ORIGINAL, "humanize"))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("空");
        verify(aiRewriteService, times(1))
                .rewriteWithFeedback(anyString(), anyString(), anyInt(), anyString());
    }

    @Test
    void nativeHumanizePropagatesCallFailureWithoutPretendingOriginalWasProcessed() {
        AiRewriteService aiRewriteService = mock(AiRewriteService.class);
        IllegalStateException transportFailure = new IllegalStateException("模型调用连接失败");
        when(aiRewriteService.rewriteWithFeedback(anyString(), anyString(), anyInt(), anyString()))
                .thenThrow(transportFailure);

        assertThatThrownBy(() -> service(aiRewriteService).execute(ORIGINAL, "humanize"))
                .isSameAs(transportFailure);
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
