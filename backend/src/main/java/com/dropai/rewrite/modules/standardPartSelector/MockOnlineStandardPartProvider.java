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
        // Mock provider only preserves the future online-provider contract. It is not a real platform lookup.
        StandardPartResult result = new StandardPartResult();
        result.setPartId("mock-" + query.getCategory() + "-" + Math.abs(query.getName().hashCode()));
        result.setCategory(query.getCategory());
        result.setName(displayName(query.getCategory()));
        result.setModel(model(query.getCategory()));
        result.setBrand("mock");
        result.setSource("mock_provider_pending_real_api");
        result.setSourceUrl("");
        result.setDimensions(dimensions(query.getCategory(), query.getRequirements()));
        result.setAvailableFormats(List.of("STEP", "IGES", "SLDPRT", "DXF"));
        result.setConfidence(0.55);
        result.setReason("接口层预留：当前未接入真实在线标准件平台，结果仅用于流程联调；接入TraceParts/MISUMI/SKF等provider后替换。");
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
            case "motor" -> "驱动电机";
            case "reducer" -> "减速器";
            case "rail" -> "直线导轨";
            case "coupling" -> "联轴器";
            case "bolt" -> "螺栓";
            case "sprocket" -> "链轮";
            default -> category + "标准件";
        };
    }

    private String model(String category) {
        return switch (category) {
            case "bearing" -> "6204";
            case "motor" -> "24V-60W";
            case "reducer" -> "PLF60-i20";
            case "rail" -> "MGN12";
            case "coupling" -> "D25L30";
            case "bolt" -> "M6-8.8";
            case "sprocket" -> "08B-Z19";
            default -> "PENDING";
        };
    }

    private Map<String, Object> dimensions(String category, Map<String, Object> requirements) {
        Map<String, Object> result = new LinkedHashMap<>(requirements);
        if ("bearing".equals(category)) result.putAll(Map.of("innerDiameter", 20, "outerDiameter", 47, "width", 14));
        if ("rail".equals(category)) result.putAll(Map.of("width", 12, "height", 8, "mountingPitch", 25));
        if ("motor".equals(category)) result.putAll(Map.of("ratedPower", 60, "ratedSpeed", 120, "mountingPitch", 32));
        if ("reducer".equals(category)) result.putAll(Map.of("ratio", 20, "outputTorque", 15, "centerDistance", 60));
        return result;
    }
}
