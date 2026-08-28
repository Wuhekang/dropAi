package com.dropai.rewrite.service.wordformat;

import com.dropai.rewrite.config.WordFormatProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

@Component
public class WordFormatProcessRunner {
    private static final Logger log = LoggerFactory.getLogger(WordFormatProcessRunner.class);
    private static final int MAX_DIAGNOSTIC_CHARS = 32_000;

    private final ObjectMapper objectMapper;
    private final WordFormatProperties properties;

    public WordFormatProcessRunner(ObjectMapper objectMapper, WordFormatProperties properties) {
        this.objectMapper = objectMapper;
        this.properties = properties;
    }

    public ProcessResult run(
            Path source,
            Path template,
            Path output,
            Path resultJson,
            Path instructionsFile,
            boolean useDoubao,
            Consumer<ProgressEvent> progressConsumer
    ) throws Exception {
        Path worker = resolveWorkerPath();
        List<String> command = new ArrayList<>();
        command.add(properties.python());
        command.add("-X");
        command.add("utf8");
        command.add(worker.toString());
        command.add("--source");
        command.add(source.toString());
        command.add("--template");
        command.add(template.toString());
        command.add("--output");
        command.add(output.toString());
        command.add("--result-json");
        command.add(resultJson.toString());
        if (instructionsFile != null) {
            command.add("--instructions-file");
            command.add(instructionsFile.toString());
        }
        if (useDoubao) {
            command.add("--use-doubao");
        }

        ProcessBuilder builder = new ProcessBuilder(command);
        builder.directory(worker.getParent().toFile());
        builder.redirectErrorStream(false);
        builder.environment().put("PYTHONUTF8", "1");
        builder.environment().put("PYTHONIOENCODING", "utf-8");

        Process process = builder.start();
        ExecutorService streams = Executors.newFixedThreadPool(2, runnable -> {
            Thread thread = new Thread(runnable, "word-format-worker-stream");
            thread.setDaemon(true);
            return thread;
        });
        Future<String> stdout = streams.submit(() -> readStdout(process, progressConsumer));
        Future<String> stderr = streams.submit(() -> readDiagnostic(process.errorReader(StandardCharsets.UTF_8)));
        try {
            boolean finished = process.waitFor(properties.timeoutSeconds(), TimeUnit.SECONDS);
            if (!finished) {
                terminateProcessTree(process);
                throw new IllegalStateException("格式处理超过 " + properties.timeoutSeconds() + " 秒，任务已终止");
            }

            String outputText = getStream(stdout);
            String errorText = getStream(stderr);
            if (!errorText.isBlank()) {
                log.info("Word formatter worker diagnostics: {}", compact(errorText, 2_000));
            }
            JsonNode payload = null;
            if (Files.isRegularFile(resultJson) && Files.size(resultJson) > 0) {
                try {
                    payload = objectMapper.readTree(resultJson.toFile());
                } catch (Exception exception) {
                    log.warn("Unable to read Word formatter result JSON", exception);
                }
            }
            if (process.exitValue() != 0) {
                String reportedError = payload == null ? "" : text(payload, "error", "message");
                String detail = !reportedError.isBlank()
                        ? reportedError
                        : (!errorText.isBlank() ? errorText : outputText);
                throw new IllegalStateException("格式处理引擎执行失败" + suffix(detail));
            }
            if (payload == null) {
                throw new IllegalStateException("格式处理引擎没有生成结果报告" + suffix(errorText));
            }
            JsonNode success = payload.get("success");
            if (success == null || !success.isBoolean()) {
                throw new IllegalStateException("格式处理引擎返回的结果报告缺少有效 success 标记");
            }
            if (!success.asBoolean()) {
                String failure = text(payload, "error", "message");
                throw new IllegalStateException(failure.isBlank() ? "格式处理失败" : failure);
            }
            JsonNode integrityPassed = payload.path("integrity").get("passed");
            if (integrityPassed == null || !integrityPassed.isBoolean() || !integrityPassed.asBoolean()) {
                throw new IllegalStateException("格式处理引擎未通过内容完整性校验");
            }
            int changedCount = integer(payload, "changedCount", "changed_count");
            if (changedCount < 0) {
                throw new IllegalStateException("格式处理引擎返回的 changedCount 无效");
            }
            return new ProcessResult(
                    changedCount,
                    strings(payload, "warnings"),
                    strings(payload, "templateNotes", "template_notes", "notes")
            );
        } finally {
            streams.shutdownNow();
            if (process.isAlive()) {
                terminateProcessTree(process);
            }
        }
    }

    private static void terminateProcessTree(Process process) {
        List<ProcessHandle> descendants;
        try {
            descendants = new ArrayList<>(process.toHandle().descendants().toList());
        } catch (Exception exception) {
            descendants = new ArrayList<>();
        }
        descendants.sort((left, right) -> Long.compare(right.pid(), left.pid()));
        descendants.forEach(WordFormatProcessRunner::destroyQuietly);
        destroyQuietly(process.toHandle());
        try {
            process.waitFor(2, TimeUnit.SECONDS);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        }
        descendants.stream()
                .filter(ProcessHandle::isAlive)
                .forEach(WordFormatProcessRunner::destroyForciblyQuietly);
        if (process.isAlive()) {
            destroyForciblyQuietly(process.toHandle());
        }
    }

