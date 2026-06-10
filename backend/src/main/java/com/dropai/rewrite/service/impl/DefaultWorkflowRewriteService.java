package com.dropai.rewrite.service.impl;

import com.dropai.rewrite.service.AiRewriteService;
import com.dropai.rewrite.service.TextStructureProtector;
import com.dropai.rewrite.service.WorkflowRewriteService;
import com.dropai.rewrite.utils.AiRiskAnalyzeUtil;
import com.dropai.rewrite.vo.AiAnalyzeVO;
import com.dropai.rewrite.vo.QualityCheckVO;
import com.dropai.rewrite.vo.WorkflowStepVO;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@Service
public class DefaultWorkflowRewriteService implements WorkflowRewriteService {

    private static final List<String> BANNED_TEMPLATE_WORDS = Arrays.asList("首先", "其次", "最后", "综上所述");
    private static final List<String> BANNED_AI_PHRASES = Arrays.asList(
            "值得注意的是", "随着", "由此可见", "具有重要意义", "内在机制", "实践启示", "有效路径"
    );

    private final AiRewriteService aiRewriteService;
    private final TextStructureProtector textStructureProtector;

    public DefaultWorkflowRewriteService(
            AiRewriteService aiRewriteService,
            TextStructureProtector textStructureProtector
    ) {
        this.aiRewriteService = aiRewriteService;
        this.textStructureProtector = textStructureProtector;
    }

    @Override
    public WorkflowRewriteResult execute(String originalText, String rewriteType) {
        List<WorkflowStepVO> steps = new ArrayList<>();
        String preparedText = preprocess(originalText);
        TextStructureProtector.ProtectedText protectedText = textStructureProtector.protect(preparedText);
        String baseRewriteType = baseRewriteType(rewriteType);
        String platformName = platformName(platformCode(rewriteType));
        steps.add(new WorkflowStepVO("TEXT_PREPROCESS", "文本预处理", "清理多余空白并保留原始语义边界"));
        steps.add(new WorkflowStepVO("STRUCTURE_PROTECT", "结构保护",
                protectedText.protectedCount() == 0
                        ? "未发现需要保护的表格、代码、URL 或参考文献"
                        : "已锁定 " + protectedText.protectedCount() + " 处表格、代码、URL 或参考文献，改写后原样恢复"));

        AiAnalyzeVO originalRisk = AiRiskAnalyzeUtil.analyze(preparedText);
        steps.add(new WorkflowStepVO("AI_TRACE_ANALYZE", "AI痕迹分析 Skill",
                "原文风险等级：" + originalRisk.getLevel() + "，评分：" + originalRisk.getScore()));

        String strategy = planStrategy(baseRewriteType, originalRisk) + "；平台约束：" + platformName;
        steps.add(new WorkflowStepVO("REWRITE_PLAN", "改写策略规划 Skill", strategy));

        String sentenceRewritten = rewriteSentences(protectedText.text(), rewriteType, originalRisk.getScore(), "");
        sentenceRewritten = protectedText.restore(sentenceRewritten);
        String sentenceProvider = aiRewriteService.lastCallProvider();
        steps.add(new WorkflowStepVO("SENTENCE_REWRITE", "分句改写 Skill",
                "按句处理，约束为不改变核心含义、不新增虚假案例、不只做同义词替换；调用：" + sentenceProvider));

        String polished = academicPolish(sentenceRewritten, baseRewriteType);
        steps.add(new WorkflowStepVO("ACADEMIC_POLISH", "学术风格润色 Skill",
                "统一论文语气，减少口语化和过度扩写"));

        boolean aiReductionType = "降低AI写作痕迹".equals(baseRewriteType) || "双降".equals(baseRewriteType);
        boolean useModelHumanize = !aiReductionType && originalRisk.getScore() >= 45;
        String finalText = humanizeExpression(polished, useModelHumanize);
        String finalProvider = useModelHumanize ? aiRewriteService.lastCallProvider() : sentenceProvider;
        steps.add(new WorkflowStepVO("HUMAN_EXPRESSION_ADJUST", "人工化表达调整 Skill",
                useModelHumanize
                        ? "调用模型进行自然化表达调整，再用规则移除高频模板词；调用：" + finalProvider
                        : "使用规则移除高频模板词，调整连接方式和句式节奏"));

        QualityCheckVO qualityCheck = qualityCheck(preparedText, finalText);
        steps.add(new WorkflowStepVO("QUALITY_CHECK", "质量检查 Skill",
                qualityCheck.getIssues().isEmpty() ? "通过基础质量检查" : String.join("；", qualityCheck.getIssues())));

        WorkflowRewriteResult result = new WorkflowRewriteResult();
        result.setRewrittenText(finalText);
        result.setAiProvider(finalProvider);
        result.setAiModel(aiRewriteService.modelName());
        result.setQualityCheck(qualityCheck);
        result.setWorkflowSteps(steps);
        return result;
    }

