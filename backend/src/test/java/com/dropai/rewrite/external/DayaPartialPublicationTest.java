package com.dropai.rewrite.external;

import com.dropai.rewrite.auth.AuthContext;
import com.dropai.rewrite.config.DoubaoProperties;
import com.dropai.rewrite.controller.DocumentRewriteController;
import com.dropai.rewrite.entity.DocumentJobRecord;
import com.dropai.rewrite.mapper.DocumentJobMapper;
import com.dropai.rewrite.service.impl.DocumentRewriteServiceImpl;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class DayaPartialPublicationTest {
    @TempDir Path temp;

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void protectedFallbackIsSuccessfulWhileServiceFailureRemainsPartialAndDownloadable(
            boolean protectedFallback) throws Exception {
        var processor = mock(PlatformDoubaoDocumentProcessor.class);
        var mapper = mock(DocumentJobMapper.class);
        var state = mock(XuejieExternalJobStateRepository.class);
        var refund = mock(XuejieExternalPointRefundService.class);
        var validator = new XuejieDocxValidator();
        var record = new DocumentJobRecord();
        record.setJobId("partial-download-test");
        record.setUserId(7L);
        record.setFileName("paper.docx");
        record.setSourceFeature("REWRITE");
        record.setPlatform("DAYA");
        record.setMode("humanize");
        record.setStatus("RUNNING");
        record.setCostPoints(10);
        record.setPointsCharged(true);
        record.setParagraphsJson("[]");
        when(mapper.selectById(record.getJobId())).thenReturn(record);
        when(mapper.selectOne(any())).thenReturn(record);
        Path input = temp.resolve("source.docx");
        writeDocx(input, "输入文件中尚未改写的正文。", "表中 13:30 的数据说明保持原文。");
        when(processor.process(any(), any(), any(), any(), any())).thenAnswer(call -> {
            Path output = call.getArgument(1);
            writeDocx(output, "已校验的改写正文。", "表中 13:30 的数据说明保持原文。");
            return new PlatformDoubaoDocumentProcessor.ProcessingResult(2, 2, 1,
                    protectedFallback ? 0 : 1, protectedFallback ? 1 : 0,
                    List.of(protectedFallback ? "p2：数字完整性校验失败，保留原文" : "p2：模型服务调用超时"));
        });
        var platformService = new XuejieExternalDocumentRewriteService(
                processor, validator, refund, state, mapper, null, null);
        var sharedService = new DocumentRewriteServiceImpl(null, null, new DoubaoProperties(),
                mapper, new ObjectMapper(), null, null);
        ReflectionTestUtils.setField(platformService, "outputDir", temp);
        ReflectionTestUtils.setField(sharedService, "outputDir", temp);
        var controller = new DocumentRewriteController(sharedService);
        try {
            ReflectionTestUtils.invokeMethod(platformService, "process", record.getJobId(), input,
                    XuejieRewriteMode.HUMANIZE, XuejiePlatform.DAYA,
                    "DOCUMENT_HUMANIZE", "文档降AI", 10);
            assertThat(record.getStatus()).isEqualTo(protectedFallback ? "SUCCESS" : "PARTIAL_SUCCESS");
            if (protectedFallback) {
                assertThat(record.getMessage()).isEqualTo("大雅 文档处理完成，结果文件已生成");
            } else {
                assertThat(record.getMessage()).contains("1 段因模型服务异常未完成", "可下载");
            }
            assertThat(record.getMessage()).doesNotContain("p2", "完整性校验");
            verifyNoInteractions(refund);
            verify(state).stage(record.getJobId(), XuejieExternalJobStateRepository.COMPLETED,
                    null, protectedFallback ? "doubao_completed" : "doubao_partial_completed");

            AuthContext.setUserId(7L);
            assertThat(sharedService.getJob(record.getJobId()).getDownloadUrl()).isNotBlank();
            var response = controller.download(record.getJobId());
            assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
            assertThat(response.getHeaders().getFirst("Content-Disposition")).contains("attachment;");
            try (var stream = response.getBody().getInputStream(); var result = new XWPFDocument(stream)) {
                assertThat(result.getParagraphs()).extracting(p -> p.getText())
                        .containsExactly("已校验的改写正文。", "表中 13:30 的数据说明保持原文。");
            }
            AuthContext.clear();
            assertThatThrownBy(() -> controller.download(record.getJobId()))
                    .isInstanceOf(IllegalStateException.class).hasMessageContaining("请先登录");
        } finally {
            AuthContext.clear();
            sharedService.shutdown();
            platformService.close();
        }
    }

    private void writeDocx(Path path, String... paragraphs) throws Exception {
        try (var doc = new XWPFDocument()) {
            for (String paragraph : paragraphs) doc.createParagraph().createRun().setText(paragraph);
            try (var stream = Files.newOutputStream(path)) { doc.write(stream); }
        }
    }
}
