package com.dropai.rewrite.service.impl;

import com.dropai.rewrite.config.DoubaoProperties;
import com.dropai.rewrite.mapper.DocumentJobMapper;
import com.dropai.rewrite.service.AiRewriteService;
import com.dropai.rewrite.service.WorkflowRewriteService;
import com.dropai.rewrite.vo.DocumentRewriteJobVO;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.poi.util.Units;
import org.apache.poi.xwpf.usermodel.*;
import org.apache.poi.wp.usermodel.HeaderFooterType;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.io.ByteArrayInputStream;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.zip.ZipInputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class DocumentRewriteImagePreservationTest {
    @TempDir Path temp;
    private static final String ORIGINAL = "现场人员先核对庭院布局，再记录各区域的使用情况。";
    private static final String REWRITTEN = "庭院布局由现场人员核对，各区域的使用情况也需要记录。";
    private static final byte[] PNG = Base64.getDecoder().decode(
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+jRZkAAAAASUVORK5CYII=");

    @ParameterizedTest
    @CsvSource({"humanize,changed", "rewrite,changed", "double,changed",
            "humanize,error", "rewrite,error", "double,error",
            "humanize,quality", "rewrite,quality", "double,quality",
            "humanize,unchanged", "rewrite,unchanged", "double,unchanged",
            "humanize,complex", "rewrite,complex", "double,complex"})
    void processingPreservesImagesOnChangedTextAndModelFallback(String mode, String outcome) throws Exception {
        boolean retained = !"changed".equals(outcome);
        Path input = temp.resolve("input.docx");
        Path output = temp.resolve("output.docx");
        try (XWPFDocument document = new XWPFDocument()) {
            document.createParagraph().createRun().setText("摘要");
            document.createParagraph().createRun().setText("第一章 绪论");
            XWPFParagraph paragraph = document.createParagraph();
            XWPFRun run = paragraph.createRun();
            run.setText(ORIGINAL.substring(0, 12));
            if ("complex".equals(outcome)) run.addBreak();
            picture(run);
            run.setText(ORIGINAL.substring(12));
            var table = document.createTable(2, 2);
            for (int i = 0; i < 2; i++) {
                picture(table.getRow(0).getCell(i).getParagraphs().get(0).createRun());
                table.getRow(1).getCell(i).setText("图片来源与说明");
            }
            picture(document.createParagraph().createRun());
            picture(document.createHeader(HeaderFooterType.DEFAULT).createParagraph().createRun());
            picture(document.createFooter(HeaderFooterType.DEFAULT).createParagraph().createRun());
            document.createParagraph().createRun().setText("参考文献");
            try (var stream = Files.newOutputStream(input)) { document.write(stream); }
        }
        String sourceXml;
        String sourceDrawing;
        String sourceTable;
        String original;
        try (var stream = Files.newInputStream(input); var document = new XWPFDocument(stream)) {
            sourceXml = document.getDocument().xmlText();
            original = document.getParagraphs().get(2).getText();
            sourceDrawing = document.getParagraphs().get(2).getRuns().get(0).getCTR().getDrawingArray(0).xmlText();
            sourceTable = document.getTables().get(0).getCTTbl().xmlText();
        }
        WorkflowRewriteService workflow = mock(WorkflowRewriteService.class);
        if ("error".equals(outcome)) when(workflow.execute(anyString(), anyString())).thenThrow(new IllegalStateException("fixture failure"));
        else if ("quality".equals(outcome)) when(workflow.execute(anyString(), anyString())).thenThrow(new RewriteQualityGateException("fixture quality fallback"));
        else when(workflow.execute(anyString(), anyString())).thenAnswer(invocation -> {
            var result = new WorkflowRewriteService.WorkflowRewriteResult();
            result.setRewrittenText("unchanged".equals(outcome) ? invocation.getArgument(0) : REWRITTEN);
            return result;
        });
        AiRewriteService ai = mock(AiRewriteService.class);
        when(ai.lastCallProvider()).thenReturn("test-model");
        var service = new DocumentRewriteServiceImpl(workflow, ai, new DoubaoProperties(),
                mock(DocumentJobMapper.class), new ObjectMapper(), null, null);
        DocumentRewriteJobVO job = new DocumentRewriteJobVO();
        job.setJobId("images-" + mode + "-" + outcome);
        job.setMode(mode);
        job.setModeName("图片回归测试");
        job.setPlatform("GENERAL");
        Field field = DocumentRewriteServiceImpl.class.getDeclaredField("jobs");
        field.setAccessible(true);
        @SuppressWarnings("unchecked")
        var jobs = (Map<String, DocumentRewriteJobVO>) field.get(service);
        jobs.put(job.getJobId(), job);
        try {
            service.process(job.getJobId(), input, output);
            assertThat(job.getStatus()).isEqualTo("SUCCESS");
            assertThat(job.getTotalParagraphs()).isEqualTo(1);
            assertThat(job.getRewrittenParagraphs()).isEqualTo(retained ? 0 : 1);
            assertThat(job.getParagraphs()).allSatisfy(p -> {
                assertThat(p.getStatus()).isEqualTo("SUCCESS");
                assertThat(p.getRewrittenText()).isEqualTo(retained ? original : REWRITTEN);
            });
            int calls = "double".equals(mode) && !"error".equals(outcome) && !"quality".equals(outcome) ? 2 : 1;
            verify(workflow, times(calls)).execute(anyString(), anyString());
            verifyNoMoreInteractions(workflow);
            try (var stream = Files.newInputStream(output); var result = new XWPFDocument(stream)) {
                var paragraph = result.getParagraphs().get(2);
                assertThat(paragraph.getText()).isEqualTo(retained ? original : REWRITTEN);
                assertThat(paragraph.getRuns().stream().mapToInt(r -> r.getCTR().sizeOfDrawingArray()).sum()).isEqualTo(1);
                assertThat(paragraph.getRuns().get(0).getCTR().getDrawingArray(0).xmlText()).isEqualTo(sourceDrawing);
                assertThat(result.getTables().get(0).getCTTbl().xmlText()).isEqualTo(sourceTable);
                if (retained) assertThat(result.getDocument().xmlText()).isEqualTo(sourceXml);
            }
            var originalParts = mediaAndRelations(input);
            var resultParts = mediaAndRelations(output);
            assertThat(resultParts.keySet()).isEqualTo(originalParts.keySet());
            originalParts.forEach((name, bytes) -> assertThat(resultParts.get(name)).as(name).isEqualTo(bytes));
        } finally { service.shutdown(); }
    }

    private static void picture(XWPFRun run) throws Exception {
        run.addPicture(new ByteArrayInputStream(PNG), Document.PICTURE_TYPE_PNG,
                "fixture.png", Units.toEMU(80), Units.toEMU(40));
    }

    private static Map<String, byte[]> mediaAndRelations(Path file) throws Exception {
        Map<String, byte[]> parts = new LinkedHashMap<>();
        try (ZipInputStream zip = new ZipInputStream(Files.newInputStream(file))) {
            for (var entry = zip.getNextEntry(); entry != null; entry = zip.getNextEntry()) {
                if (entry.getName().startsWith("word/media/") || entry.getName().endsWith(".rels")
                        || entry.getName().matches("word/(header|footer).*\\.xml"))
                    parts.put(entry.getName(), zip.readAllBytes());
            }
        }
        return parts;
    }
}
