package com.dropai.rewrite.service.ppt;

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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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
        Files.createDirectories(output.getParent());List<String> fixes=new ArrayList<>();
        try(XMLSlideShow template=new XMLSlideShow(Files.newInputStream(source));XMLSlideShow deck=new XMLSlideShow()){
            deck.setPageSize(template.getPageSize());Map<String,List<XSLFSlide>> roles=classifyTemplateSlides(template);
            int no=1;XSLFSlide slide=copyTemplateSlide(deck,pick(roles,"cover",0));fillTemplateSlide(slide,List.of(spec.topic(),spec.englishTopic(),"汇报人 "+safe(spec.presenter()),"专业 "+safe(spec.major())),null,no++,"cover");
            List<String> directoryItems=new ArrayList<>();directoryItems.add("目录");for(SectionSpec section:spec.sections())directoryItems.add(section.title());
            slide=copyTemplateSlide(deck,pick(roles,"catalog",1));fillTemplateSlide(slide,directoryItems,null,no++,"catalog");
            for(SectionSpec section:spec.sections()){
                slide=copyTemplateSlide(deck,pick(roles,"section",2));fillTemplateSlide(slide,List.of("SECTION",section.title(),"围绕来源文档展开"),null,no++,"section");
                for(SlideSpec raw:section.slides()){
                    PptTextValidator.ValidationResult checked=validator.validateSlideTextLimits(raw.title(),raw.bodyBoxes());fixes.addAll(checked.issues());
                    List<String> copy=new ArrayList<>();copy.add(checked.title());copy.addAll(checked.bodyBoxes());String role=raw.assetPath()!=null&&Files.isRegularFile(raw.assetPath())?"image":"content";
                    slide=copyTemplateSlide(deck,pick(roles,role,3));fillTemplateSlide(slide,copy,raw.assetPath(),no++,role);addSpeakerNotes(slide,raw.notes());
                }
            }
            List<String> futureCopy=new ArrayList<>();futureCopy.add("未来展望");futureCopy.addAll(spec.futureItems()==null?List.of():spec.futureItems());
            slide=copyTemplateSlide(deck,pick(roles,"content",Math.max(0,template.getSlides().size()-2)));fillTemplateSlide(slide,futureCopy,null,no++,"future");
            slide=copyTemplateSlide(deck,pick(roles,"thanks",template.getSlides().size()-1));fillTemplateSlide(slide,List.of("谢谢大家","THANK YOU","汇报人："+safe(spec.presenter()),"专业："+safe(spec.major()),"指导老师："+safe(spec.advisor()),"学号："+safe(spec.studentNumber())),null,no,"thanks");
            try(var out=Files.newOutputStream(output)){deck.write(out);}
        }
        verify(output,spec.sections().size());
        return new GenerationResult(output,Files.size(output),fixes.isEmpty()?"PASSED":"AUTO_FIXED",fixes);
    }

    private Map<String,List<XSLFSlide>> classifyTemplateSlides(XMLSlideShow template){
        Map<String,List<XSLFSlide>> roles=new LinkedHashMap<>();for(String role:List.of("cover","catalog","section","content","image","thanks"))roles.put(role,new ArrayList<>());
        List<XSLFSlide> slides=template.getSlides();for(int i=0;i<slides.size();i++){XSLFSlide slide=slides.get(i);String text=slideText(slide);List<XSLFShape> shapes=allShapes(slide);long pictures=shapes.stream().filter(XSLFPictureShape.class::isInstance).count();long textShapes=shapes.stream().filter(XSLFTextShape.class::isInstance).count();
            if(i==0)roles.get("cover").add(slide);if(i==slides.size()-1||text.matches("(?s).*(谢谢|THANK|致谢).*$"))roles.get("thanks").add(slide);if(text.matches("(?s).*(目录|CONTENTS|CONTENT).*$"))roles.get("catalog").add(slide);if(textShapes<=3)roles.get("section").add(slide);if(pictures>0)roles.get("image").add(slide);else if(i>0&&i<slides.size()-1)roles.get("content").add(slide);
        }return roles;
    }

    private XSLFSlide pick(Map<String,List<XSLFSlide>> roles,String role,int fallback){List<XSLFSlide> candidates=roles.get(role);if(candidates!=null&&!candidates.isEmpty())return candidates.get(0);return roles.values().stream().flatMap(List::stream).findFirst().orElseThrow(()->new IllegalArgumentException("模板中没有可用页面"));}
    private XSLFSlide copyTemplateSlide(XMLSlideShow deck,XSLFSlide source){XSLFSlide target=deck.createSlide();target.importContent(source);return target;}

    private void fillTemplateSlide(XSLFSlide slide,List<String> values,Path image,int pageNo,String role)throws Exception{
        for(XSLFShape shape:allShapes(slide))if(shape instanceof XSLFTextShape text)text.clearText();
        Dimension page=slide.getSlideShow().getPageSize();double sx=page.getWidth()/W,sy=page.getHeight()/H;
        if("cover".equals(role)){templateText(slide,value(values,0,"未命名PPT"),70,105,730,105,38,true,sx,sy);templateText(slide,value(values,1,""),72,225,720,52,20,false,sx,sy);for(int i=2;i<values.size();i++)templateText(slide,values.get(i),72+(i-2)*310,350,285,34,16,false,sx,sy);}
        else if("catalog".equals(role)){templateText(slide,"目录",70,45,260,55,32,true,sx,sy);for(int i=1;i<values.size();i++){double x=70+((i-1)%2)*415,y=135+((i-1)/2)*82;templateText(slide,String.format("%02d  %s",i,values.get(i)),x,y,360,48,20,true,sx,sy);}}
        else if("section".equals(role)){templateText(slide,"SECTION",78,145,260,28,14,true,sx,sy);templateText(slide,value(values,1,value(values,0,"章节")),78,190,720,75,38,true,sx,sy);templateText(slide,value(values,2,""),80,290,520,36,17,false,sx,sy);}
        else if("thanks".equals(role)){templateText(slide,"谢谢大家",80,115,640,78,46,true,sx,sy);templateText(slide,"THANK YOU",82,205,420,42,22,true,sx,sy);for(int i=2;i<values.size();i++){double x=i%2==0?82:480,y=315+((i-2)/2)*52;templateText(slide,values.get(i),x,y,330,30,16,false,sx,sy);}}
        else if("future".equals(role)){templateText(slide,"未来展望",68,45,700,55,32,true,sx,sy);for(int i=1;i<values.size();i++){double x=75+((i-1)%2)*410,y=150+((i-1)/2)*110;templateText(slide,String.format("%02d  %s",i,values.get(i)),x,y,360,66,19,true,sx,sy);}}
        else{templateText(slide,value(values,0,"内容"),68,42,780,58,30,true,sx,sy);double bodyW=image==null?790:420;for(int i=1;i<values.size();i++)templateText(slide,values.get(i),82,135+(i-1)*72,bodyW,54,18,i==1,sx,sy);if(image!=null&&Files.isRegularFile(image))addPicture(slide,image,535*sx,130*sy,350*sx,285*sy);}
        templateText(slide,String.format("%02d",pageNo),875,492,45,18,10,true,sx,sy);
    }
    private void templateText(XSLFSlide slide,String value,double x,double y,double w,double h,double size,boolean bold,double sx,double sy){text(slide,value,x*sx,y*sy,w*sx,h*sy,size*Math.min(sx,sy),bold,primary());}
    private String value(List<String> values,int index,String fallback){return index<values.size()&&values.get(index)!=null&&!values.get(index).isBlank()?values.get(index):fallback;}
    private List<XSLFShape> allShapes(XSLFSlide slide){List<XSLFShape> out=new ArrayList<>();for(XSLFShape shape:slide.getShapes())collectShape(shape,out);return out;}
    private void collectShape(XSLFShape shape,List<XSLFShape> out){out.add(shape);if(shape instanceof XSLFGroupShape group)for(XSLFShape child:group.getShapes())collectShape(child,out);}

    private void cover(XMLSlideShow deck,DeckSpec spec){
        XSLFSlide s=base(deck);accent(s);
        text(s,spec.topic(),70,125,820,125,42,true,ink());
        text(s,spec.englishTopic(),73,255,810,50,19,false,muted());
        text(s,"汇报人 "+safe(spec.presenter())+"    专业 "+safe(spec.major()),73,360,760,35,17,false,muted());
        text(s,"Dokiai Academic · PRESENTATION STUDIO",73,55,650,28,12,true,primary());page(s,1);
    }

    private void directory(XMLSlideShow deck,List<SectionSpec> sections){
        XSLFSlide s=base(deck);title(s,"目录",2);int i=0;
        for(SectionSpec section:sections){double x=70+(i%2)*420,y=145+(i/2)*82;shape(s,x,y,370,60,pale());text(s,String.format("%02d",i+1),x+16,y+15,42,30,16,true,primary());text(s,validator.compact(section.title(),16),x+68,y+14,275,34,21,true,ink());i++;}
    }

    private void divider(XMLSlideShow deck,String sectionTitle,int no){
        XSLFSlide s=base(deck);shape(s,0,0,W,H,pale());shape(s,650,0,310,260,soft(primary(),0.16));
        text(s,"SECTION",76,150,250,30,13,true,primary());text(s,validator.compact(sectionTitle,24),76,200,700,80,38,true,ink());text(s,"围绕来源文档展开",78,300,420,35,18,false,muted());page(s,no);
    }

    private void content(XMLSlideShow deck,SlideSpec spec,int no)throws Exception{
        XSLFSlide s=base(deck);title(s,spec.title(),no);List<String> boxes=spec.bodyBoxes();boolean picture=spec.assetPath()!=null&&Files.isRegularFile(spec.assetPath());boolean design="design".equals(activeTemplate.get().layoutVariant());double textW=picture?(design?350:390):800;int count=Math.max(1,boxes.size());double each=Math.min(92,280.0/count);
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
        XSLFSlide s=base(deck);accent(s);text(s,"谢谢大家",80,120,760,80,48,true,ink());text(s,"THANK YOU",82,210,500,45,22,true,primary());text(s,"汇报人："+safe(spec.presenter()),82,320,310,30,17,false,muted());text(s,"专业："+safe(spec.major()),480,320,310,30,17,false,muted());text(s,"指导老师："+safe(spec.advisor()),82,370,310,30,17,false,muted());text(s,"学号："+safe(spec.studentNumber()),480,370,310,30,17,false,muted());page(s,no);
    }

    private XSLFSlide base(XMLSlideShow deck){XSLFSlide s=deck.createSlide();s.getBackground().setFillColor(Color.WHITE);return s;}
    private void title(XSLFSlide s,String value,int no){text(s,validator.compact(value,24),68,42,760,55,32,true,ink());shape(s,68,108,70,4,primary());page(s,no);}
    private void accent(XSLFSlide s){
        String variant=activeTemplate.get().layoutVariant();
        if("tech".equals(variant)){shape(s,690,0,270,540,soft(primary(),0.18));shape(s,760,330,200,210,soft(secondary(),0.25));}
        else if("design".equals(variant)){shape(s,710,0,250,540,pale());shape(s,790,350,170,190,soft(secondary(),0.30));}
        else{shape(s,690,0,270,260,soft(primary(),0.16));shape(s,790,330,170,210,soft(secondary(),0.18));}
    }
    private void page(XSLFSlide s,int no){text(s,String.format("%02d",no),875,492,40,18,10,true,new Color(165,165,178));}
    private XSLFAutoShape shape(XSLFSlide s,double x,double y,double w,double h,Color color){XSLFAutoShape a=s.createAutoShape();a.setAnchor(new Rectangle2D.Double(x,y,w,h));a.setFillColor(color);a.setLineColor(color);return a;}
    private XSLFTextBox text(XSLFSlide s,String value,double x,double y,double w,double h,double size,boolean bold,Color color){XSLFTextBox b=s.createTextBox();b.setAnchor(new Rectangle2D.Double(x,y,w,h));b.setText(value==null?"":value);b.setVerticalAlignment(org.apache.poi.sl.usermodel.VerticalAlignment.MIDDLE);b.setWordWrap(true);for(XSLFTextParagraph p:b.getTextParagraphs()){p.setTextAlign(TextParagraph.TextAlign.LEFT);p.setSpaceAfter(0d);for(XSLFTextRun r:p.getTextRuns()){r.setFontFamily(activeTemplate.get().fontFamily());r.setFontSize(size);r.setBold(bold);r.setFontColor(color);}}return b;}

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
