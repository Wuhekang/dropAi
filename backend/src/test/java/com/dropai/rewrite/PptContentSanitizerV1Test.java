package com.dropai.rewrite;

import com.dropai.rewrite.service.ppt.PptContentPlannerV2;
import com.dropai.rewrite.service.ppt.PptContentSanitizerV1;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class PptContentSanitizerV1Test {
    private final PptContentSanitizerV1 sanitizer=new PptContentSanitizerV1();
    @Test void repairsTruncatedPointsGenericImageTitlesAndDatabaseFieldDetails(){
        var text=new PptContentPlannerV2.CandidatePage("项目背景与价值","BACKGROUND","为什么开展项目？",List.of("1 研究背景及意义","本文使用Spring Boot模式并依靠Sp"),"目前的健康管理方法在数据整理、快速查找和分析程度上还有不足来提","第1章",.9);
        var image=new PptContentPlannerV2.CandidatePage("IMAGE_EVIDENCE","系统证据图 4-10","RESULT","该图说明什么？",List.of("趋势分析"),"该图展示健康数据可视化页面。","第4章",.95,"PROOF",new PptContentPlannerV2.SourceRefs("chapter_4",List.of(),"figure_4_10",List.of(),List.of()),true,List.of());
        var table=new PptContentPlannerV2.CandidatePage("TABLE_SUMMARY","核心数据库表设计","DATABASE","数据库有哪些业务表？",List.of("用户表"),"该表汇总数据库业务对象。","第3章",.9,"",new PptContentPlannerV2.SourceRefs("chapter_3",List.of(),"",List.of(),List.of("table_3_1")),false,List.of(List.of("deleted","0/1字段标识")));
        var input=new PptContentPlannerV2.PlannerResult("computer",List.of(new PptContentPlannerV2.ChapterCandidates("测试",List.of(text,image,table))),List.of());
        var pages=sanitizer.sanitize(input).chapters().get(0).candidatePages();
        assertEquals("健康趋势可视化分析",pages.get(1).title());
        assertTrue(pages.get(0).keyPoints().stream().noneMatch(p->p.matches(".*Sp$")||p.matches("^\\d+.*")));
        assertTrue(pages.get(0).description().endsWith("。"));
        assertEquals(List.of("user","用户账户管理"),pages.get(2).tableSummary().get(0));
        assertTrue(pages.get(2).tableSummary().stream().flatMap(List::stream).noneMatch(c->c.contains("字段")||c.contains("deleted")));
    }
}
