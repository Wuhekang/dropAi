package com.dropai.rewrite.service.ppt;

import org.springframework.stereotype.Component;
import java.util.ArrayList;
import java.util.List;

@Component
public class PptTextValidator {
    public ValidationResult validateSlideTextLimits(String title,List<String> boxes){
        List<String> fixed=new ArrayList<>(); List<String> issues=new ArrayList<>();
        String safeTitle=visible(title)>24?compact(title,24):title;
        if(safeTitle==null||safeTitle.isBlank()){safeTitle="内容概览";issues.add("EMPTY_TITLE_FIXED");}
        List<String> source=boxes==null?List.of():boxes;
        for(String box:source){if(fixed.size()>=4){issues.add("BODY_BOX_COUNT_REDUCED");break;}String value=compact(box,20);if(!value.isBlank())fixed.add(value);if(visible(box)>20)issues.add("BODY_TEXT_COMPACTED");}
        return new ValidationResult(safeTitle,fixed,issues,issues.isEmpty()?"PASSED":"AUTO_FIXED");
    }
    public int visible(String value){return value==null?0:value.replaceAll("\\s+","").codePointCount(0,value.replaceAll("\\s+","").length());}
    public String compact(String value,int max){if(value==null)return"";String clean=value.replaceAll("[\\r\\n]+"," ").replaceAll("\\s+"," ").trim();if(visible(clean)<=max)return clean;int end=Math.min(clean.length(),max);return clean.substring(0,end).replaceAll("[，。；、,.!?：:]$","");}
    public record ValidationResult(String title,List<String> bodyBoxes,List<String> issues,String status){}
}
