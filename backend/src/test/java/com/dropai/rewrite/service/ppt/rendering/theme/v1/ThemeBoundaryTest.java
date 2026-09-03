package com.dropai.rewrite.service.ppt.rendering.theme.v1;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class ThemeBoundaryTest {
    private static final Path JAVA_ROOT = Path.of(
            "src", "main", "java", "com", "dropai", "rewrite", "service", "ppt", "rendering", "theme", "v1");
    private static final Path RESOURCE_ROOT = Path.of(
            "src", "main", "resources", "ppt", "themes", "v1");

    @Test
    void themeCodeDoesNotDependOnPlanningLayoutOrRenderingModules() throws IOException {
        List<String> forbiddenDependencies = List.of(
                "ContentPlanner",
                "ContentSanitizer",
                "DocumentParser",
                "OutlinePlanner",
                "OutlineValidator",
                "AssetMapper",
                "ValidatedPresentationTree",
                "LayoutCatalog",
                "LayoutSelector",
                "RenderPlanCompiler",
                "Renderer"
        );
        try (Stream<Path> paths = Files.walk(JAVA_ROOT)) {
            for (Path path : paths.filter(value -> value.toString().endsWith(".java")).toList()) {
                String source = Files.readString(path);
                for (String forbidden : forbiddenDependencies) {
                    assertFalse(source.contains(forbidden), () -> path + " must not reference " + forbidden);
                }
            }
        }
    }

    @Test
    void themeResourcesContainOnlyTheBaseAndTwoOfficialThemesWithoutContentFields() throws IOException {
        List<Path> resources;
        try (Stream<Path> paths = Files.list(RESOURCE_ROOT)) {
            resources = paths.filter(value -> value.toString().endsWith(".json")).sorted().toList();
        }
        assertEquals(
                Set.of(
                        "academic-base.json",
                        "academic-purple.json",
                        "small-bear-watercolor-blue-v1.json"),
                resources.stream().map(path -> path.getFileName().toString()).collect(java.util.stream.Collectors.toSet()));

        List<String> forbiddenContentFields = List.of(
                "pagePurpose",
                "answerQuestion",
                "sourceChapter",
                "contentType",
                "imageRole",
                "mandatoryAsset",
                "paperTitle"
        );
        for (Path resource : resources) {
            String json = Files.readString(resource);
            for (String forbidden : forbiddenContentFields) {
                assertFalse(json.contains(forbidden), () -> resource + " must not contain " + forbidden);
            }
        }
    }
}
