package com.dropai.rewrite.service;

import com.dropai.rewrite.auth.AuthContext;
import com.dropai.rewrite.entity.DocumentJobRecord;
import com.dropai.rewrite.mapper.DocumentJobMapper;
import com.dropai.rewrite.modules.model.DesignProject;
import com.dropai.rewrite.vo.DesignPackageJobVO;
import com.dropai.rewrite.vo.DesignPackageVO;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.core.task.TaskExecutor;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

@Service
public class DesignPackageJobService {
    private static final String FEATURE = "DESIGN_PACKAGE_JOB";
    private static final Duration STALE_TIMEOUT = Duration.ofMinutes(30);

    private final DocumentJobMapper mapper;
    private final DesignPackageService packageService;
    private final PointService pointService;
    private final ObjectMapper objectMapper;
    private final TaskExecutor taskExecutor;

    public DesignPackageJobService(DocumentJobMapper mapper, DesignPackageService packageService,
                                   PointService pointService, ObjectMapper objectMapper, TaskExecutor taskExecutor) {
        this.mapper = mapper;
        this.packageService = packageService;
        this.pointService = pointService;
        this.objectMapper = objectMapper;
        this.taskExecutor = taskExecutor;
    }

    public DesignPackageJobVO create(DesignProject project) {
        Long userId = AuthContext.requireUserId();
        int cost = pointService.featureCostPoints(PointService.DESIGN_GENERATE);
        pointService.ensureEnoughCustom(userId, cost);
        String jobId = "dp_" + UUID.randomUUID().toString().replace("-", "");
        LocalDateTime now = LocalDateTime.now();
        DocumentJobRecord record = new DocumentJobRecord();
        record.setJobId(jobId);
        record.setUserId(userId);
        record.setFileName(title(project) + "-design-package-job.json");
        record.setSourceFeature(FEATURE);
        record.setMode("design_package");
        record.setModeName("Design package job");
        record.setPlatform("ENGINEERING");
        record.setPlatformName("Engineering generation");
        record.setStatus("PENDING");
        record.setTotalParagraphs(100);
        record.setProcessedParagraphs(0);
        record.setRewrittenParagraphs(0);
        record.setCharCount(0);
        record.setCostPoints(cost);
        record.setPointsCharged(false);
        record.setMessage("Task queued");
        record.setParagraphsJson(toJson(state(jobId, "PENDING", "PARSING", 0, "Task queued", null, null, null)));
        record.setCreatedAt(now);
        record.setUpdatedAt(now);
        mapper.insert(record);
        taskExecutor.execute(() -> run(jobId, userId, project));
        return toVO(record);
    }

    public DesignPackageJobVO get(String jobId) {
        DocumentJobRecord record = requireOwned(jobId);
        if ("RUNNING".equals(record.getStatus()) && record.getUpdatedAt() != null
                && record.getUpdatedAt().isBefore(LocalDateTime.now().minus(STALE_TIMEOUT))) {
            fail(record, "STALE_HEARTBEAT", "Task heartbeat timed out at stage " + stage(record));
            record = mapper.selectById(jobId);
        }
        return toVO(record);
    }

    private void run(String jobId, Long userId, DesignProject project) {
        AuthContext.setUserId(userId);
        try {
            update(jobId, "RUNNING", "PARSING", 2, "Starting design package generation", null, null);
            DesignPackageVO result = packageService.generateForJob(project, (stage, progress, message) ->
                    update(jobId, "RUNNING", stage, progress, message, null, null));
            if ("success".equalsIgnoreCase(result.getStatus())) {
                DocumentJobRecord record = mapper.selectById(jobId);
                if (!Boolean.TRUE.equals(record.getPointsCharged())) {
                    pointService.deductFeatureForJob(userId, jobId, PointService.DESIGN_GENERATE,
                            "Generate mechanical design package " + title(result.getProject()));
                    record.setPointsCharged(true);
                    mapper.updateById(record);
                }
                update(jobId, "SUCCESS", "COMPLETED", 100, result.getMessage(), null, result);
            } else {
                update(jobId, "FAILED", "FAILED", 96, result.getMessage(), "QUALITY_GATE_FAILED", result);
            }
        } catch (Exception exception) {
            DocumentJobRecord record = mapper.selectById(jobId);
            if (record != null) fail(record, "GENERATION_FAILED", readable(exception));
        } finally {
            AuthContext.clear();
        }
    }

    private void update(String jobId, String status, String stage, int progress, String message,
                        String errorCode, DesignPackageVO result) {
        DocumentJobRecord record = mapper.selectById(jobId);
        if (record == null) return;
        int normalized = Math.max(0, Math.min(100, progress));
        record.setStatus(status);
        record.setProcessedParagraphs(normalized);
        record.setMessage(message == null ? stage : message);
        record.setUpdatedAt(LocalDateTime.now());
        record.setParagraphsJson(toJson(state(jobId, status, stage, normalized, record.getMessage(), errorCode, result, finishedAt(status))));
        mapper.updateById(record);
    }

