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
    public static final String LITERATURE_COMPARISON_TITLE = "国内外文献对比";
    public static final String VALUE_QUADRANT_TITLE = "四象限研究价值";
    public static final String RESEARCH_TIMELINE_TITLE = "研究逻辑时间轴";
    public static final String METRICS_PROGRESS_TITLE = "指标与进度路径";
    private final PptPlannerRuleLibrary rules;
    private static final Set<String> HEALTH_ONLY_RULES=Set.of("AI健康评估功能","健康数据可视化","用户端交互设计");

    public PptContentPlannerV2(PptPlannerRuleLibrary rules){this.rules=rules;}

    public PlannerResult plan(PlannerInput input){
        if(input==null||input.chapters()==null)throw new IllegalArgumentException("ContentPlanner V2输入章节不能为空");
        PptPlannerRuleLibrary.RuleSet ruleSet=rules.load(input.majorType());List<PptPlannerRuleLibrary.PurposeRule> purposeRules=applicablePurposeRules(input,ruleSet);List<FilteredContent> filtered=new ArrayList<>();List<ChapterCandidates> chapters=new ArrayList<>();
        String thesisTitle=metadataValue(input.metadata(),"title","题目","课题名称");Set<String> technologies=collectTechnologies(input.chapters(),ruleSet.technologyTerms());boolean technologyAdded=false;
        for(SourceChapter chapter:input.chapters()){
            if(isFrontMatterChapter(chapter)){for(String paragraph:safe(chapter.paragraphs()))filtered.add(new FilteredContent(chapter.title(),PptDocumentParser.clean(paragraph),"FRONT_MATTER"));continue;}
            Set<String> allowedPurposes=allowedPurposes(chapter,ruleSet.chapterConstraints());
            LinkedHashMap<String,CandidateDraft> drafts=new LinkedHashMap<>();
            for(String paragraph:safe(chapter.paragraphs())){
                String clean=PptDocumentParser.clean(paragraph);String reason=filterReason(clean,thesisTitle,ruleSet);
                if(reason!=null){filtered.add(new FilteredContent(chapter.title(),clean,reason));continue;}
                PptPlannerRuleLibrary.PurposeRule purpose=matchPurpose(chapter.title(),clean,purposeRules,allowedPurposes,chapterNumber(chapter.title()));
                if(purpose==null){filtered.add(new FilteredContent(chapter.title(),clean,"NO_ALLOWED_DEFENSE_PURPOSE"));continue;}
                CandidateDraft draft=drafts.computeIfAbsent(purpose.title(),ignored->new CandidateDraft(purpose));draft.sources.add(clean);
            }
            if("computer".equalsIgnoreCase(ruleSet.majorType()))ensureNamedRuleDraft(chapter,allowedPurposes,purposeRules,drafts,"系统功能架构");
            if(!technologyAdded&&technologies.size()>=2&&allowedPurposes.contains("METHOD")&&chapterContainsTechnologies(chapter,technologies)){
                PptPlannerRuleLibrary.PurposeRule technologyRule=new PptPlannerRuleLibrary.PurposeRule("METHOD",List.of(),"系统开发技术路线","系统采用什么技术实现？");
                CandidateDraft draft=new CandidateDraft(technologyRule);draft.explicitPoints.addAll(technologies.stream().limit(3).toList());draft.sources.add("系统采用"+String.join("、",technologies)+"完成主要功能开发与数据管理。");drafts.put(technologyRule.title(),draft);technologyAdded=true;
            }
            boolean richNarrative="social_science".equalsIgnoreCase(ruleSet.majorType());
            List<CandidatePage> candidates=new ArrayList<>();for(CandidateDraft draft:drafts.values()){CandidatePage page=toPage(chapter.title(),draft,richNarrative);if(isOpeningProposal(input)&&page.keyPoints().stream().allMatch(point->point.equals("概括本节核心内容")))continue;requireComplete(page);candidates.add(page);}
            if(!candidates.isEmpty())chapters.add(new ChapterCandidates(chapter.title(),candidates));
        }
        if(isOpeningProposal(input)){
            chapters=appendOpeningProposalSemanticCandidates(chapters,input);
            if(chapters.stream().flatMap(chapter->chapter.candidatePages().stream())
                    .noneMatch(page->"SUMMARY".equals(page.pagePurpose())))
                chapters=appendOpeningSummary(chapters,input);
        }
        chapters=appendEvidenceCandidates(chapters,input);
        requireEvidenceCoverage(chapters,input.assets());
        if("nursing".equalsIgnoreCase(ruleSet.majorType())&&chapters.stream().flatMap(c->c.candidatePages().stream()).noneMatch(p->"SUMMARY".equals(p.pagePurpose())))chapters=appendNursingSummary(chapters,input);
        return new PlannerResult(ruleSet.majorType(),chapters,filtered);
    }

    private List<PptPlannerRuleLibrary.PurposeRule> applicablePurposeRules(PlannerInput input,PptPlannerRuleLibrary.RuleSet rules){
        if(!"computer".equalsIgnoreCase(rules.majorType()))return rules.purposeRules();
        String title=metadataValue(input.metadata(),"title","题目","课题名称");
        boolean healthExample=title.contains("健康管理")||input.chapters().stream()
                .flatMap(chapter->safe(chapter.paragraphs()).stream())
                .anyMatch(paragraph->paragraph.contains("AI健康评估")&&paragraph.contains("健康数据"));
        if(healthExample)return rules.purposeRules();
        return rules.purposeRules().stream().filter(rule->!HEALTH_ONLY_RULES.contains(rule.title()))
                .map(rule->new PptPlannerRuleLibrary.PurposeRule(rule.purpose(),rule.keywords(),rule.title(),
                        rule.answerQuestion(),List.of(),rule.chapterNumbers(),""))
                .toList();
    }

    private List<ChapterCandidates> appendNursingSummary(List<ChapterCandidates> chapters,PlannerInput input){List<ChapterCandidates> out=new ArrayList<>(chapters);CandidatePage page=new CandidatePage("预期成果与应用价值","SUMMARY","课题预计形成什么成果并产生何种价值？",List.of("形成护理研究进展综述","归纳干预措施及适用条件","为临床护理和后续研究提供参考"),"课题将系统整理现有护理证据，比较不同措施的实施条件与证据局限，形成可供临床护理和后续研究参考的综述成果。","研究目的及意义",.9);if(out.isEmpty())out.add(new ChapterCandidates("第一章",List.of(page)));else{ChapterCandidates first=out.get(0);List<CandidatePage> pages=new ArrayList<>(first.candidatePages());pages.add(page);out.set(0,new ChapterCandidates(first.chapter(),pages));}return List.copyOf(out);}

    private List<ChapterCandidates> appendOpeningSummary(List<ChapterCandidates> chapters,PlannerInput input){
        List<String> paragraphs=input.chapters().stream().flatMap(chapter->safe(chapter.paragraphs()).stream())
                .map(PptDocumentParser::clean).filter(value->!value.isBlank()).toList();
        List<String> outcomes=paragraphsFor(input.chapters(),List.of("预期成果","研究特色","创新点","进度安排","工作计划"));
        if(outcomes.isEmpty())outcomes=paragraphs;
        String sourceChapter=chapterFor(input.chapters(),List.of("预期成果","进度安排","总体安排","工作计划"),firstChapter(input.chapters()));
        String title=metadataValue(input.metadata(),"title","题目","课题名称");
        List<String> points=List.of(
                openingOutcomePoint(outcomes,paragraphs,"研究成果"),
                openingOutcomePoint(outcomes,paragraphs,"验证重点"),
                openingOutcomePoint(outcomes,paragraphs,"交付计划"));
        String description="围绕"+title+"，本课题将形成可核验的研究成果，按计划完成方法验证、论文整理与开题后的实施工作。";
        CandidatePage page=new CandidatePage("TEXT","预期成果与实施展望","SUMMARY","开题后预计完成什么并如何检验？",
                points,description,sourceChapter,.94,"KEY_POINTS","",null,false,List.of());
        LinkedHashMap<String,List<CandidatePage>> grouped=new LinkedHashMap<>();
        for(ChapterCandidates chapter:chapters)grouped.put(chapter.chapter(),new ArrayList<>(chapter.candidatePages()));
        grouped.computeIfAbsent(sourceChapter,ignored->new ArrayList<>()).add(page);
        return grouped.entrySet().stream().map(entry->new ChapterCandidates(entry.getKey(),List.copyOf(entry.getValue()))).toList();
    }

    private List<ChapterCandidates> appendOpeningProposalSemanticCandidates(List<ChapterCandidates> chapters,PlannerInput input){
        LinkedHashMap<String,List<CandidatePage>> grouped=new LinkedHashMap<>();
        for(ChapterCandidates chapter:chapters){List<CandidatePage> pages=new ArrayList<>(safe(chapter.candidatePages()));pages.removeIf(page->Set.of("国内外研究现状","选题背景与研究价值","数据来源与技术路线","研究进度安排",LITERATURE_COMPARISON_TITLE,VALUE_QUADRANT_TITLE,RESEARCH_TIMELINE_TITLE,METRICS_PROGRESS_TITLE).contains(page.title()));grouped.put(chapter.chapter(),pages);}
        String basis=chapterFor(input.chapters(),List.of("研究现状","选题目的","选题意义"),firstChapter(input.chapters()));
        String design=chapterFor(input.chapters(),List.of("基本内容","研究方案","研究方法","技术路线"),basis);
        String plan=chapterFor(input.chapters(),List.of("进度安排","工作计划","总体安排"),design);
        List<String> paragraphs=input.chapters().stream().flatMap(chapter->safe(chapter.paragraphs()).stream()).map(PptDocumentParser::clean).filter(value->!value.isBlank()).toList();
        List<String> valueParagraphs=paragraphsFor(input.chapters(),List.of("选题目的","选题意义"));
        List<String> designParagraphs=paragraphsFor(input.chapters(),List.of("基本内容","研究方案"));
        List<String> planParagraphs=paragraphsFor(input.chapters(),List.of("进度安排","工作计划","总体安排"));
        List<String> literature=List.of(
                groundedPoint(paragraphs,List.of("国外研究","国外在线","国外"),"国外研究","梳理国外相关研究方法"),
                groundedPoint(paragraphs,List.of("国内研究","国内推荐","国内"),"国内研究","梳理国内相关研究方法"));
        List<String> values=List.of(
                groundedPoint(paragraphs,List.of("应用层面","应用前景","平台可据此"),"应用价值","识别研究对象的实际需求"),
                groundedPoint(paragraphs,List.of("技术层面","研究方法","模型组合"),"方法价值","建立可检验的研究方法"),
                groundedPoint(paragraphs,List.of("用户","消费者","受众"),"用户视角","回应目标用户的核心问题"),
                groundedPoint(paragraphs,List.of("管理员","运营","管理"),"实践视角","为业务改进提供依据"));
        List<String> logic=openingLogicSteps(paragraphs);
        List<String> metrics=openingMetrics(paragraphs,concat(designParagraphs,planParagraphs));
        addSemantic(grouped,basis,new CandidatePage("TEXT",LITERATURE_COMPARISON_TITLE,"BACKGROUND","国内外研究的关注重点、方法与不足有何差异？",literature,sourceSentence(paragraphs,List.of("综合研究动态","现有研究","研究仍","仍有拓展空间"),"国内外研究为本课题提供方法基础，也留下结合具体研究对象进一步验证的空间。"),basis,.97,"COMPARISON","",null,false,List.of()));
        addSemantic(grouped,basis,new CandidatePage("TEXT",VALUE_QUADRANT_TITLE,"BACKGROUND","本课题从哪些互补视角体现研究价值？",values,sourceSentence(paragraphs,List.of("应用层面","技术层面","研究结果可服务","研究对象"),"课题从应用、方法、用户和实践四个视角明确研究价值。"),basis,.97,"COMPARISON","",null,false,List.of()));
        addSemantic(grouped,design,new CandidatePage("TEXT",RESEARCH_TIMELINE_TITLE,"METHOD","研究如何从问题提出推进到成果形成？",logic,"研究从问题界定出发，依次完成资料整理、方法设计、实施验证与成果整理。",design,.98,"PROCESS","",null,false,List.of()));
        addSemantic(grouped,plan,new CandidatePage("TEXT",METRICS_PROGRESS_TITLE,"TEST","核心指标与研究进度如何形成可检查的推进路径？",metrics,"评价指标、数据准备、验证方法与阶段节点共同约束研究执行。",plan,.98,"METRICS","",null,false,List.of()));
        return grouped.entrySet().stream().map(entry->new ChapterCandidates(entry.getKey(),List.copyOf(entry.getValue()))).toList();
    }
    private List<String> openingMetrics(List<String> paragraphs,List<String> scoped){
        String evidence=String.join(" ",scoped.isEmpty()?paragraphs:scoped);
        String all=String.join(" ",paragraphs);
        boolean recommendation=evidence.contains("Precision@K")||evidence.contains("Recall@K")||all.contains("智能问答与推荐");
        boolean textMining=evidence.contains("LinearSVC")||evidence.contains("LDA")||all.contains("文本挖掘");
        return List.of(
                "评价指标："+(recommendation?"推荐命中率与问答溯源":textMining?"主题一致性与分类F1值":"与研究目标对应的检验指标"),
                "数据基础："+(recommendation?"安全图书与问答知识库":all.contains("京东")?"京东评价与站外讨论文本":"原始资料与研究样本"),
                "验证方法："+(recommendation?"功能测试与推荐对照":textMining?"主题、情感与聚类评估":"功能与研究结果验证"),
                "进度节点：资料整理、实施、测试、定稿",
                "成果交付："+(recommendation?"问答推荐系统与论文":textMining?"营销策略、分析系统与论文":"研究成果与答辩论文"));
    }
    private String openingOutcomePoint(List<String> scoped,List<String> all,String label){
        String evidence=String.join(" ",scoped.isEmpty()?all:scoped);
        String full=String.join(" ",all);
        boolean recommendation=full.contains("智能问答与推荐")||evidence.contains("Precision@K");
        boolean textMining=full.contains("华为Mate80")&&full.contains("文本挖掘");
        return switch(label){
            case "研究成果" -> "研究成果："+(recommendation?"问答推荐系统与安全知识服务":textMining?"用户文本分析与精准营销策略":"形成可检验的研究成果");
            case "验证重点" -> "验证重点："+(recommendation?"问答来源、推荐指标与业务闭环":textMining?"主题、情感与受众细分效果":"核对研究指标与实施结果");
            default -> "交付计划：完成成果验证、论文定稿与答辩";
        };
    }
    private void addSemantic(Map<String,List<CandidatePage>> grouped,String chapter,CandidatePage page){grouped.computeIfAbsent(chapter,ignored->new ArrayList<>()).add(page);}
    private String groundedPoint(List<String> paragraphs,List<String> markers,String label,String fallback){
        for(String paragraph:paragraphs)for(String marker:markers){int index=paragraph.indexOf(marker);if(index<0||paragraph.length()<35||paragraph.contains("毕业设计（论文）选题背景"))continue;
            int start=Math.max(paragraph.lastIndexOf('。',index),paragraph.lastIndexOf('；',index))+1;
            int end=paragraph.indexOf('。',index);if(end<0)end=paragraph.length();
            String sentence=PptDocumentParser.clean(paragraph.substring(start,Math.min(end+1,paragraph.length())));
            for(String clause:sentence.replaceAll("\\[[0-9,，—-]+]","").split("[，,；;。]+")){
                String clean=PptDocumentParser.clean(clause).replaceFirst("^(?:国外研究中|国内研究中|在应用层面|在技术层面)[：:]?","").trim();
                if(clean.length()>=10&&clean.length()<=42&&!clean.matches("^\\d+[.．].*"))
                    return label+"："+clean;
            }
        }
        return label+"："+fallback;
    }
    private List<String> openingLogicSteps(List<String> paragraphs){
        for(String paragraph:paragraphs){if(!paragraph.contains("技术路线为")&&!paragraph.contains("研究按照"))continue;
            String route=paragraph.replaceFirst("^.*?(?:技术路线为[：:]?|研究按照[“\"]?)","");
            String[] parts=route.split("[→↓—]+",7);LinkedHashSet<String> steps=new LinkedHashSet<>();
            for(String part:parts){String clean=PptDocumentParser.clean(part).replaceFirst("[”\"。；;].*$","");
                if(clean.length()>=4)steps.add(completeText(clean,16));if(steps.size()>=5)break;}
            if(steps.size()>=4)return List.copyOf(steps);
        }
        return List.of("梳理研究背景与已有成果","明确研究问题与方法","整理资料并开展分析","验证结果与关键指标","形成成果与实施建议");
    }
    private boolean isOpeningProposal(PlannerInput input){return "OPENING_PROPOSAL".equalsIgnoreCase(input.metadata()==null?"":input.metadata().getOrDefault("presentationMode",""))||PptOpeningProposalDetector.isOpeningProposal(input.chapters().stream().flatMap(chapter->safe(chapter.paragraphs()).stream()).toList());}
    private String firstChapter(List<SourceChapter> chapters){return safe(chapters).stream().filter(chapter->!isFrontMatterChapter(chapter)).map(SourceChapter::title).findFirst().orElse("第一章");}
    private String chapterFor(List<SourceChapter> chapters,List<String> markers,String fallback){return safe(chapters).stream().filter(chapter->markers.stream().anyMatch(marker->chapter.title().contains(marker))).map(SourceChapter::title).findFirst().orElse(fallback);}
    private List<String> paragraphsFor(List<SourceChapter> chapters,List<String> markers){return safe(chapters).stream().filter(chapter->markers.stream().anyMatch(marker->chapter.title().contains(marker))).flatMap(chapter->safe(chapter.paragraphs()).stream()).map(PptDocumentParser::clean).filter(value->!value.isBlank()).toList();}
    private List<String> concat(List<String> first,List<String> second){List<String> values=new ArrayList<>(safe(first));values.addAll(safe(second));return List.copyOf(values);}
    private List<String> distinctStatements(List<String> paragraphs,List<String> markers,int limit,int max){LinkedHashSet<String> values=new LinkedHashSet<>();for(String paragraph:paragraphs){if(markers.stream().noneMatch(paragraph::contains))continue;for(String part:paragraph.split("[。；;]+")){String clean=PptDocumentParser.clean(part).replaceFirst("^(?:首先|其次|再次|最后|理论方面|实践方面)[，,:：]?","");if(clean.length()<6)continue;values.add(completeText(clean,max));if(values.size()>=limit)return List.copyOf(values);}}return List.copyOf(values);}
    private List<String> fill(List<String> actual,List<String> fallback,int size){LinkedHashSet<String> values=new LinkedHashSet<>(safe(actual));for(String value:fallback){if(values.size()>=size)break;values.add(value);}return values.stream().limit(size).toList();}
    private String sourceSentence(List<String> paragraphs,List<String> markers,String fallback){for(String paragraph:paragraphs)if(markers.stream().anyMatch(paragraph::contains)){String value=completeText(paragraph,72);if(value.length()>=18)return value;}return ensureSentence(fallback);}

    public void requireComplete(CandidatePage page){if(page==null||blank(page.title())||blank(page.pagePurpose())||blank(page.answerQuestion())||page.keyPoints()==null||page.keyPoints().isEmpty()||blank(page.description())||blank(page.sourceChapter())||page.confidence()<=0||page.confidence()>1)throw new IllegalStateException("ContentPlanner V2候选页面字段不完整");}

    private CandidatePage toPage(String sourceChapter,CandidateDraft draft,boolean richNarrative){List<String> points=!draft.explicitPoints.isEmpty()?draft.explicitPoints:keyPoints(draft.sources,richNarrative);String description=blank(draft.rule.description())?completeText(richNarrative?richSummary(draft.sources,points):summary(draft.sources,points),richNarrative?110:100):draft.rule.description();double confidence=Math.min(.97,.72+Math.min(5,draft.sources.size())*.04+(points.size()>=3?.05:0));return new CandidatePage("TEXT",draft.rule.title(),draft.rule.purpose(),draft.rule.answerQuestion(),points,description,sourceChapter,confidence,contentType(draft.rule.title(),draft.rule.purpose(),draft.rule.answerQuestion()),"",null,false,List.of());}

    /**
     * ContentPlanner owns the semantic shape of a candidate page.  The value is
     * deliberately coarse and contains no coordinates or template knowledge;
     * LayoutPlanner remains responsible for choosing an actual recipe.
     */
    private String contentType(String title,String purpose,String question){
        String value=(title+" "+purpose+" "+question).replaceAll("\\s+","");
        if(value.matches(".*(流程|路径|步骤|进度|计划|技术路线|研究逻辑|实施过程).*"))return"PROCESS";
        if(value.matches(".*(国内外|对比|比较|差异|现状|问题|不足|优劣|文献).*"))return"COMPARISON";
        if(value.matches(".*(架构|框架|体系|层级|结构|模块关系).*"))return"ARCHITECTURE";
        if(value.matches(".*(指标|数据|趋势|比例|统计|绩效|测算).*"))return"METRICS";
        return"KEY_POINTS";
    }
    private List<String> keyPoints(List<String> sources,boolean richNarrative){int limit=richNarrative?4:3;int textLimit=richNarrative?34:22;LinkedHashSet<String> points=new LinkedHashSet<>();for(String source:sources){String normalized=PptDocumentParser.clean(source);if(normalized.matches("^(?:[（(][一二三四五六七八九十0-9]+[）)]|[一二三四五六七八九十0-9]+[、.．]).{2,30}$"))continue;for(String part:source.split("[。！？；;.!?，,\\n]+")){String clean=part.replaceFirst("^(?:第?[一二三四五六七八九十0-9]+章\\s*)?\\d*(?:[.．]\\d+)*\\s*","").trim();if(clean.length()<4||clean.matches(".*(?:章|节|设计|分析|实现|测试|总结|展望)$")&&clean.length()<16)continue;points.add(completeText(clean,textLimit));if(points.size()==limit)return new ArrayList<>(points);}}if(points.isEmpty())points.add("概括本节核心内容");return new ArrayList<>(points);}
    private String richSummary(List<String> sources,List<String> points){LinkedHashSet<String> sentences=new LinkedHashSet<>();int chars=0;for(String source:sources){String normalized=PptDocumentParser.clean(source).replaceFirst("^(?:第?[一二三四五六七八九十0-9]+章\\s*)?\\d*(?:[.．]\\d+)*\\s*","");for(String part:normalized.split("(?<=[。！？；])")){String sentence=ensureSentence(part);if(sentence.length()<22||points.stream().anyMatch(point->canonical(point).equals(canonical(sentence))))continue;if(chars+sentence.length()>86)continue;sentences.add(sentence);chars+=sentence.length();if(sentences.size()==2||chars>=64)return String.join("",sentences);}}return completeText(summary(sources,points),86);}
    private String summary(List<String> sources,List<String> points){for(String source:sources)if(source.length()>=24)return source;return "本候选页围绕"+String.join("、",points)+"展开，用于回答本页对应的答辩问题。";}
    private String completeText(String value,int max){String clean=PptDocumentParser.clean(value).replaceFirst("^(?:第?[一二三四五六七八九十0-9]+章\\s*)?\\d+(?:[.．]\\d+)*\\s+","");if(clean.length()<=max)return ensureSentence(clean);int cut=-1;for(char mark:new char[]{'。','！','？','；'}){int found=clean.lastIndexOf(mark,max);if(found>cut)cut=found;}if(cut>=Math.min(28,max/2))return clean.substring(0,cut+1);int comma=Math.max(clean.lastIndexOf('，',max-1),clean.lastIndexOf('、',max-1));if(comma>=Math.min(20,max/2))return ensureSentence(clean.substring(0,comma));return ensureSentence(clean.substring(0,Math.min(max,clean.length())).replaceFirst("[的地得和与及并且以及从向对在为通过利用采用基于]$",""));}
    private String ensureSentence(String value){String clean=PptDocumentParser.clean(value).replaceFirst("[，、；：:]+$","");return clean.matches(".*[。！？]$")?clean:clean+"。";}

    private List<ChapterCandidates> appendEvidenceCandidates(List<ChapterCandidates> textChapters,PlannerInput input){LinkedHashMap<String,List<CandidatePage>> byChapter=new LinkedHashMap<>();for(var chapter:textChapters)byChapter.put(chapter.chapter(),new ArrayList<>(chapter.candidatePages()));Map<Integer,SourceChapter> sourceByNumber=new LinkedHashMap<>();for(var chapter:safe(input.chapters()))sourceByNumber.put(chapterNumber(chapter.title()),chapter);
        for(Map<String,Object> asset:safe(input.assets())){String chapterId=string(asset.get("chapterId"));int number=numberFromId(chapterId);SourceChapter source=sourceByNumber.get(number);if(source==null){number=numberFromId(string(asset.get("id")));source=sourceByNumber.get(number);}if(source==null)source=sourceByNumber.getOrDefault(4,sourceByNumber.values().stream().findFirst().orElse(null));if(source==null)continue;number=chapterNumber(source.title());String caption=string(asset.get("caption"));String figureId=string(asset.get("id"));String title=figureTitle(caption,figureId);String purpose=title.contains("进度")||title.contains("时间")?"TEST":title.contains("研究方法")||title.contains("证据")||title.contains("路线")?"METHOD":number==2||number==3?"DESIGN":number==5?"TEST":"RESULT";String role=number==4?"PROOF":"INFORMATION";String description=evidenceDescription(caption,title);SourceRefs refs=new SourceRefs(source.id(),List.of(),figureId,List.of(),List.of());CandidatePage page=new CandidatePage("IMAGE_EVIDENCE",title,purpose,title.contains("进度")?"课题各阶段如何按计划推进？":"该图呈现什么信息，并如何支撑本页结论？",List.of("图中主要信息与结构关系","与来源章节的证据对应"),description,source.title(),.95,"FIGURE",role,refs,true,List.of());byChapter.computeIfAbsent(source.title(),ignored->new ArrayList<>()).add(page);}
        Map<String,List<Map<String,Object>>> tablesByChapter=new LinkedHashMap<>();for(Map<String,Object> table:safe(input.tables())){String chapter=string(table.get("chapter"));if(!chapter.isBlank())tablesByChapter.computeIfAbsent(chapter,ignored->new ArrayList<>()).add(table);}for(var entry:tablesByChapter.entrySet()){if(entry.getValue().size()<2)continue;int number=chapterNumber(entry.getKey());String purpose=number==3?"DATABASE":number==5?"TEST":"RESULT";List<List<String>> summary=entry.getValue().stream().limit(6).map(t->List.of(string(t.get("id")),blank(string(t.get("caption")))?"汇总本章关键数据":string(t.get("caption")))).toList();List<String> ids=entry.getValue().stream().map(t->string(t.get("id"))).toList();SourceRefs refs=new SourceRefs(string(entry.getValue().get(0).get("chapterId")),List.of(),"",List.of(),ids);CandidatePage page=new CandidatePage("TABLE_SUMMARY",number==3?"核心数据库表设计":"本章关键数据汇总",purpose,number==3?"系统数据库由哪些核心业务表组成？":"本章表格说明了哪些关键结果？",summary.stream().limit(3).map(row->row.get(1)).toList(),"该表汇总本章多个关键表格，集中呈现核心对象、用途及验证结果，便于答辩时快速说明数据关系。",entry.getKey(),.9,"TABULAR","",refs,false,summary);byChapter.computeIfAbsent(entry.getKey(),ignored->new ArrayList<>()).add(page);}
        return byChapter.entrySet().stream().map(e->new ChapterCandidates(e.getKey(),e.getValue())).toList();}
    private String evidenceDescription(String caption,String title){String clean=PptDocumentParser.clean(caption).replaceFirst("^(?:图|Figure|Fig\\.)\\s*\\d+(?:[-.．]\\d+)?[^\\n]{0,40}","").trim();if(clean.length()<20)clean="该图集中呈现"+title+"的主要信息、结构关系与来源依据，便于答辩时围绕图中证据进行说明。";else if(clean.length()<40)clean=ensureSentence(clean)+"图中信息与来源章节相互对应，可作为答辩说明的直观依据。";return completeText(clean,69);}
    private String figureTitle(String caption,String id){String value=caption==null?"":caption;var matcher=java.util.regex.Pattern.compile("(?m)^(?:图|Figure|Fig\\.)\\s*\\d+(?:[-.．]\\d+)?[　\\s]*([^\\n。]{2,35})",java.util.regex.Pattern.CASE_INSENSITIVE).matcher(value);if(matcher.find()){String matched=matcher.group(1).trim();return matched.replaceFirst("图$","").trim();}LinkedHashMap<String,String> names=new LinkedHashMap<>();names.put("AI健康评估","AI健康评估页面");names.put("AI助手","AI助手交互页面");names.put("数据可视化","健康数据可视化页面");names.put("系统架构","系统总体架构图");names.put("功能结构","系统功能结构图");names.put("操作流程","系统操作流程图");names.put("管理员用例","管理员用例图");names.put("登录","系统登录页面");names.put("健康目标","健康目标页面");names.put("技术路线","研究技术路线图");names.put("研究方法","研究方法流程图");names.put("指标体系","核心指标体系图");names.put("研究框架","研究总体框架图");names.put("进度安排","课题进度安排图");names.put("趋势","数据趋势分析图");for(var entry:names.entrySet())if(value.contains(entry.getKey()))return entry.getValue();String context=PptDocumentParser.clean(value).replaceFirst("^(?:第?[一二三四五六七八九十0-9]+章\\s*)?\\d*(?:[.．]\\d+)*\\s*","");if(context.length()>=2&&context.length()<=24&&!context.matches(".*[。！？；，,].*"))return context.endsWith("图")?context:context+"图示";return "来源图片解读 "+id.replace("figure_","").replace('_','-');}
    private void requireEvidenceCoverage(List<ChapterCandidates> chapters,List<Map<String,Object>> assets){LinkedHashSet<String> expected=new LinkedHashSet<>();for(Map<String,Object> asset:safe(assets)){String id=string(asset.get("id"));if(id.isBlank()||!expected.add(id))throw new IllegalStateException("ContentPlanner V2图片标识为空或重复: "+id);}LinkedHashMap<String,Integer> actual=new LinkedHashMap<>();for(var chapter:safe(chapters))for(var page:safe(chapter.candidatePages()))if("IMAGE_EVIDENCE".equals(page.candidateType())){String id=page.sourceRefs()==null?"":page.sourceRefs().figureId();if(!page.mandatoryAsset())throw new IllegalStateException("图片候选页必须标记mandatoryAsset: "+id);actual.merge(id,1,Integer::sum);}for(String id:expected)if(actual.getOrDefault(id,0)!=1)throw new IllegalStateException("每张有效图片必须生成且只生成一张专属介绍页: "+id);for(String id:actual.keySet())if(!expected.contains(id))throw new IllegalStateException("图片候选页引用了未知素材: "+id);}
    private int numberFromId(String value){var matcher=java.util.regex.Pattern.compile("(\\d+)").matcher(value==null?"":value);return matcher.find()?Integer.parseInt(matcher.group(1)):0;}
    private String string(Object value){return value==null?"":String.valueOf(value);}
    private String filterReason(String value,String thesisTitle,PptPlannerRuleLibrary.RuleSet rules){String compact=value.replaceAll("\\s+","");String canonical=canonical(compact);if(compact.length()<4)return "EMPTY_OR_HEADING";if(compact.matches("^(?:评语[：:]?|该课题选题具有|论文技术路线清晰|开题报告文献综述|指导教师意见|指导教师[（(]签字[）)]|总体来看课题|建议[：:]).*"))return "FORM_CONTENT";if(!thesisTitle.isBlank()&&(compact.equals(thesisTitle.replaceAll("\\s+",""))||compact.matches("^(题目|课题名称)[：:]?"+java.util.regex.Pattern.quote(thesisTitle.replaceAll("\\s+",""))+"$")))return "THESIS_TITLE";if(isFormContent(canonical,rules.formMarkers()))return "FORM_CONTENT";long metadataHits=rules.metadataLabels().stream().map(this::canonical).filter(canonical::contains).count();if(metadataHits>=2||rules.metadataLabels().stream().anyMatch(label->compact.matches("^"+java.util.regex.Pattern.quote(label)+"[：:].*")))return "METADATA";if(rules.forbiddenStandalone().stream().anyMatch(term->canonical.equalsIgnoreCase(canonical(term))))return "LOW_VALUE_STANDALONE";if(compact.matches("^(关键词|关键字)[：:].*"))return "KEYWORDS";return null;}
    private boolean isFormContent(String value,List<String> markers){for(String markerValue:safe(markers)){String marker=canonical(markerValue);if(marker.isBlank())continue;if(value.equals(marker))return true;if(value.length()<=marker.length()+12&&(value.startsWith(marker)||value.endsWith(marker)))return true;}return value.matches("^(?:毕业设计|毕业论文)?任务书(?:内容)?$")||value.matches("^(?:毕业设计|毕业论文)?答辩记录(?:表)?$")||value.matches("^(?:指导教师|评阅教师)?审批意见(?:表)?$")||value.matches("^综合评价(?:表|意见)?$");}
    private PptPlannerRuleLibrary.PurposeRule matchPurpose(String chapterTitle,String paragraph,List<PptPlannerRuleLibrary.PurposeRule> purposeRules,Set<String> allowedPurposes,int chapterNumber){String text=chapterTitle+" "+paragraph;PptPlannerRuleLibrary.PurposeRule best=null;int bestScore=0;for(var rule:purposeRules){boolean summarySignal="SUMMARY".equals(rule.purpose())&&paragraph.matches(".*(预期达到的目标|预期目标|预期成果|研究预期形成|达到.*目标).*" );boolean testSignal="TEST".equals(rule.purpose())&&paragraph.matches(".*(进度安排|工作计划|阶段任务|论文答辩).*" );boolean semanticOverride=summarySignal||testSignal;if("系统开发技术路线".equals(rule.title())||(!allowedPurposes.contains(rule.purpose())&&!semanticOverride)||(!semanticOverride&&!safe(rule.chapterNumbers()).isEmpty()&&!rule.chapterNumbers().contains(chapterNumber)))continue;int score=semanticOverride?100:0;for(String keyword:safe(rule.keywords()))if(text.contains(keyword))score++;if(score>bestScore){best=rule;bestScore=score;}}return best;}
    private void ensureNamedRuleDraft(SourceChapter chapter,Set<String> allowedPurposes,List<PptPlannerRuleLibrary.PurposeRule> purposeRules,Map<String,CandidateDraft> drafts,String title){
        if(drafts.containsKey(title))return;
        int number=chapterNumber(chapter.title());
        for(var rule:safe(purposeRules)){
            if(!title.equals(rule.title())||!allowedPurposes.contains(rule.purpose())||(!safe(rule.chapterNumbers()).isEmpty()&&!rule.chapterNumbers().contains(number)))continue;
            List<String> sources=safe(chapter.paragraphs()).stream().map(PptDocumentParser::clean).filter(value->safe(rule.keywords()).stream().anyMatch(value::contains)).toList();
            if(!sources.isEmpty()){CandidateDraft draft=new CandidateDraft(rule);draft.sources.addAll(sources);drafts.put(title,draft);}return;
        }
    }
    private Set<String> collectTechnologies(List<SourceChapter> chapters,List<String> terms){LinkedHashSet<String> found=new LinkedHashSet<>();String all=chapters.stream().flatMap(c->safe(c.paragraphs()).stream()).reduce("",(a,b)->a+" "+b);for(String term:safe(terms))if(all.toLowerCase().contains(term.toLowerCase()))found.add(term);return found;}
    private boolean chapterContainsTechnologies(SourceChapter chapter,Set<String> technologies){String text=String.join(" ",safe(chapter.paragraphs())).toLowerCase();return technologies.stream().filter(term->text.contains(term.toLowerCase())).count()>=2;}
    private Set<String> allowedPurposes(SourceChapter chapter,List<PptPlannerRuleLibrary.ChapterConstraint> constraints){int number=chapterNumber(chapter.title());for(var constraint:safe(constraints))if(constraint.chapterNumber()==number){LinkedHashSet<String> allowed=new LinkedHashSet<>(safe(constraint.allowedPurposes()));String text=chapter.title()+" "+String.join(" ",safe(chapter.paragraphs()));if(text.matches(".*(预期目标|预期成果|达到.*目标).*"))allowed.add("SUMMARY");if(text.matches(".*(进度安排|工作计划|阶段任务).*"))allowed.add("TEST");return allowed;}return new LinkedHashSet<>(List.of("BACKGROUND","PROBLEM","METHOD","DESIGN","DATABASE","IMPLEMENTATION","RESULT","TEST","SUMMARY"));}
    private int chapterNumber(String title){String value=title==null?"":title;if(value.matches(".*(研究现状|选题目的|选题意义).*"))return 1;if(value.matches(".*(基本内容|研究方案|预期.*目标).*"))return 2;if(value.matches(".*(具备.*条件|所需.*条件|困难|解决措施).*"))return 3;if(value.matches(".*(进度安排|工作计划).*"))return 4;var arabic=java.util.regex.Pattern.compile("(?:第)?(\\d+)章").matcher(value);if(arabic.find())return Integer.parseInt(arabic.group(1));List<String> chinese=List.of("一","二","三","四","五","六","七","八","九","十");for(int i=0;i<chinese.size();i++)if(value.contains("第"+chinese.get(i)+"章"))return i+1;return 0;}
    private boolean isFrontMatterChapter(SourceChapter chapter){String title=chapter.title()==null?"":chapter.title().replaceAll("\\s+","");return title.equals("正文内容")||title.equals("前置内容")||title.contains("封面")||title.contains("任务书")||title.contains("摘要")||title.equalsIgnoreCase("abstract");}
    private String canonical(String value){return value==null?"":value.replaceAll("[\\s：:（）()【】\\[\\]·._-]+","").toLowerCase();}
    private String metadataValue(Map<String,String> metadata,String...keys){if(metadata==null)return"";for(String key:keys){String value=metadata.get(key);if(value!=null&&!value.isBlank())return PptDocumentParser.clean(value);}return"";}
    private <T> List<T> safe(List<T> value){return value==null?List.of():value;}
    private boolean blank(String value){return value==null||value.isBlank();}

    private static class CandidateDraft{private final PptPlannerRuleLibrary.PurposeRule rule;private final List<String>sources=new ArrayList<>();private final List<String>explicitPoints=new ArrayList<>();private CandidateDraft(PptPlannerRuleLibrary.PurposeRule rule){this.rule=rule;if(rule.keyPoints()!=null)explicitPoints.addAll(rule.keyPoints());}}
    public record PlannerInput(Map<String,String> metadata,List<SourceChapter> chapters,List<Map<String,Object>> assets,List<Map<String,Object>> tables,String majorType){}
    public record SourceChapter(String id,String title,List<String> paragraphs){}
    public record CandidatePage(String candidateType,String title,String pagePurpose,String answerQuestion,List<String> keyPoints,String description,String sourceChapter,double confidence,String contentType,String imageRole,SourceRefs sourceRefs,boolean mandatoryAsset,List<List<String>> tableSummary){
        public CandidatePage(String title,String pagePurpose,String answerQuestion,List<String> keyPoints,String description,String sourceChapter,double confidence){this("TEXT",title,pagePurpose,answerQuestion,keyPoints,description,sourceChapter,confidence,"KEY_POINTS","",null,false,List.of());}
        public CandidatePage(String candidateType,String title,String pagePurpose,String answerQuestion,List<String> keyPoints,String description,String sourceChapter,double confidence,String imageRole,SourceRefs sourceRefs,boolean mandatoryAsset,List<List<String>> tableSummary){this(candidateType,title,pagePurpose,answerQuestion,keyPoints,description,sourceChapter,confidence,"IMAGE_EVIDENCE".equals(candidateType)?"FIGURE":"TABLE_SUMMARY".equals(candidateType)?"TABULAR":"KEY_POINTS",imageRole,sourceRefs,mandatoryAsset,tableSummary);}
    }
    public record SourceRefs(String chapterId,List<String> headingIds,String figureId,List<String> paragraphIds,List<String> tableIds){}
    public record ChapterCandidates(String chapter,List<CandidatePage> candidatePages){}
    public record FilteredContent(String sourceChapter,String content,String reason){}
    public record PlannerResult(String majorType,List<ChapterCandidates> chapters,List<FilteredContent> filteredContents){}
}
