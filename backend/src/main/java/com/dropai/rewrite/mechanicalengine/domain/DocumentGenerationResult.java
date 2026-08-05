package com.dropai.rewrite.mechanicalengine.domain;

import java.time.LocalDateTime;
import java.util.List;

public record DocumentGenerationResult(
        String documentJobId,
        String mechanicalResultId,
        String documentType,
        String status,
        int progress,
        String message,
        List<MechanicalProject.Artifact> artifacts,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {}
