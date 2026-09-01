package com.dropai.rewrite.service.writing;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Builds provider-safe topic queries and applies a provider-independent relevance gate.
 *
 * <p>Public catalogue scores are only meaningful inside their own provider. They must never
 * be treated as proof that a work is about the user's topic. This class deliberately favours
 * precision: an unverifiable cross-language match is omitted instead of being returned and
 * charged as a literature result.</p>
 */
public final class LiteratureTopicMatcher {
    private static final Pattern LATIN_WORD = Pattern.compile("[a-z][a-z0-9-]{2,}");
    private static final Pattern QUOTED_PHRASE = Pattern.compile("[\"']([^\"']{3,120})[\"']");
    private static final Set<String> ENGLISH_STOP_WORDS = Set.of(
            "about", "against", "analysis", "based", "background", "case", "effect", "english",
            "from", "impact", "method", "paper", "papers", "path", "research", "study", "the",
            "under", "using", "with", "journal", "academic", "literature");
    private static final List<String> CHINESE_NOISE = List.of(
            "中文学术文献", "中文文献", "文献中心独立检索", "背景下", "视角下", "语境下", "情境下",
            "基于", "关于", "相关研究", "研究", "分析", "探讨", "探索", "提升路径", "优化路径", "路径研究",
            "对策研究", "策略研究", "机制研究", "影响研究", "应用研究", "实践研究", "启示", "为例");
    private static final List<String> CHINESE_TOPIC_GLUE = List.of(
            "作用机制", "影响机制", "协同机制", "实现路径", "发展路径", "提升策略", "优化策略",
            "设计与实现", "现状及对策", "问题与对策", "影响因素", "作用", "机制", "影响", "关系",
            "关联", "赋能", "驱动", "促进", "推动", "提升", "优化", "改进", "构建", "设计", "实现",
            "开发", "评价", "评估", "测度", "发展", "现状", "问题", "对策", "策略", "路径", "模式",
            "视角", "背景", "时代", "融合", "协同", "转型", "应用", "实践", "及其", "以及", "面向",
            "针对", "下的", "中的");
    private static final Pattern CHINESE_CONNECTORS_ONLY = Pattern.compile("[与和及对在中下的]+");

    /** Ordered by common thesis wording. Each entry is one concept group with English aliases. */
    private static final List<Concept> CONCEPTS = List.of(
            concept("数字经济", "digital economy"),
            concept("数字化转型", "digital transformation", "digitalization transformation"),
            concept("数字金融", "digital finance", "fintech"),
            concept("平台经济", "platform economy"),
            concept("共享经济", "sharing economy"),
            concept("实体经济", "real economy"),
            concept("人工智能", "artificial intelligence", "AI"),
            concept("生成式人工智能", "generative artificial intelligence", "generative AI"),
            concept("机器学习", "machine learning"),
            concept("深度学习", "deep learning"),
            concept("大数据", "big data"),
            concept("云计算", "cloud computing"),
            concept("区块链", "blockchain"),
            concept("物联网", "internet of things", "IoT"),
            concept("电子商务", "electronic commerce", "e-commerce"),
            concept("跨境电商", "cross-border e-commerce"),
            concept("社交媒体", "social media"),
            concept("中小企业", "small and medium-sized enterprises", "small and medium enterprises", "SME", "SMEs"),
            concept("小微企业", "micro and small enterprises", "small businesses"),
            concept("民营企业", "private enterprises", "private firms"),
            concept("制造企业", "manufacturing enterprises", "manufacturing firms"),
            concept("家族企业", "family business", "family firms"),
            concept("企业管理", "business management", "enterprise management"),
            concept("工商管理", "business administration"),
            concept("人力资源", "human resources", "human resource management"),
            concept("绩效管理", "performance management"),
            concept("薪酬管理", "compensation management"),
            concept("员工激励", "employee motivation", "employee incentives"),
            concept("组织行为", "organizational behavior"),
            concept("组织韧性", "organizational resilience"),
            concept("企业韧性", "enterprise resilience", "firm resilience"),
            concept("供应链韧性", "supply chain resilience"),
            concept("供应链管理", "supply chain management"),
            concept("物流管理", "logistics management"),
            concept("库存管理", "inventory management"),
            concept("财务管理", "financial management"),
            concept("风险管理", "risk management"),
            concept("内部控制", "internal control"),
            concept("审计质量", "audit quality"),
            concept("融资约束", "financing constraints"),
            concept("资本结构", "capital structure"),
            concept("公司治理", "corporate governance"),
            concept("企业创新", "firm innovation", "enterprise innovation"),
            concept("创新能力", "innovation capability", "innovation capacity"),
            concept("技术创新", "technological innovation"),
            concept("绿色创新", "green innovation"),
            concept("商业模式", "business model"),
            concept("市场营销", "marketing"),
            concept("消费者行为", "consumer behavior"),
            concept("购买意愿", "purchase intention"),
            concept("品牌忠诚", "brand loyalty"),
            concept("客户满意度", "customer satisfaction"),
            concept("服务质量", "service quality"),
            concept("乡村振兴", "rural revitalization"),
            concept("共同富裕", "common prosperity"),
            concept("高质量发展", "high-quality development"),
            concept("绿色发展", "green development"),
            concept("可持续发展", "sustainable development"),
            concept("碳排放", "carbon emissions"),
            concept("碳中和", "carbon neutrality"),
            concept("绿色金融", "green finance"),
            concept("普惠金融", "financial inclusion", "inclusive finance"),
            concept("产业升级", "industrial upgrading"),
            concept("产业结构", "industrial structure"),
            concept("区域经济", "regional economy"),
            concept("经济增长", "economic growth"),
            concept("全要素生产率", "total factor productivity", "TFP"),
            concept("职业教育", "vocational education"),
            concept("职业院校", "vocational colleges", "vocational schools"),
            concept("高职院校", "higher vocational colleges"),
            concept("大学生", "college students", "university students"),
            concept("就业能力", "employability"),
            concept("就业质量", "employment quality"),
            concept("就业", "employment", "employability"),
            concept("教育公平", "educational equity", "education equity"),
            concept("在线教育", "online education"),
            concept("学习投入", "learning engagement", "student engagement"),
            concept("心理健康", "mental health"),
            concept("用户体验", "user experience"),
            concept("信息系统", "information systems"),
            concept("管理系统", "management information system", "management system"),
            concept("推荐系统", "recommender systems", "recommendation systems"),
            concept("数据治理", "data governance"),
            concept("知识管理", "knowledge management"),
            concept("公共管理", "public administration", "public management"),
            concept("政府治理", "government governance", "public governance"),
            concept("基层治理", "grassroots governance", "community governance"),
            concept("社区治理", "community governance"),
            concept("旅游管理", "tourism management"),
            concept("酒店管理", "hotel management", "hospitality management"),
            concept("文化旅游", "cultural tourism"),
            concept("直播电商", "livestream e-commerce", "live commerce"),
            concept("短视频", "short video"),
            concept("新能源汽车", "new energy vehicles", "electric vehicles"),
            concept("智慧城市", "smart city", "smart cities")
    );

