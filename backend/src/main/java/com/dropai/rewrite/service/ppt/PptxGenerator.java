package com.dropai.rewrite.service.ppt;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.poi.sl.usermodel.PictureData;
import org.apache.poi.sl.usermodel.TextParagraph;
import org.apache.poi.xslf.usermodel.*;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

@Service
public class PptxGenerator {
    private static final double W=960,H=540;
    private static final Color DEFAULT_INK=new Color(31,35,53),DEFAULT_PRIMARY=new Color(110,79,255),DEFAULT_SECONDARY=new Color(255,85,176),DEFAULT_MUTED=new Color(103,108,129),DEFAULT_PALE=new Color(247,245,255);
    private final PptTextValidator validator;
    private final ThreadLocal<TemplateProfile> activeTemplate=ThreadLocal.withInitial(TemplateProfile::defaultProfile);

    public PptxGenerator(PptTextValidator validator){this.validator=validator;}

    public GenerationResult generate(DeckSpec spec,Path output)throws Exception{return generate(spec,output,TemplateProfile.defaultProfile());}

    public GenerationResult generate(DeckSpec spec,Path output,TemplateProfile template)throws Exception{
        activeTemplate.set(template==null?TemplateProfile.defaultProfile():template);
        try{return generateActive(spec,output);}finally{activeTemplate.remove();}
    }

    private GenerationResult generateActive(DeckSpec spec,Path output)throws Exception{
        String source=activeTemplate.get().sourcePath();
        if(source!=null&&!source.isBlank()&&Files.isRegularFile(Path.of(source)))return generateFromTemplate(spec,output,Path.of(source));
        Files.createDirectories(output.getParent());List<String> fixes=new ArrayList<>();
        try(XMLSlideShow deck=new XMLSlideShow()){
            deck.setPageSize(new Dimension((int)W,(int)H));
            cover(deck,spec);directory(deck,spec.sections());int slideNo=2;
            for(SectionSpec section:spec.sections()){
                divider(deck,section.title(),++slideNo);
                for(SlideSpec raw:section.slides()){
                    PptTextValidator.ValidationResult checked=validator.validateSlideTextLimits(raw.title(),raw.bodyBoxes());fixes.addAll(checked.issues());
                    content(deck,new SlideSpec(checked.title(),checked.bodyBoxes(),raw.notes(),raw.assetPath(),raw.layout()),++slideNo);
                }
            }
            future(deck,spec.futureItems(),++slideNo);thanks(deck,spec,++slideNo);
            try(var out=Files.newOutputStream(output)){deck.write(out);}
        }
        verify(output,spec.sections().size());
        return new GenerationResult(output,Files.size(output),fixes.isEmpty()?"PASSED":"AUTO_FIXED",fixes);
    }

