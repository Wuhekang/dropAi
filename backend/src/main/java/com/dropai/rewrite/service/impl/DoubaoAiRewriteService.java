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
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

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
    private final SkillPromptService skillPromptService;

    public DoubaoAiRewriteService(
            DoubaoProperties properties,
            ObjectMapper objectMapper,
            RestClient.Builder restClientBuilder,
            SkillPromptService skillPromptService
    ) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.restClient = restClientBuilder
                .requestFactory(requestFactory(properties))
                .build();
        this.skillPromptService = skillPromptService;
    }

    @Override
    public String rewrite(String originalText, String rewriteType) {
        return rewriteWithFeedback(originalText, rewriteType, 0, "");
    }

    @Override
    public String rewriteWithFeedback(String originalText, String rewriteType, int beforeScore, String feedback) {
        if (!properties.isEnabled()) {
            lastCallProvider.set("豆包未调用：AI 服务已关闭");
            throw new IllegalStateException("AI 服务已关闭，请开启 ai.doubao.enabled");
        }
        String apiKey = normalizeApiKey(properties.getApiKey());
        if (isBlank(apiKey)) {
            lastCallProvider.set("豆包未调用：未配置 DOUBAO_API_KEY");
            throw new IllegalStateException("未读取到 DOUBAO_API_KEY，请在 Render 的 Environment 中配置豆包 API Key 后重新部署");
        }

        try {
            Map<String, Object> requestBody = Map.of(
                    "model", properties.getModel(),
                    "temperature", properties.getTemperature(),
                    "max_tokens", 4096,
                    "messages", List.of(
                            Map.of("role", "system", "content", systemPrompt(rewriteType)),
                            Map.of("role", "user", "content", userPrompt(originalText, rewriteType, beforeScore, feedback))
                    )
            );

            String response = restClient.post()
                    .uri(properties.getEndpoint())
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(requestBody)
                    .retrieve()
                    .body(String.class);

            String content = parseContent(response);
            lastCallProvider.set(providerName() + " / " + modelName());
            return content;
        } catch (RestClientResponseException exception) {
            lastCallProvider.set("豆包调用失败：HTTP " + exception.getStatusCode().value());
            throw new IllegalStateException(
                    "豆包调用失败：HTTP " + exception.getStatusCode().value() + "，" + compact(exception.getResponseBodyAsString()),
                    exception
            );
        } catch (IllegalStateException exception) {
            lastCallProvider.set("豆包调用失败：" + compact(exception.getMessage()));
            throw exception;
        } catch (Exception exception) {
            lastCallProvider.set("豆包调用失败：" + compact(exception.getMessage()));
            throw new IllegalStateException("豆包调用失败：" + compact(exception.getMessage()), exception);
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

    private SimpleClientHttpRequestFactory requestFactory(DoubaoProperties properties) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(properties.getConnectTimeoutSeconds()));
        factory.setReadTimeout(Duration.ofSeconds(properties.getReadTimeoutSeconds()));
        return factory;
    }

    private String parseContent(String response) throws Exception {
        if (isBlank(response)) {
            throw new IllegalStateException("豆包返回为空");
        }
        JsonNode root = objectMapper.readTree(response);
        JsonNode contentNode = root.path("choices").path(0).path("message").path("content");
        String content = contentNode.asText("");
        if (isBlank(content)) {
            throw new IllegalStateException("豆包返回内容为空");
        }
        return content.trim();
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private String normalizeApiKey(String value) {
        if (value == null) {
            return "";
        }
        return value.replaceAll("[\\r\\n\\t ]", "").trim();
    }

    private String compact(String value) {
        if (isBlank(value)) {
            return "无详细信息";
        }
        String compacted = value.replaceAll("\\s+", " ").trim();
        return compacted.length() > 240 ? compacted.substring(0, 240) + "..." : compacted;
    }
}
