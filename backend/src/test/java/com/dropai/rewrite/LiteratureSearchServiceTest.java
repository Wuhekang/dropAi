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
    void chargesOnlyForReferencesActuallyReturned() {
        when(pointService.featureCostPoints(PointService.LITERATURE_SEARCH)).thenReturn(2);
        when(referenceSearchService.standaloneSearch("数字经济", 6, 4)).thenReturn(Map.of(
                "actualCount", 3,
                "requestedCount", 10,
                "items", List.of(Map.of("title", "A"), Map.of("title", "B"), Map.of("title", "C"))));

        Map<String, Object> result = service.search(Map.of(
                "title", "数字经济",
                "chineseCount", 6,
                "englishCount", 4));

        assertEquals(6, result.get("costPoints"));
        assertTrue((Boolean) result.get("charged"));
        verify(pointService).ensureEnoughCustom(42L, 20);
        verify(pointService).deductCustom(eq(42L), nullable(String.class), eq(PointService.LITERATURE_SEARCH),
                eq("文献搜索（每篇）"), eq(6), contains("实际返回 3 篇"));
    }

    @Test
    void doesNotDeductPointsWhenSearchFails() {
        when(pointService.featureCostPoints(PointService.LITERATURE_SEARCH)).thenReturn(1);
        when(referenceSearchService.standaloneSearch("数字经济", 5, 5))
                .thenThrow(new IllegalStateException("所有公开来源暂时不可用"));

        assertThrows(IllegalStateException.class, () -> service.search(Map.of(
                "title", "数字经济",
                "chineseCount", 5,
                "englishCount", 5)));

        verify(pointService, never()).deductCustom(anyLong(), nullable(String.class), anyString(), anyString(), anyInt(), anyString());
    }

    @Test
    void doesNotDeductPointsWhenNoReferenceIsReturned() {
        when(pointService.featureCostPoints(PointService.LITERATURE_SEARCH)).thenReturn(1);
        when(referenceSearchService.standaloneSearch("数字经济", 5, 5)).thenReturn(Map.of(
                "actualCount", 0,
                "requestedCount", 10,
                "items", List.of()));

        Map<String, Object> result = service.search(Map.of(
                "title", "数字经济",
                "chineseCount", 5,
                "englishCount", 5));

        assertEquals(0, result.get("costPoints"));
        assertEquals(false, result.get("charged"));
        verify(pointService).ensureEnoughCustom(42L, 10);
        verify(pointService, never()).deductCustom(anyLong(), nullable(String.class), anyString(), anyString(), anyInt(), anyString());
    }
}
