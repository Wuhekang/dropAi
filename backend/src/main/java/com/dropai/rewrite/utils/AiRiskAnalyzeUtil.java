package com.dropai.rewrite.utils;

import com.dropai.rewrite.vo.AiAnalyzeVO;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class AiRiskAnalyzeUtil {

    private static final List<String> TEMPLATE_WORDS = Arrays.asList(
            "首先", "其次", "最后", "综上所述", "值得注意的是", "随着", "由此可见", "具有重要意义",
            "本文旨在", "研究表明", "提供参考", "现实意义", "实践价值", "理论基础"
    );

    private static final List<String> CONNECTORS = Arrays.asList(
            "因此", "所以", "然而", "此外", "同时", "并且", "进而", "从而", "由此可见",
            "基于此", "为了", "进一步", "另一方面", "总而言之", "换言之"
    );

    private static final List<String> ABSTRACT_PHRASES = Arrays.asList(
            "全流程", "全过程", "全周期", "多维度", "一体化", "系统性", "整体性", "内在机制",
            "有效提升", "优化效果", "用户体验提升", "管理效率提升", "实践启示", "有效路径",
            "重要支撑", "有力保障", "积极作用", "充分体现"
    );

    private static final List<String> PATTERN_PHRASES = Arrays.asList(
            "通过", "实现", "基于", "构建", "优化", "提升", "保障", "促进", "完善", "分析"
    );

    private static final List<String> CONCRETE_MARKERS = Arrays.asList(
            "模块", "字段", "表", "接口", "页面", "用户", "管理员", "员工", "考勤", "绩效",
            "数据库", "Spring", "Java", "MySQL", "Vue", "Controller", "Service", "Mapper",
            "登录", "查询", "新增", "删除", "修改", "审核", "统计", "导出"
    );

    private AiRiskAnalyzeUtil() {
    }

    public static AiAnalyzeVO analyze(String text) {
        String safeText = text == null ? "" : text.trim();
        List<String> sentences = sentences(safeText);
        int score = 8;
        List<String> suggestions = new ArrayList<>();

        int regularityScore = sentenceRegularityScore(sentences);
        score += regularityScore;
        if (regularityScore >= 14) {
            suggestions.add("自查-句式规整度偏高：保留长短句差异，避免每句结构过于相似");
        }

        int logicScore = logicDensityScore(safeText, sentences);
        score += logicScore;
        if (logicScore >= 12) {
            suggestions.add("自查-逻辑词密度偏高：减少因此、此外、同时、进而等显性连接");
        }

        int voiceScore = voicePatternScore(safeText, sentences);
        score += voiceScore;
        if (voiceScore >= 12) {
            suggestions.add("自查-语态特征偏模板：减少连续的通过、基于、实现、提升式表达");
        }

        int vocabularyScore = vocabularyScore(safeText);
        score += vocabularyScore;
        if (vocabularyScore >= 10) {
            suggestions.add("自查-词汇分布偏集中：减少抽象套话，保留具体模块和操作对象");
        }

        int argumentScore = argumentDepthScore(safeText, sentences);
        score += argumentScore;
        if (argumentScore >= 12) {
            suggestions.add("自查-论证深度偏空泛：避免只有意义、价值、提升，补回具体对象或删减总结腔");
        }

        int templateHits = countHits(safeText, TEMPLATE_WORDS);
        if (templateHits >= 2) {
            score += Math.min(18, templateHits * 5);
            suggestions.add("减少模板化开头、结尾和论文万能句");
        }

        if (hasTemplateStructure(safeText)) {
            score += 14;
            suggestions.add("弱化首先、其次、最后式段落结构");
        }

        if (suggestions.isEmpty()) {
            suggestions.add("当前文本自查风险较低，可继续保持轻量、具体、不过度规整的表达");
        }

        AiAnalyzeVO vo = new AiAnalyzeVO();
        vo.setScore(Math.min(100, score));
        vo.setLevel(level(vo.getScore()));
        vo.setSuggestions(suggestions);
        return vo;
    }

    private static int sentenceRegularityScore(List<String> sentences) {
        List<Integer> lengths = sentences.stream().map(String::length).filter(length -> length > 0).toList();
        if (lengths.size() < 3) {
            return 0;
        }
        double average = lengths.stream().mapToInt(Integer::intValue).average().orElse(0);
        double variance = 0;
        for (Integer length : lengths) {
            variance += Math.pow(length - average, 2);
        }
        double standardDeviation = Math.sqrt(variance / lengths.size());
        int score = 0;
        if (average > 18 && standardDeviation < average * 0.24) {
            score += 14;
        }
        int similarStarts = similarSentenceStarts(sentences);
        if (similarStarts >= 2) {
            score += Math.min(12, similarStarts * 4);
        }
        return score;
    }

    private static int logicDensityScore(String text, List<String> sentences) {
        int connectorHits = countHits(text, CONNECTORS);
        int sentenceCount = Math.max(1, sentences.size());
        double density = connectorHits * 1.0 / sentenceCount;
        if (connectorHits >= 5 || density >= 0.8) {
            return 18;
        }
        if (connectorHits >= 3 || density >= 0.5) {
            return 12;
        }
        return connectorHits >= 2 ? 6 : 0;
    }

    private static int voicePatternScore(String text, List<String> sentences) {
        int patternHits = countHits(text, PATTERN_PHRASES);
        int repeatedPatterns = repeatedSentencePatterns(sentences);
        int score = Math.min(16, patternHits * 2);
        if (repeatedPatterns >= 2) {
            score += Math.min(12, repeatedPatterns * 4);
        }
        if (text.contains("通过") && text.contains("实现") && text.contains("提升")) {
            score += 8;
        }
        return Math.min(26, score);
    }

    private static int vocabularyScore(String text) {
        int abstractHits = countHits(text, ABSTRACT_PHRASES);
        int concreteHits = countHits(text, CONCRETE_MARKERS);
        int score = Math.min(18, abstractHits * 4);
        if (text.length() > 120 && concreteHits <= 1 && abstractHits >= 2) {
            score += 10;
        }
        if (uniqueCharRatio(text) < 0.34 && text.length() > 80) {
            score += 6;
        }
        return Math.min(24, score);
    }

    private static int argumentDepthScore(String text, List<String> sentences) {
        int score = 0;
        boolean hasConclusionTone = hasAny(text, "具有", "意义", "价值", "作用", "提升", "优化", "保障");
        boolean lacksConcreteMarkers = countHits(text, CONCRETE_MARKERS) == 0;
        if (text.length() > 100 && hasConclusionTone && lacksConcreteMarkers) {
            score += 16;
        }
        long polishedLongSentences = sentences.stream()
                .filter(sentence -> sentence.length() >= 35)
                .filter(sentence -> hasAny(sentence, "通过", "基于", "从而", "实现", "提升", "促进"))
                .count();
        if (polishedLongSentences >= 2) {
            score += 10;
        }
        return score;
    }

    private static int countHits(String text, List<String> words) {
        int count = 0;
        for (String word : words) {
            int index = text.indexOf(word);
            while (index >= 0) {
                count++;
                index = text.indexOf(word, index + word.length());
            }
        }
        return count;
    }

    private static List<String> sentences(String text) {
        List<String> sentences = new ArrayList<>();
        for (String sentence : text.split("[。！？!?；;\\n]+")) {
            String trimmed = sentence.trim();
            if (!trimmed.isEmpty()) {
                sentences.add(trimmed);
            }
        }
        return sentences;
    }

    private static int similarSentenceStarts(List<String> sentences) {
        int count = 0;
        String previous = "";
        for (String sentence : sentences) {
            String current = sentence.length() <= 4 ? sentence : sentence.substring(0, 4);
            if (!previous.isBlank() && previous.equals(current)) {
                count++;
            }
            previous = current;
        }
        return count;
    }

    private static int repeatedSentencePatterns(List<String> sentences) {
        int count = 0;
        for (String sentence : sentences) {
            if ((sentence.contains("通过") && sentence.contains("实现"))
                    || (sentence.contains("基于") && sentence.contains("构建"))
                    || (sentence.contains("在") && sentence.contains("基础上"))) {
                count++;
            }
        }
        return Math.max(0, count - 1);
    }

    private static double uniqueCharRatio(String text) {
        String compact = text.replaceAll("\\s+", "");
        if (compact.isBlank()) {
            return 1;
        }
        Set<Character> chars = new HashSet<>();
        for (char ch : compact.toCharArray()) {
            chars.add(ch);
        }
        return chars.size() * 1.0 / compact.length();
    }

    private static boolean hasAny(String text, String... words) {
        for (String word : words) {
            if (text.contains(word)) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasTemplateStructure(String text) {
        return text.contains("首先") && text.contains("其次") && (text.contains("最后") || text.contains("综上所述"));
    }

    private static String level(int score) {
        if (score >= 70) {
            return "较高";
        }
        if (score >= 45) {
            return "中等";
        }
        return "较低";
    }
}
