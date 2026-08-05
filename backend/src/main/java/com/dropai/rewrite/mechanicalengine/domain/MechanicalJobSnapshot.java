package com.dropai.rewrite.mechanicalengine.domain;

import java.time.LocalDateTime;

public record MechanicalJobSnapshot(
        String jobId,
        MechanicalJobStatus status,
        int progress,
        String stage,
        String message,
        MechanicalProject project,
        MechanicalDesignResult result,
        java.util.List<MechanicalProject.Artifact> liveArtifacts,
        boolean resumable,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {}
