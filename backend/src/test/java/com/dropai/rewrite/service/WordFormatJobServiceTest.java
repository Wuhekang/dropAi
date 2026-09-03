package com.dropai.rewrite.service;

import com.dropai.rewrite.auth.AuthContext;
import com.dropai.rewrite.config.WordFormatProperties;
import com.dropai.rewrite.service.wordformat.WordFormatJobService;
import com.dropai.rewrite.service.wordformat.WordFormatProcessRunner;
import com.dropai.rewrite.vo.WordFormatJobVO;
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
        when(runner.run(any(), any(), any(), any(), any(), anyBoolean(), any()))
                .thenAnswer(invocation -> {
                    Path source = invocation.getArgument(0);
                    Path output = invocation.getArgument(2);
                    WordFormatProcessRunner.ProgressEvent event =
                            new WordFormatProcessRunner.ProgressEvent(68, "processing", "正在套用模板");
                    @SuppressWarnings("unchecked")
                    java.util.function.Consumer<WordFormatProcessRunner.ProgressEvent> consumer = invocation.getArgument(6);
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

        WordFormatJobVO completed = waitForTerminal(created.id());
        assertEquals("SUCCESS", completed.status());
        verify(runner).run(any(), any(), any(), any(), any(), eq(true), any());
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
        )).when(runner).verifyRuntime(true, false);
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
        verify(runner).verifyRuntime(true, false);
        verify(runner, never()).run(any(), any(), any(), any(), any(), anyBoolean(), any());
    }

    @Test
    void rejectsExtensionSpoofingBeforeWorkerExecution() {
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
        verify(runner).verifyRuntime(false, false);
        verify(runner, never()).run(any(), any(), any(), any(), any(), anyBoolean(), any());
    }

    @Test
    void rejectsBeforeWritingFilesWhenAllTaskSlotsAreOccupied() throws Exception {
        byte[] docx = document("排队容量测试");
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        WordFormatProcessRunner runner = mock(WordFormatProcessRunner.class);
        when(runner.run(any(), any(), any(), any(), any(), anyBoolean(), any()))
                .thenAnswer(invocation -> {
                    started.countDown();
                    assertTrue(release.await(5, TimeUnit.SECONDS));
                    Path source = invocation.getArgument(0);
                    Path output = invocation.getArgument(2);
                    Files.copy(source, output);
                    return new WordFormatProcessRunner.ProcessResult(1, List.of(), List.of());
                });
        service = service(runner, 1);
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
        verify(runner, times(2)).verifyRuntime(false, false);
        try (var userDirectories = Files.list(tempDir.resolve("9"))) {
            assertEquals(2, userDirectories.count());
        }
        release.countDown();
        assertEquals("SUCCESS", waitForTerminal(first.id()).status());
        assertEquals("SUCCESS", waitForTerminal(second.id()).status());
    }

    @Test
    void removesUndeliverableOutputAndWorkingFilesAfterFailure() throws Exception {
        byte[] docx = document("失败清理测试");
        WordFormatProcessRunner runner = mock(WordFormatProcessRunner.class);
        when(runner.run(any(), any(), any(), any(), any(), anyBoolean(), any()))
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
        WordFormatJobVO failed = waitForTerminal(created.id());

        assertEquals("FAILED", failed.status());
        assertEquals(WordFormatProcessRunner.PROCESS_FAILED_MESSAGE, failed.message());
        Path jobRoot = tempDir.resolve("10").resolve(created.id());
        assertTrue(Files.notExists(jobRoot.resolve("formatted.docx")));
        try (var files = Files.list(jobRoot)) {
            assertTrue(files.noneMatch(path -> path.getFileName().toString().contains(".working.")));
        }
    }

    private WordFormatJobService service(WordFormatProcessRunner runner) {
        return service(runner, 2);
    }

    private WordFormatJobService service(WordFormatProcessRunner runner, int queueCapacity) {
        MockEnvironment environment = new MockEnvironment()
                .withProperty("word-format.data-dir", tempDir.toString())
                .withProperty("word-format.max-concurrent", "1")
                .withProperty("word-format.queue-capacity", String.valueOf(queueCapacity));
        return new WordFormatJobService(new WordFormatProperties(environment), runner);
    }

    private WordFormatJobVO waitForTerminal(String id) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        WordFormatJobVO current;
        do {
            current = service.get(id);
            if ("SUCCESS".equals(current.status()) || "FAILED".equals(current.status())) {
                return current;
            }
            Thread.sleep(20);
        } while (System.nanoTime() < deadline);
        throw new AssertionError("Word format job did not finish in time");
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
