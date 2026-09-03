package com.dropai.rewrite;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PptAcademicProductionArchitectureTest {
    private static final Path PPT_SOURCE = Path.of("src/main/java/com/dropai/rewrite/service/ppt");

    @Test
    void academicGenerateCannotBeAFixedFailureOrUseAnyLegacyRenderer() throws Exception {
        String project = source("PptProjectService.java");
        String generate = methodBody(project, "generate(String id)");

        assertFalse(project.contains("FROZEN_RENDER_PLAN_REQUIRED"),
                "A hard-coded failure is not a production integration");
        for (String forbidden : List.of(
                "PptxGenerator",
                "PptRendererV1",
                "PptLayoutPlannerV1",
                "PptProductionTreeAdapter",
                "generateChargedV1",
                "if(engine!=null)",
                "legacy renderer")) {
            assertFalse(project.contains(forbidden), forbidden);
        }
        assertTrue(generate.contains("bundleLoader.load("),
                "The formal generate path must execute a persisted bundle");
    }

    @Test
    void formalGenerateLoadsTheProductionBundleAndCallsOnlyTheFrozenPlanEngineBoundary() throws Exception {
        String project = source("PptProjectService.java");
        String generate = methodBody(project, "generate(String id)");

        assertTrue(project.contains("RenderPlanBundle"),
                "PptProjectService must depend on the production RenderPlan bundle boundary");
        assertTrue(project.contains("PptEngineV1Service"));
        assertTrue(generate.contains(".generate("));
        assertTrue(generate.contains("FrozenSlideRenderPlan")
                        || generate.contains(".plan()")
                        || generate.contains(".renderPlan()"),
                "The engine call must receive the loaded frozen plan, not DB page content");
        assertTrue(generate.contains("assetResolver") || generate.contains(".assets()"),
                "The engine call must receive the exact bundle asset resolver");
    }

    @Test
    void formalGenerateCannotRebuildOrCompressSlidesFromDatabaseFields() throws Exception {
        String generate = methodBody(source("PptProjectService.java"), "generate(String id)");
        for (String forbidden : List.of(
                "ppt_slide",
                "body_boxes_json",
                "asset_ids_json",
                "layout_type",
                "content_summary",
                "speaker_notes",
                "summarize(",
                "PptTextValidator",
                "PptContentPlanner",
                "PptOutlinePlanner")) {
            assertFalse(generate.contains(forbidden), forbidden);
        }
    }

    @Test
    void planServiceMustPublishThroughTheProductionBundleOrchestration() throws Exception {
        String planning = source("PptPlanService.java");

        assertTrue(planning.contains("RenderPlanBundle"),
                "Planning completion must atomically publish a production bundle");
        assertTrue(planning.indexOf("prepareAndStage(")
                        > planning.lastIndexOf("INSERT INTO ppt_page_task"),
                "The bundle must not stage before every DB page projection succeeds");
        assertTrue(planning.indexOf("prepareAndStage(")
                        > planning.lastIndexOf("UPDATE ppt_project SET status='PLANNED'"),
                "The bundle stage must be the last mutable planning output");
        assertTrue(planning.contains("RenderPlanBundleTransaction.requireActive()"));
        assertTrue(planning.contains("RenderPlanBundleTransaction.register("));
        assertTrue(planning.contains("SELECT * FROM ppt_project WHERE id=? AND user_id=? FOR UPDATE"),
                "Planning must lock the selected template until the bundle transaction commits");
        assertFalse(planning.contains("renderPlanCoordinator.prepareAndStore("),
                "PptPlanService must not publish the bundle before transaction commit");
        assertFalse(planning.contains("PptRendererV1"));
        assertFalse(planning.contains("PptxGenerator"));
    }

    @Test
    void engineServiceRemainsOnlyAnAtomicFilesystemBoundaryAroundPureRenderer() throws Exception {
        String engine = source("PptEngineV1Service.java");
        assertTrue(engine.contains("FrozenSlideRenderPlan"));
        assertTrue(engine.contains("AssetBinaryResolver"));
        assertTrue(engine.contains("PurePptxRenderer"));
        assertTrue(engine.contains("ATOMIC_MOVE"));
        for (String forbidden : List.of(
                "JdbcTemplate",
                "PptxGenerator",
                "PptRendererV1",
                "ContentPlanner",
                "OutlinePlanner",
                "LayoutPlanner",
                "ThemeEngine")) {
            assertFalse(engine.contains(forbidden), forbidden);
        }
    }

    private String source(String file) throws IOException {
        return Files.readString(PPT_SOURCE.resolve(file), StandardCharsets.UTF_8);
    }

    private String methodBody(String source, String signature) {
        int signatureAt = source.indexOf(signature);
        assertTrue(signatureAt >= 0, "Missing method " + signature);
        int open = source.indexOf('{', signatureAt);
        assertTrue(open >= 0, "Missing method body for " + signature);
        int depth = 0;
        for (int index = open; index < source.length(); index++) {
            char current = source.charAt(index);
            if (current == '{') {
                depth++;
            } else if (current == '}' && --depth == 0) {
                return source.substring(open, index + 1);
            }
        }
        throw new AssertionError("Unclosed method body for " + signature);
    }
}
