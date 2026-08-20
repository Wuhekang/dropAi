package com.dropai.rewrite.service.ppt;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Component
public class PptContentPlannerV2InputAdapter {
    public PptContentPlannerV2.PlannerInput fromParsedDocument(PptDocumentParser.ParsedDocument document,String majorType){
        if(document==null)throw new IllegalArgumentException("DocumentParser输出不能为空");
        Set<String> headings=new LinkedHashSet<>();for(String heading:safe(document.headings())){String clean=PptDocumentParser.clean(heading);if(!clean.isBlank())headings.add(clean);}
        List<PptContentPlannerV2.SourceChapter> chapters=new ArrayList<>();String currentTitle="正文内容";List<String> paragraphs=new ArrayList<>();int chapterIndex=0,currentMajor=0;
        for(String block:safe(document.blocks())){
            String clean=PptDocumentParser.clean(block);if(clean.isBlank())continue;
            if(headings.contains(clean)){
                int major=majorChapter(clean);
                if(major>0&&major!=currentMajor){if(!paragraphs.isEmpty())chapters.add(new PptContentPlannerV2.SourceChapter("chapter_"+(++chapterIndex),currentTitle,List.copyOf(paragraphs)));currentMajor=major;currentTitle=topLevelTitle(clean,major);paragraphs.clear();}
                paragraphs.add(clean);
            }else paragraphs.add(clean);
        }
        if(!paragraphs.isEmpty())chapters.add(new PptContentPlannerV2.SourceChapter("chapter_"+(++chapterIndex),currentTitle,List.copyOf(paragraphs)));
        if(chapters.isEmpty()&&!safe(document.blocks()).isEmpty())chapters.add(new PptContentPlannerV2.SourceChapter("chapter_1","正文内容",safe(document.blocks())));
        List<Map<String,Object>> assets=new ArrayList<>();int assetIndex=0;for(PptDocumentParser.Asset asset:safe(document.assets())){Map<String,Object> value=new LinkedHashMap<>();value.put("id","asset_"+(++assetIndex));value.put("path",asset.path().toString());value.put("sourcePage",asset.sourcePage());value.put("sourcePosition",asset.sourcePosition());value.put("caption",asset.caption());assets.add(value);}
        List<Map<String,Object>> tables=new ArrayList<>();for(int i=1;i<=document.tableCount();i++)tables.add(Map.of("id","table_"+i));
        return new PptContentPlannerV2.PlannerInput(Map.of("title",resolveTitle(document)),chapters,assets,tables,majorType);
    }
    private String resolveTitle(PptDocumentParser.ParsedDocument document){
        for(String block:safe(document.blocks())){
            String clean=PptDocumentParser.clean(block);
            var matcher=java.util.regex.Pattern.compile("(?:题目|课题名称)[：:\\s]+(.{5,100}?)(?=\\s*(?:学院|专业|学生姓名|学生|学号|指导教师|教师)|$)").matcher(clean);
            if(matcher.find())return matcher.group(1).trim();
        }
        return PptDocumentParser.clean(document.title());
    }
    private int majorChapter(String value){String clean=PptDocumentParser.clean(value);var decimal=java.util.regex.Pattern.compile("^(\\d+)(?:[.．]\\d+)+").matcher(clean);if(decimal.find())return Integer.parseInt(decimal.group(1));var arabic=java.util.regex.Pattern.compile("^第?(\\d+)章").matcher(clean);if(arabic.find())return Integer.parseInt(arabic.group(1));List<String> chinese=List.of("一","二","三","四","五","六","七","八","九","十");for(int i=0;i<chinese.size();i++)if(clean.startsWith("第"+chinese.get(i)+"章"))return i+1;return 0;}
    private String topLevelTitle(String heading,int major){String clean=PptDocumentParser.clean(heading);if(clean.matches("^第?[一二三四五六七八九十0-9]+章.*"))return clean;return "第"+major+"章";}
    private <T> List<T> safe(List<T> value){return value==null?List.of():value;}
}
