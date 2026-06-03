package com.dropai.rewrite.utils;

import com.dropai.rewrite.vo.AiAnalyzeVO;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public final class AiRiskAnalyzeUtil {

    private static final List<String> TEMPLATE_WORDS = Arrays.asList(
            "首先", "其次", "最后", "综上所述", "值得注意的是", "随着", "由此可见", "具有重要意义"
    );

    private static final List<String> CONNECTORS = Arrays.asList(
            "因此", "所以", "然而", "此外", "同时", "并且", "进而", "从而", "由此可见"
    );

    private AiRiskAnalyzeUtil() {
    }

    public static AiAnalyzeVO analyze(String text) {
        String safeText = text == null ? "" : text.trim();
        int score = 12;
        List<String> suggestions = new ArrayList<>();

        int templateHits = countHits(safeText, TEMPLATE_WORDS);
        if (templateHits >= 2) {
            score += Math.min(36, templateHits * 9);
            suggestions.add("减少模板化连接词");
        }

        int connectorHits = countHits(safeText, CONNECTORS);
        if (connectorHits >= 4) {
            score += Math.min(24, connectorHits * 4);
            suggestions.add("控制连接词密度，避免表达过于机械");
        }

        List<Integer> sentenceLengths = sentenceLengths(safeText);
        if (sentenceLengths.size() >= 4 && isLengthTooAverage(sentenceLengths)) {
            score += 12;
            suggestions.add("调整句式长短");
        }

        if (hasTemplateStructure(safeText)) {
            score += 20;
            suggestions.add("弱化首先、其次、最后式段落结构");
        }

        if (safeText.length() > 120 && suggestions.size() < 3) {
            suggestions.add("增加具体案例或数据表达");
        }
        if (suggestions.isEmpty()) {
            suggestions.add("当前文本模板化特征较少，可继续保持自然表达");
        }

        AiAnalyzeVO vo = new AiAnalyzeVO();
        vo.setScore(Math.min(100, score));
        vo.setLevel(level(vo.getScore()));
        vo.setSuggestions(suggestions);
        return vo;
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

    private static List<Integer> sentenceLengths(String text) {
        List<Integer> lengths = new ArrayList<>();
        for (String sentence : text.split("[。！？!?；;\\n]+")) {
            String trimmed = sentence.trim();
            if (!trimmed.isEmpty()) {
                lengths.add(trimmed.length());
            }
        }
        return lengths;
    }

    private static boolean isLengthTooAverage(List<Integer> lengths) {
        double average = lengths.stream().mapToInt(Integer::intValue).average().orElse(0);
        double variance = 0;
        for (Integer length : lengths) {
            variance += Math.pow(length - average, 2);
        }
        double standardDeviation = Math.sqrt(variance / lengths.size());
        return average > 18 && standardDeviation < average * 0.22;
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
