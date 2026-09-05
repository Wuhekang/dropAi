package com.dropai.rewrite.external;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Source selection and output safety checks for the opt-in Daya rewrite route. */
final class DayaRewriteQualityRules {
    private static final int MIN_SIMILARITY_CHARACTERS = 40;
    private static final double RETRY_SIMILARITY = 0.72;
    private static final double RETRY_SHORT_RETAINED = 0.78;
    private static final double RETRY_SHORT_STRUCTURAL_RETAINED = 0.66;
    private static final double RETRY_SHORT_MAX_EDIT_SHARE = 0.48;
    private static final double RETRY_COPY_CONTAINMENT = 0.86;
    private static final Pattern PLACEHOLDER = Pattern.compile(
            "\\[\\[DROP_(?:AI|STYLE)_PROTECTED_[0-9]+]]");
    private static final Pattern REPEATED_PHRASE = Pattern.compile(
            "([\\p{IsHan}]{2,8})\\1");
    private static final Pattern REPEATED_FUNCTION_WORD = Pattern.compile(
            "(?:的的|将将|能能|仍然仍|能够能|可以可|需要需|已经已|这套这套|所有的所有|参考依据参考依据)");
    private static final Pattern DOUBLE_PUNCTUATION = Pattern.compile(
            "[。！？!?；;，、,:：]{2,}");
    private static final Pattern SENTENCE = Pattern.compile(
            "[^。！？!?；;\\r\\n]+(?:[。！？!?；;]+|$)");
    private static final Pattern SENTENCE_BOUNDARY = Pattern.compile("[。！？!?；;\\r\\n]+");
    private static final Pattern ENUMERATION_SHELL = Pattern.compile(
            "(?:主要|具体|大致|通常|一般)?(?:包括|包含|涵盖|涉及|分为|划分为|归纳为|概括为|"
                    + "体现在|表现为|主要有|具体有|有以下|如下)");
    private static final Pattern COUNTED_GROUP = Pattern.compile(
            "(?:共(?:有)?|存在|形成|设置|分成|分为|划分为)?\\s*"
                    + "[0-9０-９二两三四五六七八九十]+\\s*"
                    + "(?:个|类|项|方面|部分|阶段|环节|步骤|维度|模块|层面|问题|措施|路径)");
    private static final Pattern AXIS_WORD = Pattern.compile("(?:方面|层面|阶段|环节|步骤|维度|模块)");
    private static final Pattern PREDICATE_ANCHOR = Pattern.compile(
            "(?:需要|应当|应由|应按|应将|可以|可由|可按|可将|可在|可对|需由|需按|需将|"
                    + "通过|采用|用于|形成|实现|保障|提升|降低|减少|完成|建立|设置|负责|开展|进行|"
                    + "决定|对应|覆盖|反映|属于)");
    private static final String RESEARCH_CLAUSE = "[^。！？!?；;\\r\\n]";
    private static final Pattern RESEARCH_SCOPE_SHELL = Pattern.compile(
            "(?:本文|本研究)" + RESEARCH_CLAUSE + "{0,24}(?:围绕|聚焦|基于)");
    private static final Pattern RESEARCH_MODEL_SHELL = Pattern.compile(
            "构建" + RESEARCH_CLAUSE + "{0,16}(?:体系|模型)");
    private static final Pattern RESEARCH_RESULT_SHELL = Pattern.compile(
            "(?:研究|分析|评价|测算)?结果(?:显示|表明)");
    private static final Pattern RESEARCH_PROPOSAL_SHELL = Pattern.compile(
            "提出" + RESEARCH_CLAUSE + "{0,16}(?:建议|措施)");
    private static final Pattern RESEARCH_REFERENCE_SHELL = Pattern.compile(
            "提供" + RESEARCH_CLAUSE + "{0,16}(?:支撑|参考)");
    private static final Pattern ABSTRACT_CONTEXT = Pattern.compile("(?i)(?:中文摘要|英文摘要|摘要|abstract)");
    private static final Pattern METHOD_CONTEXT = Pattern.compile(
            "(?i)(?:研究内容|研究方法|技术路线|理论|原理|方法|模型|概念|定义|指标(?:选取|体系)|"
                    + "评价体系|权重|隶属|AHP|FCE|PDCA|LCC)");
    private static final Pattern SOP_CONTEXT = Pattern.compile(
            "(?:措施|优化|管理|控制|管控|防控|实施|保障|应对|改进|对策|治理)");
    private static final Pattern TABLE_CONTEXT = Pattern.compile("(?:表格长说明|图表说明|公式说明)");
    private static final Pattern CONCLUSION_CONTEXT = Pattern.compile(
            "(?:结论|展望|小结|预期效果|实施效果|效果分析|研究结论)");
    private static final Pattern LITERATURE_CONTEXT = Pattern.compile(
            "(?:文献综述|研究现状|国内外研究|研究进展|相关研究|文献回顾)");
    private static final Pattern DENSE_SEPARATOR = Pattern.compile("[，,、；;：:]");
    private static final Pattern PARALLEL_OR_FLOW_WORD = Pattern.compile(
            "(?:包括|包含|以及|同时|通过|采用|形成|建立|设置|开展|进行|负责|实现|"
                    + "核对|记录|审核|复核|整改|归档|考核)");
    private static final Pattern CITATION_OR_SCHOLAR = Pattern.compile(
            "(?:\\[[0-9０-９]{1,3}]|"
                    + "[\\p{IsHan}A-Za-z·]{2,20}(?:等)?(?:（|\\()[12][0-9]{3}(?:）|\\))|"
                    + "[\\p{IsHan}A-Za-z·]{2,20}(?:等)?(?:认为|提出|指出|发现|研究表明))");
    private static final Pattern ABSTRACT_SCOPE = Pattern.compile(
            "(?i)(?:本文|本研究|研究对象|以[^。！？!?]{0,24}为(?:例|对象)|this study|the study)");
    private static final Pattern ABSTRACT_METHOD = Pattern.compile(
            "(?i)(?:采用|运用|构建|评价|测算|分析|AHP|FCE|PDCA|method|model|evaluate|analy[sz]e)");
    private static final Pattern ABSTRACT_RESULT = Pattern.compile(
            "(?i)(?:结果(?:显示|表明)|研究发现|发现[^。！？!?]{0,16}(?:问题|不足)|results? (?:show|indicate)|findings?)");
    private static final Pattern ABSTRACT_PROPOSAL = Pattern.compile(
            "(?i)(?:提出|建议|优化|改进|对策|propose|recommend|suggest)");
    private static final Pattern ABSTRACT_VALUE = Pattern.compile(
            "(?i)(?:提供[^。！？!?]{0,20}(?:参考|借鉴|支撑)|理论意义|实践价值|reference|implication)");
    private static final Pattern METHOD_ANCHOR = Pattern.compile(
            "(?i)(?:AHP|FCE|PDCA|LCC|层次分析|模糊综合|权重|矩阵|隶属|模型|指标|"
                    + "步骤|流程|计算|赋值|打分|检验|评价|构建|确定)");
    private static final Pattern SOP_NODE = Pattern.compile(
            "(?:责任|台账|数据|阈值|预警|整改|复核|闭环|归档|考核|审核|记录|签证|部门|人员)");
    private static final Pattern TABLE_EXPLANATION = Pattern.compile(
            "(?:由表|表中|图中|如表|如图|数据显示|数值|均值|评分|占比|权重|排名|说明|表明)");
    private static final Pattern CONCLUSION_ANCHOR = Pattern.compile(
            "(?:研究|模型|指标|结果|发现|问题|建议|措施|不足|局限|展望|参考|价值)");
    private static final Pattern POLISHED_META_OPENING = Pattern.compile(
            "(?:^|[。！？!?])\\s*(?:站在[^，。！？!?]{1,18}(?:立场|角度|层面)上?"
                    + "|从[^，。！？!?]{1,24}(?:来看|角度看|层面看)"
                    + "|在具体应用中|为(?:了)?提高[^，。！？!?]{1,20}"
                    + "|依托(?:这套|上述|该)[^，。！？!?]{1,16}"
                    + "|这(?:套|一)(?:路线|机制|体系|路径|安排|做法)[^，。！？!?]{0,12})");
    private static final Pattern ROLE_OR_OBJECT = Pattern.compile(
            "(?:建设单位|施工单位|监理单位|设计单位|项目部|班组|部门|人员|负责人|主体|模块|"
                    + "材料|设备|记录|台账|指标|证据|数据|环节|目标|输入|资源|投入|检测|验证|组织|制度)");
    private static final Pattern ROLE_OR_OBJECT_ACTION = Pattern.compile(
            "(?:决定|对应|负责|承担|核对|核查|检查|归档|关联|映射|控制|验证|"
                    + "审核|复核|整改|签认|落实|覆盖|反映|属于|用于)");
    private static final Pattern REPEATED_MAPPING_PREDICATE = Pattern.compile(
            "(?:决定|对应|覆盖|反映|属于|用于)");
    private static final Pattern OUTCOME_CLAIM = Pattern.compile(
            "(?:确保|实现|形成|推动|支撑|保障|促进|提升|完善|弥补|减少)");
    private static final Pattern SCHEMA_SEPARATOR = Pattern.compile("[—－→⇒⟶⟹↦]");
    private static final Pattern ARGUMENT_CONTRAST = Pattern.compile("(?:尽管|虽然|但是|但|却)");
    private static final Pattern ARGUMENT_LIMIT = Pattern.compile("(?:若仅|如果只|难以|无法|不能仅)");
    private static final Pattern ARGUMENT_CONSEQUENCE = Pattern.compile("(?:因此|因而|所以|从而|由此|进而)");
    private static final Pattern ARGUMENT_ADDITION = Pattern.compile("(?:不仅|而且|同时|此外)");
    private static final Pattern RESULT_DATA_WORD = Pattern.compile(
            "(?:权重|得分|隶属度|一致性|敏感性|变化幅度|排序|排名|评价结果|计算结果)");
    private static final Pattern DIGIT_OR_PLACEHOLDER = Pattern.compile(
            "(?:[0-9０-９]|\\[\\[DROP_AI_PROTECTED_[0-9]+\\]\\])");
    private static final Pattern ENUMERATION_CONJUNCTION = Pattern.compile("(?:以及|还有|及|与|和)");
    private static final Pattern ABSTRACT_PROCESS_NOUN = Pattern.compile(
            "(?:体系|机制|路径|框架|流程|链条|闭环|矩阵|节点|环节|模块|接口|证据链|责任链)");
    private static final Pattern ABSTRACT_PROCESS_ACTION = Pattern.compile(
            "(?:构建|搭建|建立|形成|完善|衔接|联动|关联|映射|转化|闭合|贯通|嵌入|融入|"
                    + "推动|支撑|保障|实现|确保|落实|覆盖|跟踪|控制|设置|识别|提出|计算|"
                    + "编码|评级|评价|制定|推进)");
    private static final Pattern LINE_BREAK = Pattern.compile("[\\r\\n\\t\\u000B\\u000C\\u0085\\u2028\\u2029]");

