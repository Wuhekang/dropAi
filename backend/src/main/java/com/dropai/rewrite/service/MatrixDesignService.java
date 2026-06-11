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
            return parseText(response == null ? "" : new String(response, StandardCharsets.UTF_8));
        } catch (IllegalStateException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IllegalStateException("万量矩阵设计生成失败：" + compact(exception.getMessage()), exception);
        }
    }

    public boolean apiKeyConfigured() { return !normalize(properties.getApiKey()).isBlank(); }
    public String modelName() { return properties.getModel(); }
    public String endpoint() { return properties.getEndpoint(); }

    private String parseText(String response) throws Exception {
        JsonNode root = objectMapper.readTree(response);
        String text = root.path("choices").path(0).path("message").path("content").asText("");
        if (!text.isBlank()) return text.trim();
        throw new IllegalStateException("万量矩阵返回内容为空：" + compact(response));
    }

    private String normalize(String value) { return value == null ? "" : value.replaceAll("[\\r\\n\\t ]", "").trim(); }
    private String compact(String value) {
        if (value == null || value.isBlank()) return "无详细信息";
        String result = value.replaceAll("\\s+", " ").trim();
        return result.length() > 500 ? result.substring(0, 500) + "..." : result;
    }
}
