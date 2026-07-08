package com.dropai.rewrite.modules.mechanicalDesignPlanner;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class MechanicalDesignPlan {
    private String projectName = "";
    private String designPurpose = "";
    private String workingPrinciple = "";
    private String mechanismType = "";
    private List<SubsystemPlan> subsystems = new ArrayList<>();
    private Map<String, Object> designParameters = new LinkedHashMap<>();
    private Map<String, String> materialSelection = new LinkedHashMap<>();
    private String calculationBasis = "";
    private double confidence;
    private List<String> completedRequirements = new ArrayList<>();
    private List<String> planningNotes = new ArrayList<>();

    public String getProjectName() { return projectName; }
    public void setProjectName(String projectName) { this.projectName = projectName == null ? "" : projectName; }
    public String getDesignPurpose() { return designPurpose; }
    public void setDesignPurpose(String designPurpose) { this.designPurpose = designPurpose == null ? "" : designPurpose; }
    public String getWorkingPrinciple() { return workingPrinciple; }
    public void setWorkingPrinciple(String workingPrinciple) { this.workingPrinciple = workingPrinciple == null ? "" : workingPrinciple; }
    public String getMechanismType() { return mechanismType; }
    public void setMechanismType(String mechanismType) { this.mechanismType = mechanismType == null ? "" : mechanismType; }
    public List<SubsystemPlan> getSubsystems() { return subsystems; }
    public void setSubsystems(List<SubsystemPlan> subsystems) { this.subsystems = safe(subsystems); }
    public Map<String, Object> getDesignParameters() { return designParameters; }
    public void setDesignParameters(Map<String, Object> designParameters) {
        this.designParameters = designParameters == null ? new LinkedHashMap<>() : new LinkedHashMap<>(designParameters);
    }
    public Map<String, String> getMaterialSelection() { return materialSelection; }
    public void setMaterialSelection(Map<String, String> materialSelection) {
        this.materialSelection = materialSelection == null ? new LinkedHashMap<>() : new LinkedHashMap<>(materialSelection);
    }
    public String getCalculationBasis() { return calculationBasis; }
    public void setCalculationBasis(String calculationBasis) { this.calculationBasis = calculationBasis == null ? "" : calculationBasis; }
    public double getConfidence() { return confidence; }
    public void setConfidence(double confidence) { this.confidence = confidence; }
    public List<String> getCompletedRequirements() { return completedRequirements; }
    public void setCompletedRequirements(List<String> completedRequirements) { this.completedRequirements = safe(completedRequirements); }
    public List<String> getPlanningNotes() { return planningNotes; }
    public void setPlanningNotes(List<String> planningNotes) { this.planningNotes = safe(planningNotes); }

    private static <T> List<T> safe(List<T> value) {
        return value == null ? new ArrayList<>() : new ArrayList<>(value);
    }

    public static class SubsystemPlan {
        private String name = "";
        private String function = "";
        private List<String> components = new ArrayList<>();
        private Map<String, Object> parameters = new LinkedHashMap<>();
        private String material = "";
        private String source = "";
        private double confidence;
        private boolean required = true;

        public SubsystemPlan() {}
        public SubsystemPlan(String name, String function, List<String> components, String material, String source, double confidence) {
            this.name = name == null ? "" : name;
            this.function = function == null ? "" : function;
            this.components = safe(components);
            this.material = material == null ? "" : material;
            this.source = source == null ? "" : source;
            this.confidence = confidence;
        }

        public String getName() { return name; }
        public void setName(String name) { this.name = name == null ? "" : name; }
        public String getFunction() { return function; }
        public void setFunction(String function) { this.function = function == null ? "" : function; }
        public List<String> getComponents() { return components; }
        public void setComponents(List<String> components) { this.components = safe(components); }
        public Map<String, Object> getParameters() { return parameters; }
        public void setParameters(Map<String, Object> parameters) {
            this.parameters = parameters == null ? new LinkedHashMap<>() : new LinkedHashMap<>(parameters);
        }
        public String getMaterial() { return material; }
        public void setMaterial(String material) { this.material = material == null ? "" : material; }
        public String getSource() { return source; }
        public void setSource(String source) { this.source = source == null ? "" : source; }
        public double getConfidence() { return confidence; }
        public void setConfidence(double confidence) { this.confidence = confidence; }
        public boolean isRequired() { return required; }
        public void setRequired(boolean required) { this.required = required; }
    }
}
