package com.dropai.rewrite.mechanicalengine.domain;

import java.util.List;
import java.util.Map;

public record MechanicalDesignSpec(
        Product product,
        Requirements requirements,
        List<FunctionNode> functions,
        Architecture architecture,
        List<Module> modules,
        List<PartPlan> parts,
        List<AssemblyIntent> assemblyIntent,
        List<Parameter> parameters,
        List<MaterialDecision> materials,
        List<ManufacturingDecision> manufacturing,
        DesignProvenance provenance
) {
    public record Product(String type, String name, String purpose, String environment,
                          String operatingPrinciple, List<String> coreFunctions) {}
    public record Requirements(List<String> functions, List<String> performanceGoals,
                               List<String> operatingConditions, List<String> engineeringConstraints) {}
    public record FunctionNode(String name, String purpose, List<FunctionNode> children) {}
    public record Architecture(String selectedConcept, String selectionReason, List<String> loadPath,
                               List<String> motionPath) {}
    public record Module(String id, String name, String function, List<String> interfaces,
                         String installation) {}
    public record PartPlan(String partNumber, String name, String moduleId, String function,
                           String material, String manufacturing, List<FeatureRequirement> cadRequirements) {}
    public record FeatureRequirement(int order, String type, String intent, Map<String, Object> parameters) {}
    public record AssemblyIntent(String type, String componentA, String referenceA,
                                 String componentB, String referenceB, String purpose) {}
    public record Parameter(String name, double value, String unit, String engineeringReason) {}
    public record MaterialDecision(String partNumber, String material, String reason) {}
    public record ManufacturingDecision(String partNumber, String process, String reason) {}
    public record DesignProvenance(String reasoningSource, List<String> knowledgeReferences,
                                   List<String> architectureDecisions) {}
}
