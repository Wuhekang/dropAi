package com.dropai.rewrite.modules.model;

import java.util.ArrayList;
import java.util.List;

public class DesignProject {
    private String projectTitle = "";
    private String equipmentName = "";
    private String designType = "";
    private String projectCategory = "";
    private String workingPrinciple = "";
    private String designDepth = "graduation";
    private int partCount;
    private int featureCount;
    private int detailScore;
    private List<String> enhancementNotes = new ArrayList<>();
    private List<String> mainFunctions = new ArrayList<>();
    private List<String> mainStructures = new ArrayList<>();
    private List<Component> components = new ArrayList<>();
    private List<BomItem> bom = new ArrayList<>();
    private List<DimensionChain> dimensionChains = new ArrayList<>();
    private List<String> technicalRequirements = new ArrayList<>();
    private List<Parameter> explicitParameters = new ArrayList<>();
    private List<Parameter> derivedParameters = new ArrayList<>();
    private List<Parameter> suggestedParameters = new ArrayList<>();
    private List<String> verificationItems = new ArrayList<>();
    private List<Calculation> calculations = new ArrayList<>();

    public String getProjectTitle() { return projectTitle; }
    public void setProjectTitle(String projectTitle) { this.projectTitle = projectTitle; }
    public String getEquipmentName() { return equipmentName; }
    public void setEquipmentName(String equipmentName) { this.equipmentName = equipmentName; }
    public String getDesignType() { return designType; }
    public void setDesignType(String designType) { this.designType = designType; }
    public String getProjectCategory() { return projectCategory; }
    public void setProjectCategory(String projectCategory) { this.projectCategory = projectCategory; }
    public String getWorkingPrinciple() { return workingPrinciple; }
    public void setWorkingPrinciple(String workingPrinciple) { this.workingPrinciple = workingPrinciple; }
    public String getDesignDepth() { return designDepth; }
    public void setDesignDepth(String designDepth) { this.designDepth = designDepth; }
    public int getPartCount() { return partCount; }
    public void setPartCount(int partCount) { this.partCount = partCount; }
    public int getFeatureCount() { return featureCount; }
    public void setFeatureCount(int featureCount) { this.featureCount = featureCount; }
    public int getDetailScore() { return detailScore; }
    public void setDetailScore(int detailScore) { this.detailScore = detailScore; }
    public List<String> getEnhancementNotes() { return enhancementNotes; }
    public void setEnhancementNotes(List<String> value) { enhancementNotes = safe(value); }
    public List<String> getMainFunctions() { return mainFunctions; }
    public void setMainFunctions(List<String> value) { mainFunctions = safe(value); }
    public List<String> getMainStructures() { return mainStructures; }
    public void setMainStructures(List<String> value) { mainStructures = safe(value); }
    public List<Component> getComponents() { return components; }
    public void setComponents(List<Component> value) { components = safe(value); }
    public List<BomItem> getBom() { return bom; }
    public void setBom(List<BomItem> value) { bom = safe(value); }
    public List<DimensionChain> getDimensionChains() { return dimensionChains; }
    public void setDimensionChains(List<DimensionChain> value) { dimensionChains = safe(value); }
    public List<String> getTechnicalRequirements() { return technicalRequirements; }
    public void setTechnicalRequirements(List<String> value) { technicalRequirements = safe(value); }
    public List<Parameter> getExplicitParameters() { return explicitParameters; }
    public void setExplicitParameters(List<Parameter> value) { explicitParameters = safe(value); }
    public List<Parameter> getDerivedParameters() { return derivedParameters; }
    public void setDerivedParameters(List<Parameter> value) { derivedParameters = safe(value); }
    public List<Parameter> getSuggestedParameters() { return suggestedParameters; }
    public void setSuggestedParameters(List<Parameter> value) { suggestedParameters = safe(value); }
    public List<String> getVerificationItems() { return verificationItems; }
    public void setVerificationItems(List<String> value) { verificationItems = safe(value); }
    public List<Calculation> getCalculations() { return calculations; }
    public void setCalculations(List<Calculation> value) { calculations = safe(value); }

    public List<Parameter> allParameters() {
        List<Parameter> result = new ArrayList<>();
        result.addAll(explicitParameters);
        result.addAll(derivedParameters);
        result.addAll(suggestedParameters);
        return result;
    }

    public double number(String name, double fallback) {
        return allParameters().stream().filter(item -> name.equals(item.getName()))
                .map(Parameter::getValue).filter(Number.class::isInstance).map(Number.class::cast)
                .mapToDouble(Number::doubleValue).findFirst().orElse(fallback);
    }

    private static <T> List<T> safe(List<T> value) { return value == null ? new ArrayList<>() : new ArrayList<>(value); }

