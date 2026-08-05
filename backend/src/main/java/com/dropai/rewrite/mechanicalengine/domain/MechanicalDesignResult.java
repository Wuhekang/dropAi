package com.dropai.rewrite.mechanicalengine.domain;

import com.dropai.rewrite.mechanicalengine.validation.MechanicalArtifactValidator;

import java.util.List;

public record MechanicalDesignResult(
        String resultId,
        String projectId,
        MechanicalDesignSpec designSpec,
        MechanicalProject.AssemblySpec assembly,
        List<MechanicalProject.CADModelSpec> parts,
        List<MechanicalProject.Artifact> modelFiles,
        List<MechanicalProject.Artifact> stepFiles,
        List<MechanicalProject.Artifact> drawingFiles,
        List<BomItem> bom,
        MechanicalArtifactValidator.ValidationReport validationReport,
        String status
) {
    public record BomItem(String partNumber, String name, String material, String manufacturing, int quantity) {}
}
