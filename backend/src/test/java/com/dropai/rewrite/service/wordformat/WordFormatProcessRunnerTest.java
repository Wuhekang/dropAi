package com.dropai.rewrite.service.wordformat;

import com.dropai.rewrite.config.WordFormatProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.env.MockEnvironment;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

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

    private static WordFormatProcessRunner runner(Path worker, Path python) {
        MockEnvironment environment = new MockEnvironment()
                .withProperty("word-format.worker", worker.toString())
                .withProperty("word-format.python", python.toString());
        return new WordFormatProcessRunner(new ObjectMapper(), new WordFormatProperties(environment));
    }
}
