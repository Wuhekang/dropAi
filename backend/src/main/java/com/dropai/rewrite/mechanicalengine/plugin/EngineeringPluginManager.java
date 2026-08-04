package com.dropai.rewrite.mechanicalengine.plugin;

import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class EngineeringPluginManager {
    public Map<String, String> registry() {
        Map<String, String> tools = new LinkedHashMap<>();
        tools.put("SOLIDWORKS_API", available("SOLIDWORKS_AUTOMATION_COMMAND"));
        tools.put("FREECAD_STEP_PREVIEW", available("FREECAD_VALIDATION_COMMAND"));
        tools.put("DWG_EXPORT", "provided-by-solidworks-worker");
        tools.put("STEP_EXPORT", "provided-by-solidworks-worker");
        return tools;
    }

    private String available(String variable) {
        String value = System.getenv(variable);
        return value == null || value.isBlank() ? "missing" : "registered";
    }
}
