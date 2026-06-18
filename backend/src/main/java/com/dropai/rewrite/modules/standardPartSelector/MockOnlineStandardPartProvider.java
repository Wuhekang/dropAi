package com.dropai.rewrite.modules.standardPartSelector;

import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class MockOnlineStandardPartProvider implements OnlineStandardPartProvider {
    private static final List<String> FORMATS = List.of("STEP", "IGES", "SLDPRT", "STL", "GLTF");
    private final StandardPartCache cache;

    public MockOnlineStandardPartProvider(StandardPartCache cache) {
        this.cache = cache;
    }

    @Override
    public Optional<StandardPartResult> search(StandardPartQuery query) {
        if (query == null || query.getCategory() == null || query.getCategory().isBlank()) return Optional.empty();
        StandardPartResult result = new StandardPartResult();
        result.setPartId("mock-" + query.getCategory() + "-" + Math.abs(query.getName().hashCode()));
        result.setCategory(query.getCategory());
        result.setName(displayName(query.getCategory()));
        result.setModel(model(query.getCategory()));
        result.setBrand(brand(query.getCategory()));
        result.setSource("mock_provider");
        result.setSourcePlatform("模拟推荐：" + platform(query.getCategory()));
        result.setSourceUrl("");
        result.setDimensions(dimensions(query.getCategory(), query.getRequirements()));
        result.setTechnicalParams(new LinkedHashMap<>(query.getRequirements()));
        result.setAvailableFormats(FORMATS);
        result.setAvailableModelFormats(FORMATS);
        result.setRetrievalStatus("mock");
        result.setConfidence(0.55);
        result.setReason("标准件参数为模拟推荐，未联网校验；接入真实公开标准件平台API后由真实Provider替换。");
        return Optional.of(result);
    }

    @Override
    public Optional<StandardPartResult> fetchDetail(String partId) {
        return Optional.empty();
    }

    @Override
    public void cacheResult(StandardPartResult part) {
        // Mock provider does not download models. StandardPartSelector writes cache by query.
    }

    private String displayName(String category) {
        return switch (category) {
            case "bearing" -> "深沟球轴承";
            case "motor" -> "伺服/直流驱动电机";
            case "reducer" -> "行星减速器";
            case "rail" -> "直线导轨";
            case "coupling" -> "弹性联轴器";
            case "bolt" -> "六角头螺栓";
            case "roller" -> "包胶滚轮";
            case "shaft" -> "传动轴";
            default -> "标准件";
        };
    }

    private String model(String category) {
        return switch (category) {
            case "bearing" -> "6204";
            case "motor" -> "EC60-60W";
            case "reducer" -> "PLF60-i20";
            case "rail" -> "MGN12H";
            case "coupling" -> "D25L30";
            case "bolt" -> "M6x20";
            case "roller" -> "U608-80";
            case "shaft" -> "D20";
            default -> "PENDING";
        };
    }

    private String brand(String category) {
        return switch (category) {
            case "bearing" -> "SKF";
            case "motor" -> "MISUMI";
            case "reducer" -> "NORD";
            case "rail" -> "HIWIN";
            default -> "mock";
        };
    }

    private String platform(String category) {
        return switch (category) {
            case "bearing" -> "SKF / TraceParts";
            case "motor" -> "MISUMI / TraceParts";
            case "reducer" -> "NORD / 3D ContentCentral";
            case "rail" -> "HIWIN / CADENAS PARTsolutions";
            default -> "TraceParts / GrabCAD";
        };
    }

    private Map<String, Object> dimensions(String category, Map<String, Object> req) {
        Map<String, Object> d = new LinkedHashMap<>();
        switch (category) {
            case "bearing" -> { d.put("innerDiameter", 20); d.put("outerDiameter", 47); d.put("width", 14); }
            case "motor" -> { d.put("bodyDiameter", 60); d.put("bodyLength", 95); d.put("shaftDiameter", 8); d.put("mountingPitch", 32); }
            case "reducer" -> { d.put("length", 90); d.put("width", 60); d.put("height", 60); d.put("ratio", req.getOrDefault("ratio", 20)); d.put("mountingPitch", 50); }
            case "rail" -> { d.put("width", 12); d.put("height", 8); d.put("blockLength", 45); d.put("mountingPitch", 25); }
            case "roller" -> { d.put("diameter", req.getOrDefault("diameter", 80)); d.put("width", 28); d.put("bearingBore", 20); }
            case "bolt" -> { d.put("nominalDiameter", req.getOrDefault("nominalDiameter", 6)); d.put("length", 20); d.put("headWidth", 10); }
            default -> { d.put("length", 50); d.put("width", 30); d.put("height", 20); }
        }
        return d;
    }
}