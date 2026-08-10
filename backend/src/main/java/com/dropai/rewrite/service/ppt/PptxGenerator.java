package com.dropai.rewrite.service.ppt;

import org.apache.poi.sl.usermodel.PictureData;
import org.apache.poi.sl.usermodel.TextParagraph;
import org.apache.poi.xslf.usermodel.XMLSlideShow;
import org.apache.poi.xslf.usermodel.XSLFAutoShape;
import org.apache.poi.xslf.usermodel.XSLFPictureData;
import org.apache.poi.xslf.usermodel.XSLFPictureShape;
import org.apache.poi.xslf.usermodel.XSLFShape;
import org.apache.poi.xslf.usermodel.XSLFSlide;
import org.apache.poi.xslf.usermodel.XSLFTextBox;
import org.apache.poi.xslf.usermodel.XSLFTextParagraph;
import org.apache.poi.xslf.usermodel.XSLFTextRun;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

@Service
public class PptxGenerator {
    private static final double W=960,H=540; private static final Color INK=new Color(31,35,53),PURPLE=new Color(110,79,255),PINK=new Color(255,85,176),MUTED=new Color(103,108,129),PALE=new Color(247,245,255);
    private final PptTextValidator validator;
    public PptxGenerator(PptTextValidator validator){this.validator=validator;}

    public GenerationResult generate(DeckSpec spec,Path output)throws Exception{
        Files.createDirectories(output.getParent()); List<String> fixes=new ArrayList<>();
        try(XMLSlideShow deck=new XMLSlideShow()){
            deck.setPageSize(new Dimension((int)W,(int)H));
            cover(deck,spec); directory(deck,spec.sections());
            int slideNo=2;
            for(SectionSpec section:spec.sections()){
                slideNo++; divider(deck,section.title(),slideNo);
                for(SlideSpec raw:section.slides()){
                    PptTextValidator.ValidationResult checked=validator.validateSlideTextLimits(raw.title(),raw.bodyBoxes()); fixes.addAll(checked.issues());
                    slideNo++; content(deck,new SlideSpec(checked.title(),checked.bodyBoxes(),raw.notes(),raw.assetPath(),raw.layout()),slideNo);
                }
            }
            slideNo++; future(deck,spec.futureItems(),slideNo); slideNo++; thanks(deck,spec,slideNo);
            try(var out=Files.newOutputStream(output)){deck.write(out);}
        }
        verify(output,spec.sections().size());
        return new GenerationResult(output,Files.size(output),fixes.isEmpty()?"PASSED":"AUTO_FIXED",fixes);
    }

