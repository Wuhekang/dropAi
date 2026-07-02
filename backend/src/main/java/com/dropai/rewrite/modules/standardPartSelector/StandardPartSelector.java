package com.dropai.rewrite.modules.standardPartSelector;

import com.dropai.rewrite.modules.model.DesignProject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class StandardPartSelector {
    private static final Logger log = LoggerFactory.getLogger(StandardPartSelector.class);
    private static final List<String> MODEL_FORMATS = List.of("STEP", "IGES", "SLDPRT", "STL", "GLTF");

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
                .map(p -> "%s %s %s %s".formatted(p.getName(), p.getModel(), p.getSourcePlatform(), p.getRetrievalStatus()))
                .distinct().toList());
        appendSelectionCalculations(project, parts);
        return project;
    }

    private void collect(DesignProject.StructureNode node, String parent, DesignProject project, List<DesignProject.DesignPart> parts) {
        if (node == null) return;
        boolean groupingNode = node.getChildren() != null && !node.getChildren().isEmpty();
        if (!isRoot(node.getName()) && !groupingNode) parts.add(resolvePart(node.getName(), parent, project));
        for (DesignProject.StructureNode child : node.getChildren()) collect(child, node.getName(), project, parts);
    }

    private DesignProject.DesignPart resolvePart(String name, String parent, DesignProject project) {
        String category = standardCategory(name);
        if (category.isBlank()) return unresolved(name, parent);

        StandardPartQuery query = new StandardPartQuery(category, name, requirements(category, project));
        Optional<StandardPartResult> selected = onlineProvider.search(query).map(result -> mark(result, "online_found"));
        if (selected.isPresent()) {
            cache.save(query, selected.get());
            onlineProvider.cacheResult(selected.get());
        } else {
            selected = cache.find(query).map(result -> mark(result, "cache_found"));
        }
        if (selected.isEmpty()) selected = Optional.of(fallbackResult(query));

        DesignProject.DesignPart part = toPart(selected.get(), name, parent);
        log.info("标准件检索 name={} category={} status={} platform={} model={} sourceUrl={}",
                part.getName(), part.getCategory(), part.getRetrievalStatus(), part.getSourcePlatform(), part.getModel(), part.getSourceUrl());
        return part;
    }

    private StandardPartResult mark(StandardPartResult result, String defaultStatus) {
        if (blank(result.getRetrievalStatus())) result.setRetrievalStatus(defaultStatus);
        if (result.getAvailableModelFormats().isEmpty()) result.setAvailableModelFormats(result.getAvailableFormats().isEmpty() ? MODEL_FORMATS : result.getAvailableFormats());
        if (result.getAvailableFormats().isEmpty()) result.setAvailableFormats(result.getAvailableModelFormats());
        if (blank(result.getSourcePlatform())) result.setSourcePlatform(result.getSource());
        return result;
    }

    private StandardPartResult fallbackResult(StandardPartQuery query) {
        StandardPartResult result = new StandardPartResult();
        result.setPartId("fallback-" + query.getCategory() + "-" + Math.abs(query.getName().hashCode()));
        result.setCategory(query.getCategory());
        result.setName(displayName(query.getCategory()));
        result.setModel("FALLBACK-" + query.getCategory().toUpperCase());
        result.setBrand("fallback");
        result.setSource("fallback_generated");
        result.setSourcePlatform("fallback_placeholder");
        result.setDimensions(defaultDimensions(query.getCategory(), query.getRequirements()));
        result.setTechnicalParams(new LinkedHashMap<>(query.getRequirements()));
        result.setRetrievalStatus("fallback_generated");
        result.setConfidence(0.32);
        result.setReason("在线平台与本地缓存均未返回可用标准件，生成参数化标准件占位并标记 fallback。");
        return mark(result, "fallback_generated");
    }

    private DesignProject.DesignPart toPart(StandardPartResult result, String originalName, String parent) {
        DesignProject.DesignPart part = new DesignProject.DesignPart();
        part.setPartType("standard");
        part.setCategory(result.getCategory());
        part.setName(originalName.contains(result.getName()) ? originalName : originalName + "-" + result.getName());
        part.setModel(result.getModel());
        part.setBrand(result.getBrand());
        part.setSource(result.getSource());
        part.setSourcePlatform(result.getSourcePlatform());
        part.setSourceUrl(result.getSourceUrl());
        part.setDimensions(result.getDimensions());
        part.setTechnicalParams(result.getTechnicalParams());
        part.setAvailableFormats(result.getAvailableFormats());
        part.setAvailableModelFormats(result.getAvailableModelFormats().isEmpty() ? result.getAvailableFormats() : result.getAvailableModelFormats());
        part.setModelDownloadUrl(result.getModelDownloadUrl());
        part.setCachedModelPath(result.getCachedModelPath());
        part.setRetrievalStatus(result.getRetrievalStatus());
        part.setConfidence(result.getConfidence());
        part.setReason(result.getReason());
        part.setGeneratedBy("StandardPartSelector");
        part.setMaterial("标准件");
        part.setProcess(processDescription(part));
        part.setGeometryFeatures(geometryFeatures(part));
        part.setQuantity(quantity(result.getCategory(), originalName));
        part.setParentStructure(parent);
        return part;
    }

    private String processDescription(DesignProject.DesignPart part) {
        if (!blank(part.getModelDownloadUrl()) || !blank(part.getCachedModelPath())) return "优先加载真实标准件模型";
        if (!part.getDimensions().isEmpty()) return "按标准件尺寸参数生成参数化近似模型";
        return "缺少尺寸参数，使用简化标准件占位模型";
    }

    private List<String> geometryFeatures(DesignProject.DesignPart part) {
        List<String> features = new ArrayList<>();
        features.add("标准件类别：" + part.getCategory());
        features.add("型号：" + part.getModel());
        features.add("来源平台：" + part.getSourcePlatform());
        features.add("检索状态：" + part.getRetrievalStatus());
        if ("mock".equals(part.getRetrievalStatus())) features.add("标准件参数为模拟推荐，未联网校验");
        if (!part.getAvailableModelFormats().isEmpty()) features.add("可用模型格式：" + String.join("/", part.getAvailableModelFormats()));
        if (!part.getDimensions().isEmpty()) features.add("尺寸参数：" + part.getDimensions());
        features.add(categoryCadFeature(part.getCategory()));
        return features;
    }

    private String categoryCadFeature(String category) {
        return switch (category) {
            case "bearing" -> "CAD表达：圆环、剖面线、中心线";
            case "motor" -> "CAD表达：电机外形、安装法兰、输出轴";
            case "reducer" -> "CAD表达：减速器箱体、输入轴、输出轴、安装孔";
            case "rail" -> "CAD表达：导轨截面、滑块、安装孔";
            case "coupling" -> "CAD表达：双圆柱、中心线、紧定螺钉";
            case "bolt" -> "CAD表达：标准螺栓符号";
            case "flange" -> "CAD表达：法兰盘、孔阵列、中心孔";
            default -> "CAD表达：按标准件类别进行简化工程表达";
        };
    }

    private String standardCategory(String name) {
        if (containsAny(name, "轴承", "bearing")) return "bearing";
        if (containsAny(name, "电机", "驱动电机", "伺服", "motor")) return "motor";
        if (containsAny(name, "减速器", "减速机", "reducer", "gearbox")) return "reducer";
        if (containsAny(name, "导轨", "滑轨", "滑块", "rail", "linear guide")) return "rail";
        if (containsAny(name, "联轴器", "coupling")) return "coupling";
        if (containsAny(name, "螺栓", "螺钉", "紧固螺栓", "固定螺栓", "bolt", "screw")) return "bolt";
        if (containsAny(name, "链轮", "sprocket")) return "sprocket";
        if (containsAny(name, "同步带轮", "timing pulley")) return "timing_pulley";
        if (containsAny(name, "滚轮", "支重轮", "驱动轮", "从动轮", "轮", "roller", "wheel")) return "roller";
        if (containsAny(name, "传动轴", "轮轴", "刷盘连接轴", "轴", "shaft")) return "shaft";
        if (containsAny(name, "定位销", "销", "pin")) return "pin";
        if (containsAny(name, "键", "key")) return "key";
        if (containsAny(name, "弹簧", "spring")) return "spring";
        if (containsAny(name, "法兰", "flange")) return "flange";
        if (containsAny(name, "轴承", "bearing")) return "bearing";
        if (containsAny(name, "电机", "驱动", "伺服", "motor")) return "motor";
        if (containsAny(name, "减速器", "减速机", "reducer", "gearbox")) return "reducer";
        if (containsAny(name, "导轨", "滑轨", "rail", "linear guide")) return "rail";
        if (containsAny(name, "联轴器", "coupling")) return "coupling";
        if (containsAny(name, "螺栓", "螺钉", "bolt", "screw")) return "bolt";
        if (containsAny(name, "链轮", "sprocket")) return "sprocket";
        if (containsAny(name, "同步带轮", "timing pulley")) return "timing_pulley";
        if (containsAny(name, "滚轮", "支重轮", "驱动轮", "从动轮", "轮", "roller", "wheel")) return "roller";
        if (containsAny(name, "轴", "shaft")) return "shaft";
        if (containsAny(name, "键", "key")) return "key";
        if (containsAny(name, "销", "pin")) return "pin";
        if (containsAny(name, "弹簧", "spring")) return "spring";
        if (containsAny(name, "法兰", "flange")) return "flange";
        return "";
    }

    private Map<String, Object> requirements(String category, DesignProject project) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("category", category);
        switch (category) {
            case "motor" -> { result.put("requiredPowerW", Math.max(40, project.number("电机功率", project.number("功率", 0.08) * 1000))); result.put("requiredSpeedRpm", project.number("转速", 120)); }
            case "reducer" -> { result.put("requiredTorqueNm", Math.max(8, project.number("输出扭矩", 12))); result.put("ratio", project.number("传动比", 20)); }
            case "bearing", "coupling", "shaft" -> result.put("shaftDiameter", Math.max(12, project.number("轴径", project.number("轮径", 80) / 6)));
            case "rail" -> result.put("railWidth", project.number("导轨宽度", 12));
            case "bolt", "key", "pin" -> result.put("nominalDiameter", project.number("螺栓直径", 6));
            case "roller" -> { result.put("diameter", project.number("轮径", 80)); result.put("load", project.number("设计载荷", 1200)); }
            default -> result.put("load", project.number("设计载荷", 1000));
        }
        return result;
    }

    private Map<String, Object> defaultDimensions(String category, Map<String, Object> req) {
        Map<String, Object> d = new LinkedHashMap<>();
        switch (category) {
            case "motor" -> { d.put("bodyDiameter", 60); d.put("bodyLength", 95); d.put("shaftDiameter", 8); d.put("mountingPitch", 32); }
            case "reducer" -> { d.put("length", 90); d.put("width", 60); d.put("height", 60); d.put("mountingPitch", 50); }
            case "bearing" -> { d.put("innerDiameter", 20); d.put("outerDiameter", 47); d.put("width", 14); }
            case "rail" -> { d.put("width", 12); d.put("height", 8); d.put("blockLength", 45); d.put("mountingPitch", 25); }
            case "roller" -> { d.put("diameter", req.getOrDefault("diameter", 80)); d.put("width", 28); d.put("bearingBore", 20); }
            case "bolt" -> { d.put("nominalDiameter", req.getOrDefault("nominalDiameter", 6)); d.put("length", 20); d.put("headWidth", 10); }
            default -> { d.put("length", 50); d.put("width", 30); d.put("height", 20); }
        }
        return d;
    }

    private DesignProject.DesignPart unresolved(String name, String parent) {
        DesignProject.DesignPart part = new DesignProject.DesignPart();
        part.setPartType("unresolved");
        part.setName(name);
        part.setParentStructure(parent);
        part.setQuantity(1);
        part.setSource("StructureTreeBuilder");
        return part;
    }

    private void appendSelectionCalculations(DesignProject project, List<DesignProject.DesignPart> parts) {
        long mockCount = parts.stream().filter(p -> "mock".equals(p.getRetrievalStatus())).count();
        if (mockCount > 0) {
            project.getCalculations().add(new DesignProject.Calculation("标准件检索状态", "mockCount", "模拟推荐数量=" + mockCount, mockCount, "项", "标准件参数为模拟推荐，未联网校验"));
        }
    }

    private String displayName(String category) {
        return switch (category) {
            case "bearing" -> "深沟球轴承";
            case "motor" -> "伺服/直流驱动电机";
            case "reducer" -> "行星减速器";
            case "rail" -> "直线导轨";
            case "coupling" -> "联轴器";
            case "bolt" -> "标准螺栓";
            case "roller" -> "滚轮";
            case "shaft" -> "传动轴";
            default -> "标准件";
        };
    }

    private int quantity(String category, String name) {
        if ("bolt".equals(category)) return 8;
        if (containsAny(name, "支重轮", "螺栓")) return 4;
        if (containsAny(name, "履带", "驱动轮", "从动轮")) return 2;
        return 1;
    }

    private boolean isRoot(String name) { return name == null || name.isBlank() || "整机".equals(name); }
    private boolean containsAny(String value, String... words) { String v = value == null ? "" : value.toLowerCase(); for (String word : words) if (v.contains(word.toLowerCase())) return true; return false; }
    private boolean blank(String v) { return v == null || v.isBlank(); }
}
