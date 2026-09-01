package com.dropai.rewrite.service.writing;

import com.dropai.rewrite.config.WritingGenerationProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PreDestroy;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.Year;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CompletionService;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

@Service
public class ReferenceSearchService {
    private static final long ENGLISH_PLANNER_BUDGET_NANOS = TimeUnit.SECONDS.toNanos(4);
    private final JdbcTemplate jdbcTemplate;
    private final WritingGenerationProperties properties;
    private final Map<String, ReferenceSearchProvider> providers = new HashMap<>();
    private final ObjectMapper objectMapper;
    private final ChineseReferenceSearchPlanService searchPlanService;
    private final GbT7714Formatter formatter;
    private final LiteratureEnglishQueryPlanner englishQueryPlanner;
    private final LiteratureTopicMatcher topicMatcher = new LiteratureTopicMatcher();
    private final ThreadPoolExecutor searchExecutor;

    public ReferenceSearchService(JdbcTemplate jdbcTemplate,
                                  WritingGenerationProperties properties,
                                  List<ReferenceSearchProvider> providers,
                                  ObjectMapper objectMapper,
                                  ChineseReferenceSearchPlanService searchPlanService,
                                  GbT7714Formatter formatter,
                                  LiteratureEnglishQueryPlanner englishQueryPlanner) {
        this.jdbcTemplate = jdbcTemplate;
        this.properties = properties;
        providers.forEach(provider -> {
            this.providers.put(provider.name().toLowerCase(Locale.ROOT), provider);
            this.providers.put(provider.providerCode().toLowerCase(Locale.ROOT), provider);
        });
        this.objectMapper = objectMapper;
        this.searchPlanService = searchPlanService;
        this.formatter = formatter;
        this.englishQueryPlanner = englishQueryPlanner;
        int parallelism = Math.max(4, Math.min(16, properties.getReferenceSearch().getParallelism()));
        AtomicInteger threadNumber = new AtomicInteger();
        ThreadFactory threadFactory = runnable -> {
            Thread thread = new Thread(runnable, "literature-search-" + threadNumber.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        };
        this.searchExecutor = new ThreadPoolExecutor(
                parallelism,
                parallelism,
                30L,
                TimeUnit.SECONDS,
                new ArrayBlockingQueue<>(Math.max(32, parallelism * 8)),
                threadFactory,
                new ThreadPoolExecutor.AbortPolicy());
        this.searchExecutor.allowCoreThreadTimeOut(true);
    }

    @PreDestroy
    void shutdownSearchExecutor() {
        searchExecutor.shutdownNow();
    }

    public Map<String, Object> status() {
        List<ProviderHealthStatus> providerStatus = providers.values().stream()
                .distinct()
                .sorted(Comparator.comparing(ReferenceSearchProvider::providerCode))
                .map(ReferenceSearchProvider::healthCheck)
                .toList();
        return Map.of(
                "enabled", properties.getReferenceSearch().isEnabled(),
                "providerOrder", properties.getReferenceSearch().providerOrder(),
                "providers", providerStatus
        );
    }

    public List<ProviderHealthStatus> providers() {
        return providers.values().stream()
                .distinct()
                .sorted(Comparator.comparing(ReferenceSearchProvider::providerCode))
                .map(ReferenceSearchProvider::healthCheck)
                .toList();
    }

    public Map<String, Object> searchPlan(Long userId, String projectId, Map<String, Object> request) {
        Map<String, Object> project = WritingJdbc.one(jdbcTemplate,
                "SELECT * FROM writing_project WHERE id=? AND user_id=?", projectId, userId);
        List<Map<String, Object>> chapters = WritingJdbc.list(jdbcTemplate,
                "SELECT * FROM writing_chapter WHERE project_id=? ORDER BY sort_order", projectId);
        int yearStart = WritingJdbc.integer(request.get("yearFrom"), WritingJdbc.integer(project.get("year_start"), 0));
        int yearEnd = WritingJdbc.integer(request.get("yearTo"), WritingJdbc.integer(project.get("year_end"), 0));
        int target = WritingJdbc.integer(request.get("targetCount"), WritingJdbc.integer(project.get("chinese_reference_count"), 14));
        ReferenceSearchQuery query = new ReferenceSearchQuery(projectId, WritingJdbc.text(project.get("title")),
                WritingJdbc.text(project.get("major")), readKeywords(project),
                chapters.stream().map(row -> WritingJdbc.text(row.get("title"))).toList(),
                yearStart, yearEnd, Math.max(1, target), target, 0);
        return searchPlanService.buildPlan(query);
    }

    @Transactional
    public List<Map<String, Object>> searchAndSave(Long userId, String projectId, Integer chapterNo) {
        Map<String, Object> project = WritingJdbc.one(jdbcTemplate,
                "SELECT * FROM writing_project WHERE id=? AND user_id=?", projectId, userId);
        List<Map<String, Object>> chapters = WritingJdbc.list(jdbcTemplate,
                chapterNo == null
                        ? "SELECT * FROM writing_chapter WHERE project_id=? ORDER BY sort_order"
                        : "SELECT * FROM writing_chapter WHERE project_id=? AND chapter_no=? ORDER BY sort_order",
                chapterNo == null ? new Object[]{projectId} : new Object[]{projectId, chapterNo});
        List<String> chapterTitles = chapters.stream().map(row -> WritingJdbc.text(row.get("title"))).toList();
        List<String> keywords = readKeywords(project);
        ReferenceSearchQuery query = new ReferenceSearchQuery(projectId, WritingJdbc.text(project.get("title")),
                WritingJdbc.text(project.get("major")), keywords, chapterTitles,
                WritingJdbc.integer(project.get("year_start"), 0), WritingJdbc.integer(project.get("year_end"), 0),
                Math.max(1, WritingJdbc.integer(project.get("chinese_reference_count"), 14) + WritingJdbc.integer(project.get("english_reference_count"), 6)),
                WritingJdbc.integer(project.get("chinese_reference_count"), 14),
                WritingJdbc.integer(project.get("english_reference_count"), 6));

        jdbcTemplate.update("UPDATE writing_project SET search_status=?, search_message=?, updated_at=? WHERE id=?",
                "RUNNING", "正在联网检索参考文献", LocalDateTime.now(), projectId);
        List<ReferenceCandidate> candidates = searchOnline(query);
        List<ReferenceCandidate> deduped = dedupe(candidates).stream()
                .filter(ReferenceCandidate::basicallyVerified)
                .sorted(Comparator.comparingDouble(ReferenceCandidate::relevanceScore).reversed())
                .toList();
        List<ReferenceCandidate> selected = new ArrayList<>();
        selected.addAll(deduped.stream().filter(candidate -> "ZH".equals(languageOf(candidate))).limit(query.chineseTarget()).toList());
        selected.addAll(deduped.stream().filter(candidate -> "EN".equals(languageOf(candidate))).limit(query.englishTarget()).toList());
        jdbcTemplate.update("DELETE FROM writing_reference WHERE project_id=?", projectId);
        int index = 1;
        for (ReferenceCandidate candidate : selected) {
            insertReference(projectId, candidate, index++, chapterNo);
        }
        jdbcTemplate.update("UPDATE writing_project SET search_provider=?, search_status=?, search_message=?, updated_at=? WHERE id=?",
                activeProviderNames(), "SUCCESS", "已检索并验证 " + deduped.size() + " 条参考文献", LocalDateTime.now(), projectId);
        return references(userId, projectId);
    }

    @Transactional
    public List<Map<String, Object>> searchAndSaveLanguage(Long userId, String projectId, String language, Map<String, Object> request) {
        Map<String, Object> project = WritingJdbc.one(jdbcTemplate,
                "SELECT * FROM writing_project WHERE id=? AND user_id=?", projectId, userId);
        List<Map<String, Object>> chapters = WritingJdbc.list(jdbcTemplate,
                "SELECT * FROM writing_chapter WHERE project_id=? ORDER BY sort_order", projectId);
        int target = WritingJdbc.integer(request.get("targetCount"),
                "ZH".equalsIgnoreCase(language) ? WritingJdbc.integer(project.get("chinese_reference_count"), 14) : WritingJdbc.integer(project.get("english_reference_count"), 6));
        int yearStart = WritingJdbc.integer(request.get("yearFrom"), WritingJdbc.integer(project.get("year_start"), 0));
        int yearEnd = WritingJdbc.integer(request.get("yearTo"), WritingJdbc.integer(project.get("year_end"), 0));
        ReferenceSearchQuery query = new ReferenceSearchQuery(projectId, WritingJdbc.text(project.get("title")),
                WritingJdbc.text(project.get("major")), readKeywords(project),
                chapters.stream().map(row -> WritingJdbc.text(row.get("title"))).toList(),
                yearStart, yearEnd, Math.max(1, target), "ZH".equalsIgnoreCase(language) ? target : 0,
                "EN".equalsIgnoreCase(language) ? target : 0);
        if ("ZH".equalsIgnoreCase(language)) {
            searchPlanService.buildPlan(query);
        }
        List<ReferenceCandidate> found = dedupe(searchOnline(query, language)).stream()
                .filter(ReferenceCandidate::basicallyVerified)
                .filter(candidate -> language.equalsIgnoreCase(languageOf(candidate)))
                .sorted(Comparator.comparingDouble(ReferenceCandidate::relevanceScore).reversed())
                .limit(target)
                .toList();
        jdbcTemplate.update("DELETE FROM writing_reference WHERE project_id=? AND COALESCE(language,?)=?", projectId, language.toUpperCase(Locale.ROOT), language.toUpperCase(Locale.ROOT));
        int next = WritingJdbc.integer(WritingJdbc.one(jdbcTemplate, "SELECT COALESCE(MAX(citation_number),0)+1 AS n FROM writing_reference WHERE project_id=?", projectId).get("n"), 1);
        for (ReferenceCandidate candidate : found) insertReference(projectId, candidate, next++, null);
        jdbcTemplate.update("UPDATE writing_project SET search_provider=?, search_status=?, search_message=?, updated_at=? WHERE id=?",
                activeProviderNames(), found.size() >= target ? "SUCCESS" : "PARTIAL",
                language.toUpperCase(Locale.ROOT) + " references found " + found.size() + "/" + target, LocalDateTime.now(), projectId);
        return references(userId, projectId);
    }

    public List<Map<String, Object>> completeMetadata(Long userId, String projectId, String referenceId) {
        WritingJdbc.one(jdbcTemplate, "SELECT id FROM writing_project WHERE id=? AND user_id=?", projectId, userId);
        Map<String, Object> reference = WritingJdbc.one(jdbcTemplate,
                "SELECT * FROM writing_reference WHERE project_id=? AND id=?", projectId, referenceId);
        jdbcTemplate.update("UPDATE writing_reference SET verification_message=?, updated_at=? WHERE id=?",
                "Metadata completion requires a fresh Doubao Web Search query and public URL evidence; existing public metadata preserved.",
                LocalDateTime.now(), reference.get("id"));
        return references(userId, projectId);
    }

    @Transactional
    public List<Map<String, Object>> verifySavedReferences(Long userId, String projectId) {
        WritingJdbc.one(jdbcTemplate, "SELECT id FROM writing_project WHERE id=? AND user_id=?", projectId, userId);
        List<Map<String, Object>> rows = WritingJdbc.list(jdbcTemplate, "SELECT * FROM writing_reference WHERE project_id=?", projectId);
        for (Map<String, Object> row : rows) {
            String status = hasFormalFields(row) ? "VERIFIED_PRIMARY_PUBLIC" : "UNVERIFIED";
            String message = hasFormalFields(row) ? "Public URL and required bibliographic fields are present" : "Missing title, authors, year, source, or public URL";
            jdbcTemplate.update("UPDATE writing_reference SET verification_status=?, verification_message=?, updated_at=? WHERE id=?",
                    status, message, LocalDateTime.now(), row.get("id"));
        }
        return references(userId, projectId);
    }

    public List<Map<String, Object>> references(Long userId, String projectId) {
        WritingJdbc.one(jdbcTemplate, "SELECT id FROM writing_project WHERE id=? AND user_id=?", projectId, userId);
        return WritingJdbc.list(jdbcTemplate,
                """
                SELECT * FROM writing_reference
                WHERE project_id=?
                ORDER BY final_number IS NULL, final_number,
                citation_number IS NULL, citation_number, relevance_score DESC, created_at
                """, projectId);
    }

    public void deleteReference(Long userId, String projectId, String referenceId) {
        WritingJdbc.one(jdbcTemplate, "SELECT id FROM writing_project WHERE id=? AND user_id=?", projectId, userId);
        jdbcTemplate.update("DELETE FROM writing_reference WHERE project_id=? AND id=?", projectId, referenceId);
    }

    @Transactional
    public List<Map<String, Object>> deduplicateSavedReferences(Long userId, String projectId) {
        WritingJdbc.one(jdbcTemplate, "SELECT id FROM writing_project WHERE id=? AND user_id=?", projectId, userId);
        List<Map<String, Object>> rows = WritingJdbc.list(jdbcTemplate,
                """
                SELECT * FROM writing_reference
                WHERE project_id=?
                ORDER BY final_number IS NULL, final_number,
                citation_number IS NULL, citation_number, relevance_score DESC, created_at
                """,
                projectId);
        Map<String, Map<String, Object>> keep = new LinkedHashMap<>();
        List<Object> removeIds = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            String doi = WritingJdbc.text(row.get("doi")).toLowerCase(Locale.ROOT);
            String key = !doi.isBlank()
                    ? "doi:" + doi
                    : "title:" + normalize(WritingJdbc.text(row.get("title"))) + ":" + WritingJdbc.integer(row.get("publication_year"), 0);
            Map<String, Object> previous = keep.get(key);
            if (previous == null) {
                keep.put(key, row);
                continue;
            }
            if (referenceRank(row) > referenceRank(previous)) {
                removeIds.add(previous.get("id"));
                keep.put(key, row);
            } else {
                removeIds.add(row.get("id"));
            }
        }
        for (Object id : removeIds) {
            jdbcTemplate.update("DELETE FROM writing_reference WHERE project_id=? AND id=?", projectId, id);
        }
        renumber(projectId);
        return references(userId, projectId);
    }

