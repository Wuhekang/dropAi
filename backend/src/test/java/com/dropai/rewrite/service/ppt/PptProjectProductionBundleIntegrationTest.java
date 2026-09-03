package com.dropai.rewrite.service.ppt;

import com.dropai.rewrite.auth.AuthContext;
import com.dropai.rewrite.config.PptProperties;
import com.dropai.rewrite.service.PointService;
import com.dropai.rewrite.service.ppt.rendering.bundle.v1.RenderPlanBundleLoader;
import com.dropai.rewrite.service.ppt.rendering.bundle.v1.RenderPlanBundleTestSupport;
import com.dropai.rewrite.service.ppt.rendering.production.v1.ProductionRenderPlanCoordinator;
import com.dropai.rewrite.service.ppt.rendering.renderer.v1.PurePptxRendererImpl;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.poi.xslf.usermodel.XMLSlideShow;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.JdbcTemplate;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PptProjectProductionBundleIntegrationTest {
    private static final long USER_ID = 88_001L;
    private static final String PROJECT_ID = "health-management-defense-v1";

    @TempDir
    Path temp;

    @AfterEach
    void clearAuth() {
        AuthContext.clear();
    }

    @Test
    void formalGenerateLoadsPublishedBundleAndProducesTheFrozenFortySlides() throws Exception {
        Path bundleRoot = projectRoot().resolve("rendering-v1");
        var stored = RenderPlanBundleTestSupport.store(bundleRoot);
        JdbcTemplate jdbc = jdbcForProject(PROJECT_ID, "PLANNED");
        PptGenerationSkillService skill = mock(PptGenerationSkillService.class);
        PointService points = transparentPoints();
        ProductionRenderPlanCoordinator coordinator = mock(ProductionRenderPlanCoordinator.class);
        when(coordinator.runtimeExpectations("academic-purple")).thenReturn(RenderPlanBundleTestSupport.expectations());
        PptProjectService service = service(jdbc, skill, points, coordinator);
        AuthContext.setUserId(USER_ID);

        Map<String, Object> result = service.generate(PROJECT_ID);

        assertEquals(40, ((Number) result.get("slideCount")).intValue());
        assertEquals(stored.renderPlanHash(), result.get("renderPlanHash"));
        Path output;
        try (var files=Files.list(projectRoot().resolve("outputs"))) {
            output=files.filter(path->path.getFileName().toString().endsWith(".pptx"))
                    .findFirst().orElseThrow();
        }
        assertTrue(Files.isRegularFile(output));
        try (InputStream input = Files.newInputStream(output); XMLSlideShow pptx = new XMLSlideShow(input)) {
            assertEquals(40, pptx.getSlides().size());
        }
        verify(skill).requireManifest();
        verify(coordinator).runtimeExpectations("academic-purple");
    }

    @Test
    void formalGenerateLoadsRuntimeExpectationsForThePersistedTrustedTemplatePack() {
        RenderPlanBundleTestSupport.store(projectRoot().resolve("rendering-v1"));
        JdbcTemplate jdbc = jdbcForProject(
                PROJECT_ID, "PLANNED", "", "small-bear-watercolor-blue-v1");
        ProductionRenderPlanCoordinator coordinator = mock(ProductionRenderPlanCoordinator.class);
        when(coordinator.runtimeExpectations("small-bear-watercolor-blue-v1"))
                .thenReturn(RenderPlanBundleTestSupport.expectations());
        PptProjectService service = service(
                jdbc, mock(PptGenerationSkillService.class), transparentPoints(), coordinator);
        AuthContext.setUserId(USER_ID);

        Map<String, Object> result = service.generate(PROJECT_ID);

        assertEquals("small-bear-watercolor-blue-v1", result.get("templatePackId"));
        verify(coordinator).runtimeExpectations("small-bear-watercolor-blue-v1");
        verify(coordinator, never()).runtimeExpectations("academic-purple");
    }

    @Test
    void missingPublishedBundleFailsClosedBeforeChargingOrCreatingOutput() {
        JdbcTemplate jdbc = jdbcForProject(PROJECT_ID, "PLANNED");
        PptGenerationSkillService skill = mock(PptGenerationSkillService.class);
        PointService points = mock(PointService.class);
        ProductionRenderPlanCoordinator coordinator = mock(ProductionRenderPlanCoordinator.class);
        when(coordinator.runtimeExpectations("academic-purple")).thenReturn(RenderPlanBundleTestSupport.expectations());
        PptProjectService service = service(jdbc, skill, points, coordinator);
        AuthContext.setUserId(USER_ID);

        IllegalStateException failure = assertThrows(IllegalStateException.class,
                () -> service.generate(PROJECT_ID));

        assertTrue(failure.getMessage().contains("RenderPlan 未准备好或不存在"));
        assertFalse(Files.exists(projectRoot().resolve("outputs")));
        verify(skill).requireManifest();
        verify(points,never()).chargeAfterSuccess(anyString(),anyString(),anyString(),any());
        verify(coordinator,never()).runtimeExpectations(org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void billingFailureAfterRenderingDeletesTheUnpublishedTaskArtifact() {
        RenderPlanBundleTestSupport.store(projectRoot().resolve("rendering-v1"));
        JdbcTemplate jdbc=jdbcForProject(PROJECT_ID,"PLANNED");
        PointService points=mock(PointService.class);
        doAnswer(invocation->{
            ((Supplier<?>)invocation.getArgument(3)).get();
            throw new IllegalStateException("billing transaction failed");
        }).when(points).chargeAfterSuccess(anyString(),anyString(),anyString(),any());
        ProductionRenderPlanCoordinator coordinator=mock(ProductionRenderPlanCoordinator.class);
        when(coordinator.runtimeExpectations("academic-purple")).thenReturn(RenderPlanBundleTestSupport.expectations());
        PptProjectService service=service(jdbc,mock(PptGenerationSkillService.class),points,coordinator);
        AuthContext.setUserId(USER_ID);

        IllegalStateException failure=assertThrows(IllegalStateException.class,
                ()->service.generate(PROJECT_ID));

        assertEquals("billing transaction failed",failure.getMessage());
        Path outputs=projectRoot().resolve("outputs");
        if(Files.isDirectory(outputs)){
            try(var files=Files.list(outputs)){
                assertEquals(0,files.filter(Files::isRegularFile).count());
            }catch(java.io.IOException exception){throw new AssertionError(exception);}
        }
        verify(jdbc,org.mockito.Mockito.atLeast(2)).update(
                org.mockito.ArgumentMatchers.contains("output_path=NULL"),
                any(),any(),any(),any());
    }

    @Test
    void bundleFromAnotherProjectCannotBeReplayedThroughTheFormalEntry() {
        String otherProject="another-project";
        RenderPlanBundleTestSupport.store(projectRoot(otherProject).resolve("rendering-v1"));
        PointService points=mock(PointService.class);
        ProductionRenderPlanCoordinator coordinator=mock(ProductionRenderPlanCoordinator.class);
        when(coordinator.runtimeExpectations("academic-purple")).thenReturn(RenderPlanBundleTestSupport.expectations());
        PptProjectService service=service(
                jdbcForProject(otherProject,"PLANNED"),
                mock(PptGenerationSkillService.class),points,coordinator);
        AuthContext.setUserId(USER_ID);

        IllegalStateException failure=assertThrows(IllegalStateException.class,
                ()->service.generate(otherProject));

        assertTrue(failure.getMessage().contains("RenderPlan 与当前PPT项目不匹配"));
        verify(points,never()).chargeAfterSuccess(anyString(),anyString(),anyString(),any());
    }

    @Test
    void upstreamProjectChangeInvalidatesAnOtherwiseReadableBundleBeforeCharging() {
        RenderPlanBundleTestSupport.store(projectRoot().resolve("rendering-v1"));
        PointService points=mock(PointService.class);
        ProductionRenderPlanCoordinator coordinator=mock(ProductionRenderPlanCoordinator.class);
        PptProjectService service=service(
                jdbcForProject(PROJECT_ID,"OUTLINE_READY"),
                mock(PptGenerationSkillService.class),points,coordinator);
        AuthContext.setUserId(USER_ID);

        IllegalStateException failure=assertThrows(IllegalStateException.class,
                ()->service.generate(PROJECT_ID));

        assertTrue(failure.getMessage().contains("项目内容变更而失效"));
        verify(points,never()).chargeAfterSuccess(anyString(),anyString(),anyString(),any());
        verify(coordinator,never()).runtimeExpectations(org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void failedProjectCannotDownloadAnExistingOldArtifact() throws Exception {
        Path old=projectRoot().resolve("outputs").resolve("old-task.pptx");
        Files.createDirectories(old.getParent());Files.write(old,new byte[]{1,2,3});
        JdbcTemplate jdbc=jdbcForProject(PROJECT_ID,"FAILED",old.toString());
        PptProjectService service=service(jdbc,mock(PptGenerationSkillService.class),
                mock(PointService.class),mock(ProductionRenderPlanCoordinator.class));
        AuthContext.setUserId(USER_ID);

        IllegalStateException failure=assertThrows(IllegalStateException.class,
                ()->service.download(PROJECT_ID));

        assertTrue(failure.getMessage().contains("尚未发布"));
        assertTrue(Files.isRegularFile(old),"A failed regeneration must not overwrite the prior bytes");
    }

    private PptProjectService service(JdbcTemplate jdbc, PptGenerationSkillService skill,
                                      PointService points, ProductionRenderPlanCoordinator coordinator) {
        return new PptProjectService(jdbc, new ObjectMapper(), mock(PptDocumentParser.class),
                mock(SourceDocumentPrecheckService.class), mock(PptAiService.class),
                new PptTextValidator(), points, mock(PptProperties.class), skill,
                new PptEngineV1Service(new PurePptxRendererImpl()), coordinator,
                mock(PptContentPlannerV2InputAdapter.class), new RenderPlanBundleLoader(), temp);
    }

    private JdbcTemplate jdbcForProject(String projectId,String status) {
        return jdbcForProject(projectId,status,"");
    }

    private JdbcTemplate jdbcForProject(String projectId,String status,String outputPath) {
        return jdbcForProject(projectId,status,outputPath,"");
    }

    private JdbcTemplate jdbcForProject(
            String projectId,
            String status,
            String outputPath,
            String templatePackId
    ) {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.queryForList(anyString(), any(Object[].class))).thenAnswer(invocation -> {
            String sql = invocation.getArgument(0);
            if (sql.startsWith("SELECT * FROM ppt_project")) {
                Map<String,Object> project=new java.util.LinkedHashMap<>();
                project.put("id",projectId);project.put("topic","个人健康管理系统");
                project.put("status",status);project.put("output_path",outputPath);
                project.put("template_id",templatePackId);
                project.put("template_style",templatePackId);
                return List.of(project);
            }
            return List.of();
        });
        when(jdbc.update(anyString(), any(Object[].class))).thenReturn(1);
        return jdbc;
    }

    private PointService transparentPoints() {
        PointService points = mock(PointService.class);
        when(points.featureCostPoints(anyString())).thenReturn(100);
        doAnswer(invocation -> ((Supplier<?>) invocation.getArgument(3)).get())
                .when(points).chargeAfterSuccess(anyString(), anyString(), anyString(), any());
        return points;
    }

    private Path projectRoot() {
        return projectRoot(PROJECT_ID);
    }

    private Path projectRoot(String projectId) {
        return temp.resolve(Long.toString(USER_ID)).resolve(projectId);
    }
}
