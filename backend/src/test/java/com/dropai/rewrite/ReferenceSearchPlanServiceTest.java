package com.dropai.rewrite;

import com.dropai.rewrite.service.writing.ChineseReferenceSearchPlanService;
import com.dropai.rewrite.service.writing.ReferenceSearchQuery;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReferenceSearchPlanServiceTest {
    @Test
    void buildsChineseQueriesWithSiteQueriesAndChapterQueries() {
        ReferenceSearchQuery query = new ReferenceSearchQuery("p1", "人工智能时代职业院校学生就业能力提升路径研究",
                "职业教育", List.of("人工智能", "就业能力"), List.of("就业能力现状", "提升路径"),
                2021, 2026, 20, 20, 0);
        Map<String, Object> plan = new ChineseReferenceSearchPlanService().buildPlan(query);
        assertFalse(((List<?>) plan.get("globalQueries")).isEmpty());
        assertFalse(((List<?>) plan.get("chapterQueries")).isEmpty());
        assertTrue(((List<?>) plan.get("siteQueries")).stream().anyMatch(value -> String.valueOf(value).contains("site:kns.cnki.net")));
    }
}
