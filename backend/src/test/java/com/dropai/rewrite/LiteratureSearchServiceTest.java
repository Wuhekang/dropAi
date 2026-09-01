package com.dropai.rewrite;

import com.dropai.rewrite.auth.AuthContext;
import com.dropai.rewrite.service.PointService;
import com.dropai.rewrite.service.writing.LiteratureSearchService;
import com.dropai.rewrite.service.writing.ReferenceSearchService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LiteratureSearchServiceTest {
    @Mock
    private ReferenceSearchService referenceSearchService;

    @Mock
    private PointService pointService;

    private LiteratureSearchService service;

    @BeforeEach
    void setUp() {
        AuthContext.setUserId(42L);
        service = new LiteratureSearchService(referenceSearchService, pointService);
    }

    @AfterEach
    void clearContext() {
        AuthContext.clear();
    }

    @Test
    void chargesOnlyAfterAllRequestedLanguageQuotasAndItemsAreComplete() {
        when(pointService.featureCostPoints(PointService.LITERATURE_SEARCH)).thenReturn(2);
        when(referenceSearchService.standaloneSearch("数字经济", 10, 5)).thenReturn(Map.of(
                "actualCount", 15,
                "actualChineseCount", 10,
                "actualEnglishCount", 5,
                "requestedCount", 15,
                "items", items(10, 5)));

        Map<String, Object> result = service.search(Map.of(
                "title", "数字经济",
                "chineseCount", 10,
                "englishCount", 5));

        assertEquals(30, result.get("costPoints"));
        assertEquals(2, result.get("unitCostPoints"));
        assertTrue((Boolean) result.get("charged"));
        verify(pointService).ensureEnoughCustom(42L, 30);
        verify(pointService).deductCustom(eq(42L), nullable(String.class), eq(PointService.LITERATURE_SEARCH),
                eq("文献搜索（每篇）"), eq(30), contains("完整返回 15 篇"));
    }

    @Test
    void doesNotDeductPointsWhenSearchFails() {
        when(pointService.featureCostPoints(PointService.LITERATURE_SEARCH)).thenReturn(1);
        when(referenceSearchService.standaloneSearch("数字经济", 10, 5))
                .thenThrow(new IllegalStateException("所有公开来源暂时不可用"));

        assertThrows(IllegalStateException.class, () -> service.search(Map.of(
                "title", "数字经济",
                "chineseCount", 10,
                "englishCount", 5)));

        verify(pointService, never()).deductCustom(anyLong(), nullable(String.class), anyString(), anyString(), anyInt(), anyString());
    }

    @Test
    void rejectsMismatchedItemLanguagesDefensivelyAndDoesNotDeductPoints() {
        when(pointService.featureCostPoints(PointService.LITERATURE_SEARCH)).thenReturn(1);
        when(referenceSearchService.standaloneSearch("数字经济", 10, 5)).thenReturn(Map.of(
                "actualCount", 15,
                "actualChineseCount", 10,
                "actualEnglishCount", 5,
                "requestedCount", 15,
                "items", items(9, 6)));

        IllegalStateException exception = assertThrows(IllegalStateException.class, () -> service.search(Map.of(
                "title", "数字经济",
                "chineseCount", 10,
                "englishCount", 5)));

        assertTrue(exception.getMessage().contains("不扣费"));
        verify(pointService).ensureEnoughCustom(42L, 15);
        verify(pointService, never()).deductCustom(anyLong(), nullable(String.class), anyString(), anyString(), anyInt(), anyString());
    }

    @Test
    void rejectsDuplicateItemsDefensivelyAndDoesNotDeductPoints() {
        when(pointService.featureCostPoints(PointService.LITERATURE_SEARCH)).thenReturn(1);
        java.util.ArrayList<Map<String, Object>> duplicatedItems = new java.util.ArrayList<>(items(10, 5));
        duplicatedItems.set(1, duplicatedItems.get(0));
        when(referenceSearchService.standaloneSearch("数字经济", 10, 5)).thenReturn(Map.of(
                "actualCount", 15,
                "actualChineseCount", 10,
                "actualEnglishCount", 5,
                "requestedCount", 15,
                "items", duplicatedItems));

        IllegalStateException exception = assertThrows(IllegalStateException.class, () -> service.search(Map.of(
                "title", "数字经济", "chineseCount", 10, "englishCount", 5)));

        assertTrue(exception.getMessage().contains("不扣费"));
        verify(pointService, never()).deductCustom(anyLong(), nullable(String.class), anyString(), anyString(), anyInt(), anyString());
    }

    @Test
    void allowsDifferentPapersToShareTheSamePublicPortalUrl() {
        when(pointService.featureCostPoints(PointService.LITERATURE_SEARCH)).thenReturn(1);
        List<Map<String, Object>> sharedPortalItems = items(10, 5).stream().map(item -> {
            Map<String, Object> copy = new java.util.LinkedHashMap<>(item);
            copy.put("url", "https://example.test/journal");
            return copy;
        }).toList();
        when(referenceSearchService.standaloneSearch("数字经济", 10, 5)).thenReturn(Map.of(
                "actualCount", 15,
                "actualChineseCount", 10,
                "actualEnglishCount", 5,
                "requestedCount", 15,
                "items", sharedPortalItems));

        Map<String, Object> result = service.search(Map.of(
                "title", "数字经济", "chineseCount", 10, "englishCount", 5));

        assertEquals(15, result.get("actualCount"));
        verify(pointService).deductCustom(eq(42L), nullable(String.class), eq(PointService.LITERATURE_SEARCH),
                eq("文献搜索（每篇）"), eq(15), contains("完整返回 15 篇"));
    }

    @Test
    void defaultsToTenChineseAndFiveEnglishReferences() {
        when(pointService.featureCostPoints(PointService.LITERATURE_SEARCH)).thenReturn(1);
        when(referenceSearchService.standaloneSearch("数字经济", 10, 5)).thenReturn(Map.of(
                "actualCount", 15, "actualChineseCount", 10, "actualEnglishCount", 5,
                "items", items(10, 5)));

        service.search(Map.of("title", "数字经济"));

        verify(referenceSearchService).standaloneSearch("数字经济", 10, 5);
        verify(pointService).ensureEnoughCustom(42L, 15);
    }

    @Test
    void rejectsRequestedTotalsBelowFifteen() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> service.search(Map.of(
                "title", "数字经济", "chineseCount", 9, "englishCount", 5)));

        assertTrue(exception.getMessage().contains("至少搜索 15 篇"));
        verify(referenceSearchService, never()).standaloneSearch(anyString(), anyInt(), anyInt());
        verify(pointService, never()).deductCustom(anyLong(), nullable(String.class), anyString(), anyString(), anyInt(), anyString());
    }

    private List<Map<String, Object>> items(int chineseCount, int englishCount) {
        java.util.ArrayList<Map<String, Object>> result = new java.util.ArrayList<>();
        java.util.stream.IntStream.range(0, chineseCount)
                .mapToObj(index -> Map.<String, Object>of(
                        "title", "中文 " + index, "year", 2025, "language", "ZH",
                        "url", "https://example.test/zh/" + index))
                .forEach(result::add);
        java.util.stream.IntStream.range(0, englishCount)
                .mapToObj(index -> Map.<String, Object>of(
                        "title", "English " + index, "year", 2025, "language", "EN",
                        "url", "https://example.test/en/" + index))
                .forEach(result::add);
        return result;
    }
}