    private DayaRewriteQualityRules() {
    }

    enum Risk {
        HIGH_SIMILARITY(4),
        IMPLICIT_ENUMERATION(3),
        ISOMORPHIC_SHORT_SENTENCES(3),
        RESIDUAL_SEQUENCE(5),
        FORMULAIC_RESEARCH_CHAIN(4),
        ABSTRACT_MODULE_CHAIN(4),
        METHOD_TUTORIAL_CHAIN(3),
        SOP_CONTROL_CHAIN(3),
        TABLE_EXPLANATION_CHAIN(3),
        CONCLUSION_RECAP_CHAIN(4),
        LITERATURE_REVIEW_CHAIN(3),
        DENSE_PARALLEL_CHAIN(3),
        POLISHED_META_OPENING(3),
        ROLE_ACTION_CHAIN(3),
        OUTCOME_CLAIM_CHAIN(3),
        SCHEMA_CHAIN(3),
        ARGUMENT_CLOSURE_CHAIN(3),
        RESULT_DATA_CHAIN(3),
        ABSTRACT_PROCESS_CHAIN(3),
        LONG_STRUCTURED_BODY(1),
        FRAGMENTED_LINE_CHAIN(3);

        private final int weight;

        Risk(int weight) {
            this.weight = weight;
        }
    }

