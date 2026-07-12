package com.dropai.rewrite.service.writing;

import com.dropai.rewrite.config.WritingGenerationProperties;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Component
public class CrossrefReferenceSearchProvider implements ReferenceSearchProvider {
    private final RestClient restClient;
    private final WritingGenerationProperties properties;

    public CrossrefReferenceSearchProvider(RestClient.Builder builder, WritingGenerationProperties properties) {
        this.restClient = builder.build();
        this.properties = properties;
    }

    @Override
    public String name() {
        return "crossref";
    }

    @Override
    public boolean available() {
        return properties.getReferenceSearch().isEnabled();
    }

    @Override
    public List<ReferenceCandidate> search(ReferenceSearchQuery query) {
        String search = URLEncoder.encode(query.joinedKeywords(), StandardCharsets.UTF_8);
        String url = "https://api.crossref.org/works?query.bibliographic=" + search + "&rows=" + Math.max(1, Math.min(query.maxResults(), 50));
        JsonNode root = restClient.get().uri(url).retrieve().body(JsonNode.class);
        List<ReferenceCandidate> result = new ArrayList<>();
        JsonNode items = root == null ? null : root.path("message").path("items");
        if (items != null && items.isArray()) {
            for (JsonNode item : items) {
                String title = firstText(item.path("title"));
                Integer year = readYear(item);
                if (!within(year, query.yearStart(), query.yearEnd())) continue;
                List<String> authors = new ArrayList<>();
                for (JsonNode author : item.path("author")) {
                    String name = (author.path("given").asText("") + " " + author.path("family").asText("")).trim();
                    if (!name.isBlank()) authors.add(name);
                }
                String pages = item.path("page").asText("");
                ReferenceCandidate candidate = new ReferenceCandidate(title, authors, year, firstText(item.path("container-title")),
                        item.path("volume").asText(""), item.path("issue").asText(""), pages,
                        item.path("DOI").asText(""), item.path("URL").asText(""), name(), item.path("abstract").asText(""),
                        query.joinedKeywords(), LocalDateTime.now(), List.of(), item.path("score").asDouble(0), "VERIFIED");
                if (candidate.basicallyVerified()) result.add(candidate);
            }
        }
        return result;
    }

    private boolean within(Integer year, int start, int end) {
        if (year == null) return false;
        if (start > 0 && year < start) return false;
        return end <= 0 || year <= end;
    }

    private Integer readYear(JsonNode item) {
        JsonNode parts = item.path("published-print").path("date-parts");
        if (!parts.isArray() || parts.isEmpty()) parts = item.path("published-online").path("date-parts");
        if (!parts.isArray() || parts.isEmpty()) parts = item.path("issued").path("date-parts");
        if (parts.isArray() && !parts.isEmpty() && parts.path(0).isArray() && !parts.path(0).isEmpty()) {
            return parts.path(0).path(0).asInt();
        }
        return null;
    }

    private String firstText(JsonNode node) {
        if (node == null || !node.isArray() || node.isEmpty()) return "";
        return node.path(0).asText("");
    }
}