    private void fail(DocumentJobRecord record, String errorCode, String message) {
        record.setStatus("FAILED");
        record.setMessage(message == null || message.isBlank() ? "Design package generation failed" : message);
        record.setUpdatedAt(LocalDateTime.now());
        int progress = record.getProcessedParagraphs() == null ? 0 : Math.max(0, Math.min(96, record.getProcessedParagraphs()));
        record.setProcessedParagraphs(progress);
        record.setParagraphsJson(toJson(state(record.getJobId(), "FAILED", stage(record), progress, record.getMessage(), errorCode, null, LocalDateTime.now())));
        mapper.updateById(record);
    }

    @SuppressWarnings("unchecked")
    private DesignPackageJobVO toVO(DocumentJobRecord record) {
        Map<String, Object> state = Map.of();
        try {
            if (record.getParagraphsJson() != null && !record.getParagraphsJson().isBlank()) {
                state = objectMapper.readValue(record.getParagraphsJson(), Map.class);
            }
        } catch (Exception ignored) {
        }
        DesignPackageJobVO vo = new DesignPackageJobVO();
        vo.setJobId(record.getJobId());
        vo.setStatus(string(state.get("status"), record.getStatus()));
        vo.setStage(string(state.get("stage"), "PARSING"));
        vo.setProgress(number(state.get("progress"), record.getProcessedParagraphs()));
        vo.setMessage(string(state.get("message"), record.getMessage()));
        vo.setErrorCode(string(state.get("errorCode"), null));
        vo.setCreatedAt(record.getCreatedAt());
        vo.setHeartbeatAt(record.getUpdatedAt());
        vo.setFinishedAt(parseTime(state.get("finishedAt")));
        vo.setCostPoints(record.getCostPoints());
        vo.setPointsCharged(Boolean.TRUE.equals(record.getPointsCharged()));
        Object result = state.get("result");
        if (result != null) {
            vo.setResult(objectMapper.convertValue(result, DesignPackageVO.class));
        }
        return vo;
    }

    private DocumentJobRecord requireOwned(String jobId) {
        Long userId = AuthContext.requireUserId();
        DocumentJobRecord record = mapper.selectById(jobId);
        if (record == null || !FEATURE.equals(record.getSourceFeature()) || !userId.equals(record.getUserId())) {
            throw new IllegalArgumentException("Design package job not found");
        }
        return record;
    }

    private Map<String, Object> state(String jobId, String status, String stage, int progress, String message,
                                      String errorCode, DesignPackageVO result, LocalDateTime finishedAt) {
        return Map.of(
                "jobId", jobId,
                "status", status,
                "stage", stage == null ? "" : stage,
                "progress", progress,
                "message", message == null ? "" : message,
                "errorCode", errorCode == null ? "" : errorCode,
                "finishedAt", finishedAt == null ? "" : finishedAt.toString(),
                "result", result == null ? Map.of() : result
        );
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception exception) {
            return "{}";
        }
    }

    private String title(DesignProject project) {
        if (project == null) return "mechanical-design";
        String title = project.getProjectTitle();
        if (title == null || title.isBlank()) title = project.getEquipmentName();
        return title == null || title.isBlank() ? "mechanical-design" : title;
    }

    private String stage(DocumentJobRecord record) {
        try {
            Map<?, ?> state = objectMapper.readValue(record.getParagraphsJson(), Map.class);
            Object stage = state.get("stage");
            return stage == null ? "FAILED" : stage.toString();
        } catch (Exception exception) {
            return "FAILED";
        }
    }

    private LocalDateTime finishedAt(String status) {
        return ("SUCCESS".equals(status) || "FAILED".equals(status) || "CANCELLED".equals(status)) ? LocalDateTime.now() : null;
    }

    private LocalDateTime parseTime(Object value) {
        if (value == null || value.toString().isBlank()) return null;
        try {
            return LocalDateTime.parse(value.toString());
        } catch (Exception exception) {
            return null;
        }
    }

    private String string(Object value, String fallback) {
        if (value == null) return fallback;
        String text = value.toString();
        return text.isBlank() ? fallback : text;
    }

    private Integer number(Object value, Integer fallback) {
        if (value instanceof Number n) return n.intValue();
        try {
            return value == null ? fallback : Integer.parseInt(value.toString());
        } catch (Exception exception) {
            return fallback == null ? 0 : fallback;
        }
    }

    private String readable(Exception exception) {
        String message = exception.getMessage();
        return message == null || message.isBlank() ? exception.getClass().getSimpleName() : message;
    }
}
