package com.dropai.rewrite.service.writing;

import com.dropai.rewrite.config.WritingGenerationProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReferenceSearchServiceParallelTest {
    @Mock
    private JdbcTemplate jdbcTemplate;

    @Mock
    private ChineseReferenceSearchPlanService searchPlanService;

    @Mock
    private GbT7714Formatter formatter;

    @Mock
    private LiteratureEnglishQueryPlanner englishQueryPlanner;

    private final List<ReferenceSearchService> services = new ArrayList<>();

    @AfterEach
    void shutdownExecutors() {
        services.forEach(ReferenceSearchService::shutdownSearchExecutor);
    }

    @Test
    void searchesChineseAndEnglishAcrossMultipleProvidersConcurrently() {
        WritingGenerationProperties properties = properties("doubao_web,openalex,crossref", true);
        properties.getReferenceSearch().setParallelism(8);
        CountDownLatch allTasksStarted = new CountDownLatch(6);
        AtomicInteger inFlight = new AtomicInteger();
        AtomicInteger maxInFlight = new AtomicInteger();
        Set<String> calls = ConcurrentHashMap.newKeySet();
        long providerDelayMillis = 200L;

        ReferenceSearchService service = service(properties, List.of(
                delayedFake("doubao_web", allTasksStarted, inFlight, maxInFlight, calls, providerDelayMillis),
                delayedFake("openalex", allTasksStarted, inFlight, maxInFlight, calls, providerDelayMillis),
                delayedFake("crossref", allTasksStarted, inFlight, maxInFlight, calls, providerDelayMillis)));

        long started = System.nanoTime();
        Map<String, Object> result = service.standaloneSearch("数字经济", 1, 1);
        long elapsedMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started);

        assertEquals(2, result.get("actualCount"));
        assertEquals(1, result.get("actualChineseCount"));
        assertEquals(1, result.get("actualEnglishCount"));
        assertEquals(3, result.get("providersTried"));
        assertEquals(0L, allTasksStarted.getCount(), "all six language/provider branches should have started");
        assertTrue(maxInFlight.get() >= 6, "expected six overlapping provider calls, max=" + maxInFlight.get());
        assertEquals(Set.of(
                "doubao_web:ZH", "doubao_web:EN",
                "openalex:ZH", "openalex:EN",
                "crossref:ZH", "crossref:EN"), calls);
        long serialMillis = 6 * providerDelayMillis;
        assertTrue(elapsedMillis < serialMillis,
                "parallel search took " + elapsedMillis + "ms, serial lower bound was " + serialMillis + "ms");
    }

    @Test
    void failsWholeSearchWhenAnyLanguageBranchCannotMeetItsQuota() {
        WritingGenerationProperties properties = properties("doubao_web,openalex,crossref", true);
        Function<ReferenceSearchQuery, List<ReferenceCandidate>> behavior = query -> {
            if ("ZH".equals(language(query))) throw new IllegalStateException("ZH source unavailable");
            return List.of(candidate("EN", "Resilient English Result", "fake"));
        };
        ReferenceSearchService service = service(properties, List.of(
                fake("doubao_web", behavior),
                fake("openalex", behavior),
                fake("crossref", behavior)));

        IllegalStateException exception = assertThrows(IllegalStateException.class,
                () -> service.standaloneSearch("数字经济", 2, 1));

        assertTrue(exception.getMessage().contains("中文 0/2"));
        assertTrue(exception.getMessage().contains("英文 1/1"));
        assertTrue(exception.getMessage().contains("不返回结果且不扣费"));
    }

    @Test
    void enforcesQuotaUsingCandidateLanguageRatherThanProviderBranch() {
        WritingGenerationProperties properties = properties("doubao_web", false);
        FakeProvider provider = fake("doubao_web", query -> List.of(
                candidate("ZH", "中文候选一", "doubao_web"),
                candidate("ZH", "中文候选二", "doubao_web"),
                candidate("ZH", "中文候选三", "doubao_web"),
                candidate("EN", "English Candidate One", "doubao_web"),
                candidate("EN", "English Candidate Two", "doubao_web"),
                candidate("EN", "English Candidate Three", "doubao_web")));
        ReferenceSearchService service = service(properties, List.of(provider));

        Map<String, Object> result = service.standaloneSearch("数字经济", 2, 1);
        List<Map<String, Object>> items = items(result);

        assertEquals(3, result.get("actualCount"));
        assertEquals(2, result.get("actualChineseCount"));
        assertEquals(1, result.get("actualEnglishCount"));
        assertEquals(2L, items.stream().filter(item -> "ZH".equals(item.get("language"))).count());
        assertEquals(1L, items.stream().filter(item -> "EN".equals(item.get("language"))).count());
    }

    @Test
    void appliesRefinedProviderKeywordsOnlyToStandaloneBranches() {
        WritingGenerationProperties properties = properties("public", false);
        AtomicReference<ReferenceSearchQuery> chineseQuery = new AtomicReference<>();
        AtomicReference<ReferenceSearchQuery> englishQuery = new AtomicReference<>();
        ReferenceSearchService service = service(properties, List.of(fake("public", query -> {
            if (query.chineseTarget() > 0) chineseQuery.set(query);
            else englishQuery.set(query);
            return List.of(candidate(language(query), "Relevant Result", "public"));
        })));

        service.standaloneSearch("数字经济", 1, 1);

        assertEquals("数字经济", chineseQuery.get().providerKeywords());
        assertEquals("\"digital economy\"", englishQuery.get().providerKeywords());
        assertTrue(chineseQuery.get().joinedKeywords().contains("数字经济"));
        assertTrue(englishQuery.get().joinedKeywords().contains("数字经济"));
        assertTrue(chineseQuery.get().hasProviderKeywordsOverride());
        assertTrue(englishQuery.get().hasProviderKeywordsOverride());
        verify(englishQueryPlanner, never()).plan(anyString());
    }

    @Test
    void fullyMappedNestedConceptDoesNotRequirePlanner() {
        WritingGenerationProperties properties = properties("public", false);
        AtomicReference<ReferenceSearchQuery> providerQuery = new AtomicReference<>();
        ReferenceSearchService service = service(properties, List.of(fake("public", query -> {
            providerQuery.set(query);
            return List.of(rawCandidate(
                    "Generative artificial intelligence applications in education", "EN", "public"));
        })));

        Map<String, Object> result = service.standaloneSearch("生成式人工智能应用研究", 0, 1);

        assertEquals(1, result.get("actualEnglishCount"));
        assertEquals("\"generative artificial intelligence\"", providerQuery.get().providerKeywords());
        verify(englishQueryPlanner, never()).plan(anyString());
    }

    @Test
    void usesBoundedPlannerForDictionaryUnknownEnglishBranch() {
        WritingGenerationProperties properties = properties("public", false);
        AtomicReference<ReferenceSearchQuery> providerQuery = new AtomicReference<>();
        when(englishQueryPlanner.plan("甲骨文字形流变考")).thenReturn(Optional.of(
                new LiteratureEnglishQueryPlanner.Plan(
                        "Evolution of oracle bone script forms",
                        List.of("oracle bone script", "script forms"))));
        ReferenceSearchService service = service(properties, List.of(fake("public", query -> {
            providerQuery.set(query);
            return List.of(rawCandidate("Evolution of oracle bone script forms", "EN", "public"));
        })));

        Map<String, Object> result = service.standaloneSearch("甲骨文字形流变考", 0, 1);

        assertEquals(1, result.get("actualEnglishCount"));
        assertEquals("\"oracle bone script\" \"script forms\"", providerQuery.get().providerKeywords());
        verify(englishQueryPlanner).plan("甲骨文字形流变考");
    }

    @Test
    void asciiFragmentInsideUnknownChineseTopicStillUsesPlanner() {
        WritingGenerationProperties properties = properties("public", false);
        AtomicReference<ReferenceSearchQuery> providerQuery = new AtomicReference<>();
        when(englishQueryPlanner.plan("AIGC赋能甲骨文字形识别")).thenReturn(Optional.of(
                new LiteratureEnglishQueryPlanner.Plan(
                        "AIGC-enabled oracle bone script recognition",
                        List.of("oracle bone script recognition", "AIGC"))));
        ReferenceSearchService service = service(properties, List.of(fake("public", query -> {
            providerQuery.set(query);
            return List.of(rawCandidate("AIGC-enabled oracle bone script recognition", "EN", "public"));
        })));

        Map<String, Object> result = service.standaloneSearch("AIGC赋能甲骨文字形识别", 0, 1);

        assertEquals(1, result.get("actualEnglishCount"));
        assertEquals("\"oracle bone script recognition\" \"AIGC\"", providerQuery.get().providerKeywords());
        verify(englishQueryPlanner).plan("AIGC赋能甲骨文字形识别");
    }

    @Test
    void plannerFailureKeepsUnknownEnglishBranchFailClosedWithoutProviderCall() {
        WritingGenerationProperties properties = properties("public", false);
        AtomicInteger providerCalls = new AtomicInteger();
        when(englishQueryPlanner.plan("甲骨文字形流变考")).thenReturn(Optional.empty());
        ReferenceSearchService service = service(properties, List.of(fake("public", query -> {
            providerCalls.incrementAndGet();
            return List.of(rawCandidate("Unrelated result", "EN", "public"));
        })));

        assertThrows(IllegalStateException.class,
                () -> service.standaloneSearch("甲骨文字形流变考", 0, 1));
        assertEquals(0, providerCalls.get());
    }

    @Test
    void unknownCoreWithKnownSecondaryCannotSearchOrPassOnSecondaryAlone() {
        WritingGenerationProperties properties = properties("public", false);
        AtomicInteger providerCalls = new AtomicInteger();
        when(englishQueryPlanner.plan("大模型对供应链韧性的影响")).thenReturn(Optional.empty());
        ReferenceSearchService service = service(properties, List.of(fake("public", query -> {
            providerCalls.incrementAndGet();
            return List.of(rawCandidate("A review of supply chain resilience", "EN", "public"));
        })));

        assertThrows(IllegalStateException.class,
                () -> service.standaloneSearch("大模型对供应链韧性的影响", 0, 1));
        assertEquals(0, providerCalls.get());
        verify(englishQueryPlanner).plan("大模型对供应链韧性的影响");
    }

    @Test
    void plannerSharesTheStandaloneGlobalDeadline() throws Exception {
        WritingGenerationProperties properties = properties("public", false);
        properties.getReferenceSearch().setTimeoutSeconds(1);
        AtomicInteger providerCalls = new AtomicInteger();
        when(englishQueryPlanner.plan("甲骨文字形流变考")).thenAnswer(invocation -> {
            try {
                Thread.sleep(5_000L);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
            }
            return Optional.empty();
        });
        ReferenceSearchService service = service(properties, List.of(fake("public", query -> {
            providerCalls.incrementAndGet();
            return List.of();
        })));

        long started = System.nanoTime();
        assertThrows(IllegalStateException.class,
                () -> service.standaloneSearch("甲骨文字形流变考", 0, 1));
        long elapsedMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started);

        assertTrue(elapsedMillis < 2_500L, "planner exceeded shared deadline: " + elapsedMillis + "ms");
        assertEquals(0, providerCalls.get());
    }

    @Test
    void honorsProviderLanguageMetadataBeforeTitleScript() {
        WritingGenerationProperties properties = properties("openalex", false);
        ReferenceCandidate chineseMetadataWithLatinTitle = new ReferenceCandidate(
                "Shuzi Jingji and Regional Development", List.of("Test Author"), 2025, "Chinese Journal",
                "1", "1", "1-10", "", "https://example.test/openalex/zh", "openalex",
                "本文研究数字经济与区域发展。", "数字经济", LocalDateTime.now(), List.of(), 1.0, "VERIFIED",
                "JOURNAL", "zh", "OTHER_PUBLIC", "Chinese Journal", "数字经济与区域发展");
        ReferenceSearchService service = service(properties,
                List.of(fake("openalex", query -> List.of(chineseMetadataWithLatinTitle))));

        Map<String, Object> result = service.standaloneSearch("数字经济", 1, 0);

        assertEquals(1, result.get("actualChineseCount"));
        assertEquals("ZH", items(result).get(0).get("language"));
    }

    @Test
    void fillsBothLanguageQuotasAfterGlobalDoiDeduplication() {
        WritingGenerationProperties properties = properties("doubao_web", false);
        FakeProvider provider = fake("doubao_web", query -> "ZH".equals(language(query))
                ? List.of(candidateWithDoi("ZH", "中文共同文献", "doubao_web", "https://doi.org/10.1000/shared"))
                : List.of(
                        candidateWithDoi("EN", "Shared English Translation", "doubao_web", "doi:10.1000/shared"),
                        candidateWithDoi("EN", "Independent English Result", "doubao_web", "10.1000/unique")));
        ReferenceSearchService service = service(properties, List.of(provider));

        Map<String, Object> result = service.standaloneSearch("数字经济", 1, 1);
        List<Map<String, Object>> items = items(result);

        assertEquals(2, result.get("actualCount"));
        assertEquals(1, result.get("actualChineseCount"));
        assertEquals(1, result.get("actualEnglishCount"));
        assertTrue(items.stream().anyMatch(item -> String.valueOf(item.get("title")).contains("Independent English Result")));
    }

    @Test
    void doiAndTitleYearIdentityRulesWaitForARealReplacementBeforeCompletingQuota() {
        WritingGenerationProperties properties = properties("fast,slow", false);
        FakeProvider fast = fake("fast", query -> List.of(
                candidate("ZH", "数字经济共同文献", "fast"),
                candidateWithDoi("ZH", "数字经济共同文献", "fast", "10.1000/shared"),
                candidateWithDoi("ZH", "数字经济A题名变体", "fast", "10.1000/shared")));
        FakeProvider slow = fake("slow", query -> {
            try {
                Thread.sleep(150L);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
            }
            return List.of(candidate("ZH", "数字经济替补文献", "slow"));
        });
        ReferenceSearchService service = service(properties, List.of(fast, slow));

        Map<String, Object> result = service.standaloneSearch("数字经济", 2, 0);

        assertEquals(2, result.get("actualChineseCount"));
        assertTrue(items(result).stream()
                .anyMatch(item -> String.valueOf(item.get("title")).contains("替补文献")));
    }

    @Test
    void reportsFriendlyErrorWhenEveryProviderReturnsEmpty() {
        WritingGenerationProperties properties = properties("doubao_web,openalex,crossref", true);
        Function<ReferenceSearchQuery, List<ReferenceCandidate>> empty = query -> List.of();
        ReferenceSearchService service = service(properties, List.of(
                fake("doubao_web", empty),
                fake("openalex", empty),
                fake("crossref", empty)));

        IllegalStateException exception = assertThrows(IllegalStateException.class,
                () -> service.standaloneSearch("数字经济", 2, 1));

        assertTrue(exception.getMessage().contains("中文 0/2"));
        assertTrue(exception.getMessage().contains("英文 0/1"));
        assertTrue(exception.getMessage().contains("不扣费"));
    }

    @Test
    void fillsQuotaWithSafeLowRelevanceCandidatesButKeepsRelevantResultsFirst() {
        WritingGenerationProperties properties = properties("public", false);
        FakeProvider provider = fake("public", query -> "EN".equals(language(query))
                ? List.of(
                rawCandidate("The study of Joss paper offerings", "EN", "public"),
                candidate("EN", "Relevant English Result", "public"))
                : List.of(
                rawCandidate("短视频用户行为研究", "ZH", "public"),
                candidate("ZH", "相关中文结果", "public")));
        ReferenceSearchService service = service(properties, List.of(provider));

        Map<String, Object> result = service.standaloneSearch("数字经济", 2, 2);
        List<Map<String, Object>> items = items(result);

        assertEquals(4, result.get("actualCount"));
        assertEquals(2, result.get("actualChineseCount"));
        assertEquals(2, result.get("actualEnglishCount"));
        assertTrue(String.valueOf(items.get(0).get("title")).contains("数字经济"));
        assertTrue(String.valueOf(items.get(2).get("title")).toLowerCase().contains("digital economy"));
        assertEquals(false, result.get("partial"));
        assertEquals("SUCCESS", result.get("status"));
    }

    @Test
    void returnsImmediatelyWhenFastVerifiedLowRelevanceCandidatesFillBothQuotas() {
        WritingGenerationProperties properties = properties("fast,slow", false);
        properties.getReferenceSearch().setTimeoutSeconds(2);
        FakeProvider fast = fake("fast", query -> List.of(rawCandidate(
                "ZH".equals(language(query)) ? "短视频用户行为研究" : "The study of Joss paper offerings",
                language(query), "fast")));
        FakeProvider slow = fake("slow", query -> {
            try {
                Thread.sleep(5_000L);
                return List.of();
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("slow provider interrupted", exception);
            }
        });
        ReferenceSearchService service = service(properties, List.of(fast, slow));

        long started = System.nanoTime();
        Map<String, Object> result = service.standaloneSearch("数字经济", 1, 1);
        long elapsedMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started);

        assertEquals(2, result.get("actualCount"));
        assertTrue(elapsedMillis < 1_500L,
                "complete low-relevance quota unnecessarily waited for slow provider: " + elapsedMillis + "ms");
    }

    @Test
    void failsWholeSearchWhenEnglishQueryCannotBeBuilt() {
        WritingGenerationProperties properties = properties("public", false);
        ReferenceSearchService service = service(properties, List.of(fake("public", query -> List.of(
                rawCandidate("甲骨文字形流变考述", "ZH", "public"),
                rawCandidate("Oracle bone script evolution", "EN", "public")))));

        IllegalStateException exception = assertThrows(IllegalStateException.class,
                () -> service.standaloneSearch("甲骨文字形流变考", 1, 1));

        assertTrue(exception.getMessage().contains("英文 0/1"));
        assertTrue(exception.getMessage().contains("不扣费"));
    }

    @Test
    void stopsAtTheGlobalDeadlineWhenEveryProviderIsSlow() {
        WritingGenerationProperties properties = properties("doubao_web", false);
        properties.getReferenceSearch().setTimeoutSeconds(1);
        ReferenceSearchService service = service(properties, List.of(fake("doubao_web", query -> {
            try {
                Thread.sleep(5_000L);
                return List.of();
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("slow fake interrupted", exception);
            }
        })));

        long started = System.nanoTime();
        IllegalStateException exception = assertThrows(IllegalStateException.class,
                () -> service.standaloneSearch("数字经济", 1, 1));
        long elapsedMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started);

        assertTrue(exception.getMessage().contains("未凑齐目标文献"));
        assertTrue(exception.getMessage().contains("不扣费"));
        assertTrue(elapsedMillis < 2_500L, "global deadline took " + elapsedMillis + "ms");
    }

    @Test
    void appendsOpenAlexAndCrossrefWhenOnlyDoubaoIsConfigured() {
        WritingGenerationProperties.ReferenceSearch search = new WritingGenerationProperties.ReferenceSearch();
        search.setProvider("doubao_web");
        search.setPublicFallbackEnabled(true);

        assertEquals(List.of("doubao_web", "openalex", "crossref"), search.providerOrder());
    }

    private ReferenceSearchService service(WritingGenerationProperties properties,
                                           List<ReferenceSearchProvider> providers) {
        lenient().when(formatter.format(anyInt(), any(ReferenceCandidate.class), anyString()))
                .thenAnswer(invocation -> {
                    int number = invocation.getArgument(0);
                    ReferenceCandidate candidate = invocation.getArgument(1);
                    return "[" + number + "] " + candidate.title();
                });
        ReferenceSearchService service = new ReferenceSearchService(
                jdbcTemplate, properties, providers, new ObjectMapper(), searchPlanService, formatter,
                englishQueryPlanner);
        services.add(service);
        return service;
    }

    private WritingGenerationProperties properties(String providers, boolean publicFallbackEnabled) {
        WritingGenerationProperties properties = new WritingGenerationProperties();
        properties.getReferenceSearch().setProvider(providers);
        properties.getReferenceSearch().setPublicFallbackEnabled(publicFallbackEnabled);
        properties.getReferenceSearch().setRetryCount(1);
        properties.getReferenceSearch().setTimeoutSeconds(2);
        properties.getReferenceSearch().setParallelism(8);
        return properties;
    }

    private FakeProvider fake(String code, Function<ReferenceSearchQuery, List<ReferenceCandidate>> search) {
        return new FakeProvider(code, search);
    }

    private FakeProvider delayedFake(String code, CountDownLatch allTasksStarted,
                                     AtomicInteger inFlight, AtomicInteger maxInFlight,
                                     Set<String> calls, long delayMillis) {
        return fake(code, query -> {
            String language = language(query);
            calls.add(code + ":" + language);
            int running = inFlight.incrementAndGet();
            maxInFlight.accumulateAndGet(running, Math::max);
            allTasksStarted.countDown();
            try {
                if (!allTasksStarted.await(2, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("provider branches did not start concurrently");
                }
                Thread.sleep(delayMillis);
                return List.of(candidate(language, code + "-" + language, code));
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("fake provider interrupted", exception);
            } finally {
                inFlight.decrementAndGet();
            }
        });
    }

    private static String language(ReferenceSearchQuery query) {
        return query.chineseTarget() > 0 ? "ZH" : "EN";
    }

    private static ReferenceCandidate candidate(String language, String title, String provider) {
        return candidateWithDoi(language, title, provider, "");
    }

    private static ReferenceCandidate candidateWithDoi(String language, String title, String provider, String doi) {
        String visibleTitle = "ZH".equals(language)
                ? (title.contains("数字经济") ? title : "数字经济：" + title)
                : (title.toLowerCase().contains("digital economy") ? title : "Digital economy: " + title);
        return new ReferenceCandidate(
                visibleTitle,
                List.of("Test Author"),
                2025,
                "Test Journal",
                "1",
                "1",
                "1-10",
                doi,
                "https://example.test/" + provider + "/" + Math.abs(visibleTitle.hashCode()),
                provider,
                "Offline test abstract",
                "offline test",
                LocalDateTime.now(),
                List.of(),
                1.0,
                "VERIFIED");
    }

    private static ReferenceCandidate rawCandidate(String title, String language, String provider) {
        return new ReferenceCandidate(title, List.of("Test Author"), 2025, "Test Journal", "1", "1", "1-10",
                "", "https://example.test/" + provider + "/" + Math.abs(title.hashCode()), provider,
                "Unrelated abstract", "offline test", LocalDateTime.now(), List.of(), 100.0, "VERIFIED",
                "JOURNAL", language.toLowerCase(), "OTHER_PUBLIC", title, "Unrelated abstract");
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> items(Map<String, Object> result) {
        return (List<Map<String, Object>>) result.get("items");
    }

    @SuppressWarnings("unchecked")
    private static List<String> strings(Map<String, Object> result, String key) {
        return (List<String>) result.get(key);
    }

    private static final class FakeProvider implements ReferenceSearchProvider {
        private final String code;
        private final Function<ReferenceSearchQuery, List<ReferenceCandidate>> search;

        private FakeProvider(String code, Function<ReferenceSearchQuery, List<ReferenceCandidate>> search) {
            this.code = code;
            this.search = search;
        }

        @Override
        public String name() {
            return code;
        }

        @Override
        public String providerCode() {
            return code;
        }

        @Override
        public boolean available() {
            return true;
        }

        @Override
        public List<ReferenceCandidate> search(ReferenceSearchQuery query) {
            return search.apply(query);
        }
    }
}
