package com.dropai.rewrite.service.ppt;

import org.springframework.stereotype.Service;
import java.util.*;

/** Deterministic layout-only stage. Copy and assets remain byte-for-byte unchanged. */
@Service
public class PptLayoutPlannerV1 {
    public static final double WIDTH=13.333, HEIGHT=7.5;

    public LayoutResult plan(PptAssetMapperV1.MappingResult input){
        if(input==null||!input.assetPlanReady())throw new IllegalArgumentException("LayoutPlanner需要可用的ASSET_MAPPED_TREE");
        List<LayoutSlide> slides=new ArrayList<>();List<LayoutIssue> issues=new ArrayList<>();
        for(var mapped:input.slideTree())slides.add(layout(mapped,issues));
        if(slides.size()!=input.pageCount())issues.add(new LayoutIssue("PAGE_COUNT_CHANGED",0,"LayoutPlanner不得增删页面"));
        boolean ready=issues.isEmpty();return new LayoutResult(ready,"LAYOUT_TREE",slides.size(),slides,issues);
    }
    private LayoutSlide layout(PptAssetMapperV1.MappedSlideNode mapped,List<LayoutIssue> issues){
        var p=mapped.page();String type=select(p,mapped.assets());List<Element> e=new ArrayList<>();
        switch(type){
            case "COVER_CENTERED"->{e.add(text("ENGLISH_TITLE",.9,.65,11.55,.45,p.payload().englishTitle(),18));e.add(text("TITLE",1.05,2.05,11.2,1.35,p.title(),34));e.add(text("META",2.1,5.65,9.15,.85,"汇报人："+p.payload().presenter()+"    专业："+p.payload().major()+"\n指导教师："+p.payload().advisor()+"    学号："+p.payload().studentNumber(),17));}
            case "AGENDA_LIST"->{e.add(title(p.title()));StringBuilder s=new StringBuilder();for(var a:p.agendaItems())s.append(String.format("%02d  %s%n",a.order(),a.title()));e.add(text("AGENDA",2.0,1.65,9.3,4.75,s.toString().trim(),24));}
            case "DIAGRAM_FULL"->{e.add(title(p.title()));addImage(e,mapped.assets(),1.05,1.25,11.25,5.25);e.add(text("CAPTION",1.1,6.55,11.1,.45,p.description(),16));}
            case "SCREENSHOT_SPLIT"->{e.add(title(p.title()));addImage(e,mapped.assets(),.7,1.35,8.1,5.4);e.add(text("POINTS",9.15,1.55,3.45,3.7,bullets(p.keyPoints()),19));e.add(text("CAPTION",9.15,5.35,3.45,1.05,p.description(),16));}
            case "PORTRAIT_FOCUS"->{e.add(title(p.title()));addImage(e,mapped.assets(),4.35,1.2,4.65,5.65);e.add(text("POINTS",.65,1.7,3.25,3.8,bullets(p.keyPoints()),18));e.add(text("CAPTION",9.45,1.7,3.2,3.8,p.description(),16));}
            case "TABLE_EDITABLE"->{e.add(title(p.title()));e.add(new Element("TABLE","TABLE",.9,1.45,11.55,4.75,"",18,null,p.tableSummary()));e.add(text("SUMMARY",1.0,6.35,11.3,.55,p.description(),16));}
            case "TEXT_SPLIT"->{e.add(title(p.title()));e.add(text("POINTS",.85,1.45,5.6,4.65,bullets(p.keyPoints()),21));e.add(text("DESCRIPTION",6.85,1.45,5.6,4.65,p.description(),19));e.add(text("QUESTION",1.0,6.35,11.3,.5,p.answerQuestion(),16));}
            case "TEST_SUMMARY","FEATURE_TRIO","SUMMARY_CARDS","TEXT_CARD"->{e.add(title(p.title()));List<String> pts=p.keyPoints()==null?List.of():p.keyPoints();double cardW=3.7;for(int i=0;i<Math.min(3,pts.size());i++)e.add(text("CARD",.75+i*4.18,1.65,cardW,3.25,pts.get(i),21));e.add(text("SUMMARY",1.0,5.55,11.3,1.0,p.description(),18));}
            case "THANKS_CENTERED"->{e.add(text("TITLE",1.1,2.35,11.1,1.15,p.title(),38));e.add(text("SUBTITLE",2.25,4.05,8.85,.6,"敬请各位老师批评指正",20));}
            default->issues.add(new LayoutIssue("LAYOUT_UNKNOWN",p.pageNumber(),type));
        }
        return new LayoutSlide(p.pageNumber(),p.pageType(),p.pagePurpose(),type,p,e,mapped.assets());
    }
    private String select(PptOutlineValidatorV1.FullSlideNode p,List<PptAssetMapperV1.AssetBinding> assets){return switch(p.pageType()){
        case "COVER"->"COVER_CENTERED";case "AGENDA"->"AGENDA_LIST";case "THANKS"->"THANKS_CENTERED";case "TABLE"->"TABLE_EDITABLE";
        case "IMAGE"->{String ratio=assets.isEmpty()?"LANDSCAPE":assets.get(0).aspectRatioType();String at=assets.isEmpty()?"DIAGRAM":assets.get(0).type();yield "PORTRAIT".equals(ratio)?"PORTRAIT_FOCUS":"DIAGRAM".equals(at)?"DIAGRAM_FULL":"SCREENSHOT_SPLIT";}
        case "SUMMARY"->"SUMMARY_CARDS";default->switch(p.pagePurpose()){case "DESIGN","DATABASE"->"TEXT_SPLIT";case "IMPLEMENTATION"->"FEATURE_TRIO";case "TEST"->"TEST_SUMMARY";default->"TEXT_CARD";};};}
    private void addImage(List<Element> out,List<PptAssetMapperV1.AssetBinding> a,double x,double y,double w,double h){if(!a.isEmpty())out.add(new Element("IMAGE","IMAGE",x,y,w,h,"",0,a.get(0),List.of()));}
    private Element title(String s){return text("TITLE",.7,.35,11.9,.65,s,30);}private Element text(String role,double x,double y,double w,double h,String value,int size){return new Element("TEXT",role,x,y,w,h,value==null?"":value,size,null,List.of());}
    private String bullets(List<String> values){if(values==null)return"";return values.stream().limit(3).map(v->"• "+v).reduce((a,b)->a+"\n\n"+b).orElse("");}
    public record Element(String type,String role,double x,double y,double width,double height,String text,int fontSize,PptAssetMapperV1.AssetBinding asset,List<List<String>> table){}
    public record LayoutSlide(int pageNumber,String pageType,String pagePurpose,String layoutType,PptOutlineValidatorV1.FullSlideNode source,List<Element> elements,List<PptAssetMapperV1.AssetBinding> assets){}
    public record LayoutIssue(String code,int pageNumber,String message){}
    public record LayoutResult(boolean renderReady,String treeType,int pageCount,List<LayoutSlide> slideTree,List<LayoutIssue> issues){}
}