    @Transactional
    public List<Map<String, Object>> assignSavedReferencesToChapters(Long userId, String projectId) {
        WritingJdbc.one(jdbcTemplate, "SELECT id FROM writing_project WHERE id=? AND user_id=?", projectId, userId);
        List<Map<String, Object>> chapters = WritingJdbc.list(jdbcTemplate,
                "SELECT * FROM writing_chapter WHERE project_id=? ORDER BY sort_order, chapter_no", projectId);
        List<Map<String, Object>> rows = WritingJdbc.list(jdbcTemplate,
                """
                SELECT * FROM writing_reference
                WHERE project_id=?
                ORDER BY final_number IS NULL, final_number,
                citation_number IS NULL, citation_number, relevance_score DESC, created_at
                """,
                projectId);
        for (Map<String, Object> row : rows) {
            String haystack = normalize(String.join(" ",
                    WritingJdbc.text(row.get("title")),
                    WritingJdbc.text(row.get("abstract_text")),
                    WritingJdbc.text(row.get("search_keywords")),
                    WritingJdbc.text(row.get("journal_or_publisher"))));
            List<String> matched = new ArrayList<>();
            for (Map<String, Object> chapter : chapters) {
                String chapterNo = String.valueOf(WritingJdbc.integer(chapter.get("chapter_no"), 0));
                String title = WritingJdbc.text(chapter.get("title"));
                if (matchesChapter(haystack, title)) matched.add(chapterNo);
            }
            if (matched.isEmpty() && !chapters.isEmpty()) {
                int index = Math.floorMod(WritingJdbc.integer(row.get("citation_number"), rows.indexOf(row) + 1) - 1, chapters.size());
                matched.add(String.valueOf(WritingJdbc.integer(chapters.get(index).get("chapter_no"), index + 1)));
            }
            jdbcTemplate.update("UPDATE writing_reference SET applicable_chapters=?, updated_at=? WHERE project_id=? AND id=?",
                    String.join(",", matched), LocalDateTime.now(), projectId, row.get("id"));
        }
        return references(userId, projectId);
    }