    public String providerQuery(ReferenceSearchQuery query) {
        if (query.hasProviderKeywordsOverride()) return query.providerKeywords();
        String language = requestedLanguage(query);
        if ("MIXED".equals(language)) {
            List<ConceptGroup> groups = englishGroups(query);
            if (!groups.isEmpty()) {
                return providerGroups(groups).stream()
                        .map(group -> quoteIfPhrase(group.aliases().get(0)))
                        .reduce((left, right) -> left + " " + right).orElse("");
            }
        }
        if ("EN".equals(language)) {
            List<ConceptGroup> groups = englishGroups(query);
            if (groups.isEmpty()) return englishWords(englishRelevanceSource(query));
            return providerGroups(groups).stream()
                    .map(group -> quoteIfPhrase(group.aliases().get(0)))
                    .reduce((left, right) -> left + " " + right).orElse("");
        }
        List<ConceptGroup> groups = englishGroups(query);
        if (!groups.isEmpty()) {
            return providerGroups(groups).stream().map(ConceptGroup::chinese)
                    .reduce((left, right) -> left + " " + right).orElse("");
        }
        String topic = cleanChineseTopic(query.title());
        if (!topic.isBlank()) return topic;
        return cleanChineseTopic(String.join(" ", safeList(query.keywords())));
    }

    public boolean hasReliablePlan(ReferenceSearchQuery query, String language) {
        if ("ZH".equalsIgnoreCase(language)) return normalizeChinese(cleanChineseTopic(query.title())).length() >= 2;
        if (query.hasProviderKeywordsOverride() && needsEnglishPlanning(query)) {
            return englishTerms(query.providerKeywords()).size() >= 2;
        }
        return !englishGroups(query).isEmpty() || englishTerms(englishRelevanceSource(query)).size() >= 2;
    }

    /**
     * True when a Chinese title still contains substantive Han text after every mapped concept
     * and common thesis connective has been removed. In that case a partial deterministic query
     * must not be treated as a complete representation of the topic.
     */
    public boolean needsEnglishPlanning(ReferenceSearchQuery query) {
        String title = value(query.title());
        if (normalizeChinese(title).isBlank()) return false;
        String residual = cleanChineseTopic(title);
        List<Concept> mappedConcepts = CONCEPTS.stream()
                .filter(concept -> title.contains(concept.chinese()))
                .sorted((left, right) -> Integer.compare(right.chinese().length(), left.chinese().length()))
                .toList();
        for (Concept concept : mappedConcepts) {
            residual = residual.replace(concept.chinese(), " ");
        }
        for (String glue : CHINESE_TOPIC_GLUE) residual = residual.replace(glue, " ");
        String normalizedResidual = normalizeChinese(residual);
        return normalizedResidual.length() >= 2
                && !CHINESE_CONNECTORS_ONLY.matcher(normalizedResidual).matches();
    }