    private String preprocess(String text) {
        return text == null ? "" : text.replaceAll("[\\t ]+", " ").replaceAll("\\n{3,}", "\n\n").trim();
    }

    private String planStrategy(String rewriteType, AiAnalyzeVO risk) {
        List<String> rules = new ArrayList<>();
        rules.add("保留原意");
        rules.add("避免凭空添加数据");
        if ("降重复改写".equals(rewriteType)) {
            rules.add("调整语序与句式结构");
        }
        if ("降低AI写作痕迹".equals(rewriteType) || risk.getScore() >= 45) {
            rules.add("减少模板化连接词");
            rules.add("避免连续三句使用相同结构");
        }
        if ("扩写".equals(rewriteType)) {
            rules.add("只补充解释性表达，不添加未经提供的案例和数据");
        }
        if ("缩写".equals(rewriteType)) {
            rules.add("压缩冗余限定语，保留关键论点");
        }
        return String.join("；", rules);
    }

    private String rewriteSentences(String text, String rewriteType, int beforeScore, String feedback) {
        String rawRewrite = aiRewriteService.rewriteWithFeedback(text, rewriteType, beforeScore, feedback);
        return cleanupTemplatePhrases(normalizeAiOutput(rawRewrite, rewriteType));
    }

    private String academicPolish(String text, String rewriteType) {
        String prefix = switch (rewriteType) {
            case "扩写" -> "在保持原有论点的基础上，";
            case "缩写" -> "";
            default -> "";
        };
        String polished = prefix + text;
        return polished
                .replace("非常", "较为")
                .replace("很多", "较多")
                .replace("大概", "可能")
                .replace("我认为", "本文认为");
    }

    private String humanizeExpression(String text, boolean useModelHumanize) {
        String adjusted = text;
        if (useModelHumanize) {
            adjusted = normalizeAiOutput(aiRewriteService.rewrite(text, "降低AI写作痕迹"), "降低AI写作痕迹");
        }
        adjusted = cleanupTemplatePhrases(adjusted);
        for (String word : BANNED_TEMPLATE_WORDS) {
            adjusted = adjusted.replace(word + "，", "").replace(word + "、", "").replace(word, "");
        }
        return adjusted.replaceAll("。{2,}", "。").trim();
    }

    private String cleanupTemplatePhrases(String text) {
        String adjusted = text == null ? "" : text;
        for (String phrase : BANNED_AI_PHRASES) {
            adjusted = adjusted.replace(phrase + "，", "")
                    .replace(phrase + "。", "。")
                    .replace(phrase, "");
        }
        return adjusted
                .replace("，因此，", "，")
                .replace("，所以，", "，")
                .replace("，此外，", "，")
                .replace("，同时，", "，")
                .replace("，从而，", "，")
                .replace("，进而，", "，")
                .replaceAll("，{2,}", "，")
                .trim();
    }

    private String normalizeAiOutput(String text, String rewriteType) {
        if (text == null) {
            return "";
        }
        String baseRewriteType = baseRewriteType(rewriteType);
        return text.replace("【" + rewriteType + "】优化结果：", "")
                .replace("【" + baseRewriteType + "】优化结果：", "")
                .trim();
    }

    private String baseRewriteType(String rewriteType) {
        if (rewriteType == null) {
            return "";
        }
        int index = rewriteType.indexOf('@');
        return index >= 0 ? rewriteType.substring(0, index) : rewriteType;
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

    private QualityCheckVO qualityCheck(String originalText, String rewrittenText) {
        QualityCheckVO vo = new QualityCheckVO();
        int originalLength = originalText.length();
        int rewrittenLength = rewrittenText.length();
        boolean lengthReasonable = originalLength == 0 || rewrittenLength >= originalLength * 0.35;
        boolean templateReduced = BANNED_TEMPLATE_WORDS.stream().noneMatch(rewrittenText::contains);

        vo.setMeaningPreserved(lengthReasonable);
        vo.setAcademicTone(!rewrittenText.contains("我觉得") && !rewrittenText.contains("随便"));
        vo.setTemplateReduced(templateReduced);
        vo.setFluent(rewrittenText.length() > 0 && !rewrittenText.contains("  "));

        List<String> issues = new ArrayList<>();
        if (!vo.isMeaningPreserved()) {
            issues.add("改写后文本过短，可能存在原意缺失");
        }
        if (!vo.isAcademicTone()) {
            issues.add("仍存在口语化表达");
        }
        if (!vo.isTemplateReduced()) {
            issues.add("仍包含明显模板化连接词");
        }
        if (!vo.isFluent()) {
            issues.add("文本流畅度需要继续检查");
        }
        vo.setIssues(issues);
        return vo;
    }
}