    private List<String> readKeywords(Map<String, Object> project) {
        try {
            String json = WritingJdbc.text(project.get("keywords_json"));
            if (!json.isBlank()) {
                return objectMapper.readValue(json, objectMapper.getTypeFactory().constructCollectionType(List.class, String.class));
            }
        } catch (Exception ignored) {
        }
        String title = WritingJdbc.text(project.get("title"));
        List<String> result = new ArrayList<>();
        for (String part : title.split("[\\s，,、：:]+")) {
            if (part.length() >= 2) result.add(part);
        }
        if (result.isEmpty()) result.add(title);
        return result;
    }

    private List<ReferenceCandidate> searchOnline(ReferenceSearchQuery query) {
        return searchOnline(query, null);
    }

    private List<ReferenceCandidate> searchOnline(ReferenceSearchQuery query, String language) {
        List<ReferenceCandidate> result = new ArrayList<>();
        RuntimeException lastError = null;
        for (String providerName : properties.getReferenceSearch().providerOrder()) {
            ReferenceSearchProvider provider = providers.get(providerName);
            if (provider == null || !provider.available() || !provider.supportsLanguage(language)) continue;
            int attempts = Math.max(1, Math.min(2, properties.getReferenceSearch().getRetryCount()));
            for (int attempt = 1; attempt <= attempts; attempt++) {
                long started = System.currentTimeMillis();
                try {
                    List<ReferenceCandidate> found = provider.search(query);
                    insertSearchLog(query.projectId(), provider.providerCode(), language, query.providerKeywords(), found.size(), found.size(), System.currentTimeMillis() - started, true, "", "");
                    result.addAll(found);
                    if (result.size() >= query.maxResults()) return result;
                    break;
                } catch (RuntimeException exception) {
                    lastError = exception;
                    insertSearchLog(query.projectId(), provider.providerCode(), language, query.providerKeywords(), 0, 0, System.currentTimeMillis() - started, false,
                            exception.getClass().getSimpleName(), String.valueOf(exception.getMessage()));
                    if (attempt < attempts) {
                        try {
                            Thread.sleep(150L);
                        } catch (InterruptedException interruptedException) {
                            Thread.currentThread().interrupt();
                            throw new IllegalStateException("参考文献搜索被中断", interruptedException);
                        }
                    }
                }
            }
        }
        if (result.isEmpty() && lastError != null) {
            throw new IllegalStateException("公开学术来源暂时无法访问，请稍后重试", lastError);
        }
        if (result.isEmpty()) throw new IllegalStateException("暂未检索到符合条件的公开文献，请尝试补充或调整题目关键词");
        return result;
    }

    public List<Map<String, Object>> searchLogs(Long userId, String projectId) {
        WritingJdbc.one(jdbcTemplate, "SELECT id FROM writing_project WHERE id=? AND user_id=?", projectId, userId);
        try {
            return WritingJdbc.list(jdbcTemplate, "SELECT * FROM writing_reference_search_log WHERE project_id=? ORDER BY created_at DESC LIMIT 100", projectId);
        } catch (Exception ignored) {
            return List.of();
        }
    }

