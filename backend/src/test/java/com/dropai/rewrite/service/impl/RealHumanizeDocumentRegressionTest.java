package com.dropai.rewrite.service.impl;

import com.dropai.rewrite.config.DoubaoProperties;
import com.dropai.rewrite.mapper.DocumentJobMapper;
import com.dropai.rewrite.service.AiRewriteService;
import com.dropai.rewrite.service.WorkflowRewriteService;
import com.dropai.rewrite.vo.DocumentRewriteJobVO;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/** Optional local inputs, never uploaded or checked in; model calls are deliberately mocked. */
class RealHumanizeDocumentRegressionTest {
    @TempDir Path temporaryDirectory;

    @Test
    void originalManuscriptCoversBracketedAbstractAndPreservesWholeDocumentOnFallback() throws Exception {
        verifyDocument("dropai.test.learning.original", 99, true);
    }

    @Test
    void optimizedManuscriptCoversBracketedAbstractAndKeepsUnchangedDocumentStructure() throws Exception {
        verifyDocument("dropai.test.learning.optimized", 94, false);
    }

    private void verifyDocument(String property, int expectedTargets, boolean simulateFallback) throws Exception {
        String supplied = System.getProperty(property);
        Assumptions.assumeTrue(supplied != null && Files.isRegularFile(Path.of(supplied)),
                "Set " + property + " to enable the local manuscript regression");
        Path input = Path.of(supplied);
        String before;
        try (var stream = Files.newInputStream(input); var document = new XWPFDocument(stream)) {
            before = document.getDocument().xmlText();
        }
        var calls = new CopyOnWriteArrayList<String>();
        var workflow = mock(WorkflowRewriteService.class);
        when(workflow.execute(anyString(), anyString())).thenAnswer(invocation -> {
            String source = invocation.getArgument(0);
            calls.add(source);
            if (simulateFallback && source.startsWith("每学期开展")) {
                throw new IllegalStateException("Simulated model failure for fallback regression");
            }
            var result = new WorkflowRewriteService.WorkflowRewriteResult();
            result.setRewrittenText(source);
            return result;
        });
        var ai = mock(AiRewriteService.class);
        when(ai.lastCallProvider()).thenReturn("LOCAL_REGRESSION_MOCK");
        var properties = new DoubaoProperties();
        properties.setDocumentConcurrency(4);
        var service = new DocumentRewriteServiceImpl(workflow, ai, properties,
                mock(DocumentJobMapper.class), new ObjectMapper(), null, null);
        var job = new DocumentRewriteJobVO();
        job.setJobId("local-learning-" + (simulateFallback ? "fallback" : "unchanged"));
        job.setMode("humanize");
        job.setModeName("智能降AI");
        job.setPlatform("GENERAL");
        var field = DocumentRewriteServiceImpl.class.getDeclaredField("jobs");
        field.setAccessible(true);
        @SuppressWarnings("unchecked")
        var jobs = (Map<String, DocumentRewriteJobVO>) field.get(service);
        jobs.put(job.getJobId(), job);
        Path output = temporaryDirectory.resolve("roundtrip.docx");
        try {
            service.process(job.getJobId(), input, output);
            assertThat(job.getStatus()).isEqualTo("SUCCESS");
            assertThat(job.getTotalParagraphs()).isEqualTo(expectedTargets);
            assertThat(calls).hasSize(expectedTargets);
            assertThat(calls).anyMatch(text -> text.startsWith("[摘 要]"));
            assertThat(calls).noneMatch(text -> text.startsWith("[关键词]"));
            assertThat(job.getParagraphs()).allSatisfy(paragraph ->
                    assertThat(paragraph.getStatus()).isEqualTo("SUCCESS"));
            assertThat(output).exists();
            try (var stream = Files.newInputStream(output); var result = new XWPFDocument(stream)) {
                assertThat(result.getDocument().xmlText()).isEqualTo(before);
            }
        } finally {
            service.shutdown();
        }
    }
}
