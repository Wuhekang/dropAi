package com.dropai.rewrite.mechanicalengine.cad;

import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import com.fasterxml.jackson.databind.ObjectMapper;

@Service
public class FreeCadExecutor {
    public ExecutionResult execute(Path script, Path spec, Path workspace) {
        return execute(script, spec, workspace, event -> {});
    }

    public ExecutionResult execute(Path script, Path spec, Path workspace, Consumer<ProgressEvent> progress) {
        String command = System.getenv("FREECAD_CMD");
        if (command == null || command.isBlank()) {
            return new ExecutionResult(false, "FREECAD_WORKER_UNAVAILABLE",
                    "FREECAD_CMD is not configured; OpenCascade BRep generation cannot run", List.of());
        }
        try {
            Instant startedAt = Instant.now();
            ProcessBuilder builder = new ProcessBuilder(command, script.toString())
                    .directory(workspace.toFile()).redirectErrorStream(false);
            builder.environment().put("DROP_AI_CAD_SPEC", spec.toAbsolutePath().toString());
            builder.environment().put("DROP_AI_CAD_WORKSPACE", workspace.toAbsolutePath().toString());
            Process process = builder.start();
            List<String> outputLines = new CopyOnWriteArrayList<>();
            List<String> errorLines = new CopyOnWriteArrayList<>();
            AtomicReference<ProgressEvent> lastEvent = new AtomicReference<>();
            Thread outputReader = new Thread(() -> readOutput(process.getInputStream(), outputLines, progress, lastEvent), "freecad-progress-reader");
            Thread errorReader = new Thread(() -> readOutput(process.getErrorStream(), errorLines, event -> {}, new AtomicReference<>()), "freecad-error-reader");
            outputReader.setDaemon(true);
            errorReader.setDaemon(true);
            outputReader.start();
            errorReader.start();
            long timeoutSeconds = configuredTimeoutSeconds();
            boolean finished = process.waitFor(Duration.ofSeconds(timeoutSeconds).toMillis(), TimeUnit.MILLISECONDS);
            if (!finished) {
                destroyTree(process);
                outputReader.join(2000);
                errorReader.join(2000);
                ProgressEvent event = lastEvent.get();
                String code = timeoutCode(event);
                List<Path> files = collectFiles(workspace);
                writeRuntimeReport(workspace, command, startedAt, timeoutSeconds, event, outputLines, errorLines, -1, files, code);
                return new ExecutionResult(false, code, "FreeCAD worker timed out at " + describe(event) + "\n" + String.join("\n", errorLines), files);
            }
            outputReader.join(2000);
            errorReader.join(2000);
            String output = String.join("\n", outputLines);
            List<Path> files = collectFiles(workspace);
            if (process.exitValue() != 0) {
                String code = failureCode(lastEvent.get());
                writeRuntimeReport(workspace, command, startedAt, timeoutSeconds, lastEvent.get(), outputLines, errorLines, process.exitValue(), files, code);
                return new ExecutionResult(false, code, output + "\n" + String.join("\n", errorLines), files);
            }
            List<Path> required = List.of(
                    workspace.resolve("01_Model/Assembly.FCStd"),
                    workspace.resolve("02_STEP/Assembly.STEP"),
                    workspace.resolve("02_STEP/Assembly.stl"),
                    workspace.resolve("02_STEP/cad-reality-report.json"),
                    workspace.resolve("03_Drawing/projection-lines.json"),
                    workspace.resolve("03_Drawing/Assembly.svg"),
                    workspace.resolve("03_Drawing/Assembly.dxf"));
            List<String> missing = required.stream().filter(path -> !nonEmpty(path))
                    .map(path -> workspace.relativize(path).toString()).toList();
            if (!missing.isEmpty()) {
                writeRuntimeReport(workspace, command, startedAt, timeoutSeconds, lastEvent.get(), outputLines, errorLines, process.exitValue(), files, "FREECAD_OUTPUT_INCOMPLETE");
                return new ExecutionResult(false, "FREECAD_OUTPUT_INCOMPLETE",
                        "FreeCAD finished without required outputs: " + String.join(", ", missing) + "\n" + output, files);
            }
            writeRuntimeReport(workspace, command, startedAt, timeoutSeconds, lastEvent.get(), outputLines, errorLines, process.exitValue(), files, "");
            return new ExecutionResult(true, "", output, files);
        } catch (Exception exception) {
            return new ExecutionResult(false, "FREECAD_EXECUTION_FAILED", exception.getMessage(), List.of());
        }
    }
    private void readOutput(java.io.InputStream stream, List<String> output, Consumer<ProgressEvent> progress,
                            AtomicReference<ProgressEvent> lastEvent) {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            for (String line; (line = reader.readLine()) != null;) {
                output.add(line);
                if (!line.startsWith("DROP_AI_PROGRESS|")) continue;
                String[] fields = line.split("\\|", 4);
                if (fields.length == 4) {
                    ProgressEvent event = new ProgressEvent(Integer.parseInt(fields[1]), fields[2], fields[3]);
                    lastEvent.set(event);
                    progress.accept(event);
                }
            }
        } catch (Exception exception) {
            output.add("FREECAD_OUTPUT_READER_FAILED: " + exception.getMessage());
        }
    }
    private long configuredTimeoutSeconds() {
        try { return Math.max(10, Long.parseLong(System.getenv().getOrDefault("FREECAD_TIMEOUT_SECONDS", "600"))); }
        catch (NumberFormatException ignored) { return 600; }
    }
    private boolean nonEmpty(Path path) {
        try { return Files.isRegularFile(path) && Files.size(path) > 0; }
        catch (Exception ignored) { return false; }
    }
    private List<Path> collectFiles(Path workspace) {
        try (var stream = Files.walk(workspace)) { return stream.filter(Files::isRegularFile).toList(); }
        catch (Exception ignored) { return List.of(); }
    }
    private void destroyTree(Process process) {
        process.descendants().forEach(ProcessHandle::destroyForcibly);
        process.destroyForcibly();
    }
    private String part(ProgressEvent event) {
        if (event == null || event.message() == null) return "UNKNOWN_PART";
        String first = event.message().split(":", 2)[0].trim();
        return first.matches("P\\d+") ? first : "UNKNOWN_PART";
    }
    private String timeoutCode(ProgressEvent event) {
        return (part(event) + "_" + (event == null ? "FREECAD" : event.stage()) + "_TIMEOUT").replaceAll("[^A-Z0-9_]", "_");
    }
    private String failureCode(ProgressEvent event) {
        return (part(event) + "_" + (event == null ? "FREECAD" : event.stage()) + "_FAILED").replaceAll("[^A-Z0-9_]", "_");
    }
    private String describe(ProgressEvent event) { return event == null ? "unknown FreeCAD stage" : event.stage() + " / " + event.message(); }
    private void writeRuntimeReport(Path workspace, String command, Instant startedAt, long timeoutSeconds,
                                    ProgressEvent event, List<String> stdout, List<String> stderr,
                                    int exitCode, List<Path> files, String failureReason) {
        try {
            Map<String,Object> report = new LinkedHashMap<>();
            report.put("command", List.of(command)); report.put("runtimeSeconds", Duration.between(startedAt, Instant.now()).toMillis()/1000.0);
            report.put("timeoutSeconds", timeoutSeconds); report.put("part", part(event));
            report.put("feature", event == null ? "" : event.message()); report.put("stage", event == null ? "" : event.stage());
            report.put("stdout", stdout); report.put("stderr", stderr); report.put("exitCode", exitCode);
            report.put("files", files.stream().map(path -> workspace.relativize(path).toString()).toList());
            report.put("failureReason", failureReason);
            new ObjectMapper().writerWithDefaultPrettyPrinter().writeValue(workspace.resolve("freecad-runtime-report.json").toFile(), report);
        } catch (Exception ignored) { }
    }
    public record ExecutionResult(boolean success, String errorCode, String message, List<Path> files) {}
    public record ProgressEvent(int progress, String stage, String message) {}
}
