package com.dropai.rewrite.mechanicalengine.service;

import com.dropai.rewrite.auth.AuthContext;
import com.dropai.rewrite.mechanicalengine.domain.*;
import com.dropai.rewrite.mechanicalengine.validation.MechanicalArtifactValidator;
import org.springframework.core.task.TaskExecutor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.nio.file.Files;
import java.nio.file.Path;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;

@Service
public class MechanicalJobService {
    private final MechanicalEngineService engine;
    private final TaskExecutor executor;
    private final ConcurrentHashMap<String, MechanicalJobSnapshot> jobs = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, MechanicalDesignResult> results = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, JobContext> contexts = new ConcurrentHashMap<>();

    public MechanicalJobService(MechanicalEngineService engine, TaskExecutor executor) {
        this.engine = engine;
        this.executor = executor;
    }

    public MechanicalJobSnapshot start(String requirement) {
        if (requirement == null || requirement.isBlank()) throw new IllegalArgumentException("MECHANICAL_REQUIREMENT_REQUIRED");
        Long userId = AuthContext.requireUserId();
        String jobId = "mechanical_" + UUID.randomUUID().toString().replace("-", "");
        LocalDateTime now = LocalDateTime.now();
        Path workspace;
        try { workspace = Files.createTempDirectory("dropai-mechanical-" + jobId + "-"); }
        catch (Exception exception) { throw new IllegalStateException("MECHANICAL_WORKSPACE_CREATE_FAILED", exception); }
        contexts.put(jobId, new JobContext(requirement, userId, workspace));
        MechanicalJobSnapshot created = new MechanicalJobSnapshot(jobId, MechanicalJobStatus.CREATED, 0,
                "CREATED", "Mechanical job queued", null, null, List.of(), false, now, now);
        jobs.put(jobId, created);
        executor.execute(() -> run(jobId, requirement, userId));
        return created;
    }

    public MechanicalJobSnapshot get(String jobId) {
        ownedContext(jobId);
        return snapshot(jobId);
    }

    private MechanicalJobSnapshot snapshot(String jobId) {
        MechanicalJobSnapshot job = jobs.get(jobId);
        if (job == null) throw new IllegalArgumentException("MECHANICAL_JOB_NOT_FOUND");
        return new MechanicalJobSnapshot(job.jobId(), job.status(), job.progress(), job.stage(), job.message(),
                job.project(), job.result(), liveArtifacts(jobId), job.resumable(), job.createdAt(), job.updatedAt());
    }

