package com.dropai.rewrite.service.ppt.enhancement;

import com.dropai.rewrite.auth.AuthContext;
import com.dropai.rewrite.config.PptEnhancementProperties;
import com.dropai.rewrite.service.PointService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.task.TaskExecutor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
public class PptEnhancementService {
    private static final Set<String> PROFILES = Set.of("subtle", "balanced", "showcase");

    private final JdbcTemplate jdbc;
    private final PointService points;
    private final TaskExecutor taskExecutor;
    private final ObjectMapper mapper;
    private final PptEnhancementProperties properties;
    private final PptEnhancementSkillService skillService;
    private final PptxBaselineInspector inspector;
    private final PptEnhancementPreviewRenderer previewRenderer;
    private final PptEnhancementAiService ai;
    private final PptEnhancementPlanValidator planValidator;
    private final PptxSlideLocalEnhancer executor;
    private final PptEnhancementQualityGate qualityGate;
    private final PptEnhancementBillingService billing;
    private final Path root;

    public PptEnhancementService(
        JdbcTemplate jdbc,
        PointService points,
        TaskExecutor taskExecutor,
        ObjectMapper mapper,
        PptEnhancementProperties properties,
        PptEnhancementSkillService skillService,
        PptxBaselineInspector inspector,
        PptEnhancementPreviewRenderer previewRenderer,
        PptEnhancementAiService ai,
        PptEnhancementPlanValidator planValidator,
        PptxSlideLocalEnhancer executor,
        PptEnhancementQualityGate qualityGate,
        PptEnhancementBillingService billing
    ) {
        this.jdbc = jdbc;
        this.points = points;
        this.taskExecutor = taskExecutor;
        this.mapper = mapper.copy().enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS);
        this.properties = properties;
        this.skillService = skillService;
        this.inspector = inspector;
        this.previewRenderer = previewRenderer;
        this.ai = ai;
        this.planValidator = planValidator;
        this.executor = executor;
        this.qualityGate = qualityGate;
        this.billing = billing;
        this.root = properties.dataDir();
    }

    public Map<String, Object> quote(String projectId) {
        Long userId = AuthContext.requireUserId();
        BaseArtifact base = requireBase(projectId, userId, false);
        int currentPoints = points.currentPoints(userId);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("baseGenerationTaskId", base.taskId());
        result.put("baseChargedPoints", base.chargedPoints());
        result.put("costPoints", half(base.chargedPoints()));
        result.put("pricingRule", "CEIL_BASE_CHARGED_POINTS_DIV_2");
        result.put("discountRate", 0.5);
        result.put("currentPoints", currentPoints);
        result.put("baseOutputSha256", base.sha256());
        result.put("enabled", properties.enabled());
        result.put("providerConfigured", properties.configured());
        result.put("canStart", properties.configured() && currentPoints >= half(base.chargedPoints()));
        result.put("latestTask", latestTask(projectId, userId, base.taskId()));
        return result;
    }

    @Transactional
    public Map<String, Object> start(String projectId, Map<String, Object> request) {
        Long userId = AuthContext.requireUserId();
        requireProjectForUpdate(projectId, userId);
        BaseArtifact base = requireBase(projectId, userId, false);
        String requestedBase = text(request == null ? null : request.get("baseGenerationTaskId"));
        if (!base.taskId().equals(requestedBase)) throw new IllegalArgumentException("基础PPT版本已变化，请刷新报价后重试");
        String idempotencyKey = validateIdempotencyKey(text(request == null ? null : request.get("idempotencyKey")));
        String profile = text(request == null ? null : request.get("profile")).toLowerCase(Locale.ROOT);
        if (profile.isBlank()) profile = "balanced";
        if (!PROFILES.contains(profile)) throw new IllegalArgumentException("不支持的增幅强度：" + profile);
        if (!properties.enabled()) throw new IllegalStateException("PPT增幅美化功能未启用");
        if (!properties.configured()) throw new IllegalStateException("PPT增幅美化未配置豆包模型或API Key");
        PptEnhancementSkillService.SkillBundle skill = skillService.requireBundle();
        int cost = half(base.chargedPoints());
        points.ensureEnoughCustom(userId, cost);

        List<Map<String, Object>> duplicate = jdbc.queryForList(
            "SELECT * FROM ppt_enhancement_task WHERE user_id=? AND project_id=? AND idempotency_key=?",
            userId, projectId, idempotencyKey);
        if (!duplicate.isEmpty()) return taskView(duplicate.get(0));
        List<Map<String, Object>> active = jdbc.queryForList(
            "SELECT * FROM ppt_enhancement_task WHERE user_id=? AND project_id=? AND base_generation_task_id=? AND status IN ('QUEUED','RUNNING','FINALIZING') ORDER BY created_at DESC",
            userId, projectId, base.taskId());
        if (!active.isEmpty()) return taskView(active.get(0));

        String taskId = UUID.randomUUID().toString();
        LocalDateTime now = LocalDateTime.now();
        try {
            jdbc.update("INSERT INTO ppt_enhancement_task(id,project_id,user_id,base_generation_task_id,base_output_path,base_output_sha256,base_charged_points,enhancement_cost_points,idempotency_key,mode,profile,text_policy,status,progress,current_stage,skill_name,skill_version,skill_hash,provider,model,provider_invoked,provider_status,points_charged,created_at,updated_at) VALUES(?,?,?,?,?,?,?,?,?,'polish',?,'locked','QUEUED',0,'等待豆包增幅美化',?,?,?,?,?,FALSE,'QUEUED',FALSE,?,?)",
                taskId, projectId, userId, base.taskId(), base.path().toString(), base.sha256(), base.chargedPoints(), cost,
                idempotencyKey, profile, skill.name(), skill.version(), skill.hash(), properties.provider(), properties.model(), now, now);
        } catch (DuplicateKeyException duplicateKey) {
            return taskView(jdbc.queryForMap(
                "SELECT * FROM ppt_enhancement_task WHERE user_id=? AND project_id=? AND idempotency_key=?",
                userId, projectId, idempotencyKey));
        }
        scheduleAfterCommit(taskId);
        return taskView(jdbc.queryForMap("SELECT * FROM ppt_enhancement_task WHERE id=? AND user_id=?", taskId, userId));
    }

    public Map<String, Object> status(String projectId, String taskId) {
        Long userId = AuthContext.requireUserId();
        return taskView(requireTask(projectId, taskId, userId));
    }

    public FileSystemResource download(String projectId, String taskId) {
        Long userId = AuthContext.requireUserId();
        Map<String, Object> task = requireTask(projectId, taskId, userId);
        if (!"SUCCESS".equals(text(task.get("status")))) throw new IllegalStateException("增幅版PPTX尚未发布");
        Path path = inside(Path.of(text(task.get("output_path"))));
        Path taskRoot = enhancementRoot(userId, projectId).resolve(taskId).normalize();
        if (!path.startsWith(taskRoot) || !Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) {
            throw new IllegalStateException("增幅版PPTX文件不存在");
        }
        try {
            if (!PptxBaselineInspector.sha256(path).equals(text(task.get("output_sha256")))) {
                throw new IllegalStateException("增幅版PPTX哈希校验失败");
            }
        } catch (IllegalStateException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IllegalStateException("增幅版PPTX读取失败", exception);
        }
        return new FileSystemResource(path);
    }

    public String downloadName(String projectId, String taskId) {
        Long userId = AuthContext.requireUserId();
        requireTask(projectId, taskId, userId);
        Map<String, Object> project = project(projectId, userId);
        return safeFileName(text(project.get("topic"))) + "_精美增强版.pptx";
    }

    void runTask(String taskId) {
        Path staging = null;
        Path published = null;
        Long userId = null;
        try {
            Map<String, Object> task = jdbc.queryForMap("SELECT * FROM ppt_enhancement_task WHERE id=?", taskId);
            userId = number(task.get("user_id")).longValue();
            String projectId = text(task.get("project_id"));
            int claim = jdbc.update("UPDATE ppt_enhancement_task SET status='RUNNING',progress=8,current_stage='正在检查基础PPT',updated_at=? WHERE id=? AND status='QUEUED'",
                LocalDateTime.now(), taskId);
            if (claim != 1) return;
            Path source = inside(Path.of(text(task.get("base_output_path"))));
            String expectedSourceHash = text(task.get("base_output_sha256"));
            if (!Files.isRegularFile(source) || !expectedSourceHash.equals(PptxBaselineInspector.sha256(source))) {
                throw new IllegalStateException("基础PPT已变化或不存在，请重新生成基础版");
            }
            Path enhancementRoot = enhancementRoot(userId, projectId);
            Files.createDirectories(enhancementRoot);
            enhancementRoot = inside(enhancementRoot);
            staging = inside(enhancementRoot.resolve(".staging-" + taskId + "-" + UUID.randomUUID()));
            published = inside(enhancementRoot.resolve(taskId));
            Files.createDirectories(staging);
            staging = inside(staging);

            update(taskId, 16, "正在建立逐页视觉基线");
            String templatePackId = baseTemplatePack(text(task.get("base_generation_task_id")));
            PptxBaselineInspector.DeckInventory inventory = inspector.inspect(source, templatePackId);
            PptEnhancementPreviewRenderer.PreviewBundle previews = previewRenderer.render(source,
                staging.resolve("baseline-preview"));
            PptEnhancementSkillService.SkillBundle skill = skillService.requireBundle();
            update(taskId, 28, "豆包正在按增幅美化Skill规划每页");
            jdbc.update("UPDATE ppt_enhancement_task SET provider_invoked=TRUE,provider_status='REQUESTING',updated_at=? WHERE id=?",
                LocalDateTime.now(), taskId);
            PptEnhancementAiService.AiPlanResponse aiResponse = ai.createPlan(skill, inventory,
                text(task.get("profile")), previews.slideRenders());
            jdbc.update("UPDATE ppt_enhancement_task SET provider_status=?,provider=?,model=?,updated_at=? WHERE id=?",
                aiResponse.providerStatus(), aiResponse.provider(), aiResponse.model(), LocalDateTime.now(), taskId);
            update(taskId, 46, "正在校验并冻结增幅计划");
            PptEnhancementPlan plan = planValidator.validateAndExpand(aiResponse.plan(), skill, aiResponse, inventory,
                text(task.get("profile")));
            Path planPath = staging.resolve("enhancement-plan.json");
            byte[] planBytes = mapper.writerWithDefaultPrettyPrinter().writeValueAsBytes(plan);
            Files.write(planPath, planBytes);
            String planHash = PptxBaselineInspector.sha256(planPath);
            Files.write(staging.resolve("skill-manifest.json"), mapper.writerWithDefaultPrettyPrinter()
                .writeValueAsBytes(skillService.manifestMap(skill)));

            update(taskId, 62, "正在执行逐页增幅美化");
            Path enhanced = staging.resolve("enhanced.pptx");
            PptxSlideLocalEnhancer.EnhancementResult execution = executor.enhance(source, enhanced, plan, inventory);
            update(taskId, 80, "正在逐页渲染并执行质量门禁");
            PptEnhancementQualityGate.QualityResult quality = qualityGate.validate(source, enhanced,
                staging.resolve("qa"), inventory);
            if (!"PASSED".equals(quality.status())) throw new IllegalStateException("增幅美化质量门禁未通过");

            Map<String, Object> log = enhancementLog(task, skill, aiResponse, inventory, plan, planHash, execution, quality, enhanced);
            Path logPath = staging.resolve("enhancement-log.json");
            Files.write(logPath, mapper.writerWithDefaultPrettyPrinter().writeValueAsBytes(log));
            update(taskId, 92, "正在原子发布增强版PPT");
            publishAtomically(staging, published);
            staging = null;
            Path finalPptx = published.resolve("enhanced.pptx");
            Path finalPlan = published.resolve("enhancement-plan.json");
            Path finalLog = published.resolve("enhancement-log.json");
            String outputHash = PptxBaselineInspector.sha256(finalPptx);
            long outputSize = Files.size(finalPptx);
            billing.complete(taskId, userId, number(task.get("enhancement_cost_points")).intValue(),
                finalPptx.toString(), outputHash, finalPlan.toString(), finalLog.toString(), planHash,
                inventory.slideCount(), outputSize, text(project(projectId, userId).get("topic")));
        } catch (Exception exception) {
            cleanup(staging);
            cleanup(published);
            String error = safeError(exception);
            jdbc.update("UPDATE ppt_enhancement_task SET status='FAILED',progress=0,current_stage='增幅美化失败',points_charged=FALSE,error_message=?,provider_status='FAILED',completed_at=?,updated_at=? WHERE id=? AND status<>'SUCCESS'",
                error, LocalDateTime.now(), LocalDateTime.now(), taskId);
        }
    }

    private Map<String, Object> enhancementLog(
        Map<String, Object> task,
        PptEnhancementSkillService.SkillBundle skill,
        PptEnhancementAiService.AiPlanResponse aiResponse,
        PptxBaselineInspector.DeckInventory inventory,
        PptEnhancementPlan plan,
        String planHash,
        PptxSlideLocalEnhancer.EnhancementResult execution,
        PptEnhancementQualityGate.QualityResult quality,
        Path enhanced
    ) throws Exception {
        Map<String, Object> log = new LinkedHashMap<>();
        log.put("skillName", skill.name());
        log.put("skillVersion", skill.version());
        log.put("skillHash", skill.hash());
        log.put("skillResources", skill.resources());
        log.put("source", Map.of("path", text(task.get("base_output_path")), "sha256", inventory.sourcePptxSha256(),
            "slideCount", inventory.slideCount(), "fileSize", inventory.fileSize()));
        log.put("outputPptx", "enhanced.pptx");
        log.put("request", Map.of("mode", "polish", "profile", text(task.get("profile")), "textPolicy", "locked"));
        log.put("contentPolicy", "preserve-source-content");
        log.put("provider", Map.of("provider", aiResponse.provider(), "model", aiResponse.model(),
            "requestId", aiResponse.requestId(), "providerInvoked", aiResponse.providerInvoked(), "status", aiResponse.providerStatus()));
        log.put("planHash", planHash);
        log.put("designSystem", Map.of("palette", inventory.palette(), "templatePackId", inventory.templatePackId()));
        log.put("slides", plan.slides().stream().map(slide -> Map.of(
            "page", slide.slideNumber(), "pageType", slide.archetype(), "recipeId", slide.recipeId(),
            "changes", List.of(slide.focalEnhancement()), "originalContentChanged", false)).toList());
        log.put("executor", Map.of("name", execution.executorVersion(), "patchedSlides", execution.patchedSlides(),
            "addedShapes", execution.addedShapes(), "addedShapesBySlide", execution.addedShapesBySlide(),
            "safeGeometryValidated", execution.safeGeometryValidated()));
        log.put("preservation", Map.of(
            "slideCountMatch", true, "slideOrderMatch", true, "originalTextPreserved", true,
            "notesPreserved", true, "citationsPreserved", true, "hyperlinksPreserved", true,
            "numericalValuesPreserved", true, "hiddenContentPreserved", true,
            "opaquePackagePartsPreserved", true, "protectedTemplatePartsByteIdentical", true));
        log.put("validation", Map.of(
            "status", quality.status(), "renderedPages", quality.renderedPages(),
            "renderer", Map.of("name", PptEnhancementQualityGate.RENDERER_NAME,
                "version", PptEnhancementQualityGate.RENDERER_VERSION, "resolution", "960x540"),
            "pageRenders", quality.pageRenders(), "checks", quality.checks(), "issues", quality.issues(),
            "autoFixes", List.of(), "warnings", quality.warnings()));
        log.put("outputSize", Files.size(enhanced));
        return log;
    }

    private BaseArtifact requireBase(String projectId, Long userId, boolean forUpdate) {
        Map<String, Object> project = project(projectId, userId);
        if (!"SUCCESS".equals(text(project.get("status")))) throw new IllegalStateException("请先成功生成基础PPT");
        String output = text(project.get("output_path"));
        if (output.isBlank()) throw new IllegalStateException("基础PPTX尚未发布");
        List<Map<String, Object>> rows = jdbc.queryForList(
            "SELECT * FROM ppt_generation_task WHERE project_id=? AND user_id=? AND status='SUCCESS' AND charged_points IS NOT NULL AND output_path IS NOT NULL ORDER BY completed_at DESC,created_at DESC",
            projectId, userId);
        if (rows.isEmpty()) throw new IllegalStateException("该基础PPT缺少可审计的实扣积分记录，请重新生成一次基础PPT后再增幅美化");
        Map<String, Object> task = rows.get(0);
        Path base = inside(Path.of(text(task.get("output_path"))));
        Path projectOutput = inside(Path.of(output));
        if (!base.equals(projectOutput) || !Files.isRegularFile(base, LinkOption.NOFOLLOW_LINKS)) {
            throw new IllegalStateException("基础PPT版本与最近生成任务不一致，请重新生成");
        }
        String storedHash = text(task.get("output_sha256"));
        try {
            if (storedHash.isBlank() || !storedHash.equals(PptxBaselineInspector.sha256(base))) {
                throw new IllegalStateException("基础PPT哈希校验失败，请重新生成");
            }
        } catch (IllegalStateException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IllegalStateException("基础PPT读取失败", exception);
        }
        return new BaseArtifact(text(task.get("id")), base, storedHash, number(task.get("charged_points")).intValue());
    }

    private void requireProjectForUpdate(String projectId, Long userId) {
        List<Map<String, Object>> rows = jdbc.queryForList(
            "SELECT id FROM ppt_project WHERE id=? AND user_id=? FOR UPDATE", projectId, userId);
        if (rows.isEmpty()) throw new IllegalArgumentException("PPT项目不存在或无权访问");
    }

    private Map<String, Object> project(String projectId, Long userId) {
        List<Map<String, Object>> rows = jdbc.queryForList("SELECT * FROM ppt_project WHERE id=? AND user_id=?", projectId, userId);
        if (rows.isEmpty()) throw new IllegalArgumentException("PPT项目不存在或无权访问");
        return rows.get(0);
    }

    private Map<String, Object> requireTask(String projectId, String taskId, Long userId) {
        List<Map<String, Object>> rows = jdbc.queryForList(
            "SELECT * FROM ppt_enhancement_task WHERE id=? AND project_id=? AND user_id=?", taskId, projectId, userId);
        if (rows.isEmpty()) throw new IllegalArgumentException("增幅美化任务不存在或无权访问");
        return rows.get(0);
    }

    private Map<String, Object> latestTask(String projectId, Long userId, String baseTaskId) {
        List<Map<String, Object>> rows = jdbc.queryForList(
            "SELECT * FROM ppt_enhancement_task WHERE project_id=? AND user_id=? AND base_generation_task_id=? ORDER BY created_at DESC",
            projectId, userId, baseTaskId);
        return rows.isEmpty() ? null : taskView(rows.get(0));
    }

    private Map<String, Object> taskView(Map<String, Object> task) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", task.get("id"));
        result.put("projectId", task.get("project_id"));
        result.put("baseGenerationTaskId", task.get("base_generation_task_id"));
        result.put("status", task.get("status"));
        result.put("progress", number(task.get("progress")).intValue());
        result.put("currentStage", task.get("current_stage"));
        result.put("mode", task.get("mode"));
        result.put("profile", task.get("profile"));
        result.put("textPolicy", task.get("text_policy"));
        result.put("costPoints", number(task.get("enhancement_cost_points")).intValue());
        result.put("pointsCharged", truth(task.get("points_charged")));
        result.put("slideCount", number(task.get("slide_count")).intValue());
        result.put("fileSize", number(task.get("output_size")).longValue());
        result.put("provider", task.get("provider"));
        result.put("model", task.get("model"));
        result.put("providerInvoked", truth(task.get("provider_invoked")));
        result.put("providerStatus", task.get("provider_status"));
        result.put("skillName", task.get("skill_name"));
        result.put("skillVersion", task.get("skill_version"));
        result.put("skillHash", task.get("skill_hash"));
        result.put("planHash", task.get("plan_hash"));
        result.put("outputSha256", task.get("output_sha256"));
        result.put("errorMessage", task.get("error_message"));
        result.put("createdAt", task.get("created_at"));
        result.put("updatedAt", task.get("updated_at"));
        result.put("completedAt", task.get("completed_at"));
        result.put("downloadable", "SUCCESS".equals(text(task.get("status"))) && !text(task.get("output_path")).isBlank());
        return result;
    }

    private String baseTemplatePack(String baseTaskId) {
        List<Map<String, Object>> rows = jdbc.queryForList("SELECT template_pack_id FROM ppt_generation_task WHERE id=?", baseTaskId);
        return rows.isEmpty() ? "" : text(rows.get(0).get("template_pack_id"));
    }

    private void scheduleAfterCommit(String taskId) {
        Runnable submit = () -> {
            try {
                taskExecutor.execute(() -> runTask(taskId));
            } catch (RuntimeException exception) {
                jdbc.update("UPDATE ppt_enhancement_task SET status='FAILED',progress=0,current_stage='任务调度失败',error_message=?,completed_at=?,updated_at=? WHERE id=? AND status='QUEUED'",
                    safeError(exception), LocalDateTime.now(), LocalDateTime.now(), taskId);
            }
        };
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override public void afterCommit() { submit.run(); }
            });
        } else {
            submit.run();
        }
    }

    private void update(String taskId, int progress, String stage) {
        jdbc.update("UPDATE ppt_enhancement_task SET progress=?,current_stage=?,updated_at=? WHERE id=? AND status='RUNNING'",
            progress, stage, LocalDateTime.now(), taskId);
    }

    private void publishAtomically(Path staging, Path published) throws Exception {
        staging = inside(staging);
        published = inside(published);
        if (!staging.getParent().equals(published.getParent())) {
            throw new IllegalStateException("增幅美化暂存目录与发布目录不在同一安全父目录");
        }
        if (Files.exists(published)) throw new IllegalStateException("增幅美化发布目录已存在");
        try {
            Files.move(staging, published, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException exception) {
            throw new IllegalStateException("当前文件系统不支持增幅产物原子发布", exception);
        }
        inside(published);
    }

    private void cleanup(Path path) {
        if (path == null) return;
        Path safe;
        try {
            safe = inside(path);
        } catch (RuntimeException unsafePath) {
            return;
        }
        if (!Files.exists(safe, LinkOption.NOFOLLOW_LINKS)) return;
        try (var paths = Files.walk(safe)) {
            paths.sorted(Comparator.reverseOrder()).forEach(item -> {
                try { Files.deleteIfExists(item); } catch (Exception ignored) { }
            });
        } catch (Exception ignored) { }
    }

    private Path enhancementRoot(Long userId, String projectId) {
        return inside(root.resolve(userId.toString()).resolve(projectId).resolve("enhancements"));
    }

    Path inside(Path path) {
        Path normalized = path.toAbsolutePath().normalize();
        if (!normalized.startsWith(root)) throw new IllegalArgumentException("非法PPT文件路径");
        requireNoReparseComponents(normalized);
        return normalized;
    }

    private void requireNoReparseComponents(Path path) {
        try {
            if (Files.isSymbolicLink(root) || !Files.isDirectory(root, LinkOption.NOFOLLOW_LINKS)) {
                throw new IllegalArgumentException("PPT存储根目录不存在或属于符号链接/重解析目录");
            }
            Path rootParent = root.getParent();
            if (rootParent == null) throw new IllegalArgumentException("PPT存储根目录缺少安全父目录");
            Path realRoot = root.toRealPath();
            Path expectedRoot = rootParent.toRealPath().resolve(root.getFileName()).normalize();
            if (!realRoot.equals(expectedRoot)) {
                throw new IllegalArgumentException("PPT存储根目录属于符号链接/重解析目录");
            }
            Path cursor = root;
            for (Path segment : root.relativize(path)) {
                cursor = cursor.resolve(segment);
                if (!Files.exists(cursor, LinkOption.NOFOLLOW_LINKS)) return;
                Path expected = realRoot.resolve(root.relativize(cursor)).normalize();
                if (Files.isSymbolicLink(cursor) || !cursor.toRealPath().equals(expected)) {
                    throw new IllegalArgumentException("PPT文件路径包含符号链接/重解析点：" + cursor);
                }
            }
        } catch (IllegalArgumentException exception) {
            throw exception;
        } catch (IOException exception) {
            throw new IllegalArgumentException("无法验证PPT文件路径安全边界", exception);
        }
    }

    private int half(int base) {
        return base <= 0 ? 0 : (base + 1) / 2;
    }

    private String validateIdempotencyKey(String value) {
        if (!value.matches("[A-Za-z0-9_-]{8,128}")) throw new IllegalArgumentException("增幅任务幂等键格式不正确");
        return value;
    }

    private String safeFileName(String name) {
        String result = name == null ? "" : name.replaceAll("[\\\\/:*?\"<>|\\r\\n]", "_").trim();
        return result.isBlank() ? "未命名学术答辩" : result;
    }

    private String safeError(Throwable error) {
        String message = error == null || error.getMessage() == null ? "" : error.getMessage().replaceAll("[\\r\\n]+", " ").trim();
        return message.isBlank() ? "增幅美化失败" : message.substring(0, Math.min(500, message.length()));
    }

    private String text(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    private Number number(Object value) {
        if (value instanceof Number number) return number;
        try { return Long.parseLong(text(value)); } catch (Exception ignored) { return 0; }
    }

    private boolean truth(Object value) {
        if (value instanceof Boolean bool) return bool;
        if (value instanceof Number number) return number.intValue() != 0;
        return "true".equalsIgnoreCase(text(value)) || "1".equals(text(value));
    }

    private record BaseArtifact(String taskId, Path path, String sha256, int chargedPoints) {}
}
