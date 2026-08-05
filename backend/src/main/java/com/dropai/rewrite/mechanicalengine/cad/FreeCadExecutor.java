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
            ProcessBuilder builder = new ProcessBuilder(command, script.toString())
                    .directory(workspace.toFile()).redirectErrorStream(true);
            builder.environment().put("DROP_AI_CAD_SPEC", spec.toAbsolutePath().toString());
            builder.environment().put("DROP_AI_CAD_WORKSPACE", workspace.toAbsolutePath().toString());
            Process process = builder.start();
            boolean finished = process.waitFor(Duration.ofMinutes(10).toMillis(), TimeUnit.MILLISECONDS);
            if (!finished) {
                process.destroyForcibly();
                return new ExecutionResult(false, "FREECAD_TIMEOUT", "FreeCAD worker timed out", List.of());
            }
            String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            if (process.exitValue() != 0) return new ExecutionResult(false, "BREP_GENERATION_FAILED", output, List.of());
            List<Path> files;
            try (var stream = Files.walk(workspace)) { files = stream.filter(Files::isRegularFile).toList(); }
            List<Path> required = List.of(
                    workspace.resolve("01_Model/Assembly.FCStd"),
                    workspace.resolve("02_STEP/Assembly.STEP"),
                    workspace.resolve("02_STEP/Assembly.stl"),
                    workspace.resolve("02_STEP/cad-reality-report.json"),
                    workspace.resolve("03_Drawing/projection-lines.json"),
                    workspace.resolve("03_Drawing/Assembly.svg"),
                    workspace.resolve("03_Drawing/Assembly.dxf"));
            List<String> missing = required.stream().filter(path -> !Files.isRegularFile(path))
                    .map(path -> workspace.relativize(path).toString()).toList();
            if (!missing.isEmpty()) {
                return new ExecutionResult(false, "FREECAD_OUTPUT_INCOMPLETE",
                        "FreeCAD finished without required outputs: " + String.join(", ", missing) + "\n" + output, files);
            }
            return new ExecutionResult(true, "", output, files);
        } catch (Exception exception) {
            return new ExecutionResult(false, "FREECAD_EXECUTION_FAILED", exception.getMessage(), List.of());
        }
    }
    public record ExecutionResult(boolean success, String errorCode, String message, List<Path> files) {}
}
