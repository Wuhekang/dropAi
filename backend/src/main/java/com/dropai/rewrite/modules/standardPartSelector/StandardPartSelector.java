package com.dropai.rewrite.modules.standardPartSelector;

import com.dropai.rewrite.modules.model.DesignProject;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class StandardPartSelector {
    private final StandardPartCache cache;
    private final OnlineStandardPartProvider onlineProvider;

    public StandardPartSelector(StandardPartCache cache, OnlineStandardPartProvider onlineProvider) {
        this.cache = cache;
        this.onlineProvider = onlineProvider;
    }

    public DesignProject select(DesignProject project) {
        List<DesignProject.DesignPart> parts = new ArrayList<>();
        collect(project.getStructureTree(), "", project, parts);
        project.setResolvedParts(parts);
        project.setStandardParts(parts.stream().filter(p -> "standard".equals(p.getPartType()))
                .map(p -> p.getName() + "：" + p.getModel() + "（" + p.getSource() + "）").distinct().toList());
        appendSelectionCalculations(project, parts);
        return project;
    }

    private void collect(DesignProject.StructureNode node, String parent, DesignProject project, List<DesignProject.DesignPart> parts) {
        if (node == null) return;
        if (!"整机".equals(node.getName())) parts.add(selectPart(node.getName(), parent, project));
        for (DesignProject.StructureNode child : node.getChildren()) collect(child, node.getName(), project, parts);
    }

    private DesignProject.DesignPart selectPart(String name, String parent, DesignProject project) {
        String category = standardCategory(name);
        if (category.isBlank()) return unresolved(name, parent);
        StandardPartQuery query = new StandardPartQuery(category, name, requirements(category, project));
        Optional<StandardPartResult> cached = cache.find(query);
        Optional<StandardPartResult> result = cached.isPresent() ? cached : onlineProvider.search(query);
        if (result.isEmpty()) return unresolved(name, parent);
        if (cached.isEmpty()) {
            cache.save(query, result.get());
            onlineProvider.cacheResult(result.get());
        }
        return toPart(result.get(), name, parent, cached.isPresent());
    }

    private DesignProject.DesignPart toPart(StandardPartResult result, String originalName, String parent, boolean fromCache) {
        DesignProject.DesignPart part = new DesignProject.DesignPart();
        part.setPartType("standard");
        part.setCategory(result.getCategory());
        part.setName(originalName.contains(result.getName()) ? originalName : originalName + "-" + result.getName());
        part.setModel(result.getModel());
        part.setBrand(result.getBrand());
        part.setSource(fromCache ? "local_cache:" + result.getSource() : result.getSource());
        part.setSourceUrl(result.getSourceUrl());
        part.setDimensions(result.getDimensions());
        part.setAvailableFormats(result.getAvailableFormats());
        part.setConfidence(result.getConfidence());
        part.setReason(result.getReason());
        part.setGeneratedBy("StandardPartSelector");
        part.setMaterial("标准件");
        part.setProcess("标准件参数匹配：" + result.getDimensions());
        part.setGeometryFeatures(List.of("在线/缓存标准件型号：" + result.getModel(), "来源：" + part.getSource(), "可用格式：" + String.join("/", result.getAvailableFormats()), "装配定位面", "安装孔匹配"));
        part.setQuantity(quantity(result.getCategory(), originalName));
        part.setParentStructure(parent);
        return part;
    }

    private String standardCategory(String name) {
        if (containsAny(name, "轴承")) return "bearing";
        if (containsAny(name, "电机", "电动", "驱动")) return "motor";
        if (containsAny(name, "减速器", "减速机")) return "reducer";
        if (containsAny(name, "导轨", "滑轨")) return "rail";
        if (containsAny(name, "联轴器")) return "coupling";
        if (containsAny(name, "螺栓", "螺钉")) return "bolt";
        if (containsAny(name, "链轮")) return "sprocket";
        if (containsAny(name, "同步带轮")) return "timing_pulley";
        if (containsAny(name, "滚轮", "支重轮", "驱动轮", "从动轮")) return "roller";
        if (containsAny(name, "轴")) return "shaft";
        if (containsAny(name, "键")) return "key";
        if (containsAny(name, "销")) return "pin";
        if (containsAny(name, "弹簧")) return "spring";
        if (containsAny(name, "法兰")) return "flange";
        return "";
    }

    private Map<String, Object> requirements(String category, DesignProject project) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("category", category);
        switch (category) {
            case "motor" -> {
                result.put("requiredPowerW", Math.max(40, project.number("电机功率", project.number("功率", 0.08) * 1000)));
                result.put("requiredSpeed", project.number("转速", 120));
            }
            case "reducer" -> {
                result.put("requiredTorqueNm", Math.max(8, project.number("驱动轮输出扭矩", project.number("输出扭矩", 12))));
                result.put("ratio", project.number("传动比", 20));
            }
            case "bearing", "coupling", "shaft" -> result.put("shaftDiameter", Math.max(12, project.number("轴径", project.number("轮径", 80) / 6)));
            case "rail" -> result.put("railWidth", project.number("导轨宽度", 12));
            case "bolt", "key", "pin" -> result.put("nominalDiameter", project.number("螺栓直径", 6));
            default -> result.put("load", project.number("设计载荷", 1200));
        }
        return result;
    }

    private DesignProject.DesignPart unresolved(String name, String parent) {
        DesignProject.DesignPart part = new DesignProject.DesignPart();
        part.setPartType("unresolved");
        part.setName(name);
        part.setSource("StructureTree");
        part.setParentStructure(parent);
        part.setQuantity(quantity("", name));
        return part;
    }

    private int quantity(String category, String name) {
        if (name.contains("支重") || name.contains("螺栓")) return 8;
        if (name.contains("左右") || name.contains("驱动") || name.contains("从动") || name.contains("电机") || name.contains("减速器") || name.contains("轮")) return 2;
        return 1;
    }

    private void appendSelectionCalculations(DesignProject project, List<DesignProject.DesignPart> parts) {
        parts.stream().filter(p -> "standard".equals(p.getPartType())).limit(8).forEach(part -> project.getCalculations().add(new DesignProject.Calculation(
                part.getName() + "标准件选型",
                "online/cache search → parameter match",
                part.getModel() + "；来源：" + part.getSource() + "；尺寸参数：" + part.getDimensions() + "；说明：" + part.getReason(),
                1,
                "",
                "标准件参数已写入BOM、装配树、CAD和3D展示数据")));
    }

    private boolean containsAny(String value, String... words) {
        if (value == null) return false;
        for (String word : words) if (value.contains(word)) return true;
        return false;
    }
}
