package com.dropai.rewrite.service.writing;

import com.dropai.rewrite.config.DoubaoProperties;
import com.dropai.rewrite.service.ai.AiRequestType;
import com.dropai.rewrite.service.ai.DoubaoModelRouter;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class DoubaoWritingService {
    private final DoubaoProperties properties;
    private final ObjectMapper objectMapper;
    private final RestClient restClient;
    private final DoubaoModelRouter modelRouter;

    public DoubaoWritingService(DoubaoProperties properties,
                                ObjectMapper objectMapper,
                                RestClient.Builder restClientBuilder,
                                DoubaoModelRouter modelRouter) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.modelRouter = modelRouter;
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(properties.getConnectTimeoutSeconds()));
        factory.setReadTimeout(Duration.ofSeconds(Math.max(180, properties.getReadTimeoutSeconds())));
        this.restClient = restClientBuilder.requestFactory(factory).build();
    }

    public boolean configured() {
        return !apiKey().isBlank();
    }

    public String providerStatus() {
        if (!properties.isEnabled()) return "豆包未调用：AI 服务已关闭";
        if (!configured()) return "豆包未调用：未配置 DOUBAO_API_KEY";
        return "豆包 Ark · " + model();
    }

    public String complete(String systemPrompt, String userPrompt, int maxTokens) {
        if (!properties.isEnabled()) throw new IllegalStateException("AI 服务已关闭，请开启 ai.doubao.enabled");
        String key = apiKey();
        if (key.isBlank()) throw new IllegalStateException("未配置 DOUBAO_API_KEY，请在桌面 .env 中配置");
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("model", model());
        request.put("stream", false);
        request.put("temperature", properties.getTemperature());
        request.put("max_tokens", Math.max(1024, maxTokens));
        request.put("thinking", Map.of("type", "disabled"));
        request.put("messages", List.of(
                Map.of("role", "system", "content", systemPrompt),
                Map.of("role", "user", "content", userPrompt)
        ));
        return parse(postWithRetry(key, request));
    }

    private String model() {
        return modelRouter.resolveModel(AiRequestType.TEXT);
    }

    private String apiKey() {
        String value = properties.getApiKey();
        return value == null ? "" : value.replaceAll("[\\r\\n\\t ]", "").trim();
    }

    private String postWithRetry(String key, Map<String, Object> requestBody) {
        int attempts = Math.max(1, properties.getMaxRetries() + 1);
        RuntimeException last = null;
        for (int attempt = 1; attempt <= attempts; attempt++) {
            try {
                byte[] response = restClient.post()
                        .uri(properties.getEndpoint())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(requestBody)
                        .exchange((request, clientResponse) -> {
                            byte[] body = StreamUtils.copyToByteArray(clientResponse.getBody());
                            if (clientResponse.getStatusCode().isError()) {
                                throw new IllegalStateException("豆包调用失败：HTTP " + clientResponse.getStatusCode().value()
                                        + "，" + compact(new String(body, StandardCharsets.UTF_8)));
                            }
                            return body;
                        });
                return response == null ? "" : new String(response, StandardCharsets.UTF_8);
            } catch (RuntimeException exception) {
                last = exception;
                if (!String.valueOf(exception.getMessage()).contains("429") || attempt == attempts) break;
                sleep(attempt * 4000L);
            }
        }
        throw last == null ? new IllegalStateException("豆包调用失败") : last;
    }

    private String parse(String response) {
        try {
            JsonNode root = objectMapper.readTree(response);
            String content = root.path("choices").path(0).path("message").path("content").asText("");
            if (content.isBlank()) throw new IllegalStateException("豆包返回内容为空");
            return content.trim();
        } catch (Exception exception) {
            throw new IllegalStateException("豆包响应解析失败：" + exception.getMessage(), exception);
        }
    }

    private void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("豆包重试被中断", exception);
        }
    }

    private String compact(String value) {
        if (value == null || value.isBlank()) return "无详细信息";
        String compacted = value.replaceAll("\\s+", " ").trim();
        return compacted.length() > 240 ? compacted.substring(0, 240) + "..." : compacted;
    }
}