    record Assessment(double similarity, Set<Risk> risks) {
        Assessment {
            risks = risks == null || risks.isEmpty() ? Set.of() : Set.copyOf(risks);
        }

        boolean requiresRecheck() {
            return !risks.isEmpty();
        }

        int riskScore() {
            return risks.stream().mapToInt(risk -> risk.weight).sum();
        }

        List<String> reasonCodes() {
            return risks.stream().sorted().map(Enum::name).toList();
        }
    }

    static Assessment assess(String original, String candidate) {
        return assess(original, candidate, "正文");
    }

    static Assessment assess(String original, String candidate, String context) {
        String source = comparableText(original);
        String draft = comparableText(candidate);
        double similarity = trigramDice(source, draft);
        EnumSet<Risk> risks = EnumSet.noneOf(Risk.class);
        if (isRetrySimilarity(source, draft, similarity)) risks.add(Risk.HIGH_SIMILARITY);
        if (hasImplicitEnumeration(candidate)) risks.add(Risk.IMPLICIT_ENUMERATION);
        if (hasIsomorphicShortSentences(candidate)) risks.add(Risk.ISOMORPHIC_SHORT_SENTENCES);
        if (DayaEnumerationRules.containsOrderingRisk(candidate)) risks.add(Risk.RESIDUAL_SEQUENCE);
        if (hasFormulaicResearchChain(candidate)) risks.add(Risk.FORMULAIC_RESEARCH_CHAIN);
        if (hasPolishedMetaOpening(candidate)) {
            risks.add(Risk.POLISHED_META_OPENING);
        }
        if (hasRoleActionChain(candidate)) risks.add(Risk.ROLE_ACTION_CHAIN);
        if (hasOutcomeClaimChain(candidate)) risks.add(Risk.OUTCOME_CLAIM_CHAIN);
        if (matcherCount(SCHEMA_SEPARATOR, candidate == null ? "" : candidate) >= 3) {
            risks.add(Risk.SCHEMA_CHAIN);
        }
        if (hasArgumentClosureChain(candidate)) risks.add(Risk.ARGUMENT_CLOSURE_CHAIN);
        if (hasResultDataChain(candidate)) risks.add(Risk.RESULT_DATA_CHAIN);
        if (hasAbstractProcessChain(candidate)) risks.add(Risk.ABSTRACT_PROCESS_CHAIN);
        if (hasLongStructuredBody(candidate)) risks.add(Risk.LONG_STRUCTURED_BODY);
        addContextualRisks(risks, candidate, context);
        return new Assessment(similarity, risks);
    }

