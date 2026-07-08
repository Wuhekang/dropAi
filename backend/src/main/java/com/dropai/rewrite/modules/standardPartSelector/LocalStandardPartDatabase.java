package com.dropai.rewrite.modules.standardPartSelector;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

@Service
public class LocalStandardPartDatabase {
    private static final String RESOURCE = "/knowledge/mechanical/standard_parts.json";
    private final ObjectMapper objectMapper;
    private List<Map<String, Object>> parts;

    public LocalStandardPartDatabase(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public Optional<StandardPartResult> find(StandardPartQuery query) {
        return load().stream()
                .filter(item -> sameCategory(query.getCategory(), string(item.get("category"))))
                .sorted((a, b) -> Integer.compare(score(b, query), score(a, query)))
                .findFirst()
                .map(item -> toResult(item, query));
    }

    private synchronized List<Map<String, Object>> load() {
        if (parts != null) return parts;
        try (InputStream input = LocalStandardPartDatabase.class.getResourceAsStream(RESOURCE)) {
            if (input == null) {
                parts = List.of();
                return parts;
            }
            Map<String, Object> root = objectMapper.readValue(input, new TypeReference<>() {});
            Object rawParts = root.get("parts");
            if (rawParts instanceof List<?> list) {
                List<Map<String, Object>> parsed = new ArrayList<>();
                for (Object item : list) {
                    if (item instanceof Map<?, ?> map) parsed.add(new LinkedHashMap<>((Map<String, Object>) map));
                }
                parts = parsed;
            } else {
                parts = List.of();
            }
            return parts;
        } catch (Exception ignored) {
            parts = List.of();
            return parts;
        }
    }

    private StandardPartResult toResult(Map<String, Object> item, StandardPartQuery query) {
        StandardPartResult result = new StandardPartResult();
        result.setPartId(string(item.get("id")));
        result.setCategory(query.getCategory());
        result.setName(string(item.get("name")));
        result.setModel(firstNonBlank(string(item.get("standard")), string(item.get("name"))));
        result.setBrand("local-library");
        result.setSource("knowledge/mechanical/standard_parts.json");
        result.setSourcePlatform("local_standard_part_database");
        result.setDimensions(map(item.get("parameters")));
        result.setTechnicalParams(map(item.get("parameters")));
        result.setAvailableFormats(List.of("STEP"));
        result.setAvailableModelFormats(List.of("STEP"));
        result.setCachedModelPath(string(item.get("stepPath")));
        result.setRetrievalStatus("local_library");
        result.setConfidence(0.86);
        result.setReason("Matched local mechanical standard part database before online/mock fallback.");
        return result;
    }

    private int score(Map<String, Object> item, StandardPartQuery query) {
        String name = (string(item.get("name")) + " " + string(item.get("standard")) + " " + string(item.get("application"))).toLowerCase(Locale.ROOT);
        String queryName = query.getName() == null ? "" : query.getName().toLowerCase(Locale.ROOT);
        int score = 10;
        for (String token : queryName.split("[\\s\\-/]+")) {
            if (!token.isBlank() && name.contains(token)) score += 5;
        }
        return score;
    }

    private boolean sameCategory(String queryCategory, String libraryCategory) {
        return normalizeCategory(queryCategory).equals(normalizeCategory(libraryCategory));
    }

    private String normalizeCategory(String category) {
        String value = category == null ? "" : category.toLowerCase(Locale.ROOT).trim();
        return switch (value) {
            case "guide rail", "linear guide" -> "rail";
            case "gearbox" -> "reducer";
            case "screw" -> "bolt";
            default -> value;
        };
    }

    private Map<String, Object> map(Object value) {
        return value instanceof Map<?, ?> raw ? new LinkedHashMap<>((Map<String, Object>) raw) : new LinkedHashMap<>();
    }

    private String firstNonBlank(String first, String second) {
        return first == null || first.isBlank() ? second : first;
    }

    private String string(Object value) {
        return value == null ? "" : String.valueOf(value);
    }
}
