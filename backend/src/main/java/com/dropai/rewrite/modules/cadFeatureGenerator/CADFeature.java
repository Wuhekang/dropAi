package com.dropai.rewrite.modules.cadFeatureGenerator;

import java.util.LinkedHashMap;
import java.util.Map;

public class CADFeature {
    private String type = "";
    private Map<String, Object> parameters = new LinkedHashMap<>();
    private String source = "";

    public CADFeature() {}

    public CADFeature(String type, Map<String, Object> parameters, String source) {
        this.type = type == null ? "" : type;
        this.parameters = parameters == null ? new LinkedHashMap<>() : new LinkedHashMap<>(parameters);
        this.source = source == null ? "" : source;
    }

    public String getType() { return type; }
    public void setType(String type) { this.type = type == null ? "" : type; }
    public Map<String, Object> getParameters() { return parameters; }
    public void setParameters(Map<String, Object> parameters) {
        this.parameters = parameters == null ? new LinkedHashMap<>() : new LinkedHashMap<>(parameters);
    }
    public String getSource() { return source; }
    public void setSource(String source) { this.source = source == null ? "" : source; }
}
