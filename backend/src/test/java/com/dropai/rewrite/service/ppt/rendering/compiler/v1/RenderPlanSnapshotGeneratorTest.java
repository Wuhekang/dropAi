package com.dropai.rewrite.service.ppt.rendering.compiler.v1;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/** Explicit, opt-in compiler snapshot writer; normal test runs never mutate sources. */
class RenderPlanSnapshotGeneratorTest {
    @Test
    void generateSnapshotOnlyWhenExplicitlyRequested() throws IOException {
        Assumptions.assumeTrue(Boolean.getBoolean("dokiai.updateRenderPlanSnapshot"));
        HealthManagementRenderPlanSupport.CompiledFixture fixture =
                HealthManagementRenderPlanSupport.compile();
        Path root = Path.of("src/test/resources/ppt/rendering-fixtures/health-management/v1")
                .toAbsolutePath().normalize();
        Files.write(root.resolve("expected-render-plan.v1.json"), fixture.frozen().canonicalBytes());
        Files.writeString(
                root.resolve("expected-render-plan.v1.sha256"),
                fixture.hash() + "\n",
                StandardCharsets.UTF_8);
    }
}
