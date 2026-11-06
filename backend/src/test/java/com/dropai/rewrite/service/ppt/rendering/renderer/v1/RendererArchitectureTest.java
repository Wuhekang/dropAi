package com.dropai.rewrite.service.ppt.rendering.renderer.v1;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RendererArchitectureTest {
    private static final Path RENDERER_SOURCE = Path.of(
            "src/main/java/com/dropai/rewrite/service/ppt/rendering/renderer/v1");

    @Test
    void rendererCannotDependOnPlanningMeasurementThemeLegacyOrQualityGateModules() throws Exception {
        String source = rendererSources();
        for (String forbidden : List.of(
                ".theme.",
                ".layout.",
                ".measurement.",
                ".compiler.",
                ".renderability.",
                "ContentPlanner",
                "OutlinePlanner",
                "AssetMapper",
                "PptRendererV1",
                "PptxGenerator",
                "PptxPackageInspector",
                "PreviewRenderer",
                "PptxQualityGate")) {
            assertFalse(source.contains(forbidden), forbidden);
        }
    }

    @Test
    void rendererCannotBranchOnHighLevelSemanticsOrRepairOrdering() throws Exception {
        String source = rendererSources();
        for (String forbidden : List.of(
                "pageType",
                "pagePurpose",
                "contentType",
                "layoutId",
                "imageRole",
                "assetKind",
                ".sort(",
                "Collections.sort",
                "Comparator.comparing")) {
            assertFalse(source.contains(forbidden), forbidden);
        }
        assertTrue(source.contains("FrozenSlideRenderPlan"));
    }

    @Test
    void rendererCannotSearchForAssetsOrUseTheNetwork() throws Exception {
        String source = rendererSources();
        for (String forbidden : List.of(
                "Files.walk",
                "Files.list",
                "DirectoryStream",
                "HttpClient",
                "java.net.URL",
                "RestTemplate",
                "WebClient")) {
            assertFalse(source.contains(forbidden), forbidden);
        }
    }

    private String rendererSources() throws IOException {
        assertTrue(Files.isDirectory(RENDERER_SOURCE), RENDERER_SOURCE.toAbsolutePath().toString());
        StringBuilder source = new StringBuilder();
        try (Stream<Path> files = Files.walk(RENDERER_SOURCE)) {
            for (Path file : files.filter(path -> path.toString().endsWith(".java")).sorted().toList()) {
                source.append('\n').append(file).append('\n')
                        .append(Files.readString(file, StandardCharsets.UTF_8));
            }
        }
        return source.toString();
    }
}