    public Map<String, Object> standaloneSearch(String chineseKeyword, String englishKeyword, int count) {
        int target = Math.max(1, Math.min(20, count));
        int currentYear = Year.now().getValue();
        List<String> keywords = new ArrayList<>();
        if (chineseKeyword != null && !chineseKeyword.isBlank()) keywords.add(chineseKeyword.trim());
        if (englishKeyword != null && !englishKeyword.isBlank()) keywords.add(englishKeyword.trim());
        if (keywords.isEmpty()) throw new IllegalArgumentException("请输入中文或英文关键词");
        ReferenceSearchQuery query = new ReferenceSearchQuery(
                "literature_" + WritingJdbc.id("search"),
                String.join(" / ", keywords),
                "文献中心独立检索",
                keywords,
                List.of(),
                currentYear - 5,
                currentYear,
                target,
                0,
                0
        );
        List<ReferenceCandidate> selected = dedupe(searchOnline(query)).stream()
                .filter(ReferenceCandidate::basicallyVerified)
                .sorted(Comparator.comparingDouble(ReferenceCandidate::relevanceScore).reversed())
                .limit(target)
                .toList();
        List<Map<String, Object>> items = new ArrayList<>();
        int index = 1;
        for (ReferenceCandidate candidate : selected) {
            items.add(Map.ofEntries(
                    Map.entry("number", index),
                    Map.entry("title", blank(candidate.title())),
                    Map.entry("authors", String.join("; ", candidate.authors() == null ? List.of() : candidate.authors())),
                    Map.entry("year", candidate.year() == null ? "" : candidate.year()),
                    Map.entry("source", blank(candidate.container())),
                    Map.entry("abstractText", blank(candidate.abstractText())),
                    Map.entry("url", blank(candidate.url())),
                    Map.entry("language", languageOf(candidate)),
                    Map.entry("gbt7714", formatter.format(index, candidate, "GBT_7714_2025"))
            ));
            index++;
        }
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("keywordZh", blank(chineseKeyword));
        response.put("keywordEn", blank(englishKeyword));
        response.put("requestedCount", target);
        response.put("actualCount", items.size());
        response.put("costPoints", target);
        response.put("citationText", citationText(items));
        response.put("items", items);
        return response;
    }

    public Map<String, Object> standaloneSearch(String title, int chineseCount, int englishCount) {
        String normalizedTitle = blank(title).trim();
        if (normalizedTitle.isBlank()) throw new IllegalArgumentException("请输入题目名称");
        int zhTarget = Math.max(0, Math.min(20, chineseCount));
        int enTarget = Math.max(0, Math.min(20, englishCount));
        if (zhTarget + enTarget < 1) throw new IllegalArgumentException("中文和英文文献数量不能同时为 0");

        int timeoutSeconds = Math.max(1, Math.min(20, properties.getReferenceSearch().getTimeoutSeconds()));
        long deadlineNanos = System.nanoTime() + TimeUnit.SECONDS.toNanos(timeoutSeconds);
        int currentYear = Year.now().getValue();
        int configuredMax = Math.max(1, Math.min(50, properties.getReferenceSearch().getMaxResults()));
        List<SearchBranch> branches = new ArrayList<>();
        if (zhTarget > 0) {
            int searchMax = Math.min(configuredMax, Math.max(zhTarget * 5, zhTarget + 20));
            ReferenceSearchQuery zhQuery = new ReferenceSearchQuery(
                    "literature_" + WritingJdbc.id("zh"), normalizedTitle, "",
                    List.of(normalizedTitle), List.of(), currentYear - 50, currentYear,
                    searchMax, zhTarget, 0);
            zhQuery = zhQuery.withProviderKeywords(topicMatcher.providerQuery(zhQuery));
            branches.add(new SearchBranch("ZH", zhTarget, zhQuery,
                    topicMatcher.hasReliablePlan(zhQuery, "ZH")));
        }
        if (enTarget > 0) {
            int searchMax = Math.min(configuredMax, Math.max(enTarget * 5, enTarget + 20));
            ReferenceSearchQuery enQuery = new ReferenceSearchQuery(
                    "literature_" + WritingJdbc.id("en"), normalizedTitle, "",
                    List.of(normalizedTitle), List.of(), currentYear - 50, currentYear,
                    searchMax, 0, enTarget);
            String providerKeywords = topicMatcher.providerQuery(enQuery);
            boolean needsPlanning = topicMatcher.needsEnglishPlanning(enQuery);
            boolean deterministicReliable = !providerKeywords.isBlank()
                    && topicMatcher.hasReliablePlan(enQuery.withProviderKeywords(providerKeywords), "EN");
            if (needsPlanning || !deterministicReliable) {
                providerKeywords = plannedEnglishProviderKeywords(normalizedTitle, deadlineNanos);
            }
            if (!providerKeywords.isBlank()) enQuery = enQuery.withProviderKeywords(providerKeywords);
            boolean queryAvailable = !providerKeywords.isBlank()
                    && topicMatcher.hasReliablePlan(enQuery, "EN")
                    && (!needsPlanning || enQuery.hasProviderKeywordsOverride());
            branches.add(new SearchBranch("EN", enTarget, enQuery, queryAvailable));
        }

        ParallelSearchResult searchResult = searchStandaloneBranches(branches, deadlineNanos);
        List<ReferenceCandidate> unique = searchResult.selected();
        List<Map<String, Object>> items = new ArrayList<>();
        int index = 1;
        for (ReferenceCandidate candidate : unique) {
            items.add(Map.ofEntries(
                    Map.entry("number", index), Map.entry("title", blank(candidate.title())),
                    Map.entry("authors", String.join("; ", candidate.authors() == null ? List.of() : candidate.authors())),
                    Map.entry("year", candidate.year() == null ? "" : candidate.year()),
                    Map.entry("source", blank(candidate.container())), Map.entry("abstractText", blank(candidate.abstractText())),
                    Map.entry("url", blank(candidate.url())), Map.entry("doi", blank(candidate.doi())),
                    Map.entry("language", languageOf(candidate)),
                    Map.entry("gbt7714", formatter.format(index, candidate, "GBT_7714_2025"))));
            index++;
        }
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("title", normalizedTitle);
        response.put("chineseCount", zhTarget);
        response.put("englishCount", enTarget);
        response.put("requestedCount", zhTarget + enTarget);
        response.put("actualCount", items.size());
        response.put("actualChineseCount", searchResult.actualChineseCount());
        response.put("actualEnglishCount", searchResult.actualEnglishCount());
        response.put("status", searchResult.warnings().isEmpty() ? "SUCCESS" : "PARTIAL");
        response.put("partial", !searchResult.warnings().isEmpty());
        response.put("warnings", searchResult.warnings());
        response.put("searchMode", "PARALLEL_MULTI_PROVIDER_RANKED_FILL");
        response.put("providersTried", searchResult.providersTried());
        response.put("providerOutcomes", searchResult.providerOutcomes());
        response.put("timedOut", searchResult.timedOut());
        response.put("citationText", citationText(items));
        response.put("items", items);
        return response;
    }

