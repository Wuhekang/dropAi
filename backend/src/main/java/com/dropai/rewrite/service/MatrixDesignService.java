package com.dropai.rewrite.service;

import com.dropai.rewrite.config.DoubaoProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StreamUtils;
import org.springframework.web.client.RestClient;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.stream.StreamSupport;

@Service
public class MatrixDesignService {
    private static final Logger log = LoggerFactory.getLogger(MatrixDesignService.class);
    private final DoubaoProperties properties;
    private final ObjectMapper objectMapper;
    private final RestClient restClient;

    public MatrixDesignService(DoubaoProperties properties, ObjectMapper objectMapper, RestClient.Builder builder) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(properties.getConnectTimeoutSeconds()));
        factory.setReadTimeout(Duration.ofSeconds(properties.getReadTimeoutSeconds()));
        this.restClient = builder.requestFactory(factory).build();
    }

    public String generate(String instructions, String input) {
        if (!properties.isEnabled()) throw new IllegalStateException("大模型设计生成服务已关闭");
        String apiKey = normalize(properties.getApiKey());
        if (apiKey.isBlank()) throw new IllegalStateException("未配置 DOUBAO_API_KEY，无法调用豆包大模型服务");
        Map<String, Object> body = Map.of(
                "model", properties.getModel(),
                "stream", false,
                "messages", List.of(
                        Map.of("role", "system", "content", instructions),
                        Map.of("role", "user", "content", input)
                )
        );
        String lastError = "";
        for (int attempt = 1; attempt <= 3; attempt++) {
            try {
                byte[] response = restClient.post()
                        .uri(properties.getEndpoint())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(body)
                        .exchange((request, result) -> {
                            byte[] bytes = StreamUtils.copyToByteArray(result.getBody());
                            if (result.getStatusCode().isError()) {
                                throw new ModelRequestException(result.getStatusCode().value(), compact(new String(bytes, StandardCharsets.UTF_8)));
                            }
                            return bytes;
                        });
                String parsed = parseText(response == null ? "" : new String(response, StandardCharsets.UTF_8));
                if (!parsed.isBlank()) return parsed;
                lastError = "大模型连续返回空内容";
            } catch (Exception exception) {
                lastError = friendlyMessage(exception);
                log.warn("豆包设计模型请求失败 attempt={}/3 endpoint={} reason={}", attempt, properties.getEndpoint(), lastError);
            }
            if (attempt < 3) sleep(attempt);
        }
        throw new IllegalStateException(lastError.isBlank() ? "大模型接口请求失败，请稍后重试。" : lastError);
    }

    public boolean apiKeyConfigured() { return !normalize(properties.getApiKey()).isBlank(); }
    public String modelName() { return properties.getModel(); }
    public String endpoint() { return properties.getEndpoint(); }

    public List<String> availableModels() {
        String apiKey = normalize(properties.getApiKey());
        if (apiKey.isBlank()) throw new IllegalStateException("未配置 DOUBAO_API_KEY");
        try {
            String response = restClient.get()
                    .uri(properties.getEndpoint().replaceFirst("/chat/completions/?$", "/models"))
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                    .retrieve().body(String.class);
            JsonNode root = objectMapper.readTree(response);
            return StreamSupport.stream(root.path("data").spliterator(), false)
                    .map(model -> model.path("id").asText("")).filter(id -> !id.isBlank()).sorted().toList();
        } catch (Exception exception) {
            throw new IllegalStateException(friendlyMessage(exception), exception);
        }
    }

    private String parseText(String response) throws Exception {
        JsonNode content = objectMapper.readTree(response).path("choices").path(0).path("message").path("content");
        if (content.isTextual()) return content.asText("").trim();
        if (!content.isArray()) return "";
        StringBuilder result = new StringBuilder();
        for (JsonNode part : content) {
            String text = part.path("text").asText("");
            if (!text.isBlank()) result.append(result.isEmpty() ? "" : "\n").append(text);
        }
        return result.toString().trim();
    }

    private String friendlyMessage(Exception exception) {
        if (exception instanceof ModelRequestException request && request.status == 429) {
            return "豆包接口请求受限，请稍后重试或更换可用API Key。";
        }
        String message = compact(exception.getMessage());
        if (message.contains("429")) return "豆包接口请求受限，请稍后重试或更换可用API Key。";
        if (message.toLowerCase().contains("timeout") || message.contains("超时")) return "豆包接口请求超时，请稍后重试。";
        return "豆包接口请求失败：" + message;
    }

    private void sleep(int attempt) {
        try { Thread.sleep(1000L * attempt); }
        catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("大模型请求重试被中断", exception);
        }
    }
    private String normalize(String value) { return value == null ? "" : value.replaceAll("[\\r\\n\\t ]", "").trim(); }
    private static String compact(String value) {
        if (value == null || value.isBlank()) return "无详细信息";
        String result = value.replaceAll("\\s+", " ").trim();
        return result.length() > 500 ? result.substring(0, 500) + "..." : result;
    }
    private static class ModelRequestException extends RuntimeException {
        private final int status;
        private ModelRequestException(int status, String detail) { super("HTTP " + status + " " + detail); this.status = status; }
    }
}
