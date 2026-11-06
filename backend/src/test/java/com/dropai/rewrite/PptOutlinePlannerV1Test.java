package com.dropai.rewrite;

import com.dropai.rewrite.service.ppt.PptContentPlannerV2;
import com.dropai.rewrite.service.ppt.PptOutlinePlannerV1;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class PptOutlinePlannerV1Test {
    private final PptOutlinePlannerV1 planner=new PptOutlinePlannerV1();

    @Test void scoresMergesOrdersAndNumbersCandidatesWithoutCreatingContent(){
        var background=page("项目背景与价值","BACKGROUND","为什么开展项目？","第一章",.91);
        var architecture=page("系统总体架构","DESIGN","系统如何组织核心功能？","第三章",.93);
        var duplicate=page("系统总体架构","DESIGN","系统如何组织核心功能？","第三章",.88);
        var test=page("系统测试与验证","TEST","系统是否达到设计要求？","第五章",.90);
        var result=planner.plan(new PptOutlinePlannerV1.OutlineRequest(List.of(test,architecture,duplicate,background),12));
        assertEquals("CONTENT_TREE",result.treeType());
        assertEquals(3,result.contentSlideCount());
        assertEquals(List.of(1,2,3),result.slideTree().stream().map(PptOutlinePlannerV1.SlideNode::pageNumber).toList());
        assertEquals(List.of("BACKGROUND","DESIGN","TEST"),result.slideTree().stream().map(PptOutlinePlannerV1.SlideNode::pagePurpose).toList());
        assertEquals(2,result.slideTree().stream().filter(s->s.title().equals("系统总体架构")).findFirst().orElseThrow().mergedCandidateCount());
        assertTrue(result.decisions().stream().anyMatch(d->d.action().equals("MERGED")));
    }

    @Test void enforcesPageLimitAndNeverAddsCoverAssetsLayoutsOrRendererData(){
        var pages=List.of(page("背景","BACKGROUND","为什么？","第一章",.9),page("方法","METHOD","怎么做？","第二章",.9),page("设计","DESIGN","如何设计？","第三章",.9));
        var result=planner.plan(new PptOutlinePlannerV1.OutlineRequest(pages,2));
        assertEquals(2,result.contentSlideCount());
        assertTrue(result.decisions().stream().anyMatch(d->d.action().equals("DELETE_PAGE_LIMIT")));
        assertTrue(List.of(PptOutlinePlannerV1.class.getDeclaredMethods()).stream().noneMatch(m->m.getName().matches(".*(render|asset|layout|document).*")));
    }

    @Test void placesDatabaseBeforeImplementationAndPreservesSummaryOrder(){
        var pages=List.of(page("项目总结","SUMMARY","完成了什么？","第六章",.9),page("未来优化方向","SUMMARY","如何提升？","第六章",.9),page("功能实现","IMPLEMENTATION","如何实现？","第四章",.9),page("数据库设计","DATABASE","如何管理数据？","第三章",.9));
        var titles=planner.plan(new PptOutlinePlannerV1.OutlineRequest(pages,12)).slideTree().stream().map(PptOutlinePlannerV1.SlideNode::title).toList();
        assertEquals(List.of("数据库设计","功能实现","项目总结","未来优化方向"),titles);
    }

    @Test void mandatoryImagePagesBypassOrdinaryPageLimitAndRemainUnique(){
        var image1=new PptContentPlannerV2.CandidatePage("IMAGE_EVIDENCE","系统架构图","DESIGN","架构如何协同？",List.of("分层关系"),"该图展示系统架构图中的关键模块关系。","第三章",.95,"INFORMATION",new PptContentPlannerV2.SourceRefs("chapter_3",List.of(),"figure_3_1",List.of(),List.of()),true,List.of());
        var image2=new PptContentPlannerV2.CandidatePage("IMAGE_EVIDENCE","功能结构图","DESIGN","功能如何组织？",List.of("模块关系"),"该图展示功能结构图中的核心业务模块。","第三章",.95,"INFORMATION",new PptContentPlannerV2.SourceRefs("chapter_3",List.of(),"figure_3_2",List.of(),List.of()),true,List.of());
        var result=planner.plan(new PptOutlinePlannerV1.OutlineRequest(List.of(page("背景","BACKGROUND","为什么？","第一章",.9),image1,image2),1));
        assertEquals(1,result.ordinaryContentSlideCount());assertEquals(2,result.evidenceSlideCount());assertEquals(3,result.contentSlideCount());
        assertEquals(2,result.decisions().stream().filter(d->d.action().equals("KEEP_MANDATORY_ASSET")).count());
    }

    private PptContentPlannerV2.CandidatePage page(String title,String purpose,String question,String chapter,double confidence){
        return new PptContentPlannerV2.CandidatePage(title,purpose,question,List.of("核心观点一","核心观点二","核心观点三"),"该候选页面直接使用内容规划层已经提炼出的答辩内容，不重新读取或理解论文正文。",chapter,confidence);
    }
}