    private ParallelSearchResult searchStandaloneBranches(List<SearchBranch> branches, long deadlineNanos) {
        List<ProviderTask> tasks = new ArrayList<>();
        int providerIndex = 0;
        for (String providerName : properties.getReferenceSearch().providerOrder()) {
            ReferenceSearchProvider provider = providers.get(providerName);
            if (provider == null) continue;
            for (SearchBranch branch : branches) {
                if (provider.available() && provider.supportsLanguage(branch.language())) {
                    tasks.add(new ProviderTask(branch, provider, providerIndex));
                }
            }
            providerIndex++;
        }
        tasks = distinctTasks(tasks);
        if (tasks.isEmpty()) {
            throw new IllegalStateException("当前未启用可用的公开文献检索来源，请检查服务器搜索配置");
        }

        CompletionService<ProviderSearchOutcome> completion = new ExecutorCompletionService<>(searchExecutor);
        List<Future<ProviderSearchOutcome>> futures = new ArrayList<>();
        List<ProviderSearchOutcome> outcomes = new ArrayList<>();
        int submitted = 0;
        for (ProviderTask task : tasks) {
            try {
                futures.add(completion.submit(() -> callProvider(task)));
                submitted++;
            } catch (RejectedExecutionException exception) {
                outcomes.add(ProviderSearchOutcome.busy(task));
            }
        }
        if (submitted == 0) {
            throw new IllegalStateException("文献检索任务较多，请稍后重试");
        }

        int completed = 0;
        boolean timedOut = false;
        try {
            while (completed < submitted) {
                long remaining = deadlineNanos - System.nanoTime();
                if (remaining <= 0) {
                    timedOut = true;
                    break;
                }
                Future<ProviderSearchOutcome> completedFuture = completion.poll(remaining, TimeUnit.NANOSECONDS);
                if (completedFuture == null) {
                    timedOut = true;
                    break;
                }
                completed++;
                try {
                    outcomes.add(completedFuture.get());
                } catch (Exception exception) {
                    // Provider tasks return an outcome for normal failures. Keep another task alive if one
                    // encounters an unexpected runtime error.
                }
                if (quotasSatisfied(branches, outcomes)) break;
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("参考文献搜索被中断", exception);
        } finally {
            futures.forEach(future -> {
                if (!future.isDone()) future.cancel(true);
            });
        }

        outcomes.sort(Comparator.comparingInt(ProviderSearchOutcome::providerIndex)
                .thenComparing(ProviderSearchOutcome::language));
        List<ReferenceCandidate> selected = new ArrayList<>();
        CandidateIdentitySet selectedKeys = new CandidateIdentitySet();
        int actualChineseCount = addBranchSelection("ZH", branches, outcomes, selected, selectedKeys);
        int actualEnglishCount = addBranchSelection("EN", branches, outcomes, selected, selectedKeys);
        int expectedChineseCount = branchTarget(branches, "ZH");
        int expectedEnglishCount = branchTarget(branches, "EN");
        if (actualChineseCount != expectedChineseCount || actualEnglishCount != expectedEnglishCount) {
            String progress = "中文 " + actualChineseCount + "/" + expectedChineseCount
                    + "，英文 " + actualEnglishCount + "/" + expectedEnglishCount;
            if (timedOut) {
                throw new IllegalStateException("20 秒内未凑齐目标文献（" + progress + "），本次不返回结果且不扣费");
            }
            throw new IllegalStateException("未凑齐目标文献（" + progress + "），本次不返回结果且不扣费");
        }

        List<Map<String, Object>> publicOutcomes = outcomes.stream().map(outcome -> Map.<String, Object>of(
                "language", outcome.language(),
                "provider", outcome.providerCode(),
                "status", outcome.status(),
                "resultCount", outcome.candidates().size(),
                "rawCount", outcome.rawCount(),
                "acceptedCount", outcome.candidates().size(),
                "rejectedCount", outcome.rejectedCount(),
                "durationMs", outcome.durationMs()
        )).toList();
        int providersTried = (int) tasks.stream().map(task -> task.provider().providerCode()).distinct().count();
        return new ParallelSearchResult(selected, actualChineseCount, actualEnglishCount,
                List.of(), publicOutcomes, timedOut, providersTried);
    }

    private String plannerProviderKeywords(LiteratureEnglishQueryPlanner.Plan plan) {
        List<String> terms = plan.coreTerms().stream()
                .filter(term -> term != null && !term.isBlank())
                .distinct()
                .limit(3)
                .map(term -> "\"" + term.trim() + "\"")
                .toList();
        if (!terms.isEmpty()) return String.join(" ", terms);
        return plan.englishQuery();
    }

    private String plannedEnglishProviderKeywords(String title, long deadlineNanos) {
        long waitNanos = Math.min(ENGLISH_PLANNER_BUDGET_NANOS, deadlineNanos - System.nanoTime());
        if (waitNanos <= 0) return "";
        Future<java.util.Optional<LiteratureEnglishQueryPlanner.Plan>> future;
        try {
            future = searchExecutor.submit(() -> englishQueryPlanner.plan(title));
        } catch (RejectedExecutionException exception) {
            return "";
        }
        try {
            java.util.Optional<LiteratureEnglishQueryPlanner.Plan> plan = future.get(waitNanos, TimeUnit.NANOSECONDS);
            return plan == null ? "" : plan.map(this::plannerProviderKeywords).orElse("");
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return "";
        } catch (Exception exception) {
            return "";
        } finally {
            if (!future.isDone()) future.cancel(true);
        }
    }

    private List<ProviderTask> distinctTasks(List<ProviderTask> tasks) {
        Set<ReferenceSearchProvider> seenChinese = Collections.newSetFromMap(new IdentityHashMap<>());
        Set<ReferenceSearchProvider> seenEnglish = Collections.newSetFromMap(new IdentityHashMap<>());
        List<ProviderTask> distinct = new ArrayList<>();
        for (ProviderTask task : tasks) {
            Set<ReferenceSearchProvider> seen = "ZH".equals(task.branch().language()) ? seenChinese : seenEnglish;
            if (seen.add(task.provider())) distinct.add(task);
        }
        return distinct;
    }

    private ProviderSearchOutcome callProvider(ProviderTask task) {
        int attempts = Math.max(1, Math.min(2, properties.getReferenceSearch().getRetryCount()));
        long started = System.currentTimeMillis();
        if (!task.branch().queryAvailable()
                || !topicMatcher.hasReliablePlan(task.branch().query(), task.branch().language())) {
            return ProviderSearchOutcome.queryUnavailable(task, started);
        }
        for (int attempt = 1; attempt <= attempts; attempt++) {
            if (Thread.currentThread().isInterrupted()) return ProviderSearchOutcome.interrupted(task, started);
            long attemptStarted = System.currentTimeMillis();
            try {
                List<ReferenceCandidate> found = task.provider().search(task.branch().query());
                List<ReferenceCandidate> rawFound = found == null ? List.of() : found;
                List<ReferenceCandidate> safeFound = rawFound.stream()
                        .filter(Objects::nonNull)
                        .filter(ReferenceCandidate::basicallyVerified)
                        .filter(candidate -> task.branch().language().equals(languageOf(candidate)))
                        .filter(candidate -> withinYear(candidate, task.branch().query()))
                        .filter(this::hasPublicSource)
                        .sorted(candidateRanking(task.branch()))
                        .toList();
                insertSearchLog(task.branch().query().projectId(), task.provider().providerCode(), task.branch().language(),
                        task.branch().query().providerKeywords(), rawFound.size(), safeFound.size(),
                        System.currentTimeMillis() - attemptStarted, true, "", "");
                return new ProviderSearchOutcome(task.branch().language(), task.providerIndex(), task.provider().providerCode(),
                        safeFound, safeFound.isEmpty() ? "EMPTY" : "SUCCESS", System.currentTimeMillis() - started,
                        rawFound.size(), Math.max(0, rawFound.size() - safeFound.size()));
            } catch (RuntimeException exception) {
                insertSearchLog(task.branch().query().projectId(), task.provider().providerCode(), task.branch().language(),
                        task.branch().query().providerKeywords(), 0, 0, System.currentTimeMillis() - attemptStarted, false,
                        exception.getClass().getSimpleName(), String.valueOf(exception.getMessage()));
                if (attempt < attempts) {
                    try {
                        Thread.sleep(150L);
                    } catch (InterruptedException interruptedException) {
                        Thread.currentThread().interrupt();
                        return ProviderSearchOutcome.interrupted(task, started);
                    }
                }
            }
        }
        return new ProviderSearchOutcome(task.branch().language(), task.providerIndex(), task.provider().providerCode(),
                List.of(), "FAILED", System.currentTimeMillis() - started, 0, 0);
    }

    private boolean quotasSatisfied(List<SearchBranch> branches, List<ProviderSearchOutcome> outcomes) {
        List<ProviderSearchOutcome> orderedOutcomes = new ArrayList<>(outcomes);
        orderedOutcomes.sort(Comparator.comparingInt(ProviderSearchOutcome::providerIndex)
                .thenComparing(ProviderSearchOutcome::language));
        CandidateIdentitySet selectedKeys = new CandidateIdentitySet();
        for (SearchBranch branch : branches) {
            List<CandidateGroup> candidates = rankedCandidateGroups(branch, orderedOutcomes.stream()
                    .filter(outcome -> branch.language().equals(outcome.language()))
                    .flatMap(outcome -> outcome.candidates().stream())
                    .toList());
            int count = 0;
            for (CandidateGroup candidate : candidates) {
                if (selectedKeys.add(candidate)) count++;
                if (count >= branch.target()) break;
            }
            if (count < branch.target()) return false;
        }
        return true;
    }

    private int addBranchSelection(String language, List<SearchBranch> branches,
                                   List<ProviderSearchOutcome> outcomes, List<ReferenceCandidate> selected,
                                   CandidateIdentitySet selectedKeys) {
        SearchBranch branch = branches.stream().filter(item -> language.equals(item.language())).findFirst().orElse(null);
        if (branch == null) return 0;
        List<CandidateGroup> candidates = rankedCandidateGroups(branch, outcomes.stream()
                .filter(outcome -> language.equals(outcome.language()))
                .flatMap(outcome -> outcome.candidates().stream())
                .toList());
        List<ReferenceCandidate> branchSelected = new ArrayList<>();
        for (CandidateGroup candidate : candidates) {
            if (selectedKeys.add(candidate)) branchSelected.add(candidate.candidate());
            if (branchSelected.size() >= branch.target()) break;
        }
        selected.addAll(branchSelected);
        return branchSelected.size();
    }

    private List<CandidateGroup> rankedCandidateGroups(SearchBranch branch, List<ReferenceCandidate> candidates) {
        List<ReferenceCandidate> ranked = candidates.stream()
                .filter(Objects::nonNull)
                .filter(ReferenceCandidate::basicallyVerified)
                .filter(candidate -> branch.language().equals(languageOf(candidate)))
                .filter(candidate -> withinYear(candidate, branch.query()))
                .filter(this::hasPublicSource)
                .sorted(candidateRanking(branch))
                .toList();
        return groupCandidates(ranked);
    }

    private Comparator<ReferenceCandidate> candidateRanking(SearchBranch branch) {
        return Comparator
                .comparing((ReferenceCandidate candidate) ->
                        topicMatcher.isRelevant(branch.query(), candidate, branch.language())).reversed()
                .thenComparing(Comparator.comparingDouble((ReferenceCandidate candidate) ->
                        topicMatcher.rankingScore(branch.query(), candidate, branch.language())).reversed())
                .thenComparing(candidate -> blank(candidate.title()));
    }

    private boolean withinYear(ReferenceCandidate candidate, ReferenceSearchQuery query) {
        if (candidate.year() == null) return false;
        if (query.yearStart() > 0 && candidate.year() < query.yearStart()) return false;
        return query.yearEnd() <= 0 || candidate.year() <= query.yearEnd();
    }

    private boolean hasPublicSource(ReferenceCandidate candidate) {
        try {
            java.net.URI uri = java.net.URI.create(blank(candidate.url()).trim());
            String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
            return ("http".equals(scheme) || "https".equals(scheme))
                    && uri.getHost() != null && !uri.getHost().isBlank();
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    private int branchTarget(List<SearchBranch> branches, String language) {
        return branches.stream().filter(branch -> language.equals(branch.language()))
                .mapToInt(SearchBranch::target).findFirst().orElse(0);
    }

    private record SearchBranch(String language, int target, ReferenceSearchQuery query, boolean queryAvailable) {
    }

    private record ProviderTask(SearchBranch branch, ReferenceSearchProvider provider, int providerIndex) {
    }

    private record ProviderSearchOutcome(String language, int providerIndex, String providerCode,
                                          List<ReferenceCandidate> candidates, String status, long durationMs,
                                          int rawCount, int rejectedCount) {
        private static ProviderSearchOutcome busy(ProviderTask task) {
            return new ProviderSearchOutcome(task.branch().language(), task.providerIndex(), task.provider().providerCode(),
                    List.of(), "BUSY", 0L, 0, 0);
        }

        private static ProviderSearchOutcome interrupted(ProviderTask task, long started) {
            return new ProviderSearchOutcome(task.branch().language(), task.providerIndex(), task.provider().providerCode(),
                    List.of(), "CANCELLED", System.currentTimeMillis() - started, 0, 0);
        }

        private static ProviderSearchOutcome queryUnavailable(ProviderTask task, long started) {
            return new ProviderSearchOutcome(task.branch().language(), task.providerIndex(), task.provider().providerCode(),
                    List.of(), "QUERY_UNAVAILABLE", System.currentTimeMillis() - started, 0, 0);
        }
    }

    private record ParallelSearchResult(List<ReferenceCandidate> selected, int actualChineseCount,
                                        int actualEnglishCount, List<String> warnings,
                                        List<Map<String, Object>> providerOutcomes, boolean timedOut,
                                        int providersTried) {
    }

    private String citationText(List<Map<String, Object>> items) {
        StringBuilder builder = new StringBuilder();
        for (Map<String, Object> item : items) {
            builder.append(item.get("gbt7714")).append("\n");
        }
        return builder.toString().trim();
    }

    private void insertSearchLog(String projectId, String provider, String language, String queryText, int resultCount,
                                 int acceptedCount, long durationMs, boolean success, String errorCode, String errorMessage) {
        try {
            jdbcTemplate.update("""
                    INSERT INTO writing_reference_search_log (id, project_id, provider, language, query_text, request_api_type, request_method,
                    model, web_search_enabled, result_count, accepted_count, rejected_count, duration_ms, success, error_code, error_message, created_at)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """, WritingJdbc.id("wrsl"), projectId, provider, language == null ? "" : language.toUpperCase(Locale.ROOT),
                    queryText, "RESPONSES_API", "POST", "", "doubao_web".equals(provider), resultCount, acceptedCount,
                    Math.max(0, resultCount - acceptedCount), durationMs, success, errorCode,
                    errorMessage == null ? "" : errorMessage.substring(0, Math.min(500, errorMessage.length())), LocalDateTime.now());
        } catch (Exception ignored) {
        }
    }

    private List<ReferenceCandidate> dedupe(List<ReferenceCandidate> candidates) {
        List<ReferenceCandidate> ranked = candidates.stream()
                .filter(Objects::nonNull)
                .sorted(Comparator.comparingDouble(ReferenceCandidate::relevanceScore).reversed())
                .toList();
        return groupCandidates(ranked).stream().map(CandidateGroup::candidate).toList();
    }

    private List<CandidateGroup> groupCandidates(List<ReferenceCandidate> ordered) {
        if (ordered.isEmpty()) return List.of();
        int[] parents = new int[ordered.size()];
        Map<String, Integer> doiOwners = new LinkedHashMap<>();
        Map<String, Integer> titleYearOwners = new LinkedHashMap<>();
        for (int index = 0; index < ordered.size(); index++) {
            parents[index] = index;
            ReferenceCandidate candidate = ordered.get(index);
            String doi = normalizeDoi(candidate.doi());
            String titleYear = titleYearKey(candidate);
            if (!doi.isBlank()) linkIdentity(doiOwners, doi, index, parents);
            linkIdentity(titleYearOwners, titleYear, index, parents);
        }

        Map<Integer, ReferenceCandidate> representatives = new LinkedHashMap<>();
        Map<Integer, Set<String>> componentDois = new LinkedHashMap<>();
        Map<Integer, Set<String>> componentTitleYears = new LinkedHashMap<>();
        for (int index = 0; index < ordered.size(); index++) {
            ReferenceCandidate candidate = ordered.get(index);
            int root = findRoot(parents, index);
            representatives.putIfAbsent(root, candidate);
            String doi = normalizeDoi(candidate.doi());
            if (!doi.isBlank()) componentDois.computeIfAbsent(root, ignored -> new LinkedHashSet<>()).add(doi);
            componentTitleYears.computeIfAbsent(root, ignored -> new LinkedHashSet<>()).add(titleYearKey(candidate));
        }
        return representatives.entrySet().stream()
                .map(entry -> new CandidateGroup(entry.getValue(),
                        Set.copyOf(componentDois.getOrDefault(entry.getKey(), Set.of())),
                        Set.copyOf(componentTitleYears.getOrDefault(entry.getKey(), Set.of()))))
                .toList();
    }

    private void linkIdentity(Map<String, Integer> owners, String identity, int index, int[] parents) {
        Integer owner = owners.putIfAbsent(identity, index);
        if (owner != null) union(parents, owner, index);
    }

    private int findRoot(int[] parents, int index) {
        int root = index;
        while (parents[root] != root) root = parents[root];
        while (parents[index] != index) {
            int next = parents[index];
            parents[index] = root;
            index = next;
        }
        return root;
    }

    private void union(int[] parents, int left, int right) {
        int leftRoot = findRoot(parents, left);
        int rightRoot = findRoot(parents, right);
        if (leftRoot != rightRoot) parents[rightRoot] = leftRoot;
    }

    private String titleYearKey(ReferenceCandidate candidate) {
        return normalize(candidate.title()) + ":" + candidate.year();
    }

    private final class CandidateIdentitySet {
        private final Set<String> dois = new LinkedHashSet<>();
        private final Set<String> titleYears = new LinkedHashSet<>();

        private boolean add(CandidateGroup candidate) {
            boolean duplicate = candidate.dois().stream().anyMatch(dois::contains)
                    || candidate.titleYears().stream().anyMatch(titleYears::contains);
            dois.addAll(candidate.dois());
            titleYears.addAll(candidate.titleYears());
            return !duplicate;
        }
    }

    private record CandidateGroup(ReferenceCandidate candidate, Set<String> dois, Set<String> titleYears) {
    }

    private String normalizeDoi(String doi) {
        if (doi == null) return "";
        return doi.trim().toLowerCase(Locale.ROOT)
                .replaceFirst("^https?://(dx\\.)?doi\\.org/", "")
                .replaceFirst("^doi\\s*:\\s*", "");
    }

    private String normalize(String title) {
        return title == null ? "" : title.toLowerCase(Locale.ROOT).replaceAll("[^\\p{IsHan}a-z0-9]+", "");
    }

    private double referenceRank(Map<String, Object> row) {
        String status = WritingJdbc.text(row.get("verification_status")).toUpperCase(Locale.ROOT);
        double statusScore = switch (status) {
            case "VERIFIED", "VERIFIED_AUTHORIZED" -> 3.0;
            case "PARTIALLY_VERIFIED" -> 2.0;
            default -> 1.0;
        };
        Number relevance = row.get("relevance_score") instanceof Number number ? number : 0;
        return statusScore * 100 + relevance.doubleValue();
    }

    private boolean matchesChapter(String haystack, String chapterTitle) {
        String normalizedTitle = normalize(chapterTitle);
        if (normalizedTitle.length() >= 3 && haystack.contains(normalizedTitle)) return true;
        for (String token : chapterTitle.split("[\\s,，、:：;；（）()]+")) {
            String normalizedToken = normalize(token);
            if (normalizedToken.length() >= 3 && haystack.contains(normalizedToken)) return true;
        }
        return false;
    }

    private void renumber(String projectId) {
        List<Map<String, Object>> rows = WritingJdbc.list(jdbcTemplate,
                """
                SELECT id FROM writing_reference
                WHERE project_id=?
                ORDER BY final_number IS NULL, final_number,
                citation_number IS NULL, citation_number, relevance_score DESC, created_at
                """,
                projectId);
        int index = 1;
        for (Map<String, Object> row : rows) {
            jdbcTemplate.update("UPDATE writing_reference SET citation_number=?, final_number=?, reference_key=?, updated_at=? WHERE id=?",
                    index, index, "ref_" + String.format("%03d", index), LocalDateTime.now(), row.get("id"));
            index++;
        }
    }

    private void insertReference(String projectId, ReferenceCandidate candidate, int index, Integer chapterNo) {
        LocalDateTime now = LocalDateTime.now();
        String id = WritingJdbc.id("ref");
        String authors = String.join("; ", candidate.authors());
        String formatted = formatter.format(index, candidate, "GBT_7714_2025");
        jdbcTemplate.update("""
                INSERT INTO writing_reference (id, project_id, reference_key, title, authors, publication_year,
                journal_or_publisher, volume, issue, pages, doi, url, source_platform, abstract_text, search_keywords,
                searched_at, applicable_chapters, verification_status, relevance_score, formatted_text, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                id, projectId, "ref_" + String.format("%03d", index), candidate.title(), authors, candidate.year(),
                candidate.container(), candidate.volume(), candidate.issue(), candidate.pages(), blankToNull(candidate.doi()),
                candidate.url(), candidate.sourcePlatform(), candidate.abstractText(), candidate.searchKeywords(),
                candidate.searchedAt(), chapterNo == null ? "" : String.valueOf(chapterNo),
                candidate.verificationStatus(), candidate.relevanceScore(), formatted, now, now);
        updateReferenceExtendedFields(id, candidate, index);
        insertReferenceEvidence(projectId, id, candidate);
    }

    private void updateReferenceExtendedFields(String id, ReferenceCandidate candidate, int index) {
        String missingFields = missingFieldsJson(candidate);
        String evidenceJson = sourceEvidenceJson(candidate);
        try {
            jdbcTemplate.update("""
                    UPDATE writing_reference SET language=?, provider=?, citation_number=?, source_url=?, landing_page_url=?,
                    journal=?, publisher=?, verified_at=?, final_number=?, document_type=?, source_database=?,
                    source_query=?, retrieved_at=?, abstract_source_type=?, verification_message=?, format_incomplete=?,
                    missing_fields_json=?, metadata_conflicts_json=?, source_evidence_json=?, raw_metadata_json=? WHERE id=?
                    """,
                    languageOf(candidate), providerOf(candidate), index, candidate.url(), candidate.url(), candidate.container(),
                    candidate.container(), LocalDateTime.now(), index, candidate.documentType(), candidate.sourceType(),
                    candidate.searchKeywords(), candidate.searchedAt(), "SEARCH_SNIPPET",
                    formatter.formatIncomplete(candidate) ? "Missing optional GB/T fields: " + missingFields : "Public metadata verified",
                    formatter.formatIncomplete(candidate), missingFields, "[]", evidenceJson, evidenceJson, id);
        } catch (Exception ignored) {
            try {
                jdbcTemplate.update("UPDATE writing_reference SET final_number=? WHERE id=?", index, id);
            } catch (Exception ignoredAgain) {
            }
        }
    }

    private void insertReferenceEvidence(String projectId, String referenceId, ReferenceCandidate candidate) {
        try {
            jdbcTemplate.update("""
                    INSERT INTO writing_reference_source_evidence (id, reference_id, project_id, provider, source_type,
                    source_title, source_url, source_domain, source_snippet, query_text, retrieved_at, created_at)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """, WritingJdbc.id("wrse"), referenceId, projectId, providerOf(candidate), candidate.sourceType(),
                    candidate.sourceTitle(), candidate.url(), sourceDomain(candidate.url()), candidate.sourceSnippet(),
                    candidate.searchKeywords(), candidate.searchedAt(), LocalDateTime.now());
        } catch (Exception ignored) {
        }
    }

    private String sourceEvidenceJson(ReferenceCandidate candidate) {
        try {
            return objectMapper.writeValueAsString(List.of(Map.of(
                    "sourceUrl", blank(candidate.url()),
                    "sourceTitle", blank(candidate.sourceTitle()),
                    "sourceDomain", sourceDomain(candidate.url()),
                    "sourceSnippet", blank(candidate.sourceSnippet()),
                    "retrievedAt", String.valueOf(candidate.searchedAt()),
                    "queryText", blank(candidate.searchKeywords()),
                    "provider", providerOf(candidate),
                    "sourceType", blank(candidate.sourceType())
            )));
        } catch (Exception ignored) {
            return "[]";
        }
    }

    private String missingFieldsJson(ReferenceCandidate candidate) {
        List<String> fields = new ArrayList<>();
        if (blank(candidate.title()).isBlank()) fields.add("title");
        if (candidate.authors() == null || candidate.authors().isEmpty()) fields.add("authors");
        if (candidate.year() == null) fields.add("publicationYear");
        if (blank(candidate.container()).isBlank()) fields.add("journalOrPublisher");
        if (blank(candidate.url()).isBlank()) fields.add("url");
        if ("JOURNAL".equalsIgnoreCase(candidate.documentType()) && blank(candidate.volume()).isBlank()) fields.add("volume");
        if ("JOURNAL".equalsIgnoreCase(candidate.documentType()) && blank(candidate.issue()).isBlank()) fields.add("issue");
        if ("JOURNAL".equalsIgnoreCase(candidate.documentType()) && blank(candidate.pages()).isBlank()) fields.add("pages");
        try {
            return objectMapper.writeValueAsString(fields);
        } catch (Exception ignored) {
            return "[]";
        }
    }

    private boolean hasFormalFields(Map<String, Object> row) {
        return !WritingJdbc.text(row.get("title")).isBlank()
                && !WritingJdbc.text(row.get("authors")).isBlank()
                && WritingJdbc.integer(row.get("publication_year"), 0) > 1900
                && !WritingJdbc.text(row.get("journal_or_publisher")).isBlank()
                && !WritingJdbc.text(row.get("url")).isBlank();
    }

    private String sourceDomain(String url) {
        try {
            String host = java.net.URI.create(url).getHost();
            return host == null ? "" : host;
        } catch (Exception ignored) {
            return "";
        }
    }

    private String blank(String value) {
        return value == null ? "" : value;
    }

    private String providerOf(ReferenceCandidate candidate) {
        String source = candidate.sourcePlatform() == null ? "" : candidate.sourcePlatform().toUpperCase(Locale.ROOT).replace('-', '_');
        if (source.contains("DOUBAO")) return "DOUBAO_WEB_SEARCH";
        if (source.contains("OPENALEX")) return "OPENALEX";
        if (source.contains("CROSSREF")) return "CROSSREF";
        return source.isBlank() ? "MANUAL" : source;
    }

    private String languageOf(ReferenceCandidate candidate) {
        String metadataLanguage = candidate.language() == null ? "" : candidate.language().trim().toLowerCase(Locale.ROOT);
        if (metadataLanguage.equals("zh") || metadataLanguage.startsWith("zh-") || metadataLanguage.contains("chinese")) return "ZH";
        if (metadataLanguage.equals("en") || metadataLanguage.startsWith("en-") || metadataLanguage.contains("english")) return "EN";
        if (!metadataLanguage.isBlank()) return "OTHER";
        String title = candidate.title() == null ? "" : candidate.title();
        if (title.codePoints().anyMatch(code -> Character.UnicodeScript.of(code) == Character.UnicodeScript.HAN)) return "ZH";
        return "EN";
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private String activeProviderNames() {
        return String.join(",", properties.getReferenceSearch().providerOrder());
    }
}