    private GenerationResult generateFromTemplate(DeckSpec spec,Path output,Path source)throws Exception{
        Files.createDirectories(output.getParent());List<String> fixes=new ArrayList<>();List<TemplateUsageLog> usageLog=new ArrayList<>();
        try(XMLSlideShow template=new XMLSlideShow(Files.newInputStream(source));XMLSlideShow deck=new XMLSlideShow()){
            deck.setPageSize(template.getPageSize());List<XSLFSlide> sourceSlides=new ArrayList<>(template.getSlides());PptTemplateService.TemplateMetadata metadata=new PptTemplateService(null,new ObjectMapper()).analyze(source);TemplateMatcher matcher=new TemplateMatcher(metadata,spec.topic());
            int sourcePage=matcher.fixed("cover"),no=1;XSLFSlide slide=copyMappedSlide(deck,sourceSlides,sourcePage,no,"cover",usageLog);fillTemplateSlide(slide,List.of(spec.topic(),spec.englishTopic(),"汇报人 "+safe(spec.presenter()),"专业 "+safe(spec.major())),null,no++,"cover",slideMetadata(metadata,sourcePage));
            List<String> directoryItems=new ArrayList<>();directoryItems.add("目录");for(SectionSpec section:spec.sections())directoryItems.add(section.title());
            sourcePage=matcher.fixed("catalog");slide=copyMappedSlide(deck,sourceSlides,sourcePage,no,"catalog",usageLog);fillTemplateSlide(slide,directoryItems,null,no++,"catalog",slideMetadata(metadata,sourcePage));
            int sectionOrder=0;for(SectionSpec section:spec.sections()){
                sectionOrder++;sourcePage=matcher.match("section");slide=copyMappedSlide(deck,sourceSlides,sourcePage,no,"section",usageLog);fillTemplateSlide(slide,List.of(String.format("%02d",sectionOrder),section.title(),englishSectionTitle(section.title())),null,no++,"section",slideMetadata(metadata,sourcePage));
                for(SlideSpec raw:section.slides()){
                    PptTextValidator.ValidationResult checked=validator.validateSlideTextLimits(raw.title(),raw.bodyBoxes());fixes.addAll(checked.issues());
                    List<String> copy=new ArrayList<>();copy.add(checked.title());copy.addAll(checked.bodyBoxes());String contentType=slideContentType(raw);String role="image_text".equals(contentType)?"image":"content";sourcePage=matcher.match(contentType);
                    slide=copyMappedSlide(deck,sourceSlides,sourcePage,no,contentType,usageLog);fillTemplateSlide(slide,copy,raw.assetPath(),no++,role,slideMetadata(metadata,sourcePage));addSpeakerNotes(slide,raw.notes());
                }
            }
            List<String> futureCopy=new ArrayList<>();futureCopy.add("未来展望");futureCopy.addAll(spec.futureItems()==null?List.of():spec.futureItems());
            sourcePage=matcher.match("text");slide=copyMappedSlide(deck,sourceSlides,sourcePage,no,"future",usageLog);fillTemplateSlide(slide,futureCopy,null,no++,"future",slideMetadata(metadata,sourcePage));
            sourcePage=matcher.fixed("thanks");slide=copyMappedSlide(deck,sourceSlides,sourcePage,no,"thanks",usageLog);fillTemplateSlide(slide,List.of("谢谢大家","THANK YOU","汇报人："+safe(spec.presenter()),"专业："+safe(spec.major()),"指导老师："+safe(spec.advisor()),"学号："+safe(spec.studentNumber())),null,no,"thanks",slideMetadata(metadata,sourcePage));
            validateTemplateUsage(usageLog,metadata);writeTemplateUsageLog(output,source,metadata,usageLog);
            try(var out=Files.newOutputStream(output)){deck.write(out);}
        }
        verify(output,spec.sections().size());
        return new GenerationResult(output,Files.size(output),fixes.isEmpty()?"PASSED":"AUTO_FIXED",fixes);
    }

