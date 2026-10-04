package com.dropai.rewrite.mechanicalpaper;

import com.fasterxml.jackson.databind.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.apache.poi.xwpf.usermodel.*;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.nio.file.*;
import java.net.URI;
import java.util.*;
import java.util.zip.ZipFile;
import static org.junit.jupiter.api.Assertions.*;

class MechanicalPaperTest {
    ObjectMapper mapper=new ObjectMapper();
    @TempDir Path temporary;
    @Test void fixedPlacementMatchesAcceptedSample() {
        assertEquals(1,MechanicalPaperPlan.FIGURES.stream().filter(f->f.section().startsWith("1.")).count());
        assertEquals("photo",MechanicalPaperPlan.FIGURES.get(0).kind());
        assertEquals(4,MechanicalPaperPlan.FIGURES.stream().filter(f->f.kind().equals("part")).count());
        assertEquals(4,MechanicalPaperPlan.FIGURES.stream().filter(f->List.of("stress","displacement").contains(f.kind())).count());
        assertEquals(15,MechanicalPaperPlan.FIGURES.size());
        int target=MechanicalPaperPlan.SECTIONS.stream().mapToInt(MechanicalPaperPlan.Section::words).sum();
        assertTrue(target>=7600 && target<=8400);
        assertTrue(Math.ceil(target*1.06)<=9000);
        assertTrue(Math.floor(target*.90)>=6000);
    }
    @Test void cloudsDoNotInventSolverScreenshots() throws Exception {
        JsonNode design=mapper.readTree("{\"inputs\":\"最大应力82MPa\",\"parts\":[],\"figures\":[{\"id\":\"5-4\",\"caption\":\"承载件应力分布\",\"description\":\"最大值82MPa\"}]}");
        String prompt=MechanicalPaperService.figurePrompt(design,MechanicalPaperPlan.FIGURES.get(11));
        assertTrue(prompt.contains("浅灰白背景"));assertTrue(prompt.contains("竖直彩虹色标"));
        assertTrue(prompt.contains("不得出现任何数字"));assertTrue(prompt.contains("定性分析"));
        assertFalse(prompt.contains("82MPa"));assertFalse(prompt.contains("设计参数"));
    }
    @Test void downloadsRejectPrivateAddresses() {
        for(String url:List.of("http://127.0.0.1/a.png","http://192.168.1.3/img","http://169.254.169.254/latest","http://[::1]/","file:///x","http://user:pw@example.com/a"))
            assertThrows(Exception.class,()->MechanicalPaperPhotoService.validatePublic(URI.create(url)));
    }
    @Test void photoLinksPreferMetadataAndSkipLogos() {
        List<URI> images=MechanicalPaperPhotoService.imageCandidates(URI.create("https://example.com/product/a"),"<meta content='/real.jpg' property='og:image'><img src='/logo.jpg' alt='logo'><img src='/fixture.png'>");
        assertEquals(List.of(URI.create("https://example.com/real.jpg"),URI.create("https://example.com/fixture.png")),images);
    }
    @Test void searchImageSourcesAreNotParsedAsHtml() throws Exception {
        var output=new java.io.ByteArrayOutputStream();ImageIO.write(new BufferedImage(400,300,BufferedImage.TYPE_INT_RGB),"png",output);
        var source=URI.create("https://public.example/photo.png");
        assertEquals(List.of(source),MechanicalPaperPhotoService.sourceCandidates(source,output.toByteArray()));
    }
    @Test void sectionChecksRejectMalformedTablesAndMissingEquations() throws Exception {
        var section=MechanicalPaperPlan.SECTIONS.stream().filter(s->s.id().equals("3.1")).findFirst().orElseThrow();
        assertThrows(IllegalStateException.class,()->MechanicalPaperService.validateSection(mapper.readTree("{\"paragraphs\":[\"分析\"],\"formulas\":[],\"tables\":[]}"),section));
        final var intro=MechanicalPaperPlan.SECTIONS.get(0);
        assertThrows(IllegalStateException.class,()->MechanicalPaperService.validateSection(mapper.readTree("{\"paragraphs\":[\"资料来源待补充\"],\"formulas\":[],\"tables\":[]}"),intro));
        assertThrows(IllegalStateException.class,()->MechanicalPaperService.validateSection(mapper.readTree("{\"paragraphs\":[\"通过三维建模和有限元分析验证结构\"],\"formulas\":[],\"tables\":[]}"),intro));
        assertEquals("设计参数",MechanicalPaperExporter.cleanCaption("表2-1 表 2-1 设计参数"));
    }
    @Test void exportedWordHasNativeMathActualTocAllImagesAndPageBreaks() throws Exception {
        var state=fixture();
        new MechanicalPaperExporter(null).export(state,temporary);
        try(var zip=new ZipFile(temporary.resolve("paper.docx").toFile())) {
            String xml=new String(zip.getInputStream(zip.getEntry("word/document.xml")).readAllBytes(),java.nio.charset.StandardCharsets.UTF_8);
            assertTrue(xml.contains("TOC "));assertTrue(xml.contains("oMath"));
            assertFalse(xml.contains("{{"));assertFalse(xml.contains("诚信声明"));assertFalse(xml.contains("资料来源"));
            var footerEntry=zip.stream().filter(e->e.getName().matches("word/footer\\d+\\.xml")).findFirst().orElseThrow();
            String footerXml=new String(zip.getInputStream(footerEntry).readAllBytes(),java.nio.charset.StandardCharsets.UTF_8);
            assertEquals(1,footerXml.split("PAGE",-1).length-1);assertFalse(footerXml.contains("<w:pict"));
        }
        try(var document=new XWPFDocument(Files.newInputStream(temporary.resolve("paper.docx")))) {
            assertEquals(15,document.getAllPictures().size());assertEquals(3,document.getTables().size());
            var chapters=document.getParagraphs().stream().filter(p->p.getCTP().isSetPPr() && p.getCTP().getPPr().isSetOutlineLvl() && p.getCTP().getPPr().getOutlineLvl().getVal().intValue()==0).toList();
            assertEquals(6,chapters.size());assertTrue(chapters.stream().allMatch(XWPFParagraph::isPageBreak));
            assertEquals(14,document.getParagraphs().stream().filter(p->p.getCTP().isSetPPr() && p.getCTP().getPPr().isSetOutlineLvl() && p.getCTP().getPPr().getOutlineLvl().getVal().intValue()==1).count());
        }
    }
    @Test void optionalLayoutSmokeUsesAcceptedSampleMedia() throws Exception {
        String reference=System.getProperty("mechanical.paper.qa.reference", "");
        Assumptions.assumeFalse(reference.isBlank());
        Path output=Path.of("target/mechanical-paper-qa").toAbsolutePath();Files.createDirectories(output);
        var state=fixture();
        try(var source=new XWPFDocument(Files.newInputStream(Path.of(reference)))) {
            var media=source.getParagraphs().stream().flatMap(p->p.getRuns().stream()).flatMap(r->r.getEmbeddedPictures().stream()).map(XWPFPicture::getPictureData).toList();
            assertEquals(15,media.size());
            for(int i=0;i<MechanicalPaperPlan.FIGURES.size();i++) {
                var task=state.tasks.get("image:"+MechanicalPaperPlan.FIGURES.get(i).id());
                task.file="source-"+i+"."+media.get(i).suggestFileExtension();Files.write(output.resolve(task.file),media.get(i).getData());
            }
            String bodyStyle=source.getStyles().getStyleWithName("Normal").getStyleId();
            var paragraphs=source.getParagraphs().stream().filter(p->(p.getStyle()==null || bodyStyle.equals(p.getStyle())) && p.getText().length()>50).map(XWPFParagraph::getText).toList();
            assertTrue(paragraphs.size()>=30);
            int index=0,perSection=Math.max(1,paragraphs.size()/14);
            for(var section:MechanicalPaperPlan.SECTIONS) {
                var content=(com.fasterxml.jackson.databind.node.ObjectNode)state.tasks.get("text:"+section.id()).content;
                var body=content.putArray("paragraphs");
                for(int j=0;j<perSection && index<paragraphs.size();j++)body.add(paragraphs.get(index++));
            }
        }
        new MechanicalPaperExporter(new MechanicalPaperFieldUpdater(new org.springframework.mock.env.MockEnvironment())).export(state,output);
        try(var document=new XWPFDocument(Files.newInputStream(output.resolve("paper.docx")))) {
            String full=document.getParagraphs().stream().map(XWPFParagraph::getText).reduce("",(a,b)->a+b);
            assertTrue(full.contains("绪论"));
            try(var zip=new ZipFile(output.resolve("paper.docx").toFile())) {
                String xml=new String(zip.getInputStream(zip.getEntry("word/document.xml")).readAllBytes(),java.nio.charset.StandardCharsets.UTF_8);
                assertTrue(xml.contains("PAGEREF"),"Word must materialize actual TOC page references");
            }
        }
    }
    MechanicalPaperState fixture() throws Exception {
        var state=new MechanicalPaperState();state.id="mp_"+"a".repeat(32);state.title="板类零件钻孔夹具结构设计";
        var design=mapper.createObjectNode();design.put("abstract","对板类零件钻孔夹具进行结构设计和材料力学分析。");design.put("englishAbstract","A drilling fixture is designed and analyzed.");design.putArray("keywords").add("钻孔夹具");design.putArray("sections");design.putArray("figures");state.design=design;
        for(var section:MechanicalPaperPlan.SECTIONS) {
            ((com.fasterxml.jackson.databind.node.ArrayNode)design.get("sections")).addObject().put("id",section.id()).put("title",section.title());
            var content=mapper.createObjectNode();content.putArray("paragraphs").add("以夹具体和压板的结构为依据开展载荷传递分析。设计载荷作为方案级计算假定，结构参数和解析计算采用统一单位。");
            var formulas=content.putArray("formulas");if(section.chapter()==3)formulas.add("σ = M/W = 62.5 MPa").add("F = 1500 N").add("δ = 0.0442 mm");
            var tables=content.putArray("tables");if(List.of("2.1","4.1","5.3").contains(section.id())) {var table=tables.addObject().put("caption","结构设计参数");table.putArray("headers").add("参数").add("数值");table.putArray("rows").addArray().add("载荷").add("1500 N");}
            var task=new MechanicalPaperState.Task(section.title());task.content=content;task.status="completed";state.tasks.put("text:"+section.id(),task);
        }
        for(var figure:MechanicalPaperPlan.FIGURES) {
            ((com.fasterxml.jackson.databind.node.ArrayNode)design.get("figures")).addObject().put("id",figure.id()).put("caption",figure.caption());
            var bitmap=new BufferedImage(600,400,BufferedImage.TYPE_INT_RGB);bitmap.setRGB(Integer.parseInt(figure.id().replace("-","")),0,0x010101);
            Path file=temporary.resolve(figure.id()+".png");ImageIO.write(bitmap,"png",file.toFile());
            var task=new MechanicalPaperState.Task(figure.caption());task.status="completed";task.file=file.getFileName().toString();state.tasks.put("image:"+figure.id(),task);
        }
        return state;
    }
}
