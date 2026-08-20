package com.dropai.rewrite.service.ppt;

import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Service
public class PptContentPlanner {
    private static final List<String> FORM_MARKERS=List.of("毕业设计任务书","开题报告","答辩记录","综合评价","教师信息表","答辩成员","课题来源","指导教师","教师职称");
    private static final List<String> META_LABELS=List.of("题目","课题名称","学生","学生姓名","学号","专业","教师","指导教师","职称","学院","日期","课题来源","答辩成员");
    private static final List<String> KEYWORD_TITLES=List.of("关键词","关键字","技术关键词","关键词总结","开发环境","环境配置");

    public List<PagePlan> planTextPages(String thesisTitle,String sectionTitle,String sectionDescription,List<String> rawBlocks,int count){
        List<String> candidates=rawBlocks.stream().map(PptDocumentParser::clean)
                .filter(v->isContent(v,thesisTitle)).sorted(Comparator.comparingInt(v->-score(v,sectionTitle))).toList();
        List<PagePlan> pages=new ArrayList<>();Set<String> usedTitles=new LinkedHashSet<>();
        for(int i=0;i<Math.max(1,count);i++){
            String source=sourceAt(candidates,sectionDescription,i);
            String purpose=purpose(sectionTitle,source,i);
            String title=uniqueTitle(rewriteTitle(sectionTitle,source,i),usedTitles);
            List<String> points=keyPoints(source,sectionDescription);
            String description=description(points,purpose);
            PagePlan page=new PagePlan(purpose,title,points,description,source);
            pages.add(validateOrRebuild(page,sectionTitle,i));
        }
        return pages;
    }

    public PagePlan planImagePage(String sectionTitle,String caption){
        String clean=PptDocumentParser.clean(caption);String title=rewriteTitle(sectionTitle,clean,0);
        String description=clean.length()>=40?PptDocumentParser.shorten(clean,70):"该图展示"+title+"的核心结构与业务关系，用于说明系统方案在实际功能流程中的组织方式。";
        return new PagePlan("用原文图示说明"+sectionTitle,title,List.of("呈现关键结构","对应章节方案"),description,clean);
    }

    public boolean isContent(String value,String thesisTitle){
        String clean=PptDocumentParser.clean(value),compact=clean.replaceAll("\\s+","");if(clean.length()<12)return false;
        String normalizedTitle=PptDocumentParser.clean(thesisTitle).replaceAll("\\s+","");if(!normalizedTitle.isBlank()&&(compact.equals(normalizedTitle)||compact.contains(normalizedTitle)&&compact.length()<normalizedTitle.length()+20))return false;
        if(FORM_MARKERS.stream().anyMatch(compact::contains))return false;
        if(KEYWORD_TITLES.stream().anyMatch(k->compact.equals(k)||compact.matches("^(?:第?[一二三四五六七八九十0-9.．]+)?"+k+"[:：]?$")))return false;
        long labels=META_LABELS.stream().filter(k->compact.matches(".*(?:^|[：:])?"+k+"[：:].*")||compact.startsWith(k+"：")||compact.startsWith(k+":")).count();
        return labels<2&&!compact.matches("^(学院|专业|学号|学生姓名|指导教师|日期|课题来源)[：:].*");
    }

    public void requireValue(PagePlan page){
        if(page.pagePurpose().isBlank()||page.title().isBlank()||page.keyPoints().isEmpty()||page.description().isBlank())throw new IllegalStateException("PPT页面缺少展示价值");
        String title=page.title().replaceAll("\\s+","");if(KEYWORD_TITLES.stream().anyMatch(title::contains))throw new IllegalStateException("禁止生成关键词或开发环境独立页面");
        if(page.keyPoints().size()>3)throw new IllegalStateException("普通页核心观点不得超过3个");
    }

