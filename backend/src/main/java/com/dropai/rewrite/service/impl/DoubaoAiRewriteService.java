package com.dropai.rewrite.service.impl;

import com.dropai.rewrite.config.DoubaoProperties;
import com.dropai.rewrite.service.AiRewriteService;
import com.dropai.rewrite.service.SkillPromptService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.List;
import java.util.Map;

@Primary
@Service
public class DoubaoAiRewriteService implements AiRewriteService {

    private final ThreadLocal<String> lastCallProvider = new ThreadLocal<>();
    private final DoubaoProperties properties;
    private final ObjectMapper objectMapper;
    private final RestClient restClient;
    private final MockAiRewriteService mockAiRewriteService;
    private final SkillPromptService skillPromptService;

    public DoubaoAiRewriteService(
            DoubaoProperties properties,
            ObjectMapper objectMapper,
            RestClient.Builder restClientBuilder,
            MockAiRewriteService mockAiRewriteService,
            SkillPromptService skillPromptService
    ) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.restClient = restClientBuilder
                .requestFactory(requestFactory(properties))
                .build();
        this.mockAiRewriteService = mockAiRewriteService;
        this.skillPromptService = skillPromptService;
    }

    @Override
    public String rewrite(String originalText, String rewriteType) {
        return rewriteWithFeedback(originalText, rewriteType, 0, "");
    }

    @Override
    public String rewriteWithFeedback(String originalText, String rewriteType, int beforeScore, String feedback) {
        if (!properties.isEnabled() || isBlank(properties.getApiKey())) {
            lastCallProvider.set("Mock fallback（未配置 DOUBAO_API_KEY）");
            return mockAiRewriteService.rewrite(originalText, rewriteType);
        }

        try {
            Map<String, Object> requestBody = Map.of(
                    "model", properties.getModel(),
                    "temperature", properties.getTemperature(),
                    "messages", List.of(
                            Map.of("role", "system", "content", systemPrompt(rewriteType)),
                            Map.of("role", "user", "content", userPrompt(originalText, rewriteType, beforeScore, feedback))
                    )
            );

            String response = restClient.post()
                    .uri(properties.getEndpoint())
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + properties.getApiKey())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(requestBody)
                    .retrieve()
                    .body(String.class);

            String content = parseContent(response, originalText, rewriteType);
            lastCallProvider.set(providerName() + " / " + modelName());
            return content;
        } catch (RestClientResponseException exception) {
            lastCallProvider.set("Mock fallback（豆包HTTP失败：" + exception.getStatusCode().value() + "，" + compact(exception.getResponseBodyAsString()) + "）");
            return mockAiRewriteService.rewrite(originalText, rewriteType);
        } catch (Exception exception) {
            lastCallProvider.set("Mock fallback（豆包调用失败：" + compact(exception.getMessage()) + "）");
            return mockAiRewriteService.rewrite(originalText, rewriteType);
        }
    }

    @Override
    public String providerName() {
        return "豆包 Ark";
    }

    @Override
    public String modelName() {
        return properties.getModel();
    }

    @Override
    public String lastCallProvider() {
        String provider = lastCallProvider.get();
        return isBlank(provider) ? providerName() + " / " + modelName() : provider;
    }

    @Override
    public boolean apiKeyConfigured() {
        return !isBlank(properties.getApiKey());
    }

    @Override
    public String endpoint() {
        return properties.getEndpoint();
    }

    private String systemPrompt(String rewriteType) {
        if ("降低AI写作痕迹".equals(rewriteType)) {
            return skillPromptService.loadSkill("humanize-zh-academic");
        }
        return """
                你是学术写作优化助手。你的任务是帮助用户改进论文段落表达质量，而不是承诺规避任何检测。
                必须遵守：
                1. 保留原意，不改变核心论点。
                2. 不新增虚假案例、虚假数据、虚假引用。
                3. 不要输出提纲、列表、多个版本、说明文字、标题或“以下是”。
                4. 避免“首先、其次、最后、综上所述、值得注意的是、随着……的发展”等模板化表达。
                5. 不连续三句使用相同句式，不把所有句子改得一样长。
                6. 不追求过度正式，不堆叠“重要意义、有效路径、内在机制、实践启示”等空泛表达。
                7. 输出只包含一版改写后的正文。
                """;
    }

    private SimpleClientHttpRequestFactory requestFactory(DoubaoProperties properties) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(properties.getConnectTimeoutSeconds()));
        factory.setReadTimeout(Duration.ofSeconds(properties.getReadTimeoutSeconds()));
        return factory;
    }

    private String userPrompt(String originalText, String rewriteType, int beforeScore, String feedback) {
        String extraRule = "";
        if ("降低AI写作痕迹".equals(rewriteType)) {
            extraRule = """
                    改写前风险分：%d
                    上一次失败原因：%s
                    请严格按 Skill 执行，只输出一版改写后的正文。
                    """.formatted(beforeScore, isBlank(feedback) ? "无" : feedback);
        }
        return """
                优化类型：%s

                请按以下策略处理：
                - 保留原意和论文语气。
                - 降低重复表达风险，减少机械化连接词。
                - 如果是扩写，只补充解释性表达，不添加未经提供的数据或案例。
                - 如果是缩写，压缩冗余内容但保留关键论点。
                %s

                原文：
                %s
                """.formatted(rewriteType, extraRule, originalText);
    }

    private String parseContent(String response, String originalText, String rewriteType) throws Exception {
        if (isBlank(response)) {
            return mockAiRewriteService.rewrite(originalText, rewriteType);
        }
        JsonNode root = objectMapper.readTree(response);
        JsonNode contentNode = root.path("choices").path(0).path("message").path("content");
        String content = contentNode.asText("");
        if (isBlank(content)) {
            return mockAiRewriteService.rewrite(originalText, rewriteType);
        }
        return content.trim();
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private String compact(String value) {
        if (isBlank(value)) {
            return "无详细信息";
        }
        String compacted = value.replaceAll("\\s+", " ").trim();
        return compacted.length() > 120 ? compacted.substring(0, 120) + "..." : compacted;
    }
}
