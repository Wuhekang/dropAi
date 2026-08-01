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
            String key = String.join("|", canonicalName(component.getName()), safe(component.getMaterial()));
            DesignProject.BomItem existing = merged.get(key);
            if (existing == null) {
                merged.put(key, new DesignProject.BomItem(merged.size() + 1, canonicalName(component.getName()),
                        component.getMaterial(), Math.max(1, component.getQuantity()), component.getFunction()));
            } else {
                existing.setQuantity(existing.getQuantity() + Math.max(1, component.getQuantity()));
            }
        }
        project.setBom(merged.values().stream().toList());
        project.getTechnicalRequirements().removeIf(item -> item != null && item.contains("BOM"));
        project.getTechnicalRequirements().add("BOM must map to assembly drawing, part drawings, and paper structure figures.");
        project.getTechnicalRequirements().add("BOM rows are merged by semantic part name and material; drawing item numbers must match BOM sequence.");
        return project;
    }

    private String safe(String value) {
        return value == null ? "" : value.trim();
    }

    private String canonicalName(String value) {
        String name = safe(value).replaceAll("(主体板主体板|安装板安装板|加强筋加强筋)", "$1");
        String[] suffixes = {"主体板", "安装板", "加强筋", "侧板", "底板", "盖板"};
        for (String suffix : suffixes) {
            int first = name.indexOf(suffix);
            int second = first < 0 ? -1 : name.indexOf(suffix, first + suffix.length());
            if (second >= 0) name = name.substring(0, second) + name.substring(second + suffix.length());
        }
        return name;
    }
}
