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
public class OpenAlexReferenceSearchProvider implements ReferenceSearchProvider {
    private final RestClient restClient;
    private final WritingGenerationProperties properties;

    public OpenAlexReferenceSearchProvider(RestClient.Builder builder, WritingGenerationProperties properties) {
        this.restClient = builder.build();
        this.properties = properties;
    }

    @Override
    public String name() {
        return "openalex";
    }

    @Override
    public boolean available() {
        return properties.getReferenceSearch().isEnabled();
    }

    @Override
    public List<ReferenceCandidate> search(ReferenceSearchQuery query) {
        String search = URLEncoder.encode(query.joinedKeywords(), StandardCharsets.UTF_8);
        String filter = "";
        if (query.yearStart() > 0 || query.yearEnd() > 0) {
            int start = query.yearStart() > 0 ? query.yearStart() : 1900;
            int end = query.yearEnd() > 0 ? query.yearEnd() : 2100;
            filter = "&filter=from_publication_date:" + start + "-01-01,to_publication_date:" + end + "-12-31";
        }
        String url = "https://api.openalex.org/works?search=" + search + filter + "&per-page=" + Math.max(1, Math.min(query.maxResults(), 50));
        JsonNode root = restClient.get().uri(url).retrieve().body(JsonNode.class);
        List<ReferenceCandidate> result = new ArrayList<>();
        JsonNode items = root == null ? null : root.path("results");
        if (items != null && items.isArray()) {
            for (JsonNode item : items) {
                String title = item.path("title").asText("");
                Integer year = item.path("publication_year").isMissingNode() ? null : item.path("publication_year").asInt();
                List<String> authors = new ArrayList<>();
                for (JsonNode authorship : item.path("authorships")) {
                    String name = authorship.path("author").path("display_name").asText("");
                    if (!name.isBlank()) authors.add(name);
                }
                String doi = item.path("doi").asText("");
                if (doi.startsWith("https://doi.org/")) doi = doi.substring("https://doi.org/".length());
                String source = item.path("primary_location").path("source").path("display_name").asText("");
                String landing = item.path("primary_location").path("landing_page_url").asText(item.path("id").asText(""));
                double score = item.path("relevance_score").asDouble(0);
                ReferenceCandidate candidate = new ReferenceCandidate(title, authors, year, source, "", "", "",
                        doi, landing, name(), item.path("abstract").asText(""), query.joinedKeywords(), LocalDateTime.now(),
                        List.of(), score, "VERIFIED");
                if (candidate.basicallyVerified()) result.add(candidate);
            }
        }
        return result;
    }
}