    public static class Parameter {
        private String name;
        private Object value;
        private String unit = "";
        private String source;
        private String basis;

        public Parameter() {}
        public Parameter(String name, Object value, String unit, String source, String basis) {
            this.name = name; this.value = value; this.unit = unit; this.source = source; this.basis = basis;
        }
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public Object getValue() { return value; }
        public void setValue(Object value) { this.value = value; }
        public String getUnit() { return unit; }
        public void setUnit(String unit) { this.unit = unit; }
        public String getSource() { return source; }
        public void setSource(String source) { this.source = source; }
        public String getBasis() { return basis; }
        public void setBasis(String basis) { this.basis = basis; }
    }

    public static class Calculation {
        private String name;
        private String formula;
        private String substitution;
        private double result;
        private String unit;
        private String conclusion;

        public Calculation() {}
        public Calculation(String name, String formula, String substitution, double result, String unit, String conclusion) {
            this.name = name; this.formula = formula; this.substitution = substitution;
            this.result = result; this.unit = unit; this.conclusion = conclusion;
        }
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public String getFormula() { return formula; }
        public void setFormula(String formula) { this.formula = formula; }
        public String getSubstitution() { return substitution; }
        public void setSubstitution(String substitution) { this.substitution = substitution; }
        public double getResult() { return result; }
        public void setResult(double result) { this.result = result; }
        public String getUnit() { return unit; }
        public void setUnit(String unit) { this.unit = unit; }
        public String getConclusion() { return conclusion; }
        public void setConclusion(String conclusion) { this.conclusion = conclusion; }
    }

    public static class Component {
        private int sequence;
        private String role;
        private String name;
        private String function;
        private String material;
        private String geometry = "BOX";
        private int quantity;
        private double x;
        private double y;
        private double z;
        private double length;
        private double width;
        private double height;
        private boolean keyPart;

        public Component() {}
        public Component(int sequence, String role, String name, String function, String material, int quantity,
                         double x, double y, double z, double length, double width, double height, boolean keyPart) {
            this.sequence = sequence; this.role = role; this.name = name; this.function = function; this.material = material;
            this.quantity = quantity; this.x = x; this.y = y; this.z = z; this.length = length; this.width = width; this.height = height; this.keyPart = keyPart;
        }
        public int getSequence() { return sequence; } public void setSequence(int v) { sequence = v; }
        public String getRole() { return role; } public void setRole(String v) { role = v; }
        public String getName() { return name; } public void setName(String v) { name = v; }
        public String getFunction() { return function; } public void setFunction(String v) { function = v; }
        public String getMaterial() { return material; } public void setMaterial(String v) { material = v; }
        public String getGeometry() { return geometry; } public void setGeometry(String v) { geometry = v; }
        public int getQuantity() { return quantity; } public void setQuantity(int v) { quantity = v; }
        public double getX() { return x; } public void setX(double v) { x = v; }
        public double getY() { return y; } public void setY(double v) { y = v; }
        public double getZ() { return z; } public void setZ(double v) { z = v; }
        public double getLength() { return length; } public void setLength(double v) { length = v; }
        public double getWidth() { return width; } public void setWidth(double v) { width = v; }
        public double getHeight() { return height; } public void setHeight(double v) { height = v; }
        public boolean isKeyPart() { return keyPart; } public void setKeyPart(boolean v) { keyPart = v; }
    }

    public static class BomItem {
        private int sequence;
        private String name;
        private String material;
        private int quantity;
        private String remark;
        public BomItem() {}
        public BomItem(int sequence, String name, String material, int quantity, String remark) {
            this.sequence = sequence; this.name = name; this.material = material; this.quantity = quantity; this.remark = remark;
        }
        public int getSequence() { return sequence; } public void setSequence(int v) { sequence = v; }
        public String getName() { return name; } public void setName(String v) { name = v; }
        public String getMaterial() { return material; } public void setMaterial(String v) { material = v; }
        public int getQuantity() { return quantity; } public void setQuantity(int v) { quantity = v; }
        public String getRemark() { return remark; } public void setRemark(String v) { remark = v; }
    }

    public static class DimensionChain {
        private String name;
        private double value;
        private String unit;
        private String relatedComponent;
        public DimensionChain() {}
        public DimensionChain(String name, double value, String unit, String relatedComponent) {
            this.name = name; this.value = value; this.unit = unit; this.relatedComponent = relatedComponent;
        }
        public String getName() { return name; } public void setName(String v) { name = v; }
        public double getValue() { return value; } public void setValue(double v) { value = v; }
        public String getUnit() { return unit; } public void setUnit(String v) { unit = v; }
        public String getRelatedComponent() { return relatedComponent; } public void setRelatedComponent(String v) { relatedComponent = v; }
    }
}
