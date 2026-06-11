package com.dropai.rewrite.service;

import com.dropai.rewrite.config.OpenAiDesignProperties;
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
import java.util.Map;

@Service
public class OpenAiDesignService {
    private final OpenAiDesignProperties properties;
    private final ObjectMapper objectMapper;
    private final RestClient restClient;

    public OpenAiDesignService(OpenAiDesignProperties properties, ObjectMapper objectMapper, RestClient.Builder builder) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(properties.getConnectTimeoutSeconds()));
        factory.setReadTimeout(Duration.ofSeconds(properties.getReadTimeoutSeconds()));
        this.restClient = builder.requestFactory(factory).build();
    }

    public String generate(String instructions, String input) {
        if (!properties.isEnabled()) throw new IllegalStateException("设计生成 OpenAI 服务已关闭");
        String apiKey = normalize(properties.getApiKey());
        if (apiKey.isBlank()) throw new IllegalStateException("未配置 OPENAI_API_KEY，设计生成不会返回模拟结果");
        try {
            byte[] response = restClient.post()
                    .uri(properties.getEndpoint())
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("model", properties.getModel(), "instructions", instructions, "input", input))
                    .exchange((request, result) -> {
                        byte[] bytes = StreamUtils.copyToByteArray(result.getBody());
                        if (result.getStatusCode().isError()) {
                            throw new IllegalStateException("OpenAI 设计生成失败：HTTP " + result.getStatusCode().value() + "，" + compact(new String(bytes, StandardCharsets.UTF_8)));
                        }
                        return bytes;
                    });
            return parseText(response == null ? "" : new String(response, StandardCharsets.UTF_8));
        } catch (IllegalStateException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IllegalStateException("OpenAI 设计生成失败：" + compact(exception.getMessage()), exception);
        }
    }

    public boolean apiKeyConfigured() { return !normalize(properties.getApiKey()).isBlank(); }
    public String modelName() { return properties.getModel(); }
    public String endpoint() { return properties.getEndpoint(); }

    private String parseText(String response) throws Exception {
        JsonNode root = objectMapper.readTree(response);
        String outputText = root.path("output_text").asText("");
        if (!outputText.isBlank()) return outputText.trim();
        for (JsonNode output : root.path("output")) {
            for (JsonNode content : output.path("content")) {
                String text = content.path("text").asText("");
                if (!text.isBlank()) return text.trim();
            }
        }
        throw new IllegalStateException("OpenAI 返回内容为空");
    }

    private String normalize(String value) { return value == null ? "" : value.replaceAll("[\\r\\n\\t ]", "").trim(); }
    private String compact(String value) {
        if (value == null || value.isBlank()) return "无详细信息";
        String result = value.replaceAll("\\s+", " ").trim();
        return result.length() > 300 ? result.substring(0, 300) + "..." : result;
    }
}