    private XSLFSlide copyMappedSlide(XMLSlideShow deck,List<XSLFSlide> sourceSlides,int sourcePage,int outputPage,String contentType,List<TemplateUsageLog> log){
        if(sourcePage<1||sourcePage>sourceSlides.size())throw new IllegalArgumentException("模板页不存在: "+sourcePage);
        log.add(new TemplateUsageLog(outputPage,sourcePage,contentType));return copyTemplateVisuals(deck,sourceSlides.get(sourcePage-1),contentType);
    }
    private PptTemplateService.TemplateSlideMetadata slideMetadata(PptTemplateService.TemplateMetadata metadata,int sourcePage){return metadata.slides().stream().filter(s->s.pageNumber()==sourcePage).findFirst().orElseThrow(()->new IllegalArgumentException("模板页元数据不存在: "+sourcePage));}
    private XSLFSlide copyTemplateVisuals(XMLSlideShow deck,XSLFSlide source,String contentType){
        XSLFSlide target=deck.createSlide();Color background=source.getBackground()==null?null:source.getBackground().getFillColor();if(background!=null)target.getBackground().setFillColor(background);for(XSLFShape shape:source.getShapes())copyVisualShape(deck,target,shape,contentType);return target;
    }
    private void copyVisualShape(XMLSlideShow deck,XSLFShapeContainer target,XSLFShape shape,String contentType){
        if(shape instanceof XSLFTextShape||shape instanceof XSLFGroupShape)return;
        if(shape instanceof XSLFPictureShape picture){if(!List.of("cover","catalog","section","thanks").contains(contentType)&&isContentAsset(picture))return;try{XSLFPictureData data=deck.addPicture(picture.getPictureData().getData(),picture.getPictureData().getType());XSLFPictureShape copy=target.createPicture(data);copy.setAnchor((Rectangle2D)picture.getAnchor().clone());copy.setRotation(picture.getRotation());}catch(Exception ignored){}return;}
        if(shape instanceof XSLFAutoShape auto){try{XSLFAutoShape copy=target.createAutoShape();copy.setShapeType(auto.getShapeType());copy.setAnchor((Rectangle2D)auto.getAnchor().clone());copy.setRotation(auto.getRotation());Color fill=auto.getFillColor();if(fill!=null)copy.setFillColor(fill);Color line=auto.getLineColor();if(line!=null)copy.setLineColor(line);}catch(Exception ignored){}return;}
        if(shape instanceof XSLFFreeformShape free){try{XSLFFreeformShape copy=target.createFreeform();copy.setPath(free.getPath());copy.setAnchor((Rectangle2D)free.getAnchor().clone());Color fill=free.getFillColor();if(fill!=null)copy.setFillColor(fill);Color line=free.getLineColor();if(line!=null)copy.setLineColor(line);}catch(Exception ignored){}return;}
    }
    private boolean isContentAsset(XSLFPictureShape picture){Rectangle2D a=picture.getAnchor();if(a==null)return false;double x=a.getX()*100/W,w=a.getWidth()*100/W,h=a.getHeight()*100/H,center=x+w/2;return w*h>=550&&w>=22&&h>=18&&center>22&&center<78&&x>12&&x+w<94;}
    private String slideContentType(SlideSpec raw){String layout=safeLower(raw.layout()),title=safeLower(raw.title());if(raw.assetPath()!=null&&Files.isRegularFile(raw.assetPath()))return "image_text";if(layout.contains("chart")||title.matches(".*(图表|趋势|统计).*"))return "chart";if(layout.contains("table")||title.matches(".*(测试|结果|数据表).*"))return "table";if(layout.contains("timeline")||title.matches(".*(流程|历程|时间轴|架构).*"))return "timeline";return "text";}
    private String englishSectionTitle(String title){String value=safeLower(title);if(value.contains("概述")||value.contains("背景"))return "PROJECT OVERVIEW";if(value.contains("设计")||value.contains("架构"))return "SYSTEM DESIGN";if(value.contains("实现")||value.contains("功能"))return "SYSTEM IMPLEMENTATION";if(value.contains("测试")||value.contains("结果"))return "SYSTEM TESTING";if(value.contains("总结")||value.contains("展望"))return "CONCLUSION & OUTLOOK";return "DOCUMENT SECTION";}
    private String safeLower(String value){return value==null?"":value.toLowerCase();}
    private void validateTemplateUsage(List<TemplateUsageLog> log,PptTemplateService.TemplateMetadata metadata){
        if(log.size()<3)throw new IllegalStateException("模板映射页数不足");
        if(log.stream().filter(x->"cover".equals(x.contentType())).count()!=1||log.stream().filter(x->"catalog".equals(x.contentType())).count()!=1||log.stream().filter(x->"thanks".equals(x.contentType())).count()!=1)throw new IllegalStateException("模板映射错误：封面、目录和致谢页必须各且仅有一页");
        if(log.get(0).sourcePage()!=1||!"cover".equals(log.get(0).contentType()))throw new IllegalStateException("模板映射错误：第一页必须使用封面页");
        if(log.get(1).sourcePage()!=2||!"catalog".equals(log.get(1).contentType()))throw new IllegalStateException("模板映射错误：第二页必须使用目录页");
        TemplateUsageLog last=log.get(log.size()-1);if(!"thanks".equals(last.contentType()))throw new IllegalStateException("模板映射错误：最后一页必须使用致谢页");
        Set<Integer> fixed=new HashSet<>(List.of(1,2,last.sourcePage()));Map<Integer,String> types=new LinkedHashMap<>();for(var slide:metadata.slides())types.put(slide.pageNumber(),slide.pageType());
        for(int i=2;i<log.size()-1;i++){TemplateUsageLog item=log.get(i);if(fixed.contains(item.sourcePage()))throw new IllegalStateException("模板映射错误：正文调用了固定页 "+item.sourcePage());if(List.of("cover","catalog","thanks").contains(types.get(item.sourcePage())))throw new IllegalStateException("模板映射错误：正文调用了 "+types.get(item.sourcePage()));}
    }
    private void writeTemplateUsageLog(Path output,Path source,PptTemplateService.TemplateMetadata metadata,List<TemplateUsageLog> log)throws Exception{
        List<Map<String,Object>> mappings=new ArrayList<>();for(TemplateUsageLog item:log){PptTemplateService.TemplateSlideMetadata slide=slideMetadata(metadata,item.sourcePage());Map<String,Object> mapping=new LinkedHashMap<>();mapping.put("outputPage",item.outputPage());mapping.put("sourcePage",item.sourcePage());mapping.put("templatePageType",slide.pageType());mapping.put("contentType",item.contentType());mapping.put("layoutStyle",slide.layout());mapping.put("assets",slide.assets());mapping.put("hasImageSlot",slide.hasImageSlot());mapping.put("safeArea",slide.safeArea());mapping.put("visualCenterX",slide.visualCenterX());mapping.put("titleAlign",slide.titleAlign());mapping.put("contentAlign",slide.contentAlign());mappings.add(mapping);}
        Map<String,Object> payload=new LinkedHashMap<>();payload.put("metadataVersion","2.2-bear-priority");payload.put("template",source.toAbsolutePath().toString());payload.put("templateName",metadata.templateName());payload.put("validated",true);payload.put("mappings",mappings);
        Path path=output.resolveSibling(stripExtension(output.getFileName().toString())+"-template-mapping.json");Files.writeString(path,new ObjectMapper().writerWithDefaultPrettyPrinter().writeValueAsString(payload));
    }
    private String stripExtension(String value){int dot=value.lastIndexOf('.');return dot>0?value.substring(0,dot):value;}

