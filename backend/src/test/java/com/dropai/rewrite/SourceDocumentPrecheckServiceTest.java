package com.dropai.rewrite;

import com.dropai.rewrite.service.ppt.PptDocumentParser;
import com.dropai.rewrite.service.ppt.SourceDocumentPrecheckService;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class SourceDocumentPrecheckServiceTest {
    private final SourceDocumentPrecheckService service=new SourceDocumentPrecheckService();
    @Test void rejectsOnePageAdministrativeForm(){
        var doc=new PptDocumentParser.ParsedDocument("开题答辩记录表",List.of("学生信息","综合评价"),List.of("学院 专业 学号 指导教师 日期"),List.of(),0,25);
        var error=assertThrows(SourceDocumentPrecheckService.InsufficientSourceException.class,()->service.require(doc));
        assertEquals("SOURCE_CONTENT_INSUFFICIENT",error.report().code());
    }
    @Test void acceptsCompleteSourceWithTwoBodyChapters(){
        String body="本章节包含连续完整的研究正文，用于说明研究目标、实施方法、系统过程以及可以追溯的分析结果。";
        var doc=new PptDocumentParser.ParsedDocument("完整项目报告",List.of("系统分析","系统设计"),List.of(body.repeat(3),body.repeat(3)),List.of(),1,body.length()*6);
        assertTrue(service.require(doc).passed());
    }
}
