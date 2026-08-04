package com.dropai.rewrite.mechanicalengine.cad;

import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Service
public class FreeCadExecutor {
    public ExecutionResult execute(Path script, Path spec, Path workspace) {
        String command = System.getenv("FREECAD_CMD");
        if (command == null || command.isBlank()) {
            return new ExecutionResult(false, "FREECAD_WORKER_UNAVAILABLE",
                    "FREECAD_CMD is not configured; OpenCascade BRep generation cannot run", List.of());
        }
        try {
            Process process = new ProcessBuilder(command, script.toString(), spec.toString(), workspace.toString())
                    .directory(workspace.toFile()).redirectErrorStream(true).start();
            boolean finished = process.waitFor(Duration.ofMinutes(10).toMillis(), TimeUnit.MILLISECONDS);
            if (!finished) {
                process.destroyForcibly();
                return new ExecutionResult(false, "FREECAD_TIMEOUT", "FreeCAD worker timed out", List.of());
            }
            String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            if (process.exitValue() != 0) return new ExecutionResult(false, "BREP_GENERATION_FAILED", output, List.of());
            List<Path> files;
            try (var stream = Files.walk(workspace)) { files = stream.filter(Files::isRegularFile).toList(); }
            return new ExecutionResult(true, "", output, files);
        } catch (Exception exception) {
            return new ExecutionResult(false, "FREECAD_EXECUTION_FAILED", exception.getMessage(), List.of());
        }
    }
    public record ExecutionResult(boolean success, String errorCode, String message, List<Path> files) {}
}