    private static final class TemplateMatcher{
        private final Map<String,List<Integer>> pools=new LinkedHashMap<>();private final Set<Integer> usedBodyPages=new HashSet<>();private final int slideCount;private final int thanksPage;
        TemplateMatcher(PptTemplateService.TemplateMetadata metadata,String seed){
            slideCount=metadata.slideCount();for(String type:List.of("section","text_content","image_content","content","image","chart","timeline"))pools.put(type,new ArrayList<>());int thanks=slideCount;
            for(var slide:metadata.slides()){if("thanks".equals(slide.pageType()))thanks=slide.pageNumber();if(slide.pageNumber()>2&&!"thanks".equals(slide.pageType()))pools.computeIfAbsent(slide.pageType(),k->new ArrayList<>()).add(slide.pageNumber());}
            thanksPage=thanks;Random random=new Random((seed==null?0:seed.hashCode())*31L+metadata.templateName().hashCode());for(List<Integer> pool:pools.values())Collections.shuffle(pool,random);
        }
        int fixed(String role){return switch(role){case "cover"->1;case "catalog"->Math.min(2,slideCount);case "thanks"->thanksPage;default->throw new IllegalArgumentException("未知固定页类型: "+role);};}
        int match(String contentType){String preferred=switch(contentType){case "image_text"->"image_content";case "chart","table"->"chart";case "timeline"->"timeline";case "section"->"section";default->"text_content";};List<Integer> candidates=new ArrayList<>(pool(preferred));if(candidates.isEmpty()&&"timeline".equals(preferred))candidates=new ArrayList<>(pool("section"));if(candidates.isEmpty()&&"image_content".equals(preferred))candidates=new ArrayList<>(pool("image"));if(candidates.isEmpty()&&"text_content".equals(preferred))candidates=new ArrayList<>(pool("content"));if(candidates.isEmpty())candidates=new ArrayList<>(contentPool());if(candidates.isEmpty())throw new IllegalArgumentException("模板没有可用于正文的中间页面");Integer selected=candidates.stream().filter(i->!usedBodyPages.contains(i)).findFirst().orElse(null);if(selected==null)selected=contentPool().stream().filter(i->!usedBodyPages.contains(i)).findFirst().orElse(null);if(selected==null){usedBodyPages.clear();selected=candidates.get(0);}usedBodyPages.add(selected);return selected;}
        private List<Integer> pool(String type){return pools.getOrDefault(type,List.of());}
        private List<Integer> contentPool(){return pools.values().stream().flatMap(List::stream).filter(i->i>2&&i!=thanksPage).distinct().sorted(Comparator.naturalOrder()).toList();}
    }
    public record TemplateUsageLog(int outputPage,int sourcePage,String contentType){}

