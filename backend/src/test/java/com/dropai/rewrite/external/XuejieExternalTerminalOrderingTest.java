package com.dropai.rewrite.external;

import com.dropai.rewrite.entity.DocumentJobRecord;
import com.dropai.rewrite.mapper.DocumentJobMapper;
import com.dropai.rewrite.service.DocumentCharacterCountService;
import com.dropai.rewrite.service.PointService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InOrder;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class XuejieExternalTerminalOrderingTest {
    private XuejieExternalDocumentRewriteService service;

    @AfterEach
    void closeExecutor() {
        if (service != null) service.close();
    }

    @Test
    void finalizesDocumentSuccessBeforeMarkingExternalStateCompleted() {
        Fixture fixture = fixture();
        DocumentJobRecord record = documentJob("job-success", "RUNNING");
        when(fixture.documentJobMapper.selectById("job-success")).thenReturn(record);

        service.finalizeSuccessfulJob("job-success", "大雅", "doubao-local", "completed");

        InOrder order = inOrder(fixture.documentJobMapper, fixture.stateRepository);
        order.verify(fixture.documentJobMapper).selectById("job-success");
        order.verify(fixture.documentJobMapper).updateById(record);
        order.verify(fixture.stateRepository).stage("job-success",
                XuejieExternalJobStateRepository.COMPLETED, null, "doubao_completed");
        assertThat(record.getStatus()).isEqualTo("SUCCESS");
        assertThat(record.getProcessedParagraphs()).isEqualTo(1);
        assertThat(record.getRewrittenParagraphs()).isEqualTo(1);
    }

    @Test
    void finalizesPartialDayaRewriteWhenTheRemainingProcessedParagraphWasUnchanged() {
        Fixture fixture = fixture();
        DocumentJobRecord record = documentJob("job-partial-success", "RUNNING");
        when(fixture.documentJobMapper.selectById("job-partial-success")).thenReturn(record);

        service.finalizeSuccessfulJob("job-partial-success", "大雅",
                new PlatformDoubaoDocumentProcessor.ProcessingResult(
                        2, 2, 1, 0, java.util.List.of()));

        InOrder order = inOrder(fixture.documentJobMapper, fixture.stateRepository);
        order.verify(fixture.documentJobMapper).selectById("job-partial-success");
        order.verify(fixture.documentJobMapper).updateById(record);
        order.verify(fixture.stateRepository).stage("job-partial-success",
                XuejieExternalJobStateRepository.COMPLETED, null, "doubao_completed");
        assertThat(record.getStatus()).isEqualTo("SUCCESS");
        assertThat(record.getTotalParagraphs()).isEqualTo(2);
        assertThat(record.getProcessedParagraphs()).isEqualTo(2);
        assertThat(record.getRewrittenParagraphs()).isEqualTo(1);
        assertThat(record.getMessage()).contains(
                "2 个可处理段落均已获得有效模型结果", "1 段文字有修改", "1 段经模型处理后文字保持不变");
    }

    @Test
    void finalizesAllUnchangedValidModelResponsesWithoutClaimingTextChanges() {
        Fixture fixture = fixture();
        DocumentJobRecord record = documentJob("job-all-unchanged", "RUNNING");
        when(fixture.documentJobMapper.selectById("job-all-unchanged")).thenReturn(record);

        service.finalizeSuccessfulJob("job-all-unchanged", "大雅",
                new PlatformDoubaoDocumentProcessor.ProcessingResult(
                        2, 2, 0, 0, java.util.List.of()));

        assertThat(record.getStatus()).isEqualTo("SUCCESS");
        assertThat(record.getRewrittenParagraphs()).isZero();
        assertThat(record.getMessage()).contains("0 段文字有修改", "2 段经模型处理后文字保持不变");
        verify(fixture.stateRepository).stage("job-all-unchanged",
                XuejieExternalJobStateRepository.COMPLETED, null, "doubao_completed");
    }

    @Test
    void publishesPartialSuccessWhenFourOf182ParagraphsKeepTheOriginal() {
        Fixture fixture = fixture();
        DocumentJobRecord record = documentJob("job-partial-file", "RUNNING");
        when(fixture.documentJobMapper.selectById("job-partial-file")).thenReturn(record);

        service.finalizeSuccessfulJob("job-partial-file", "大雅",
                new PlatformDoubaoDocumentProcessor.ProcessingResult(
                        182, 182, 178, 4, java.util.List.of("p22：数字完整性校验未通过")));

        assertThat(record.getStatus()).isEqualTo("PARTIAL_SUCCESS");
        assertThat(record.getProcessedParagraphs()).isEqualTo(182);
        assertThat(record.getRewrittenParagraphs()).isEqualTo(178);
        assertThat(record.getMessage()).contains("部分完成", "可下载", "4 段处理失败", "保留原文和原格式")
                .doesNotContain("均已获得有效模型结果", "全文适配完成");
        verify(fixture.stateRepository).stage("job-partial-file",
                XuejieExternalJobStateRepository.COMPLETED, null, "doubao_partial_completed");
        com.dropai.rewrite.vo.DocumentRewriteJobVO job = ReflectionTestUtils.invokeMethod(service, "toJob", record);
        assertThat(job.getDownloadUrl()).isEqualTo("/api/document/rewrite/download/job-partial-file");
    }

    @Test
    void refusesPublicationWhenAllParagraphsHaveNoValidModelResponse() {
        Fixture fixture = fixture();
        assertThatThrownBy(() -> service.finalizeSuccessfulJob("job-all-call-failed", "大雅",
                new PlatformDoubaoDocumentProcessor.ProcessingResult(
                        2, 2, 0, 2, java.util.List.of())))
                .isInstanceOf(IllegalStateException.class);
        verifyNoInteractions(fixture.documentJobMapper, fixture.stateRepository);
    }

    @Test
    void rejectsImpossibleDayaCountersBeforePublishingSuccess() {
        Fixture fixture = fixture();

        assertThatThrownBy(() -> service.finalizeSuccessfulJob(
                "job-invalid-counts", "大雅",
                new PlatformDoubaoDocumentProcessor.ProcessingResult(
                        2, 2, 3, 0, java.util.List.of())))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("结果状态不完整");
        verifyNoInteractions(fixture.documentJobMapper, fixture.stateRepository);
    }

    @ParameterizedTest
    @ValueSource(strings = {"SUCCESS", "PARTIAL_SUCCESS"})
    void restartClosesCrashWindowWithoutPollingOrSubmittingAgain(String publishedStatus) throws Exception {
        Fixture fixture = fixture();
        String jobId = "recovered-" + UUID.randomUUID().toString().replace("-", "");
        Path result = Path.of("storage", "outputs", jobId + "-ai-optimized.docx");
        Files.createDirectories(result.getParent());
        Files.write(result, new byte[]{1});
        try {
            when(fixture.documentJobMapper.selectById(jobId))
                    .thenReturn(documentJob(jobId, publishedStatus));
            XuejieExternalJobStateRepository.State state = new XuejieExternalJobStateRepository.State(
                    jobId, 7L, "paper.docx", XuejiePlatform.DAYA.name(),
                    XuejieRewriteMode.HUMANIZE.apiValue(), "DOCUMENT_HUMANIZE", "文档降AI", 10,
                    XuejieExternalJobStateRepository.PROCESSING, "", "doubao:DAYA", "NONE",
                    LocalDateTime.now(), LocalDateTime.now());

            service.recover(state);

            verify(fixture.stateRepository).stage(jobId,
                    XuejieExternalJobStateRepository.COMPLETED, null,
                    "PARTIAL_SUCCESS".equals(publishedStatus) ? "doubao_partial_completed" : "doubao_completed");
            verify(fixture.documentJobMapper, never()).updateById(
                    org.mockito.ArgumentMatchers.<DocumentJobRecord>any());
            verifyNoInteractions(fixture.processor);
        } finally {
            Files.deleteIfExists(result);
        }
    }

    @Test
    void dayaHardFailureKeepsTheLastRealProgressInsteadOfResettingToZero() {
        Fixture fixture = fixture();
        String jobId = "job-daya-hard-failure";
        DocumentJobRecord record = documentJob(jobId, "RUNNING");
        when(fixture.documentJobMapper.selectById(jobId)).thenReturn(record);
        when(fixture.processor.process(
                any(Path.class), any(Path.class), eq(XuejiePlatform.DAYA),
                eq(XuejieRewriteMode.HUMANIZE),
                any(PlatformDoubaoDocumentProcessor.ProgressListener.class)))
                .thenThrow(new PlatformDoubaoDocumentProcessor.DayaProcessingException(
                        "尚有 72 段未通过不可放宽的完整性校验", 182, 182, 110, 72));

        ReflectionTestUtils.invokeMethod(service, "process",
                jobId, Path.of("storage", "uploads", jobId + "-missing.docx"),
                XuejieRewriteMode.HUMANIZE, XuejiePlatform.DAYA,
                "DOCUMENT_HUMANIZE", "文档降AI", 10);

        assertThat(record.getStatus()).isEqualTo("FAILED");
        assertThat(record.getTotalParagraphs()).isEqualTo(182);
        assertThat(record.getProcessedParagraphs()).isEqualTo(182);
        assertThat(record.getRewrittenParagraphs()).isEqualTo(110);
        assertThat(record.getMessage()).contains("72 段未通过不可放宽的完整性校验");
        verify(fixture.documentJobMapper, times(2)).updateById(record);
    }

    private Fixture fixture() {
        PlatformDoubaoDocumentProcessor processor = mock(PlatformDoubaoDocumentProcessor.class);
        XuejieExternalJobStateRepository stateRepository = mock(XuejieExternalJobStateRepository.class);
        DocumentJobMapper documentJobMapper = mock(DocumentJobMapper.class);
        service = new XuejieExternalDocumentRewriteService(
                processor,
                mock(XuejieDocxValidator.class),
                mock(XuejieExternalPointRefundService.class),
                stateRepository,
                documentJobMapper,
                mock(DocumentCharacterCountService.class),
                mock(PointService.class));
        return new Fixture(processor, stateRepository, documentJobMapper);
    }

    private DocumentJobRecord documentJob(String jobId, String status) {
        DocumentJobRecord record = new DocumentJobRecord();
        record.setJobId(jobId);
        record.setStatus(status);
        record.setCostPoints(10);
        record.setPointsCharged(true);
        return record;
    }

    private record Fixture(PlatformDoubaoDocumentProcessor processor,
                           XuejieExternalJobStateRepository stateRepository,
                           DocumentJobMapper documentJobMapper) {
    }
}
