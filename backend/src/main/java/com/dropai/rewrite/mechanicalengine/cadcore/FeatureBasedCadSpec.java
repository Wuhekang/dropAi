package com.dropai.rewrite.mechanicalengine.cadcore;

import com.dropai.rewrite.mechanicalengine.domain.MechanicalProject;
import com.dropai.rewrite.mechanicalengine.domain.MechanicalDesignSpec;

import java.util.List;
import java.util.Map;

public record FeatureBasedCadSpec(String projectId, List<Part> parts, Assembly assembly) {
    public record Part(String partNumber, String partName, String material, List<Feature> body) {}
    public record Feature(int order, String featureType, String intent, Map<String, Object> parameters) {}
    public record Assembly(List<MechanicalProject.AssemblyComponent> components,
                           List<MechanicalProject.Constraint> constraints) {}

    public static FeatureBasedCadSpec from(MechanicalProject project) {
        if (project.getDesignSpec() != null) {
            return new MechanicalDesignCadConverter().convert(project.getProjectId(), project.getDesignSpec());
        }
        List<Part> parts = project.getParts().stream().map(part -> new Part(
                part.partNumber(), part.name(), part.material(),
                part.features().stream().map(feature -> new Feature(
                        feature.order(), feature.type().toUpperCase(), feature.intent(), feature.parameters())).toList()
        )).toList();
        return new FeatureBasedCadSpec(project.getProjectId(), parts,
                new Assembly(project.getAssembly().getComponents(), project.getAssembly().getConstraints()));
    }
}