    private void fillTemplateSlide(XSLFSlide slide,List<String> values,Path image,int pageNo,String role,PptTemplateService.TemplateSlideMetadata template)throws Exception{
        for(XSLFShape shape:allShapes(slide))if(shape instanceof XSLFTextShape text)text.clearText();
        Dimension page=slide.getSlideShow().getPageSize();double sx=page.getWidth()/W,sy=page.getHeight()/H;PptTemplateService.SafeArea safe=computeSafeArea(template);double center=computeVisualCenter(template);boolean actualImage=image!=null&&Files.isRegularFile(image);LayoutFrame frame=resolveContentFrame(template,actualImage?"image_text":role);
        if("cover".equals(role)){templateText(slide,value(values,0,"未命名PPT"),70,105,730,105,38,true,sx,sy);templateText(slide,value(values,1,""),72,225,720,52,20,false,sx,sy);for(int i=2;i<values.size();i++)templateText(slide,values.get(i),72+(i-2)*310,350,285,34,16,false,sx,sy);}
        else if("catalog".equals(role)){templateText(slide,"目录",70,45,260,55,32,true,sx,sy);for(int i=1;i<values.size();i++){double x=70+((i-1)%2)*415,y=135+((i-1)/2)*82;templateText(slide,String.format("%02d  %s",i,values.get(i)),x,y,360,48,20,true,sx,sy);}}
        else if("section".equals(role)){double width=Math.min(610,(safe.right()-safe.left())*W),x=center*W-width/2;templateTextAligned(slide,value(values,0,"01"),x,118,width,62,46,true,sx,sy,TextParagraph.TextAlign.CENTER);templateTextAligned(slide,value(values,1,"章节"),x,190,width,80,38,true,sx,sy,TextParagraph.TextAlign.CENTER);templateTextAligned(slide,value(values,2,"DOCUMENT SECTION"),x+width*.12,282,width*.76,38,17,false,sx,sy,TextParagraph.TextAlign.CENTER);}
        else if("thanks".equals(role)){templateText(slide,"谢谢大家",80,115,640,78,46,true,sx,sy);templateText(slide,"THANK YOU",82,205,420,42,22,true,sx,sy);for(int i=2;i<values.size();i++){double x=i%2==0?82:480,y=315+((i-2)/2)*52;templateText(slide,values.get(i),x,y,330,30,16,false,sx,sy);}}
        else if("future".equals(role)){double width=Math.min(650,(safe.right()-safe.left())*W),x=center*W-width/2;templateTextAligned(slide,"未来展望",x,92,width,60,32,true,sx,sy,TextParagraph.TextAlign.CENTER);for(int i=1;i<values.size();i++){double itemW=(width-30)/2,itemX=x+((i-1)%2)*(itemW+30),y=185+((i-1)/2)*105;templateTextAligned(slide,String.format("%02d  %s",i,values.get(i)),itemX,y,itemW,66,19,true,sx,sy,TextParagraph.TextAlign.CENTER);}}
        else{TextParagraph.TextAlign titleAlign=resolveTitleAlignment(template,actualImage?"image_text":role);templateTextAligned(slide,value(values,0,"内容"),frame.titleX(),frame.titleY(),frame.titleW(),58,30,true,sx,sy,titleAlign);TextParagraph.TextAlign contentAlign="center".equals(template.contentAlign())&&!actualImage?TextParagraph.TextAlign.CENTER:TextParagraph.TextAlign.LEFT;for(int i=1;i<values.size();i++)templateTextAligned(slide,values.get(i),frame.bodyX(),frame.bodyY()+(i-1)*68,frame.bodyW(),52,18,i==1,sx,sy,contentAlign);if(actualImage)addPicture(slide,image,frame.imageX()*sx,frame.imageY()*sy,frame.imageW()*sx,frame.imageH()*sy);}
        double pageX=Math.min(900,safe.right()*W-20);templateTextAligned(slide,String.format("%02d",pageNo),pageX,492,42,18,10,true,sx,sy,TextParagraph.TextAlign.CENTER);
    }
    private PptTemplateService.SafeArea computeSafeArea(PptTemplateService.TemplateSlideMetadata template){return template.safeArea()==null?new PptTemplateService.SafeArea(.12,.88,.14,.86):template.safeArea();}
    private double computeVisualCenter(PptTemplateService.TemplateSlideMetadata template){PptTemplateService.SafeArea safe=computeSafeArea(template);double center=template.visualCenterX();return center>safe.left()&&center<safe.right()?center:(safe.left()+safe.right())/2;}
    private TextParagraph.TextAlign resolveTitleAlignment(PptTemplateService.TemplateSlideMetadata template,String contentType){boolean noImage=!"image_text".equals(contentType);if(noImage&&(List.of("section","future","summary").contains(contentType)||!template.hasImageSlot()))return TextParagraph.TextAlign.CENTER;return "center".equals(template.titleAlign())?TextParagraph.TextAlign.CENTER:TextParagraph.TextAlign.LEFT;}
    private LayoutFrame resolveContentFrame(PptTemplateService.TemplateSlideMetadata template,String contentType){PptTemplateService.SafeArea safe=computeSafeArea(template);double left=safe.left()*W,right=safe.right()*W,top=safe.top()*H,width=right-left;boolean image="image_text".equals(contentType);if(!image)return new LayoutFrame(left,Math.max(68,top),width,left,Math.max(155,top+80),width,0,0,0,0);boolean imageLeft=template.layout()!=null&&template.layout().contains("image-left");double gap=34,textW=width*.48,imageW=width-textW-gap;if(imageLeft)return new LayoutFrame(left+imageW+gap,Math.max(58,top),textW,left+imageW+gap,Math.max(145,top+80),textW,left,Math.max(125,top+55),imageW,285);return new LayoutFrame(left,Math.max(58,top),textW,left,Math.max(145,top+80),textW,left+textW+gap,Math.max(125,top+55),imageW,285);}
    private XSLFTextBox templateText(XSLFSlide slide,String value,double x,double y,double w,double h,double size,boolean bold,double sx,double sy){return text(slide,value,x*sx,y*sy,w*sx,h*sy,size*Math.min(sx,sy),bold,primary());}
    private XSLFTextBox templateTextAligned(XSLFSlide slide,String value,double x,double y,double w,double h,double size,boolean bold,double sx,double sy,TextParagraph.TextAlign align){XSLFTextBox box=templateText(slide,value,x,y,w,h,size,bold,sx,sy);for(XSLFTextParagraph paragraph:box.getTextParagraphs())paragraph.setTextAlign(align);return box;}
    private record LayoutFrame(double titleX,double titleY,double titleW,double bodyX,double bodyY,double bodyW,double imageX,double imageY,double imageW,double imageH){}
    private String value(List<String> values,int index,String fallback){return index<values.size()&&values.get(index)!=null&&!values.get(index).isBlank()?values.get(index):fallback;}
    private List<XSLFShape> allShapes(XSLFSlide slide){List<XSLFShape> out=new ArrayList<>();for(XSLFShape shape:slide.getShapes())collectShape(shape,out);return out;}
    private void collectShape(XSLFShape shape,List<XSLFShape> out){out.add(shape);if(shape instanceof XSLFGroupShape group)for(XSLFShape child:group.getShapes())collectShape(child,out);}

