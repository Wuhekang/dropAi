package com.dropai.rewrite.service.writing;

import com.dropai.rewrite.auth.AuthContext;
import com.dropai.rewrite.service.PointService;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
public class LiteratureSearchService {
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
        int chineseCount = clamp(integer(request.get("chineseCount"), 5), 0, 20);
        int englishCount = clamp(integer(request.get("englishCount"), 5), 0, 20);
        int count = chineseCount + englishCount;
        if (count < 1) throw new IllegalArgumentException("中文和英文文献数量不能同时为 0");
        if (count > 20) throw new IllegalArgumentException("单次最多搜索 20 篇文献");
        int unitCost = pointService.featureCostPoints(PointService.LITERATURE_SEARCH);
        int cost = unitCost * count;
        pointService.ensureEnoughCustom(userId, cost);
        Map<String, Object> result = referenceSearchService.standaloneSearch(title, chineseCount, englishCount);
        pointService.deductCustom(userId, null, PointService.LITERATURE_SEARCH, "文献搜索（每篇）", cost,
                "文献中心搜索：" + title + "，中文 " + chineseCount + " 篇，英文 " + englishCount + " 篇");
        result.put("unitCostPoints", unitCost);
        result.put("costPoints", cost);
        result.put("charged", true);
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
}
