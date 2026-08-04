package com.dropai.rewrite.mechanicalengine.plugin;

import org.springframework.stereotype.Service;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class EngineeringPluginManager {
    public Map<String,String> registry() {
        Map<String,String> tools = new LinkedHashMap<>();
        tools.put("OPENCASCADE_BREP", env("FREECAD_CMD"));
        tools.put("FREECAD_STEP_EXPORT", env("FREECAD_CMD"));
        tools.put("BROWSER_STL_VIEWER", "built-in");
        tools.put("ENGINEERING_DRAWING", "built-in");
        tools.put("RULE_ANALYSIS", "built-in");
        tools.put("CALCULIX_FEA", env("CALCULIX_CMD"));
        return tools;
    }
    private String env(String name) { String value=System.getenv(name); return value==null||value.isBlank()?"missing":"registered"; }
}
