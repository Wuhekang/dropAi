package com.dropai.rewrite.service.ppt.rendering.measurement.v1;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MeasurementBoundaryTest {
    private static final List<String> FORBIDDEN_DEPENDENCIES = List.of(
            "org.apache.poi",
            "PptRenderer",
            "PptxGenerator",
            "ContentPlanner",
            "OutlinePlanner",
            "DocumentParser",
            "AssetMapper",
            "jakarta.persistence",
            "javax.persistence");

    @Test
    void measurementDoesNotDependOnRendererPowerPointPlanningOrDatabase() throws IOException {
        Path root = Path.of("src/main/java/com/dropai/rewrite/service/ppt/rendering/measurement/v1");
        assertTrue(Files.isDirectory(root));
        try (var files = Files.walk(root)) {
            List<Path> javaFiles = files.filter(path -> path.toString().endsWith(".java")).toList();
            assertFalse(javaFiles.isEmpty());
            for (Path file : javaFiles) {
                String source = Files.readString(file);
                for (String forbidden : FORBIDDEN_DEPENDENCIES) {
                    assertFalse(
                            source.contains(forbidden),
                            () -> file + " must not depend on " + forbidden);
                }
            }
        }
    }
}
