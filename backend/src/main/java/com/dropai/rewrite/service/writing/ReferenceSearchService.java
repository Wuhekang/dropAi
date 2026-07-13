package com.dropai.rewrite.service.writing;

import com.dropai.rewrite.config.WritingGenerationProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class ReferenceSearchService {
    private final JdbcTemplate jdbcTemplate;
    private final WritingGenerationProperties properties;
    private final Map<String, ReferenceSearchProvider> providers = new HashMap<>();
    private final ObjectMapper objectMapper;
    private final ChineseReferenceSearchPlanService searchPlanService;
    private final GbT7714Formatter formatter;

    public ReferenceSearchService(JdbcTemplate jdbcTemplate,
                                  WritingGenerationProperties properties,
                                  List<ReferenceSearchProvider> providers,
                                  ObjectMapper objectMapper,
                                  ChineseReferenceSearchPlanService searchPlanService,
                                  GbT7714Formatter formatter) {
        this.jdbcTemplate = jdbcTemplate;
        this.properties = properties;
        providers.forEach(provider -> {
            this.providers.put(provider.name().toLowerCase(Locale.ROOT), provider);
            this.providers.put(provider.providerCode().toLowerCase(Locale.ROOT), provider);
        });
        this.objectMapper = objectMapper;
        this.searchPlanService = searchPlanService;
        this.formatter = formatter;
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
                "SELECT * FROM writing_reference WHERE project_id=? ORDER BY citation_number IS NULL, citation_number, relevance_score DESC, created_at", projectId);
    }

    public void deleteReference(Long userId, String projectId, String referenceId) {
        WritingJdbc.one(jdbcTemplate, "SELECT id FROM writing_project WHERE id=? AND user_id=?", projectId, userId);
        jdbcTemplate.update("DELETE FROM writing_reference WHERE project_id=? AND id=?", projectId, referenceId);
    }

    @Transactional
    public List<Map<String, Object>> deduplicateSavedReferences(Long userId, String projectId) {
        WritingJdbc.one(jdbcTemplate, "SELECT id FROM writing_project WHERE id=? AND user_id=?", projectId, userId);
        List<Map<String, Object>> rows = WritingJdbc.list(jdbcTemplate,
                "SELECT * FROM writing_reference WHERE project_id=? ORDER BY citation_number IS NULL, citation_number, relevance_score DESC, created_at",
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
                "SELECT * FROM writing_reference WHERE project_id=? ORDER BY citation_number IS NULL, citation_number, relevance_score DESC, created_at",
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
            for (int attempt = 1; attempt <= Math.max(1, properties.getReferenceSearch().getRetryCount()); attempt++) {
                long started = System.currentTimeMillis();
                try {
                    List<ReferenceCandidate> found = provider.search(query);
                    insertSearchLog(query.projectId(), provider.providerCode(), language, query.joinedKeywords(), found.size(), found.size(), System.currentTimeMillis() - started, true, "", "");
                    result.addAll(found);
                    if (result.size() >= query.maxResults()) return result;
                    break;
                } catch (RuntimeException exception) {
                    lastError = exception;
                    insertSearchLog(query.projectId(), provider.providerCode(), language, query.joinedKeywords(), 0, 0, System.currentTimeMillis() - started, false,
                            exception.getClass().getSimpleName(), String.valueOf(exception.getMessage()));
                    try {
                        Thread.sleep(800L * attempt);
                    } catch (InterruptedException interruptedException) {
                        Thread.currentThread().interrupt();
                        throw new IllegalStateException("参考文献搜索被中断", interruptedException);
                    }
                }
            }
        }
        if (result.isEmpty() && lastError != null) throw lastError;
        if (result.isEmpty()) throw new IllegalStateException("没有可用的联网参考文献搜索Provider，或搜索结果为空");
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
        Map<String, ReferenceCandidate> map = new LinkedHashMap<>();
        for (ReferenceCandidate candidate : candidates) {
            String key = candidate.doi() != null && !candidate.doi().isBlank()
                    ? "doi:" + candidate.doi().toLowerCase(Locale.ROOT)
                    : "title:" + normalize(candidate.title()) + ":" + candidate.year();
            ReferenceCandidate previous = map.get(key);
            if (previous == null || candidate.relevanceScore() > previous.relevanceScore()) map.put(key, candidate);
        }
        return new ArrayList<>(map.values());
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
                "SELECT id FROM writing_reference WHERE project_id=? ORDER BY citation_number IS NULL, citation_number, relevance_score DESC, created_at",
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
