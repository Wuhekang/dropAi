package com.dropai.rewrite.service.writing;

import com.dropai.rewrite.config.WritingGenerationProperties;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

@Component
public class OpenAlexReferenceSearchProvider implements ReferenceSearchProvider {
    private static final int MAX_HTTP_TIMEOUT_SECONDS = 20;
    private static final int MAX_CONNECT_TIMEOUT_SECONDS = 5;
    private final RestClient restClient;
    private final WritingGenerationProperties properties;

    public OpenAlexReferenceSearchProvider(RestClient.Builder builder, WritingGenerationProperties properties) {
        this.restClient = builder.requestFactory(requestFactory(properties.getReferenceSearch().getTimeoutSeconds())).build();
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
        JsonNode root = restClient.get().uri(buildUrl(query)).retrieve().body(JsonNode.class);
        return candidatesFromResponse(root, query);
    }

    String buildUrl(ReferenceSearchQuery query) {
        String search = URLEncoder.encode(query.providerKeywords(), StandardCharsets.UTF_8);
        List<String> filters = new ArrayList<>();
        String language = targetLanguage(query);
        if (!language.isBlank()) filters.add("language:" + language);
        if (query.yearStart() > 0 || query.yearEnd() > 0) {
            int start = query.yearStart() > 0 ? query.yearStart() : 1900;
            int end = query.yearEnd() > 0 ? query.yearEnd() : 2100;
            filters.add("from_publication_date:" + start + "-01-01");
            filters.add("to_publication_date:" + end + "-12-31");
        }
        String filter = filters.isEmpty() ? "" : "&filter=" + String.join(",", filters);
        return "https://api.openalex.org/works?search=" + search + filter + "&per-page=" + overfetchLimit(query.maxResults());
    }

    List<ReferenceCandidate> candidatesFromResponse(JsonNode root, ReferenceSearchQuery query) {
        List<ReferenceCandidate> result = new ArrayList<>();
        JsonNode items = root == null ? null : root.path("results");
        if (items != null && items.isArray()) {
            for (JsonNode item : items) {
                String title = item.path("title").asText("");
                JsonNode publicationYear = item.path("publication_year");
                Integer year = publicationYear.isIntegralNumber() ? publicationYear.asInt() : null;
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
                String abstractText = readAbstract(item);
                String language = normalizeLanguage(item.path("language").asText(""), title);
                String documentType = documentType(item.path("type").asText(""));
                ReferenceCandidate candidate = new ReferenceCandidate(title, authors, year, source, "", "", "",
                        doi, landing, name(), abstractText, query.providerKeywords(), LocalDateTime.now(),
                        List.of(), score, "VERIFIED", documentType, language, "OTHER_PUBLIC",
                        source, abstractText);
                if (candidate.basicallyVerified()) result.add(candidate);
            }
        }
        return result;
    }

    private String readAbstract(JsonNode item) {
        String direct = item.path("abstract").asText("").trim();
        if (!direct.isBlank()) return direct.replaceAll("\\s+", " ");

        JsonNode inverted = item.path("abstract_inverted_index");
        if (!inverted.isObject()) return "";
        Map<Integer, String> wordsByPosition = new TreeMap<>();
        inverted.fields().forEachRemaining(entry -> {
            if (!entry.getValue().isArray()) return;
            for (JsonNode position : entry.getValue()) {
                if (position.isIntegralNumber()) wordsByPosition.putIfAbsent(position.asInt(), entry.getKey());
            }
        });
        return String.join(" ", wordsByPosition.values()).replaceAll("\\s+", " ").trim();
    }

    private String documentType(String rawType) {
        String type = rawType == null ? "" : rawType.toLowerCase(Locale.ROOT);
        if (type.contains("dissertation") || type.contains("thesis")) return "THESIS";
        if (type.contains("proceedings") || type.contains("conference")) return "CONFERENCE";
        if (type.equals("book") || type.contains("book-chapter") || type.contains("reference-entry")) return "BOOK";
        if (type.contains("report")) return "REPORT";
        if (type.contains("standard")) return "STANDARD";
        if (type.contains("patent")) return "PATENT";
        if (type.contains("dataset") || type.contains("web") || type.contains("posted") || type.contains("preprint")) return "ONLINE";
        return "JOURNAL";
    }

    private String normalizeLanguage(String rawLanguage, String title) {
        String language = rawLanguage == null ? "" : rawLanguage.trim().toLowerCase(Locale.ROOT);
        if (language.equals("zh") || language.startsWith("zh-") || language.equals("zho") || language.equals("chi")) return "zh";
        if (language.equals("en") || language.startsWith("en-") || language.equals("eng")) return "en";
        if (!language.isBlank()) return language;
        return containsHan(title) ? "zh" : "en";
    }

    private String targetLanguage(ReferenceSearchQuery query) {
        if (query.chineseTarget() > 0 && query.englishTarget() == 0) return "zh";
        if (query.englishTarget() > 0 && query.chineseTarget() == 0) return "en";
        return "";
    }

    private int overfetchLimit(int requested) {
        int safeRequested = Math.max(1, requested);
        return Math.min(75, Math.max(safeRequested * 2, safeRequested + 5));
    }

    private boolean containsHan(String value) {
        return value != null && value.codePoints()
                .anyMatch(code -> Character.UnicodeScript.of(code) == Character.UnicodeScript.HAN);
    }

    private SimpleClientHttpRequestFactory requestFactory(int configuredTimeoutSeconds) {
        int readTimeoutSeconds = Math.max(1, Math.min(configuredTimeoutSeconds, MAX_HTTP_TIMEOUT_SECONDS));
        int connectTimeoutSeconds = Math.min(MAX_CONNECT_TIMEOUT_SECONDS, readTimeoutSeconds);
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(connectTimeoutSeconds));
        factory.setReadTimeout(Duration.ofSeconds(readTimeoutSeconds));
        return factory;
    }
}
