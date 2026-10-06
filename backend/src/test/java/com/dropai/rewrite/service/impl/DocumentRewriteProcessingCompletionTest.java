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
    void gezidaRejectsDoubleBeforeChargingOrReservingRequest() {
        var points = mock(com.dropai.rewrite.service.PointService.class);
        var mapper = mock(DocumentJobMapper.class);
        var service = new DocumentRewriteServiceImpl(mock(WorkflowRewriteService.class), mock(AiRewriteService.class),
                new DoubaoProperties(), mapper, new ObjectMapper(), points, null);
        com.dropai.rewrite.auth.AuthContext.setUserId(1L);
        try {
            for (int i = 0; i < 2; i++) {
                org.assertj.core.api.Assertions.assertThatThrownBy(() -> service.submit(null, "double", "GEZIDA", "same-request"))
                        .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("仅支持降AI");
            }
            verifyNoInteractions(points, mapper);
        } finally {
            com.dropai.rewrite.auth.AuthContext.clear();
            service.shutdown();
        }
    }

    @Test
    void gezidaDistinguishesChapterReferencesFromCustomHeadings() throws Exception {
        var service = new DocumentRewriteServiceImpl(mock(WorkflowRewriteService.class), mock(AiRewriteService.class),
                new DoubaoProperties(), mock(DocumentJobMapper.class), new ObjectMapper(), null, null);
        var method = DocumentRewriteServiceImpl.class.getDeclaredMethod("isGezidaHeadingParagraph", XWPFParagraph.class, String.class);
        method.setAccessible(true);
        try (var doc = new XWPFDocument()) {
            var heading = org.openxmlformats.schemas.wordprocessingml.x2006.main.CTStyle.Factory.newInstance();
            heading.setStyleId("custom2");
            heading.addNewName().setVal("Aa 2级标题");
            doc.createStyles().addStyle(new org.apache.poi.xwpf.usermodel.XWPFStyle(heading));
            var p = doc.createParagraph();
            p.setStyle("custom2");
            assertThat(method.invoke(service, p, "（一）选题背景与意义")).isEqualTo(true);
            var body = doc.createParagraph();
            assertThat(method.invoke(service, body, "第三章通过现有指标分析差异，并对照行业资料讨论结果。")).isEqualTo(false);
            assertThat(method.invoke(service, body, "第三章 薪酬公平性分析")).isEqualTo(true);
        } finally { service.shutdown(); }
    }

    @Test
    void gezidaProcessesAbstractAndBodyOnceAndDoesNotDeleteSentencesForLength() throws Exception {
        WorkflowRewriteService workflow = mock(WorkflowRewriteService.class);
        String expanded = "已有材料的口径需要明确。".repeat(18) + "末句证据必须保留。";
        when(workflow.execute(anyString(), anyString())).thenAnswer(call -> {
            var value = new WorkflowRewriteService.WorkflowRewriteResult();
            value.setRewrittenText(expanded);
            return value;
        });
        AiRewriteService ai = mock(AiRewriteService.class);
        var service = new DocumentRewriteServiceImpl(workflow, ai, new DoubaoProperties(),
                mock(DocumentJobMapper.class), new ObjectMapper(), null, null);
        Path input = temp.resolve("gezida-input.docx");
        Path output = temp.resolve("gezida-output.docx");
        try (var doc = new XWPFDocument()) {
            for (String text : new String[]{"摘要", "该研究比较岗位数据，并保留原有统计边界。", "1 绪论", "本节依据已有材料讨论统计对象和研究方法。", "参考文献", "[1] 原有参考文献。"}) {
                doc.createParagraph().createRun().setText(text);
            }
            try (var stream = Files.newOutputStream(input)) { doc.write(stream); }
        }
        var job = new DocumentRewriteJobVO();
        job.setJobId("gezida-single-pass-test");
        job.setMode("humanize");
        job.setModeName("智能降AI");
        job.setPlatform("GEZIDA");
        Field field = DocumentRewriteServiceImpl.class.getDeclaredField("jobs");
        field.setAccessible(true);
        @SuppressWarnings("unchecked")
        Map<String, DocumentRewriteJobVO> jobs = (Map<String, DocumentRewriteJobVO>) field.get(service);
        jobs.put(job.getJobId(), job);
        try {
            service.process(job.getJobId(), input, output);
            assertThat(job.getStatus()).isEqualTo("SUCCESS");
            assertThat(job.getTotalParagraphs()).isEqualTo(2);
            verify(workflow, times(2)).execute(anyString(), org.mockito.ArgumentMatchers.eq("humanize@GEZIDA"));
            verifyNoMoreInteractions(workflow);
            try (var stream = Files.newInputStream(output); var doc = new XWPFDocument(stream)) {
                assertThat(doc.getParagraphs().get(1).getText()).isEqualTo(expanded);
                assertThat(doc.getParagraphs().get(3).getText()).isEqualTo(expanded);
                assertThat(doc.getParagraphs().get(5).getText()).isEqualTo("[1] 原有参考文献。");
            }
        } finally { service.shutdown(); }
    }

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
