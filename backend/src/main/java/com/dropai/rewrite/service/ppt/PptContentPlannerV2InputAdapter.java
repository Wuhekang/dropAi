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
        List<PptContentPlannerV2.SourceChapter> chapters=new ArrayList<>();String currentTitle="正文内容";List<String> paragraphs=new ArrayList<>();int chapterIndex=0;
        for(String block:safe(document.blocks())){
            String clean=PptDocumentParser.clean(block);if(clean.isBlank())continue;
            if(headings.contains(clean)){
                if(!paragraphs.isEmpty())chapters.add(new PptContentPlannerV2.SourceChapter("chapter_"+(++chapterIndex),currentTitle,List.copyOf(paragraphs)));
                currentTitle=clean;paragraphs.clear();
            }else paragraphs.add(clean);
        }
        if(!paragraphs.isEmpty())chapters.add(new PptContentPlannerV2.SourceChapter("chapter_"+(++chapterIndex),currentTitle,List.copyOf(paragraphs)));
        if(chapters.isEmpty()&&!safe(document.blocks()).isEmpty())chapters.add(new PptContentPlannerV2.SourceChapter("chapter_1","正文内容",safe(document.blocks())));
        List<Map<String,Object>> assets=new ArrayList<>();int assetIndex=0;for(PptDocumentParser.Asset asset:safe(document.assets())){Map<String,Object> value=new LinkedHashMap<>();value.put("id","asset_"+(++assetIndex));value.put("path",asset.path().toString());value.put("sourcePage",asset.sourcePage());value.put("sourcePosition",asset.sourcePosition());value.put("caption",asset.caption());assets.add(value);}
        List<Map<String,Object>> tables=new ArrayList<>();for(int i=1;i<=document.tableCount();i++)tables.add(Map.of("id","table_"+i));
        return new PptContentPlannerV2.PlannerInput(Map.of("title",document.title()),chapters,assets,tables,majorType);
    }
    private <T> List<T> safe(List<T> value){return value==null?List.of():value;}
}