    private static void destroyQuietly(ProcessHandle handle) {
        try {
            if (handle.isAlive()) {
                handle.destroy();
            }
        } catch (Exception ignored) {
            // Best-effort task-scoped cleanup; never target unrelated Word processes.
        }
    }

    private static void destroyForciblyQuietly(ProcessHandle handle) {
        try {
            if (handle.isAlive()) {
                handle.destroyForcibly();
            }
        } catch (Exception ignored) {
            // Best-effort task-scoped cleanup; never target unrelated Word processes.
        }
    }

    private String readStdout(Process process, Consumer<ProgressEvent> consumer) throws IOException {
        StringBuilder diagnostic = new StringBuilder();
        try (BufferedReader reader = process.inputReader(StandardCharsets.UTF_8)) {
            String line;
            while ((line = reader.readLine()) != null) {
                appendBounded(diagnostic, line);
                try {
                    JsonNode event = objectMapper.readTree(line);
                    int progress = integer(event, "progress", "percent");
                    String stage = text(event, "currentStage", "stage", "event");
                    String message = text(event, "message", "detail");
                    if (progress >= 0 || !stage.isBlank() || !message.isBlank()) {
                        consumer.accept(new ProgressEvent(progress, stage, message));
                    }
                } catch (Exception ignored) {
                    log.debug("Ignored non-JSON formatter output: {}", compact(line, 300));
                }
            }
        }
        return diagnostic.toString();
    }

    private static String readDiagnostic(BufferedReader reader) throws IOException {
        StringBuilder diagnostic = new StringBuilder();
        try (reader) {
            String line;
            while ((line = reader.readLine()) != null) {
                appendBounded(diagnostic, line);
            }
        }
        return diagnostic.toString();
    }

    private static void appendBounded(StringBuilder target, String line) {
        if (target.length() >= MAX_DIAGNOSTIC_CHARS) {
            return;
        }
        int remaining = MAX_DIAGNOSTIC_CHARS - target.length();
        String value = line + System.lineSeparator();
        target.append(value, 0, Math.min(value.length(), remaining));
    }

    private String getStream(Future<String> future) {
        try {
            return future.get(5, TimeUnit.SECONDS);
        } catch (Exception exception) {
            return "";
        }
    }

    private Path resolveWorkerPath() {
        Path configured = Path.of(properties.worker());
        if (configured.isAbsolute()) {
            if (Files.isRegularFile(configured)) {
                return configured.normalize();
            }
            throw new IllegalStateException("Word 格式处理脚本不存在：" + configured.normalize());
        }
        Path current = Path.of("").toAbsolutePath().normalize();
        Path direct = current.resolve(configured).normalize();
        if (Files.isRegularFile(direct)) {
            return direct;
        }
        Path parent = current.getParent();
        if (parent != null) {
            Path fromParent = parent.resolve(configured).normalize();
            if (Files.isRegularFile(fromParent)) {
                return fromParent;
            }
        }
        throw new IllegalStateException("Word 格式处理脚本不存在，请设置 WORD_FORMAT_WORKER");
    }

    private static int integer(JsonNode node, String... names) {
        for (String name : names) {
            JsonNode value = node.path(name);
            if (value.isNumber()) {
                return value.asInt();
            }
            if (value.isTextual()) {
                try {
                    return Integer.parseInt(value.asText());
                } catch (NumberFormatException ignored) {
                    // Try the next compatible field name.
                }
            }
        }
        return -1;
    }

    private static String text(JsonNode node, String... names) {
        for (String name : names) {
            String value = node.path(name).asText("").trim();
            if (!value.isBlank()) {
                return value;
            }
        }
        return "";
    }

    private static List<String> strings(JsonNode node, String... names) {
        for (String name : names) {
            JsonNode value = node.path(name);
            if (!value.isArray()) {
                continue;
            }
            List<String> result = new ArrayList<>();
            value.forEach(item -> {
                String text = item.asText("").trim();
                if (!text.isBlank() && result.size() < 100) {
                    result.add(compact(text, 1_000));
                }
            });
            return List.copyOf(result);
        }
        return List.of();
    }

    private static String suffix(String text) {
        String value = compact(text, 1_000);
        return value.isBlank() ? "" : "：" + value;
    }

    private static String compact(String text, int limit) {
        if (text == null) {
            return "";
        }
        String value = text.replaceAll("[\\r\\n]+", " ").trim();
        return value.length() <= limit ? value : value.substring(0, limit) + "...";
    }

    public record ProgressEvent(int progress, String stage, String message) {
    }

    public record ProcessResult(int changedCount, List<String> warnings, List<String> templateNotes) {
    }
}
