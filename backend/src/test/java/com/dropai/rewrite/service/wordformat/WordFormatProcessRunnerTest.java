package com.dropai.rewrite.service.wordformat;

import com.dropai.rewrite.config.WordFormatProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.env.MockEnvironment;

import java.nio.file.Files;
import java.nio.file.Path;
import java.io.BufferedReader;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WordFormatProcessRunnerTest {
    @TempDir
    Path tempDir;

    @Test
    void workerCommandOnlyPermitsAiDuringTemplateAnalysis() {
        WordFormatProcessRunner runner = runner(tempDir.resolve("format_cli.py"), tempDir.resolve("python.exe"));
        for (Path rulesFile : new Path[]{null, tempDir.resolve("confirmed-rules.json")}) {
            var processing = runner.workerCommand(tempDir.resolve("format_cli.py"), tempDir.resolve("source.docx"),
                    tempDir.resolve("template.docx"), tempDir.resolve("output.docx"), tempDir.resolve("result.json"),
                    tempDir.resolve("instructions.txt"), true, false, rulesFile);
            assertFalse(processing.contains("--use-doubao"));
            assertFalse(processing.contains("--analyze-only"));
            assertEquals(rulesFile != null, processing.contains("--rules-file"));
        }
        var analysis = runner.workerCommand(tempDir.resolve("format_cli.py"), tempDir.resolve("source.docx"),
                tempDir.resolve("template.docx"), tempDir.resolve("output.docx"), tempDir.resolve("result.json"),
                null, true, true, null);
        assertTrue(analysis.contains("--use-doubao"));
        assertTrue(analysis.contains("--analyze-only"));
    }

    @Test
    void pdfErrorsUseFixedSafeMessagesNotWorkerDiagnosticText() {
        assertTrue(new WordFormatProcessRunner.ProcessingException("PDF_TEXT_UNAVAILABLE").getMessage().contains("扫描件"));
        assertTrue(new WordFormatProcessRunner.ProcessingException("PDF_DEPENDENCY_MISSING").getMessage().contains("requirements-web.txt"));
        assertEquals(WordFormatProcessRunner.PROCESS_FAILED_MESSAGE,
                new WordFormatProcessRunner.ProcessingException("C:\\private\\key.txt").getMessage());
    }

    @Test
    void requiresRuntimeCheckNextToConfiguredWorkerWithoutLeakingItsPath() throws Exception {
        Path worker = Files.createFile(tempDir.resolve("format_cli.py"));
        WordFormatProcessRunner runner = runner(worker, tempDir.resolve("missing-python.exe"));

        WordFormatProcessRunner.RuntimeUnavailableException error = assertThrows(
                WordFormatProcessRunner.RuntimeUnavailableException.class,
                () -> runner.verifyRuntime(false, false)
        );

        assertEquals(WordFormatProcessRunner.RUNTIME_UNAVAILABLE_MESSAGE, error.getMessage());
        assertFalse(error.getMessage().contains(tempDir.toString()));
    }

    @Test
    void hidesConfiguredPythonAndWorkerPathsWhenRuntimeProbeCannotStart() throws Exception {
        Path worker = Files.createFile(tempDir.resolve("format_cli.py"));
        Files.writeString(tempDir.resolve("runtime_check.py"), "print('{}')");
        Path missingPython = tempDir.resolve("private-python.exe");
        WordFormatProcessRunner runner = runner(worker, missingPython);

        WordFormatProcessRunner.RuntimeUnavailableException error = assertThrows(
                WordFormatProcessRunner.RuntimeUnavailableException.class,
                () -> runner.verifyRuntime(true, true)
        );

        assertEquals(WordFormatProcessRunner.RUNTIME_UNAVAILABLE_MESSAGE, error.getMessage());
        assertFalse(error.getMessage().contains(missingPython.toString()));
        assertFalse(error.getMessage().contains(worker.toString()));
    }

    @Test
    void hidesConfiguredPathsWhenWorkerCannotStart() throws Exception {
        Path worker = Files.createFile(tempDir.resolve("format_cli.py"));
        Path missingPython = tempDir.resolve("private-python.exe");
        WordFormatProcessRunner runner = runner(worker, missingPython);

        WordFormatProcessRunner.ProcessingException error = assertThrows(
                WordFormatProcessRunner.ProcessingException.class,
                () -> runner.run(
                        tempDir.resolve("source.docx"),
                        tempDir.resolve("template.docx"),
                        tempDir.resolve("formatted.docx"),
                        tempDir.resolve("result.json"),
                        null,
                        false,
                        event -> {
                        }
                )
        );

        assertEquals(WordFormatProcessRunner.PROCESS_FAILED_MESSAGE, error.getMessage());
        assertFalse(error.getMessage().contains(missingPython.toString()));
        assertFalse(error.getMessage().contains(worker.toString()));
    }

    @Test
    void hidesWorkerFailureDetailsFromProgressPolling() {
        String privateFailure = "Traceback: C:\\Users\\Administrator\\private\\format_cli.py";

        assertEquals(
                WordFormatProcessRunner.PROCESS_FAILED_MESSAGE,
                WordFormatProcessRunner.safeProgressMessage("failed", privateFailure)
        );
        assertEquals(
                "正在处理",
                WordFormatProcessRunner.safeProgressMessage("processing", "正在处理")
        );
    }

    @Test
    void formatFirstDifferencesCanBeDeliveredWithAnHonestReport() throws Exception {
        WordFormatProcessRunner runner = runner(tempDir.resolve("format_cli.py"), tempDir.resolve("python.exe"));
        ObjectNode payload = formatFirstPayload();
        payload.set("formatReport", new ObjectMapper().readTree("""
                {"applied":[{"item":"正文","count":12}],
                 "notApplied":[{"item":"复杂对象","reason":"请人工核对"}],
                 "warnings":["保留无法自动调整的部分"],"changedCount":12}
                """));
        WordFormatProcessRunner.ProcessResult result = runner.parseSuccessfulPayload(payload);
        assertEquals(12, result.changedCount());
        assertEquals(false, result.integrity().get("passed"));
        assertEquals("format_first", result.integrity().get("mode"));
        assertTrue(result.formatReport().containsKey("notApplied"));
        assertFalse(result.partialSuccess(), "Unclassified legacy notices are not proof of an operation failure");
    }

    @Test
    void partialSuccessPreservesMessageReportAndSafeRuntimeMetadata() throws Exception {
        WordFormatProcessRunner runner = runner(tempDir.resolve("format_cli.py"), tempDir.resolve("python.exe"));
        ObjectNode payload = formatFirstPayload();
        payload.put("partialSuccess", true).put("message", "可处理的格式已完成，未处理项已列出，文档可下载");
        payload.set("formatReport", new ObjectMapper().readTree("""
                {"applied":[{"item":"正文","count":12}],
                 "notApplied":[{"item":"复杂表格","reason":"保留原格式","count":1,"status":"skipped"}]}
                """));
        payload.putObject("runtimeInfo").put("engineVersion", "0.5.0")
                .put("workerSha256", "a".repeat(64)).put("workerPath", "C:\\private\\format_cli.py")
                .put("DOUBAO_API_KEY", "secret-value");
        WordFormatProcessRunner.ProcessResult result = runner.parseSuccessfulPayload(payload);
        assertTrue(result.partialSuccess());
        assertEquals("可处理的格式已完成，未处理项已列出，文档可下载", result.message());
        assertEquals(Map.of("engineVersion", "0.5.0", "workerSha256", "a".repeat(64)), result.runtimeInfo());
        assertEquals(1, ((List<?>) result.formatReport().get("notApplied")).size());
        ((ObjectNode) payload.path("integrity")).put("deliveryAllowed", false);
        assertThrows(WordFormatProcessRunner.ProcessingException.class, () -> runner.parseSuccessfulPayload(payload));
        ((ObjectNode) payload.path("integrity")).put("deliveryAllowed", true);
        payload.put("success", false);
        assertThrows(WordFormatProcessRunner.ProcessingException.class, () -> runner.parseSuccessfulPayload(payload));
    }

    @Test
    void onlyActualSkipMarkersMayInferLegacyPartialSuccess() throws Exception {
        WordFormatProcessRunner runner = runner(tempDir.resolve("format_cli.py"), tempDir.resolve("python.exe"));
        ObjectNode payload = formatFirstPayload();
        ObjectNode report = payload.putObject("formatReport");
        report.putArray("notApplied").addObject().put("item", "未定位图标题").put("status", "not_found");
        assertFalse(runner.parseSuccessfulPayload(payload).partialSuccess());
        report.putArray("notApplied").addObject().put("item", "复杂表格").put("status", "skipped");
        assertTrue(runner.parseSuccessfulPayload(payload).partialSuccess());
        report.putArray("notApplied");
        report.put("skippedCount", 1);
        assertTrue(runner.parseSuccessfulPayload(payload).partialSuccess());
        payload.put("partialSuccess", false);
        assertFalse(runner.parseSuccessfulPayload(payload).partialSuccess());
    }

    @Test
    void skippedDetailedVerificationPermitsPartialDeliveryOnlyWithBasicChecksPassed() throws Exception {
        WordFormatProcessRunner runner = runner(tempDir.resolve("format_cli.py"), tempDir.resolve("python.exe"));
        ObjectNode payload = formatFirstPayload();
        payload.put("partialSuccess", true);
        ObjectNode integrity = (ObjectNode) payload.path("integrity");
        integrity.put("verificationSkipped", true);
        WordFormatProcessRunner.ProcessResult result = runner.parseSuccessfulPayload(payload);
        assertTrue(result.partialSuccess());
        assertEquals(false, result.integrity().get("passed"));
        assertEquals(true, result.integrity().get("verificationSkipped"));
        assertEquals("format_first", result.integrity().get("mode"));
        for (String key : List.of("basicChecksPassed", "deliveryAllowed")) {
            integrity.remove(key);
            assertThrows(WordFormatProcessRunner.ProcessingException.class, () -> runner.parseSuccessfulPayload(payload));
            integrity.put(key, false);
            assertThrows(WordFormatProcessRunner.ProcessingException.class, () -> runner.parseSuccessfulPayload(payload));
            integrity.put(key, true);
        }
    }

    @Test
    void runtimeEventsOnlyLogValidatedMetadataAndNeverChangeProgress() throws Exception {
        WordFormatProcessRunner runner = runner(tempDir.resolve("format_cli.py"), tempDir.resolve("python.exe"));
        ObjectNode event = new ObjectMapper().createObjectNode();
        event.put("type", "runtime").put("engineVersion", "0.5.0").put("pythonVersion", "3.10.11")
                .put("workerSha256", "A".repeat(64)).put("processorSha256", "b".repeat(64))
                .put("namespaceHelperSha256", "c".repeat(64)).put("executionMode", "best_effort_local")
                .put("path", "C:\\private\\worker.py").put("DOUBAO_API_KEY", "private-token")
                .put("progress", 100).put("stage", "completed").put("message", "private-message");
        List<WordFormatProcessRunner.ProgressEvent> progress = new ArrayList<>();
        String diagnostic = runner.readStdout(new BufferedReader(new StringReader(event + "\n"
                + "{\"type\":\"progress\",\"progress\":40,\"stage\":\"processing\",\"message\":\"正在处理\"}\n")), progress::add);
        assertEquals(List.of(new WordFormatProcessRunner.ProgressEvent(40, "processing", "正在处理")), progress);
        assertFalse(diagnostic.contains("private"));
        assertFalse(diagnostic.contains("DOUBAO_API_KEY"));
        assertEquals(6, WordFormatProcessRunner.safeRuntimeInfo(event).size());
        event.put("engineVersion", "0.5.0\nprivate-token").put("pythonVersion", "C:\\private\\python.exe")
                .put("workerSha256", "private-token").put("processorSha256", "a".repeat(65))
                .put("namespaceHelperSha256", true).put("executionMode", "private-token");
        assertEquals(Map.of(), WordFormatProcessRunner.safeRuntimeInfo(event));
    }

    @Test
    void runtimeSerializationFailureNeverFallsBackToPrivateOriginalLine() throws Exception {
        ObjectMapper mapper = new ObjectMapper() {
            @Override
            public String writeValueAsString(Object value) throws com.fasterxml.jackson.core.JsonProcessingException {
                throw new com.fasterxml.jackson.core.JsonProcessingException("private-serialization-detail") { };
            }
        };
        WordFormatProcessRunner runner = new WordFormatProcessRunner(mapper,
                new WordFormatProperties(new MockEnvironment()));
        List<WordFormatProcessRunner.ProgressEvent> progress = new ArrayList<>();
        String diagnostic = runner.readStdout(new BufferedReader(new StringReader("""
                {"type":"runtime","engineVersion":"0.5.0","DOUBAO_API_KEY":"private-token"}
                {"type":"progress","progress":40,"stage":"processing","message":"正在处理"}
                """)), progress::add);
        assertFalse(diagnostic.contains("private"));
        assertFalse(diagnostic.contains("DOUBAO_API_KEY"));
        assertFalse(diagnostic.contains("runtime"));
        assertEquals(List.of(new WordFormatProcessRunner.ProgressEvent(40, "processing", "正在处理")), progress);
    }

    @Test
    void formatFirstRequiresEveryExplicitDeliveryMarker() throws Exception {
        WordFormatProcessRunner runner = runner(tempDir.resolve("format_cli.py"), tempDir.resolve("python.exe"));
        for (String key : new String[]{"mode", "basicChecksPassed", "deliveryAllowed", "passed"}) {
            ObjectNode payload = formatFirstPayload();
            ((ObjectNode) payload.path("integrity")).remove(key);
            assertThrows(WordFormatProcessRunner.ProcessingException.class, () -> runner.parseSuccessfulPayload(payload), key);
        }
        ObjectNode textualMarker = formatFirstPayload();
        ((ObjectNode) textualMarker.path("integrity")).put("basicChecksPassed", "true");
        assertThrows(WordFormatProcessRunner.ProcessingException.class, () -> runner.parseSuccessfulPayload(textualMarker));
        ObjectNode failedBasicCheck = formatFirstPayload();
        ((ObjectNode) failedBasicCheck.path("integrity")).put("passed", true).put("basicChecksPassed", false);
        assertThrows(WordFormatProcessRunner.ProcessingException.class, () -> runner.parseSuccessfulPayload(failedBasicCheck));
    }

    @Test
    void strictWorkerStillRequiresPassedTrue() throws Exception {
        WordFormatProcessRunner runner = runner(tempDir.resolve("format_cli.py"), tempDir.resolve("python.exe"));
        ObjectNode strictPayload = formatFirstPayload();
        ((ObjectNode) strictPayload.path("integrity")).put("mode", "strict");
        assertThrows(WordFormatProcessRunner.ProcessingException.class, () -> runner.parseSuccessfulPayload(strictPayload));
        strictPayload.set("integrity", new ObjectMapper().readTree("{\"passed\":true}"));
        assertEquals(12, runner.parseSuccessfulPayload(strictPayload).changedCount());
    }

    private static ObjectNode formatFirstPayload() throws Exception {
        return (ObjectNode) new ObjectMapper().readTree("""
                {"success":true,"changedCount":12,
                 "integrity":{"passed":false,"mode":"format_first", "basicChecksPassed":true,
                              "deliveryAllowed":true,"differences":{"paragraph_count":{"before":5,"after":6}}}}
                """);
    }

    private static WordFormatProcessRunner runner(Path worker, Path python) {
        MockEnvironment environment = new MockEnvironment()
                .withProperty("word-format.worker", worker.toString())
                .withProperty("word-format.python", python.toString());
        return new WordFormatProcessRunner(new ObjectMapper(), new WordFormatProperties(environment));
    }
}