    private PagePlan validateOrRebuild(PagePlan page,String section,int index){
        try{requireValue(page);return page;}catch(RuntimeException ignored){String title=rewriteTitle(section,section,index);List<String> points=List.of("明确本节目标","归纳核心方案","说明实际作用");return new PagePlan("说明"+section+"的答辩重点",title,points,description(points,"概括本节价值"),section);}
    }
    private String sourceAt(List<String> candidates,String fallback,int index){if(!candidates.isEmpty())return candidates.get(index%candidates.size());String clean=PptDocumentParser.clean(fallback);return clean.isBlank()?"围绕系统目标、核心方案与实际作用进行说明":clean;}
    private int score(String value,String section){String theme=(section+" "+value).toLowerCase(Locale.ROOT);int score=0;for(String word:themeWords(section))if(value.contains(word))score+=4;if(value.length()>=40)score+=2;if(value.matches(".*[。；，].*"))score++;return score;}
    private List<String> themeWords(String section){if(section.matches(".*(概述|背景|课题).*"))return List.of("背景","意义","目标","需求","问题");if(section.matches(".*(设计|方案|架构).*"))return List.of("设计","架构","数据库","模块","流程");if(section.matches(".*(实现|功能).*"))return List.of("实现","功能","界面","用户","管理");if(section.matches(".*(测试|验证).*"))return List.of("测试","结果","用例","性能","验证");return List.of("目标","方案","作用");}
    private String purpose(String section,String source,int index){if(source.contains("背景")||source.contains("意义"))return "说明课题背景与建设价值";if(source.contains("需求"))return "归纳系统要解决的核心需求";if(source.contains("架构"))return "解释系统总体架构及职责划分";if(source.contains("数据库"))return "说明数据模型与业务支撑关系";if(source.contains("实现")||source.contains("功能"))return "展示核心功能的实现方案";if(source.contains("测试"))return "说明测试方法与验证结果";return "提炼"+section+"的核心答辩内容"+(index+1);}
    private String rewriteTitle(String section,String source,int index){String v=PptDocumentParser.clean(source).replaceFirst("^(?:第?[一二三四五六七八九十0-9]+章\\s*)","").replaceFirst("^\\d+(?:[.．]\\d+)+\\s*","");if(section.matches(".*(概述|背景).*"))return index==0?"项目背景与价值":"建设目标与需求";if(section.matches(".*(设计|方案).*")){if(v.contains("数据库"))return "数据模型设计";return index==0?"系统总体架构":"核心模块设计";}if(section.matches(".*实现.*"))return index==0?"核心功能实现":"业务流程实现";if(section.matches(".*测试.*"))return index==0?"测试方案与结果":"系统验证结论";if(v.contains("系统架构")||v.contains("架构设计"))return "系统总体架构";if(v.contains("需求分析"))return "核心业务需求";if(v.contains("数据库"))return "数据模型设计";if(v.contains("功能设计"))return "核心功能设计";if(v.contains("功能实现")||v.contains("系统实现"))return "核心功能实现";if(v.contains("系统测试")||v.contains("测试分析"))return "测试方案与结果";if(v.contains("研究背景")||v.contains("研究意义"))return "项目背景与价值";if(v.contains("可行性"))return "项目可行性分析";if(section.contains("课题"))return index==0?"核心业务需求":"解决方案概览";return PptDocumentParser.shorten(section+"核心内容",24);}
    private String uniqueTitle(String title,Set<String> used){String candidate=PptDocumentParser.shorten(title,24);if(used.add(candidate))return candidate;int n=2;while(!used.add(PptDocumentParser.shorten(candidate+"（"+n+"）",24)))n++;return PptDocumentParser.shorten(candidate+"（"+n+"）",24);}
    private List<String> keyPoints(String source,String fallback){LinkedHashSet<String> out=new LinkedHashSet<>();String combined=PptDocumentParser.clean(source+"。"+fallback);for(String part:combined.split("[。！？；;.!?\\n]+")){String clean=part.replaceFirst("^\\d+(?:[.．]\\d+)+\\s*","").trim();if(clean.length()<6||FORM_MARKERS.stream().anyMatch(clean::contains))continue;out.add(PptDocumentParser.shorten(clean,20));if(out.size()==3)break;}if(out.isEmpty())out.addAll(List.of("明确页面目标","提炼核心方案","说明实际作用"));return new ArrayList<>(out);}
    private String description(List<String> points,String purpose){String joined=String.join("、",points);return PptDocumentParser.shorten("本页围绕"+purpose+"展开，重点说明"+joined+"，形成面向答辩展示的完整结论。",70);}

    public record PagePlan(String pagePurpose,String title,List<String> keyPoints,String description,String sourceText){}
}
