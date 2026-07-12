package com.dropai.rewrite.modules.stepExportEngine;

import com.dropai.rewrite.config.CadWorkerProperties;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;

@Component
public class CadWorkerLocator {
    private final CadWorkerProperties properties;

    public CadWorkerLocator(CadWorkerProperties properties) {
        this.properties = properties;
    }

    public Path locateScript() {
        List<Path> candidates = new ArrayList<>();
        if (hasText(properties.getScript())) {
            Path configured = Path.of(properties.getScript());
            if (configured.isAbsolute()) candidates.add(configured);
        }
        Path userDir = Path.of(System.getProperty("user.dir", ".")).toAbsolutePath().normalize();
        candidates.add(userDir.resolve(Path.of("cad_worker", "cad_worker.py")));
        candidates.add(userDir.resolve(Path.of("backend", "cad_worker", "cad_worker.py")));
        for (Path candidate : candidates) {
            if (Files.isRegularFile(candidate)) return candidate.toAbsolutePath().normalize();
        }
        return extractClasspathWorker();
    }

    public Path workRoot() {
        if (hasText(properties.getWorkDir())) return Path.of(properties.getWorkDir()).toAbsolutePath().normalize();
        return Path.of(System.getProperty("java.io.tmpdir"), "dropai-cad-worker").toAbsolutePath().normalize();
    }

    private Path extractClasspathWorker() {
        ClassPathResource resource = new ClassPathResource("cad-worker/cad_worker.py");
        if (!resource.exists()) {
            throw new IllegalStateException("CAD_WORKER_SCRIPT_NOT_FOUND: cad_worker.py not found in configured path, development path, or classpath resource cad-worker/cad_worker.py");
        }
        try {
            Path dir = workRoot().resolve("classpath-worker-v1");
            Files.createDirectories(dir);
            Path script = dir.resolve("cad_worker.py");
            try (InputStream input = resource.getInputStream()) {
                Files.copy(input, script, StandardCopyOption.REPLACE_EXISTING);
            }
            script.toFile().setReadable(true, false);
            script.toFile().setExecutable(true, false);
            ClassPathResource requirements = new ClassPathResource("cad-worker/requirements.txt");
            if (requirements.exists()) {
                try (InputStream input = requirements.getInputStream()) {
                    Files.copy(input, dir.resolve("requirements.txt"), StandardCopyOption.REPLACE_EXISTING);
                }
            }
            return script.toAbsolutePath().normalize();
        } catch (Exception exception) {
            throw new IllegalStateException("CAD_WORKER_EXTRACT_FAILED: " + exception.getMessage(), exception);
        }
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
