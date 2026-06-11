package com.dropai.rewrite.service;

import com.dropai.rewrite.config.MatrixDesignProperties;
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
import java.util.List;
import java.util.Map;
import java.util.stream.StreamSupport;

@Service
public class MatrixDesignService {
    private final MatrixDesignProperties properties;
    private final ObjectMapper objectMapper;
    private final RestClient restClient;

    public MatrixDesignService(MatrixDesignProperties properties, ObjectMapper objectMapper, RestClient.Builder builder) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(properties.getConnectTimeoutSeconds()));
        factory.setReadTimeout(Duration.ofSeconds(properties.getReadTimeoutSeconds()));
        this.restClient = builder.requestFactory(factory).build();
    }

    public String generate(String instructions, String input) {
        if (!properties.isEnabled()) throw new IllegalStateException("设计生成的万量矩阵服务已关闭");
        String apiKey = normalize(properties.getApiKey());
        if (apiKey.isBlank()) throw new IllegalStateException("未配置 MATRIX_API_KEY，设计生成无法调用万量矩阵");
        try {
            Map<String, Object> body = Map.of(
                    "model", properties.getModel(),
                    "messages", List.of(
                            Map.of("role", "system", "content", instructions),
                            Map.of("role", "user", "content", input)
                    )
            );
            String lastEmptyResponse = "";
            for (int attempt = 1; attempt <= 3; attempt++) {
                byte[] response = restClient.post()
                        .uri(properties.getEndpoint())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(body)
                        .exchange((request, result) -> {
                            byte[] bytes = StreamUtils.copyToByteArray(result.getBody());
                            if (result.getStatusCode().isError()) {
                                throw new IllegalStateException("万量矩阵设计生成失败：HTTP " + result.getStatusCode().value() + "，" + compact(new String(bytes, StandardCharsets.UTF_8)));
                            }
                            return bytes;
                        });
                String responseText = response == null ? "" : new String(response, StandardCharsets.UTF_8);
                String parsed = parseText(responseText);
                if (!parsed.isBlank()) return parsed;
                lastEmptyResponse = responseText;
                if (attempt < 3) Thread.sleep(800L * attempt);
            }
            throw new IllegalStateException("万量矩阵连续返回空内容：" + compact(lastEmptyResponse));
        } catch (IllegalStateException exception) {
            throw exception;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("万量矩阵设计生成重试被中断", exception);
        } catch (Exception exception) {
            throw new IllegalStateException("万量矩阵设计生成失败：" + compact(exception.getMessage()), exception);
        }
    }

    public boolean apiKeyConfigured() { return !normalize(properties.getApiKey()).isBlank(); }
    public String modelName() { return properties.getModel(); }
    public String endpoint() { return properties.getEndpoint(); }

    public List<String> availableModels() {
        String apiKey = normalize(properties.getApiKey());
        if (apiKey.isBlank()) throw new IllegalStateException("未配置 MATRIX_API_KEY");
        try {
            String modelsEndpoint = properties.getEndpoint().replaceFirst("/chat/completions/?$", "/models");
            String response = restClient.get()
                    .uri(modelsEndpoint)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                    .retrieve()
                    .body(String.class);
            JsonNode root = objectMapper.readTree(response);
            return StreamSupport.stream(root.path("data").spliterator(), false)
                    .map(model -> model.path("id").asText(""))
                    .filter(id -> !id.isBlank())
                    .sorted()
                    .toList();
        } catch (Exception exception) {
            throw new IllegalStateException("读取万量矩阵模型列表失败：" + compact(exception.getMessage()), exception);
        }
    }

    private String parseText(String response) throws Exception {
        JsonNode content = objectMapper.readTree(response).path("choices").path(0).path("message").path("content");
        if (content.isTextual()) return content.asText("").trim();
        if (!content.isArray()) return "";
        StringBuilder result = new StringBuilder();
        for (JsonNode part : content) {
            String text = part.path("text").asText("");
            if (!text.isBlank()) {
                if (!result.isEmpty()) result.append('\n');
                result.append(text);
            }
        }
        return result.toString().trim();
    }

    private String normalize(String value) { return value == null ? "" : value.replaceAll("[\\r\\n\\t ]", "").trim(); }
    private String compact(String value) {
        if (value == null || value.isBlank()) return "无详细信息";
        String result = value.replaceAll("\\s+", " ").trim();
        return result.length() > 500 ? result.substring(0, 500) + "..." : result;
    }
}
