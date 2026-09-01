package com.dropai.rewrite.external;

import com.dropai.rewrite.entity.DocumentJobRecord;
import com.dropai.rewrite.mapper.DocumentJobMapper;
import com.dropai.rewrite.service.DocumentCharacterCountService;
import com.dropai.rewrite.service.PointService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
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
    void restartClosesCrashWindowWithoutPollingOrSubmittingAgain() throws Exception {
        Fixture fixture = fixture();
        String jobId = "recovered-" + UUID.randomUUID().toString().replace("-", "");
        Path result = Path.of("storage", "outputs", jobId + "-ai-optimized.docx");
        Files.createDirectories(result.getParent());
        Files.write(result, new byte[]{1});
        try {
            when(fixture.documentJobMapper.selectById(jobId))
                    .thenReturn(documentJob(jobId, "SUCCESS"));
            XuejieExternalJobStateRepository.State state = new XuejieExternalJobStateRepository.State(
                    jobId, 7L, "paper.docx", XuejiePlatform.DAYA.name(),
                    XuejieRewriteMode.HUMANIZE.apiValue(), "DOCUMENT_HUMANIZE", "文档降AI", 10,
                    XuejieExternalJobStateRepository.PROCESSING, "", "doubao:DAYA", "NONE",
                    LocalDateTime.now(), LocalDateTime.now());

            service.recover(state);

            verify(fixture.stateRepository).stage(jobId,
                    XuejieExternalJobStateRepository.COMPLETED, null, "doubao_completed");
            verify(fixture.documentJobMapper, never()).updateById(
                    org.mockito.ArgumentMatchers.<DocumentJobRecord>any());
            verifyNoInteractions(fixture.processor);
        } finally {
            Files.deleteIfExists(result);
        }
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