    public Resource artifact(String jobId, String name) {
        JobContext context = ownedContext(jobId);
        try (var files = Files.walk(context.workspace())) {
            Path match = files.filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().equals(name)).findFirst()
                    .orElseThrow(() -> new IllegalArgumentException("MECHANICAL_LIVE_ARTIFACT_NOT_FOUND"));
            return new FileSystemResource(match);
        } catch (java.io.IOException exception) {
            throw new IllegalStateException("MECHANICAL_LIVE_ARTIFACT_READ_FAILED", exception);
        }
    }

    public MechanicalJobSnapshot resume(String jobId) {
        MechanicalJobSnapshot previous = get(jobId);
        JobContext context = contexts.get(jobId);
        if (context == null || !previous.resumable()) throw new IllegalStateException("MECHANICAL_JOB_NOT_RESUMABLE");
        update(jobId, MechanicalJobStatus.CREATED, Math.max(previous.progress(), 1), "RESUME_QUEUED",
                "Resume queued from the existing FreeCAD checkpoint", previous.project(), null, false);
        executor.execute(() -> run(jobId, context));
        return snapshot(jobId);
    }

    public MechanicalDesignResult requireResult(String resultId) {
        MechanicalDesignResult result = results.get(resultId);
        if (result == null) throw new IllegalArgumentException("MECHANICAL_RESULT_NOT_FOUND");
        ownedContext(resultId);
        return result;
    }

    public MechanicalProject requireProjectByResult(String resultId, Long userId) {
        JobContext context = contexts.get(resultId);
        if (context == null || !context.userId().equals(userId)) throw new IllegalArgumentException("MECHANICAL_RESULT_FORBIDDEN");
        MechanicalProject project = snapshot(resultId).project();
        if (project == null) throw new IllegalArgumentException("MECHANICAL_RESULT_PROJECT_NOT_FOUND");
        return project;
    }

    private void run(String jobId, String requirement, Long userId) {
        run(jobId, contexts.get(jobId));
    }

    private void run(String jobId, JobContext context) {
        try {
            MechanicalProject project = engine.execute(context.requirement(), context.userId(), event ->
                    update(jobId, status(event.stage()), event.progress(), event.stage(), event.message(), null, null, false), context.workspace());
            if (!"COMPLETED".equals(project.getStatus())) {
                update(jobId, MechanicalJobStatus.FAILED, snapshot(jobId).progress(), project.getCurrentStage(), project.getFailureMessage(), project, null, true);
                return;
            }
            MechanicalDesignResult result = toResult(jobId, project);
            results.put(result.resultId(), result);
            update(jobId, MechanicalJobStatus.COMPLETED, 100, "COMPLETED", "Mechanical result is ready", project, result, false);
        } catch (Exception exception) {
            update(jobId, MechanicalJobStatus.FAILED, snapshot(jobId).progress(), "FAILED", readable(exception), snapshot(jobId).project(), null, true);
        }
    }

    private MechanicalJobStatus status(String stage) {
        if (stage == null) return MechanicalJobStatus.CAD_GENERATING;
        if (stage.startsWith("REQUIREMENT")) return MechanicalJobStatus.REQUIREMENT_ANALYSIS;
        if (stage.startsWith("DESIGN")) return MechanicalJobStatus.DESIGNING;
        if (stage.contains("STEP")) return MechanicalJobStatus.STEP_EXPORTING;
        if (stage.contains("PART_BUILDING") || stage.contains("FEATURE")) return MechanicalJobStatus.BUILDING_PART;
        if (stage.contains("EXPORTING")) return MechanicalJobStatus.EXPORTING;
        if (stage.contains("DRAWING")) return MechanicalJobStatus.DRAWING_GENERATING;
        if (stage.contains("VALIDAT")) return MechanicalJobStatus.VALIDATING;
        if (stage.contains("FREECAD") || stage.contains("PART")) return MechanicalJobStatus.FREECAD_RUNNING;
        return MechanicalJobStatus.CAD_GENERATING;
    }

    private MechanicalDesignResult toResult(String resultId, MechanicalProject project) {
        List<MechanicalProject.Artifact> artifacts = project.getArtifacts();
        List<MechanicalDesignResult.BomItem> bom = project.getParts().stream()
                .map(part -> new MechanicalDesignResult.BomItem(part.partNumber(), part.name(), part.material(), part.manufacturing(), 1)).toList();
        return new MechanicalDesignResult(resultId, project.getProjectId(), project.getDesignSpec(), project.getAssembly(), project.getParts(),
                artifacts.stream().filter(a -> "MODEL".equals(a.category())).toList(),
                artifacts.stream().filter(a -> "STEP".equals(a.category())).toList(),
                artifacts.stream().filter(a -> "DRAWING".equals(a.category())).toList(), bom,
                new MechanicalArtifactValidator.ValidationReport(true, List.of()), "COMPLETED");
    }

    private void update(String id, MechanicalJobStatus status, int progress, String stage, String message,
                        MechanicalProject project, MechanicalDesignResult result) {
        update(id, status, progress, stage, message, project, result, false);
    }

    private void update(String id, MechanicalJobStatus status, int progress, String stage, String message,
                        MechanicalProject project, MechanicalDesignResult result, boolean resumable) {
        MechanicalJobSnapshot previous = jobs.get(id);
        jobs.put(id, new MechanicalJobSnapshot(id, status, progress, stage, message, project, result,
                previous == null ? List.of() : previous.liveArtifacts(), resumable,
                previous == null ? LocalDateTime.now() : previous.createdAt(), LocalDateTime.now()));
    }

    private String readable(Exception exception) {
        String message = exception.getMessage();
        return message == null || message.isBlank() ? exception.getClass().getSimpleName() : message;
    }

    private List<MechanicalProject.Artifact> liveArtifacts(String jobId) {
        JobContext context = contexts.get(jobId);
        if (context == null || !Files.isDirectory(context.workspace())) return List.of();
        List<MechanicalProject.Artifact> artifacts = new ArrayList<>();
        try (var files = Files.walk(context.workspace())) {
            for (Path path : files.filter(Files::isRegularFile).toList()) {
                String name = path.getFileName().toString();
                String category;
                String mediaType;
                if (name.matches("P\\d+\\.stl") || name.equals("Assembly.stl")) { category="MODEL"; mediaType="model/stl"; }
                else if (name.matches("P\\d+\\.step") || name.equals("Assembly.STEP")) { category="STEP"; mediaType="application/step"; }
                else if (name.endsWith(".svg") || name.endsWith(".dxf")) { category="DRAWING"; mediaType=name.endsWith(".svg")?"image/svg+xml":"image/vnd.dxf"; }
                else if (name.equals("freecad-runtime-report.json")) { category="ANALYSIS"; mediaType="application/json"; }
                else continue;
                String url = "/api/mechanical/jobs/" + jobId + "/artifacts/" + URLEncoder.encode(name, StandardCharsets.UTF_8);
                artifacts.add(new MechanicalProject.Artifact(name, category, mediaType, Files.size(path), url, true));
            }
        } catch (Exception ignored) {
            return List.of();
        }
        return artifacts.stream().sorted(java.util.Comparator.comparing(MechanicalProject.Artifact::name)).toList();
    }

    private JobContext ownedContext(String jobId) {
        JobContext context = contexts.get(jobId);
        if (context == null) throw new IllegalArgumentException("MECHANICAL_JOB_NOT_FOUND");
        if (!context.userId().equals(AuthContext.requireUserId())) throw new IllegalArgumentException("MECHANICAL_JOB_FORBIDDEN");
        return context;
    }

    private record JobContext(String requirement, Long userId, Path workspace) {}
}
