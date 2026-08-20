package com.dropai.rewrite.service.ppt;

import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Quality gate and deterministic structural wrapper for a content-only outline. */
@Service
public class PptOutlineValidatorV1 {
    private static final Set<String> LOW_VALUE_TITLES=Set.of(
            "java","mysql","vue","springboot","java简介","mysql介绍","vue介绍","springboot介绍","开发环境","技术关键词");

    public ValidationResult validate(ValidationRequest request){
        List<ValidationIssue> issues=new ArrayList<>();
        if(request==null||request.outline()==null){
            return new ValidationResult(false,"FULL_PRESENTATION_TREE",List.of(),List.of(new ValidationIssue("OUTLINE_MISSING",0,"缺少内容页面树")));
        }
        if(!"CONTENT_TREE".equals(request.outline().treeType()))issues.add(new ValidationIssue("TREE_TYPE_INVALID",0,"OutlineValidator只接受CONTENT_TREE"));
        String title=metadataTitle(request.metadata());
        if(title.isBlank())issues.add(new ValidationIssue("COVER_TITLE_MISSING",1,"封面缺少论文题目"));
        List<FullSlideNode> fullTree=new ArrayList<>();
        fullTree.add(fixed(1,"COVER",title.isBlank()?"答辩汇报":title,"OPENING"));
        fullTree.add(fixed(2,"AGENDA","目录","OPENING"));
        int number=3;
        for(var slide:safe(request.outline().slideTree())){
            validateContentSlide(slide,number,issues);
            String pageType="SUMMARY".equals(slide.pagePurpose())?"SUMMARY":"CONTENT";
            fullTree.add(new FullSlideNode(number++,pageType,sectionFor(slide.pagePurpose()),slide.title(),slide.pagePurpose(),slide.answerQuestion(),slide.keyPoints(),slide.description(),slide.sourceChapter(),slide.score()));
        }
        fullTree.add(fixed(number,"THANKS","谢谢大家","CLOSING"));
        validateGlobal(fullTree,issues);
        return new ValidationResult(issues.isEmpty(),"FULL_PRESENTATION_TREE",fullTree,issues);
    }

    public void requireValid(ValidationResult result){if(result==null||!result.valid())throw new IllegalStateException("OutlineValidator质量门禁失败: "+(result==null?"无结果":result.issues()));}

    private void validateContentSlide(PptOutlinePlannerV1.SlideNode slide,int pageNumber,List<ValidationIssue> issues){
        if(slide==null){issues.add(new ValidationIssue("CONTENT_PAGE_NULL",pageNumber,"内容页为空"));return;}
        if(blank(slide.title()))issues.add(new ValidationIssue("TITLE_MISSING",pageNumber,"内容页缺少标题"));
        if(blank(slide.pagePurpose()))issues.add(new ValidationIssue("PURPOSE_MISSING",pageNumber,"内容页缺少pagePurpose"));
        if(blank(slide.answerQuestion()))issues.add(new ValidationIssue("ANSWER_QUESTION_MISSING",pageNumber,"内容页缺少answerQuestion"));
        if(slide.keyPoints()==null||slide.keyPoints().isEmpty())issues.add(new ValidationIssue("KEY_POINTS_MISSING",pageNumber,"内容页缺少核心观点"));
        if(LOW_VALUE_TITLES.contains(canonical(slide.title())))issues.add(new ValidationIssue("LOW_VALUE_TITLE",pageNumber,"禁止技术名词或关键词独立成页"));
    }

    private void validateGlobal(List<FullSlideNode> tree,List<ValidationIssue> issues){
        checkCount(tree,"COVER",1,issues);checkCount(tree,"AGENDA",1,issues);checkCount(tree,"THANKS",1,issues);
        if(tree.stream().noneMatch(p->"CONTENT".equals(p.pageType())))issues.add(new ValidationIssue("CONTENT_MISSING",0,"至少需要一个正文页"));
        if(tree.stream().noneMatch(p->"SUMMARY".equals(p.pageType())))issues.add(new ValidationIssue("SUMMARY_MISSING",0,"至少需要一个总结或展望页"));
        long sections=tree.stream().filter(p->"CONTENT".equals(p.pageType())||"SUMMARY".equals(p.pageType())).map(FullSlideNode::section).distinct().count();
        if(sections>5)issues.add(new ValidationIssue("SECTION_LIMIT_EXCEEDED",0,"一级目录不能超过5个"));
        for(int i=0;i<tree.size();i++)if(tree.get(i).pageNumber()!=i+1)issues.add(new ValidationIssue("PAGE_NUMBER_INVALID",tree.get(i).pageNumber(),"页面编号必须连续"));
    }

    private void checkCount(List<FullSlideNode> tree,String type,long expected,List<ValidationIssue> issues){long actual=tree.stream().filter(p->type.equals(p.pageType())).count();if(actual!=expected)issues.add(new ValidationIssue(type+"_COUNT_INVALID",0,type+"页面必须为"+expected+"页"));}
    private FullSlideNode fixed(int number,String type,String title,String section){return new FullSlideNode(number,type,section,title,"","",List.of(),"","",null);}
    private String sectionFor(String purpose){return switch(purpose==null?"":purpose){case "BACKGROUND","PROBLEM","METHOD"->"PROJECT_OVERVIEW";case "DESIGN","DATABASE"->"SYSTEM_DESIGN";case "IMPLEMENTATION","RESULT"->"SYSTEM_IMPLEMENTATION";case "TEST"->"TEST_VALIDATION";case "SUMMARY"->"CONCLUSION";default->"OTHER";};}
    private String metadataTitle(Map<String,String> metadata){if(metadata==null)return"";for(String key:List.of("title","题目","课题名称")){String value=metadata.get(key);if(value!=null&&!value.isBlank())return PptDocumentParser.clean(value);}return"";}
    private String canonical(String value){return value==null?"":value.replaceAll("[\\s：:，,。！？!?、._-]+","").toLowerCase(Locale.ROOT);}
    private <T> List<T> safe(List<T> value){return value==null?List.of():value;}
    private boolean blank(String value){return value==null||value.isBlank();}

    public record ValidationRequest(Map<String,String> metadata,PptOutlinePlannerV1.OutlineResult outline){}
    public record FullSlideNode(int pageNumber,String pageType,String section,String title,String pagePurpose,String answerQuestion,List<String> keyPoints,String description,String sourceChapter,PptOutlinePlannerV1.PageScore score){}
    public record ValidationIssue(String code,int pageNumber,String message){}
    public record ValidationResult(boolean valid,String treeType,List<FullSlideNode> slideTree,List<ValidationIssue> issues){}
}
