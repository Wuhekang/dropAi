package com.dropai.rewrite.service.ppt;

import com.dropai.rewrite.auth.AuthContext;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.poi.sl.usermodel.PaintStyle;
import org.apache.poi.xslf.usermodel.XMLSlideShow;
import org.apache.poi.xslf.usermodel.XSLFShape;
import org.apache.poi.xslf.usermodel.XSLFGroupShape;
import org.apache.poi.xslf.usermodel.XSLFPictureShape;
import org.apache.poi.xslf.usermodel.XSLFGraphicFrame;
import org.apache.poi.xslf.usermodel.XSLFTable;
import org.apache.poi.xslf.usermodel.XSLFSimpleShape;
import org.apache.poi.xslf.usermodel.XSLFTextShape;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.awt.Color;
import java.awt.geom.Rectangle2D;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.charset.Charset;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

@Service
public class PptTemplateService {
    public static final String AI_RECOMMEND="AI_RECOMMEND",TECH_DEFENSE="TECH_DEFENSE",SIMPLE_ACADEMIC="SIMPLE_ACADEMIC",PREMIUM_DESIGN="PREMIUM_DESIGN",BUSINESS="BUSINESS",CUSTOM="CUSTOM";
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;
    private final Path root=Path.of("storage","ppt-templates").toAbsolutePath().normalize();

    public PptTemplateService(JdbcTemplate jdbc,ObjectMapper mapper){this.jdbc=jdbc;this.mapper=mapper;}

    public List<Map<String,Object>> list(){
        Long userId=AuthContext.requireUserId();
        List<Map<String,Object>> result=new ArrayList<>(builtIns());
        result.addAll(jdbc.queryForList("SELECT id,template_name,style,suitable_major,slide_types_json,metadata_json,created_at FROM ppt_template WHERE user_id=? AND status='READY' ORDER BY created_at DESC",userId));
        result.forEach(this::expandJson);
        return result;
    }

    @Transactional public List<Map<String,Object>> uploadZip(MultipartFile file)throws Exception{
        Long userId=AuthContext.requireUserId();
        if(file==null||file.isEmpty())throw new IllegalArgumentException("请选择模板ZIP文件");
        String name=String.valueOf(file.getOriginalFilename()).toLowerCase(Locale.ROOT);
        if(!name.endsWith(".zip"))throw new IllegalArgumentException("模板包仅支持ZIP格式");
        if(file.getSize()>500L*1024*1024)throw new IllegalArgumentException("模板包不能超过500MB");
        Path batch=inside(root.resolve(userId.toString()).resolve(UUID.randomUUID().toString()));Files.createDirectories(batch);Path archive=inside(batch.resolve("upload.zip"));Files.copy(file.getInputStream(),archive,StandardCopyOption.REPLACE_EXISTING);
        List<Path> pptxFiles=new ArrayList<>();long expanded=0;int entries=0;
        try(ZipFile zip=new ZipFile(archive.toFile(),Charset.forName("GBK"))){
            for(var all=zip.entries();all.hasMoreElements();){ZipEntry entry=all.nextElement();
                if(++entries>500)throw new IllegalArgumentException("模板包文件数量过多");
                if(entry.isDirectory())continue;
                String safe=entry.getName().replace('\\','/');Path target=inside(batch.resolve(safe));
                if(!target.startsWith(batch))throw new IllegalArgumentException("模板包包含非法路径");
                String lower=target.getFileName().toString().toLowerCase(Locale.ROOT);
                if(!lower.endsWith(".pptx"))continue;
                Files.createDirectories(target.getParent());
                try(var in=zip.getInputStream(entry);var out=Files.newOutputStream(target)){expanded+=in.transferTo(out);}
                if(expanded>900L*1024*1024)throw new IllegalArgumentException("模板包解压后过大");
                pptxFiles.add(target);
            }
        }
        Files.deleteIfExists(archive);
        if(pptxFiles.isEmpty())throw new IllegalArgumentException("ZIP中未找到PPTX模板");
        List<Map<String,Object>> created=new ArrayList<>();
        for(Path pptx:pptxFiles){TemplateMetadata meta=analyze(pptx);String id=UUID.randomUUID().toString();Path dir=inside(root.resolve(userId.toString()).resolve(id));Files.createDirectories(dir);Path stored=inside(dir.resolve("template.pptx"));Files.move(pptx,stored,StandardCopyOption.REPLACE_EXISTING);Files.writeString(inside(dir.resolve("template_metadata.json")),mapper.writerWithDefaultPrettyPrinter().writeValueAsString(meta));
            jdbc.update("INSERT INTO ppt_template(id,user_id,template_name,style,suitable_major,slide_types_json,metadata_json,file_path,status,created_at,updated_at) VALUES(?,?,?,?,?,?,?,?, 'READY',?,?)",id,userId,meta.templateName(),meta.style(),meta.suitableMajor(),json(meta.slideTypes()),json(meta),stored.toString(),LocalDateTime.now(),LocalDateTime.now());
            Map<String,Object> row=new LinkedHashMap<>();row.put("id",id);row.put("templateName",meta.templateName());row.put("style",meta.style());row.put("suitableMajor",meta.suitableMajor());row.put("slideTypes",meta.slideTypes());row.put("metadata",meta);created.add(row);
        }
        return created;
    }