    static int comparableLength(String text) {
        return comparableText(text).length();
    }

    static boolean hasLowerRisk(String original, String before, String after) {
        return hasLowerRisk(original, before, after, "正文");
    }

    static boolean hasLowerRisk(String original, String before, String after, String context) {
        Assessment beforeAssessment = assess(original, before, context);
        Assessment afterAssessment = assess(original, after, context);
        return substantiveRiskScore(afterAssessment) < substantiveRiskScore(beforeAssessment);
    }

    static void validateRewrite(String original, String rewritten, boolean enumeration) {
        String value = rewritten == null ? "" : rewritten.trim();
        if (containsLineBreak(value)) {
            throw new IllegalStateException("大雅改写结果含回车、软换行或制表符");
        }
        if (REPEATED_PHRASE.matcher(value).find()
                || REPEATED_FUNCTION_WORD.matcher(value).find()) {
            throw new IllegalStateException("大雅改写结果含相邻重复词或重复短语");
        }
        if (DOUBLE_PUNCTUATION.matcher(value).find()) {
            throw new IllegalStateException("大雅改写结果含连续同类标点");
        }
        if (!enumeration) {
            int originalRun = longestShortChineseSentenceRun(original);
            int rewrittenRun = longestShortChineseSentenceRun(value);
            if (rewrittenRun >= 4 && rewrittenRun > originalRun) {
                throw new IllegalStateException("大雅普通段落被改成连续整齐短句");
            }
        }
    }

    /** Final gate available to the Daya document processor after placeholders are restored. */
    static void validateFinal(String original, String rewritten) {
        validateFinal(original, rewritten, "正文");
    }

    static void validateFinal(String original, String rewritten, String context) {
        validateRequiredRewrite(original, rewritten);
        Assessment assessment = assess(original, rewritten, context);
        if (assessment.risks().contains(Risk.RESIDUAL_SEQUENCE)) {
            throw new IllegalStateException("大雅改写仍含成组顺序词");
        }
        if (assessment.risks().contains(Risk.IMPLICIT_ENUMERATION)) {
            throw new IllegalStateException("大雅改写仍含报数式隐性列举");
        }
        if (assessment.risks().contains(Risk.ISOMORPHIC_SHORT_SENTENCES)) {
            throw new IllegalStateException("大雅改写仍含连续同构短句");
        }
        boolean enumeration = DayaEnumerationRules.requiresBreak(original);
        if (!enumeration && assessment.risks().contains(Risk.FRAGMENTED_LINE_CHAIN)) {
            throw new IllegalStateException("大雅普通段仍被改成连续短句列举");
        }
        if (containsBlockingStyleRisk(assessment.risks())) {
            throw new IllegalStateException("大雅改写仍含完整报告链或过度专业的并列结构："
                    + String.join(",", assessment.reasonCodes()));
        }
    }

