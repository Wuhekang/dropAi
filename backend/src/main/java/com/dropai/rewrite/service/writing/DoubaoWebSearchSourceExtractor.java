package com.dropai.rewrite.service.writing;

import com.fasterxml.jackson.databind.JsonNode;

import java.net.InetAddress;
import java.net.URI;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class DoubaoWebSearchSourceExtractor {
    private static final Pattern HTTP_URL = Pattern.compile("https?://[^\\s\\]})>\"']+");

    public ExtractionResult extract(JsonNode root) {
        ExtractionState state = new ExtractionState();
        walk(root, "$", null, state);
        return new ExtractionResult(state.toolInvoked, new ArrayList<>(state.sources.values()), new ArrayList<>(state.rejectedUrls.values()));
    }

    public StructureDiagnostics diagnose(JsonNode root) {
        DiagnosticState state = new DiagnosticState();
        diagnose(root, "$", null, state);
        return new StructureDiagnostics(state.topLevelFields, state.outputCount, state.outputTypes,
                state.contentTypes, state.annotationCount, state.webSearchCallCount, state.actionCount,
                state.sourceCount, state.citationCount, state.urlLikeFieldPaths, state.httpTextPaths);
    }

    private void walk(JsonNode node, String path, String fieldName, ExtractionState state) {
        if (node == null || node.isNull() || node.isMissingNode()) return;

        String lowerField = fieldName == null ? "" : fieldName.toLowerCase(Locale.ROOT);
        if (isToolField(lowerField) || (node.isObject() && isToolType(node.path("type").asText("")))) {
            state.toolInvoked = true;
        }

        if (node.isObject()) {
            node.fields().forEachRemaining(entry -> {
                if (isUrlLikeField(entry.getKey().toLowerCase(Locale.ROOT)) && entry.getValue().isTextual()) {
                    addUrl(entry.getValue().asText(), node, path + "." + entry.getKey(), state);
                }
                walk(entry.getValue(), path + "." + entry.getKey(), entry.getKey(), state);
            });
            return;
        }

        if (node.isArray()) {
            for (int i = 0; i < node.size(); i++) {
                walk(node.get(i), path + "[" + i + "]", fieldName, state);
            }
            return;
        }

        if (node.isTextual()) {
            Matcher matcher = HTTP_URL.matcher(node.asText(""));
            while (matcher.find()) {
                addUrl(cleanUrl(matcher.group()), null, path, state);
            }
        }
    }

    private void diagnose(JsonNode node, String path, String fieldName, DiagnosticState state) {
        if (node == null || node.isNull() || node.isMissingNode()) return;
        String lowerField = fieldName == null ? "" : fieldName.toLowerCase(Locale.ROOT);
        if ("$".equals(path) && node.isObject()) {
            node.fieldNames().forEachRemaining(state.topLevelFields::add);
        }
        if (node.isObject()) {
            String type = node.path("type").asText("");
            if (path.matches("\\$\\.output\\[\\d+\\]")) {
                state.outputCount++;
                state.outputTypes.add(path + ".type=" + type);
            }
            if (path.contains(".content[") && !type.isBlank()) {
                state.contentTypes.add(path + ".type=" + type);
            }
            if (isToolType(type)) state.webSearchCallCount++;
            if (node.has("action")) state.actionCount++;
            if (node.has("annotations") && node.path("annotations").isArray()) {
                state.annotationCount += node.path("annotations").size();
            }
            if (node.has("sources") && node.path("sources").isArray()) {
                state.sourceCount += node.path("sources").size();
            }
            if (node.has("citations") && node.path("citations").isArray()) {
                state.citationCount += node.path("citations").size();
            }
            node.fields().forEachRemaining(entry -> diagnose(entry.getValue(), path + "." + entry.getKey(), entry.getKey(), state));
            return;
        }
        if (node.isArray()) {
            for (int i = 0; i < node.size(); i++) {
                diagnose(node.get(i), path + "[" + i + "]", fieldName, state);
            }
            return;
        }
        if (node.isTextual()) {
            if (isUrlLikeField(lowerField)) {
                state.urlLikeFieldPaths.add(path + "=" + cleanUrl(node.asText("")));
            }
            if (node.asText("").contains("http://") || node.asText("").contains("https://")) {
                state.httpTextPaths.add(path);
            }
        }
    }

    private boolean isToolField(String field) {
        return field.contains("web_search_call")
                || field.contains("tool_call")
                || field.contains("tool_use")
                || field.contains("search_results");
    }

    private boolean isToolType(String type) {
        String value = type == null ? "" : type.toLowerCase(Locale.ROOT);
        return value.contains("web_search") || value.contains("tool_call") || value.contains("tool_use");
    }

    private boolean isUrlLikeField(String field) {
        return "url".equals(field) || "uri".equals(field) || "link".equals(field) || "href".equals(field);
    }

    private void addUrl(String rawUrl, JsonNode parent, String rawPath, ExtractionState state) {
        String url = cleanUrl(rawUrl);
        if (url.isBlank()) return;
        UrlValidation validation = validatePublicUrl(url);
        if (!validation.accepted()) {
            state.rejectedUrls.putIfAbsent(url, new RejectedUrl(url, validation.reason(), rawPath));
            return;
        }
        state.sources.putIfAbsent(url, new SourceEvidence(url,
                text(parent, "title", "name", "sourceTitle"),
                text(parent, "snippet", "sourceSnippet", "summary", "content", "text"),
                domain(url),
                text(parent, "query", "search_query"),
                text(parent, "sourceType", "type"),
                rawPath));
    }

    public UrlValidation validatePublicUrl(String url) {
        try {
            if (url == null || url.isBlank()) return new UrlValidation(false, "blank");
            URI uri = URI.create(url.trim());
            String scheme = uri.getScheme();
            if (!"http".equalsIgnoreCase(scheme) && !"https".equalsIgnoreCase(scheme)) {
                return new UrlValidation(false, "unsupported_scheme");
            }
            String host = uri.getHost();
            if (host == null || host.isBlank()) return new UrlValidation(false, "missing_host");
            String lower = host.toLowerCase(Locale.ROOT);
            if ("localhost".equals(lower) || lower.endsWith(".localhost")) {
                return new UrlValidation(false, "localhost");
            }
            if (lower.endsWith(".local") || lower.endsWith(".internal") || lower.endsWith(".lan")) {
                return new UrlValidation(false, "internal_domain");
            }
            if (isIpLiteral(lower)) {
                InetAddress address = InetAddress.getByName(lower);
                if (address.isAnyLocalAddress() || address.isLoopbackAddress() || address.isLinkLocalAddress()
                        || address.isSiteLocalAddress() || address.isMulticastAddress()) {
                    return new UrlValidation(false, "private_ip");
                }
            }
            return new UrlValidation(true, "");
        } catch (Exception exception) {
            return new UrlValidation(false, "invalid_url");
        }
    }

    private boolean isIpLiteral(String host) {
        return host.matches("\\d+\\.\\d+\\.\\d+\\.\\d+") || host.contains(":");
    }

    private String cleanUrl(String value) {
        if (value == null) return "";
        return value.trim().replaceAll("[,.;，。；）)]+$", "");
    }

    private String domain(String url) {
        try {
            String host = URI.create(url).getHost();
            return host == null ? "" : host;
        } catch (Exception ignored) {
            return "";
        }
    }

    private String text(JsonNode node, String... fields) {
        if (node == null || !node.isObject()) return "";
        for (String field : fields) {
            String value = node.path(field).asText("");
            if (!value.isBlank()) return value.trim();
        }
        return "";
    }

    private static class ExtractionState {
        boolean toolInvoked;
        Map<String, SourceEvidence> sources = new LinkedHashMap<>();
        Map<String, RejectedUrl> rejectedUrls = new LinkedHashMap<>();
    }

    private static class DiagnosticState {
        List<String> topLevelFields = new ArrayList<>();
        int outputCount;
        List<String> outputTypes = new ArrayList<>();
        List<String> contentTypes = new ArrayList<>();
        int annotationCount;
        int webSearchCallCount;
        int actionCount;
        int sourceCount;
        int citationCount;
        List<String> urlLikeFieldPaths = new ArrayList<>();
        List<String> httpTextPaths = new ArrayList<>();
    }

    public record ExtractionResult(boolean toolInvoked, List<SourceEvidence> sources, List<RejectedUrl> rejectedUrls) {
        public boolean hasAcceptedSources() {
            return !sources.isEmpty();
        }

        public boolean hasRejectedUrls() {
            return !rejectedUrls.isEmpty();
        }
    }

    public record SourceEvidence(String url, String title, String snippet, String domain, String query,
                                 String sourceType, String rawPath) {
    }

    public record RejectedUrl(String url, String reason, String rawPath) {
    }

    public record UrlValidation(boolean accepted, String reason) {
    }

    public record StructureDiagnostics(List<String> topLevelFields, int outputCount, List<String> outputTypes,
                                       List<String> contentTypes, int annotationCount, int webSearchCallCount,
                                       int actionCount, int sourceCount, int citationCount,
                                       List<String> urlLikeFieldPaths, List<String> httpTextPaths) {
    }
}
