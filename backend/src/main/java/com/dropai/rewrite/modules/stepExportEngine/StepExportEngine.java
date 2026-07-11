package com.dropai.rewrite.modules.stepExportEngine;

import com.dropai.rewrite.modules.drawingEngine.DrawingArtifact;
import com.dropai.rewrite.modules.model.DesignProject;
import com.fasterxml.jackson.databind.ObjectMapper;
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

    public StepExportEngine() {
        this(new ObjectMapper());
    }

    public StepExportEngine(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public List<DrawingArtifact> export(DesignProject project) {
        try {
            Path worker = workerPath();
            Path workspace = Files.createTempDirectory("dropai-cad-worker-");
            Path input = workspace.resolve("design-project.json");
            Path output = workspace.resolve("out");
            Files.createDirectories(output);
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(input.toFile(), project);

            Process process = new ProcessBuilder("python", worker.toAbsolutePath().toString(), input.toAbsolutePath().toString(), output.toAbsolutePath().toString())
                    .redirectErrorStream(true)
                    .start();
            boolean finished = process.waitFor(Duration.ofMinutes(4).toMillis(), TimeUnit.MILLISECONDS);
            String log = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            if (!finished) {
                process.destroyForcibly();
                throw new IllegalStateException("CAD Worker timed out after 4 minutes");
            }
            if (process.exitValue() != 0) {
                throw new IllegalStateException("CAD Worker failed: " + log);
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
        List<Path> candidates = List.of(
                Path.of("cad_worker", "cad_worker.py"),
                Path.of("backend", "cad_worker", "cad_worker.py")
        );
        return candidates.stream()
                .filter(Files::exists)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("未找到CAD Worker脚本 backend/cad_worker/cad_worker.py"));
    }

    private String mediaType(String name) {
        if (name.endsWith(".json")) return "application/json";
        return "model/step";
    }
}