    /**
     * Non-negotiable acceptance checks for every narrative segment written to the document.
     * Heuristic risk labels are intentionally absent: they choose the review strategy, while
     * this gate ensures the result is a substantive, structurally safe rewrite.
     */
    static void validateRequiredRewrite(String original, String rewritten) {
        if (containsLineBreak(rewritten)) {
            throw new IllegalStateException("大雅改写结果含回车、软换行或制表符");
        }
        String source = comparableText(original);
        String candidate = comparableText(rewritten);
        if (!source.isEmpty() && source.equals(candidate)) {
            throw new IllegalStateException("大雅改写仅调整了标点或空白，未重建段落表达");
        }
        boolean enumeration = DayaEnumerationRules.requiresBreak(original);
        validateRewrite(original, rewritten, enumeration);
        DayaEnumerationRules.validateRewrite(original, rewritten);

        double similarity = trigramDice(source, candidate);
        if (isRetrySimilarity(source, candidate, similarity)) {
            throw new IllegalStateException("大雅改写与原段高度相似，仍属于近义词式微调");
        }
    }

    static boolean hasImplicitEnumeration(String text) {
        String value = text == null ? "" : text.trim();
        if (value.isEmpty()) return false;
        Matcher shell = ENUMERATION_SHELL.matcher(value);
        while (shell.find()) {
            String tail = firstSentenceTail(value, shell.end(), 120);
            if (COUNTED_GROUP.matcher(tail).find() || looksLikeEnumerationTail(tail)) return true;
        }
        Matcher counted = COUNTED_GROUP.matcher(value);
        if (counted.find()) {
            String tail = firstSentenceTail(value, counted.end(), 120);
            if (tail.startsWith("：") || tail.startsWith(":") || looksLikeEnumerationTail(tail)) return true;
        }
        return matcherCount(AXIS_WORD, value) >= 3 && separatorCount(value) >= 2;
    }

    static boolean hasIsomorphicShortSentences(String text) {
        String[] rawSentences = SENTENCE_BOUNDARY.split(text == null ? "" : text);
        Map<String, List<Integer>> lengthsByShape = new HashMap<>();
        for (String raw : rawSentences) {
            String sentence = raw.trim();
            int han = DayaEnumerationRules.countHan(sentence);
            if (han < 5 || han > 24) continue;
            Matcher anchors = PREDICATE_ANCHOR.matcher(sentence);
            List<String> matched = new ArrayList<>();
            while (anchors.find()) matched.add(anchors.group());
            if (matched.isEmpty()) continue;
            String shape = commaCount(sentence) + "|" + String.join("/", matched);
            lengthsByShape.computeIfAbsent(shape, ignored -> new ArrayList<>()).add(han);
        }
        for (List<Integer> lengths : lengthsByShape.values()) {
            if (lengths.size() < 3 || lengths.size() > 8) continue;
            double mean = lengths.stream().mapToInt(Integer::intValue).average().orElse(0);
            if (mean <= 0) continue;
            double variance = lengths.stream()
                    .mapToDouble(length -> Math.pow(length - mean, 2))
                    .average().orElse(0);
            if (Math.sqrt(variance) / mean <= 0.22) return true;
        }
        return false;
    }

    static boolean hasFormulaicResearchChain(String text) {
        String value = text == null ? "" : text.trim();
        if (value.isEmpty()) return false;
        int categories = 0;
        if (RESEARCH_SCOPE_SHELL.matcher(value).find()) categories++;
        if (RESEARCH_MODEL_SHELL.matcher(value).find()) categories++;
        if (RESEARCH_RESULT_SHELL.matcher(value).find()) categories++;
        if (RESEARCH_PROPOSAL_SHELL.matcher(value).find()) categories++;
        if (RESEARCH_REFERENCE_SHELL.matcher(value).find()) categories++;
        return categories >= 3;
    }

    private static boolean hasRoleActionChain(String text) {
        String value = text == null ? "" : text.trim();
        if (comparableText(value).length() < 45) return false;
        int matchedClauses = 0;
        for (String clause : value.split("[，,；;。！？!?]+")) {
            if (ROLE_OR_OBJECT.matcher(clause).find()
                    && ROLE_OR_OBJECT_ACTION.matcher(clause).find()) {
                matchedClauses++;
            }
        }
        return matchedClauses >= 4 || matcherCount(REPEATED_MAPPING_PREDICATE, value) >= 3;
    }

    private static boolean hasPolishedMetaOpening(String text) {
        String value = text == null ? "" : text.trim();
        return comparableText(value).length() >= 45
                && POLISHED_META_OPENING.matcher(value).find();
    }

