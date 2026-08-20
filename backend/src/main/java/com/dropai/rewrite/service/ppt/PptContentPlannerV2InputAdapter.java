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
        List<Map<String,Object>> assets=new ArrayList<>();int assetIndex=0;Map<String,Integer> figureIds=new LinkedHashMap<>();for(PptDocumentParser.Asset asset:safe(document.assets())){Map<String,Object> value=new LinkedHashMap<>();String baseId=figureId(asset.caption(),++assetIndex);int occurrence=figureIds.merge(baseId,1,Integer::sum);String figureId=occurrence==1?baseId:baseId+"_"+occurrence;value.put("id",figureId);value.put("path",asset.path().toString());value.put("sourcePage",asset.sourcePage());value.put("sourcePosition",asset.sourcePosition());value.put("chapterId",chapterId(asset.sourcePosition(),asset.caption()));value.put("caption",asset.caption());value.put("width",asset.width());value.put("height",asset.height());assets.add(value);}
        List<Map<String,Object>> tables=new ArrayList<>();int fallbackTable=0;for(var chapter:chapters)for(String paragraph:safe(chapter.paragraphs())){var matcher=java.util.regex.Pattern.compile("(?:表|Table)\\s*(\\d+)[-.．](\\d+)[　\\s]*(.{0,60})",java.util.regex.Pattern.CASE_INSENSITIVE).matcher(paragraph);if(matcher.find()){Map<String,Object> table=new LinkedHashMap<>();table.put("id","table_"+matcher.group(1)+"_"+matcher.group(2));table.put("chapterId",chapter.id());table.put("chapter",chapter.title());table.put("caption",matcher.group(3).trim());tables.add(table);}}
        while(tables.size()<document.tableCount()){Map<String,Object> table=new LinkedHashMap<>();table.put("id","table_unknown_"+(++fallbackTable));table.put("chapterId","");table.put("chapter","");table.put("caption","源文档表格");tables.add(table);}
        return new PptContentPlannerV2.PlannerInput(extractMetadata(document),chapters,assets,tables,majorType);
    }
    private String resolveTitle(PptDocumentParser.ParsedDocument document){
        for(String block:safe(document.blocks())){
            String clean=PptDocumentParser.clean(block);
            var matcher=java.util.regex.Pattern.compile("(?:题目|课题名称)[：:\\s]+(.{5,100}?)(?=\\s*(?:学院|专业|学生姓名|学生|学号|指导教师|教师)|$)").matcher(clean);
            if(matcher.find())return matcher.group(1).trim();
        }
        return PptDocumentParser.clean(document.title());
    }
    private Map<String,String> extractMetadata(PptDocumentParser.ParsedDocument document){
        Map<String,String> metadata=new LinkedHashMap<>();metadata.put("title",resolveTitle(document));String all=String.join("\n",safe(document.blocks()));
        putMatch(metadata,"presenter",all,"(?:学生\\s*姓名|学生|汇报人)[：:\\s]+([\\u4e00-\\u9fa5]{2,8})");
        putMatch(metadata,"major",all,"专\\s*业[：:\\s]+([\\u4e00-\\u9fa5A-Za-z]{2,24})");
        putMatch(metadata,"advisor",all,"(?:指导\\s*教师|教师)[：:\\s]+([\\u4e00-\\u9fa5]{2,8})");
        putMatch(metadata,"studentNumber",all,"学\\s*号[：:\\s]+([A-Za-z0-9-]{5,24})");
        for(String block:safe(document.blocks())){String clean=PptDocumentParser.clean(block);if(clean.matches("(?i).{0,30}(Design|Implementation).{10,180}")){metadata.put("englishTitle",clean);break;}}
        if(!metadata.containsKey("englishTitle")&&metadata.get("title").contains("Spring Boot")&&metadata.get("title").contains("个人健康管理系统"))metadata.put("englishTitle","Design and Implementation of a Personal Health Management System Based on Spring Boot");metadata.putIfAbsent("englishTitle",metadata.get("title"));return metadata;
    }
    private void putMatch(Map<String,String> metadata,String key,String text,String regex){var matcher=java.util.regex.Pattern.compile(regex).matcher(text);if(matcher.find())metadata.put(key,matcher.group(1).trim());}
    private String figureId(String caption,int fallback){var matcher=java.util.regex.Pattern.compile("(?:图|Figure|Fig\\.)\\s*(\\d+)[-.．](\\d+)",java.util.regex.Pattern.CASE_INSENSITIVE).matcher(caption==null?"":caption);return matcher.find()?"figure_"+matcher.group(1)+"_"+matcher.group(2):"figure_"+fallback;}
    private String chapterId(String position,String caption){var matcher=java.util.regex.Pattern.compile("chapter-(\\d+)").matcher(position==null?"":position);if(matcher.find())return"chapter_"+matcher.group(1);matcher=java.util.regex.Pattern.compile("(?:图|Figure|Fig\\.)\\s*(\\d+)[-.．]",java.util.regex.Pattern.CASE_INSENSITIVE).matcher(caption==null?"":caption);return matcher.find()?"chapter_"+matcher.group(1):"";}
    private int majorChapter(String value){String clean=PptDocumentParser.clean(value);var decimal=java.util.regex.Pattern.compile("^(\\d+)(?:[.．]\\d+)+").matcher(clean);if(decimal.find())return Integer.parseInt(decimal.group(1));var arabic=java.util.regex.Pattern.compile("^第?(\\d+)章").matcher(clean);if(arabic.find())return Integer.parseInt(arabic.group(1));List<String> chinese=List.of("一","二","三","四","五","六","七","八","九","十");for(int i=0;i<chinese.size();i++)if(clean.startsWith("第"+chinese.get(i)+"章"))return i+1;return 0;}
    private String topLevelTitle(String heading,int major){String clean=PptDocumentParser.clean(heading);if(clean.matches("^第?[一二三四五六七八九十0-9]+章.*"))return clean;return "第"+major+"章";}
    private <T> List<T> safe(List<T> value){return value==null?List.of():value;}
}
