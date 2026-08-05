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

@Service
public class MechanicalJobService {
    private final MechanicalEngineService engine;
    private final TaskExecutor executor;
    private final ConcurrentHashMap<String, MechanicalJobSnapshot> jobs = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, MechanicalDesignResult> results = new ConcurrentHashMap<>();

    public MechanicalJobService(MechanicalEngineService engine, TaskExecutor executor) {
        this.engine = engine;
        this.executor = executor;
    }

    public MechanicalJobSnapshot start(String requirement) {
        if (requirement == null || requirement.isBlank()) throw new IllegalArgumentException("MECHANICAL_REQUIREMENT_REQUIRED");
        Long userId = AuthContext.requireUserId();
        String jobId = "mechanical_" + UUID.randomUUID().toString().replace("-", "");
        LocalDateTime now = LocalDateTime.now();
        MechanicalJobSnapshot created = new MechanicalJobSnapshot(jobId, MechanicalJobStatus.CREATED, 0,
                "CREATED", "Mechanical job queued", null, null, now, now);
        jobs.put(jobId, created);
        executor.execute(() -> run(jobId, requirement, userId));
        return created;
    }

    public MechanicalJobSnapshot get(String jobId) {
        MechanicalJobSnapshot job = jobs.get(jobId);
        if (job == null) throw new IllegalArgumentException("MECHANICAL_JOB_NOT_FOUND");
        return job;
    }

    public MechanicalDesignResult requireResult(String resultId) {
        MechanicalDesignResult result = results.get(resultId);
        if (result == null) throw new IllegalArgumentException("MECHANICAL_RESULT_NOT_FOUND");
        return result;
    }

    public MechanicalProject requireProjectByResult(String resultId) {
        return jobs.values().stream()
                .filter(job -> job.result() != null && resultId.equals(job.result().resultId()) && job.project() != null)
                .map(MechanicalJobSnapshot::project).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("MECHANICAL_RESULT_PROJECT_NOT_FOUND"));
    }

    private void run(String jobId, String requirement, Long userId) {
        try {
            MechanicalProject project = engine.execute(requirement, userId, event ->
                    update(jobId, status(event.stage()), event.progress(), event.stage(), event.message(), null, null));
            if (!"COMPLETED".equals(project.getStatus())) {
                update(jobId, MechanicalJobStatus.FAILED, 100, project.getCurrentStage(), project.getFailureMessage(), project, null);
                return;
            }
            MechanicalDesignResult result = toResult(jobId, project);
            results.put(result.resultId(), result);
            update(jobId, MechanicalJobStatus.COMPLETED, 100, "COMPLETED", "Mechanical result is ready", project, result);
        } catch (Exception exception) {
            update(jobId, MechanicalJobStatus.FAILED, 100, "FAILED", readable(exception), null, null);
        }
    }

    private MechanicalJobStatus status(String stage) {
        if (stage == null) return MechanicalJobStatus.CAD_GENERATING;
        if (stage.startsWith("REQUIREMENT")) return MechanicalJobStatus.REQUIREMENT_ANALYSIS;
        if (stage.startsWith("DESIGN")) return MechanicalJobStatus.DESIGNING;
        if (stage.contains("STEP")) return MechanicalJobStatus.STEP_EXPORTING;
        if (stage.contains("DRAWING")) return MechanicalJobStatus.DRAWING_GENERATING;
        if (stage.contains("VALIDAT")) return MechanicalJobStatus.VALIDATING;
        if (stage.contains("FREECAD") || stage.contains("FEATURE") || stage.contains("PART")) return MechanicalJobStatus.FREECAD_RUNNING;
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
        MechanicalJobSnapshot previous = jobs.get(id);
        jobs.put(id, new MechanicalJobSnapshot(id, status, progress, stage, message, project, result,
                previous == null ? LocalDateTime.now() : previous.createdAt(), LocalDateTime.now()));
    }

    private String readable(Exception exception) {
        String message = exception.getMessage();
        return message == null || message.isBlank() ? exception.getClass().getSimpleName() : message;
    }
}
