package com.dropai.rewrite.service;

import com.dropai.rewrite.auth.AuthContext;
import com.dropai.rewrite.config.WordFormatProperties;
import com.dropai.rewrite.service.wordformat.WordFormatJobService;
import com.dropai.rewrite.service.wordformat.WordFormatProcessRunner;
import com.dropai.rewrite.vo.WordFormatJobVO;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class WordFormatJobServiceTest {
    @TempDir
    Path tempDir;

    private WordFormatJobService service;

    @AfterEach
    void cleanup() {
        AuthContext.clear();
        if (service != null) {
            service.shutdown();
        }
    }

    @Test
    void createsIsolatedAsyncJobAndDownloadsVerifiedDocx() throws Exception {
        byte[] docx = document("真实论文内容");
        WordFormatProcessRunner runner = mock(WordFormatProcessRunner.class);
        when(runner.run(any(), any(), any(), any(), any(), anyBoolean(), eq(false), any(), any()))
                .thenAnswer(invocation -> {
                    Path source = invocation.getArgument(0);
                    Path output = invocation.getArgument(2);
                    Path confirmedRules = invocation.getArgument(7);
                    JsonNode legacyEnvelope = new ObjectMapper().readTree(confirmedRules.toFile());
                    assertTrue(legacyEnvelope.has("editableRules"));
                    assertEquals(false, legacyEnvelope.has("analyzedRules"));
                    WordFormatProcessRunner.ProgressEvent event =
                            new WordFormatProcessRunner.ProgressEvent(68, "processing", "正在套用模板");
                    @SuppressWarnings("unchecked")
                    java.util.function.Consumer<WordFormatProcessRunner.ProgressEvent> consumer = invocation.getArgument(8);
                    consumer.accept(event);
                    Files.copy(source, output);
                    return new WordFormatProcessRunner.ProcessResult(
                            17,
                            List.of("已保留封面"),
                            List.of("已提取正文和表格样式")
                    );
                });
        service = service(runner);
        AuthContext.setUserId(42L);

        WordFormatJobVO created = service.submit(
                upload("template", "C:\\fakepath\\学校模板.docx", docx),
                upload("source", "../../论文原稿.docx", docx),
                "一级标题居中",
                true
        );
        verify(runner).verifyRuntime(false, true);

        confirmWhenReady(created.id());
        WordFormatJobVO completed = waitForTerminal(created.id());
        assertEquals("SUCCESS", completed.status());
        verify(runner).run(any(), any(), any(), any(), any(), eq(true), eq(false), any(), any());
        assertEquals(100, completed.progress());
        assertEquals("论文原稿.docx", completed.sourceName());
        assertEquals("学校模板.docx", completed.templateName());
        assertEquals("论文原稿-格式修订版.docx", completed.outputName());
        assertEquals(17, completed.changedCount());
        assertEquals("已保留封面", completed.warnings().get(0));
        assertNotNull(completed.downloadUrl());

        Path jobRoot = tempDir.resolve("42").resolve(created.id());
        assertTrue(Files.isRegularFile(jobRoot.resolve("source.docx")));
        assertTrue(Files.isRegularFile(jobRoot.resolve("template.docx")));
        assertTrue(Files.isRegularFile(jobRoot.resolve("instructions.txt")));
        assertTrue(Files.isRegularFile(jobRoot.resolve("formatted.docx")));
        assertEquals(docx.length, service.download(created.id()).size());

        AuthContext.setUserId(43L);
        assertThrows(WordFormatJobService.JobNotFoundException.class, () -> service.get(created.id()));
    }

    @Test
    void rejectsLegacySubmissionBeforeQueueingOrWritingWhenRuntimePreflightFails() throws Exception {
        byte[] source = document("运行时预检测试");
        byte[] legacyTemplate = {
                (byte) 0xD0, (byte) 0xCF, 0x11, (byte) 0xE0,
                (byte) 0xA1, (byte) 0xB1, 0x1A, (byte) 0xE1
        };
        WordFormatProcessRunner runner = mock(WordFormatProcessRunner.class);
        doThrow(new IllegalStateException(
                "Traceback: C:\\Users\\Administrator\\dropAi\\document-format-tool\\format_cli.py"
        )).when(runner).verifyRuntime(true, true);
        service = service(runner);
        AuthContext.setUserId(11L);

        IllegalStateException error = assertThrows(
                IllegalStateException.class,
                () -> service.submit(
                        upload("template", "template.doc", legacyTemplate),
                        upload("source", "source.docx", source),
                        "",
                        false
                )
        );

        assertEquals(WordFormatProcessRunner.RUNTIME_UNAVAILABLE_MESSAGE, error.getMessage());
        assertTrue(Files.notExists(tempDir.resolve("11")));
        verify(runner).verifyRuntime(true, true);
        verify(runner, never()).run(any(), any(), any(), any(), any(), anyBoolean(), anyBoolean(), any(), any());
    }

    @Test
    void rejectsExtensionSpoofingBeforeWorkerExecution() throws Exception {
        service = service(mock(WordFormatProcessRunner.class));
        AuthContext.setUserId(7L);
        byte[] invalid = "not-a-docx".getBytes(java.nio.charset.StandardCharsets.UTF_8);

        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> service.submit(
                        upload("template", "template.docx", invalid),
                        upload("source", "source.docx", invalid),
                        "",
                        false
                )
        );
        assertTrue(error.getMessage().contains("DOCX") || error.getMessage().contains("Word"));
    }

    @Test
    void hidesStoragePathsWhenUploadPersistenceFails() throws Exception {
        byte[] docx = document("上传落盘异常测试");
        WordFormatProcessRunner runner = mock(WordFormatProcessRunner.class);
        MultipartFile brokenSource = mock(MultipartFile.class);
        when(brokenSource.isEmpty()).thenReturn(false);
        when(brokenSource.getSize()).thenReturn((long) docx.length);
        when(brokenSource.getOriginalFilename()).thenReturn("source.docx");
        when(brokenSource.getInputStream()).thenThrow(
                new IOException("C:\\Users\\Administrator\\private\\source.docx")
        );
        service = service(runner);
        AuthContext.setUserId(12L);

        IllegalStateException error = assertThrows(
                IllegalStateException.class,
                () -> service.submit(
                        upload("template", "template.docx", docx),
                        brokenSource,
                        "",
                        false
                )
        );

        assertEquals("上传文件保存失败，请重试", error.getMessage());
        assertTrue(Files.notExists(tempDir.resolve("12")));
        verify(runner).verifyRuntime(false, true);
        verify(runner, never()).run(any(), any(), any(), any(), any(), anyBoolean(), anyBoolean(), any(), any());
    }

    @Test
    void rejectsBeforeWritingFilesWhenAllTaskSlotsAreOccupied() throws Exception {
        byte[] docx = document("排队容量测试");
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        WordFormatProcessRunner runner = mock(WordFormatProcessRunner.class);
        service = service(runner, 1);
        when(runner.run(any(), any(), any(), any(), any(), anyBoolean(), eq(true), any(), any()))
                .thenAnswer(invocation -> {
                    started.countDown();
                    assertTrue(release.await(5, TimeUnit.SECONDS));
                    return analysisResult();
                });
        AuthContext.setUserId(9L);

        WordFormatJobVO first = service.submit(
                upload("template", "template.docx", docx),
                upload("source", "first.docx", docx),
                "",
                false
        );
        assertTrue(started.await(2, TimeUnit.SECONDS));
        WordFormatJobVO second = service.submit(
                upload("template", "template.docx", docx),
                upload("source", "second.docx", docx),
                "",
                false
        );

        assertThrows(
                WordFormatJobService.JobQueueFullException.class,
                () -> service.submit(
                        upload("template", "template.docx", docx),
                        upload("source", "third.docx", docx),
                        "",
                        false
                )
        );
        verify(runner, times(2)).verifyRuntime(false, true);
        try (var userDirectories = Files.list(tempDir.resolve("9"))) {
            assertEquals(2, userDirectories.count());
        }
        release.countDown();
        assertEquals("AWAITING_CONFIRMATION", waitForStatus(first.id(), "AWAITING_CONFIRMATION").status());
        assertEquals("AWAITING_CONFIRMATION", waitForStatus(second.id(), "AWAITING_CONFIRMATION").status());
    }

    @Test
    void removesUndeliverableOutputAndWorkingFilesAfterFailure() throws Exception {
        byte[] docx = document("失败清理测试");
        WordFormatProcessRunner runner = mock(WordFormatProcessRunner.class);
        when(runner.run(any(), any(), any(), any(), any(), anyBoolean(), eq(false), any(), any()))
                .thenAnswer(invocation -> {
                    Path output = invocation.getArgument(2);
                    Files.write(output, new byte[]{1, 2, 3});
                    Files.write(output.resolveSibling(".formatted.test.working.docx"), new byte[]{4});
                    Files.write(output.resolveSibling(".formatted.test.working.log.json"), new byte[]{5});
                    throw new IllegalStateException(
                            "Traceback: C:\\Users\\Administrator\\dropAi\\document-format-tool\\format_cli.py"
                    );
                });
        service = service(runner);
        AuthContext.setUserId(10L);

        WordFormatJobVO created = service.submit(
                upload("template", "template.docx", docx),
                upload("source", "source.docx", docx),
                "",
                false
        );
        confirmWhenReady(created.id());
        WordFormatJobVO failed = waitForTerminal(created.id());

        assertEquals("FAILED", failed.status());
        assertEquals(WordFormatProcessRunner.PROCESS_FAILED_MESSAGE, failed.message());
        Path jobRoot = tempDir.resolve("10").resolve(created.id());
        assertTrue(Files.notExists(jobRoot.resolve("formatted.docx")));
        try (var files = Files.list(jobRoot)) {
            assertTrue(files.noneMatch(path -> path.getFileName().toString().contains(".working.")));
        }
    }

    @Test
    void confirmationKeepsServerAnalysisAndCoverDecisionInsteadOfClientOverrides() throws Exception {
        byte[] docx = document("规范正文");
        WordFormatProcessRunner runner = mock(WordFormatProcessRunner.class);
        service = service(runner);
        Map<String, Object> snapshot = Map.of("normal_text", Map.of("font_size_pt", 12), "page_setup", Map.of("margin_top_mm", 30));
        Map<String, Object> decision = new java.util.LinkedHashMap<>();
        decision.put("documentKind", "specification");
        decision.put("copyFrontMatter", false);
        decision.put("reason", "仅含文字撰写要求，没有独立封面页");
        decision.put("frontMatterRange", null);
        when(runner.run(any(), any(), any(), any(), any(), anyBoolean(), eq(true), any(), any()))
                .thenReturn(new WordFormatProcessRunner.ProcessResult(0, List.of(), List.of(),
                        editableRules(), List.of(), Map.of(), snapshot, decision, "abc123"));
        when(runner.run(any(), any(), any(), any(), any(), anyBoolean(), eq(false), any(), any()))
                .thenAnswer(invocation -> {
                    Path rulesPath = invocation.getArgument(7);
                    JsonNode confirmed = new ObjectMapper().readTree(rulesPath.toFile());
                    assertEquals(15, confirmed.path("editableRules").path("body").path("normal").path("fontSizePt").asInt());
                    assertEquals(24, confirmed.path("editableRules").path("body").path("normal").path("spaceBefore").path("value").asInt());
                    assertEquals(12, confirmed.path("analyzedRules").path("normal_text").path("font_size_pt").asInt());
                    assertEquals(30, confirmed.path("analyzedRules").path("page_setup").path("margin_top_mm").asInt());
                    assertEquals(false, confirmed.path("templateAnalysis").path("copyFrontMatter").asBoolean());
                    assertEquals("abc123", confirmed.path("templateSha256").asText());
                    assertEquals(false, confirmed.path("editableRules").has("templateAnalysis"));
                    Files.copy((Path) invocation.getArgument(0), (Path) invocation.getArgument(2));
                    return new WordFormatProcessRunner.ProcessResult(3, List.of(), List.of());
                });
        AuthContext.setUserId(15L);
        WordFormatJobVO submitted = service.submit(upload("template", "规范.docx", docx), upload("source", "论文.docx", docx), "", true);
        WordFormatJobVO ready = waitForStatus(submitted.id(), "AWAITING_CONFIRMATION");
        assertEquals(decision, ready.result().get("templateAnalysis"));
        service.confirm(submitted.id(), Map.of(
                "body", Map.of("normal", Map.of("fontSizePt", 15, "bold", false, "alignment", "justify", "spaceBefore", Map.of("unit", "pt", "value", 24))),
                "templateAnalysis", Map.of("copyFrontMatter", true),
                "analyzedRules", Map.of("normal_text", Map.of("font_size_pt", 72)),
                "templateSha256", "client-override"));
        assertEquals("SUCCESS", waitForTerminal(submitted.id()).status());
    }

    @Test
    void invalidEditableValueStaysAtConfirmationWithoutLaunchingProcessing() throws Exception {
        byte[] docx = document("确认格式测试");
        WordFormatProcessRunner runner = mock(WordFormatProcessRunner.class);
        service = service(runner);
        AuthContext.setUserId(16L);
        WordFormatJobVO submitted = service.submit(upload("template", "规范.docx", docx), upload("source", "论文.docx", docx), "", true);
        waitForStatus(submitted.id(), "AWAITING_CONFIRMATION");
        assertThrows(IllegalArgumentException.class, () -> service.confirm(submitted.id(), Map.of("body", Map.of("normal", Map.of("fontSizePt", "")))));
        assertEquals("AWAITING_CONFIRMATION", service.get(submitted.id()).status());
        verify(runner, never()).run(any(), any(), any(), any(), any(), anyBoolean(), eq(false), any(), any());
    }

    @Test
    void deliverableFormatFirstResultHasDownloadAndPreservesPendingItems() throws Exception {
        byte[] docx = document("已完成主要格式调整的论文");
        WordFormatProcessRunner runner = mock(WordFormatProcessRunner.class);
        service = service(runner);
        Map<String, Object> report = Map.of("applied", List.of(Map.of("item", "正文", "count", 12)),
                "notApplied", List.of(Map.of("item", "复杂域", "reason", "请人工核对")),
                "warnings", List.of("部分内容保留原格式"), "changedCount", 12);
        Map<String, Object> integrity = Map.of("passed", false, "mode", "format_first",
                "basicChecksPassed", true, "deliveryAllowed", true);
        when(runner.run(any(), any(), any(), any(), any(), anyBoolean(), eq(false), any(), any()))
                .thenAnswer(invocation -> {
                    Files.copy((Path) invocation.getArgument(0), (Path) invocation.getArgument(2));
                    return new WordFormatProcessRunner.ProcessResult(12, List.of("部分内容保留原格式"), List.of(),
                            Map.of(), List.of(), Map.of(), Map.of(), Map.of(), "", report, integrity);
                });
        AuthContext.setUserId(17L);
        WordFormatJobVO submitted = service.submit(upload("template", "规范.docx", docx), upload("source", "论文.docx", docx), "", true);
        confirmWhenReady(submitted.id());
        WordFormatJobVO completed = waitForTerminal(submitted.id());
        assertEquals("SUCCESS", completed.status());
        assertNotNull(completed.downloadUrl());
        assertEquals(report, completed.result().get("formatReport"));
        assertEquals(integrity, completed.result().get("integrity"));
        assertTrue(completed.message().contains("可下载"));
        assertEquals(docx.length, service.download(submitted.id()).size());
    }

    @Test
    void deliverableMarkerCannotMakeMissingOrBrokenOutputDownloadable() throws Exception {
        byte[] docx = document("检查结果文件");
        WordFormatProcessRunner runner = mock(WordFormatProcessRunner.class);
        service = service(runner);
        java.util.concurrent.atomic.AtomicInteger calls = new java.util.concurrent.atomic.AtomicInteger();
        when(runner.run(any(), any(), any(), any(), any(), anyBoolean(), eq(false), any(), any()))
                .thenAnswer(invocation -> {
                    if (calls.incrementAndGet() == 2) Files.write((Path) invocation.getArgument(2), new byte[]{1, 2, 3});
                    return new WordFormatProcessRunner.ProcessResult(12, List.of(), List.of(),
                            Map.of(), List.of(), Map.of(), Map.of(), Map.of(), "", Map.of(),
                            Map.of("passed", false, "mode", "format_first", "basicChecksPassed", true, "deliveryAllowed", true));
                });
        AuthContext.setUserId(18L);
        for (int index = 0; index < 2; index++) {
            WordFormatJobVO submitted = service.submit(upload("template", "规范.docx", docx), upload("source", "论文.docx", docx), "", true);
            confirmWhenReady(submitted.id());
            WordFormatJobVO failed = waitForTerminal(submitted.id());
            assertEquals("FAILED", failed.status());
            assertEquals(null, failed.downloadUrl());
            assertThrows(WordFormatJobService.JobNotReadyException.class, () -> service.download(submitted.id()));
        }
    }

    private WordFormatJobService service(WordFormatProcessRunner runner) throws Exception {
        return service(runner, 2);
    }

    private WordFormatJobService service(WordFormatProcessRunner runner, int queueCapacity) throws Exception {
        doReturn(analysisResult()).when(runner)
                .run(any(), any(), any(), any(), any(), anyBoolean(), eq(true), any(), any());
        MockEnvironment environment = new MockEnvironment()
                .withProperty("word-format.data-dir", tempDir.toString())
                .withProperty("word-format.max-concurrent", "1")
                .withProperty("word-format.queue-capacity", String.valueOf(queueCapacity));
        return new WordFormatJobService(new WordFormatProperties(environment), runner);
    }

    private WordFormatJobVO waitForTerminal(String id) throws Exception {
        return waitForStatus(id, "SUCCESS");
    }

    private WordFormatJobVO waitForStatus(String id, String status) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        WordFormatJobVO current;
        do {
            current = service.get(id);
            if (status.equals(current.status()) || "FAILED".equals(current.status())) {
                return current;
            }
            Thread.sleep(20);
        } while (System.nanoTime() < deadline);
        throw new AssertionError("Word format job did not finish in time");
    }

    private void confirmWhenReady(String id) throws Exception {
        assertEquals("AWAITING_CONFIRMATION", waitForStatus(id, "AWAITING_CONFIRMATION").status());
        service.confirm(id, editableRules());
    }

    private static Map<String, Object> editableRules() {
        return Map.of("body", Map.of("normal", Map.of("fontSizePt", 12)));
    }

    private static WordFormatProcessRunner.ProcessResult analysisResult() {
        return new WordFormatProcessRunner.ProcessResult(0, List.of(), List.of(), editableRules(), List.of(), Map.of());
    }

    private static MockMultipartFile upload(String field, String name, byte[] bytes) {
        return new MockMultipartFile(
                field,
                name,
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                bytes
        );
    }

    private static byte[] document(String text) throws Exception {
        try (XWPFDocument document = new XWPFDocument();
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            document.createParagraph().createRun().setText(text);
            document.write(output);
            return output.toByteArray();
        }
    }
}
