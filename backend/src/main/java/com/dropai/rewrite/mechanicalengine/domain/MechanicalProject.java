package com.dropai.rewrite.mechanicalengine.domain;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class MechanicalProject {
    private String projectId = "";
    private String productName = "";
    private String scenario = "";
    private String status = "PENDING";
    private String currentStage = "REQUIREMENT_UNDERSTANDING";
    private String failureCode = "";
    private String failureMessage = "";
    private MechanicalDesignSpec designSpec;
    private MechanicalRequirementAnalysis requirementAnalysis;
    private FunctionalRequirement requirement = new FunctionalRequirement();
    private MechanicalConcept concept = new MechanicalConcept();
    private List<EngineeringParameter> parameters = new ArrayList<>();
    private AssemblySpec assembly = new AssemblySpec();
    private List<CADModelSpec> parts = new ArrayList<>();
    private DrawingSpec drawings = new DrawingSpec();
    private AnalysisSpec analysis = new AnalysisSpec();
    private MechanicalAnalysisReport analysisReport;
    private List<StageState> stages = new ArrayList<>();
    private List<Artifact> artifacts = new ArrayList<>();

    public String getProjectId() { return projectId; } public void setProjectId(String v) { projectId = v; }
    public String getProductName() { return productName; } public void setProductName(String v) { productName = v; }
    public String getScenario() { return scenario; } public void setScenario(String v) { scenario = v; }
    public String getStatus() { return status; } public void setStatus(String v) { status = v; }
    public String getCurrentStage() { return currentStage; } public void setCurrentStage(String v) { currentStage = v; }
    public String getFailureCode() { return failureCode; } public void setFailureCode(String v) { failureCode = v; }
    public String getFailureMessage() { return failureMessage; } public void setFailureMessage(String v) { failureMessage = v; }
    public MechanicalDesignSpec getDesignSpec() { return designSpec; } public void setDesignSpec(MechanicalDesignSpec v) { designSpec = v; }
    public MechanicalRequirementAnalysis getRequirementAnalysis() { return requirementAnalysis; } public void setRequirementAnalysis(MechanicalRequirementAnalysis v) { requirementAnalysis = v; }
    public FunctionalRequirement getRequirement() { return requirement; } public void setRequirement(FunctionalRequirement v) { requirement = v; }
    public MechanicalConcept getConcept() { return concept; } public void setConcept(MechanicalConcept v) { concept = v; }
    public List<EngineeringParameter> getParameters() { return parameters; } public void setParameters(List<EngineeringParameter> v) { parameters = v; }
    public AssemblySpec getAssembly() { return assembly; } public void setAssembly(AssemblySpec v) { assembly = v; }
    public List<CADModelSpec> getParts() { return parts; } public void setParts(List<CADModelSpec> v) { parts = v; }
    public DrawingSpec getDrawings() { return drawings; } public void setDrawings(DrawingSpec v) { drawings = v; }
    public AnalysisSpec getAnalysis() { return analysis; } public void setAnalysis(AnalysisSpec v) { analysis = v; }
    public MechanicalAnalysisReport getAnalysisReport() { return analysisReport; } public void setAnalysisReport(MechanicalAnalysisReport v) { analysisReport = v; }
    public List<StageState> getStages() { return stages; } public void setStages(List<StageState> v) { stages = v; }
    public List<Artifact> getArtifacts() { return artifacts; } public void setArtifacts(List<Artifact> v) { artifacts = v; }

    public static class FunctionalRequirement {
        private List<String> functions = new ArrayList<>();
        private List<String> constraints = new ArrayList<>();
        public List<String> getFunctions() { return functions; } public void setFunctions(List<String> v) { functions = v; }
        public List<String> getConstraints() { return constraints; } public void setConstraints(List<String> v) { constraints = v; }
    }
    public static class MechanicalConcept {
        private List<ConceptOption> alternatives = new ArrayList<>();
        private String selectedConcept = "";
        private String selectionReason = "";
        private List<String> modules = new ArrayList<>();
        public List<ConceptOption> getAlternatives() { return alternatives; } public void setAlternatives(List<ConceptOption> v) { alternatives = v; }
        public String getSelectedConcept() { return selectedConcept; } public void setSelectedConcept(String v) { selectedConcept = v; }
        public String getSelectionReason() { return selectionReason; } public void setSelectionReason(String v) { selectionReason = v; }
        public List<String> getModules() { return modules; } public void setModules(List<String> v) { modules = v; }
    }
    public record ConceptOption(String name, String advantages, String limitations, double score) {}
    public record EngineeringParameter(String name, double value, String unit, String reason) {}
    public static class AssemblySpec {
        private String root = "Assembly";
        private List<AssemblyComponent> components = new ArrayList<>();
        private List<Constraint> constraints = new ArrayList<>();
        public String getRoot() { return root; } public void setRoot(String v) { root = v; }
        public List<AssemblyComponent> getComponents() { return components; } public void setComponents(List<AssemblyComponent> v) { components = v; }
        public List<Constraint> getConstraints() { return constraints; } public void setConstraints(List<Constraint> v) { constraints = v; }
    }
    public record AssemblyComponent(String partNumber, String name, String parent, Pose position, Pose orientation) {}
    public record Pose(double x, double y, double z) {}
    public record Constraint(String type, String componentA, String referenceA, String componentB, String referenceB) {}
    public record CADModelSpec(String partNumber, String name, String purpose, String material, String manufacturing,
                               List<CADFeature> features) {}
    public record CADFeature(int order, String type, String intent, Map<String, Object> parameters) {}
    public static class DrawingSpec {
        private List<String> views = List.of("front", "top", "right", "section", "isometric");
        private String standard = "GB/T 14689 + ISO 128";
        private List<String> outputs = List.of("SVG", "DXF", "PDF");
        public List<String> getViews() { return views; } public void setViews(List<String> v) { views = v; }
        public String getStandard() { return standard; } public void setStandard(String v) { standard = v; }
        public List<String> getOutputs() { return outputs; } public void setOutputs(List<String> v) { outputs = v; }
    }
    public static class AnalysisSpec {
        private String method = "RULE_BASED_PHASE_1";
        private double maximumStressMpa;
        private double displacementMm;
        private double safetyFactor;
        private String conclusion = "";
        public String getMethod() { return method; } public void setMethod(String v) { method = v; }
        public double getMaximumStressMpa() { return maximumStressMpa; } public void setMaximumStressMpa(double v) { maximumStressMpa = v; }
        public double getDisplacementMm() { return displacementMm; } public void setDisplacementMm(double v) { displacementMm = v; }
        public double getSafetyFactor() { return safetyFactor; } public void setSafetyFactor(double v) { safetyFactor = v; }
        public String getConclusion() { return conclusion; } public void setConclusion(String v) { conclusion = v; }
    }
    public record StageState(String stage, String status, String message) {}
    public record Artifact(String name, String category, String mediaType, long size, String downloadUrl, boolean validated) {}
}