    public Map<String,Object> recommend(String projectId){Long userId=AuthContext.requireUserId();Map<String,Object> p=project(projectId,userId);String style=recommendStyle(p);Map<String,Object> result=new LinkedHashMap<>();result.put("style",style);result.put("reason",recommendReason(style,p));result.put("profile",profile(style,null));return result;}

    @Transactional public Map<String,Object> select(String projectId,Map<String,Object> input){
        Long userId=AuthContext.requireUserId();Map<String,Object> p=project(projectId,userId);String style=string(input.get("style"));if(style.isBlank())style=AI_RECOMMEND;String templateId=string(input.get("templateId"));
        if(AI_RECOMMEND.equals(style))style=recommendStyle(p);
        PptxGenerator.TemplateProfile profile=profile(style,templateId.isBlank()?null:templateId);
        jdbc.update("UPDATE ppt_project SET template_style=?,template_id=?,template_metadata_json=?,updated_at=? WHERE id=? AND user_id=?",style,templateId.isBlank()?null:templateId,json(profile),LocalDateTime.now(),projectId,userId);
        Map<String,Object> out=new LinkedHashMap<>();out.put("style",style);out.put("templateId",templateId);out.put("profile",profile);out.put("reason",recommendReason(style,p));return out;
    }

    public PptxGenerator.TemplateProfile selectedProfile(Map<String,Object> project){
        Object raw=readJson(string(project.get("template_metadata_json")));if(raw instanceof Map<?,?> map){try{return mapper.convertValue(map,PptxGenerator.TemplateProfile.class);}catch(Exception ignored){}}
        String style=string(project.get("template_style"));if(style.isBlank()||AI_RECOMMEND.equals(style))style=recommendStyle(project);
        return profile(style,string(project.get("template_id")));
    }

