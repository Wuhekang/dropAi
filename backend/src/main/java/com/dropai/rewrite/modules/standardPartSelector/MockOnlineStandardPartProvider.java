package com.dropai.rewrite.modules.standardPartSelector;

import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class MockOnlineStandardPartProvider implements OnlineStandardPartProvider {
    private final StandardPartCache cache;

    public MockOnlineStandardPartProvider(StandardPartCache cache) {
        this.cache = cache;
    }

    @Override
    public Optional<StandardPartResult> search(StandardPartQuery query) {
        // This provider keeps the online-search contract alive until real platform APIs are configured.
        // Results are explicitly marked as mock_provider_pending_real_api and must not be treated as downloaded models.
        StandardPartResult result = new StandardPartResult();
        result.setPartId("mock-online-" + query.getCategory() + "-" + Math.abs(query.getName().hashCode()));
        result.setCategory(query.getCategory());
        result.setName(displayName(query.getCategory()));
        result.setModel(model(query.getCategory()));
        result.setBrand(brand(query.getCategory()));
        result.setSource("mock_provider_pending_real_api");
        result.setSourcePlatform(platform(query.getCategory()));
        result.setSourceUrl(sourceUrl(query.getCategory()));
        result.setDimensions(dimensions(query.getCategory(), query.getRequirements()));
        result.setTechnicalParams(technicalParams(query.getCategory(), query.getRequirements()));
        result.setAvailableModelFormats(List.of("STEP", "IGES", "SLDPRT", "STL", "GLTF"));
        result.setAvailableFormats(result.getAvailableModelFormats());
        result.setModelDownloadUrl("");
        result.setRetrievalStatus("online_found");
        result.setConfidence(0.62);
        result.setReason("在线标准件接口层模拟结果；接入TraceParts、MISUMI、SKF等真实API后由真实Provider替换。");
        return Optional.of(result);
    }

    @Override
    public Optional<StandardPartResult> fetchDetail(String partId) {
        return Optional.empty();
    }

    @Override
    public void cacheResult(StandardPartResult part) {
        StandardPartQuery query = new StandardPartQuery(part.getCategory(), part.getName(), part.getDimensions());
        cache.save(query, part);
    }

    private String displayName(String category) {
        return switch (category) {
            case "bearing" -> "深沟球轴承";
            case "motor" -> "伺服/直流驱动电机";
            case "reducer" -> "行星减速器";
            case "rail" -> "直线导轨";
            case "coupling" -> "弹性联轴器";
            case "bolt" -> "内六角螺栓";
            case "flange" -> "标准法兰";
            case "roller" -> "滚轮";
            default -> category + "标准件";
        };
    }

    private String model(String category) {
        return switch (category) {
            case "bearing" -> "6204";
            case "motor" -> "EC60-60W";
            case "reducer" -> "PLF60-i20";
            case "rail" -> "MGN12H";
            case "coupling" -> "D25L30";
            case "bolt" -> "M6x20-8.8";
            case "flange" -> "DN80-PN16";
            case "roller" -> "U608-80";
            default -> "PENDING-" + category.toUpperCase();
        };
    }

    private String brand(String category) {
        return switch (category) {
            case "bearing" -> "SKF";
            case "rail" -> "HIWIN";
            case "motor" -> "MISUMI";
            case "reducer" -> "NORD";
            case "bolt", "flange", "coupling" -> "MISUMI";
            default -> "TraceParts";
        };
    }

    private String platform(String category) {
        return switch (category) {
            case "bearing" -> "SKF / TraceParts";
            case "rail" -> "HIWIN / CADENAS PARTsolutions";
            case "motor", "bolt", "flange", "coupling" -> "MISUMI / TraceParts";
            case "reducer" -> "NORD / 3D ContentCentral";
            default -> "TraceParts / GrabCAD";
        };
    }

    private String sourceUrl(String category) {
        return switch (category) {
            case "bearing" -> "https://www.skf.com/group/products/rolling-bearings/ball-bearings/deep-groove-ball-bearings";
            case "rail" -> "https://www.hiwin.com/linear-guideways.html";
            case "motor", "bolt", "flange", "coupling" -> "https://us.misumi-ec.com/";
            case "reducer" -> "https://www.nord.com/";
            default -> "https://www.traceparts.com/";
        };
    }

    private Map<String, Object> dimensions(String category, Map<String, Object> requirements) {
        Map<String, Object> result = new LinkedHashMap<>(requirements);
        switch (category) {
            case "bearing" -> result.putAll(Map.of("innerDiameter", 20, "outerDiameter", 47, "width", 14));
            case "rail" -> result.putAll(Map.of("width", 12, "height", 8, "sliderLength", 34, "mountingPitch", 25));
            case "motor" -> result.putAll(Map.of("ratedPowerW", requirements.getOrDefault("requiredPowerW", 60), "ratedSpeedRpm", requirements.getOrDefault("requiredSpeedRpm", 120), "bodyDiameter", 60, "mountingPitch", 32, "shaftDiameter", 8));
            case "reducer" -> result.putAll(Map.of("ratio", requirements.getOrDefault("ratio", 20), "outputTorqueNm", requirements.getOrDefault("requiredTorqueNm", 15), "centerDistance", 60, "mountingPitch", 50));
            case "bolt" -> result.putAll(Map.of("nominalDiameter", requirements.getOrDefault("nominalDiameter", 6), "length", 20, "headDiameter", 10, "grade", "8.8"));
            case "flange" -> result.putAll(Map.of("outerDiameter", 120, "innerDiameter", 80, "holeCount", 8, "holeDiameter", 10));
            case "coupling" -> result.putAll(Map.of("outerDiameter", 25, "length", 30, "boreDiameter", requirements.getOrDefault("shaftDiameter", 12)));
            case "roller" -> result.putAll(Map.of("diameter", 80, "width", 28, "bearingBore", 20));
            default -> result.putIfAbsent("nominalSize", 50);
        }
        return result;
    }

    private Map<String, Object> technicalParams(String category, Map<String, Object> requirements) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("selectionBasis", requirements);
        switch (category) {
            case "bearing" -> result.putAll(Map.of("standard", "GB/T 276", "type", "deep groove ball bearing"));
            case "motor" -> result.putAll(Map.of("voltage", "24V", "mounting", "flange"));
            case "reducer" -> result.putAll(Map.of("backlash", "standard", "mounting", "flange"));
            case "rail" -> result.putAll(Map.of("accuracyGrade", "normal", "sliderType", "block"));
            default -> result.put("standard", "platform catalog");
        }
        return result;
    }
}
