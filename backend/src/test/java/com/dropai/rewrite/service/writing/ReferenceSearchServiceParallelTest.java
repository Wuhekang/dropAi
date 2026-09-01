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
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;

@ExtendWith(MockitoExtension.class)
class ReferenceSearchServiceParallelTest {
    @Mock
    private JdbcTemplate jdbcTemplate;

    @Mock
    private ChineseReferenceSearchPlanService searchPlanService;

    @Mock
    private GbT7714Formatter formatter;

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
    void returnsPartialWithWarningWhenChineseFailsAndEnglishSucceeds() {
        WritingGenerationProperties properties = properties("doubao_web,openalex,crossref", true);
        Function<ReferenceSearchQuery, List<ReferenceCandidate>> behavior = query -> {
            if ("ZH".equals(language(query))) throw new IllegalStateException("ZH source unavailable");
            return List.of(candidate("EN", "Resilient English Result", "fake"));
        };
        ReferenceSearchService service = service(properties, List.of(
                fake("doubao_web", behavior),
                fake("openalex", behavior),
                fake("crossref", behavior)));

        Map<String, Object> result = service.standaloneSearch("数字经济", 2, 1);

        assertEquals("PARTIAL", result.get("status"));
        assertEquals(true, result.get("partial"));
        assertEquals(1, result.get("actualCount"));
        assertEquals(0, result.get("actualChineseCount"));
        assertEquals(1, result.get("actualEnglishCount"));
        assertTrue(strings(result, "warnings").stream().anyMatch(warning -> warning.contains("中文来源")));
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
        assertTrue(items.stream().anyMatch(item -> "Independent English Result".equals(item.get("title"))));
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

        assertEquals("暂未检索到符合条件的公开文献，请尝试补充或调整题目关键词", exception.getMessage());
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

        assertEquals("公开学术来源响应超时，请稍后重试或缩小检索范围", exception.getMessage());
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
                jdbcTemplate, properties, providers, new ObjectMapper(), searchPlanService, formatter);
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
        String visibleTitle = "ZH".equals(language) && title.codePoints().noneMatch(code ->
                Character.UnicodeScript.of(code) == Character.UnicodeScript.HAN)
                ? "中文" + title
                : title;
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
