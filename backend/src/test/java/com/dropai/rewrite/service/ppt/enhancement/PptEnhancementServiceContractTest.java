package com.dropai.rewrite.service.ppt.enhancement;

import com.dropai.rewrite.auth.AuthContext;
import com.dropai.rewrite.config.PptEnhancementProperties;
import com.dropai.rewrite.service.PointService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.core.task.TaskExecutor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.mock.env.MockEnvironment;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.LinkOption;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PptEnhancementServiceContractTest {
    private static final long OWNER_ID = 8_801L;
    private static final long OTHER_USER_ID = 8_802L;
    private static final String PROJECT_ID = "ppt-enhancement-project";
    private static final String BASE_TASK_ID = "base-generation-task";

    @TempDir
    Path temp;

    private JdbcTemplate jdbc;
    private PointService points;
    private CapturingTaskExecutor taskExecutor;
    private PptxBaselineInspector inspector;
    private PptEnhancementPreviewRenderer previewRenderer;
    private PptEnhancementAiService ai;
    private PptEnhancementPlanValidator planValidator;
    private PptEnhancementBillingService billing;
    private PptEnhancementService service;

    @BeforeEach
    void setUp() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource();
        dataSource.setDriverClassName("org.h2.Driver");
        dataSource.setUrl("jdbc:h2:mem:ppt_enhancement_" + UUID.randomUUID()
            + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1");
        dataSource.setUsername("sa");
        dataSource.setPassword("");
        jdbc = new JdbcTemplate(dataSource);
        createSchema();

        points = mock(PointService.class);
        when(points.currentPoints(anyLong())).thenReturn(10_000);
        taskExecutor = new CapturingTaskExecutor();
        inspector = mock(PptxBaselineInspector.class);
        previewRenderer = mock(PptEnhancementPreviewRenderer.class);
        ai = mock(PptEnhancementAiService.class);
        planValidator = mock(PptEnhancementPlanValidator.class);
        billing = mock(PptEnhancementBillingService.class);

        MockEnvironment environment = new MockEnvironment()
            .withProperty("DOKIAI_PPT_ENHANCEMENT_ENABLED", "true")
            .withProperty("DOKIAI_PPT_ENHANCEMENT_ARK_API_KEY", "test-key-not-a-secret")
            .withProperty("DOKIAI_PPT_ENHANCEMENT_MODEL", "test-model")
            .withProperty("DOKIAI_PPT_ENHANCEMENT_DATA_DIR", temp.toString());
        PptEnhancementSkillService skillService = mock(PptEnhancementSkillService.class);
        when(skillService.requireBundle()).thenReturn(new PptEnhancementSkillService.SkillBundle(
            "ppt-enhancement", "1.0.0", "a".repeat(64), "trusted", List.of()));
        service = new PptEnhancementService(
            jdbc,
            points,
            taskExecutor,
            new ObjectMapper(),
            new PptEnhancementProperties(environment),
            skillService,
            inspector,
            previewRenderer,
            ai,
            planValidator,
            mock(PptxSlideLocalEnhancer.class),
            mock(PptEnhancementQualityGate.class),
            billing
        );
        AuthContext.setUserId(OWNER_ID);
    }

    @AfterEach
    void clearAuth() {
        AuthContext.clear();
    }

    @Test
    void quoteChargesExactlyCeilingOfHalfTheActuallyChargedBasePoints() throws Exception {
        seedBaseArtifact(101);

        Map<String, Object> oddQuote = service.quote(PROJECT_ID);

        assertEquals(101, ((Number) oddQuote.get("baseChargedPoints")).intValue());
        assertEquals(51, ((Number) oddQuote.get("costPoints")).intValue());
        assertEquals("CEIL_BASE_CHARGED_POINTS_DIV_2", oddQuote.get("pricingRule"));

        jdbc.update("UPDATE ppt_generation_task SET charged_points=100 WHERE id=?", BASE_TASK_ID);
        Map<String, Object> evenQuote = service.quote(PROJECT_ID);
        assertEquals(50, ((Number) evenQuote.get("costPoints")).intValue());
    }

    @Test
    void sameIdempotencyKeyCreatesAndSchedulesOneTaskAndCanOnlyBeChargedOnce() throws Exception {
        BaseSnapshot base = seedBaseArtifact(101);
        Map<String, Object> request = request("same-key-0001");

        Map<String, Object> first = service.start(PROJECT_ID, request);
        Map<String, Object> replay = service.start(PROJECT_ID, request);

        String taskId = String.valueOf(first.get("id"));
        assertEquals(taskId, replay.get("id"));
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM ppt_enhancement_task", Integer.class));
        assertEquals(1, taskExecutor.submissionCount.get());
        verify(points, never()).deductCustom(anyLong(), anyString(), anyString(), anyString(), anyInt(), anyString());

        jdbc.update("UPDATE ppt_enhancement_task SET status='RUNNING' WHERE id=?", taskId);
        PptEnhancementBillingService settlement = new PptEnhancementBillingService(jdbc, points);
        settlement.complete(taskId, OWNER_ID, 51,
            temp.resolve("enhanced.pptx").toString(), "b".repeat(64),
            temp.resolve("plan.json").toString(), temp.resolve("log.json").toString(),
            "c".repeat(64), 49, 12_345L, "个人健康管理系统");

        IllegalStateException replayedSettlement = assertThrows(IllegalStateException.class,
            () -> settlement.complete(taskId, OWNER_ID, 51,
                temp.resolve("enhanced.pptx").toString(), "b".repeat(64),
                temp.resolve("plan.json").toString(), temp.resolve("log.json").toString(),
                "c".repeat(64), 49, 12_345L, "个人健康管理系统"));

        assertTrue(replayedSettlement.getMessage().contains("已被结算或状态异常"));
        verify(points, times(1)).deductCustom(
            eq(OWNER_ID), eq(taskId), eq(PptEnhancementBillingService.FEATURE_CODE),
            eq(PptEnhancementBillingService.FEATURE_NAME), eq(51), anyString());
        assertBaseUnchanged(base);
    }

    @Test
    void failureBeforePublicationDoesNotChargeAndDoesNotRewriteTheBaseArtifact() throws Exception {
        BaseSnapshot base = seedBaseArtifact(99);
        when(inspector.inspect(any(Path.class), anyString()))
            .thenThrow(new IllegalStateException("baseline inspection failed"));
        Map<String, Object> started = service.start(PROJECT_ID, request("failure-key-01"));
        Runnable asynchronousWork = taskExecutor.task.get();
        assertNotNull(asynchronousWork);

        asynchronousWork.run();

        Map<String, Object> failed = jdbc.queryForMap(
            "SELECT * FROM ppt_enhancement_task WHERE id=?", started.get("id"));
        assertEquals("FAILED", failed.get("status"));
        assertFalse(Boolean.TRUE.equals(failed.get("points_charged")));
        assertTrue(String.valueOf(failed.get("error_message")).contains("baseline inspection failed"));
        verify(billing, never()).complete(anyString(), anyLong(), anyInt(), anyString(), anyString(),
            anyString(), anyString(), anyString(), anyInt(), anyLong(), anyString());
        verify(points, never()).deductCustom(anyLong(), anyString(), anyString(), anyString(), anyInt(), anyString());
        assertBaseUnchanged(base);
    }

    @Test
    void failureAfterProviderSuccessNeverLeavesProviderStatusAsSuccess() throws Exception {
        seedBaseArtifact(99);
        Path preview = temp.resolve("preview.png");
        Files.write(preview, new byte[]{1, 2, 3});
        var inventory = new PptxBaselineInspector.DeckInventory(
            "source", 10, 1, 12_192_000, 6_858_000, "academic-purple",
            List.of("7257FF", "E85BB5"),
            List.of(new PptxBaselineInspector.SlideInventory(
                1, "封面", "cover", 2, 0, 0, 2, List.of())));
        when(inspector.inspect(any(Path.class), anyString())).thenReturn(inventory);
        when(previewRenderer.render(any(Path.class), any(Path.class)))
            .thenReturn(new PptEnhancementPreviewRenderer.PreviewBundle(
                List.of(preview), List.of(), "test-renderer"));
        when(ai.createPlan(any(), any(), anyString(), any()))
            .thenReturn(new PptEnhancementAiService.AiPlanResponse(
                new ObjectMapper().createObjectNode(), "doubao_ark", "test-model", "request-1", true, "SUCCESS"));
        when(planValidator.validateAndExpand(any(), any(), any(), any(), anyString()))
            .thenThrow(new IllegalStateException("plan validation failed"));
        Map<String, Object> started = service.start(PROJECT_ID, request("provider-failure-01"));

        taskExecutor.task.get().run();

        Map<String, Object> failed = jdbc.queryForMap(
            "SELECT status,provider_invoked,provider_status,error_message FROM ppt_enhancement_task WHERE id=?",
            started.get("id"));
        assertEquals("FAILED", failed.get("status"));
        assertTrue(Boolean.TRUE.equals(failed.get("provider_invoked")));
        assertEquals("FAILED", failed.get("provider_status"));
        assertTrue(String.valueOf(failed.get("error_message")).contains("plan validation failed"));
    }

    @Test
    void rejectsSymbolicOrReparseComponentsInsideConfiguredStorageRoot() throws Exception {
        Path outside = Files.createTempDirectory("ppt-enhancement-outside-");
        Path link = temp.resolve("linked-storage");
        try {
            try {
                Files.createSymbolicLink(link, outside);
            } catch (UnsupportedOperationException | java.io.IOException | SecurityException unsupported) {
                assumeTrue(false, "Current runtime cannot create a symbolic link for path-boundary verification");
            }
            assumeTrue(Files.exists(link, LinkOption.NOFOLLOW_LINKS));

            IllegalArgumentException failure = assertThrows(IllegalArgumentException.class,
                () -> service.inside(link.resolve("escaped.pptx")));

            assertTrue(failure.getMessage().contains("符号链接/重解析点"));
        } finally {
            Files.deleteIfExists(link);
            Files.deleteIfExists(outside);
        }
    }

    @Test
    void enhancementTasksAreNotVisibleAcrossUsers() throws Exception {
        seedBaseArtifact(100);
        Map<String, Object> started = service.start(PROJECT_ID, request("private-key-01"));
        AuthContext.setUserId(OTHER_USER_ID);

        IllegalArgumentException failure = assertThrows(IllegalArgumentException.class,
            () -> service.status(PROJECT_ID, String.valueOf(started.get("id"))));

        assertTrue(failure.getMessage().contains("不存在或无权访问"));
    }

    private BaseSnapshot seedBaseArtifact(int chargedPoints) throws Exception {
        Path base = temp.resolve(String.valueOf(OWNER_ID)).resolve(PROJECT_ID)
            .resolve("base").resolve("base.pptx");
        Files.createDirectories(base.getParent());
        byte[] bytes = "immutable-base-pptx-package".getBytes(StandardCharsets.UTF_8);
        Files.write(base, bytes);
        String hash = PptxBaselineInspector.sha256(base);
        LocalDateTime now = LocalDateTime.now();
        jdbc.update("INSERT INTO ppt_project(id,user_id,status,output_path,topic) VALUES(?,?,?,?,?)",
            PROJECT_ID, OWNER_ID, "SUCCESS", base.toString(), "个人健康管理系统");
        jdbc.update("INSERT INTO ppt_generation_task(id,project_id,user_id,status,charged_points,output_path,output_sha256,completed_at,created_at,template_pack_id) VALUES(?,?,?,?,?,?,?,?,?,?)",
            BASE_TASK_ID, PROJECT_ID, OWNER_ID, "SUCCESS", chargedPoints, base.toString(), hash,
            now, now.minusSeconds(1), "small-bear-watercolor-blue-v1");
        return new BaseSnapshot(base, bytes, hash);
    }

    private Map<String, Object> request(String idempotencyKey) {
        return Map.of(
            "baseGenerationTaskId", BASE_TASK_ID,
            "idempotencyKey", idempotencyKey,
            "profile", "balanced"
        );
    }

    private void assertBaseUnchanged(BaseSnapshot expected) throws Exception {
        assertArrayEquals(expected.bytes(), Files.readAllBytes(expected.path()));
        assertEquals(expected.sha256(), PptxBaselineInspector.sha256(expected.path()));
        Map<String, Object> project = jdbc.queryForMap("SELECT * FROM ppt_project WHERE id=?", PROJECT_ID);
        Map<String, Object> generation = jdbc.queryForMap(
            "SELECT * FROM ppt_generation_task WHERE id=?", BASE_TASK_ID);
        assertEquals(expected.path().toString(), project.get("output_path"));
        assertEquals(expected.path().toString(), generation.get("output_path"));
        assertEquals(expected.sha256(), generation.get("output_sha256"));
    }

    private void createSchema() {
        jdbc.execute("""
            CREATE TABLE ppt_project (
              id VARCHAR(64) PRIMARY KEY,
              user_id BIGINT NOT NULL,
              status VARCHAR(40) NOT NULL,
              output_path VARCHAR(700),
              topic VARCHAR(255)
            )
            """);
        jdbc.execute("""
            CREATE TABLE ppt_generation_task (
              id VARCHAR(64) PRIMARY KEY,
              project_id VARCHAR(64) NOT NULL,
              user_id BIGINT NOT NULL,
              status VARCHAR(40) NOT NULL,
              charged_points INT,
              output_path VARCHAR(700),
              output_sha256 VARCHAR(64),
              completed_at TIMESTAMP,
              created_at TIMESTAMP NOT NULL,
              template_pack_id VARCHAR(80)
            )
            """);
        jdbc.execute("""
            CREATE TABLE ppt_enhancement_task (
              id VARCHAR(64) PRIMARY KEY,
              project_id VARCHAR(64) NOT NULL,
              user_id BIGINT NOT NULL,
              base_generation_task_id VARCHAR(64) NOT NULL,
              base_output_path VARCHAR(700) NOT NULL,
              base_output_sha256 VARCHAR(64) NOT NULL,
              base_charged_points INT NOT NULL,
              enhancement_cost_points INT NOT NULL,
              idempotency_key VARCHAR(128) NOT NULL,
              mode VARCHAR(24) NOT NULL,
              profile VARCHAR(24) NOT NULL,
              text_policy VARCHAR(32) NOT NULL,
              status VARCHAR(40) NOT NULL,
              progress INT NOT NULL DEFAULT 0,
              current_stage VARCHAR(160),
              skill_name VARCHAR(80),
              skill_version VARCHAR(32),
              skill_hash VARCHAR(64),
              provider VARCHAR(40),
              model VARCHAR(160),
              provider_invoked BOOLEAN NOT NULL DEFAULT FALSE,
              provider_status VARCHAR(60),
              plan_hash VARCHAR(64),
              output_path VARCHAR(700),
              output_sha256 VARCHAR(64),
              plan_path VARCHAR(700),
              log_path VARCHAR(700),
              slide_count INT,
              output_size BIGINT,
              points_charged BOOLEAN NOT NULL DEFAULT FALSE,
              error_message CLOB,
              created_at TIMESTAMP NOT NULL,
              updated_at TIMESTAMP NOT NULL,
              completed_at TIMESTAMP,
              CONSTRAINT uk_ppt_enhancement_idempotency UNIQUE(user_id, project_id, idempotency_key)
            )
            """);
    }

    private record BaseSnapshot(Path path, byte[] bytes, String sha256) {}

    private static final class CapturingTaskExecutor implements TaskExecutor {
        private final AtomicInteger submissionCount = new AtomicInteger();
        private final AtomicReference<Runnable> task = new AtomicReference<>();

        @Override
        public void execute(Runnable task) {
            submissionCount.incrementAndGet();
            this.task.set(task);
        }
    }
}