    public boolean isRelevant(ReferenceSearchQuery query, ReferenceCandidate candidate, String language) {
        return score(query, candidate, language) >= threshold(query, language);
    }

    public double score(ReferenceSearchQuery query, ReferenceCandidate candidate, String language) {
        if (candidate == null) return 0;
        String title = value(candidate.title());
        String details = String.join(" ", title, value(candidate.abstractText()), value(candidate.sourceSnippet()));
        if ("EN".equalsIgnoreCase(language)) return englishScore(query, details);
        return chineseScore(query, details);
    }

    private double englishScore(ReferenceSearchQuery query, String candidateText) {
        String haystack = normalizeEnglish(candidateText);
        List<ConceptGroup> groups = query.hasProviderKeywordsOverride() && needsEnglishPlanning(query)
                ? List.of()
                : englishGroups(query);
        if (!groups.isEmpty()) {
            if (!matchesEnglishGroup(haystack, groups.get(0))) return 0;
            int secondaryMatches = (int) groups.stream().skip(1)
                    .filter(group -> matchesEnglishGroup(haystack, group)).count();
            if (groups.size() > 1 && secondaryMatches == 0) return 0;
            int matched = 1 + secondaryMatches;
            double coverage = (double) matched / groups.size();
            return Math.min(1.0, 0.55 + coverage * 0.45);
        }

        String relevanceSource = englishRelevanceSource(query);
        List<String> phrases = quotedPhrases(relevanceSource);
        if (!phrases.isEmpty() && !containsPhrase(haystack, normalizeEnglish(phrases.get(0)).trim())) return 0;
        List<String> terms = englishTerms(relevanceSource);
        if (terms.isEmpty()) return 0;
        long matched = terms.stream().filter(term -> containsPhrase(haystack, term)).count();
        int required = terms.size() == 1 ? 1 : Math.max(2, (int) Math.ceil(terms.size() * 0.45));
        return matched < required ? 0 : Math.min(1.0, (double) matched / terms.size());
    }

    private double chineseScore(ReferenceSearchQuery query, String candidateText) {
        String haystack = normalizeChinese(candidateText);
        if (haystack.isBlank()) return 0;
        List<String> concepts = chineseConcepts(query);
        if (!concepts.isEmpty()) {
            if (!haystack.contains(concepts.get(0))) return 0;
            long secondaryMatches = concepts.stream().skip(1).filter(haystack::contains).count();
            if (concepts.size() > 1 && secondaryMatches == 0) return 0;
            long matched = 1 + secondaryMatches;
            return Math.min(1.0, 0.55 + ((double) matched / concepts.size()) * 0.45);
        }

        String topic = normalizeChinese(cleanChineseTopic(query.title()));
        if (topic.length() < 2) return 0;
        if (haystack.contains(topic)) return 1.0;
        Set<String> grams = bigrams(topic);
        if (grams.isEmpty()) return 0;
        long matched = grams.stream().filter(haystack::contains).count();
        double coverage = (double) matched / grams.size();
        return matched >= Math.min(2, grams.size()) && coverage >= 0.45 ? coverage : 0;
    }

    private double threshold(ReferenceSearchQuery query, String language) {
        return hasReliablePlan(query, language) ? 0.45 : 1.01;
    }

    private List<ConceptGroup> englishGroups(ReferenceSearchQuery query) {
        String source = value(query.title()) + " " + String.join(" ", safeList(query.keywords()));
        String normalizedEnglishSource = normalizeEnglish(source);
        List<IndexedConcept> allMatched = new ArrayList<>();
        for (Concept concept : CONCEPTS) {
            int index = conceptMatchIndex(source, normalizedEnglishSource, concept);
            if (index >= 0) allMatched.add(new IndexedConcept(index, concept));
        }
        List<IndexedConcept> matched = allMatched.stream()
                .filter(item -> allMatched.stream().noneMatch(other -> other != item
                        && other.concept().chinese().length() > item.concept().chinese().length()
                        && other.concept().chinese().contains(item.concept().chinese())))
                .collect(java.util.stream.Collectors.toCollection(ArrayList::new));
        matched.sort((left, right) -> {
            int byPosition = Integer.compare(left.index(), right.index());
            if (byPosition != 0) return byPosition;
            return Integer.compare(right.concept().chinese().length(), left.concept().chinese().length());
        });
        List<ConceptGroup> result = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        for (IndexedConcept item : matched) {
            String key = item.concept().english().get(0).toLowerCase(Locale.ROOT);
            if (seen.add(key)) result.add(new ConceptGroup(item.concept().chinese(), item.concept().english()));
        }
        return result;
    }

