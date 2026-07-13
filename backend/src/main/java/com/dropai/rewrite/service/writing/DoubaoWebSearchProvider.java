package com.dropai.rewrite.service.writing;

import com.dropai.rewrite.config.DoubaoProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.net.InetAddress;
import java.net.URI;
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
    public String providerCode() {
        return "doubao_web";
    }

    @Override
    public String providerName() {
        return "Doubao Responses API Web Search";
    }

    @Override
    public boolean supportsLanguage(String language) {
        return language == null || language.isBlank() || "ZH".equalsIgnoreCase(language) || "EN".equalsIgnoreCase(language);
    }

    @Override
    public boolean available() {
        return properties.isWebSearchEnabled()
                && properties.isEnabled()
                && !isBlank(properties.getApiKey())
                && !isBlank(properties.getWebSearchModel());
    }

    @Override
    public ProviderHealthStatus healthCheck() {
        long started = System.currentTimeMillis();
        String endpoint = endpoint();
        if (!properties.isWebSearchEnabled()) {
            return status(false, false, false, endpoint, "DISABLED", "Doubao Web Search is disabled", elapsed(started));
        }
        if (isBlank(properties.getApiKey())) {
            return status(true, false, false, endpoint, "MISSING_API_KEY", "DOUBAO_API_KEY is not configured", elapsed(started));
        }
        if (isBlank(properties.getWebSearchModel())) {
            return status(true, false, false, endpoint, "MISSING_WEB_SEARCH_MODEL", "DOUBAO_WEB_SEARCH_MODEL is not configured", elapsed(started));
        }
        try {
            ReferenceSearchQuery probe = new ReferenceSearchQuery("health",
                    "中华人民共和国国家标准GB/T 7714-2025实施日期", "reference standard",
                    List.of("GB/T 7714"), List.of(), 2024, 2026, 3, 3, 0);
            List<ReferenceCandidate> found = search(probe);
            boolean hasUrl = found.stream().anyMatch(candidate -> isSafePublicUrl(candidate.url()));
            return status(true, true, hasUrl, endpoint, hasUrl ? "AVAILABLE" : "NO_SOURCE_URL",
                    hasUrl ? "Responses API Web Search returned public source URLs"
                            : "Responses API responded but no public source URL was found",
                    elapsed(started));
        } catch (Exception exception) {
            return status(true, true, false, endpoint, exception.getClass().getSimpleName(),
                    "Responses API Web Search check failed: " + safeMessage(exception), elapsed(started));
        }
    }

    @Override
    public List<ReferenceCandidate> search(ReferenceSearchQuery query) {
        if (!available()) return List.of();
        Map<String, Object> request = Map.of(
                "model", properties.getWebSearchModel(),
                "stream", false,
                "max_output_tokens", Math.max(1024, properties.getMaxOutputTokens()),
                "tools", List.of(Map.of("type", "web_search")),
                "tool_choice", Map.of("type", "web_search"),
                "input", webSearchPrompt(query)
        );
        JsonNode root = restClient.post()
                .uri(endpoint())
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + normalizeApiKey(properties.getApiKey()))
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .retrieve()
                .body(JsonNode.class);
        return parseCandidates(root, query).stream()
                .filter(candidate -> isSafePublicUrl(candidate.url()))
                .filter(ReferenceCandidate::basicallyVerified)
                .limit(Math.max(1, properties.getWebSearchMaxResults()))
                .toList();
    }

    private String webSearchPrompt(ReferenceSearchQuery query) {
        return """
                You must use the web_search tool. Search only public bibliographic pages, CNKI public catalog pages,
                CNKI journal portal pages, journal official sites, university journal pages, publisher pages, DOI pages,
                and other public publication pages. Do not log in. Do not bypass captcha. Do not download PDF, CAJ, or full text.
                Do not invent authors, years, journals, DOI, volume, issue, or pages. Return only a JSON array.
                Every item must have a real public URL; drop any item without a URL.

                Paper title: %s
                Field: %s
                Year range: %d-%d
                Search keywords: %s

                JSON item fields:
                title, authors, year, journalOrPublisher, volume, issue, pages, doi, url,
                documentType, language, abstractText, keywords, sourceTitle, sourceSnippet, sourceType.
                sourceType must be one of CNKI_PUBLIC_PAGE, CNKI_JOURNAL_PORTAL, JOURNAL_OFFICIAL, UNIVERSITY,
                PUBLISHER, DOI_PAGE, OTHER_PUBLIC.
                """.formatted(query.title(), query.major(), query.yearStart(), query.yearEnd(), query.joinedChineseKeywords());
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
            String snippet = firstNonBlank(node.path("sourceSnippet").asText(""), node.path("snippet").asText(""),
                    node.path("summary").asText(""), node.path("content").asText(""));
            if (!title.isBlank() && isSafePublicUrl(url)) {
                Integer year = node.path("year").canConvertToInt() ? node.path("year").asInt() : null;
                if (year == null) year = node.path("publicationYear").canConvertToInt() ? node.path("publicationYear").asInt() : null;
                List<String> authors = authors(node.path("authors"));
                String container = firstNonBlank(node.path("journalOrPublisher").asText(""), node.path("journal").asText(""),
                        node.path("source").asText(""), node.path("publisher").asText(""));
                String sourceType = firstNonBlank(node.path("sourceType").asText(""), classifySource(url));
                String verificationStatus = isPrimaryPublicSource(sourceType) ? "VERIFIED_PRIMARY_PUBLIC" : "PARTIALLY_VERIFIED";
                result.add(new ReferenceCandidate(title, authors, year, container,
                        node.path("volume").asText(""), node.path("issue").asText(""), node.path("pages").asText(""),
                        node.path("doi").asText(""), url, "DOUBAO_WEB_SEARCH",
                        firstNonBlank(node.path("abstractText").asText(""), snippet), query.joinedKeywords(),
                        LocalDateTime.now(), List.of(), 0.70, verificationStatus,
                        firstNonBlank(node.path("documentType").asText(""), "JOURNAL"),
                        firstNonBlank(node.path("language").asText(""), "zh"),
                        sourceType, firstNonBlank(node.path("sourceTitle").asText(""), title), snippet));
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

    private List<String> authors(JsonNode node) {
        List<String> result = new ArrayList<>();
        if (node == null || node.isMissingNode() || node.isNull()) return result;
        if (node.isArray()) {
            node.forEach(item -> {
                String value = item.asText("").trim();
                if (!value.isBlank()) result.add(value);
            });
            return result;
        }
        for (String part : node.asText("").split("[,;；，、]+")) {
            String value = part.trim();
            if (!value.isBlank()) result.add(value);
        }
        return result;
    }

    private ProviderHealthStatus status(boolean enabled, boolean configured, boolean available, String endpoint,
                                        String errorCode, String message, long latencyMs) {
        return ProviderHealthStatus.of(providerCode(), enabled, configured, available, "BEARER_API_KEY",
                maskModel(properties.getWebSearchModel()), "RESPONSES_API", properties.isWebSearchEnabled(),
                endpoint, errorCode, message, latencyMs);
    }

    private String classifySource(String url) {
        try {
            String host = URI.create(url).getHost();
            if (host == null) return "OTHER_PUBLIC";
            host = host.toLowerCase();
            if (host.contains("kns.cnki.net")) return "CNKI_PUBLIC_PAGE";
            if (host.contains("cbpt.cnki.net") || host.contains("cnki.net")) return "CNKI_JOURNAL_PORTAL";
            if (host.contains("doi.org")) return "DOI_PAGE";
            if (host.endsWith(".edu.cn") || host.contains(".edu.cn")) return "UNIVERSITY";
            return "OTHER_PUBLIC";
        } catch (Exception ignored) {
            return "OTHER_PUBLIC";
        }
    }

    private boolean isPrimaryPublicSource(String sourceType) {
        return "CNKI_PUBLIC_PAGE".equalsIgnoreCase(sourceType)
                || "CNKI_JOURNAL_PORTAL".equalsIgnoreCase(sourceType)
                || "JOURNAL_OFFICIAL".equalsIgnoreCase(sourceType);
    }

    public boolean isSafePublicUrl(String url) {
        try {
            if (url == null || url.isBlank()) return false;
            URI uri = URI.create(url.trim());
            String scheme = uri.getScheme();
            if (!"http".equalsIgnoreCase(scheme) && !"https".equalsIgnoreCase(scheme)) return false;
            String host = uri.getHost();
            if (host == null || host.isBlank()) return false;
            String lower = host.toLowerCase();
            if ("localhost".equals(lower) || lower.endsWith(".localhost")) return false;
            InetAddress address = InetAddress.getByName(host);
            return !(address.isAnyLocalAddress() || address.isLoopbackAddress() || address.isLinkLocalAddress()
                    || address.isSiteLocalAddress() || address.isMulticastAddress());
        } catch (Exception ignored) {
            return false;
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

    private String endpoint() {
        return trimRight(blankTo(properties.getResponsesBaseUrl(), properties.getBaseUrl()), "/") + ensureLeadingSlash(properties.getResponsesPath());
    }

    private String blankTo(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private String firstNonBlank(String... values) {
        for (String value : values) if (value != null && !value.isBlank()) return value.trim();
        return "";
    }

    private String maskModel(String model) {
        if (model == null || model.isBlank()) return "";
        if (model.length() <= 8) return "***";
        return model.substring(0, Math.min(6, model.length())) + "***" + model.substring(model.length() - 4);
    }

    private String safeMessage(Exception exception) {
        String message = exception.getMessage();
        if (message == null) return "";
        return message.replaceAll("(?i)Bearer\\s+[A-Za-z0-9._\\-]+", "Bearer ***");
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private long elapsed(long started) {
        return System.currentTimeMillis() - started;
    }
}
