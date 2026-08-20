package com.dropai.rewrite.service.ppt;

import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Turns already-understood candidate pages into an ordered defense outline.
 * It must not read the source document, infer new content, bind assets or select layouts.
 */
@Service
public class PptOutlinePlannerV1 {
    private static final int MIN_FINAL_SCORE = 70;
    private static final Map<String,Integer> PURPOSE_ORDER = Map.of(
            "BACKGROUND",10,"PROBLEM",20,"METHOD",30,"DESIGN",40,"DATABASE",45,
            "IMPLEMENTATION",50,"RESULT",60,"TEST",70,"SUMMARY",80);

    public OutlineResult plan(OutlineRequest request){
        if(request==null||request.candidatePages()==null)throw new IllegalArgumentException("OutlinePlanner候选页面不能为空");
        int maxSlides=request.maxContentSlides()<=0?12:request.maxContentSlides();
        List<MergeGroup> groups=mergeDuplicates(request.candidatePages());
        List<ScoredCandidate> scored=new ArrayList<>();
        for(int i=0;i<groups.size();i++)scored.add(score(groups.get(i),i));
        List<ScoredCandidate> retained=scored.stream().filter(c->c.score().finalScore()>=MIN_FINAL_SCORE).toList();
        LinkedHashSet<ScoredCandidate> selected=new LinkedHashSet<>(retained.stream()
                .sorted(Comparator.comparingInt((ScoredCandidate c)->c.score().finalScore()).reversed())
                .limit(maxSlides).toList());
        List<ScoredCandidate> ordered=selected.stream().sorted(Comparator
                .comparingInt((ScoredCandidate c)->PURPOSE_ORDER.getOrDefault(c.page().pagePurpose(),999))
                .thenComparing(c->chapterNumber(c.page().sourceChapter()))
                .thenComparingInt(ScoredCandidate::originalIndex)).toList();
        List<SlideNode> slides=new ArrayList<>();
        for(int i=0;i<ordered.size();i++){
            ScoredCandidate item=ordered.get(i);
            slides.add(new SlideNode(i+1,item.page().title(),item.page().pagePurpose(),item.page().answerQuestion(),
                    item.page().keyPoints(),item.page().description(),item.page().sourceChapter(),item.score(),item.mergedCandidateCount()));
        }
        List<Decision> decisions=new ArrayList<>();
        for(ScoredCandidate item:scored){
            String action=selected.contains(item)?(item.mergedCandidateCount()>1?"MERGED":"KEEP"):
                    item.score().finalScore()<MIN_FINAL_SCORE?"DELETE_LOW_SCORE":"DELETE_PAGE_LIMIT";
            decisions.add(new Decision(item.page().title(),item.page().sourceChapter(),action,item.score(),item.mergedCandidateCount()));
        }
        return new OutlineResult("CONTENT_TREE",slides.size(),slides,decisions);
    }

    private List<MergeGroup> mergeDuplicates(List<PptContentPlannerV2.CandidatePage> pages){
        LinkedHashMap<String,MergeGroup> groups=new LinkedHashMap<>();
        for(var page:pages){
            if(page==null)continue;
            String key=canonical(page.title())+"|"+canonical(page.answerQuestion());
            groups.computeIfAbsent(key,ignored->new MergeGroup()).pages.add(page);
        }
        return new ArrayList<>(groups.values());
    }

    private ScoredCandidate score(MergeGroup group,int originalIndex){
        var first=group.pages.get(0);LinkedHashSet<String> points=new LinkedHashSet<>();LinkedHashSet<String> chapters=new LinkedHashSet<>();
        String description="";double confidence=0;
        for(var page:group.pages){points.addAll(page.keyPoints()==null?List.of():page.keyPoints());chapters.add(page.sourceChapter());if(page.description()!=null&&page.description().length()>description.length())description=page.description();confidence=Math.max(confidence,page.confidence());}
        var merged=new PptContentPlannerV2.CandidatePage(first.title(),first.pagePurpose(),first.answerQuestion(),points.stream().limit(3).toList(),description,String.join(" / ",chapters),confidence);
        int contentScore=Math.min(100,45+merged.keyPoints().size()*12+Math.min(19,merged.description().length()/5));
        int answerScore=merged.answerQuestion()==null||merged.answerQuestion().isBlank()?0:Math.min(100,75+Math.min(25,merged.answerQuestion().length()));
        int duplicateScore=group.pages.size()>1?Math.min(100,(group.pages.size()-1)*20):0;
        int finalScore=(int)Math.round(contentScore*.45+answerScore*.35+confidence*20-duplicateScore*.05);
        return new ScoredCandidate(merged,new PageScore(contentScore,answerScore,duplicateScore,Math.max(0,Math.min(100,finalScore))),group.pages.size(),originalIndex);
    }

    private String canonical(String value){return value==null?"":value.replaceAll("[\\s：:，,。！？!?、._-]+","").toLowerCase(Locale.ROOT);}
    private int chapterNumber(String value){if(value==null)return 999;var matcher=java.util.regex.Pattern.compile("(\\d+)").matcher(value);if(matcher.find())return Integer.parseInt(matcher.group(1));List<String> cn=List.of("一","二","三","四","五","六","七","八","九","十");for(int i=0;i<cn.size();i++)if(value.contains(cn.get(i)))return i+1;return 999;}

    private static class MergeGroup{private final List<PptContentPlannerV2.CandidatePage> pages=new ArrayList<>();}
    private record ScoredCandidate(PptContentPlannerV2.CandidatePage page,PageScore score,int mergedCandidateCount,int originalIndex){}
    public record OutlineRequest(List<PptContentPlannerV2.CandidatePage> candidatePages,int maxContentSlides){}
    public record PageScore(int contentScore,int answerScore,int duplicateScore,int finalScore){}
    public record SlideNode(int pageNumber,String title,String pagePurpose,String answerQuestion,List<String> keyPoints,String description,String sourceChapter,PageScore score,int mergedCandidateCount){}
    public record Decision(String title,String sourceChapter,String action,PageScore score,int candidateCount){}
    public record OutlineResult(String treeType,int contentSlideCount,List<SlideNode> slideTree,List<Decision> decisions){}
}