    private int conceptMatchIndex(String source, String normalizedEnglishSource, Concept concept) {
        int first = source.indexOf(concept.chinese());
        for (String alias : concept.english()) {
            String normalizedAlias = normalizeEnglish(alias).trim();
            if (normalizedAlias.isBlank()) continue;
            int aliasIndex = normalizedEnglishSource.indexOf(" " + normalizedAlias + " ");
            if (aliasIndex >= 0 && (first < 0 || aliasIndex < first)) first = aliasIndex;
        }
        return first;
    }

    private List<String> chineseConcepts(ReferenceSearchQuery query) {
        return englishGroups(query).stream().map(ConceptGroup::chinese).toList();
    }

    private List<ConceptGroup> providerGroups(List<ConceptGroup> groups) {
        if (groups.size() <= 1) return groups;
        ConceptGroup secondary = groups.stream().skip(1)
                .max((left, right) -> Integer.compare(discrimination(left), discrimination(right)))
                .orElse(groups.get(1));
        return List.of(groups.get(0), secondary);
    }

    private int discrimination(ConceptGroup group) {
        String primaryAlias = group.aliases().isEmpty() ? "" : group.aliases().get(0);
        return group.chinese().codePointCount(0, group.chinese().length()) * 100 + primaryAlias.length();
    }

    private boolean matchesEnglishGroup(String haystack, ConceptGroup group) {
        return group.aliases().stream().map(LiteratureTopicMatcher::normalizeEnglish)
                .anyMatch(alias -> !alias.isBlank() && containsPhrase(haystack, alias));
    }

    private static String requestedLanguage(ReferenceSearchQuery query) {
        if (query.englishTarget() > 0 && query.chineseTarget() == 0) return "EN";
        if (query.englishTarget() > 0 && query.chineseTarget() > 0) return "MIXED";
        return "ZH";
    }

    private static String cleanChineseTopic(String input) {
        String result = value(input);
        for (String noise : CHINESE_NOISE) result = result.replace(noise, " ");
        return result.replaceAll("[^\\p{IsHan}A-Za-z0-9]+", " ").replaceAll("\\s+", " ").trim();
    }

    private static String englishWords(String input) {
        return String.join(" ", englishTerms(input));
    }

    private static String englishRelevanceSource(ReferenceSearchQuery query) {
        if (query.hasProviderKeywordsOverride()) return query.providerKeywords();
        return value(query.title()) + " " + String.join(" ", safeList(query.keywords()));
    }

    private static List<String> quotedPhrases(String input) {
        List<String> phrases = new ArrayList<>();
        Matcher matcher = QUOTED_PHRASE.matcher(value(input));
        while (matcher.find()) {
            String phrase = matcher.group(1).trim();
            if (!phrase.isBlank()) phrases.add(phrase);
        }
        return phrases;
    }

    private static List<String> englishTerms(String input) {
        LinkedHashSet<String> terms = new LinkedHashSet<>();
        Matcher matcher = LATIN_WORD.matcher(value(input).toLowerCase(Locale.ROOT));
        while (matcher.find()) {
            String word = matcher.group();
            if (!ENGLISH_STOP_WORDS.contains(word)) terms.add(word);
        }
        return new ArrayList<>(terms);
    }

    private static String quoteIfPhrase(String value) {
        String normalized = value(value).trim();
        return normalized.contains(" ") ? "\"" + normalized + "\"" : normalized;
    }

    private static String normalizeEnglish(String value) {
        return " " + value(value).toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", " ").trim() + " ";
    }

    private static boolean containsPhrase(String haystack, String phrase) {
        String needle = phrase.trim();
        return !needle.isBlank() && haystack.contains(" " + needle + " ");
    }

    private static String normalizeChinese(String value) {
        return value(value).replaceAll("[^\\p{IsHan}]", "");
    }

    private static Set<String> bigrams(String value) {
        LinkedHashSet<String> result = new LinkedHashSet<>();
        int[] codePoints = value.codePoints().toArray();
        for (int index = 0; index + 1 < codePoints.length; index++) {
            result.add(new String(codePoints, index, 2));
        }
        return result;
    }

    private static List<String> safeList(List<String> values) {
        return values == null ? List.of() : values.stream().filter(item -> item != null && !item.isBlank()).toList();
    }

    private static String value(String value) {
        return value == null ? "" : value;
    }

    private static Concept concept(String chinese, String... english) {
        return new Concept(chinese, List.of(english));
    }

    private record Concept(String chinese, List<String> english) {
    }

    private record IndexedConcept(int index, Concept concept) {
    }

    private record ConceptGroup(String chinese, List<String> aliases) {
    }
}
