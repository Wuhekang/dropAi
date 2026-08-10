package com.dropai.rewrite.service.writing;

import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

@Service
public class ChineseReferenceSearchPlanService {
    public Map<String, Object> buildPlan(ReferenceSearchQuery query) {
        List<String> core = limit(unique(words(query.joinedChineseKeywords())), 10);
        if (core.size() < 5) {
            core = limit(unique(List.of("人工智能", "职业教育", "高职学生", "就业能力", "人才培养", "数字技能", "就业质量")), 10);
        }
        List<String> synonyms = List.of("数字化转型", "智能技术", "产教融合", "职业能力", "职业素养", "人才培养模式", "就业竞争力");
        List<String> chineseQueries = expandChineseQueries(query, core, synonyms);
        List<String> englishQueries = expandEnglishQueries(query);
        List<String> global = new ArrayList<>();
        global.add(String.join(" ", limit(core, 4)) + " 期刊");
        global.add(String.join(" ", limit(core, 3)) + " 研究");
        global.add("site:kns.cnki.net " + String.join(" ", limit(core, 4)));
        global.add("site:cnki.net " + String.join(" ", limit(core, 4)));
        global.add("site:cbpt.cnki.net " + String.join(" ", limit(core, 3)));
        global.add("site:edu.cn " + String.join(" ", limit(core, 4)) + " 学报");
        global.add(String.join(" ", limit(core, 3)) + " DOI");
        List<String> coreKeywords = core;
        List<Map<String, Object>> chapterQueries = query.chapterTitles().stream()
                .filter(title -> title != null && !title.isBlank())
                .map(title -> Map.<String, Object>of(
                        "chapterId", "",
                        "chapterTitle", title,
                        "queries", List.of(title + " " + String.join(" ", limit(coreKeywords, 3)), title + " 期刊 学报")
                ))
                .toList();
        return Map.of(
                "coreKeywords", core,
                "synonyms", synonyms,
                "chineseQueries", chineseQueries,
                "englishQueries", englishQueries,
                "chineseQueryCount", chineseQueries.size(),
                "englishQueryCount", englishQueries.size(),
                "globalQueries", limit(global, 10),
                "chapterQueries", chapterQueries,
                "exactTitleQueries", List.of("\"" + query.title() + "\"", "\"" + query.title() + "\" 作者", "\"" + query.title() + "\" 期刊"),
                "siteQueries", List.of(
                        "site:kns.cnki.net " + String.join(" ", limit(core, 4)),
                        "site:cnki.net " + String.join(" ", limit(core, 4)),
                        "site:cbpt.cnki.net " + String.join(" ", limit(core, 3)),
                        "site:edu.cn " + String.join(" ", limit(core, 4))
                )
        );
    }

    private List<String> expandChineseQueries(ReferenceSearchQuery query, List<String> core, List<String> synonyms) {
        String topic = query.title();
        String shortCore = String.join(" ", limit(core, 4));
        List<String> values = new ArrayList<>(List.of(
                topic, topic + " 研究", topic + " 现状", topic + " 路径", topic + " 实证研究",
                shortCore + " 理论研究", shortCore + " 影响因素", shortCore + " 对策研究",
                shortCore + " 评价体系", shortCore + " 应用实践"
        ));
        synonyms.stream().limit(5).forEach(word -> values.add(shortCore + " " + word));
        return limit(unique(values), 15);
    }

    private List<String> expandEnglishQueries(ReferenceSearchQuery query) {
        String englishCore = query.joinedKeywords();
        return unique(List.of(
                englishCore, englishCore + " research", englishCore + " empirical study",
                englishCore + " current status", englishCore + " influencing factors",
                englishCore + " improvement pathways", englishCore + " evaluation framework",
                englishCore + " educational practice", englishCore + " systematic review",
                englishCore + " international comparison", englishCore + " digital transformation",
                englishCore + " competency development"
        ));
    }

    private List<String> words(String text) {
        List<String> result = new ArrayList<>();
        for (String part : text.split("[\\s,，、:：;；。！？()（）]+")) {
            String value = part.trim();
            if (value.length() >= 2 && value.length() <= 12) result.add(value);
        }
        return result;
    }

    private List<String> unique(List<String> values) {
        return new ArrayList<>(new LinkedHashSet<>(values));
    }

    private <T> List<T> limit(List<T> values, int max) {
        return values.stream().limit(max).toList();
    }
}
