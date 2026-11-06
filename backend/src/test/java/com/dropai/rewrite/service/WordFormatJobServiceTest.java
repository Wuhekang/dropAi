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
        assertEquals(false, completed.result().get("partialSuccess"));
        verify(runner).run(any(), any(), any(), any(), any(), eq(false), eq(false), any(), any());
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
                        Map.of("body", Map.of("normal", Map.of("fontSizePt", 12, "chineseFont", "楷体")),
                                "headings", Map.of("level2", Map.of("fontSizePt", 18))),
                        List.of(), Map.of(), snapshot, decision, "abc123"));
        when(runner.run(any(), any(), any(), any(), any(), anyBoolean(), eq(false), any(), any()))
                .thenAnswer(invocation -> {
                    Path rulesPath = invocation.getArgument(7);
                    JsonNode confirmed = new ObjectMapper().readTree(rulesPath.toFile());
                    assertEquals(false, invocation.getArgument(5));
                    assertEquals(2, confirmed.path("confirmationVersion").asInt());
                    assertEquals(15, confirmed.path("editableRules").path("body").path("normal").path("fontSizePt").asInt());
                    assertEquals("楷体", confirmed.path("editableRules").path("body").path("normal").path("chineseFont").asText());
                    assertEquals("Times New Roman", confirmed.path("editableRules").path("body").path("normal").path("latinFont").asText());
                    assertEquals(18, confirmed.path("editableRules").path("headings").path("level2").path("fontSizePt").asInt());
                    assertEquals(14, confirmed.path("editableRules").path("toc").path("level3").path("fontSizePt").asInt());
                    assertEquals(24, confirmed.path("editableRules").path("body").path("normal").path("spaceBefore").path("value").asInt());
                    assertEquals(0.5, confirmed.path("editableRules").path("body").path("normal").path("leftIndentCm").asDouble());
                    assertEquals(10.5, confirmed.path("editableRules").path("details").path("table").path("fontSizePt").asDouble());
                    assertEquals(false, confirmed.path("editableRules").path("details").path("table").path("bold").asBoolean());
                    assertEquals("center", confirmed.path("editableRules").path("details").path("table").path("alignment").asText());
                    assertEquals(0, confirmed.path("editableRules").path("details").path("table").path("leftIndentCm").asDouble());
                    assertEquals(false, confirmed.path("editableRules").path("body").path("normal").has("firstLineIndentChars"));
                    assertEquals(false, confirmed.path("editableRules").has("page_setup"));
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
                "body", Map.of("normal", Map.of("fontSizePt", 15, "bold", false, "alignment", "justify", "leftIndentCm", 0.5,
                        "spaceBefore", Map.of("unit", "pt", "value", 24), "firstLineIndentChars", 0)),
                "details", Map.of("table", Map.of("fontSizePt", 10.5, "bold", true, "alignment", "left", "leftIndentCm", 1)),
                "page_setup", Map.of("margin_top_mm", 0),
                "templateAnalysis", Map.of("copyFrontMatter", true),
                "analyzedRules", Map.of("normal_text", Map.of("font_size_pt", 72)),
                "templateSha256", "client-override"));
        assertEquals("SUCCESS", waitForTerminal(submitted.id()).status());
    }

    @Test
    void missingOrInvalidAnalysisFieldsExposeDefaultsAndCanBeConfirmedWithoutChanges() throws Exception {
        byte[] docx = document("默认格式确认");
        WordFormatProcessRunner runner = mock(WordFormatProcessRunner.class);
        service = service(runner);
        Map<String, Object> invalidBody = new java.util.LinkedHashMap<>();
        invalidBody.put("fontSizePt", null);
        invalidBody.put("chineseFont", "");
        invalidBody.put("leftIndentCm", 4);
        invalidBody.put("spaceBefore", Map.of("unit", "line", "value", -1));
        invalidBody.put("latinFont", "Arial");
        when(runner.run(any(), any(), any(), any(), any(), anyBoolean(), eq(true), any(), any()))
                .thenReturn(new WordFormatProcessRunner.ProcessResult(0, List.of(), List.of(),
                        Map.of("body", Map.of("normal", invalidBody)), List.of(), Map.of()));
        when(runner.run(any(), any(), any(), any(), any(), anyBoolean(), eq(false), any(), any()))
                .thenAnswer(invocation -> {
                    JsonNode confirmed = new ObjectMapper().readTree(((Path) invocation.getArgument(7)).toFile());
                    assertEquals(false, invocation.getArgument(5));
                    assertEquals(2, confirmed.path("confirmationVersion").asInt());
                    assertEquals(5, confirmed.path("editableRules").size());
                    assertEquals(12, confirmed.path("editableRules").path("body").path("normal").path("fontSizePt").asDouble());
                    assertEquals("宋体", confirmed.path("editableRules").path("body").path("normal").path("chineseFont").asText());
                    assertEquals("Arial", confirmed.path("editableRules").path("body").path("normal").path("latinFont").asText());
                    assertEquals(0, confirmed.path("editableRules").path("body").path("normal").path("leftIndentCm").asDouble());
                    assertEquals(0, confirmed.path("editableRules").path("body").path("normal").path("spaceBefore").path("value").asDouble());
                    assertEquals(10.5, confirmed.path("editableRules").path("captions").path("figure").path("fontSizePt").asDouble());
                    assertEquals(12, confirmed.path("editableRules").path("details").path("reference").path("fontSizePt").asDouble());
                    Files.copy((Path) invocation.getArgument(0), (Path) invocation.getArgument(2));
                    return new WordFormatProcessRunner.ProcessResult(1, List.of(), List.of());
                });
        AuthContext.setUserId(19L);
        WordFormatJobVO submitted = service.submit(upload("template", "规范.docx", docx), upload("source", "论文.docx", docx), "", true);
        WordFormatJobVO ready = waitForStatus(submitted.id(), "AWAITING_CONFIRMATION");
        JsonNode shownRules = new ObjectMapper().valueToTree(ready.result().get("editableRules"));
        assertEquals(5, shownRules.size());
        assertEquals(16, shownRules.path("headings").path("level1").path("fontSizePt").asDouble());
        service.confirm(submitted.id(), Map.of());
        WordFormatJobVO completed = waitForTerminal(submitted.id());
        assertEquals("SUCCESS", completed.status());
        assertEquals(ready.result().get("editableRules"), completed.result().get("editableRules"));
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
    void unsafeIndentsAreRejectedForAllEditableRulesWithoutWritingOrLaunching() throws Exception {
        byte[] docx = document("缩进输入防护");
        WordFormatProcessRunner runner = mock(WordFormatProcessRunner.class);
        service = service(runner);
        AuthContext.setUserId(81L);
        WordFormatJobVO submitted = service.submit(upload("template", "规范.docx", docx), upload("source", "论文.docx", docx), "", true);
        assertEquals("AWAITING_CONFIRMATION", waitForStatus(submitted.id(), "AWAITING_CONFIRMATION").status());
        Map<String, List<String>> groups = Map.of(
                "body", List.of("normal"), "headings", List.of("level1", "level2", "level3"),
                "toc", List.of("title", "level1", "level2", "level3"),
                "captions", List.of("figure", "table"), "details", List.of("table", "reference"));
        Object[] invalid = {-0.001, 2.001, 5, 100, Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY,
                "", "1", "NaN", "Infinity", null, true, false, List.of(), Map.of()};
        for (var group : groups.entrySet()) {
            for (String item : group.getValue()) {
                for (String field : List.of("leftIndentCm", "rightIndentCm")) {
                    for (Object value : invalid) {
                        Map<String, Object> rule = new java.util.LinkedHashMap<>();
                        rule.put(field, value);
                        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                                () -> service.confirm(submitted.id(), Map.of(group.getKey(), Map.of(item, rule))),
                                group.getKey() + "." + item + "." + field + "=" + value);
                        assertTrue(error.getMessage().contains("0–2 厘米"));
                        assertEquals("AWAITING_CONFIRMATION", service.get(submitted.id()).status());
                    }
                }
            }
        }
        assertTrue(Files.notExists(tempDir.resolve("81").resolve(submitted.id()).resolve("confirmed-rules.json")));
        verify(runner, never()).run(any(), any(), any(), any(), any(), anyBoolean(), eq(false), any(), any());
    }

    @Test
    void confirmationAcceptsZeroAndTwoCentimeterIndentBoundaries() throws Exception {
        byte[] docx = document("缩进合法边界");
        WordFormatProcessRunner runner = mock(WordFormatProcessRunner.class);
        service = service(runner);
        when(runner.run(any(), any(), any(), any(), any(), anyBoolean(), eq(false), any(), any()))
                .thenAnswer(invocation -> {
                    JsonNode confirmed = new ObjectMapper().readTree(((Path) invocation.getArgument(7)).toFile());
                    JsonNode rules = confirmed.path("editableRules");
                    assertEquals(0, rules.path("body").path("normal").path("leftIndentCm").asDouble());
                    assertEquals(2, rules.path("body").path("normal").path("rightIndentCm").asDouble());
                    assertEquals(2, rules.path("toc").path("level3").path("leftIndentCm").asDouble());
                    assertEquals(0.5, rules.path("toc").path("level3").path("rightIndentCm").asDouble());
                    Files.copy((Path) invocation.getArgument(0), (Path) invocation.getArgument(2));
                    return new WordFormatProcessRunner.ProcessResult(2, List.of(), List.of());
                });
        AuthContext.setUserId(82L);
        WordFormatJobVO submitted = service.submit(upload("template", "规范.docx", docx), upload("source", "论文.docx", docx), "", true);
        assertEquals("AWAITING_CONFIRMATION", waitForStatus(submitted.id(), "AWAITING_CONFIRMATION").status());
        service.confirm(submitted.id(), Map.of(
                "body", Map.of("normal", Map.of("leftIndentCm", 0, "rightIndentCm", 2)),
                "toc", Map.of("level3", Map.of("leftIndentCm", 2, "rightIndentCm", 0.5))));
        assertEquals("SUCCESS", waitForTerminal(submitted.id()).status());
    }

    @Test
    void skippedDetailedVerificationResultHasDownloadAndPreservesPendingItems() throws Exception {
        byte[] docx = document("已完成主要格式调整的论文");
        WordFormatProcessRunner runner = mock(WordFormatProcessRunner.class);
        service = service(runner);
        Map<String, Object> report = Map.of("applied", List.of(Map.of("item", "正文", "count", 12)),
                "notApplied", List.of(Map.of("item", "复杂域", "reason", "请人工核对", "status", "skipped", "count", 1)),
                "warnings", List.of("部分内容保留原格式"), "changedCount", 12);
        Map<String, Object> integrity = Map.of("passed", false, "mode", "format_first",
                "basicChecksPassed", true, "deliveryAllowed", true, "verificationSkipped", true);
        when(runner.run(any(), any(), any(), any(), any(), anyBoolean(), eq(false), any(), any()))
                .thenAnswer(invocation -> {
                    Files.copy((Path) invocation.getArgument(0), (Path) invocation.getArgument(2));
                    return new WordFormatProcessRunner.ProcessResult(12, List.of("部分内容保留原格式"), List.of(),
                            Map.of(), List.of(), Map.of(), Map.of(), Map.of(), "", report, integrity,
                            true, "可处理的格式已完成，未处理项已列出，文档可下载",
                            Map.of("engineVersion", "0.5.0", "workerSha256", "a".repeat(64)));
                });
        AuthContext.setUserId(17L);
        WordFormatJobVO submitted = service.submit(upload("template", "规范.docx", docx), upload("source", "论文.docx", docx), "", true);
        confirmWhenReady(submitted.id());
        WordFormatJobVO completed = waitForTerminal(submitted.id());
        assertEquals("SUCCESS", completed.status());
        assertNotNull(completed.downloadUrl());
        assertEquals(report, completed.result().get("formatReport"));
        assertEquals(integrity, completed.result().get("integrity"));
        assertEquals(true, completed.result().get("partialSuccess"));
        assertEquals("部分格式已处理，文档可下载，请核对未处理项", completed.message());
        assertEquals("可处理的格式已完成，未处理项已列出，文档可下载", completed.result().get("message"));
        assertEquals(Map.of("engineVersion", "0.5.0", "workerSha256", "a".repeat(64)), completed.result().get("runtimeInfo"));
        assertEquals(List.of("部分内容保留原格式"), completed.warnings());
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
                            Map.of("passed", false, "mode", "format_first", "basicChecksPassed", true, "deliveryAllowed", true),
                            true, "部分格式已处理，文档可下载", Map.of());
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

    @Test
    void zeroChangePartialResultOffersReviewCopyWithoutClaimingFormattingWasApplied() throws Exception {
        byte[] docx = document("无法自动调整但已保留的论文内容");
        WordFormatProcessRunner runner = mock(WordFormatProcessRunner.class);
        service = service(runner);
        String workerMessage = "未能自动完成格式调整，已保留内容生成可下载核对副本，请查看未处理项。";
        Map<String, Object> report = Map.of("applied", List.of(),
                "notApplied", List.of(Map.of("item", "正文", "reason", "保留原格式", "status", "skipped", "count", 1)),
                "changedCount", 0, "skippedCount", 1);
        when(runner.run(any(), any(), any(), any(), any(), anyBoolean(), eq(false), any(), any()))
                .thenAnswer(invocation -> {
                    Files.copy((Path) invocation.getArgument(0), (Path) invocation.getArgument(2));
                    return new WordFormatProcessRunner.ProcessResult(0, List.of("请人工核对"), List.of(),
                            Map.of(), List.of(), Map.of(), Map.of(), Map.of(), "", report,
                            Map.of("passed", true, "mode", "format_first", "basicChecksPassed", true, "deliveryAllowed", true),
                            true, workerMessage, Map.of());
                });
        AuthContext.setUserId(83L);
        WordFormatJobVO submitted = service.submit(upload("template", "规范.docx", docx), upload("source", "论文.docx", docx), "", true);
        confirmWhenReady(submitted.id());
        WordFormatJobVO completed = waitForTerminal(submitted.id());
        assertEquals("SUCCESS", completed.status());
        assertEquals(0, completed.changedCount());
        assertEquals(true, completed.result().get("partialSuccess"));
        assertEquals("已生成可下载核对副本，自动调整未完成", completed.message());
        assertEquals(workerMessage, completed.result().get("message"));
        assertEquals(report, completed.result().get("formatReport"));
        assertNotNull(completed.downloadUrl());
        assertEquals(docx.length, service.download(submitted.id()).size());
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

    @Test
    void pdfTemplateIsQueuedForTextAnalysisWithoutWordZipValidation() throws Exception {
        WordFormatProcessRunner runner = mock(WordFormatProcessRunner.class);
        service = service(runner);
        AuthContext.setUserId(71L);
        byte[] pdfHeader = "%PDF-1.7\n% Worker validates PDF contents".getBytes(java.nio.charset.StandardCharsets.US_ASCII);
        WordFormatJobVO submitted = service.submit(upload("template", "规范.pdf", pdfHeader),
                upload("source", "论文.docx", document("正文")), "", true);
        assertEquals("AWAITING_CONFIRMATION", waitForStatus(submitted.id(), "AWAITING_CONFIRMATION").status());
        assertTrue(Files.exists(tempDir.resolve("71").resolve(submitted.id()).resolve("template.pdf")));
        verify(runner).verifyRuntime(false, true);
        assertThrows(IllegalArgumentException.class, () -> service.submit(
                upload("template", "伪装.pdf", document("not pdf")), upload("source", "论文.docx", document("正文")), "", true));
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