    public TemplateMetadata analyze(Path pptx)throws Exception{
        try(InputStream in=Files.newInputStream(pptx);XMLSlideShow deck=new XMLSlideShow(in)){
            Map<String,Integer> colorFrequency=new LinkedHashMap<>();Set<String> fonts=new LinkedHashSet<>(),types=new LinkedHashSet<>();List<TemplateSlideMetadata> slides=new ArrayList<>();int pictures=0,charts=0;
            for(int index=0;index<deck.getSlides().size();index++){
                var slide=deck.getSlides().get(index);int textShapes=0,pictureShapes=0,graphicFrames=0;boolean hasTable=false;Map<String,Integer> slideColors=new LinkedHashMap<>();List<UsableRegion> regions=new ArrayList<>();StringBuilder visibleText=new StringBuilder();
                for(XSLFShape shape:allShapes(slide.getShapes())){
                    if(shape instanceof XSLFTextShape text){textShapes++;if(!text.getText().isBlank())visibleText.append(text.getText()).append('\n');text.getTextParagraphs().forEach(p->p.getTextRuns().forEach(r->{if(r.getFontFamily()!=null&&!r.getFontFamily().isBlank())fonts.add(r.getFontFamily());Color c=paintColor(r.getFontColor());if(c!=null){colorFrequency.merge(hex(c),1,Integer::sum);slideColors.merge(hex(c),1,Integer::sum);}}));addRegion(regions,shape,"text",deck);}
                    if(shape instanceof XSLFPictureShape){pictures++;pictureShapes++;addRegion(regions,shape,"image",deck);}
                    if(shape instanceof XSLFGraphicFrame){charts++;graphicFrames++;if(shape instanceof XSLFTable)hasTable=true;addRegion(regions,shape,shape instanceof XSLFTable?"table":"chart",deck);}
                    if(shape instanceof XSLFSimpleShape simple){Color c=paintColor(simple.getFillStyle()==null?null:simple.getFillStyle().getPaint());if(c!=null){colorFrequency.merge(hex(c),1,Integer::sum);slideColors.merge(hex(c),1,Integer::sum);}}
                }
                String pageType=pageType(index,deck.getSlides().size(),visibleText.toString(),textShapes,pictureShapes,graphicFrames,hasTable);types.add(pageType);String layout=slideLayout(regions,pictureShapes,graphicFrames,hasTable);
                List<String> palette=slideColors.entrySet().stream().sorted(Map.Entry.<String,Integer>comparingByValue().reversed()).map(Map.Entry::getKey).limit(6).toList();
                slides.add(new TemplateSlideMetadata(index+1,"slide_"+(index+1),pageType,palette,layout,regions,textShapes,pictureShapes,graphicFrames,hasTable));
            }
            List<String> palette=colorFrequency.entrySet().stream().filter(e->!List.of("#FFFFFF","#000000").contains(e.getKey())).sorted(Map.Entry.<String,Integer>comparingByValue().reversed()).map(Map.Entry::getKey).limit(8).toList();if(palette.isEmpty())palette=List.of("#6E4FFF","#FF55B0","#202438","#F7F5FF");
            String templateName=stripExtension(pptx.getFileName().toString());if("template".equalsIgnoreCase(templateName)&&pptx.getParent()!=null)templateName=pptx.getParent().getFileName().toString();String style=classifyStyle(templateName,palette,pictures,deck.getSlides().size());String major=suitableMajor(style,templateName);
            return new TemplateMetadata(templateName,style,palette,fonts.stream().limit(6).toList(),major,new ArrayList<>(types),deck.getPageSize().width,deck.getPageSize().height,deck.getSlides().size(),pictures,charts,layoutVariant(style),slides);
        }
    }

    private String pageType(int index,int total,String text,int textShapes,int pictures,int frames,boolean table){
        String normalized=text.toLowerCase(Locale.ROOT);if(index==0)return "cover";if(index==1)return "catalog";if(index==total-1||normalized.matches("(?s).*(谢谢|thank|致谢).*$"))return "thanks";
        if(table||normalized.matches("(?s).*(测试结果|数据表|统计表|table).*$"))return "chart";
        if(normalized.matches("(?s).*(时间轴|发展历程|实施流程|timeline|milestone).*$"))return "timeline";
        if(pictures>0)return "image";if(textShapes<=3)return "section";return "content";
    }
    private String slideLayout(List<UsableRegion> regions,int pictures,int frames,boolean table){
        if(table)return "table";if(frames>0)return "chart";if(pictures>0){UsableRegion image=regions.stream().filter(r->"image".equals(r.usage())).findFirst().orElse(null);if(image==null)return "image";if(image.width()>55)return "full_image";return image.x()<45?"image_left":"image_right";}long text=regions.stream().filter(r->"text".equals(r.usage())).count();return text>=4?"multi_text":text>=2?"two_column":"text";
    }
    private void addRegion(List<UsableRegion> regions,XSLFShape shape,String usage,XMLSlideShow deck){Rectangle2D a=shape.getAnchor();if(a==null||a.getWidth()<20||a.getHeight()<12)return;double w=deck.getPageSize().getWidth(),h=deck.getPageSize().getHeight();regions.add(new UsableRegion(round(a.getX()*100/w),round(a.getY()*100/h),round(a.getWidth()*100/w),round(a.getHeight()*100/h),usage));}
    private double round(double value){return Math.round(value*100.0)/100.0;}
    private List<XSLFShape> allShapes(List<XSLFShape> source){List<XSLFShape> out=new ArrayList<>();for(XSLFShape shape:source)collect(shape,out);return out;}
    private void collect(XSLFShape shape,List<XSLFShape> out){out.add(shape);if(shape instanceof XSLFGroupShape group)for(XSLFShape child:group.getShapes())collect(child,out);}

