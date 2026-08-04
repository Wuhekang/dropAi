package com.dropai.rewrite.mechanicalengine.cad;

import org.springframework.stereotype.Service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

@Service
public class SolidWorksExecutor {
    public ExecutionResult execute(Path script, Path workspace) {
        String command = System.getenv("SOLIDWORKS_AUTOMATION_COMMAND");
        if (command == null || command.isBlank()) {
            return new ExecutionResult(false, "SOLIDWORKS_WORKER_UNAVAILABLE",
                    "SOLIDWORKS_AUTOMATION_COMMAND is not configured; real SolidWorks generation cannot run", List.of());
        }
        try {
            List<String> args = new ArrayList<>(List.of(command, script.toString(), workspace.toString()));
            Process process = new ProcessBuilder(args).directory(workspace.toFile()).redirectErrorStream(true).start();
            boolean finished = process.waitFor(Duration.ofMinutes(20).toMillis(), java.util.concurrent.TimeUnit.MILLISECONDS);
            if (!finished) {
                process.destroyForcibly();
                return new ExecutionResult(false, "SOLIDWORKS_TIMEOUT", "SolidWorks worker timed out", List.of());
            }
            String output = new String(process.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
            if (process.exitValue() != 0) return new ExecutionResult(false, "SOLIDWORKS_EXECUTION_FAILED", output, List.of());
            List<Path> files;
            try (var stream = Files.walk(workspace)) { files = stream.filter(Files::isRegularFile).toList(); }
            return new ExecutionResult(true, "", output, files);
        } catch (Exception exception) {
            return new ExecutionResult(false, "SOLIDWORKS_EXECUTION_FAILED", exception.getMessage(), List.of());
        }
    }

    public record ExecutionResult(boolean success, String errorCode, String message, List<Path> files) {}
}
