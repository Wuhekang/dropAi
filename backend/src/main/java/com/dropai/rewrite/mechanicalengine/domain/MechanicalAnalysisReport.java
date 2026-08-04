package com.dropai.rewrite.mechanicalengine.domain;

import java.util.List;

public record MechanicalAnalysisReport(
        String method,
        double governingLoadN,
        double estimatedStressMpa,
        double estimatedDisplacementMm,
        double safetyFactor,
        List<String> loadPath,
        List<String> materialFindings,
        List<CloudPoint> stressCloud,
        String conclusion,
        boolean calculixReady
) {
    public record CloudPoint(double normalizedPosition, double stressMpa) {}
}
