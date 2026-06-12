package com.dropai.rewrite.modules.model;

import java.util.ArrayList;
import java.util.List;

public class DesignProject {
    private String projectTitle = "通用机械类毕业设计";
    private String equipmentName = "机械设备";
    private String designType = "通用机械结构设计";
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
}
