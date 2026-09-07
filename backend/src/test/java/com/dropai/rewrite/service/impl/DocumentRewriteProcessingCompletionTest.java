package com.dropai.rewrite.service.impl;

import com.dropai.rewrite.config.DoubaoProperties;
import com.dropai.rewrite.mapper.DocumentJobMapper;
import com.dropai.rewrite.service.AiRewriteService;
import com.dropai.rewrite.service.WorkflowRewriteService;
import com.dropai.rewrite.vo.DocumentRewriteJobVO;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class DocumentRewriteProcessingCompletionTest {
    @TempDir
    Path temp;

    @Test
    void allSameModelResultsGenerateSuccessAndPreserveParagraphRuns() throws Exception {
        WorkflowRewriteService workflow = mock(WorkflowRewriteService.class);
        when(workflow.execute(anyString(), anyString())).thenAnswer(invocation -> {
            var result = new WorkflowRewriteService.WorkflowRewriteResult();
            result.setRewrittenText(invocation.getArgument(0));
            return result;
        });
        AiRewriteService ai = mock(AiRewriteService.class);
        when(ai.lastCallProvider()).thenReturn("test-model");
        DoubaoProperties properties = new DoubaoProperties();
        properties.setDocumentConcurrency(32);
        var service = new DocumentRewriteServiceImpl(workflow, ai, properties,
                mock(DocumentJobMapper.class), new ObjectMapper(), null, null);
        String schedule = "（3）中午11:30-13:30午间餐饮休闲";
        String body = "该平台负责核对资料，并记录现场处理情况[1]。";
        String bodyXml;
        Path input = temp.resolve("input.docx");
        Path output = temp.resolve("output.docx");
        try (XWPFDocument document = new XWPFDocument()) {
            document.createParagraph().createRun().setText("摘要");
            XWPFParagraph paragraph = document.createParagraph();
            paragraph.setStyle("Normal");
            var bold = paragraph.createRun();
            bold.setBold(true);
            bold.setText("该平台负责核对资料，");
            var normal = paragraph.createRun();
            normal.setItalic(true);
            normal.setText("并记录现场处理情况");
            var reference = paragraph.createRun();
            reference.setColor("2255AA");
            reference.setText("[1]。");
            document.createParagraph().createRun().setText(schedule);
            document.createParagraph().createRun().setText("参考文献");
            try (var stream = Files.newOutputStream(input)) {
                document.write(stream);
            }
        }
        // Compare the persisted OOXML on both sides, not POI's pre-serialization namespace aliases.
        try (var stream = Files.newInputStream(input); XWPFDocument document = new XWPFDocument(stream)) {
            bodyXml = document.getParagraphs().get(1).getCTP().xmlText();
        }
        DocumentRewriteJobVO job = new DocumentRewriteJobVO();
        job.setJobId("same-valid-output-test");
        job.setMode("humanize");
        job.setModeName("智能降AI");
        job.setPlatform("GENERAL");
        Field field = DocumentRewriteServiceImpl.class.getDeclaredField("jobs");
        field.setAccessible(true);
        @SuppressWarnings("unchecked")
        Map<String, DocumentRewriteJobVO> jobs = (Map<String, DocumentRewriteJobVO>) field.get(service);
        jobs.put(job.getJobId(), job);
        try {
            service.process(job.getJobId(), input, output);
            assertThat(job.getStatus()).isEqualTo("SUCCESS");
            assertThat(job.getTotalParagraphs()).isEqualTo(2);
            assertThat(job.getProcessedParagraphs()).isEqualTo(2);
            assertThat(job.getRewrittenParagraphs()).isZero();
            assertThat(job.getMessage()).contains("已成功处理 2", "处理后内容相同 2");
            assertThat(job.getParagraphs()).allSatisfy(paragraph -> {
                assertThat(paragraph.getStatus()).isEqualTo("SUCCESS");
                assertThat(paragraph.getMessage()).contains("模型处理后内容相同");
                assertThat(paragraph.getRewrittenText()).isEqualTo(paragraph.getOriginalText());
            });
            verify(workflow).execute(body, "humanize");
            verify(workflow).execute(schedule, "humanize");
            verifyNoMoreInteractions(workflow);
            try (var stream = Files.newInputStream(output); XWPFDocument result = new XWPFDocument(stream)) {
                assertThat(result.getParagraphs().get(1).getCTP().xmlText()).isEqualTo(bodyXml);
                assertThat(result.getParagraphs().get(2).getText()).isEqualTo(schedule);
            }
        } finally {
            service.shutdown();
        }
    }
}
