package com.dropai.rewrite.service.ppt;

import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class PptContentPlannerV2 {
    private final PptPlannerRuleLibrary rules;

    public PptContentPlannerV2(PptPlannerRuleLibrary rules){this.rules=rules;}

    public PlannerResult plan(PlannerInput input){
        if(input==null||input.chapters()==null)throw new IllegalArgumentException("ContentPlanner V2输入章节不能为空");
        PptPlannerRuleLibrary.RuleSet ruleSet=rules.load(input.majorType());List<FilteredContent> filtered=new ArrayList<>();List<ChapterCandidates> chapters=new ArrayList<>();
        String thesisTitle=metadataValue(input.metadata(),"title","题目","课题名称");Set<String> technologies=collectTechnologies(input.chapters(),ruleSet.technologyTerms());boolean technologyAdded=false;
        for(SourceChapter chapter:input.chapters()){
            if(isFrontMatterChapter(chapter)){for(String paragraph:safe(chapter.paragraphs()))filtered.add(new FilteredContent(chapter.title(),PptDocumentParser.clean(paragraph),"FRONT_MATTER"));continue;}
            Set<String> allowedPurposes=allowedPurposes(chapter,ruleSet.chapterConstraints());
            LinkedHashMap<String,CandidateDraft> drafts=new LinkedHashMap<>();
            for(String paragraph:safe(chapter.paragraphs())){
                String clean=PptDocumentParser.clean(paragraph);String reason=filterReason(clean,thesisTitle,ruleSet);
                if(reason!=null){filtered.add(new FilteredContent(chapter.title(),clean,reason));continue;}
                PptPlannerRuleLibrary.PurposeRule purpose=matchPurpose(chapter.title()+" "+clean,ruleSet.purposeRules(),allowedPurposes,chapterNumber(chapter.title()));
                if(purpose==null){filtered.add(new FilteredContent(chapter.title(),clean,"NO_ALLOWED_DEFENSE_PURPOSE"));continue;}
                CandidateDraft draft=drafts.computeIfAbsent(purpose.title(),ignored->new CandidateDraft(purpose));draft.sources.add(clean);
            }
            if(!technologyAdded&&technologies.size()>=2&&allowedPurposes.contains("METHOD")&&chapterContainsTechnologies(chapter,technologies)){
                PptPlannerRuleLibrary.PurposeRule technologyRule=new PptPlannerRuleLibrary.PurposeRule("METHOD",List.of(),"系统开发技术路线","系统采用什么技术实现？");
                CandidateDraft draft=new CandidateDraft(technologyRule);draft.explicitPoints.addAll(technologies.stream().limit(3).toList());draft.sources.add("系统采用"+String.join("、",technologies)+"完成主要功能开发与数据管理。");drafts.put(technologyRule.title(),draft);technologyAdded=true;
            }
            List<CandidatePage> candidates=new ArrayList<>();for(CandidateDraft draft:drafts.values()){CandidatePage page=toPage(chapter.title(),draft);requireComplete(page);candidates.add(page);}
            if(!candidates.isEmpty())chapters.add(new ChapterCandidates(chapter.title(),candidates));
        }
        chapters=appendEvidenceCandidates(chapters,input);
        return new PlannerResult(ruleSet.majorType(),chapters,filtered);
    }

    public void requireComplete(CandidatePage page){if(page==null||blank(page.title())||blank(page.pagePurpose())||blank(page.answerQuestion())||page.keyPoints()==null||page.keyPoints().isEmpty()||blank(page.description())||blank(page.sourceChapter())||page.confidence()<=0||page.confidence()>1)throw new IllegalStateException("ContentPlanner V2候选页面字段不完整");}

    private CandidatePage toPage(String sourceChapter,CandidateDraft draft){List<String> points=!draft.explicitPoints.isEmpty()?draft.explicitPoints:keyPoints(draft.sources);String description=blank(draft.rule.description())?completeText(summary(draft.sources,points),100):draft.rule.description();double confidence=Math.min(.97,.72+Math.min(5,draft.sources.size())*.04+(points.size()>=3?.05:0));return new CandidatePage(draft.rule.title(),draft.rule.purpose(),draft.rule.answerQuestion(),points,description,sourceChapter,confidence);}
    private List<String> keyPoints(List<String> sources){LinkedHashSet<String> points=new LinkedHashSet<>();for(String source:sources)for(String part:source.split("[。！？；;.!?，,\\n]+")){String clean=part.replaceFirst("^(?:第?[一二三四五六七八九十0-9]+章\\s*)?\\d*(?:[.．]\\d+)*\\s*","").trim();if(clean.length()<4||clean.matches(".*(?:章|节|设计|分析|实现|测试|总结|展望)$")&&clean.length()<16)continue;points.add(completeText(clean,22));if(points.size()==3)return new ArrayList<>(points);}if(points.isEmpty())points.add("概括本节核心内容");return new ArrayList<>(points);}
    private String summary(List<String> sources,List<String> points){for(String source:sources)if(source.length()>=24)return source;return "本候选页围绕"+String.join("、",points)+"展开，用于回答本页对应的答辩问题。";}
    private String completeText(String value,int max){String clean=PptDocumentParser.clean(value).replaceFirst("^(?:第?[一二三四五六七八九十0-9]+章\\s*)?\\d+(?:[.．]\\d+)*\\s+","");if(clean.length()<=max)return ensureSentence(clean);int cut=-1;for(char mark:new char[]{'。','！','？','；'}){int found=clean.lastIndexOf(mark,max);if(found>cut)cut=found;}if(cut>=Math.min(28,max/2))return clean.substring(0,cut+1);int comma=Math.max(clean.lastIndexOf('，',max-1),clean.lastIndexOf('、',max-1));if(comma>=Math.min(20,max/2))return ensureSentence(clean.substring(0,comma));return ensureSentence(clean.substring(0,Math.min(max,clean.length())).replaceFirst("[的地得和与及并且以及从向对在为通过利用采用基于]$",""));}
    private String ensureSentence(String value){String clean=PptDocumentParser.clean(value).replaceFirst("[，、；：:]+$","");return clean.matches(".*[。！？]$")?clean:clean+"。";}

    private List<ChapterCandidates> appendEvidenceCandidates(List<ChapterCandidates> textChapters,PlannerInput input){LinkedHashMap<String,List<CandidatePage>> byChapter=new LinkedHashMap<>();for(var chapter:textChapters)byChapter.put(chapter.chapter(),new ArrayList<>(chapter.candidatePages()));Map<Integer,SourceChapter> sourceByNumber=new LinkedHashMap<>();for(var chapter:safe(input.chapters()))sourceByNumber.put(chapterNumber(chapter.title()),chapter);
        for(Map<String,Object> asset:safe(input.assets())){String chapterId=string(asset.get("chapterId"));int number=numberFromId(chapterId);SourceChapter source=sourceByNumber.get(number);if(source==null){number=numberFromId(string(asset.get("id")));source=sourceByNumber.get(number);}if(source==null)source=sourceByNumber.getOrDefault(4,sourceByNumber.values().stream().findFirst().orElse(null));if(source==null)continue;number=chapterNumber(source.title());String caption=string(asset.get("caption"));String figureId=string(asset.get("id"));String title=figureTitle(caption,figureId);String purpose=number==2||number==3?"DESIGN":number==5?"TEST":"RESULT";String role=number==4?"PROOF":"INFORMATION";String description=evidenceDescription(caption,title);SourceRefs refs=new SourceRefs(source.id(),List.of(),figureId,List.of(),List.of());CandidatePage page=new CandidatePage("IMAGE_EVIDENCE",title,purpose,"该图如何证明本节设计或实现成果？",List.of("图示结构与关键关系","对应章节的实现依据"),description,source.title(),.95,role,refs,true,List.of());byChapter.computeIfAbsent(source.title(),ignored->new ArrayList<>()).add(page);}
        Map<String,List<Map<String,Object>>> tablesByChapter=new LinkedHashMap<>();for(Map<String,Object> table:safe(input.tables())){String chapter=string(table.get("chapter"));if(!chapter.isBlank())tablesByChapter.computeIfAbsent(chapter,ignored->new ArrayList<>()).add(table);}for(var entry:tablesByChapter.entrySet()){if(entry.getValue().size()<2)continue;int number=chapterNumber(entry.getKey());String purpose=number==3?"DATABASE":number==5?"TEST":"RESULT";List<List<String>> summary=entry.getValue().stream().limit(6).map(t->List.of(string(t.get("id")),blank(string(t.get("caption")))?"汇总本章关键数据":string(t.get("caption")))).toList();List<String> ids=entry.getValue().stream().map(t->string(t.get("id"))).toList();SourceRefs refs=new SourceRefs(string(entry.getValue().get(0).get("chapterId")),List.of(),"",List.of(),ids);CandidatePage page=new CandidatePage("TABLE_SUMMARY",number==3?"核心数据库表设计":"本章关键数据汇总",purpose,number==3?"系统数据库由哪些核心业务表组成？":"本章表格说明了哪些关键结果？",summary.stream().limit(3).map(row->row.get(1)).toList(),"该表汇总本章多个关键表格，集中呈现核心对象、用途及验证结果，便于答辩时快速说明数据关系。",entry.getKey(),.9,"",refs,false,summary);byChapter.computeIfAbsent(entry.getKey(),ignored->new ArrayList<>()).add(page);}
        return byChapter.entrySet().stream().map(e->new ChapterCandidates(e.getKey(),e.getValue())).toList();}
    private String evidenceDescription(String caption,String title){String clean=PptDocumentParser.clean(caption).replaceFirst("^(?:图|Figure|Fig\\.)\\s*\\d+(?:[-.．]\\d+)?[^\\n]{0,40}","").trim();if(clean.length()<20)clean="该图展示"+title+"中的核心结构、业务关系与实现结果，用于说明对应方案如何在系统中落地。";return completeText(clean,90);}
    private String figureTitle(String caption,String id){String value=caption==null?"":caption;var matcher=java.util.regex.Pattern.compile("(?m)^(?:图|Figure|Fig\\.)\\s*\\d+(?:[-.．]\\d+)?[　\\s]*([^\\n。]{2,35})",java.util.regex.Pattern.CASE_INSENSITIVE).matcher(value);if(matcher.find())return matcher.group(1).replaceFirst("图$","").trim();LinkedHashMap<String,String> names=new LinkedHashMap<>();names.put("AI健康评估","AI健康评估页面");names.put("AI助手","AI助手交互页面");names.put("数据可视化","健康数据可视化页面");names.put("系统架构","系统总体架构图");names.put("功能结构","系统功能结构图");names.put("操作流程","系统操作流程图");names.put("管理员用例","管理员用例图");names.put("登录","系统登录页面");names.put("健康目标","健康目标页面");for(var entry:names.entrySet())if(value.contains(entry.getKey()))return entry.getValue();return "系统证据图 "+id.replace("figure_","").replace('_','-');}
    private int numberFromId(String value){var matcher=java.util.regex.Pattern.compile("(\\d+)").matcher(value==null?"":value);return matcher.find()?Integer.parseInt(matcher.group(1)):0;}
    private String string(Object value){return value==null?"":String.valueOf(value);}
    private String filterReason(String value,String thesisTitle,PptPlannerRuleLibrary.RuleSet rules){String compact=value.replaceAll("\\s+","");String canonical=canonical(compact);if(compact.length()<4)return "EMPTY_OR_HEADING";if(!thesisTitle.isBlank()&&(compact.equals(thesisTitle.replaceAll("\\s+",""))||compact.matches("^(题目|课题名称)[：:]?"+java.util.regex.Pattern.quote(thesisTitle.replaceAll("\\s+",""))+"$")))return "THESIS_TITLE";if(rules.formMarkers().stream().map(this::canonical).anyMatch(canonical::contains)||canonical.contains("任务书")||canonical.contains("答辩记录")||canonical.contains("审批意见")||canonical.contains("综合评价"))return "FORM_CONTENT";long metadataHits=rules.metadataLabels().stream().map(this::canonical).filter(canonical::contains).count();if(metadataHits>=2||rules.metadataLabels().stream().anyMatch(label->compact.matches("^"+java.util.regex.Pattern.quote(label)+"[：:].*")))return "METADATA";if(rules.forbiddenStandalone().stream().anyMatch(term->canonical.equalsIgnoreCase(canonical(term))))return "LOW_VALUE_STANDALONE";if(compact.matches("^(关键词|关键字)[：:].*"))return "KEYWORDS";return null;}
    private PptPlannerRuleLibrary.PurposeRule matchPurpose(String text,List<PptPlannerRuleLibrary.PurposeRule> purposeRules,Set<String> allowedPurposes,int chapterNumber){PptPlannerRuleLibrary.PurposeRule best=null;int bestScore=0;for(var rule:purposeRules){if("系统开发技术路线".equals(rule.title())||!allowedPurposes.contains(rule.purpose())||!safe(rule.chapterNumbers()).isEmpty()&&!rule.chapterNumbers().contains(chapterNumber))continue;int score=0;for(String keyword:safe(rule.keywords()))if(text.contains(keyword))score++;if(score>bestScore){best=rule;bestScore=score;}}return best;}
    private Set<String> collectTechnologies(List<SourceChapter> chapters,List<String> terms){LinkedHashSet<String> found=new LinkedHashSet<>();String all=chapters.stream().flatMap(c->safe(c.paragraphs()).stream()).reduce("",(a,b)->a+" "+b);for(String term:safe(terms))if(all.toLowerCase().contains(term.toLowerCase()))found.add(term);return found;}
    private boolean chapterContainsTechnologies(SourceChapter chapter,Set<String> technologies){String text=String.join(" ",safe(chapter.paragraphs())).toLowerCase();return technologies.stream().filter(term->text.contains(term.toLowerCase())).count()>=2;}
    private Set<String> allowedPurposes(SourceChapter chapter,List<PptPlannerRuleLibrary.ChapterConstraint> constraints){int number=chapterNumber(chapter.title());for(var constraint:safe(constraints))if(constraint.chapterNumber()==number)return new LinkedHashSet<>(safe(constraint.allowedPurposes()));return new LinkedHashSet<>(List.of("BACKGROUND","PROBLEM","METHOD","DESIGN","DATABASE","IMPLEMENTATION","RESULT","TEST","SUMMARY"));}
    private int chapterNumber(String title){String value=title==null?"":title;var arabic=java.util.regex.Pattern.compile("(?:第)?(\\d+)章").matcher(value);if(arabic.find())return Integer.parseInt(arabic.group(1));List<String> chinese=List.of("一","二","三","四","五","六","七","八","九","十");for(int i=0;i<chinese.size();i++)if(value.contains("第"+chinese.get(i)+"章"))return i+1;return 0;}
    private boolean isFrontMatterChapter(SourceChapter chapter){String title=chapter.title()==null?"":chapter.title().replaceAll("\\s+","");return title.equals("正文内容")||title.equals("前置内容")||title.contains("封面")||title.contains("任务书")||title.contains("摘要")||title.equalsIgnoreCase("abstract");}
    private String canonical(String value){return value==null?"":value.replaceAll("[\\s：:（）()【】\\[\\]·._-]+","").toLowerCase();}
    private String metadataValue(Map<String,String> metadata,String...keys){if(metadata==null)return"";for(String key:keys){String value=metadata.get(key);if(value!=null&&!value.isBlank())return PptDocumentParser.clean(value);}return"";}
    private <T> List<T> safe(List<T> value){return value==null?List.of():value;}
    private boolean blank(String value){return value==null||value.isBlank();}

    private static class CandidateDraft{private final PptPlannerRuleLibrary.PurposeRule rule;private final List<String>sources=new ArrayList<>();private final List<String>explicitPoints=new ArrayList<>();private CandidateDraft(PptPlannerRuleLibrary.PurposeRule rule){this.rule=rule;if(rule.keyPoints()!=null)explicitPoints.addAll(rule.keyPoints());}}
    public record PlannerInput(Map<String,String> metadata,List<SourceChapter> chapters,List<Map<String,Object>> assets,List<Map<String,Object>> tables,String majorType){}
    public record SourceChapter(String id,String title,List<String> paragraphs){}
    public record CandidatePage(String candidateType,String title,String pagePurpose,String answerQuestion,List<String> keyPoints,String description,String sourceChapter,double confidence,String imageRole,SourceRefs sourceRefs,boolean mandatoryAsset,List<List<String>> tableSummary){public CandidatePage(String title,String pagePurpose,String answerQuestion,List<String> keyPoints,String description,String sourceChapter,double confidence){this("TEXT",title,pagePurpose,answerQuestion,keyPoints,description,sourceChapter,confidence,"",null,false,List.of());}}
    public record SourceRefs(String chapterId,List<String> headingIds,String figureId,List<String> paragraphIds,List<String> tableIds){}
    public record ChapterCandidates(String chapter,List<CandidatePage> candidatePages){}
    public record FilteredContent(String sourceChapter,String content,String reason){}
    public record PlannerResult(String majorType,List<ChapterCandidates> chapters,List<FilteredContent> filteredContents){}
}
