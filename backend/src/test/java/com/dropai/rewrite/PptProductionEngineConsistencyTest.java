package com.dropai.rewrite;

import com.dropai.rewrite.auth.AuthContext;
import com.dropai.rewrite.config.PptProperties;
import com.dropai.rewrite.service.ppt.PptAiService;
import com.dropai.rewrite.service.ppt.PptContentPlannerV2InputAdapter;
import com.dropai.rewrite.service.ppt.PptDocumentParser;
import com.dropai.rewrite.service.ppt.PptEngineV1Service;
import com.dropai.rewrite.service.ppt.PptGenerationSkillService;
import com.dropai.rewrite.service.ppt.PptProjectService;
import com.dropai.rewrite.service.ppt.PptTextValidator;
import com.dropai.rewrite.service.ppt.SourceDocumentPrecheckService;
import com.dropai.rewrite.service.ppt.rendering.canonical.v1.FrozenSlideRenderPlan;
import com.dropai.rewrite.service.ppt.rendering.renderer.v1.AssetBinaryResolver;
import com.dropai.rewrite.service.ppt.rendering.renderer.v1.PurePptxRenderer;
import com.dropai.rewrite.service.ppt.rendering.renderer.v1.RenderedPptx;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.JdbcTemplate;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

class PptProductionEngineConsistencyTest {
    @TempDir
    Path temp;

    @AfterEach
    void clearAuthentication() {
        AuthContext.clear();
    }

    @Test
    void engineAcceptsOnlyFrozenPlanAndDelegatesToPureRenderer() throws Exception {
        FrozenSlideRenderPlan plan = mock(FrozenSlideRenderPlan.class);
        AssetBinaryResolver assets = mock(AssetBinaryResolver.class);
        byte[] bytes = "pure-renderer-output".getBytes(StandardCharsets.UTF_8);
        RenderedPptx receipt = new RenderedPptx(
                "pure-pptx-renderer.v1", "sha256:" + "a".repeat(64), 1, bytes.length);
        PurePptxRenderer renderer = (actualPlan, actualAssets, output) -> {
            assertSame(plan, actualPlan);
            assertSame(assets, actualAssets);
            try {
                output.write(bytes);
            } catch (IOException exception) {
                throw new UncheckedIOException(exception);
            }
            return receipt;
        };

        Path output = temp.resolve("nested").resolve("production.pptx");
        RenderedPptx actual = new PptEngineV1Service(renderer).generate(plan, assets, output);

        assertSame(receipt, actual);
        assertArrayEquals(bytes, Files.readAllBytes(output));
    }

    @Test
    void engineDoesNotPublishPartialFileWhenPureRendererFails() {
        FrozenSlideRenderPlan plan = mock(FrozenSlideRenderPlan.class);
        AssetBinaryResolver assets = mock(AssetBinaryResolver.class);
        PurePptxRenderer renderer = (ignoredPlan, ignoredAssets, output) -> {
            try {
                output.write(1);
            } catch (IOException exception) {
                throw new UncheckedIOException(exception);
            }
            throw new IllegalStateException("render failed");
        };
        Path output = temp.resolve("failed.pptx");

        assertThrows(
                IllegalStateException.class,
                () -> new PptEngineV1Service(renderer).generate(plan, assets, output));
        assertFalse(Files.exists(output));
    }

    @Test
    void engineNeverOverwritesAnExistingPublishedArtifact() throws Exception {
        FrozenSlideRenderPlan plan=mock(FrozenSlideRenderPlan.class);
        AssetBinaryResolver assets=mock(AssetBinaryResolver.class);
        PurePptxRenderer renderer=(ignoredPlan,ignoredAssets,output)->{
            try{output.write("replacement".getBytes(StandardCharsets.UTF_8));}
            catch(IOException exception){throw new UncheckedIOException(exception);}
            return new RenderedPptx("pure-pptx-renderer.v1","sha256:"+"b".repeat(64),1,11);
        };
        Path output=temp.resolve("published.pptx");
        byte[] original="original".getBytes(StandardCharsets.UTF_8);
        Files.write(output,original);

        assertThrows(java.nio.file.FileAlreadyExistsException.class,
                ()->new PptEngineV1Service(renderer).generate(plan,assets,output));
        assertArrayEquals(original,Files.readAllBytes(output));
    }

    @Test
    void academicProductionEntryHasNoLegacyRendererDependency() throws IOException {
        Path main = Path.of("src/main/java/com/dropai/rewrite/service/ppt");
        String engine = Files.readString(main.resolve("PptEngineV1Service.java"));
        String project = Files.readString(main.resolve("PptProjectService.java"));
        for (String forbidden : List.of(
                "PptxGenerator",
                "PptLayoutPlannerV1",
                "PptRendererV1",
                "PptProductionTreeAdapter",
                "generateChargedV1",
                "if(engine!=null)")) {
            assertFalse(engine.contains(forbidden), () -> "Engine references " + forbidden);
            assertFalse(project.contains(forbidden), () -> "Project service references " + forbidden);
        }
        assertTrue(engine.contains("FrozenSlideRenderPlan"));
        assertTrue(engine.contains("AssetBinaryResolver"));
        assertTrue(engine.contains("PurePptxRenderer"));
    }
}
