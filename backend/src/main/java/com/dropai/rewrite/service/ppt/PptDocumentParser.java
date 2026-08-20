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
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.IBodyElement;
import org.apache.poi.xwpf.usermodel.XWPFRun;
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
        Set<String> hashes = new HashSet<>(); int totalImages = 0, filteredImages = 0;
        try (InputStream in = Files.newInputStream(source); XWPFDocument doc = new XWPFDocument(in)) {
            tables = doc.getTables().size();
            int chapter = 0, imageIndex = 0; boolean acceptImages = false;
            String currentHeading = "", previousText = "", previousPreviousText = "";
            for (IBodyElement element : doc.getBodyElements()) {
                if (element instanceof XWPFTable table) { String text=clean(table.getText()); if(!text.isBlank())blocks.add(text); continue; }
                if (!(element instanceof XWPFParagraph paragraph)) continue;
                String text=clean(paragraph.getText());
                if(!text.isBlank()){
                    blocks.add(text);
                    if(isHeading(doc,paragraph)){
                        headings.add(text);
                        if(isEndMatterHeading(text))acceptImages=false;
                        else if(!isFrontMatterHeading(text)){
                            int inferredChapter=chapterNumber(text);
                            if(inferredChapter>0)chapter=Math.max(chapter,inferredChapter);
                            else if(headingLevel(doc,paragraph)==1)chapter++;
                            acceptImages=chapter>=2;currentHeading=text;
                        }
                    }
                }
                for(XWPFRun run:paragraph.getRuns())for(var picture:run.getEmbeddedPictures()){
                    totalImages++;imageIndex++;XWPFPictureData data=picture.getPictureData();if(data==null){filteredImages++;continue;}
                    String ext=data.suggestFileExtension();Path target=assetDir.resolve(UUID.randomUUID()+"."+(ext==null?"png":ext));Files.write(target,data.getData());
                    int[] size=imageSize(target);String hash=sha256(data.getData());boolean valid=acceptImages&&size[0]>=240&&size[1]>=120&&hashes.add(hash);
                    if(valid){String caption=nearestCaption(text,previousText,previousPreviousText,currentHeading);assets.add(new Asset(target,null,"chapter-"+chapter+"-image-"+imageIndex,caption,size[0],size[1]));}
                    else{filteredImages++;Files.deleteIfExists(target);}
                }
                if(!text.isBlank()){previousPreviousText=previousText;previousText=text;}
            }
        }
        int packageImages;
        try(InputStream in=Files.newInputStream(source);XWPFDocument doc=new XWPFDocument(in)){packageImages=doc.getAllPictures().size();}
        if(packageImages>totalImages)filteredImages+=packageImages-totalImages;
        return result(headings,blocks,assets,tables,Math.max(totalImages,packageImages),filteredImages);
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
    private static boolean isHeading(XWPFDocument document,XWPFParagraph paragraph){String id=String.valueOf(paragraph.getStyle());String name="";try{var style=document.getStyles()==null?null:document.getStyles().getStyle(id);name=style==null?"":String.valueOf(style.getName());}catch(Exception ignored){}String marker=(id+" "+name).toLowerCase(Locale.ROOT);if(marker.contains("toc")||marker.contains("目录"))return false;if(marker.contains("heading")||marker.contains("标题"))return true;return id.matches("[1-3]");}
    private static int headingLevel(XWPFDocument document,XWPFParagraph paragraph){String id=String.valueOf(paragraph.getStyle());String name="";try{var style=document.getStyles()==null?null:document.getStyles().getStyle(id);name=style==null?"":String.valueOf(style.getName());}catch(Exception ignored){}String marker=(id+" "+name).toLowerCase(Locale.ROOT);var matcher=java.util.regex.Pattern.compile("(?:heading|标题|標題)\\s*([1-3])").matcher(marker);if(matcher.find())return Integer.parseInt(matcher.group(1));if(id.matches("[1-3]"))return Integer.parseInt(id);String text=clean(paragraph.getText());return text.matches("^(?:第?[一二三四五六七八九十0-9]+章|[一二三四五六七八九十0-9]+[、.．]\\s*[^0-9]).*")?1:2;}
    private static boolean isFrontMatterHeading(String text){String v=clean(text).replaceAll("\\s+","").toLowerCase(Locale.ROOT);return v.contains("摘要")||v.equals("abstract")||v.contains("关键词")||v.contains("目录");}
    private static boolean isEndMatterHeading(String text){String v=clean(text).replaceAll("\\s+","");return List.of("结论","总结","参考文献","致谢","附录").stream().anyMatch(v::contains);}
    private static int chapterNumber(String text){String v=clean(text);var decimal=java.util.regex.Pattern.compile("^(\\d+)(?:[.．]\\d+)+").matcher(v);if(decimal.find())return Integer.parseInt(decimal.group(1));var arabic=java.util.regex.Pattern.compile("^第?(\\d+)章").matcher(v);if(arabic.find())return Integer.parseInt(arabic.group(1));var chinese=java.util.regex.Pattern.compile("^第([一二三四五六七八九十])章").matcher(v);if(!chinese.find())return 0;return switch(chinese.group(1)){case"一"->1;case"二"->2;case"三"->3;case"四"->4;case"五"->5;case"六"->6;case"七"->7;case"八"->8;case"九"->9;case"十"->10;default->0;};}
    private static String nearestCaption(String current,String previous,String previousPrevious,String heading){String before=clean(previous);if(isFigureCaptionOnly(before)&&isImageContextParagraph(previousPrevious))return shorten(before+"\n"+clean(previousPrevious),320);if(isImageContextParagraph(before))return shorten(before,240);String same=clean(current);if(isImageContextParagraph(same))return shorten(same,240);return shorten(clean(heading).isBlank()?"源文档图片":heading,80);}
    private static boolean isFigureCaptionOnly(String value){return clean(value).matches("^(?:图|Figure|Fig\\.)\\s*\\d+(?:[-.．]\\d+)?(?:\\s+.{1,30})?$");}
    private static boolean isImageContextParagraph(String value){String v=clean(value);if(v.length()<8)return false;if(v.matches("^(?:第?[一二三四五六七八九十0-9]+章|\\d+(?:[.．]\\d+)+)\\s*[^，。；]{0,30}$"))return false;return !List.of("目录","摘要","关键词","参考文献","致谢").stream().anyMatch(v::equals);}
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
