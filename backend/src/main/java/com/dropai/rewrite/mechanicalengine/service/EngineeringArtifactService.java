package com.dropai.rewrite.mechanicalengine.service;

import com.dropai.rewrite.mechanicalengine.domain.MechanicalProject;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

@Service
public class EngineeringArtifactService {
    private final ObjectMapper mapper;
    public EngineeringArtifactService(ObjectMapper mapper) { this.mapper=mapper; }

    public void generate(MechanicalProject project,Path root){
        try{
            Path analysis=root.resolve("05_Analysis"),document=root.resolve("04_Document"),drawing=root.resolve("03_Drawing");
            Files.createDirectories(analysis);Files.createDirectories(document);
            Files.writeString(analysis.resolve("stress-cloud.svg"),analysisSvg(project),StandardCharsets.UTF_8);
            createReport(project,document.resolve("Design_Report.pdf"));
            createDrawingPdf(project,drawing.resolve("projection-lines.json"),drawing.resolve("Assembly_Drawing.pdf"));
        }catch(Exception e){throw new IllegalStateException("ENGINEERING_ARTIFACT_GENERATION_FAILED: "+e.getMessage(),e);}
    }

    private void createDrawingPdf(MechanicalProject project,Path projection,Path output)throws Exception{
        JsonNode views=mapper.readTree(projection.toFile());
        try(PDDocument doc=new PDDocument()){
            PDPage page=new PDPage(PDRectangle.A3);doc.addPage(page);
            try(PDPageContentStream c=new PDPageContentStream(doc,page)){
                c.setLineWidth(.45f);c.addRect(30,30,780,520);c.stroke();
                drawView(c,views.path("front"),70,300,1.25f);drawView(c,views.path("top"),70,80,1.25f);drawView(c,views.path("right"),470,300,1.25f);
                text(c,bold(),16,55,555,"AUTOMATIC CLAMP - BREP PROJECTED DRAWING");
                text(c,regular(),9,55,48,"GB/T 14689 | GB/T 1804-m | Source: OpenCascade edge projections");
            }doc.save(output.toFile());
        }
    }
    private void drawView(PDPageContentStream c,JsonNode lines,float ox,float oy,float scale)throws Exception{
        for(JsonNode line:lines){boolean first=true;for(JsonNode point:line){float x=ox+(float)point.get(0).asDouble()*scale,y=oy+(float)point.get(1).asDouble()*scale;if(first){c.moveTo(x,y);first=false;}else c.lineTo(x,y);}if(!first)c.stroke();}
    }
    private void createReport(MechanicalProject p,Path output)throws Exception{
        try(PDDocument doc=new PDDocument()){
            PDPage page=new PDPage(PDRectangle.A4);doc.addPage(page);
            try(PDPageContentStream c=new PDPageContentStream(doc,page)){
                text(c,bold(),18,55,790,"DropAI Mechanical Design Report");int y=750;
                y=line(c,y,"1 Product: "+p.getProductName());y=line(c,y,"2 Requirement: positioning, controllable clamping, self-locking, maintenance");
                y=line(c,y,"3 Concept: trapezoidal lead-screw modular clamp");y=line(c,y,"4 Structure: base, fixed jaw, moving jaw, lead screw, handle");
                y=line(c,y,"5 Parts: "+p.getParts().size()+" validated BRep solids");y=line(c,y,"6 Materials: Q235B, 45 steel, 40Cr");
                y=line(c,y,"7 Manufacturing: CNC milling, turning, thread rolling");y=line(c,y,"8 Rule stress: "+p.getAnalysis().getMaximumStressMpa()+" MPa; safety factor "+p.getAnalysis().getSafetyFactor());
                line(c,y,"9 Conclusion: prototype and formal FEA validation are required before production.");
            }doc.save(output.toFile());
        }
    }
    private String analysisSvg(MechanicalProject p){return """
        <svg xmlns="http://www.w3.org/2000/svg" width="1000" height="620"><defs><linearGradient id="g"><stop stop-color="#174ea6"/><stop offset=".5" stop-color="#f9d423"/><stop offset="1" stop-color="#c62828"/></linearGradient></defs><rect width="1000" height="620" fill="#f7f8fa"/><text x="55" y="60" font-family="Arial" font-size="28">RULE-BASED STRESS DISTRIBUTION</text><path d="M120 240h650v130H120z" fill="url(#g)" stroke="#222"/><circle cx="720" cy="305" r="34" fill="#b71c1c"/><g font-family="Arial" font-size="20"><text x="55" y="455">Maximum stress: %.1f MPa</text><text x="55" y="490">Displacement trend: %.2f mm</text><text x="55" y="525">Safety factor: %.2f</text><text x="55" y="565">Phase 1 rule analysis; not finite-element certification.</text></g></svg>
        """.formatted(p.getAnalysis().getMaximumStressMpa(),p.getAnalysis().getDisplacementMm(),p.getAnalysis().getSafetyFactor());}
    private int line(PDPageContentStream c,int y,String s)throws Exception{text(c,regular(),11,55,y,s);return y-34;}
    private void text(PDPageContentStream c,PDType1Font f,int size,float x,float y,String s)throws Exception{c.beginText();c.setFont(f,size);c.newLineAtOffset(x,y);c.showText(s.replaceAll("[^\\x20-\\x7E]","?"));c.endText();}
    private PDType1Font regular(){return new PDType1Font(Standard14Fonts.FontName.HELVETICA);}
    private PDType1Font bold(){return new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);}
}
