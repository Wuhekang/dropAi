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

    public ReferenceSearchService(JdbcTemplate jdbcTemplate,
                                  WritingGenerationProperties properties,
                                  List<ReferenceSearchProvider> providers,
                                  ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.properties = properties;
        providers.forEach(provider -> this.providers.put(provider.name().toLowerCase(Locale.ROOT), provider));
        this.objectMapper = objectMapper;
    }

    public Map<String, Object> status() {
        List<Map<String, Object>> providerStatus = properties.getReferenceSearch().providerOrder().stream()
                .map(name -> {
                    ReferenceSearchProvider provider = providers.get(name);
                    return Map.<String, Object>of(
                            "name", name,
                            "available", provider != null && provider.available()
                    );
                })
                .toList();
        return Map.of(
                "enabled", properties.getReferenceSearch().isEnabled(),
                "providerOrder", properties.getReferenceSearch().providerOrder(),
                "providers", providerStatus
        );
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
                Math.max(20, WritingJdbc.integer(project.get("reference_count"), properties.getReferenceSearch().getMaxResults())),
                WritingJdbc.integer(project.get("chinese_reference_count"), 8),
                WritingJdbc.integer(project.get("english_reference_count"), 12));

        jdbcTemplate.update("UPDATE writing_project SET search_status=?, search_message=?, updated_at=? WHERE id=?",
                "RUNNING", "正在联网检索参考文献", LocalDateTime.now(), projectId);
        List<ReferenceCandidate> candidates = searchOnline(query);
        List<ReferenceCandidate> deduped = dedupe(candidates).stream()
                .filter(ReferenceCandidate::basicallyVerified)
                .sorted(Comparator.comparingDouble(ReferenceCandidate::relevanceScore).reversed())
                .limit(query.maxResults())
                .toList();
        jdbcTemplate.update("DELETE FROM writing_reference WHERE project_id=?", projectId);
        int index = 1;
        for (ReferenceCandidate candidate : deduped) {
            insertReference(projectId, candidate, index++, chapterNo);
        }
        jdbcTemplate.update("UPDATE writing_project SET search_provider=?, search_status=?, search_message=?, updated_at=? WHERE id=?",
                activeProviderNames(), "SUCCESS", "已检索并验证 " + deduped.size() + " 条参考文献", LocalDateTime.now(), projectId);
        return references(userId, projectId);
    }

    public List<Map<String, Object>> references(Long userId, String projectId) {
        WritingJdbc.one(jdbcTemplate, "SELECT id FROM writing_project WHERE id=? AND user_id=?", projectId, userId);
        return WritingJdbc.list(jdbcTemplate,
                "SELECT * FROM writing_reference WHERE project_id=? ORDER BY relevance_score DESC, created_at", projectId);
    }

    public void deleteReference(Long userId, String projectId, String referenceId) {
        WritingJdbc.one(jdbcTemplate, "SELECT id FROM writing_project WHERE id=? AND user_id=?", projectId, userId);
        jdbcTemplate.update("DELETE FROM writing_reference WHERE project_id=? AND id=?", projectId, referenceId);
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
        List<ReferenceCandidate> result = new ArrayList<>();
        RuntimeException lastError = null;
        for (String providerName : properties.getReferenceSearch().providerOrder()) {
            ReferenceSearchProvider provider = providers.get(providerName);
            if (provider == null || !provider.available()) continue;
            for (int attempt = 1; attempt <= Math.max(1, properties.getReferenceSearch().getRetryCount()); attempt++) {
                try {
                    List<ReferenceCandidate> found = provider.search(query);
                    result.addAll(found);
                    if (result.size() >= query.maxResults()) return result;
                    break;
                } catch (RuntimeException exception) {
                    lastError = exception;
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

    private void insertReference(String projectId, ReferenceCandidate candidate, int index, Integer chapterNo) {
        LocalDateTime now = LocalDateTime.now();
        String id = WritingJdbc.id("ref");
        String authors = String.join("; ", candidate.authors());
        String formatted = "[" + index + "] " + authors + ". " + candidate.title() + ". " +
                (candidate.container() == null ? "" : candidate.container()) + ", " + candidate.year() +
                (candidate.doi() == null || candidate.doi().isBlank() ? "" : ". DOI: " + candidate.doi()) +
                (candidate.url() == null || candidate.url().isBlank() ? "" : ". " + candidate.url());
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
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private String activeProviderNames() {
        return String.join(",", properties.getReferenceSearch().providerOrder());
    }
}
