package com.dropai.rewrite.service.writing;

import com.dropai.rewrite.config.WritingGenerationProperties;
import com.dropai.rewrite.service.AiRewriteService;
import com.fasterxml.jackson.databind.JsonNode;
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
    private final AiRewriteService aiRewriteService;

    public ReferenceSearchService(JdbcTemplate jdbcTemplate,
                                  WritingGenerationProperties properties,
                                  List<ReferenceSearchProvider> providers,
                                  ObjectMapper objectMapper,
                                  ChineseReferenceSearchPlanService searchPlanService,
                                  GbT7714Formatter formatter,
                                  AiRewriteService aiRewriteService) {
        this.jdbcTemplate = jdbcTemplate;
        this.properties = properties;
        providers.forEach(provider -> {
            this.providers.put(provider.name().toLowerCase(Locale.ROOT), provider);
            this.providers.put(provider.providerCode().toLowerCase(Locale.ROOT), provider);
        });
        this.objectMapper = objectMapper;
        this.searchPlanService = searchPlanService;
        this.formatter = formatter;
        this.aiRewriteService = aiRewriteService;
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

    public Map<String, Object> aiSearch(Long userId, String projectId, Map<String, Object> request) {
        Map<String, Object> project = WritingJdbc.one(jdbcTemplate,
                "SELECT * FROM writing_project WHERE id=? AND user_id=?", projectId, userId);
        int currentYear = java.time.Year.now().getValue();
        int yearFrom = Math.max(currentYear - 4, WritingJdbc.integer(request.get("yearFrom"), currentYear - 4));
        int yearTo = Math.min(currentYear, WritingJdbc.integer(request.get("yearTo"), currentYear));
        int chineseTarget = Math.max(0, WritingJdbc.integer(request.get("chineseReferenceCount"), 10));
        int englishTarget = Math.max(0, WritingJdbc.integer(request.get("englishReferenceCount"), 10));
        if (chineseTarget + englishTarget <= 0) {
            throw new IllegalArgumentException("中文和英文参考文献数量不能同时为 0");
        }
        jdbcTemplate.update("""
                UPDATE writing_project SET chinese_reference_count=?, english_reference_count=?, reference_count=?,
                year_start=?, year_end=?, search_status=?, search_message=?, updated_at=? WHERE id=?
                """, chineseTarget, englishTarget, chineseTarget + englishTarget, yearFrom, yearTo,
                "RUNNING", "AI正在分析主题并生成检索词", LocalDateTime.now(), projectId);
        Map<String, Object> plan = searchPlan(userId, projectId, Map.of(
                "targetCount", chineseTarget,
                "yearFrom", yearFrom,
                "yearTo", yearTo));
        List<Map<String, Object>> existing = references(userId, projectId);
        int searchChinese = Math.max(0, chineseTarget - (int) countQuotaReferences(existing, "ZH"));
        int searchEnglish = Math.max(0, englishTarget - (int) countQuotaReferences(existing, "EN"));
        List<Map<String, Object>> found = existing;
        if (searchChinese + searchEnglish > 0) {
            jdbcTemplate.update("UPDATE writing_project SET chinese_reference_count=?,english_reference_count=?,reference_count=? WHERE id=?",
                    searchChinese, searchEnglish, searchChinese + searchEnglish, projectId);
            try {
                found = searchAndSave(userId, projectId, null);
            } finally {
                jdbcTemplate.update("UPDATE writing_project SET chinese_reference_count=?,english_reference_count=?,reference_count=? WHERE id=?",
                        chineseTarget, englishTarget, chineseTarget + englishTarget, projectId);
            }
        }
        List<Map<String, Object>> verified = verifySavedReferences(userId, projectId);
        long chineseCount = countQuotaReferences(verified, "ZH");
        long englishCount = countQuotaReferences(verified, "EN");
        long verifiedCount = verified.stream()
                .filter(row -> WritingJdbc.text(row.get("verification_status")).toUpperCase(Locale.ROOT).startsWith("VERIFIED"))
                .count();
        int missingChinese = Math.max(0, chineseTarget - (int) chineseCount);
        int missingEnglish = Math.max(0, englishTarget - (int) englishCount);
        boolean quotaSatisfied = missingChinese == 0 && missingEnglish == 0;
        if (!quotaSatisfied) {
            jdbcTemplate.update("UPDATE writing_project SET search_status=?,search_message=?,updated_at=? WHERE id=?",
                    "INSUFFICIENT", quotaMessage(chineseTarget, englishTarget, chineseCount, englishCount), LocalDateTime.now(), projectId);
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("projectId", projectId);
        result.put("topic", WritingJdbc.text(project.get("title")));
        result.put("researchDirection", WritingJdbc.text(project.get("major")));
        result.put("yearFrom", yearFrom);
        result.put("yearTo", yearTo);
        result.put("searchPlan", plan);
        result.put("references", verified);
        result.put("totalCount", found.size());
        result.put("chineseCount", chineseCount);
        result.put("englishCount", englishCount);
        result.put("verifiedCount", verifiedCount);
        result.put("targetChineseCount", chineseTarget);
        result.put("targetEnglishCount", englishTarget);
        result.put("missingChineseCount", missingChinese);
        result.put("missingEnglishCount", missingEnglish);
        result.put("quotaSatisfied", quotaSatisfied);
        result.put("searchExhausted", !quotaSatisfied);
        result.put("searchMessage", quotaSatisfied ? "已达到中文和英文目标数量"
                : "已尝试全部可用搜索源和组合关键词，当前无法继续补充");
        return result;
    }

    public Map<String, Object> ensureQuota(Long userId, String projectId) {
        Map<String, Object> project = WritingJdbc.one(jdbcTemplate, "SELECT * FROM writing_project WHERE id=? AND user_id=?", projectId, userId);
        int targetChinese = WritingJdbc.integer(project.get("chinese_reference_count"), 0);
        int targetEnglish = WritingJdbc.integer(project.get("english_reference_count"), 0);
        Map<String, Object> counts = WritingJdbc.one(jdbcTemplate, """
                SELECT SUM(CASE WHEN language='ZH' AND (verification_status LIKE 'VERIFIED%' OR verification_status='MANUAL') THEN 1 ELSE 0 END) AS zh,
                SUM(CASE WHEN language='EN' AND (verification_status LIKE 'VERIFIED%' OR verification_status='MANUAL') THEN 1 ELSE 0 END) AS en
                FROM writing_reference WHERE project_id=?
                """, projectId);
        int currentChinese = WritingJdbc.integer(counts.get("zh"), 0);
        int currentEnglish = WritingJdbc.integer(counts.get("en"), 0);
        String mode = WritingJdbc.text(project.get("reference_mode")).toUpperCase(Locale.ROOT);
        if ("UPLOAD".equals(mode)) {
            int uploaded = WritingJdbc.integer(WritingJdbc.one(jdbcTemplate,
                    "SELECT COUNT(*) AS n FROM writing_reference WHERE project_id=? AND source_type IN ('UPLOAD','MANUAL')", projectId).get("n"), 0);
            if (uploaded <= 0) throw new IllegalStateException("请至少上传或手动添加一篇参考文献");
            return Map.of("targetChinese", 0, "targetEnglish", 0,
                    "currentChinese", currentChinese, "currentEnglish", currentEnglish, "satisfied", true, "mode", "UPLOAD");
        }
        if (currentChinese < targetChinese || currentEnglish < targetEnglish) {
            throw new IllegalStateException(quotaMessage(targetChinese, targetEnglish, currentChinese, currentEnglish));
        }
        return Map.of("targetChinese", targetChinese, "targetEnglish", targetEnglish,
                "currentChinese", currentChinese, "currentEnglish", currentEnglish, "satisfied", true);
    }

    private String quotaMessage(int targetChinese, int targetEnglish, long currentChinese, long currentEnglish) {
        return "参考文献数量不足。目标数量：中文" + targetChinese + "篇、英文" + targetEnglish
                + "篇；当前数量：中文" + currentChinese + "篇、英文" + currentEnglish
                + "篇；缺少数量：中文" + Math.max(0, targetChinese - currentChinese)
                + "篇、英文" + Math.max(0, targetEnglish - currentEnglish) + "篇。";
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
        jdbcTemplate.update("DELETE FROM writing_reference WHERE project_id=? AND COALESCE(source_type,'AI_SEARCH')='AI_SEARCH'", projectId);
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
        jdbcTemplate.update("DELETE FROM writing_reference WHERE project_id=? AND COALESCE(language,?)=? AND COALESCE(source_type,'AI_SEARCH')='AI_SEARCH'",
                projectId, language.toUpperCase(Locale.ROOT), language.toUpperCase(Locale.ROOT));
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
            if (!"AI_SEARCH".equalsIgnoreCase(WritingJdbc.text(row.get("source_type")))) continue;
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

    @Transactional
    public Map<String, Object> addManualReference(Long userId, String projectId, Map<String, Object> request) {
        Map<String, Object> project = WritingJdbc.one(jdbcTemplate,
                "SELECT * FROM writing_project WHERE id=? AND user_id=?", projectId, userId);
        Map<String, Object> fields = new LinkedHashMap<>(request == null ? Map.of() : request);
        String rawText = WritingJdbc.text(fields.get("rawText"));
        if (!rawText.isBlank()) fields.putAll(parseReferenceText(rawText));
        String language = normalizeLanguage(WritingJdbc.text(fields.get("language")), rawText + WritingJdbc.text(fields.get("title")));
        String title = WritingJdbc.text(fields.get("title"));
        List<String> authors = authorsOf(fields.get("authors"));
        int year = WritingJdbc.integer(fields.get("year"), 0);
        String source = WritingJdbc.text(fields.get("source"));
        String doi = WritingJdbc.text(fields.get("doi"));
        String url = WritingJdbc.text(fields.get("url"));
        if (title.isBlank() || authors.isEmpty() || year < 1900 || source.isBlank()) {
            throw new IllegalArgumentException("请补全文献标题、作者、年份和来源");
        }
        if (!WritingJdbc.list(jdbcTemplate,
                "SELECT id FROM writing_reference WHERE project_id=? AND (LOWER(title)=LOWER(?) OR (?<>'' AND LOWER(COALESCE(doi,''))=LOWER(?)))",
                projectId, title, doi, doi).isEmpty()) {
            throw new IllegalArgumentException("该文献已存在于当前文献库");
        }
        int next = WritingJdbc.integer(WritingJdbc.one(jdbcTemplate,
                "SELECT COALESCE(MAX(citation_number),0)+1 AS n FROM writing_reference WHERE project_id=?", projectId).get("n"), 1);
        LocalDateTime now = LocalDateTime.now();
        String id = WritingJdbc.id("ref");
        ReferenceCandidate candidate = new ReferenceCandidate(title, authors, year, source, "", "", "", doi, url,
                "MANUAL", "", rawText, now, List.of(), 1.0, "MANUAL", "JOURNAL", language,
                "MANUAL", source, rawText);
        String formatted = formatter.format(next, candidate, "GBT_7714_2025");
        jdbcTemplate.update("""
                INSERT INTO writing_reference (id,project_id,reference_key,title,authors,publication_year,
                journal_or_publisher,doi,url,source_platform,abstract_text,search_keywords,searched_at,
                applicable_chapters,verification_status,relevance_score,formatted_text,final_number,created_at,updated_at)
                VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
                """, id, projectId, "ref_" + String.format("%03d", next), title, String.join("; ", authors), year,
                source, blankToNull(doi), blankToNull(url), "MANUAL", "", "", now, "", "MANUAL", 1.0,
                formatted, next, now, now);
        jdbcTemplate.update("""
                UPDATE writing_reference SET language=?,source_type='MANUAL',provider='MANUAL',citation_number=?,
                journal=?,publisher=?,verified_at=?,verification_message=?,document_type='JOURNAL' WHERE id=?
                """, language, next, source, source, now, "用户手动添加", id);
        renumber(projectId);
        List<Map<String, Object>> all = references(userId, projectId);
        int targetChinese = WritingJdbc.integer(project.get("chinese_reference_count"), 0);
        int targetEnglish = WritingJdbc.integer(project.get("english_reference_count"), 0);
        long currentChinese = countQuotaReferences(all, "ZH");
        long currentEnglish = countQuotaReferences(all, "EN");
        return Map.of(
                "reference", WritingJdbc.one(jdbcTemplate, "SELECT * FROM writing_reference WHERE id=?", id),
                "references", all,
                "targetChineseCount", targetChinese,
                "targetEnglishCount", targetEnglish,
                "chineseCount", currentChinese,
                "englishCount", currentEnglish,
                "missingChineseCount", Math.max(0, targetChinese - (int) currentChinese),
                "missingEnglishCount", Math.max(0, targetEnglish - (int) currentEnglish),
                "quotaSatisfied", currentChinese >= targetChinese && currentEnglish >= targetEnglish
        );
    }

    private Map<String, Object> parseReferenceText(String rawText) {
        try {
            String response = aiRewriteService.rewrite("""
                    请解析下面的完整参考文献，只输出一个JSON对象，不要Markdown代码块。
                    字段：language（ZH或EN）、title、authors（字符串数组）、year（整数）、source、doi、url。
                    不得补造原文没有的信息，缺失字段使用空字符串或空数组。
                    参考文献：%s
                    """.formatted(rawText), "REFERENCE_PARSE").trim();
            int start = response.indexOf('{');
            int end = response.lastIndexOf('}');
            if (start < 0 || end <= start) throw new IllegalArgumentException("AI未返回可解析的文献字段");
            JsonNode node = objectMapper.readTree(response.substring(start, end + 1));
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("language", node.path("language").asText(""));
            result.put("title", node.path("title").asText(""));
            List<String> authors = new ArrayList<>();
            node.path("authors").forEach(author -> authors.add(author.asText()));
            result.put("authors", authors);
            result.put("year", node.path("year").asInt(0));
            result.put("source", node.path("source").asText(""));
            result.put("doi", node.path("doi").asText(""));
            result.put("url", node.path("url").asText(""));
            return result;
        } catch (IllegalArgumentException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IllegalStateException("参考文献文本解析失败：" + exception.getMessage(), exception);
        }
    }

    private List<String> authorsOf(Object value) {
        if (value instanceof List<?> list) return list.stream().map(WritingJdbc::text).filter(text -> !text.isBlank()).toList();
        String text = WritingJdbc.text(value);
        if (text.isBlank()) return List.of();
        return List.of(text.split("\\s*[;；,，、]\\s*"));
    }

    private String normalizeLanguage(String value, String sample) {
        if ("ZH".equalsIgnoreCase(value) || "中文".equals(value)) return "ZH";
        if ("EN".equalsIgnoreCase(value) || "英文".equals(value)) return "EN";
        return sample != null && sample.matches(".*[\\p{IsHan}].*") ? "ZH" : "EN";
    }

    private long countQuotaReferences(List<Map<String, Object>> rows, String language) {
        return rows.stream().filter(row -> language.equalsIgnoreCase(WritingJdbc.text(row.get("language"))))
                .filter(row -> {
                    String status = WritingJdbc.text(row.get("verification_status")).toUpperCase(Locale.ROOT);
                    return status.startsWith("VERIFIED") || "MANUAL".equals(status);
                }).count();
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
        List<ReferenceCandidate> result = new ArrayList<>();
        result.addAll(searchOnline(query, "ZH"));
        result.addAll(searchOnline(query, "EN"));
        if (result.isEmpty()) throw new IllegalStateException("所有联网参考文献搜索源均已耗尽，未获得可用结果");
        return result;
    }

    private List<ReferenceCandidate> searchOnline(ReferenceSearchQuery query, String language) {
        int target = "ZH".equalsIgnoreCase(language) ? query.chineseTarget()
                : "EN".equalsIgnoreCase(language) ? query.englishTarget() : query.maxResults();
        if (target <= 0) return List.of();
        Map<String, ReferenceCandidate> accepted = new LinkedHashMap<>();
        RuntimeException lastError = null;
        List<String> variants = searchVariants(query, language);
        for (String variant : variants) {
            for (String providerName : properties.getReferenceSearch().providerOrder()) {
                ReferenceSearchProvider provider = providers.get(providerName);
                if (provider == null || !provider.available() || !provider.supportsLanguage(language)) continue;
                ReferenceSearchQuery roundQuery = queryForRound(query, language, variant, target - accepted.size());
                for (int attempt = 1; attempt <= Math.max(1, properties.getReferenceSearch().getRetryCount()); attempt++) {
                    long started = System.currentTimeMillis();
                    try {
                        List<ReferenceCandidate> found = provider.search(roundQuery);
                        int before = accepted.size();
                        for (ReferenceCandidate candidate : found) {
                            if (candidate == null || !candidate.basicallyVerified() || !language.equalsIgnoreCase(languageOf(candidate))) continue;
                            String key = candidate.doi() != null && !candidate.doi().isBlank()
                                    ? "doi:" + candidate.doi().toLowerCase(Locale.ROOT)
                                    : "title:" + normalize(candidate.title()) + ":" + candidate.year();
                            accepted.putIfAbsent(key, candidate);
                        }
                        insertSearchLog(query.projectId(), provider.providerCode(), language, variant, found.size(),
                                accepted.size() - before, System.currentTimeMillis() - started, true, "", "");
                        break;
                    } catch (RuntimeException exception) {
                        lastError = exception;
                        insertSearchLog(query.projectId(), provider.providerCode(), language, variant, 0, 0,
                                System.currentTimeMillis() - started, false, exception.getClass().getSimpleName(), String.valueOf(exception.getMessage()));
                        if (attempt < Math.max(1, properties.getReferenceSearch().getRetryCount())) try {
                            Thread.sleep(800L * attempt);
                        } catch (InterruptedException interruptedException) {
                            Thread.currentThread().interrupt();
                            throw new IllegalStateException("参考文献搜索被中断", interruptedException);
                        }
                    }
                }
                if (accepted.size() >= target) return new ArrayList<>(accepted.values()).subList(0, target);
            }
        }
        if (accepted.isEmpty() && lastError != null) throw lastError;
        return new ArrayList<>(accepted.values());
    }

    private ReferenceSearchQuery queryForRound(ReferenceSearchQuery query, String language, String variant, int remaining) {
        List<String> keywords = new ArrayList<>(query.keywords() == null ? List.of() : query.keywords());
        keywords.add(variant);
        return new ReferenceSearchQuery(query.projectId(), query.title(), query.major(), keywords, query.chapterTitles(),
                query.yearStart(), query.yearEnd(), Math.max(1, remaining),
                "ZH".equalsIgnoreCase(language) ? Math.max(1, remaining) : 0,
                "EN".equalsIgnoreCase(language) ? Math.max(1, remaining) : 0);
    }

    private List<String> searchVariants(ReferenceSearchQuery query, String language) {
        String title = WritingJdbc.text(query.title());
        if ("ZH".equalsIgnoreCase(language)) return List.of(
                title + " 核心概念", title + " 理论基础", title + " 研究现状", title + " 影响因素", title + " 实证研究",
                title + " 问题分析", title + " 对策路径", title + " 评价体系", title + " 应用研究", title + " 案例研究",
                title + " 国内研究", title + " 比较研究", title + " 机制研究", title + " 模型构建", title + " 实践探索",
                title + " 发展趋势", title + " 技术应用", title + " 教育改革", title + " 协同机制", title + " 高质量发展");
        return List.of(
                title + " core concepts English literature", title + " theoretical framework", title + " research status", title + " influencing factors", title + " empirical study",
                title + " problem analysis", title + " improvement pathways", title + " evaluation framework", title + " applied research", title + " case study",
                title + " international research", title + " comparative study", title + " mechanism study", title + " model construction", title + " practical exploration",
                title + " development trends", title + " technology adoption", title + " educational reform", title + " collaborative mechanism", title + " high quality development");
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
                    journal=?, publisher=?, verified_at=?, final_number=?, document_type=?, source_database=?, source_type='AI_SEARCH',
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
