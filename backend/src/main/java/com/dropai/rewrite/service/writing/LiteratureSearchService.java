package com.dropai.rewrite.service.writing;

import com.dropai.rewrite.auth.AuthContext;
import com.dropai.rewrite.service.PointService;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Service
public class LiteratureSearchService {
    private static final int DEFAULT_CHINESE_COUNT = 10;
    private static final int DEFAULT_ENGLISH_COUNT = 5;
    private static final int MINIMUM_TOTAL_COUNT = 15;
    private final ReferenceSearchService referenceSearchService;
    private final PointService pointService;

    public LiteratureSearchService(ReferenceSearchService referenceSearchService, PointService pointService) {
        this.referenceSearchService = referenceSearchService;
        this.pointService = pointService;
    }

    public Map<String, Object> search(Map<String, Object> request) {
        Long userId = AuthContext.requireUserId();
        String title = text(request.get("title"));
        if (title.isBlank()) throw new IllegalArgumentException("请输入题目名称");
        int chineseCount = clamp(integer(request.get("chineseCount"), DEFAULT_CHINESE_COUNT), 0, 20);
        int englishCount = clamp(integer(request.get("englishCount"), DEFAULT_ENGLISH_COUNT), 0, 20);
        int count = chineseCount + englishCount;
        if (count < MINIMUM_TOTAL_COUNT) throw new IllegalArgumentException("单次至少搜索 15 篇文献");
        if (count > 20) throw new IllegalArgumentException("单次最多搜索 20 篇文献");
        int unitCost = pointService.featureCostPoints(PointService.LITERATURE_SEARCH);
        int maximumCost = unitCost * count;
        pointService.ensureEnoughCustom(userId, maximumCost);
        Map<String, Object> result = new LinkedHashMap<>(
                referenceSearchService.standaloneSearch(title, chineseCount, englishCount));
        int actualCount = integer(result.get("actualCount"), -1);
        int actualChineseCount = integer(result.get("actualChineseCount"), -1);
        int actualEnglishCount = integer(result.get("actualEnglishCount"), -1);
        java.util.List<?> items = result.get("items") instanceof java.util.List<?> values ? values : java.util.List.of();
        int itemChineseCount = 0;
        int itemEnglishCount = 0;
        boolean itemMetadataComplete = true;
        boolean duplicateItem = false;
        Set<String> itemDois = new HashSet<>();
        Set<String> itemTitleYears = new HashSet<>();
        for (Object item : items) {
            if (!(item instanceof Map<?, ?> map)) {
                itemMetadataComplete = false;
                continue;
            }
            String language = text(map.get("language")).toUpperCase(Locale.ROOT);
            if ("ZH".equals(language)) itemChineseCount++;
            if ("EN".equals(language)) itemEnglishCount++;
            String url = text(map.get("url"));
            String doi = normalizeDoi(text(map.get("doi")));
            String titleYear = normalizeTitle(text(map.get("title"))) + "|" + text(map.get("year"));
            if (!publicUrl(url) || titleYear.startsWith("|") || titleYear.endsWith("|")) itemMetadataComplete = false;
            if (!doi.isBlank() && !itemDois.add(doi)) duplicateItem = true;
            if (!itemTitleYears.add(titleYear)) duplicateItem = true;
        }
        if (actualCount != count || actualChineseCount != chineseCount
                || actualEnglishCount != englishCount || items.size() != count
                || itemChineseCount != chineseCount || itemEnglishCount != englishCount
                || !itemMetadataComplete || duplicateItem) {
            throw new IllegalStateException("文献数量未完整达到目标，本次不返回结果且不扣费");
        }
        int cost = unitCost * count;
        if (cost > 0) {
            pointService.deductCustom(userId, null, PointService.LITERATURE_SEARCH, "文献搜索（每篇）", cost,
                    "文献中心搜索：" + title + "，完整返回 " + actualCount + " 篇");
        }
        result.put("unitCostPoints", unitCost);
        result.put("costPoints", cost);
        result.put("charged", cost > 0);
        return result;
    }

    private int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private int integer(Object value, int fallback) {
        if (value instanceof Number number) return number.intValue();
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private String text(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    private String normalizeDoi(String doi) {
        return doi.toLowerCase(Locale.ROOT)
                .replaceFirst("^https?://(dx\\.)?doi\\.org/", "")
                .replaceFirst("^doi\\s*:\\s*", "");
    }

    private String normalizeTitle(String title) {
        return title.toLowerCase(Locale.ROOT).replaceAll("[^\\p{IsHan}a-z0-9]+", "");
    }

    private boolean publicUrl(String value) {
        try {
            java.net.URI uri = java.net.URI.create(value);
            String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
            return ("http".equals(scheme) || "https".equals(scheme))
                    && uri.getHost() != null && !uri.getHost().isBlank();
        } catch (RuntimeException ignored) {
            return false;
        }
    }
}