    private void cover(XMLSlideShow deck,DeckSpec spec){
        XSLFSlide s=base(deck);accent(s);
        centeredText(s,spec.topic(),150,125,660,125,42,true,ink());
        centeredText(s,spec.englishTopic(),180,255,600,50,19,false,muted());
        centeredText(s,"汇报人 "+safe(spec.presenter())+"    专业 "+safe(spec.major()),180,360,600,35,17,false,muted());
        centeredText(s,"Dokiai Academic · PRESENTATION STUDIO",230,55,500,28,12,true,primary());page(s,1);
    }

    private void directory(XMLSlideShow deck,List<SectionSpec> sections){
        XSLFSlide s=base(deck);title(s,"目录",2);int i=0;
        for(SectionSpec section:sections){double x=70+(i%2)*420,y=145+(i/2)*82;shape(s,x,y,370,60,pale());text(s,String.format("%02d",i+1),x+16,y+15,42,30,16,true,primary());text(s,validator.compact(section.title(),16),x+68,y+14,275,34,21,true,ink());i++;}
    }

    private void divider(XMLSlideShow deck,String sectionTitle,int no){
        XSLFSlide s=base(deck);shape(s,0,0,W,H,pale());shape(s,650,0,310,260,soft(primary(),0.16));
        centeredText(s,"SECTION "+String.format("%02d",no),260,145,440,30,13,true,primary());centeredText(s,validator.compact(sectionTitle,24),160,195,640,80,38,true,ink());centeredText(s,"DOCUMENT SECTION",280,292,400,35,16,false,muted());page(s,no);
    }

    private void content(XMLSlideShow deck,SlideSpec spec,int no)throws Exception{
        XSLFSlide s=base(deck);title(s,spec.title(),no);List<String> boxes=spec.bodyBoxes();boolean picture=spec.assetPath()!=null&&Files.isRegularFile(spec.assetPath());String variant=String.valueOf(activeTemplate.get().layoutVariant());boolean design=List.of("environment","visual").contains(variant);double textW=picture?(design?350:390):800;int count=Math.max(1,boxes.size());double each=Math.min(92,280.0/count);
        for(int i=0;i<boxes.size();i++){double y=135+i*(each+15);shape(s,70,y,textW,each,pale());text(s,boxes.get(i),90,y+20,textW-40,each-24,20,i==0,ink());}
        if(picture)addPicture(s,spec.assetPath(),design?455:500,design?110:125,design?440:390,design?340:300);
        if("business".equals(activeTemplate.get().layoutVariant()))shape(s,50,135,5,300,secondary());
        addSpeakerNotes(s,spec.notes());
    }

