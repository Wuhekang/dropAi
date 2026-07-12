package com.dropai.rewrite.modules.stepExportEngine;

import com.dropai.rewrite.config.CadWorkerProperties;
import com.dropai.rewrite.modules.drawingEngine.DrawingArtifact;
import com.dropai.rewrite.modules.model.DesignProject;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Service
public class StepExportEngine {
    private static final List<String> REQUIRED_OUTPUTS = List.of(
            "assembly.step",
            "part_01.step",
            "part_02.step",
            "part_03.step",
            "part_04.step",
            "part_05.step",
            "assembly-validation.json"
    );
    private final ObjectMapper objectMapper;
    private final CadWorkerProperties properties;
    private final CadWorkerLocator locator;

    public StepExportEngine() {
        this(new ObjectMapper(), new CadWorkerProperties(), null);
    }

    public StepExportEngine(ObjectMapper objectMapper) {
        this(objectMapper, new CadWorkerProperties(), null);
    }

    @Autowired
    public StepExportEngine(ObjectMapper objectMapper, CadWorkerProperties properties, CadWorkerLocator locator) {
        this.objectMapper = objectMapper;
        this.properties = properties;
        this.locator = locator;
    }

    public List<DrawingArtifact> export(DesignProject project) {
        try {
            if (!properties.isEnabled()) {
                throw new IllegalStateException("CAD_WORKER_UNAVAILABLE: CAD Worker is disabled");
            }
            Path worker = workerPath();
            Path workspace = workspace(project);
            Path input = workspace.resolve("design-project.json");
            Path output = workspace.resolve("out");
            Files.createDirectories(output);
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(input.toFile(), project);

            Process process = new ProcessBuilder(properties.getPython(), worker.toAbsolutePath().toString(), input.toAbsolutePath().toString(), output.toAbsolutePath().toString())
                    .directory(workspace.toFile())
                    .redirectErrorStream(true)
                    .start();
            boolean finished = process.waitFor(Duration.ofSeconds(Math.max(30, properties.getTimeoutSeconds())).toMillis(), TimeUnit.MILLISECONDS);
            String log = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            Files.writeString(workspace.resolve("cad-worker.log"), log, StandardCharsets.UTF_8);
            if (!finished) {
                process.destroyForcibly();
                throw new IllegalStateException("CAD_WORKER_TIMEOUT: CAD Worker timed out after " + properties.getTimeoutSeconds() + " seconds");
            }
            if (process.exitValue() != 0) {
                throw new IllegalStateException(rootCadError(log));
            }

            List<DrawingArtifact> result = new ArrayList<>();
            for (String name : REQUIRED_OUTPUTS) {
                Path file = output.resolve(name);
                if (!Files.exists(file) || Files.size(file) == 0) {
                    throw new IllegalStateException("CAD Worker did not produce required file: " + name);
                }
                result.add(new DrawingArtifact(name, Files.readAllBytes(file), mediaType(name)));
            }
            return result;
        } catch (Exception exception) {
            throw new IllegalStateException("真实STEP生成失败：" + exception.getMessage(), exception);
        }
    }

    private Path workerPath() {
        if (locator != null) return locator.locateScript();
        List<Path> candidates = List.of(Path.of("cad_worker", "cad_worker.py"), Path.of("backend", "cad_worker", "cad_worker.py"));
        return candidates.stream().filter(Files::exists).findFirst()
                .orElseThrow(() -> new IllegalStateException("CAD_WORKER_SCRIPT_NOT_FOUND: 未找到CAD Worker脚本"));
    }

    private Path workspace(DesignProject project) throws Exception {
        String projectId = safe(project == null ? "" : project.getProjectTitle()).replaceAll("[^\\p{IsAlphabetic}\\p{IsDigit}_-]+", "_");
        if (projectId.isBlank()) projectId = "mechanical";
        Path root = locator == null ? Files.createTempDirectory("dropai-cad-worker-") : locator.workRoot();
        Path dir = root.resolve(projectId).resolve(String.valueOf(System.currentTimeMillis())).toAbsolutePath().normalize();
        Files.createDirectories(dir);
        return dir;
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }

    private String compact(String value) {
        if (value == null || value.isBlank()) return "无详细信息";
        String clean = value.replaceAll("\\s+", " ").trim();
        return clean.length() > 1200 ? clean.substring(0, 1200) + "..." : clean;
    }

    private String rootCadError(String log) {
        String clean = compact(log);
        if (clean.contains("CADQUERY_MODULE_NOT_FOUND")) {
            return "CADQUERY_MODULE_NOT_FOUND: CAD Worker Python environment does not contain cadquery";
        }
        if (clean.contains("CAD_WORKER_IMPORT_FAILED")) {
            return "CAD_WORKER_IMPORT_FAILED: " + clean;
        }
        return "CAD_WORKER_FAILED: " + clean;
    }

    private String mediaType(String name) {
        if (name.endsWith(".json")) return "application/json";
        return "model/step";
    }
}
