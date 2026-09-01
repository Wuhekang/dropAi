package com.dropai.rewrite.service.writing;

import com.dropai.rewrite.config.WritingGenerationProperties;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.util.HtmlUtils;
import org.springframework.web.client.RestClient;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Component
public class CrossrefReferenceSearchProvider implements ReferenceSearchProvider {
    private static final int MAX_HTTP_TIMEOUT_SECONDS = 20;
    private static final int MAX_CONNECT_TIMEOUT_SECONDS = 5;
    private final RestClient restClient;
    private final WritingGenerationProperties properties;

    public CrossrefReferenceSearchProvider(RestClient.Builder builder, WritingGenerationProperties properties) {
        this.restClient = builder.requestFactory(requestFactory(properties.getReferenceSearch().getTimeoutSeconds())).build();
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
        JsonNode root = restClient.get().uri(buildUrl(query)).retrieve().body(JsonNode.class);
        return candidatesFromResponse(root, query);
    }

    String buildUrl(ReferenceSearchQuery query) {
        String search = URLEncoder.encode(query.providerKeywords(), StandardCharsets.UTF_8);
        List<String> filters = new ArrayList<>();
        if (query.yearStart() > 0) filters.add("from-pub-date:" + query.yearStart() + "-01-01");
        if (query.yearEnd() > 0) filters.add("until-pub-date:" + query.yearEnd() + "-12-31");
        String filter = filters.isEmpty() ? "" : "&filter=" + String.join(",", filters);
        return "https://api.crossref.org/works?query.bibliographic=" + search + filter
                + "&rows=" + overfetchLimit(query.maxResults());
    }

    List<ReferenceCandidate> candidatesFromResponse(JsonNode root, ReferenceSearchQuery query) {
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
                String container = firstText(item.path("container-title"));
                String abstractText = cleanAbstract(item.path("abstract").asText(""));
                String language = normalizeLanguage(item.path("language").asText(""), title);
                String documentType = documentType(item.path("type").asText(""));
                String doi = item.path("DOI").asText("");
                ReferenceCandidate candidate = new ReferenceCandidate(title, authors, year, container,
                        item.path("volume").asText(""), item.path("issue").asText(""), pages,
                        doi, item.path("URL").asText(""), name(), abstractText,
                        query.providerKeywords(), LocalDateTime.now(), List.of(), item.path("score").asDouble(0), "VERIFIED",
                        documentType, language, doi.isBlank() ? "OTHER_PUBLIC" : "DOI_PAGE", container, abstractText);
                if (candidate.basicallyVerified()) result.add(candidate);
            }
        }
        return result;
    }

    private String cleanAbstract(String value) {
        if (value == null || value.isBlank()) return "";
        String withoutMarkup = value.replaceAll("(?s)<[^>]+>", " ");
        return HtmlUtils.htmlUnescape(withoutMarkup).replaceAll("\\s+", " ").trim();
    }

    private String documentType(String rawType) {
        String type = rawType == null ? "" : rawType.toLowerCase(Locale.ROOT);
        if (type.contains("dissertation")) return "THESIS";
        if (type.contains("proceedings")) return "CONFERENCE";
        if (type.equals("book") || type.contains("book-chapter") || type.contains("reference-entry")) return "BOOK";
        if (type.contains("report")) return "REPORT";
        if (type.contains("standard")) return "STANDARD";
        if (type.contains("patent")) return "PATENT";
        if (type.contains("dataset") || type.contains("posted") || type.contains("component")) return "ONLINE";
        return "JOURNAL";
    }

    private String normalizeLanguage(String rawLanguage, String title) {
        String language = rawLanguage == null ? "" : rawLanguage.trim().toLowerCase(Locale.ROOT);
        if (language.equals("zh") || language.startsWith("zh-") || language.equals("zho") || language.equals("chi")) return "zh";
        if (language.equals("en") || language.startsWith("en-") || language.equals("eng")) return "en";
        if (!language.isBlank()) return language;
        return containsHan(title) ? "zh" : "en";
    }

    private int overfetchLimit(int requested) {
        int safeRequested = Math.max(1, requested);
        return Math.min(75, Math.max(safeRequested * 2, safeRequested + 5));
    }

    private boolean containsHan(String value) {
        return value != null && value.codePoints()
                .anyMatch(code -> Character.UnicodeScript.of(code) == Character.UnicodeScript.HAN);
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

    private SimpleClientHttpRequestFactory requestFactory(int configuredTimeoutSeconds) {
        int readTimeoutSeconds = Math.max(1, Math.min(configuredTimeoutSeconds, MAX_HTTP_TIMEOUT_SECONDS));
        int connectTimeoutSeconds = Math.min(MAX_CONNECT_TIMEOUT_SECONDS, readTimeoutSeconds);
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(connectTimeoutSeconds));
        factory.setReadTimeout(Duration.ofSeconds(readTimeoutSeconds));
        return factory;
    }
}