    private void future(XMLSlideShow deck,List<String> raw,int no){
        XSLFSlide s=base(deck);title(s,"未来展望",no);List<String> items=raw==null||raw.isEmpty()?List.of("持续优化使用体验","扩展智能分析能力","完善数据安全机制"):raw;
        for(int i=0;i<Math.min(4,items.size());i++){double x=70+(i%2)*410,y=145+(i/2)*125;shape(s,x,y,370,92,pale());text(s,String.valueOf(i+1),x+20,y+23,38,38,22,true,primary());text(s,validator.compact(items.get(i),20),x+76,y+23,260,38,20,true,ink());}
    }

    private void thanks(XMLSlideShow deck,DeckSpec spec,int no){
        XSLFSlide s=base(deck);accent(s);centeredText(s,"谢谢大家",180,120,600,80,48,true,ink());centeredText(s,"THANK YOU",230,210,500,45,22,true,primary());centeredText(s,"汇报人："+safe(spec.presenter())+"    专业："+safe(spec.major()),180,320,600,30,17,false,muted());centeredText(s,"指导老师："+safe(spec.advisor())+"    学号："+safe(spec.studentNumber()),180,370,600,30,17,false,muted());page(s,no);
    }

    private XSLFSlide base(XMLSlideShow deck){XSLFSlide s=deck.createSlide();s.getBackground().setFillColor(pale());return s;}
    private void title(XSLFSlide s,String value,int no){text(s,validator.compact(value,24),68,42,760,55,32,true,ink());shape(s,68,108,70,4,primary());page(s,no);}
    private void accent(XSLFSlide s){
        String variant=activeTemplate.get().layoutVariant();
        if("tech".equals(variant)){shape(s,690,0,270,540,soft(primary(),0.18));shape(s,760,330,200,210,soft(secondary(),0.25));}
        else if("environment".equals(variant)){shape(s,710,0,250,540,soft(primary(),0.13));shape(s,790,350,170,190,soft(secondary(),0.28));}
        else if("visual".equals(variant)){shape(s,720,0,240,540,soft(secondary(),0.20));shape(s,0,410,230,130,soft(primary(),0.14));}
        else if("minimal".equals(variant)){shape(s,760,0,200,540,soft(secondary(),0.10));shape(s,70,104,90,3,primary());}
        else{shape(s,690,0,270,260,soft(primary(),0.16));shape(s,790,330,170,210,soft(secondary(),0.18));}
    }
    private void page(XSLFSlide s,int no){text(s,String.format("%02d",no),875,492,40,18,10,true,new Color(165,165,178));}
    private XSLFAutoShape shape(XSLFSlide s,double x,double y,double w,double h,Color color){XSLFAutoShape a=s.createAutoShape();a.setAnchor(new Rectangle2D.Double(x,y,w,h));a.setFillColor(color);a.setLineColor(color);return a;}
    private XSLFTextBox text(XSLFSlide s,String value,double x,double y,double w,double h,double size,boolean bold,Color color){XSLFTextBox b=s.createTextBox();b.setAnchor(new Rectangle2D.Double(x,y,w,h));b.setText(value==null?"":value);b.setVerticalAlignment(org.apache.poi.sl.usermodel.VerticalAlignment.MIDDLE);b.setWordWrap(true);for(XSLFTextParagraph p:b.getTextParagraphs()){p.setTextAlign(TextParagraph.TextAlign.LEFT);p.setSpaceAfter(0d);for(XSLFTextRun r:p.getTextRuns()){r.setFontFamily(activeTemplate.get().fontFamily());r.setFontSize(size);r.setBold(bold);r.setFontColor(color);}}return b;}
    private XSLFTextBox centeredText(XSLFSlide s,String value,double x,double y,double w,double h,double size,boolean bold,Color color){XSLFTextBox box=text(s,value,x,y,w,h,size,bold,color);for(XSLFTextParagraph paragraph:box.getTextParagraphs())paragraph.setTextAlign(TextParagraph.TextAlign.CENTER);return box;}

