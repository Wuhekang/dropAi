package com.dropai.rewrite.service.ppt;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.poi.sl.usermodel.PictureData;
import org.apache.poi.xslf.usermodel.*;
import org.springframework.stereotype.Service;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.geom.Rectangle2D;
import java.io.*;
import java.nio.file.*;
import java.util.*;

/** Pure LAYOUT_TREE executor. Text, pictures and tables remain separate editable objects. */
@Service
public class PptRendererV1 {
    private final PptRenderValidatorV1 validator;private final ObjectMapper mapper;
    public PptRendererV1(PptRenderValidatorV1 validator,ObjectMapper mapper){this.validator=validator;this.mapper=mapper;}
    public RenderResult render(PptLayoutPlannerV1.LayoutResult tree,Path output,Path reportPath)throws Exception{if(tree==null||!tree.renderReady())throw new IllegalArgumentException("Renderer需要renderReady的LAYOUT_TREE");long started=System.currentTimeMillis();Files.createDirectories(output.toAbsolutePath().getParent());try(XMLSlideShow show=new XMLSlideShow()){show.setPageSize(new Dimension(960,540));for(var plan:tree.slideTree())renderSlide(show,plan);try(OutputStream out=Files.newOutputStream(output)){show.write(out);}}
        var report=validator.validate(tree,output,System.currentTimeMillis()-started);if(reportPath!=null){Files.createDirectories(reportPath.toAbsolutePath().getParent());mapper.writerWithDefaultPrettyPrinter().writeValue(reportPath.toFile(),report);}if(!report.valid())throw new IllegalStateException("RenderValidator失败: "+report.issues());return new RenderResult(output,report);
    }
    private void renderSlide(XMLSlideShow show,PptLayoutPlannerV1.LayoutSlide plan)throws Exception{XSLFSlide slide=show.createSlide();slide.getBackground().setFillColor(Color.WHITE);for(var e:plan.elements())switch(e.type()){case"TEXT"->text(slide,e);case"IMAGE"->image(show,slide,e);case"TABLE"->table(slide,e);default->throw new IllegalStateException("未知元素: "+e.type());};}
    private void text(XSLFSlide slide,PptLayoutPlannerV1.Element e){XSLFTextBox box=slide.createTextBox();box.setAnchor(rect(e));box.setFillColor("CARD".equals(e.role())?new Color(241,245,249):Color.WHITE);box.setLineColor("CARD".equals(e.role())?new Color(203,213,225):Color.WHITE);box.setLeftInset(10);box.setRightInset(10);box.setTopInset(6);XSLFTextParagraph p=box.addNewTextParagraph();p.setTextAlign(e.role().contains("TITLE")?org.apache.poi.sl.usermodel.TextParagraph.TextAlign.CENTER:org.apache.poi.sl.usermodel.TextParagraph.TextAlign.LEFT);XSLFTextRun run=p.addNewTextRun();run.setText(e.text());run.setFontFamily("微软雅黑");run.setFontSize((double)e.fontSize());run.setFontColor(e.role().equals("TITLE")?new Color(15,23,42):new Color(51,65,85));run.setBold(e.role().equals("TITLE")||e.role().equals("ENGLISH_TITLE"));}
    private void image(XMLSlideShow show,XSLFSlide slide,PptLayoutPlannerV1.Element e)throws Exception{var a=e.asset();byte[] bytes=Files.readAllBytes(Path.of(a.source()));String low=a.source().toLowerCase(Locale.ROOT);PictureData.PictureType type=low.endsWith(".jpg")||low.endsWith(".jpeg")?PictureData.PictureType.JPEG:PictureData.PictureType.PNG;XSLFPictureData data=show.addPicture(bytes,type);XSLFPictureShape shape=slide.createPicture(data);double boxW=e.width()*72,boxH=e.height()*72,ratio=a.aspectRatio()>0?a.aspectRatio():boxW/boxH;double w=boxW,h=w/ratio;if(h>boxH){h=boxH;w=h*ratio;}double x=e.x()*72+(boxW-w)/2,y=e.y()*72+(boxH-h)/2;shape.setAnchor(new Rectangle2D.Double(x,y,w,h));}
    private void table(XSLFSlide slide,PptLayoutPlannerV1.Element e){List<List<String>> rows=e.table()==null?List.of():e.table();if(rows.isEmpty())rows=List.of(List.of("数据表","用途"));XSLFTable t=slide.createTable(rows.size(),Math.max(2,rows.stream().mapToInt(List::size).max().orElse(2)));t.setAnchor(rect(e));for(int r=0;r<rows.size();r++)for(int c=0;c<t.getNumberOfColumns();c++){XSLFTableCell cell=t.getCell(r,c);cell.setText(c<rows.get(r).size()?rows.get(r).get(c):"");cell.setFillColor(r==0?new Color(30,64,175):new Color(248,250,252));for(var p:cell.getTextParagraphs())for(var run:p.getTextRuns()){run.setFontFamily("微软雅黑");run.setFontSize(18d);run.setFontColor(r==0?Color.WHITE:new Color(30,41,59));}}}
    private Rectangle2D rect(PptLayoutPlannerV1.Element e){return new Rectangle2D.Double(e.x()*72,e.y()*72,e.width()*72,e.height()*72);}
    public record RenderResult(Path output,PptRenderValidatorV1.Report report){}
}
