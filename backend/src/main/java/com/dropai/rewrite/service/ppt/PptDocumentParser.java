package com.dropai.rewrite.service.ppt;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.hwpf.HWPFDocument;
import org.apache.poi.sl.usermodel.PictureData;
import org.apache.poi.xslf.usermodel.XMLSlideShow;
import org.apache.poi.xslf.usermodel.XSLFPictureShape;
import org.apache.poi.xslf.usermodel.XSLFShape;
import org.apache.poi.xslf.usermodel.XSLFSlide;
import org.apache.poi.xslf.usermodel.XSLFTable;
import org.apache.poi.xslf.usermodel.XSLFTextShape;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFPictureData;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.security.MessageDigest;
import java.util.HashSet;

@Service
public class PptDocumentParser {
    public ParsedDocument parse(Path source, Path assetDir) throws Exception {
        Files.createDirectories(assetDir);
        String ext = extension(source.getFileName().toString());
        return switch (ext) {
            case "pptx" -> parsePptx(source, assetDir);
            case "docx" -> parseDocx(source, assetDir);
            case "pdf" -> parsePdf(source, assetDir);
            case "txt", "md", "markdown" -> parseText(source);
            case "doc" -> parseDoc(source);
            default -> throw new IllegalArgumentException("仅支持 DOCX、PDF、PPTX、TXT 和 Markdown 文件");
        };
    }

    private ParsedDocument parsePptx(Path source, Path assetDir) throws Exception {
        List<String> blocks = new ArrayList<>(), headings = new ArrayList<>();
        List<Asset> assets = new ArrayList<>(); Set<String> hashes=new HashSet<>(); int tables = 0, page = 0, validChapter=0, totalImages=0, filteredImages=0;
        try (InputStream in = Files.newInputStream(source); XMLSlideShow deck = new XMLSlideShow(in)) {
            for (XSLFSlide slide : deck.getSlides()) {
                page++; String title = clean(slide.getTitle()); PageRole role=pageRole(page,title); if (!title.isBlank()) headings.add(title);if(role==PageRole.BODY)validChapter++;
                String text = clean(slideText(slide)); if (!text.isBlank()) blocks.add("[第"+page+"页] "+text);
                int position = 0;
                for (XSLFShape shape : slide.getShapes()) {
                    position++;
                    if (shape instanceof XSLFTable) tables++;
                    if (shape instanceof XSLFPictureShape picture) {
                        totalImages++;
                        PictureData data = picture.getPictureData();
                        String suffix = data.getType().extension;
                        Path target = assetDir.resolve(UUID.randomUUID()+"."+(suffix == null ? "png" : suffix));
                        Files.write(target, data.getData());
                        var anchor = picture.getAnchor();
                        double ratio=anchor.getHeight()<=0?0:anchor.getWidth()/anchor.getHeight();
                        String hash=sha256(data.getData());boolean content=role==PageRole.BODY&&validChapter>=2&&anchor.getWidth()>=180&&anchor.getHeight()>=90&&(ratio>=1.15||anchor.getWidth()>=300)&&hashes.add(hash);
                        if(content)assets.add(new Asset(target, page, "shape-"+position, title, (int)anchor.getWidth(), (int)anchor.getHeight()));
                        else {filteredImages++;Files.deleteIfExists(target);}
                    }
                }
            }
        }
        return result(headings, blocks, assets, tables,totalImages,filteredImages);
    }

    private ParsedDocument parseDocx(Path source, Path assetDir) throws Exception {
        List<String> blocks = new ArrayList<>(), headings = new ArrayList<>(); List<Asset> assets = new ArrayList<>(); int tables;
        try (InputStream in = Files.newInputStream(source); XWPFDocument doc = new XWPFDocument(in)) {
            doc.getParagraphs().forEach(p -> { String t=clean(p.getText()); if(t.isBlank()) return; blocks.add(t); String style=String.valueOf(p.getStyle()).toLowerCase(); if(style.contains("heading")||style.contains("标题")) headings.add(t); });
            tables = doc.getTables().size();
            for (XWPFTable table : doc.getTables()) blocks.add(table.getText());
            int i=0; for (XWPFPictureData data : doc.getAllPictures()) {
                i++; String ext=data.suggestFileExtension(); Path target=assetDir.resolve(UUID.randomUUID()+"."+(ext==null?"png":ext)); Files.write(target,data.getData());
                int[] size=imageSize(target); assets.add(new Asset(target, null,"image-"+i,"源文档图片",size[0],size[1]));
            }
        }
        return result(headings,blocks,assets,tables,assets.size(),0);
    }

