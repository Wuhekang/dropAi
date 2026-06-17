package com.dropai.rewrite.modules.standardPartSelector;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class StandardPartResult {
    private String partId = "";
    private String partType = "standard";
    private String category = "";
    private String name = "";
    private String model = "";
    private String brand = "";
    private String source = "";
    private String sourceUrl = "";
    private Map<String, Object> dimensions = new LinkedHashMap<>();
    private List<String> availableFormats = new ArrayList<>();
    private double confidence;
    private String reason = "";

    public String getPartId() { return partId; }
    public void setPartId(String partId) { this.partId = partId; }
    public String getPartType() { return partType; }
    public void setPartType(String partType) { this.partType = partType; }
    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getModel() { return model; }
    public void setModel(String model) { this.model = model; }
    public String getBrand() { return brand; }
    public void setBrand(String brand) { this.brand = brand; }
    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }
    public String getSourceUrl() { return sourceUrl; }
    public void setSourceUrl(String sourceUrl) { this.sourceUrl = sourceUrl; }
    public Map<String, Object> getDimensions() { return dimensions; }
    public void setDimensions(Map<String, Object> dimensions) { this.dimensions = dimensions == null ? new LinkedHashMap<>() : new LinkedHashMap<>(dimensions); }
    public List<String> getAvailableFormats() { return availableFormats; }
    public void setAvailableFormats(List<String> availableFormats) { this.availableFormats = availableFormats == null ? new ArrayList<>() : new ArrayList<>(availableFormats); }
    public double getConfidence() { return confidence; }
    public void setConfidence(double confidence) { this.confidence = confidence; }
    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
}
