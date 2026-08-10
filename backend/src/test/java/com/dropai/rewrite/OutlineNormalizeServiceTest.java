package com.dropai.rewrite;

import com.dropai.rewrite.service.writing.OutlineNormalizeService;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class OutlineNormalizeServiceTest {
    private final OutlineNormalizeService service = new OutlineNormalizeService();

    @Test
    void removesRepeatedChapterNumbering() {
        assertEquals("绪论", service.chapterTitle("第一章 第1章 绪论"));
        assertEquals("项目概况", service.chapterTitle("第二章 第2章 项目概况"));
    }

    @Test
    void removesRepeatedSectionNumbering() {
        assertEquals("课题背景", service.sectionTitle("1.1 1.1 课题背景"));
        assertEquals("基址分析", service.sectionTitle("2.2 2.2 基址分析"));
    }
}
