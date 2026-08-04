package com.dropai.rewrite.mechanicalengine.domain;

import java.util.ArrayList;
import java.util.List;

public class MechanicalProject {
    private String projectId = "";
    private String productName = "";
    private String scenario = "";
    private String status = "PENDING";
    private String currentStage = "REQUIREMENT_UNDERSTANDING";
    private String failureCode = "";
    private String failureMessage = "";
    private FunctionalRequirement requirement = new FunctionalRequirement();
    private MechanicalConcept concept = new MechanicalConcept();
    private List<EngineeringParameter> parameters = new ArrayList<>();
    private AssemblySpecification assembly = new AssemblySpecification();
    private List<CADSpecification> parts = new ArrayList<>();
    private DrawingSpecification drawings = new DrawingSpecification();
    private List<StageState> stages = new ArrayList<>();
    private List<Artifact> artifacts = new ArrayList<>();

    public String getProjectId() { return projectId; } public void setProjectId(String v) { projectId = v; }
    public String getProductName() { return productName; } public void setProductName(String v) { productName = v; }
    public String getScenario() { return scenario; } public void setScenario(String v) { scenario = v; }
    public String getStatus() { return status; } public void setStatus(String v) { status = v; }
    public String getCurrentStage() { return currentStage; } public void setCurrentStage(String v) { currentStage = v; }
    public String getFailureCode() { return failureCode; } public void setFailureCode(String v) { failureCode = v; }
    public String getFailureMessage() { return failureMessage; } public void setFailureMessage(String v) { failureMessage = v; }
    public FunctionalRequirement getRequirement() { return requirement; } public void setRequirement(FunctionalRequirement v) { requirement = v; }
    public MechanicalConcept getConcept() { return concept; } public void setConcept(MechanicalConcept v) { concept = v; }
    public List<EngineeringParameter> getParameters() { return parameters; } public void setParameters(List<EngineeringParameter> v) { parameters = v; }
    public AssemblySpecification getAssembly() { return assembly; } public void setAssembly(AssemblySpecification v) { assembly = v; }
    public List<CADSpecification> getParts() { return parts; } public void setParts(List<CADSpecification> v) { parts = v; }
    public DrawingSpecification getDrawings() { return drawings; } public void setDrawings(DrawingSpecification v) { drawings = v; }
    public List<StageState> getStages() { return stages; } public void setStages(List<StageState> v) { stages = v; }
    public List<Artifact> getArtifacts() { return artifacts; } public void setArtifacts(List<Artifact> v) { artifacts = v; }

    public static class FunctionalRequirement {
        private List<String> functions = new ArrayList<>();
        private List<String> constraints = new ArrayList<>();
        public List<String> getFunctions() { return functions; } public void setFunctions(List<String> v) { functions = v; }
        public List<String> getConstraints() { return constraints; } public void setConstraints(List<String> v) { constraints = v; }
    }
    public static class MechanicalConcept {
        private String selectedConcept = "";
        private String selectionReason = "";
        private List<String> modules = new ArrayList<>();
        public String getSelectedConcept() { return selectedConcept; } public void setSelectedConcept(String v) { selectedConcept = v; }
        public String getSelectionReason() { return selectionReason; } public void setSelectionReason(String v) { selectionReason = v; }
        public List<String> getModules() { return modules; } public void setModules(List<String> v) { modules = v; }
    }
    public record EngineeringParameter(String name, double value, String unit, String reason) {}
    public static class AssemblySpecification {
        private String root = "Assembly";
        private List<AssemblyComponent> components = new ArrayList<>();
        private List<Mate> mates = new ArrayList<>();
        public String getRoot() { return root; } public void setRoot(String v) { root = v; }
        public List<AssemblyComponent> getComponents() { return components; } public void setComponents(List<AssemblyComponent> v) { components = v; }
        public List<Mate> getMates() { return mates; } public void setMates(List<Mate> v) { mates = v; }
    }
    public record AssemblyComponent(String id, String name, String parent, Pose position, Pose orientation) {}
    public record Pose(double x, double y, double z) {}
    public record Mate(String type, String componentA, String referenceA, String componentB, String referenceB) {}
    public record CADSpecification(String partNumber, String name, String material, List<Feature> featureTree) {}
    public record Feature(int order, String type, String plane, String parameters) {}
    public static class DrawingSpecification {
        private List<String> assemblyViews = List.of("Front", "Top", "Right", "Isometric");
        private List<String> partDrawings = new ArrayList<>();
        private String standard = "GB/T + ISO";
        public List<String> getAssemblyViews() { return assemblyViews; } public void setAssemblyViews(List<String> v) { assemblyViews = v; }
        public List<String> getPartDrawings() { return partDrawings; } public void setPartDrawings(List<String> v) { partDrawings = v; }
        public String getStandard() { return standard; } public void setStandard(String v) { standard = v; }
    }
    public record StageState(String stage, String status, String message) {}
    public record Artifact(String name, String type, long size, String downloadUrl, boolean validated) {}
}