    private static boolean hasOutcomeClaimChain(String text) {
        String value = text == null ? "" : text.trim();
        return comparableText(value).length() >= 60
                && matcherCount(OUTCOME_CLAIM, value) >= 3;
    }

    private static boolean hasArgumentClosureChain(String text) {
        String value = text == null ? "" : text.trim();
        if (comparableText(value).length() < 45) return false;
        int categories = 0;
        if (ARGUMENT_CONTRAST.matcher(value).find()) categories++;
        if (ARGUMENT_LIMIT.matcher(value).find()) categories++;
        if (ARGUMENT_CONSEQUENCE.matcher(value).find()) categories++;
        if (ARGUMENT_ADDITION.matcher(value).find()) categories++;
        return categories >= 3;
    }

    private static boolean hasResultDataChain(String text) {
        String value = text == null ? "" : text.trim();
        return comparableText(value).length() >= 20
                && DIGIT_OR_PLACEHOLDER.matcher(value).find()
                && matcherCount(RESULT_DATA_WORD, value) >= 3;
    }

    private static boolean hasAbstractProcessChain(String text) {
        String value = text == null ? "" : text.trim();
        return comparableText(value).length() >= 60
                && matcherCount(ABSTRACT_PROCESS_NOUN, value) >= 3
                && matcherCount(ABSTRACT_PROCESS_ACTION, value) >= 3;
    }

    private static boolean hasLongStructuredBody(String text) {
        String value = text == null ? "" : text.trim();
        if (comparableText(value).length() < 120) return false;
        return sentenceCount(value) >= 2
                || matcherCount(DENSE_SEPARATOR, value) >= 4
                || matcherCount(PREDICATE_ANCHOR, value) >= 2
                || matcherCount(ABSTRACT_PROCESS_NOUN, value) >= 2
                || (DIGIT_OR_PLACEHOLDER.matcher(value).find()
                && RESULT_DATA_WORD.matcher(value).find());
    }

    /** Context-aware report chains are rechecked and must be removed before a paragraph is stored. */
    private static void addContextualRisks(EnumSet<Risk> risks, String text, String context) {
        String value = text == null ? "" : text.trim();
        String section = context == null ? "" : context.trim();
        if (value.isEmpty()) return;

        int contentLength = comparableText(value).length();
        int separators = matcherCount(DENSE_SEPARATOR, value);
        int flowWords = matcherCount(PARALLEL_OR_FLOW_WORD, value);
        int sentences = sentenceCount(value);

        if (containsLineBreak(value) || hasIndependentShortSentenceChain(value)) {
            risks.add(Risk.FRAGMENTED_LINE_CHAIN);
        }

        if (ABSTRACT_CONTEXT.matcher(section).find()
                && contentLength >= 60
                && abstractModuleCount(value) >= 3) {
            risks.add(Risk.ABSTRACT_MODULE_CHAIN);
        }

        int methodAnchors = matcherCount(METHOD_ANCHOR, value);
        if (METHOD_CONTEXT.matcher(section).find()
                && contentLength >= 60
                && methodAnchors >= 3
                && (flowWords >= 2 || separators >= 4 || sentences >= 3)) {
            risks.add(Risk.METHOD_TUTORIAL_CHAIN);
        }

        int sopNodes = matcherCount(SOP_NODE, value);
        if (SOP_CONTEXT.matcher(section).find()
                && contentLength >= 50
                && sopNodes >= 4
                && (flowWords >= 3 || separators >= 5 || sentences >= 3)) {
            risks.add(Risk.SOP_CONTROL_CHAIN);
        }

        if (TABLE_CONTEXT.matcher(section).find()
                && contentLength >= 50
                && (matcherCount(TABLE_EXPLANATION, value) >= 2
                || separators >= 4
                || sentences >= 3)) {
            risks.add(Risk.TABLE_EXPLANATION_CHAIN);
        }

        if (CONCLUSION_CONTEXT.matcher(section).find()
                && contentLength >= 90
                && matcherCount(CONCLUSION_ANCHOR, value) >= 4
                && (sentences >= 3 || separators >= 5)) {
            risks.add(Risk.CONCLUSION_RECAP_CHAIN);
        }

        if (LITERATURE_CONTEXT.matcher(section).find()
                && contentLength >= 90
                && matcherCount(CITATION_OR_SCHOLAR, value) >= 2) {
            risks.add(Risk.LITERATURE_REVIEW_CHAIN);
        }

        if (contentLength >= 110 && separators >= 7 && flowWords >= 4) {
            risks.add(Risk.DENSE_PARALLEL_CHAIN);
        }
    }