    private PptxGenerator.TemplateProfile profile(String style,String templateId){
        if(templateId!=null&&!templateId.isBlank()){
            Long userId=AuthContext.requireUserId();List<Map<String,Object>> rows=jdbc.queryForList("SELECT metadata_json,file_path FROM ppt_template WHERE id=? AND user_id=? AND status='READY'",templateId,userId);if(rows.isEmpty())throw new IllegalArgumentException("自定义模板不存在或无权访问");Object raw=readJson(string(rows.get(0).get("metadata_json")));if(raw instanceof Map<?,?> m){TemplateMetadata meta=mapper.convertValue(m,TemplateMetadata.class);List<String> c=meta.colors();return new PptxGenerator.TemplateProfile(CUSTOM,color(c,0,"#6E4FFF"),color(c,1,"#FF55B0"),color(c,2,"#202438"),"#6D7285",color(c,3,"#F7F5FF"),meta.fonts().isEmpty()?"Microsoft YaHei":meta.fonts().get(0),meta.layoutVariant(),meta.templateName(),string(rows.get(0).get("file_path")));}}
        return switch(style){
            case TECH_DEFENSE->new PptxGenerator.TemplateProfile(style,"#2563EB","#22D3EE","#0F172A","#64748B","#EFF6FF","Microsoft YaHei","tech","科技答辩",null);
            case SIMPLE_ACADEMIC->new PptxGenerator.TemplateProfile(style,"#6E4FFF","#A78BFA","#22263A","#74798B","#F8F7FC","Microsoft YaHei","academic","简约学术",null);
            case PREMIUM_DESIGN->new PptxGenerator.TemplateProfile(style,"#A06A42","#D8B48A","#27231F","#756B62","#F7F1EA","Microsoft YaHei","design","高级设计",null);
            case BUSINESS->new PptxGenerator.TemplateProfile(style,"#1E3A5F","#D9A441","#172033","#657087","#F3F6FA","Microsoft YaHei","business","商务汇报",null);
            default->new PptxGenerator.TemplateProfile(SIMPLE_ACADEMIC,"#6E4FFF","#FF55B0","#202438","#676C81","#F7F5FF","Microsoft YaHei","academic","简约学术",null);
        };
    }

