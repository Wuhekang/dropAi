package com.dropai.rewrite.mechanicalpaper;

import com.fasterxml.jackson.databind.JsonNode;
import org.apache.poi.xwpf.usermodel.*;
import org.apache.poi.util.Units;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.*;
import org.springframework.stereotype.Service;
import javax.imageio.ImageIO;
import java.nio.file.*;
import java.io.*;
import java.math.BigInteger;
import java.util.*;

@Service
public class MechanicalPaperExporter {
    private final MechanicalPaperFieldUpdater fields;
    public MechanicalPaperExporter(MechanicalPaperFieldUpdater fields) {this.fields=fields;}
    public void export(MechanicalPaperState state,Path directory) throws Exception {
        try(InputStream template=getClass().getResourceAsStream("/templates/mechanical-paper.docx")) {
            if(template==null) throw new IOException("机械论文排版母版缺失");
            try(XWPFDocument document=new XWPFDocument(template)) {
                // The source has a floating PAGE textbox on top of the normal centered PAGE field.
                // Keep the real footer paragraph/field; remove only the duplicate page-number drawing.
                for(var footer:document.getFooterList())for(var paragraph:footer.getParagraphs())for(int index=paragraph.getRuns().size()-1;index>=0;index--) {
                    var run=paragraph.getRuns().get(index);
                    if(!run.getCTR().getPictList().isEmpty()&&run.getCTR().xmlText().contains("PAGE"))paragraph.removeRun(index);
                }
                Map<String,CTPPr> roles=new HashMap<>();
                for(var p:document.getParagraphs()) if(p.getCTP().isSetPPr()) roles.put(p.getText(),(CTPPr)p.getCTP().getPPr().copy());
                while(!document.getBodyElements().isEmpty()) document.removeBodyElement(0);
                paragraph(document,roles,"{{TITLE}}",state.title);
                paragraph(document,roles,"{{FRONT}}","摘要");
                paragraph(document,roles,"{{BODY}}",state.design.path("abstract").asText());
                List<String> keywords=new ArrayList<>();state.design.path("keywords").forEach(k->keywords.add(k.asText()));
                paragraph(document,roles,"{{BODY}}","关键词："+String.join("；",keywords));
                paragraph(document,roles,"{{FRONT}}","Abstract");
                paragraph(document,roles,"{{BODY}}",state.design.path("englishAbstract").asText());
                var tocTitle=paragraph(document,roles,"{{FRONT}}","目录");tocTitle.setPageBreak(true);
                var toc=document.createParagraph();
                toc.createRun().getCTR().addNewFldChar().setFldCharType(STFldCharType.BEGIN);
                toc.createRun().getCTR().addNewInstrText().setStringValue(" TOC \\o \"1-3\" \\h \\z \\u ");
                toc.createRun().getCTR().addNewFldChar().setFldCharType(STFldCharType.SEPARATE);
                toc.createRun().setText(" ");
                toc.createRun().getCTR().addNewFldChar().setFldCharType(STFldCharType.END);
                // Real TOC field, not fake cached page numbers. Office refreshes it on open.
                document.enforceUpdateFields();
                for(int chapter=1;chapter<=6;chapter++) {
                    int equationNo=0;
                    var h1=paragraph(document,roles,"{{CHAPTER}}","第 "+chapter+" 章 "+MechanicalPaperPlan.CHAPTERS.get(chapter-1));h1.setPageBreak(true);
                    for(var section:MechanicalPaperPlan.SECTIONS) {
                        if(section.chapter()!=chapter) continue;
                        var name=MechanicalPaperService.find(state.design,"sections",section.id()).path("title").asText();
                        paragraph(document,roles,"{{SECTION}}",section.id()+" "+name);
                        if(section.id().equals("5.3")) paragraph(document,roles,"{{BODY}}","结构分析采用材料力学解析计算与定性可视化相结合的方法。分布图由生成式图像方法绘制，用于讨论载荷传递与变形趋势，数值计算以第三章解析公式为依据。");
                        JsonNode content=state.tasks.get("text:"+section.id()).content;
                        for(var text:content.path("paragraphs")) paragraph(document,roles,"{{BODY}}",text.asText());
                        for(var formula:content.path("formulas")) {
                            var p=document.createParagraph();p.setAlignment(ParagraphAlignment.CENTER);
                            var properties=p.getCTP().isSetPPr()?p.getCTP().getPPr():p.getCTP().addNewPPr();
                            properties.addNewKeepLines().setVal(true);
                            // Word interprets spaced slashes next to units inconsistently during BuildUp.
                            // An explicit division glyph preserves the calculation, without guessing fractions.
                            p.getCTP().addNewOMath().addNewR().addNewT().setStringValue(formula.asText().replace("/"," ÷ "));
                            p.createRun().setText("  （"+chapter+"‑"+(++equationNo)+"）");
                        }
                        for(var table:content.path("tables")) table(document,roles,section.id(),table);
                        for(var figure:MechanicalPaperPlan.FIGURES) if(figure.section().equals(section.id())) {
                            var image=state.tasks.get("image:"+figure.id());
                            if(!"completed".equals(image.status) || image.file.isBlank()) throw new IOException("配图缺失："+figure.id());
                            String caption=figure.kind().equals("photo")?"应用实物图":cleanCaption(MechanicalPaperService.find(state.design,"figures",figure.id()).path("caption").asText());
                            picture(document,roles,directory.resolve(image.file),"图 "+figure.id()+" "+caption,figure.kind().equals("photo")?340:411);
                        }
                    }
                }
                Path output=directory.resolve("paper.docx"),temporary=directory.resolve("paper.building.docx");
                try(OutputStream out=Files.newOutputStream(temporary)) {document.write(out);}
                Files.move(temporary,output,StandardCopyOption.REPLACE_EXISTING);
                if(fields!=null) fields.update(output);
            }
        }
    }
    private XWPFParagraph paragraph(XWPFDocument doc,Map<String,CTPPr> roles,String role,String text) {
        var p=doc.createParagraph();if(roles.containsKey(role)) p.getCTP().setPPr((CTPPr)roles.get(role).copy());
        var run=p.createRun();run.setText(text);run.setColor("000000");
        if(role.equals("{{BODY}}")) {
            var properties=p.getCTP().getPPr();
            var widow=properties.isSetWidowControl()?properties.getWidowControl():properties.addNewWidowControl();widow.setVal(true);
        }
        run.setFontFamily("宋体");run.setFontSize(role.equals("{{TITLE}}")?18:role.equals("{{CHAPTER}}")?16:role.equals("{{SECTION}}")?14:role.equals("{{CAPTION}}")?10.5:12);
        run.setBold(List.of("{{TITLE}}","{{CHAPTER}}","{{SECTION}}","{{FRONT}}").contains(role));
        if(role.equals("{{CHAPTER}}") || role.equals("{{SECTION}}")) {
            var properties=p.getCTP().getPPr();
            var level=properties.isSetOutlineLvl()?properties.getOutlineLvl():properties.addNewOutlineLvl();
            level.setVal(BigInteger.valueOf(role.equals("{{CHAPTER}}")?0:1));
        }
        if(role.equals("{{FRONT}}")) {
            p.setStyle(doc.getStyles().getStyleWithName("Normal").getStyleId());
            if(p.getCTP().getPPr().isSetOutlineLvl())p.getCTP().getPPr().unsetOutlineLvl();
        }
        return p;
    }
    private void picture(XWPFDocument doc,Map<String,CTPPr> roles,Path file,String caption,double maximumWidth) throws Exception {
        var bitmap=ImageIO.read(file.toFile());if(bitmap==null) throw new IOException("图片无法读取");
        double width=maximumWidth,height=width*bitmap.getHeight()/bitmap.getWidth();
        if(height>365) {width*=365/height;height=365;}
        var p=doc.createParagraph();p.setAlignment(ParagraphAlignment.CENTER);p.setKeepNext(true);p.setSpacingAfter(0);
        try(InputStream input=Files.newInputStream(file)) {
            p.createRun().addPicture(input,file.toString().endsWith(".jpg")?Document.PICTURE_TYPE_JPEG:Document.PICTURE_TYPE_PNG,
                caption,Units.toEMU(width),Units.toEMU(height));
        }
        paragraph(doc,roles,"{{CAPTION}}",caption);
    }
    private void table(XWPFDocument doc,Map<String,CTPPr> roles,String section,JsonNode data) {
        var caption=paragraph(doc,roles,"{{CAPTION}}","表 "+section.charAt(0)+"-1 "+cleanCaption(data.path("caption").asText()));caption.setKeepNext(true);
        int columns=data.path("headers").size();
        XWPFTable table=doc.createTable(data.path("rows").size()+1,columns);table.setWidth("100%");
        var borders=table.getCTTbl().getTblPr().addNewTblBorders();
        borders.addNewTop().setVal(STBorder.SINGLE);borders.getTop().setSz(BigInteger.valueOf(8));
        borders.addNewBottom().setVal(STBorder.SINGLE);borders.getBottom().setSz(BigInteger.valueOf(8));
        borders.addNewLeft().setVal(STBorder.NIL);borders.addNewRight().setVal(STBorder.NIL);borders.addNewInsideH().setVal(STBorder.NIL);borders.addNewInsideV().setVal(STBorder.NIL);
        for(int row=0;row<table.getNumberOfRows();row++) {
            var r=table.getRow(row);if(row==0)r.setRepeatHeader(true);
            for(int col=0;col<columns;col++) {
                var cell=r.getCell(col);cell.setVerticalAlignment(XWPFTableCell.XWPFVertAlign.CENTER);
                var p=cell.getParagraphs().get(0);p.setAlignment(ParagraphAlignment.CENTER);p.setSpacingAfter(40);p.setSpacingBefore(40);
                var run=p.createRun();run.setFontFamily("宋体");run.setFontSize(10.5);run.setBold(false);
                run.setText((row==0?data.path("headers").path(col):data.path("rows").path(row-1).path(col)).asText());
                if(row==0) {var cb=cell.getCTTc().addNewTcPr().addNewTcBorders();cb.addNewBottom().setVal(STBorder.SINGLE);cb.getBottom().setSz(BigInteger.valueOf(6));}
            }
        }
        doc.createParagraph();
    }
    static String cleanCaption(String text) {return text.replaceFirst("^(?:\\s*[图表]\\s*\\d+\\s*[-－—.]\\s*\\d+\\s*)+","").trim();}
}