    private static int abstractModuleCount(String value) {
        int modules = 0;
        if (ABSTRACT_SCOPE.matcher(value).find()) modules++;
        if (ABSTRACT_METHOD.matcher(value).find()) modules++;
        if (ABSTRACT_RESULT.matcher(value).find()) modules++;
        if (ABSTRACT_PROPOSAL.matcher(value).find()) modules++;
        if (ABSTRACT_VALUE.matcher(value).find()) modules++;
        return modules;
    }

    private static int sentenceCount(String value) {
        int count = 0;
        for (String sentence : SENTENCE_BOUNDARY.split(value)) {
            if (!sentence.isBlank()) count++;
        }
        return count;
    }

    private static boolean hasIndependentShortSentenceChain(String value) {
        int sentences = 0;
        int shortSentences = 0;
        for (String sentence : SENTENCE_BOUNDARY.split(value)) {
            String normalized = sentence.trim();
            if (normalized.isEmpty()) continue;
            sentences++;
            int han = DayaEnumerationRules.countHan(normalized);
            if (han >= 5 && han <= 24) shortSentences++;
        }
        return sentences >= 5 && shortSentences >= 5;
    }

    private static boolean containsBlockingStyleRisk(Set<Risk> risks) {
        return risks.stream().anyMatch(risk -> switch (risk) {
            case FORMULAIC_RESEARCH_CHAIN,
                    ABSTRACT_MODULE_CHAIN,
                    SOP_CONTROL_CHAIN,
                    TABLE_EXPLANATION_CHAIN,
                    CONCLUSION_RECAP_CHAIN,
                    DENSE_PARALLEL_CHAIN,
                    POLISHED_META_OPENING,
                    ROLE_ACTION_CHAIN,
                    OUTCOME_CLAIM_CHAIN,
                    SCHEMA_CHAIN,
                    ARGUMENT_CLOSURE_CHAIN,
                    RESULT_DATA_CHAIN,
                    ABSTRACT_PROCESS_CHAIN -> true;
            default -> false;
        });
    }

    private static int substantiveRiskScore(Assessment assessment) {
        return assessment.risks().stream()
                .filter(risk -> risk != Risk.LONG_STRUCTURED_BODY)
                .mapToInt(risk -> risk.weight)
                .sum();
    }

    private static boolean containsLineBreak(String text) {
        return LINE_BREAK.matcher(text == null ? "" : text).find();
    }

    private static int longestShortChineseSentenceRun(String text) {
        Matcher matcher = SENTENCE.matcher(text == null ? "" : text.trim());
        int longest = 0;
        int current = 0;
        while (matcher.find()) {
            int han = DayaEnumerationRules.countHan(matcher.group());
            if (han > 0 && han <= 20) {
                current++;
                longest = Math.max(longest, current);
            } else {
                current = 0;
            }
        }
        return longest;
    }

    private static boolean isRetrySimilarity(String source, String candidate, double similarity) {
        if (source.isEmpty() || candidate.isEmpty()) return false;
        if (source.equals(candidate)) return true;
        int comparableCharacters = Math.min(source.length(), candidate.length());
        if (trigramContainment(source, candidate) >= RETRY_COPY_CONTAINMENT) return true;
        if (comparableCharacters < MIN_SIMILARITY_CHARACTERS) {
            return isShortSurfaceEdit(source, candidate,
                    RETRY_SHORT_RETAINED,
                    RETRY_SHORT_STRUCTURAL_RETAINED,
                    RETRY_SHORT_MAX_EDIT_SHARE);
        }
        return similarity >= RETRY_SIMILARITY;
    }

    private static String comparableText(String text) {
        String withoutPlaceholders = PLACEHOLDER.matcher(text == null ? "" : text).replaceAll("");
        StringBuilder result = new StringBuilder(withoutPlaceholders.length());
        withoutPlaceholders.codePoints()
                .filter(Character::isLetterOrDigit)
                .map(Character::toLowerCase)
                .forEach(result::appendCodePoint);
        return result.toString().toLowerCase(Locale.ROOT);
    }

    private static double trigramDice(String left, String right) {
        if (left.isEmpty() || right.isEmpty()) return 0;
        if (left.equals(right)) return 1;
        if (left.length() < 3 || right.length() < 3) return 0;
        Map<String, Integer> leftCounts = trigramCounts(left);
        Map<String, Integer> rightCounts = trigramCounts(right);
        int intersection = 0;
        for (Map.Entry<String, Integer> entry : leftCounts.entrySet()) {
            intersection += Math.min(entry.getValue(), rightCounts.getOrDefault(entry.getKey(), 0));
        }
        return (2.0 * intersection) / ((left.length() - 2) + (right.length() - 2));
    }

