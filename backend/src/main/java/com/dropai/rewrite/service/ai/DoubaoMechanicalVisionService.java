package com.dropai.rewrite.service.ai;

import com.dropai.rewrite.config.DoubaoProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StreamUtils;
import org.springframework.web.client.RestClient;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.Map;

@Service
public class DoubaoMechanicalVisionService {
    private static final long MAX_IMAGE_BYTES = 8L * 1024L * 1024L;

    private final DoubaoProperties properties;
    private final DoubaoModelRouter modelRouter;
    private final ObjectMapper objectMapper;
    private final RestClient restClient;

    public DoubaoMechanicalVisionService(
            DoubaoProperties properties,
            DoubaoModelRouter modelRouter,
            ObjectMapper objectMapper,
            RestClient.Builder restClientBuilder
    ) {
        this.properties = properties;
        this.modelRouter = modelRouter;
        this.objectMapper = objectMapper;
        this.restClient = restClientBuilder
                .requestFactory(requestFactory(properties))
                .build();
    }

    public MechanicalVisionResponse analyze(byte[] imageBytes, String fileName, String prompt) throws Exception {
        String model = modelRouter.resolveModel(AiRequestType.MECHANICAL_VISION);
        String imageUrl = buildImageDataUrl(imageBytes, fileName);
        Map<String, Object> requestBody = requestBody(model, imageUrl, prompt);
        String response = postWithRetry(normalizeApiKey(properties.getApiKey()), requestBody, model);
        MechanicalVisionAnalysisResult result = parseResult(parseContent(response));
        return new MechanicalVisionResponse(
                model,
                properties.getEndpoint(),
                true,
                result,
                response == null ? 0 : response.length()
        );
    }

    public String buildImageDataUrl(byte[] imageBytes, String fileName) {
        validateImage(imageBytes, fileName);
        String mimeType = mimeType(fileName);
        return "data:" + mimeType + ";base64," + Base64.getEncoder().encodeToString(imageBytes);
    }

    public Map<String, Object> diagnosticConfig() {
        return Map.of(
                "endpoint", properties.getEndpoint(),
                "textModel", modelRouter.currentTextModel(),
                "mechanicalVisionModel", modelRouter.resolveModel(AiRequestType.MECHANICAL_VISION),
                "apiKey", normalizeApiKey(properties.getApiKey()).isBlank() ? "missing" : "configured"
        );
    }

    private Map<String, Object> requestBody(String model, String imageUrl, String prompt) {
        String finalPrompt = isBlank(prompt) ? defaultPrompt() : prompt.trim();
        return Map.of(
                "model", model,
                "stream", false,
                "temperature", 0.1,
                "messages", List.of(Map.of(
                        "role", "user",
                        "content", List.of(
                                Map.of("type", "text", "text", finalPrompt + "\n\nReturn strict JSON only."),
                                Map.of("type", "image_url", "image_url", Map.of("url", imageUrl))
                        )
                ))
        );
    }

