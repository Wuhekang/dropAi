package com.dropai.rewrite.service.ai;

import java.util.ArrayList;
import java.util.List;

public class MechanicalVisionAnalysisResult {
    private String imageType = "unknown";
    private String equipmentName = "";
    private List<Component> components = new ArrayList<>();
    private Views views = new Views();
    private List<Dimension> dimensions = new ArrayList<>();
    private List<AssemblyRelation> assemblyRelations = new ArrayList<>();
    private List<String> detectedAnnotations = new ArrayList<>();
    private List<String> missingComponents = new ArrayList<>();
    private List<String> drawingProblems = new ArrayList<>();
    private List<String> assemblyProblems = new ArrayList<>();
    private int qualityScore;
    private List<String> evidence = new ArrayList<>();
    private List<String> warnings = new ArrayList<>();

    public String getImageType() { return imageType; }
    public void setImageType(String imageType) { this.imageType = safe(imageType, "unknown"); }
    public String getEquipmentName() { return equipmentName; }
    public void setEquipmentName(String equipmentName) { this.equipmentName = safe(equipmentName, ""); }
    public List<Component> getComponents() { return components; }
    public void setComponents(List<Component> components) { this.components = list(components); }
    public Views getViews() { return views; }
    public void setViews(Views views) { this.views = views == null ? new Views() : views; }
    public List<Dimension> getDimensions() { return dimensions; }
    public void setDimensions(List<Dimension> dimensions) { this.dimensions = list(dimensions); }
    public List<AssemblyRelation> getAssemblyRelations() { return assemblyRelations; }
    public void setAssemblyRelations(List<AssemblyRelation> assemblyRelations) { this.assemblyRelations = list(assemblyRelations); }
    public List<String> getDetectedAnnotations() { return detectedAnnotations; }
    public void setDetectedAnnotations(List<String> detectedAnnotations) { this.detectedAnnotations = list(detectedAnnotations); }
    public List<String> getMissingComponents() { return missingComponents; }
    public void setMissingComponents(List<String> missingComponents) { this.missingComponents = list(missingComponents); }
    public List<String> getDrawingProblems() { return drawingProblems; }
    public void setDrawingProblems(List<String> drawingProblems) { this.drawingProblems = list(drawingProblems); }
    public List<String> getAssemblyProblems() { return assemblyProblems; }
    public void setAssemblyProblems(List<String> assemblyProblems) { this.assemblyProblems = list(assemblyProblems); }
    public int getQualityScore() { return qualityScore; }
    public void setQualityScore(int qualityScore) { this.qualityScore = Math.max(0, Math.min(100, qualityScore)); }
    public List<String> getEvidence() { return evidence; }
    public void setEvidence(List<String> evidence) { this.evidence = list(evidence); }
    public List<String> getWarnings() { return warnings; }
    public void setWarnings(List<String> warnings) { this.warnings = list(warnings); }

    public void applyDefaults() {
        setImageType(imageType);
        setEquipmentName(equipmentName);
        setComponents(components);
        setViews(views);
        setDimensions(dimensions);
        setAssemblyRelations(assemblyRelations);
        setDetectedAnnotations(detectedAnnotations);
        setMissingComponents(missingComponents);
        setDrawingProblems(drawingProblems);
        setAssemblyProblems(assemblyProblems);
        setEvidence(evidence);
        setWarnings(warnings);
    }

    private static String safe(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private static <T> List<T> list(List<T> value) {
        return value == null ? new ArrayList<>() : new ArrayList<>(value);
    }

    public static class Component {
        private String name = "";
        private String category = "";
        private int quantity = 1;
        private String position = "";
        private String shape = "";
        private double confidence;

        public String getName() { return name; }
        public void setName(String name) { this.name = safe(name, ""); }
        public String getCategory() { return category; }
        public void setCategory(String category) { this.category = safe(category, ""); }
        public int getQuantity() { return quantity; }
        public void setQuantity(int quantity) { this.quantity = Math.max(1, quantity); }
        public String getPosition() { return position; }
        public void setPosition(String position) { this.position = safe(position, ""); }
        public String getShape() { return shape; }
        public void setShape(String shape) { this.shape = safe(shape, ""); }
        public double getConfidence() { return confidence; }
        public void setConfidence(double confidence) { this.confidence = Math.max(0, Math.min(1, confidence)); }
    }

    public static class Views {
        private boolean front;
        private boolean top;
        private boolean side;
        private boolean isometric;
        private boolean section;

        public boolean isFront() { return front; }
        public void setFront(boolean front) { this.front = front; }
        public boolean isTop() { return top; }
        public void setTop(boolean top) { this.top = top; }
        public boolean isSide() { return side; }
        public void setSide(boolean side) { this.side = side; }
        public boolean isIsometric() { return isometric; }
        public void setIsometric(boolean isometric) { this.isometric = isometric; }
        public boolean isSection() { return section; }
        public void setSection(boolean section) { this.section = section; }
    }

    public static class Dimension {
        private String name = "";
        private String value = "";
        private String unit = "";
        private double confidence;

        public String getName() { return name; }
        public void setName(String name) { this.name = safe(name, ""); }
        public String getValue() { return value; }
        public void setValue(String value) { this.value = safe(value, ""); }
        public String getUnit() { return unit; }
        public void setUnit(String unit) { this.unit = safe(unit, ""); }
        public double getConfidence() { return confidence; }
        public void setConfidence(double confidence) { this.confidence = Math.max(0, Math.min(1, confidence)); }
    }

    public static class AssemblyRelation {
        private String source = "";
        private String target = "";
        private String relation = "";
        private double confidence;

        public String getSource() { return source; }
        public void setSource(String source) { this.source = safe(source, ""); }
        public String getTarget() { return target; }
        public void setTarget(String target) { this.target = safe(target, ""); }
        public String getRelation() { return relation; }
        public void setRelation(String relation) { this.relation = safe(relation, ""); }
        public double getConfidence() { return confidence; }
        public void setConfidence(double confidence) { this.confidence = Math.max(0, Math.min(1, confidence)); }
    }
}
