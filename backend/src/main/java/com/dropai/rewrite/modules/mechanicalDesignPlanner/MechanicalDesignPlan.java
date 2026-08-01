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
    private EngineeringDecisionLog engineeringDecisionLog = new EngineeringDecisionLog();
    private ForceReport forceReport = new ForceReport();
    private ManufacturingPlan manufacturingPlan = new ManufacturingPlan();
    private DesignReviewReport designReviewReport = new DesignReviewReport();

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
    public EngineeringDecisionLog getEngineeringDecisionLog() { return engineeringDecisionLog; }
    public void setEngineeringDecisionLog(EngineeringDecisionLog value) { engineeringDecisionLog = value == null ? new EngineeringDecisionLog() : value; }
    public ForceReport getForceReport() { return forceReport; }
    public void setForceReport(ForceReport value) { forceReport = value == null ? new ForceReport() : value; }
    public ManufacturingPlan getManufacturingPlan() { return manufacturingPlan; }
    public void setManufacturingPlan(ManufacturingPlan value) { manufacturingPlan = value == null ? new ManufacturingPlan() : value; }
    public DesignReviewReport getDesignReviewReport() { return designReviewReport; }
    public void setDesignReviewReport(DesignReviewReport value) { designReviewReport = value == null ? new DesignReviewReport() : value; }

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

    public static class EngineeringDecisionLog {
        private List<String> functionalRequirements = new ArrayList<>();
        private List<String> constraints = new ArrayList<>();
        private List<String> designObjectives = new ArrayList<>();
        private List<String> mechanismCandidates = new ArrayList<>();
        private String recommendedMechanism = "";
        private List<String> decisionReasons = new ArrayList<>();

        public List<String> getFunctionalRequirements() { return functionalRequirements; }
        public void setFunctionalRequirements(List<String> value) { functionalRequirements = safe(value); }
        public List<String> getConstraints() { return constraints; }
        public void setConstraints(List<String> value) { constraints = safe(value); }
        public List<String> getDesignObjectives() { return designObjectives; }
        public void setDesignObjectives(List<String> value) { designObjectives = safe(value); }
        public List<String> getMechanismCandidates() { return mechanismCandidates; }
        public void setMechanismCandidates(List<String> value) { mechanismCandidates = safe(value); }
        public String getRecommendedMechanism() { return recommendedMechanism; }
        public void setRecommendedMechanism(String value) { recommendedMechanism = value == null ? "" : value; }
        public List<String> getDecisionReasons() { return decisionReasons; }
        public void setDecisionReasons(List<String> value) { decisionReasons = safe(value); }
    }

    public static class ForceReport {
        private String loadModel = "";
        private List<String> forcePath = new ArrayList<>();
        private List<String> criticalParts = new ArrayList<>();
        private double estimatedLoadN;
        private double safetyFactor;

        public String getLoadModel() { return loadModel; }
        public void setLoadModel(String value) { loadModel = value == null ? "" : value; }
        public List<String> getForcePath() { return forcePath; }
        public void setForcePath(List<String> value) { forcePath = safe(value); }
        public List<String> getCriticalParts() { return criticalParts; }
        public void setCriticalParts(List<String> value) { criticalParts = safe(value); }
        public double getEstimatedLoadN() { return estimatedLoadN; }
        public void setEstimatedLoadN(double value) { estimatedLoadN = value; }
        public double getSafetyFactor() { return safetyFactor; }
        public void setSafetyFactor(double value) { safetyFactor = value; }
    }

    public static class ManufacturingPlan {
        private List<String> processes = new ArrayList<>();
        private List<String> materialDecisions = new ArrayList<>();
        private List<String> manufacturabilityNotes = new ArrayList<>();

        public List<String> getProcesses() { return processes; }
        public void setProcesses(List<String> value) { processes = safe(value); }
        public List<String> getMaterialDecisions() { return materialDecisions; }
        public void setMaterialDecisions(List<String> value) { materialDecisions = safe(value); }
        public List<String> getManufacturabilityNotes() { return manufacturabilityNotes; }
        public void setManufacturabilityNotes(List<String> value) { manufacturabilityNotes = safe(value); }
    }

    public static class DesignReviewReport {
        private boolean passed;
        private int score;
        private List<String> checks = new ArrayList<>();
        private List<String> risks = new ArrayList<>();
        private List<String> recommendations = new ArrayList<>();

        public boolean isPassed() { return passed; }
        public void setPassed(boolean value) { passed = value; }
        public int getScore() { return score; }
        public void setScore(int value) { score = value; }
        public List<String> getChecks() { return checks; }
        public void setChecks(List<String> value) { checks = safe(value); }
        public List<String> getRisks() { return risks; }
        public void setRisks(List<String> value) { risks = safe(value); }
        public List<String> getRecommendations() { return recommendations; }
        public void setRecommendations(List<String> value) { recommendations = safe(value); }
    }
}
