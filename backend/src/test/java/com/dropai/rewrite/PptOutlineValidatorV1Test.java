package com.dropai.rewrite;

import com.dropai.rewrite.service.ppt.PptContentPlannerV2;
import com.dropai.rewrite.service.ppt.PptOutlinePlannerV1;
import com.dropai.rewrite.service.ppt.PptOutlineValidatorV1;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class PptOutlineValidatorV1Test {
    private final PptOutlinePlannerV1 planner=new PptOutlinePlannerV1();
    private final PptOutlineValidatorV1 validator=new PptOutlineValidatorV1();

    @Test void wrapsContentTreeWithFixedPagesAndPassesQualityGate(){
        var outline=planner.plan(new PptOutlinePlannerV1.OutlineRequest(List.of(
                page("项目背景与价值","BACKGROUND","为什么开展项目？","第一章"),
                page("项目总结","SUMMARY","项目完成了什么？","第六章")),12));
        var result=validator.validate(new PptOutlineValidatorV1.ValidationRequest(Map.of("title","健康管理系统设计与实现","englishTitle","Health Management System","presenter","测试学生","major","软件工程","advisor","测试教师","studentNumber","20260001"),outline));
        assertTrue(result.valid(),result.issues().toString());
        assertEquals("FULL_PRESENTATION_TREE",result.treeType());
        assertEquals(List.of("COVER","AGENDA","SECTION","CONTENT","SECTION","SUMMARY","THANKS"),result.slideTree().stream().map(PptOutlineValidatorV1.FullSlideNode::pageType).toList());
        assertEquals("01 项目背景与需求",result.slideTree().get(2).title());
        assertEquals("02 总结展望",result.slideTree().get(4).title());
        assertFalse(result.slideTree().get(1).agendaItems().isEmpty());
        assertEquals(List.of(3,4),result.slideTree().get(1).agendaItems().get(0).pageRange());
        assertEquals(List.of(5,6),result.slideTree().get(1).agendaItems().get(1).pageRange());
        assertEquals("测试学生",result.slideTree().get(0).payload().presenter());
        assertEquals(List.of(1,2,3,4,5,6,7),result.slideTree().stream().map(PptOutlineValidatorV1.FullSlideNode::pageNumber).toList());
        assertDoesNotThrow(()->validator.requireValid(result));
    }

    @Test void rejectsMissingMetadataSummaryAndStandaloneTechnologyPage(){
        var outline=planner.plan(new PptOutlinePlannerV1.OutlineRequest(List.of(page("Spring Boot","METHOD","采用什么技术？","第一章")),12));
        var result=validator.validate(new PptOutlineValidatorV1.ValidationRequest(Map.of(),outline));
        assertFalse(result.valid());
        var codes=result.issues().stream().map(PptOutlineValidatorV1.ValidationIssue::code).toList();
        assertTrue(codes.containsAll(List.of("COVER_TITLE_MISSING","LOW_VALUE_TITLE","SUMMARY_MISSING")));
        assertThrows(IllegalStateException.class,()->validator.requireValid(result));
    }

    private PptContentPlannerV2.CandidatePage page(String title,String purpose,String question,String chapter){return new PptContentPlannerV2.CandidatePage(title,purpose,question,List.of("观点一","观点二","观点三"),"该页面具有明确的答辩目标和完整的内容说明，用于验证页面规划质量门禁。",chapter,.9);}
}
