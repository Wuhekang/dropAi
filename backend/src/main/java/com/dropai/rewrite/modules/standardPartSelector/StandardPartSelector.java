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
        if (!isRoot(node.getName())) parts.add(resolvePart(node.getName(), parent, project));
        for (DesignProject.StructureNode child : node.getChildren()) collect(child, node.getName(), project, parts);
    }

    private DesignProject.DesignPart resolvePart(String name, String parent, DesignProject project) {
        String category = standardCategory(name);
        if (category.isBlank()) return unresolved(name, parent);

        StandardPartQuery query = new StandardPartQuery(category, name, requirements(category, project));
        Optional<StandardPartResult> online = onlineProvider.search(query).map(result -> mark(result, "online_found"));
        Optional<StandardPartResult> selected = online;
        if (selected.isPresent()) {
            cache.save(query, selected.get());
            onlineProvider.cacheResult(selected.get());
        } else {
            selected = cache.find(query).map(result -> mark(result, "cache_found"));
        }
        if (selected.isEmpty()) {
            selected = Optional.of(fallbackResult(query));
        }

        DesignProject.DesignPart part = toPart(selected.get(), name, parent);
        log.info("标准件检索 name={} category={} status={} platform={} model={} sourceUrl={}",
                part.getName(), part.getCategory(), part.getRetrievalStatus(), part.getSourcePlatform(), part.getModel(), part.getSourceUrl());
        return part;
    }

    private StandardPartResult mark(StandardPartResult result, String status) {
        if (result.getRetrievalStatus() == null || result.getRetrievalStatus().isBlank()) result.setRetrievalStatus(status);
        if (result.getAvailableModelFormats().isEmpty()) result.setAvailableModelFormats(result.getAvailableFormats().isEmpty() ? MODEL_FORMATS : result.getAvailableFormats());
        if (result.getAvailableFormats().isEmpty()) result.setAvailableFormats(result.getAvailableModelFormats());
        if (result.getSourcePlatform() == null || result.getSourcePlatform().isBlank()) result.setSourcePlatform(result.getSource());
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
        result.setSourcePlatform("AI simplified standard part placeholder");
        result.setSourceUrl("");
        result.setDimensions(defaultDimensions(query.getCategory(), query.getRequirements()));
        result.setTechnicalParams(new LinkedHashMap<>(query.getRequirements()));
        result.setAvailableModelFormats(List.of());
        result.setAvailableFormats(List.of());
        result.setRetrievalStatus("fallback_generated");
        result.setConfidence(0.32);
        result.setReason("在线平台与本地缓存均未返回可用标准件，生成参数化标准件占位并标记fallback。");
        return result;
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
        if (notBlank(part.getModelDownloadUrl()) || notBlank(part.getCachedModelPath())) return "优先加载真实标准件模型";
        if (!part.getDimensions().isEmpty()) return "按标准件尺寸参数生成参数化近似模型";
        return "缺少尺寸参数，使用简化标准件占位模型";
    }

    private List<String> geometryFeatures(DesignProject.DesignPart part) {
        List<String> features = new ArrayList<>();
        features.add("标准件类别：" + part.getCategory());
        features.add("型号：" + part.getModel());
        features.add("来源平台：" + part.getSourcePlatform());
        features.add("检索状态：" + part.getRetrievalStatus());
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
        if (containsAny(name, "轴承", "杞存壙")) return "bearing";
        if (containsAny(name, "电机", "鐢垫満", "驱动", "椹卞姩")) return "motor";
        if (containsAny(name, "减速器", "减速机", "鍑忛€熷櫒", "鍑忛€熸満")) return "reducer";
        if (containsAny(name, "导轨", "滑轨", "瀵艰建", "婊戣建")) return "rail";
        if (containsAny(name, "联轴器", "鑱旇酱鍣")) return "coupling";
        if (containsAny(name, "螺栓", "螺钉", "铻烘爴", "铻洪拤")) return "bolt";
        if (containsAny(name, "链轮", "閾捐疆")) return "sprocket";
        if (containsAny(name, "同步带轮", "鍚屾甯﹁疆")) return "timing_pulley";
        if (containsAny(name, "滚轮", "支重轮", "驱动轮", "从动轮", "婊氳疆", "鏀噸杞", "椹卞姩杞", "浠庡姩杞")) return "roller";
        if (containsAny(name, "轴", "杞")) return "shaft";
        if (containsAny(name, "键", "閿")) return "key";
        if (containsAny(name, "销", "閿€")) return "pin";
        if (containsAny(name, "弹簧", "寮圭哀")) return "spring";
        if (containsAny(name, "法兰", "娉曞叞")) return "flange";
        return "";
    }

    private Map<String, Object> requirements(String category, DesignProject project) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("category", category);
        switch (category) {
            case "motor" -> {
                result.put("requiredPowerW", Math.max(40, project.number("电机功率", project.number("功率", 0.08) * 1000)));
                result.put("requiredSpeedRpm", project.number("转速", 120));
            }
            case "reducer" -> {
                result.put("requiredTorqueNm", Math.max(8, project.number("输出扭矩", 12)));
                result.put("ratio", project.number("传动比", 20));
            }
            case "bearing", "coupling", "shaft" -> result.put("shaftDiameter", Math.max(12, project.number("轴径", project.number("轮径", 80) / 6)));
            case "rail" -> result.put("railWidth", project.number("导轨宽度", 12));
            case "bolt", "key", "pin" -> result.put("nominalDiameter", project.number("螺栓直径", 6));
            default -> result.put("load", project.number("设计载荷", 1200));
        }
        return result;
    }

    private Map<String, Object> defaultDimensions(String category, Map<String, Object> requirements) {
        Map<String, Object> result = new LinkedHashMap<>(requirements);
        switch (category) {
            case "bearing" -> result.putAll(Map.of("innerDiameter", 20, "outerDiameter", 47, "width", 14));
            case "motor" -> result.putAll(Map.of("ratedPowerW", requirements.getOrDefault("requiredPowerW", 60), "ratedSpeedRpm", requirements.getOrDefault("requiredSpeedRpm", 120), "mountingPitch", 32, "shaftDiameter", 8));
            case "reducer" -> result.putAll(Map.of("ratio", requirements.getOrDefault("ratio", 20), "outputTorqueNm", requirements.getOrDefault("requiredTorqueNm", 15), "centerDistance", 60));
            case "rail" -> result.putAll(Map.of("width", requirements.getOrDefault("railWidth", 12), "height", 8, "mountingPitch", 25));
            case "bolt" -> result.putAll(Map.of("nominalDiameter", requirements.getOrDefault("nominalDiameter", 6), "length", 20, "grade", "8.8"));
            case "flange" -> result.putAll(Map.of("outerDiameter", 120, "innerDiameter", 60, "holeCount", 6, "holeDiameter", 10));
            default -> result.putIfAbsent("nominalSize", 50);
        }
        return result;
    }

    private String displayName(String category) {
        return switch (category) {
            case "bearing" -> "深沟球轴承";
            case "motor" -> "驱动电机";
            case "reducer" -> "减速器";
            case "rail" -> "直线导轨";
            case "coupling" -> "联轴器";
            case "bolt" -> "螺栓";
            case "flange" -> "法兰";
            default -> category + "标准件";
        };
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
        if (containsAny(name, "支重", "螺栓", "鏀噸", "铻烘爴")) return 8;
        if (containsAny(name, "左右", "驱动", "从动", "电机", "减速器", "轮", "宸﹀彸", "椹卞姩", "浠庡姩", "鐢垫満", "鍑忛€熷櫒", "杞")) return 2;
        return 1;
    }

    private void appendSelectionCalculations(DesignProject project, List<DesignProject.DesignPart> parts) {
        parts.stream().filter(p -> "standard".equals(p.getPartType())).limit(10).forEach(part -> project.getCalculations().add(new DesignProject.Calculation(
                part.getName() + "标准件选型",
                "OnlineStandardPartProvider.search -> cache fallback -> parametric fallback",
                part.getModel() + "；平台：" + part.getSourcePlatform() + "；状态：" + part.getRetrievalStatus()
                        + "；格式：" + part.getAvailableModelFormats() + "；尺寸：" + part.getDimensions(),
                1,
                "",
                "标准件结果已进入装配树、BOM、3D和CAD数据链")));
    }

    private boolean isRoot(String value) {
        return containsAny(value, "整机", "鏁存満");
    }

    private boolean containsAny(String value, String... words) {
        if (value == null) return false;
        for (String word : words) if (word != null && !word.isBlank() && value.contains(word)) return true;
        return false;
    }

    private boolean notBlank(String value) {
        return value != null && !value.isBlank();
    }
}
