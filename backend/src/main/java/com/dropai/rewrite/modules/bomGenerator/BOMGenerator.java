package com.dropai.rewrite.modules.bomGenerator;

import com.dropai.rewrite.modules.model.DesignProject;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class BOMGenerator {
    public DesignProject generate(DesignProject project) {
        Map<String, DesignProject.BomItem> merged = new LinkedHashMap<>();
        for (DesignProject.Component component : project.getComponents()) {
            String key = String.join("|",
                    safe(component.getName()),
                    safe(component.getMaterial()),
                    safe(component.getFunction()));
            DesignProject.BomItem existing = merged.get(key);
            if (existing == null) {
                merged.put(key, new DesignProject.BomItem(merged.size() + 1, component.getName(),
                        component.getMaterial(), Math.max(1, component.getQuantity()), component.getFunction()));
            } else {
                existing.setQuantity(existing.getQuantity() + Math.max(1, component.getQuantity()));
            }
        }
        project.setBom(merged.values().stream().toList());
        project.getTechnicalRequirements().removeIf(item -> item != null && item.contains("BOM"));
        project.getTechnicalRequirements().add("BOM must map to assembly drawing, part drawings, and paper structure figures.");
        project.getTechnicalRequirements().add("BOM rows are merged by same name/material/function; drawing item numbers must match BOM sequence.");
        return project;
    }

    private String safe(String value) {
        return value == null ? "" : value.trim();
    }
}
