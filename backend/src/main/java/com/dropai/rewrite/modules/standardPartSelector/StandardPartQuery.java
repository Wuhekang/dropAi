package com.dropai.rewrite.modules.standardPartSelector;

import java.util.LinkedHashMap;
import java.util.Map;

public class StandardPartQuery {
    private String category = "";
    private String name = "";
    private Map<String, Object> requirements = new LinkedHashMap<>();

    public StandardPartQuery() {}
    public StandardPartQuery(String category, String name, Map<String, Object> requirements) {
        this.category = category;
        this.name = name;
        this.requirements = requirements == null ? new LinkedHashMap<>() : new LinkedHashMap<>(requirements);
    }

    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public Map<String, Object> getRequirements() { return requirements; }
    public void setRequirements(Map<String, Object> requirements) { this.requirements = requirements == null ? new LinkedHashMap<>() : new LinkedHashMap<>(requirements); }
}
