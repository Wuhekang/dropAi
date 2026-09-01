package com.dropai.rewrite.service.writing;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LiteratureTopicMatcherTest {
    private final LiteratureTopicMatcher matcher = new LiteratureTopicMatcher();

    @Test
    void createsRealEnglishTopicInsteadOfGenericAcademicWords() {
        ReferenceSearchQuery query = query("数字经济", "EN");

        assertEquals("\"digital economy\"", matcher.providerQuery(query));
        assertFalse(matcher.providerQuery(query).toLowerCase().contains("english papers"));
        assertTrue(matcher.hasReliablePlan(query, "EN"));
    }

    @Test
    void leavesGeneralJoinedKeywordsUntouchedUnlessStandaloneExplicitlyOverridesProviderKeywords() {
        ReferenceSearchQuery projectQuery = query("数字经济", "EN");
        String generalKeywords = projectQuery.joinedKeywords();

        assertTrue(generalKeywords.contains("数字经济"));
        assertFalse(generalKeywords.contains("digital economy"));
        assertEquals(generalKeywords, projectQuery.providerKeywords());

        ReferenceSearchQuery standaloneQuery = projectQuery.withProviderKeywords(matcher.providerQuery(projectQuery));
        assertEquals(generalKeywords, standaloneQuery.joinedKeywords());
        assertEquals("\"digital economy\"", standaloneQuery.providerKeywords());
    }

    @Test
    void rejectsTheThreeUnrelatedScreenshotResults() {
        ReferenceSearchQuery query = query("数字经济", "EN");

        assertFalse(matcher.isRelevant(query, candidate(
                "The study of the offerings in Hong Kong: Joss paper ingots, Joss candles and Joss sticks", "EN"), "EN"));
        assertFalse(matcher.isRelevant(query, candidate(
                "Cultural elements in Chinese animation characters: A systematic review", "EN"), "EN"));
        assertFalse(matcher.isRelevant(query, candidate(
                "Do or Die: HPV E5, E6 and E7 in Cell Death Evasion", "EN"), "EN"));
    }

    @Test
    void acceptsTopicMatchesInBothLanguages() {
        ReferenceSearchQuery english = query("数字经济", "EN");
        ReferenceSearchQuery chinese = query("数字经济", "ZH");

        assertTrue(matcher.isRelevant(english,
                candidate("Digital economy and enterprise innovation: new evidence", "EN"), "EN"));
        assertTrue(matcher.isRelevant(chinese,
                candidate("数字经济对中小企业创新能力的影响", "ZH"), "ZH"));
        assertFalse(matcher.isRelevant(chinese,
                candidate("短视频平台用户持续使用意愿研究", "ZH"), "ZH"));
    }

    @Test
    void failsClosedWhenChineseTopicHasNoReliableEnglishConcept() {
        ReferenceSearchQuery query = query("甲骨文字形流变考", "EN");

        assertEquals("", matcher.providerQuery(query));
        assertFalse(matcher.hasReliablePlan(query, "EN"));
        assertFalse(matcher.isRelevant(query, candidate("Oracle bone script evolution", "EN"), "EN"));
    }

    @Test
    void acceptsValidatedPlannerKeywordsForDictionaryUnknownTopic() {
        ReferenceSearchQuery query = query("甲骨文字形流变考", "EN")
                .withProviderKeywords("\"oracle bone script\" \"script forms\"");

        assertTrue(matcher.hasReliablePlan(query, "EN"));
        assertTrue(matcher.isRelevant(query,
                candidate("Evolution of oracle bone script forms", "EN"), "EN"));
        assertFalse(matcher.isRelevant(query,
                candidate("Digital preservation practices in museums", "EN"), "EN"));
    }

    @Test
    void requiresMainConceptAndAtLeastOneDistinctSecondaryConcept() {
        ReferenceSearchQuery english = query("数字经济对中小企业创新能力的影响研究", "EN");
        String providerQuery = matcher.providerQuery(english);

        assertTrue(providerQuery.contains("\"digital economy\""));
        assertTrue(providerQuery.contains("\"small and medium-sized enterprises\""));
        assertFalse(matcher.isRelevant(english,
                candidate("Innovation capability of small and medium-sized enterprises", "EN"), "EN"));
        assertFalse(matcher.isRelevant(english,
                candidate("Digital economy development: a systematic review", "EN"), "EN"));
        assertTrue(matcher.isRelevant(english,
                candidate("Digital economy and innovation capability of small and medium-sized enterprises", "EN"), "EN"));

        ReferenceSearchQuery chinese = query("数字经济对中小企业创新能力的影响研究", "ZH");
        assertFalse(matcher.isRelevant(chinese, candidate("中小企业创新能力提升研究", "ZH"), "ZH"));
        assertFalse(matcher.isRelevant(chinese, candidate("数字经济发展水平测度研究", "ZH"), "ZH"));
        assertTrue(matcher.isRelevant(chinese, candidate("数字经济促进中小企业创新能力提升研究", "ZH"), "ZH"));
    }

    @Test
    void recognizesEnglishAliasesAndAcronymsWithWordBoundaries() {
        ReferenceSearchQuery acronyms = query("AI and IoT adoption in SMEs", "EN");
        String providerQuery = matcher.providerQuery(acronyms);

        assertTrue(providerQuery.contains("\"artificial intelligence\""));
        assertTrue(providerQuery.contains("\"small and medium-sized enterprises\""));
        assertTrue(matcher.isRelevant(acronyms,
                candidate("Artificial intelligence adoption by small and medium enterprises", "EN"), "EN"));

        assertEquals("\"internet of things\"", matcher.providerQuery(query("IoT adoption", "EN")));
        assertEquals("\"small and medium-sized enterprises\"",
                matcher.providerQuery(query("Financing practices of SMEs", "EN")));

        ReferenceSearchQuery training = query("Employee training practices in SMEs", "EN");
        assertFalse(matcher.providerQuery(training).contains("artificial intelligence"));
    }

    @Test
    void detectsChineseTopicPartsThatDeterministicConceptsDoNotCover() {
        assertFalse(matcher.needsEnglishPlanning(query("数字经济", "EN")));
        assertFalse(matcher.needsEnglishPlanning(query("生成式人工智能应用研究", "EN")));
        assertFalse(matcher.needsEnglishPlanning(
                query("数字经济背景下中小企业创新能力提升路径研究", "EN")));
        assertTrue(matcher.needsEnglishPlanning(query("AIGC赋能甲骨文字形识别", "EN")));
        assertTrue(matcher.needsEnglishPlanning(query("大模型对供应链韧性的影响", "EN")));
        assertTrue(matcher.needsEnglishPlanning(query("中医与人工智能融合研究", "EN")));

        ReferenceSearchQuery planned = query("大模型对供应链韧性的影响", "EN")
                .withProviderKeywords("\"large language models\" \"supply chain resilience\"");
        assertFalse(matcher.isRelevant(planned,
                candidate("A review of supply chain resilience", "EN"), "EN"));
        assertTrue(matcher.isRelevant(planned,
                candidate("Large language models for supply chain resilience", "EN"), "EN"));
    }

    private ReferenceSearchQuery query(String title, String language) {
        return new ReferenceSearchQuery("test", title, "", List.of(title), List.of(), 2020, 2026, 20,
                "ZH".equals(language) ? 5 : 0, "EN".equals(language) ? 5 : 0);
    }

    private ReferenceCandidate candidate(String title, String language) {
        return new ReferenceCandidate(title, List.of("Author"), 2025, "Journal", "", "", "",
                "", "https://example.test/work", "TEST", "", "", LocalDateTime.now(), List.of(),
                1.0, "VERIFIED", "JOURNAL", language.toLowerCase(), "OTHER_PUBLIC", title, "");
    }
}