    private void cover(XMLSlideShow deck,DeckSpec spec){XSLFSlide s=base(deck);accent(s);text(s,spec.topic(),70,125,820,125,42,true,INK);text(s,spec.englishTopic(),73,255,810,50,19,false,MUTED);text(s,"汇报人 "+safe(spec.presenter())+"    专业 "+safe(spec.major()),73,360,760,35,17,false,MUTED);text(s,"Dokiai Academic · PRESENTATION STUDIO",73,55,650,28,12,true,PURPLE);page(s,1);}
    private void directory(XMLSlideShow deck,List<SectionSpec> sections){XSLFSlide s=base(deck);title(s,"目录",2);int i=0;for(SectionSpec section:sections){double x=70+(i%2)*420,y=145+(i/2)*82;shape(s,x,y,370,60,new Color(249,247,255));text(s,String.format("%02d",i+1),x+16,y+15,42,30,16,true,PURPLE);text(s,validator.compact(section.title(),16),x+68,y+14,275,34,21,true,INK);i++;}}
    private void divider(XMLSlideShow deck,String title,int no){XSLFSlide s=base(deck);shape(s,0,0,W,H,new Color(245,242,255));shape(s,650,0,310,260,new Color(234,225,255));text(s,"SECTION",76,150,250,30,13,true,PURPLE);text(s,validator.compact(title,24),76,200,700,80,38,true,INK);text(s,"围绕来源文档展开",78,300,420,35,18,false,MUTED);page(s,no);}
    private void content(XMLSlideShow deck,SlideSpec spec,int no)throws Exception{XSLFSlide s=base(deck);title(s,spec.title(),no);List<String> boxes=spec.bodyBoxes();boolean picture=spec.assetPath()!=null&&Files.isRegularFile(spec.assetPath());double textW=picture?390:800;int count=Math.max(1,boxes.size());double each=Math.min(92,280.0/count);for(int i=0;i<boxes.size();i++){double y=135+i*(each+15);shape(s,70,y,textW,each,new Color(249,248,253));text(s,boxes.get(i),90,y+20,textW-40,each-24,20,i==0,INK);}if(picture)addPicture(s,spec.assetPath(),500,125,390,300);addSpeakerNotes(s,spec.notes());}
    private void future(XMLSlideShow deck,List<String> raw,int no){XSLFSlide s=base(deck);title(s,"未来展望",no);List<String> items=raw==null||raw.isEmpty()?List.of("持续优化使用体验","扩展智能分析能力","完善数据安全机制"):raw;for(int i=0;i<Math.min(4,items.size());i++){double x=70+(i%2)*410,y=145+(i/2)*125;shape(s,x,y,370,92,new Color(248,246,255));text(s,String.valueOf(i+1),x+20,y+23,38,38,22,true,PURPLE);text(s,validator.compact(items.get(i),20),x+76,y+23,260,38,20,true,INK);}}
    private void thanks(XMLSlideShow deck,DeckSpec spec,int no){XSLFSlide s=base(deck);accent(s);text(s,"谢谢大家",80,120,760,80,48,true,INK);text(s,"THANK YOU",82,210,500,45,22,true,PURPLE);text(s,"汇报人："+safe(spec.presenter()),82,320,310,30,17,false,MUTED);text(s,"专业："+safe(spec.major()),480,320,310,30,17,false,MUTED);text(s,"指导老师："+safe(spec.advisor()),82,370,310,30,17,false,MUTED);text(s,"学号："+safe(spec.studentNumber()),480,370,310,30,17,false,MUTED);page(s,no);}
    private XSLFSlide base(XMLSlideShow d){XSLFSlide s=d.createSlide();s.getBackground().setFillColor(Color.WHITE);return s;}
    private void title(XSLFSlide s,String title,int no){text(s,validator.compact(title,24),68,42,760,55,32,true,INK);shape(s,68,108,70,4,PURPLE);page(s,no);}
    private void accent(XSLFSlide s){shape(s,690,0,270,260,new Color(239,232,255));shape(s,790,330,170,210,new Color(255,231,246));}
    private void page(XSLFSlide s,int no){text(s,String.format("%02d",no),875,492,40,18,10,true,new Color(165,165,178));}
    private XSLFAutoShape shape(XSLFSlide s,double x,double y,double w,double h,Color color){XSLFAutoShape a=s.createAutoShape();a.setAnchor(new Rectangle2D.Double(x,y,w,h));a.setFillColor(color);a.setLineColor(color);return a;}
    private XSLFTextBox text(XSLFSlide s,String value,double x,double y,double w,double h,double size,boolean bold,Color color){XSLFTextBox b=s.createTextBox();b.setAnchor(new Rectangle2D.Double(x,y,w,h));b.setText(value==null?"":value);b.setVerticalAlignment(org.apache.poi.sl.usermodel.VerticalAlignment.MIDDLE);b.setWordWrap(true);for(XSLFTextParagraph p:b.getTextParagraphs()){p.setTextAlign(TextParagraph.TextAlign.LEFT);p.setSpaceAfter(0d);for(XSLFTextRun r:p.getTextRuns()){r.setFontFamily("Microsoft YaHei");r.setFontSize(size);r.setBold(bold);r.setFontColor(color);}}return b;}
    private void addPicture(XSLFSlide s,Path path,double x,double y,double w,double h)throws Exception{byte[] bytes=Files.readAllBytes(path);PictureData.PictureType type=pictureType(path);XSLFPictureData data=s.getSlideShow().addPicture(bytes,type);XSLFPictureShape pic=s.createPicture(data);double iw=w,ih=h;try{BufferedImage img=ImageIO.read(path.toFile());if(img!=null){double scale=Math.min(w/img.getWidth(),h/img.getHeight());iw=img.getWidth()*scale;ih=img.getHeight()*scale;}}catch(Exception ignored){}pic.setAnchor(new Rectangle2D.Double(x+(w-iw)/2,y+(h-ih)/2,iw,ih));}
    private void addSpeakerNotes(XSLFSlide slide,String value){if(value==null||value.isBlank())return;try{var groups=slide.getSlideShow().getNotesSlide(slide).getTextParagraphs();for(var group:groups)for(var paragraph:group)if(paragraph.getText().isBlank()){XSLFTextRun run=paragraph.getTextRuns().isEmpty()?paragraph.addNewTextRun():paragraph.getTextRuns().get(0);run.setText(value);return;}}catch(Exception ignored){}}
    private PictureData.PictureType pictureType(Path p){String n=p.getFileName().toString().toLowerCase();if(n.endsWith(".jpg")||n.endsWith(".jpeg"))return PictureData.PictureType.JPEG;if(n.endsWith(".gif"))return PictureData.PictureType.GIF;if(n.endsWith(".bmp"))return PictureData.PictureType.BMP;return PictureData.PictureType.PNG;}
    private void verify(Path output,int sections)throws Exception{try(XMLSlideShow deck=new XMLSlideShow(Files.newInputStream(output))){if(deck.getSlides().size()<sections*2+4)throw new IllegalStateException("PPT页数不完整");for(XSLFSlide slide:deck.getSlides()){String title=slide.getTitle();if((title==null||title.isBlank())&&slideText(slide).isBlank())throw new IllegalStateException("存在空白页");}String beforeLast=slideText(deck.getSlides().get(deck.getSlides().size()-2));String last=slideText(deck.getSlides().get(deck.getSlides().size()-1));if(!beforeLast.contains("未来展望")||!last.contains("谢谢大家"))throw new IllegalStateException("固定结尾页校验失败");}}
    private String slideText(XSLFSlide slide){StringBuilder out=new StringBuilder();for(XSLFShape shape:slide.getShapes())if(shape instanceof org.apache.poi.xslf.usermodel.XSLFTextShape text&&!text.getText().isBlank())out.append(text.getText()).append('\n');return out.toString();}
    private String safe(String v){return v==null||v.isBlank()?"未填写":validator.compact(v,20);}

    public record DeckSpec(String topic,String englishTopic,String presenter,String major,String advisor,String studentNumber,List<SectionSpec> sections,List<String> futureItems){}
    public record SectionSpec(String id,String title,List<SlideSpec> slides){}
    public record SlideSpec(String title,List<String> bodyBoxes,String notes,Path assetPath,String layout){}
    public record GenerationResult(Path path,long size,String validationStatus,List<String> autoFixes){}
}
