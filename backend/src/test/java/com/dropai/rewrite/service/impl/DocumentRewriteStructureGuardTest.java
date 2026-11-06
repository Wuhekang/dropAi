package com.dropai.rewrite.service.impl;

import com.dropai.rewrite.config.DoubaoProperties;
import com.dropai.rewrite.mapper.DocumentJobMapper;
import com.dropai.rewrite.service.AiRewriteService;
import com.dropai.rewrite.service.WorkflowRewriteService;
import com.dropai.rewrite.vo.DocumentRewriteJobVO;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.poi.util.Units;
import org.apache.poi.xwpf.usermodel.Document;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.STFldCharType;

import java.io.ByteArrayInputStream;
import java.lang.reflect.Field;
import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DocumentRewriteStructureGuardTest {
    @TempDir
    Path temp;

    private static final String REWRITTEN = "现场资料已核对，处理情况另有记录。";
    private static final byte[] PNG = Base64.getDecoder().decode(
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+jRZkAAAAASUVORK5CYII=");

    @Test
    void humanizeSkipsFieldsAndBookmarksAndKeepsRunFormattingAndDrawings() throws Exception {
        Path input = temp.resolve("input.docx");
        Path output = temp.resolve("output.docx");
        try (XWPFDocument document = new XWPFDocument()) {
            document.createParagraph().createRun().setText("摘要");
            XWPFParagraph ref = document.createParagraph();
            ref.createRun().getCTR().addNewFldChar().setFldCharType(STFldCharType.BEGIN);
            ref.createRun().getCTR().addNewInstrText().setStringValue(" REF _Ref148898 \\h ");
            ref.createRun().getCTR().addNewFldChar().setFldCharType(STFldCharType.SEPARATE);
            ref.createRun().setText("图1-1");
            ref.createRun().getCTR().addNewFldChar().setFldCharType(STFldCharType.END);
            ref.createRun().setText("所示结构在后续分析中保持稳定，编号不能改写。");

            XWPFParagraph bookmark = document.createParagraph();
            var start = bookmark.getCTP().addNewBookmarkStart();
            start.setName("_Ref148898");
            start.setId(BigInteger.ONE);
            bookmark.createRun().setText("这段被书签包裹的正文必须原样保留，不能送去改写。");
            bookmark.getCTP().addNewBookmarkEnd().setId(BigInteger.ONE);

            XWPFParagraph plain = document.createParagraph();
            plain.setStyle("Normal");
            var bold = plain.createRun();
            bold.setBold(true);
            bold.setText("该平台负责核对资料，");
            var italic = plain.createRun();
            italic.setItalic(true);
            italic.setText("并记录现场处理情况。");

            XWPFParagraph figure = document.createParagraph();
            var run = figure.createRun();
            run.setText("图片前面的文字用于说明现场情况，图片后面继续记录。");
            run.addPicture(new ByteArrayInputStream(PNG), Document.PICTURE_TYPE_PNG,
                    "figure.png", Units.toEMU(80), Units.toEMU(40));
            document.createParagraph().createRun().setText("参考文献");
            try (var stream = Files.newOutputStream(input)) {
                document.write(stream);
            }
        }

        String fieldXml;
        String bookmarkXml;
        String drawingXml;
        String plainProperties;
        try (var stream = Files.newInputStream(input); XWPFDocument document = new XWPFDocument(stream)) {
            fieldXml = document.getParagraphs().get(1).getCTP().xmlText();
            bookmarkXml = document.getParagraphs().get(2).getCTP().xmlText();
            drawingXml = document.getParagraphs().get(4).getRuns().get(0).getCTR().getDrawingArray(0).xmlText();
            plainProperties = document.getParagraphs().get(3).getCTP().getPPr().xmlText();
        }

        WorkflowRewriteService workflow = mock(WorkflowRewriteService.class);
        when(workflow.execute(anyString(), anyString())).thenAnswer(invocation -> {
            var result = new WorkflowRewriteService.WorkflowRewriteResult();
            result.setRewrittenText(REWRITTEN);
            return result;
        });
        DocumentRewriteServiceImpl service = service(workflow);
        DocumentRewriteJobVO job = job(service, "humanize-structure", "humanize");
        try {
            service.process(job.getJobId(), input, output);
            ArgumentCaptor<String> texts = ArgumentCaptor.forClass(String.class);
            verify(workflow, times(2)).execute(texts.capture(), anyString());
            assertThat(texts.getAllValues())
                    .noneMatch(text -> text.contains("REF") || text.contains("_Ref148898") || text.contains("书签"));
            try (var stream = Files.newInputStream(output); XWPFDocument result = new XWPFDocument(stream)) {
                assertThat(result.getParagraphs().get(1).getCTP().xmlText()).isEqualTo(fieldXml);
                assertThat(result.getParagraphs().get(2).getCTP().xmlText()).isEqualTo(bookmarkXml);
                XWPFParagraph plain = result.getParagraphs().get(3);
                assertThat(plain.getText()).isEqualTo(REWRITTEN);
                assertThat(plain.getRuns()).hasSize(2);
                assertThat(plain.getRuns().get(0).isBold()).isTrue();
                assertThat(plain.getRuns().get(1).isItalic()).isTrue();
                assertThat(plain.getCTP().getPPr().xmlText()).isEqualTo(plainProperties);
                XWPFParagraph figure = result.getParagraphs().get(4);
                assertThat(figure.getText()).isEqualTo(REWRITTEN);
                assertThat(figure.getRuns().get(0).getCTR().getDrawingArray(0).xmlText()).isEqualTo(drawingXml);
            }
        } finally {
            service.shutdown();
        }
    }

    @Test
    void rewriteStillRefillsByReplacingRuns() throws Exception {
        Path input = temp.resolve("rewrite.docx");
        Path output = temp.resolve("rewrite-out.docx");
        try (XWPFDocument document = new XWPFDocument()) {
            document.createParagraph().createRun().setText("摘要");
            document.createParagraph().createRun().setText("第一章 绪论");
            XWPFParagraph plain = document.createParagraph();
            plain.createRun().setBold(true);
            plain.getRuns().get(0).setText("该平台负责核对资料，");
            var italic = plain.createRun();
            italic.setItalic(true);
            italic.setText("并记录现场处理情况。");
            document.createParagraph().createRun().setText("参考文献");
            try (var stream = Files.newOutputStream(input)) {
                document.write(stream);
            }
        }
        WorkflowRewriteService workflow = mock(WorkflowRewriteService.class);
        when(workflow.execute(anyString(), anyString())).thenAnswer(invocation -> {
            var result = new WorkflowRewriteService.WorkflowRewriteResult();
            result.setRewrittenText(REWRITTEN);
            return result;
        });
        DocumentRewriteServiceImpl service = service(workflow);
        DocumentRewriteJobVO job = job(service, "rewrite-structure", "rewrite");
        try {
            service.process(job.getJobId(), input, output);
            try (var stream = Files.newInputStream(output); XWPFDocument result = new XWPFDocument(stream)) {
                XWPFParagraph plain = result.getParagraphs().get(2);
                assertThat(plain.getText()).isEqualTo(REWRITTEN);
                assertThat(plain.getRuns()).hasSize(1);
            }
        } finally {
            service.shutdown();
        }
    }

    private static DocumentRewriteServiceImpl service(WorkflowRewriteService workflow) {
        AiRewriteService ai = mock(AiRewriteService.class);
        when(ai.lastCallProvider()).thenReturn("test-model");
        return new DocumentRewriteServiceImpl(workflow, ai, new DoubaoProperties(),
                mock(DocumentJobMapper.class), new ObjectMapper(), null, null);
    }

    private static DocumentRewriteJobVO job(DocumentRewriteServiceImpl service, String jobId, String mode) throws Exception {
        DocumentRewriteJobVO job = new DocumentRewriteJobVO();
        job.setJobId(jobId);
        job.setMode(mode);
        job.setModeName(mode);
        job.setPlatform("GENERAL");
        Field field = DocumentRewriteServiceImpl.class.getDeclaredField("jobs");
        field.setAccessible(true);
        @SuppressWarnings("unchecked")
        Map<String, DocumentRewriteJobVO> jobs = (Map<String, DocumentRewriteJobVO>) field.get(service);
        jobs.put(jobId, job);
        return job;
    }
}
