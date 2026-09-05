package com.dropai.rewrite.service.wordformat;

import com.dropai.rewrite.config.WordFormatProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.env.MockEnvironment;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WordFormatProcessRunnerTest {
    @TempDir
    Path tempDir;

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
