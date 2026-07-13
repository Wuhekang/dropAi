package com.dropai.rewrite.service.writing;

import com.dropai.rewrite.config.DoubaoProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Component
public class DoubaoWebSearchProvider implements ReferenceSearchProvider {
    private final DoubaoProperties properties;
    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    public DoubaoWebSearchProvider(DoubaoProperties properties,
                                   RestClient.Builder builder,
                                   ObjectMapper objectMapper) {
        this.properties = properties;
        this.restClient = builder.build();
        this.objectMapper = objectMapper;
    }

    @Override
    public String name() {
        return "doubao";
    }

    @Override
    public boolean available() {
        return properties.isWebSearchEnabled() && properties.isEnabled() && properties.getApiKey() != null && !properties.getApiKey().isBlank();
    }

    @Override
    public List<ReferenceCandidate> search(ReferenceSearchQuery query) {
        if (!available()) return List.of();
        String endpoint = trimRight(properties.getBaseUrl(), "/") + ensureLeadingSlash(properties.getResponsesPath());
        Map<String, Object> request = Map.of(
                "model", blankTo(properties.getTextModel(), properties.getModel()),
                "stream", false,
                "temperature", properties.getTemperature(),
                "max_output_tokens", Math.max(1024, properties.getMaxOutputTokens()),
                "tools", List.of(Map.of("type", "web_search")),
                "tool_choice", Map.of("type", "web_search"),
                "input", webSearchPrompt(query)
        );
        JsonNode root = restClient.post()
                .uri(endpoint)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + normalizeApiKey(properties.getApiKey()))
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .retrieve()
                .body(JsonNode.class);
        return parseCandidates(root, query);
    }

    private String webSearchPrompt(ReferenceSearchQuery query) {
        return """
                请必须使用 web_search 工具检索真实公开学术资料，只返回可追踪来源。
                不要凭记忆补全文献，不要猜 DOI、作者、期刊或页码。
                请围绕主题：%s
                研究方向：%s
                年份范围：%d-%d
                关键词：%s
                返回 JSON 数组，每项包含 title, url, snippet, year, language。
                """.formatted(query.title(), query.major(), query.yearStart(), query.yearEnd(), query.joinedKeywords());
    }

    private List<ReferenceCandidate> parseCandidates(JsonNode root, ReferenceSearchQuery query) {
        List<ReferenceCandidate> result = new ArrayList<>();
        collectCandidates(root, query, result);
        return result;
    }

    private void collectCandidates(JsonNode node, ReferenceSearchQuery query, List<ReferenceCandidate> result) {
        if (node == null || node.isNull()) return;
        if (node.isObject()) {
            String title = firstNonBlank(node.path("title").asText(""), node.path("name").asText(""));
            String url = firstNonBlank(node.path("url").asText(""), node.path("source_url").asText(""), node.path("landing_page_url").asText(""));
            String snippet = firstNonBlank(node.path("snippet").asText(""), node.path("summary").asText(""), node.path("content").asText(""));
            if (!title.isBlank() && !url.isBlank()) {
                Integer year = node.path("year").canConvertToInt() ? node.path("year").asInt() : null;
                result.add(new ReferenceCandidate(title, List.of("未验证"), year == null || year < 1900 ? query.yearEnd() : year,
                        "", "", "", "", "", url, name(), snippet, query.joinedKeywords(), LocalDateTime.now(),
                        List.of(), 0.65, "PARTIALLY_VERIFIED"));
            }
            node.fields().forEachRemaining(entry -> collectCandidates(entry.getValue(), query, result));
        } else if (node.isArray()) {
            node.forEach(item -> collectCandidates(item, query, result));
        } else if (node.isTextual()) {
            try {
                String text = node.asText();
                if (text.trim().startsWith("[") || text.trim().startsWith("{")) collectCandidates(objectMapper.readTree(text), query, result);
            } catch (Exception ignored) {
            }
        }
    }

    private String normalizeApiKey(String apiKey) {
        if (apiKey == null) return "";
        String value = apiKey.trim();
        return value.regionMatches(true, 0, "Bearer ", 0, 7) ? value.substring(7).trim() : value;
    }

    private String trimRight(String value, String suffix) {
        if (value == null || value.isBlank()) return "";
        String result = value.trim();
        while (result.endsWith(suffix)) result = result.substring(0, result.length() - suffix.length());
        return result;
    }

    private String ensureLeadingSlash(String value) {
        if (value == null || value.isBlank()) return "/responses";
        return value.startsWith("/") ? value : "/" + value;
    }

    private String blankTo(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private String firstNonBlank(String... values) {
        for (String value : values) if (value != null && !value.isBlank()) return value;
        return "";
    }
}