    private List<Map<String,Object>> builtIns(){return List.of(builtin(AI_RECOMMEND,"AI智能推荐","根据专业、文档与图片自动选择"),builtin(TECH_DEFENSE,"科技答辩","计算机、工科、深色科技"),builtin(SIMPLE_ACADEMIC,"简约学术","论文答辩、课题汇报"),builtin(PREMIUM_DESIGN,"高级设计","环境、视觉传达、室内设计"),builtin(BUSINESS,"商务汇报","项目汇报、实施报告"));}
    private Map<String,Object> builtin(String id,String name,String description){Map<String,Object> row=new LinkedHashMap<>();row.put("id",id);row.put("templateName",name);row.put("style",id);row.put("description",description);row.put("builtIn",true);return row;}
    private String recommendStyle(Map<String,Object> project){String major=string(project.get("major")).toLowerCase(Locale.ROOT),source=string(project.get("source_file_name")).toLowerCase(Locale.ROOT),analysis=string(project.get("analysis_json")).toLowerCase(Locale.ROOT);int images=integer(project.get("image_count"),0),slides=Math.max(1,integer(project.get("target_slide_count"),16));if(major.contains("计算机")||major.contains("软件")||major.contains("工程")||analysis.contains("技术栈"))return TECH_DEFENSE;if(major.contains("环境")||major.contains("景观")||major.contains("视觉")||major.contains("室内")||images*2>=slides||analysis.contains("设计作品"))return PREMIUM_DESIGN;if(major.contains("商务")||major.contains("管理")||source.contains("实施报告")||source.contains("工作汇报"))return BUSINESS;if(source.contains("开题")||source.contains("论文")||source.contains("答辩"))return SIMPLE_ACADEMIC;return SIMPLE_ACADEMIC;}
    private String recommendReason(String style,Map<String,Object> project){String major=string(project.get("major"));String source=string(project.get("source_file_name"));int images=integer(project.get("image_count"),0),slides=Math.max(1,integer(project.get("target_slide_count"),16));String type=source.contains("开题")?"开题报告":source.contains("论文")?"论文":source.contains("实施")?"实施报告":"项目文档";return "根据专业“"+(major.isBlank()?"通用学术":major)+"”、"+type+"、图片比例 "+images+"/"+slides+" 与内容类型，推荐"+switch(style){case TECH_DEFENSE->"科技答辩";case PREMIUM_DESIGN->"高级设计";case BUSINESS->"商务汇报";case CUSTOM->"自定义模板";default->"简约学术";};}
    private String classifyStyle(String name,List<String> colors,int pictures,int slides){String n=name.toLowerCase(Locale.ROOT);if(n.contains("科技")||n.contains("计算机"))return TECH_DEFENSE;if(n.contains("商务")||n.contains("汇报")||n.contains("实施"))return BUSINESS;if(n.contains("艺术")||n.contains("设计")||pictures>slides*2)return PREMIUM_DESIGN;return SIMPLE_ACADEMIC;}
    private String suitableMajor(String style,String name){if(TECH_DEFENSE.equals(style))return "计算机/软件/工科";if(PREMIUM_DESIGN.equals(style))return "环境/视觉传达/室内";if(BUSINESS.equals(style))return "管理/商务/项目汇报";return name.contains("教师")?"教育/说课":"通用学术";}
    private String layoutVariant(String style){return switch(style){case TECH_DEFENSE->"tech";case PREMIUM_DESIGN->"design";case BUSINESS->"business";default->"academic";};}
    private Map<String,Object> project(String id,Long userId){List<Map<String,Object>> rows=jdbc.queryForList("SELECT p.*,(SELECT COUNT(*) FROM ppt_asset a WHERE a.project_id=p.id) image_count FROM ppt_project p WHERE p.id=? AND p.user_id=?",id,userId);if(rows.isEmpty())throw new IllegalArgumentException("PPT项目不存在或无权访问");return rows.get(0);}
    private void expandJson(Map<String,Object> row){row.put("templateName",row.get("template_name"));row.put("suitableMajor",row.get("suitable_major"));Object m=readJson(string(row.get("metadata_json")));if(m instanceof Map<?,?>)row.put("metadata",m);Object s=readJson(string(row.get("slide_types_json")));if(s instanceof List<?>)row.put("slideTypes",s);}
    private String color(List<String> colors,int index,String fallback){return colors!=null&&index<colors.size()?colors.get(index):fallback;}
    private String hex(Color c){return String.format("#%02X%02X%02X",c.getRed(),c.getGreen(),c.getBlue());}
    private Color paintColor(PaintStyle paint){if(paint instanceof PaintStyle.SolidPaint solid)return solid.getSolidColor().getColor();return null;}
    private String stripExtension(String name){int dot=name.lastIndexOf('.');return dot>0?name.substring(0,dot):name;}
    private Path inside(Path p){Path n=p.toAbsolutePath().normalize();if(!n.startsWith(root))throw new IllegalArgumentException("非法模板路径");return n;}
    private String json(Object value){try{return mapper.writeValueAsString(value);}catch(Exception e){throw new IllegalStateException("模板元数据序列化失败",e);}}
    private Object readJson(String value){try{return value==null||value.isBlank()?Map.of():mapper.readValue(value,new TypeReference<Object>(){});}catch(Exception e){return Map.of();}}
    private String string(Object value){return value==null?"":String.valueOf(value);}
    private int integer(Object value,int fallback){try{return value instanceof Number n?n.intValue():Integer.parseInt(string(value));}catch(Exception e){return fallback;}}

    public record UsableRegion(double x,double y,double width,double height,String usage){}
    public record TemplateSlideMetadata(int pageNumber,String slideId,String pageType,List<String> colors,String layout,List<UsableRegion> usableRegions,int textShapeCount,int pictureCount,int chartCount,boolean hasTable){}
    public record TemplateMetadata(String templateName,String style,List<String> colors,List<String> fonts,String suitableMajor,List<String> slideTypes,int pageWidth,int pageHeight,int slideCount,int pictureCount,int chartCount,String layoutVariant,List<TemplateSlideMetadata> slides){}
}