    private void addPicture(XSLFSlide s,Path path,double x,double y,double w,double h)throws Exception{
        byte[] bytes=Files.readAllBytes(path);XSLFPictureData data=s.getSlideShow().addPicture(bytes,pictureType(path));XSLFPictureShape pic=s.createPicture(data);double iw=w,ih=h;
        try{BufferedImage img=ImageIO.read(path.toFile());if(img!=null){double scale=Math.min(w/img.getWidth(),h/img.getHeight());iw=img.getWidth()*scale;ih=img.getHeight()*scale;}}catch(Exception ignored){}
        pic.setAnchor(new Rectangle2D.Double(x+(w-iw)/2,y+(h-ih)/2,iw,ih));
    }
    private void addSpeakerNotes(XSLFSlide slide,String value){if(value==null||value.isBlank())return;try{var groups=slide.getSlideShow().getNotesSlide(slide).getTextParagraphs();for(var group:groups)for(var paragraph:group)if(paragraph.getText().isBlank()){XSLFTextRun run=paragraph.getTextRuns().isEmpty()?paragraph.addNewTextRun():paragraph.getTextRuns().get(0);run.setText(value);return;}}catch(Exception ignored){}}
    private PictureData.PictureType pictureType(Path p){String n=p.getFileName().toString().toLowerCase();if(n.endsWith(".jpg")||n.endsWith(".jpeg"))return PictureData.PictureType.JPEG;if(n.endsWith(".gif"))return PictureData.PictureType.GIF;if(n.endsWith(".bmp"))return PictureData.PictureType.BMP;return PictureData.PictureType.PNG;}

    private void verify(Path output,int sections)throws Exception{
        try(XMLSlideShow deck=new XMLSlideShow(Files.newInputStream(output))){if(deck.getSlides().size()<sections*2+4)throw new IllegalStateException("PPT页数不完整");for(XSLFSlide slide:deck.getSlides()){String slideTitle=slide.getTitle();if((slideTitle==null||slideTitle.isBlank())&&slideText(slide).isBlank())throw new IllegalStateException("存在空白页");}String beforeLast=slideText(deck.getSlides().get(deck.getSlides().size()-2));String last=slideText(deck.getSlides().get(deck.getSlides().size()-1));if(!beforeLast.contains("未来展望")||!last.contains("谢谢大家"))throw new IllegalStateException("固定结尾页校验失败");}
    }
    private String slideText(XSLFSlide slide){StringBuilder out=new StringBuilder();for(XSLFShape shape:allShapes(slide))if(shape instanceof XSLFTextShape text&&!text.getText().isBlank())out.append(text.getText()).append('\n');return out.toString();}
    private String safe(String value){return value==null||value.isBlank()?"未填写":validator.compact(value,20);}
    private Color ink(){return color(activeTemplate.get().ink(),DEFAULT_INK);}private Color primary(){return color(activeTemplate.get().primary(),DEFAULT_PRIMARY);}private Color secondary(){return color(activeTemplate.get().secondary(),DEFAULT_SECONDARY);}private Color muted(){return color(activeTemplate.get().muted(),DEFAULT_MUTED);}private Color pale(){return color(activeTemplate.get().background(),DEFAULT_PALE);}
    private Color color(String hex,Color fallback){try{return Color.decode(hex);}catch(Exception e){return fallback;}}
    private Color soft(Color c,double strength){double s=Math.max(0,Math.min(1,strength));return new Color((int)(255-(255-c.getRed())*s),(int)(255-(255-c.getGreen())*s),(int)(255-(255-c.getBlue())*s));}

    public record DeckSpec(String topic,String englishTopic,String presenter,String major,String advisor,String studentNumber,List<SectionSpec> sections,List<String> futureItems){}
    public record SectionSpec(String id,String title,List<SlideSpec> slides){}
    public record SlideSpec(String title,List<String> bodyBoxes,String notes,Path assetPath,String layout){}
    public record GenerationResult(Path path,long size,String validationStatus,List<String> autoFixes){}
    public record TemplateProfile(String style,String primary,String secondary,String ink,String muted,String background,String fontFamily,String layoutVariant,String displayName,String sourcePath){public static TemplateProfile defaultProfile(){return new TemplateProfile("SIMPLE_ACADEMIC","#6E4FFF","#FF55B0","#202438","#676C81","#F7F5FF","Microsoft YaHei","academic","PRESENTATION STUDIO",null);}}
}