    /**
     * Detects an original span copied intact into a much longer or shorter candidate. Dice alone
     * can be diluted by appending text, so compare the shared trigrams with the shorter side too.
     */
    private static double trigramContainment(String left, String right) {
        if (left.length() < 3 || right.length() < 3) return 0;
        Map<String, Integer> leftCounts = trigramCounts(left);
        Map<String, Integer> rightCounts = trigramCounts(right);
        int intersection = 0;
        for (Map.Entry<String, Integer> entry : leftCounts.entrySet()) {
            intersection += Math.min(entry.getValue(), rightCounts.getOrDefault(entry.getKey(), 0));
        }
        return (double) intersection / Math.min(left.length() - 2, right.length() - 2);
    }

    /**
     * Short paragraphs need both retention and edit-distance checks because replacing a few nouns
     * can disrupt trigrams while leaving the original sentence skeleton intact.
     */
    private static boolean isShortSurfaceEdit(String left, String right,
                                              double retainedLimit,
                                              double structuralRetainedLimit,
                                              double maximumEditShare) {
        int shortest = Math.min(left.length(), right.length());
        int longest = Math.max(left.length(), right.length());
        if (shortest == 0 || longest == 0) return false;
        double retained = (double) longestCommonSubsequenceLength(left, right) / shortest;
        double editShare = (double) levenshteinDistance(left, right) / longest;
        return retained >= retainedLimit
                || (retained >= structuralRetainedLimit && editShare <= maximumEditShare);
    }

    private static int levenshteinDistance(String left, String right) {
        int[] previous = new int[right.length() + 1];
        int[] current = new int[right.length() + 1];
        for (int column = 0; column <= right.length(); column++) previous[column] = column;
        for (int row = 1; row <= left.length(); row++) {
            current[0] = row;
            for (int column = 1; column <= right.length(); column++) {
                int substitution = previous[column - 1]
                        + (left.charAt(row - 1) == right.charAt(column - 1) ? 0 : 1);
                current[column] = Math.min(
                        Math.min(previous[column] + 1, current[column - 1] + 1),
                        substitution);
            }
            int[] swap = previous;
            previous = current;
            current = swap;
        }
        return previous[right.length()];
    }

    private static int longestCommonSubsequenceLength(String left, String right) {
        int[] previous = new int[right.length() + 1];
        int[] current = new int[right.length() + 1];
        for (int row = 1; row <= left.length(); row++) {
            for (int column = 1; column <= right.length(); column++) {
                current[column] = left.charAt(row - 1) == right.charAt(column - 1)
                        ? previous[column - 1] + 1
                        : Math.max(previous[column], current[column - 1]);
            }
            int[] swap = previous;
            previous = current;
            current = swap;
            java.util.Arrays.fill(current, 0);
        }
        return previous[right.length()];
    }

    private static Map<String, Integer> trigramCounts(String value) {
        Map<String, Integer> result = new HashMap<>();
        for (int index = 0; index <= value.length() - 3; index++) {
            result.merge(value.substring(index, index + 3), 1, Integer::sum);
        }
        return result;
    }

    private static int separatorCount(String value) {
        int count = 0;
        for (int index = 0; index < value.length(); index++) {
            char current = value.charAt(index);
            if (current == '、' || current == '；' || current == ';'
                    || current == '，' || current == ',') count++;
        }
        return count;
    }

    private static boolean looksLikeEnumerationTail(String value) {
        int separators = separatorCount(value);
        return separators >= 2
                || (separators >= 1 && ENUMERATION_CONJUNCTION.matcher(value).find());
    }

    private static String firstSentenceTail(String value, int start, int limit) {
        int end = Math.min(value.length(), start + limit);
        for (int index = start; index < end; index++) {
            if ("。！？!?；;\r\n".indexOf(value.charAt(index)) >= 0) {
                end = index;
                break;
            }
        }
        return value.substring(start, end);
    }

    private static int commaCount(String value) {
        int count = 0;
        for (int index = 0; index < value.length(); index++) {
            char current = value.charAt(index);
            if (current == '，' || current == ',') count++;
        }
        return count;
    }

    private static int matcherCount(Pattern pattern, String value) {
        int count = 0;
        Matcher matcher = pattern.matcher(value);
        while (matcher.find()) count++;
        return count;
    }
}
