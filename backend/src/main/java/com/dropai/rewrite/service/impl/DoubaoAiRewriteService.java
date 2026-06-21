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
import org.springframework.util.StreamUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.nio.charset.StandardCharsets;
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
            throw new IllegalStateException("未配置 DOUBAO_API_KEY，未调用模型，也不会生成模拟结果");
        }

        try {
            Map<String, Object> requestBody = Map.of(
                    "model", properties.getModel(),
                    "temperature", properties.getTemperature(),
                    "max_tokens", 4096,
                    "thinking", Map.of("type", "disabled"),
                    "messages", List.of(
                            Map.of("role", "system", "content", systemPrompt(rewriteType)),
                            Map.of("role", "user", "content", userPrompt(originalText, rewriteType, beforeScore, feedback))
                    )
            );

            String response = postWithRetry(apiKey, requestBody);

            String content = parseContent(response);
            lastCallProvider.set(providerName());
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
        return isBlank(provider) ? providerName() : provider;
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
        String baseRewriteType = baseRewriteType(rewriteType);
        if ("设计参数提取".equals(baseRewriteType)) {
            return """
                    你是机械设计需求分析工程师。严格按用户给出的 JSON 结构返回结果。
                    只输出合法 JSON，不输出 Markdown、解释、标题或额外文字。
                    必须区分资料明确参数、推导参数和工程建议值，不得把建议值伪装成任务书原值。
                    """;
        }
        if (isRewriteMode(baseRewriteType)) {
            return rewriteSystemPrompt();
        }
        if (isHumanizeMode(baseRewriteType)) {
            return humanizeSystemPrompt();
        }
        if (isDoubleMode(baseRewriteType)) {
            return doubleSystemPrompt();
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
        String baseRewriteType = baseRewriteType(rewriteType);
        if ("设计参数提取".equals(baseRewriteType)) {
            return originalText;
        }
        String platform = platformCode(rewriteType);
        if (isRewriteMode(baseRewriteType) || isHumanizeMode(baseRewriteType) || isDoubleMode(baseRewriteType)) {
            return """
                    优化类型：%s
                    目标检测口径：%s
                    改写前风险分：%d
                    上一次失败原因：%s

                    请严格按照系统 Prompt 的任务目标和禁止事项处理。
                    任何 [[DROP_AI_PROTECTED_数字]] 占位符都必须逐字原样保留，不得删除、改写、翻译或调整顺序。
                    只输出优化后的完整正文，不输出解释、标题、步骤、列表或多个版本。

                    原文：
                    %s
                    """.formatted(displayModeName(baseRewriteType), platformName(platform), beforeScore, isBlank(feedback) ? "无" : feedback, originalText);
        }
        String extraRule = "";
        if ("深度降低AI写作痕迹".equals(baseRewriteType)) {
            extraRule = """
                    当前为全文深度降 AI 模式。改写前风险分：%d
                    上一次失败原因：%s
                    这一模式的目标不是同义词替换或单纯降重，而是降低整段表达中的机械规律。
                    请在保留事实、论点、专业术语和逻辑关系的前提下执行较深入重写：
                    1. 先识别段落中最像模板生成的部分，再重组信息出现顺序；不要沿着原句逐句翻译式改写。
                    2. 至少对一处句子进行合理拆分或合并，并改变部分句子的起笔方式和主语安排。
                    3. 删除可有可无的总结句、过渡词、空泛限定词；不要用新的套话补足它们。
                    4. 保留长短句差异，允许表达略有停顿和转折，不要把结果写得过分完整、均匀、顺滑。
                    5. 避免连续句使用相同语法骨架，也不要只靠替换近义词制造表面差异。
                    6. 改写后通常保持原文长度的 75%% 到 110%%，不得新增原文没有的事实、数据或结论。
                    只输出一版改写后的正文。
                    """.formatted(beforeScore, isBlank(feedback) ? "无" : feedback);
        } else if ("降低AI写作痕迹".equals(baseRewriteType)) {
            extraRule = """
                    改写前风险分：%d
                    上一次失败原因：%s
                    请严格按 Skill 执行，只输出一版改写后的正文。
                    """.formatted(beforeScore, isBlank(feedback) ? "无" : feedback);
        } else if ("双降".equals(baseRewriteType)) {
            extraRule = """
                    双降优先级：
                    1. 一次性同时处理重复表达和 AI 痕迹，不要写成“先降重、再降 AI”的两段式结果。
                    2. 优先避免 AI 味升高，再降低重复表达；宁可少改，也不要改得更工整、更完整。
                    3. 降重只能通过局部语序调整、短语替换、删减冗余来完成，禁止大幅扩写。
                    4. 不要把短句改成完整解释句，不要把技术描述改成“全流程、多维度、系统性”等泛化表达。
                    5. 改写后长度控制在原文 80%% 到 105%% 之间；若原文较短，尽量保持相近长度。
                    6. 如果降重和降 AI 冲突，优先选择更自然、更像人工修改的表达。
                    """;
        }
        String platformRule = "深度降低AI写作痕迹".equals(baseRewriteType) && "GENERAL".equals(platform)
                ? """
                    通用深度降 AI 口径：
                    - 优先打散句式、信息顺序和段落节奏中的机械规律，不以同义替换率作为主要目标。
                    - 可以进行必要的句子拆合与删减，但不扩写、不拔高、不改变原意。
                    """
                : platformRules(platform);
        return """
                优化类型：%s
                目标检测口径：%s

                请按以下策略处理：
                - 保留原意和论文语气。
                - 降低重复表达风险，减少机械化连接词。
                - 采用轻量改写，通常控制在原文长度的 85%% 到 115%% 之间，不要为了降重而明显扩写。
                - 主动降低中文 AIGC 高风险特征：四字套话堆叠、虚词过密、主语连续重复、句长过于均匀、结论绝对化。
                - 避免连续使用“通过……实现……”“基于……构建……”“在……基础上……”这类同形句。
                - 功能类和工程类段落保留具体对象和动作，不要抽象成宏观管理学表达。
                - 代码、注解、接口路径、SQL、字段名、变量名、方法名、配置项必须尽量原样保留，不要改成解释性长句。
                - 任何 [[DROP_AI_PROTECTED_数字]] 占位符都必须逐字原样保留，不得删除、改写、翻译或调整顺序。
                - 不要新增“全流程、多维度、系统性、有效提升、优化效果、用户体验提升”等泛化套话。
                - 如果是扩写，只补充解释性表达，不添加未经提供的数据或案例。
                - 如果是缩写，压缩冗余内容但保留关键论点。
                %s
                %s

                原文：
                %s
                """.formatted(baseRewriteType, platformName(platform), extraRule, platformRule, originalText);
    }

    private String baseRewriteType(String rewriteType) {
        if (rewriteType == null) {
            return "";
        }
        int index = rewriteType.indexOf('@');
        return index >= 0 ? rewriteType.substring(0, index) : rewriteType;
    }

    private boolean isRewriteMode(String rewriteType) {
        return "rewrite".equals(rewriteType)
                || "智能降重".equals(rewriteType)
                || "降重复改写".equals(rewriteType);
    }

    private boolean isHumanizeMode(String rewriteType) {
        return "humanize".equals(rewriteType)
                || "智能降AI".equals(rewriteType)
                || "降低AI写作痕迹".equals(rewriteType)
                || "深度降低AI写作痕迹".equals(rewriteType);
    }

    private boolean isDoubleMode(String rewriteType) {
        return "double".equals(rewriteType)
                || "双降增强".equals(rewriteType)
                || "双降".equals(rewriteType);
    }

    private String displayModeName(String rewriteType) {
        if (isRewriteMode(rewriteType)) {
            return "智能降重";
        }
        if (isDoubleMode(rewriteType)) {
            return "双降增强";
        }
        return "智能降AI";
    }

    private String rewriteSystemPrompt() {
        return """
                你是一名学术论文改写专家。

                任务目标：
                在保持原意、数据、专业术语和论文结构不变的前提下，降低文本重复表达风险。

                改写要求：
                1. 不改变原文核心观点。
                2. 不删除重要信息。
                3. 不随意增加未经原文支持的新内容。
                4. 保留专业术语、数据、公式、引用、图表编号。
                5. 避免大段照搬原句。
                6. 优先通过句式重组、同义替换、语序调整、主被动转换、长短句变化降低重复风险。
                7. 输出完整改写后的正文。

                禁止：
                1. 禁止虚构数据。
                2. 禁止改变引用编号。
                3. 禁止把论文改成口语化文章。
                4. 禁止破坏标题层级。
                """;
    }

    private String humanizeSystemPrompt() {
        return """
                你是一名毕业论文AI痕迹优化专家。

                目标：
                降低论文AI检测率。

                重点不是同义词替换，而是消除AI写作模式，使文本更接近真实本科毕业论文。

                必须遵守：
                1. 保持原文事实不变
                2. 保持原文数据不变
                3. 保持专业术语不变
                4. 保持引用编号不变
                5. 保持图表编号不变
                6. 保持章节结构不变
                7. 不虚构实验数据
                8. 不删除技术内容
                9. 不改变研究结论

                【规则一：文献综述去模板化】
                发现“某某研究了……提出了……实现了……得到了……”连续出现时，禁止保留流水线句式。
                改写方向：围绕XX需求展开研究、重点解决XX问题、完成XX功能验证、针对XX场景进行设计、关注XX方面优化。
                避免“研究了→提出了→实现了→得到了”重复循环。

                【规则二：减少系统主语重复】
                检测“系统采用……系统实现……系统完成……系统能够……”连续超过2次时，必须替换主语。
                优先改为：主控模块、心率模块、体温模块、显示模块、报警模块、程序运行过程中、用户操作时、测试过程中。
                避免整段都以“系统”开头。

                【规则三：增加开发痕迹】
                优先保留：调试过程中、实际测试发现、联调阶段、模块测试时、接线过程中、长时间运行后、焊接完成后、实验过程中、参数调整后。
                避免全文只讨论理论，增加真实开发过程特征。

                【规则四：工程实现优先】
                优先描述：用了什么、怎么连接、怎么实现、怎么测试、怎么调试。
                避免大量描述：是什么、为什么重要、有什么意义、有什么价值。
                连续解释链“定义→原理→作用→优势”必须压缩。

                【规则五：教材化表达压缩】
                减少“XX是……”“XX主要用于……”“XX具有……”“XX能够……”等教材式定义。
                改为“本设计采用XX……”“模块通过XX实现……”“程序调用XX完成……”“实验中使用XX……”。
                增加工程实现视角，减少知识讲解视角。

                【规则六：场景化表达增强】
                优先使用：学生、上班族、老人、运动人群、实验人员、普通用户、居家环境、校园场景、实际佩戴过程、实际运行环境。
                避免频繁使用：用户、使用者、目标对象、目标群体等泛化词汇。

                【规则七：打散解释链】
                AI常见结构“是什么→原理→作用→优势”禁止完整保留。
                允许调整为“采用原因→关键参数→实现方式→测试结果”，让论述顺序更接近真实论文。

                【规则八：打散平行结构】
                避免目标1、目标2、目标3、目标4或功能1、功能2、功能3、功能4全部长度一致。
                允许部分展开、部分合并、部分举例，增强自然度。

                【规则九：测试章节重构】
                减少“测试结果表明”“实验结果表明”“满足设计要求”“功能运行正常”连续出现。
                改为“实际测试过程中发现”“连续运行30分钟后”“多次测量结果显示”“与预期结果基本一致”“从记录数据来看”。
                增加测试细节。

                【规则十：总结展望重构】
                禁止“未来可以”“未来将”“进一步研究”“具有重要意义”“具有较高价值”“提供参考”“验证了可行性”等万能总结句。
                改为“当前版本仍存在……”“受硬件条件限制……”“后续若继续完善……”“在实际应用中仍需解决……”。
                增加真实不足与改进方向。

                【规则十一：句长与段落离散化】
                避免连续多个长度接近的句子，避免全文段落长度高度一致。
                允许短句、中句、长句混合出现，避免AI均匀节奏。

                【规则十二：本科论文风格优先】
                优先保留：设计、实现、测试、调试、模块、电路、程序、参数、功能、实验、结果。
                避免转变为行业分析报告、媒体评论、政策文件、企业方案书、咨询报告风格。

                最终目标：
                让论文更像真实本科生完成的毕业设计。

                重点体现：
                设计过程、实现过程、调试过程、测试过程，而不是理论讲解和知识科普。

                只输出优化后的正文。
                不要解释。不要分析。不要输出修改说明。
                """;
    }

    private String doubleSystemPrompt() {
        return """
                你需要分两阶段处理论文文本。

                第一阶段：
                按照“智能降重”规则改写文本，降低重复表达风险。

                第二阶段：
                在第一阶段结果基础上，按照“智能降AI”规则继续优化，减少AI生成痕迹。

                总体要求：
                1. 保持原意不变。
                2. 保持论文结构不变。
                3. 保留标题、编号、引用、公式、表格、图片编号。
                4. 不虚构数据。
                5. 不加入无关内容。
                6. 最终只输出优化后的完整正文。
                """;
    }

    private String platformCode(String rewriteType) {
        if (rewriteType == null) {
            return "GENERAL";
        }
        int index = rewriteType.indexOf('@');
        if (index < 0 || index == rewriteType.length() - 1) {
            return "GENERAL";
        }
        return rewriteType.substring(index + 1).trim().toUpperCase();
    }

    private String platformName(String platform) {
        return switch (platform) {
            case "CNKI" -> "知网";
            case "WEIPU" -> "维普";
            case "WANFANG" -> "万方";
            case "GEZIDA" -> "格子达";
            default -> "通用";
        };
    }

    private String platformRules(String platform) {
        return switch (platform) {
            case "CNKI" -> """
                    知网口径适配：
                    - 弱化“背景-问题-措施-意义”的完整模板结构。
                    - 不要把每段都写成高度规范的论文综述腔。
                    - 摘要、绪论和系统介绍段落要保留部分朴素、直接的作者表达。
                    - 避免“本文旨在、研究表明、具有重要意义、提供参考”等万能句。
                    """;
            case "WEIPU" -> """
                    维普口径适配：
                    - 重点打散词汇分布和句式规律，不要连续使用“通过……实现……”。
                    - 保留句长差异，避免每句都在相近长度收尾。
                    - 降低连接词密度，少用“因此、此外、同时、进而、从而”。
                    - 不要把同类功能名反复换成同一组高级同义词。
                    """;
            case "WANFANG" -> """
                    万方口径适配：
                    - 不要让语义承接过度顺滑，避免每句都严丝合缝地解释上一句。
                    - 保留具体模块、对象、操作和约束，不要抽象成“系统性、整体性、多维度”。
                    - 工程类描述以清楚为主，不改成宏观理论表述。
                    - 避免“有效提升、优化效果、实践价值、管理效率提升”等泛化结论。
                    """;
            case "GEZIDA" -> """
                    格子达口径适配：
                    - 控制段落疑似率，短段落不要扩成长段落。
                    - 避免万能开头和万能结尾，不要每段都有完整总结句。
                    - 保留人工写作中的节奏变化，允许局部表达不完全对称。
                    - 拆开过长的完整说明句，也不要把所有短句合成长句。
                    """;
            default -> """
                    通用口径适配：
                    - 轻量、局部、克制地修改，不扩写、不拔高、不模板化。
                    - 保留人工写作节奏，避免过度完整、过度顺滑、过度正式。
                    """;
        };
    }

    private SimpleClientHttpRequestFactory requestFactory(DoubaoProperties properties) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(properties.getConnectTimeoutSeconds()));
        factory.setReadTimeout(Duration.ofSeconds(properties.getReadTimeoutSeconds()));
        return factory;
    }

    private String postWithRetry(String apiKey, Map<String, Object> requestBody) {
        int maxAttempts = 4;
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                byte[] response = restClient.post()
                        .uri(properties.getEndpoint())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(requestBody)
                        .exchange((request, clientResponse) -> {
                            byte[] body = StreamUtils.copyToByteArray(clientResponse.getBody());
                            if (clientResponse.getStatusCode().isError()) {
                                throw new DoubaoHttpException(clientResponse.getStatusCode().value(), new String(body, StandardCharsets.UTF_8));
                            }
                            return body;
                        });
                return response == null ? "" : new String(response, StandardCharsets.UTF_8);
            } catch (DoubaoHttpException exception) {
                if (exception.statusCode() != 429 || attempt == maxAttempts) {
                    throw new IllegalStateException("豆包调用失败：HTTP " + exception.statusCode() + "，" + compact(exception.responseBody()), exception);
                }
                sleepQuietly(attempt * 5000L);
            } catch (RestClientResponseException exception) {
                if (exception.getStatusCode().value() != 429 || attempt == maxAttempts) {
                    throw exception;
                }
                sleepQuietly(attempt * 5000L);
            }
        }
        throw new IllegalStateException("Doubao request exhausted retry attempts");
    }

    private void sleepQuietly(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Doubao retry interrupted", exception);
        }
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

    private static class DoubaoHttpException extends RuntimeException {
        private final int statusCode;
        private final String responseBody;

        DoubaoHttpException(int statusCode, String responseBody) {
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
