package com.dropai.rewrite;

import com.dropai.rewrite.service.writing.GbT7714Formatter;
import com.dropai.rewrite.service.writing.ReferenceCandidate;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GbT7714FormatterTest {
    @Test
    void formatsJournalReferenceWithoutInventingMissingFields() {
        ReferenceCandidate candidate = new ReferenceCandidate("人工智能时代职业教育就业能力研究",
                List.of("张三", "李四"), 2025, "职业教育研究", "12", "3", "45-51",
                "", "https://example.edu.cn/article", "DOUBAO_WEB_SEARCH", "公开摘要片段",
                "人工智能 职业教育", LocalDateTime.now(), List.of(), 0.9,
                "VERIFIED_PRIMARY_PUBLIC", "JOURNAL", "zh", "JOURNAL_OFFICIAL", "期刊页面", "片段");
        GbT7714Formatter formatter = new GbT7714Formatter();
        String formatted = formatter.format(1, candidate, "GBT_7714_2025");
        assertTrue(formatted.startsWith("[1] 张三, 李四."));
        assertTrue(formatted.contains("[J]. 职业教育研究, 2025, 12(3):45-51."));
        assertFalse(formatter.formatIncomplete(candidate));
    }
}