    private String postWithRetry(String apiKey, Map<String, Object> requestBody, String model) {
        if (apiKey.isBlank()) {
            throw new IllegalStateException("Missing DOUBAO_API_KEY for mechanical vision request");
        }
        int maxAttempts = Math.max(1, properties.getMaxRetries() + 1);
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                byte[] response = restClient.post()
                        .uri(properties.getEndpoint())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(requestBody)
                        .exchange((request, clientResponse) -> {
                            byte[] body = StreamUtils.copyToByteArray(clientResponse.getBody());
                            int status = clientResponse.getStatusCode().value();
                            if (clientResponse.getStatusCode().isError()) {
                                throw new DoubaoVisionHttpException(status, new String(body, StandardCharsets.UTF_8));
                            }
                            return body;
                        });
                return response == null ? "" : new String(response, StandardCharsets.UTF_8);
            } catch (DoubaoVisionHttpException exception) {
                if (stopRetry(exception.statusCode()) || attempt == maxAttempts) {
                    throw diagnosticException(exception, model);
                }
                sleepQuietly(backoffMillis(attempt));
            }
        }
        throw new IllegalStateException("Mechanical vision request exhausted retry attempts");
    }

    private boolean stopRetry(int status) {
        return status == 401 || status == 403 || status == 404;
    }

    private IllegalStateException diagnosticException(DoubaoVisionHttpException exception, String model) {
        String body = compact(exception.responseBody());
        String code = errorField(exception.responseBody(), "code");
        String message = errorField(exception.responseBody(), "message");
        return new IllegalStateException("""
                Mechanical vision model call failed:
                model=%s
                endpoint=%s
                status=%d
                errorCode=%s
                errorMessage=%s
                response=%s
                apiKey=%s
                Check Ark model permission, endpoint, Render/Windows env vars, and backend restart.
                """.formatted(
                model,
                properties.getEndpoint(),
                exception.statusCode(),
                code,
                message,
                body,
                normalizeApiKey(properties.getApiKey()).isBlank() ? "missing" : "configured"
        ), exception);
    }

    private String parseContent(String response) throws Exception {
        if (isBlank(response)) {
            throw new IllegalStateException("Mechanical vision response is empty");
        }
        JsonNode root = objectMapper.readTree(response);
        String content = root.path("choices").path(0).path("message").path("content").asText("");
        if (isBlank(content)) {
            throw new IllegalStateException("Mechanical vision response content is empty");
        }
        return content;
    }

    private MechanicalVisionAnalysisResult parseResult(String content) throws Exception {
        String json = extractJson(content);
        MechanicalVisionAnalysisResult result = objectMapper.readValue(json, MechanicalVisionAnalysisResult.class);
        result.applyDefaults();
        return result;
    }

    private String extractJson(String value) {
        String text = value == null ? "" : value.trim();
        if (text.startsWith("```")) {
            text = text.replaceFirst("^```(?:json)?\\s*", "").replaceFirst("\\s*```$", "").trim();
        }
        int start = text.indexOf('{');
        int end = text.lastIndexOf('}');
        if (start < 0 || end <= start) {
            throw new IllegalStateException("Mechanical vision response is not JSON: " + compact(value));
        }
        return text.substring(start, end + 1);
    }

    private void validateImage(byte[] imageBytes, String fileName) {
        if (imageBytes == null || imageBytes.length == 0) {
            throw new IllegalArgumentException("Mechanical vision image is empty");
        }
        if (imageBytes.length > MAX_IMAGE_BYTES) {
            throw new IllegalArgumentException("Mechanical vision image is too large, max 8MB");
        }
        if ("application/octet-stream".equals(mimeType(fileName))) {
            throw new IllegalArgumentException("Unsupported mechanical vision image type, only png/jpg/jpeg/webp are allowed");
        }
    }

    private String mimeType(String fileName) {
        String lower = fileName == null ? "" : fileName.toLowerCase();
        if (lower.endsWith(".png")) return "image/png";
        if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) return "image/jpeg";
        if (lower.endsWith(".webp")) return "image/webp";
        return "application/octet-stream";
    }

    private String defaultPrompt() {
        return """
                Analyze this mechanical design image, drawing, assembly, part, or CAD preview.
                Return only JSON with fields:
                imageType, equipmentName, components, views, dimensions, assemblyRelations,
                detectedAnnotations, missingComponents, drawingProblems, assemblyProblems,
                qualityScore, evidence, warnings.
                """;
    }

    private String normalizeApiKey(String value) {
        return value == null ? "" : value.replaceAll("[\\r\\n\\t ]", "").trim();
    }

    private String errorField(String responseBody, String field) {
        try {
            JsonNode root = objectMapper.readTree(responseBody == null ? "" : responseBody);
            JsonNode value = root.path("error").path(field);
            if (value.isMissingNode() || value.asText("").isBlank()) {
                value = root.path(field);
            }
            return value.asText("unknown");
        } catch (Exception ignored) {
            return "unknown";
        }
    }

    private SimpleClientHttpRequestFactory requestFactory(DoubaoProperties properties) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(properties.getConnectTimeoutSeconds()));
        factory.setReadTimeout(Duration.ofSeconds(properties.getReadTimeoutSeconds()));
        return factory;
    }

    private long backoffMillis(int attempt) {
        return Math.min(15000L, 1000L * (1L << Math.max(0, attempt - 1)));
    }

    private void sleepQuietly(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Mechanical vision retry interrupted", exception);
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private String compact(String value) {
        if (isBlank(value)) {
            return "none";
        }
        String compacted = value.replaceAll("\\s+", " ").trim();
        return compacted.length() > 500 ? compacted.substring(0, 500) + "..." : compacted;
    }

    public record MechanicalVisionResponse(
            String model,
            String endpoint,
            boolean requestContainsImage,
            MechanicalVisionAnalysisResult result,
            int rawResponseLength
    ) {}

    private static class DoubaoVisionHttpException extends RuntimeException {
        private final int statusCode;
        private final String responseBody;

        DoubaoVisionHttpException(int statusCode, String responseBody) {
            super("HTTP " + statusCode);
            this.statusCode = statusCode;
            this.responseBody = responseBody;
        }

        int statusCode() {
            return statusCode;
        }

        String responseBody() {
            return responseBody;
        }
    }
}