    private ParsedDocument parsePdf(Path source, Path assetDir) throws Exception {
        List<String> blocks=new ArrayList<>(), headings=new ArrayList<>(); List<Asset> assets=new ArrayList<>();
        try (PDDocument doc=Loader.loadPDF(source.toFile())) {
            PDFTextStripper stripper=new PDFTextStripper();
            for(int i=0;i<doc.getNumberOfPages();i++){
                stripper.setStartPage(i+1); stripper.setEndPage(i+1); String text=clean(stripper.getText(doc)); if(!text.isBlank()) blocks.add("[第"+(i+1)+"页] "+text);
                BufferedImage image=new PDFRenderer(doc).renderImageWithDPI(i,110); Path target=assetDir.resolve("page-"+(i+1)+".png"); ImageIO.write(image,"png",target.toFile()); assets.add(new Asset(target,i+1,"page","PDF页面",image.getWidth(),image.getHeight()));
            }
        }
        return result(headings,blocks,assets,0,assets.size(),0);
    }

    private ParsedDocument parseText(Path source) throws Exception { return result(List.of(),List.of(Files.readString(source,StandardCharsets.UTF_8)),List.of(),0,0,0); }
    private ParsedDocument parseDoc(Path source) throws Exception { try(InputStream in=Files.newInputStream(source); HWPFDocument doc=new HWPFDocument(in)){return result(List.of(),List.of(clean(doc.getDocumentText())),List.of(),0,0,0);} }

    private ParsedDocument result(List<String> headings,List<String> blocks,List<Asset> assets,int tables,int totalImages,int filteredImages){
        List<String> cleanHeadings=new ArrayList<>(new LinkedHashSet<>(headings.stream().map(PptDocumentParser::clean).filter(x->!x.isBlank()).toList()));
        String full=String.join("\n",blocks); String title=!cleanHeadings.isEmpty()?cleanHeadings.get(0):firstMeaningfulLine(full);
        return new ParsedDocument(shorten(title,120),cleanHeadings,blocks,assets,tables,full.length(),totalImages,filteredImages);
    }
    private static String firstMeaningfulLine(String text){for(String line:text.split("[\r\n]+")){line=clean(line);if(line.length()>=4)return line;}return "未命名PPT项目";}
    private static int[] imageSize(Path p){try{BufferedImage i=ImageIO.read(p.toFile());return i==null?new int[]{0,0}:new int[]{i.getWidth(),i.getHeight()};}catch(Exception e){return new int[]{0,0};}}
    private static String extension(String name){int dot=name.lastIndexOf('.');return dot<0?"":name.substring(dot+1).toLowerCase(Locale.ROOT);}
    private static String slideText(XSLFSlide slide){StringBuilder out=new StringBuilder();for(XSLFShape shape:slide.getShapes())if(shape instanceof XSLFTextShape text&&!text.getText().isBlank())out.append(text.getText()).append('\n');return out.toString();}
    static String clean(String value){return value==null?"":value.replaceAll("[\\p{Cntrl}&&[^\r\n\t]]"," ").replaceAll("[ \t]+"," ").replaceAll("\n{3,}","\n\n").trim();}
    static String shorten(String value,int max){String v=clean(value);return v.length()<=max?v:v.substring(0,max);}
    private static PageRole pageRole(int page,String title){String v=title.toLowerCase();if(page<=1||v.contains("封面"))return PageRole.COVER;if(v.contains("目录")||v.equals("contents"))return PageRole.CATALOG;if(List.of("摘要","abstract","关键词","结论","参考文献","致谢","附录","谢谢").stream().anyMatch(v::contains))return PageRole.EXCLUDED;return PageRole.BODY;}
    private static String sha256(byte[] bytes)throws Exception{byte[] digest=MessageDigest.getInstance("SHA-256").digest(bytes);return java.util.HexFormat.of().formatHex(digest);}
    private enum PageRole{COVER,CATALOG,BODY,EXCLUDED}

    public record Asset(Path path,Integer sourcePage,String sourcePosition,String caption,int width,int height){}
    public record ParsedDocument(String title,List<String> headings,List<String> blocks,List<Asset> assets,int tableCount,int characterCount,int totalImageCount,int filteredAssetCount){
        public ParsedDocument(String title,List<String> headings,List<String> blocks,List<Asset> assets,int tableCount,int characterCount){this(title,headings,blocks,assets,tableCount,characterCount,assets==null?0:assets.size(),0);}
    }
}
