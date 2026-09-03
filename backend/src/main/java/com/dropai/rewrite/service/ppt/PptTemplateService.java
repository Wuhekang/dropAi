package com.dropai.rewrite.service.ppt;

import com.dropai.rewrite.auth.AuthContext;
import com.dropai.rewrite.service.ppt.rendering.template.v1.RenderingTemplatePackRegistry;
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
    public static final String AI_RECOMMEND="AI_RECOMMEND",TECH_DEFENSE="TECH_DEFENSE",SIMPLE_ACADEMIC="SIMPLE_ACADEMIC",ENVIRONMENT_DESIGN="ENVIRONMENT_DESIGN",VISUAL_COMMUNICATION="VISUAL_COMMUNICATION",BUSINESS="BUSINESS",MINIMAL_PREMIUM="MINIMAL_PREMIUM",CUSTOM="CUSTOM";
    public static final String ACADEMIC_PURPLE=RenderingTemplatePackRegistry.ACADEMIC_PURPLE;
    public static final String SMALL_BEAR_WATERCOLOR_BLUE_V1=RenderingTemplatePackRegistry.SMALL_BEAR_WATERCOLOR_BLUE_V1;
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;
    private final Path root=Path.of("storage","ppt-templates").toAbsolutePath().normalize();

    public PptTemplateService(JdbcTemplate jdbc,ObjectMapper mapper){this.jdbc=jdbc;this.mapper=mapper;}

    public List<Map<String,Object>> list(){
        Long userId=AuthContext.requireUserId();
        List<Map<String,Object>> result=new ArrayList<>(builtIns());
        List<Map<String,Object>> custom=jdbc.queryForList("SELECT id,template_name,style,suitable_major,slide_types_json,metadata_json,file_path,created_at FROM ppt_template WHERE user_id=? AND status='READY' ORDER BY CASE WHEN template_name LIKE '%小熊熊%' THEN 0 ELSE 1 END,created_at DESC",userId);
        custom.forEach(row->{row.put("templatePackId",null);row.put("renderingV1Supported",false);row.put("trusted",false);row.put("unsupportedReason","自定义PPTX尚未编译为可信 Rendering V1 TemplatePack");});
        result.addAll(custom);
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

    public Map<String,Object> recommend(String projectId){
        Long userId=AuthContext.requireUserId();Map<String,Object> p=project(projectId,userId);
        String templatePackId=recommendTemplatePack(p);PptxGenerator.TemplateProfile profile=profile(templatePackId,templatePackId);
        Map<String,Object> result=new LinkedHashMap<>();result.put("style",templatePackId);result.put("templateId",templatePackId);result.put("templatePackId",templatePackId);result.put("renderingV1Supported",true);result.put("trusted",true);result.put("reason",recommendReason(templatePackId,p));result.put("profile",profile);return result;
    }

    @Transactional public Map<String,Object> select(String projectId,Map<String,Object> input){
        Long userId=AuthContext.requireUserId();Map<String,Object> p=project(projectId,userId);
        if("GENERATING".equals(string(p.get("status"))))throw new IllegalStateException("PPT正在生成，暂时不能切换模板");
        if(input==null)input=Map.of();
        String requested=firstNonBlank(string(input.get("templatePackId")),string(input.get("templateId")),string(input.get("style")),AI_RECOMMEND);
        String templatePackId=AI_RECOMMEND.equalsIgnoreCase(requested)?recommendTemplatePack(p):requireTrustedPack(requested);
        PptxGenerator.TemplateProfile profile=profile(templatePackId,templatePackId);
        Map<String,Object> metadata=selectionMetadata(templatePackId,profile);
        int changed=jdbc.update("UPDATE ppt_project SET template_style=?,template_id=?,template_metadata_json=?,current_stage=CASE WHEN status IN ('PLANNED','SUCCESS','FAILED') THEN '模板已更新，等待重新规划' ELSE current_stage END,progress=CASE WHEN status IN ('PLANNED','SUCCESS','FAILED') THEN 38 ELSE progress END,status=CASE WHEN status IN ('PLANNED','SUCCESS','FAILED') THEN 'OUTLINE_READY' ELSE status END,output_path=NULL,error_message=NULL,updated_at=? WHERE id=? AND user_id=? AND status<>'GENERATING'",templatePackId,templatePackId,json(metadata),LocalDateTime.now(),projectId,userId);
        if(changed!=1)throw new IllegalStateException("PPT正在生成，暂时不能切换模板");
        Map<String,Object> out=new LinkedHashMap<>();out.put("style",templatePackId);out.put("templateId",templatePackId);out.put("templatePackId",templatePackId);out.put("renderingV1Supported",true);out.put("trusted",true);out.put("profile",profile);out.put("planInvalidated",List.of("PLANNED","SUCCESS","FAILED").contains(string(p.get("status"))));out.put("reason",recommendReason(templatePackId,p));return out;
    }

    public PptxGenerator.TemplateProfile selectedProfile(Map<String,Object> project){
        Object raw=readJson(string(project.get("template_metadata_json")));if(raw instanceof Map<?,?> map){Object nested=map.get("profile");try{return mapper.convertValue(nested==null?map:nested,PptxGenerator.TemplateProfile.class);}catch(Exception ignored){}}
        String style=string(project.get("template_style"));String templateId=string(project.get("template_id"));if(style.isBlank()||AI_RECOMMEND.equalsIgnoreCase(style)){style=recommendTemplatePack(project);templateId=style;}
        return profile(style,templateId);
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
                LayoutAnalysis analysis=analyzeLayout(regions,textShapes,graphicFrames,hasTable);String pageType=pageType(index,deck.getSlides().size(),visibleText.toString(),textShapes,analysis.hasImageSlot()?1:0,graphicFrames,hasTable);types.add(pageType);String layout=analysis.layoutStyle();
                List<String> palette=slideColors.entrySet().stream().sorted(Map.Entry.<String,Integer>comparingByValue().reversed()).map(Map.Entry::getKey).limit(6).toList();
                List<TemplateAssetMetadata> assets=classifyAssets(regions);
                slides.add(new TemplateSlideMetadata(index+1,"slide_"+(index+1),pageType,palette,layout,regions,assets,textShapes,pictureShapes,graphicFrames,hasTable,analysis.hasImageSlot(),analysis.hasLeftDecoration(),analysis.hasRightDecoration(),analysis.safeArea(),analysis.visualCenterX(),analysis.titleAlign(),analysis.contentAlign()));
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
        if(pictures>0)return "image_content";if(textShapes<=3)return "section";return "text_content";
    }
    private LayoutAnalysis analyzeLayout(List<UsableRegion> regions,int textShapes,int frames,boolean table){
        List<UsableRegion> images=regions.stream().filter(r->"image".equals(r.usage())).toList();boolean left=images.stream().anyMatch(r->isEdgeDecoration(r,true)),right=images.stream().anyMatch(r->isEdgeDecoration(r,false));
        List<UsableRegion> slots=images.stream().filter(this::isContentImageSlot).toList();boolean hasImage=!slots.isEmpty(),centeredText=!hasImage&&frames==0&&!table;double leftWeight=decorationWeight(images,true),rightWeight=decorationWeight(images,false);SafeArea safe=computeSafeArea(left,right,leftWeight,rightWeight);double center=computeVisualCenter(safe,leftWeight,rightWeight);String titleAlign=resolveTitleAlignment(hasImage,centeredText),contentAlign=centeredText?"center":"left";
        String style;if(table)style="chart-table";else if(frames>0)style="chart";else if(hasImage){UsableRegion slot=slots.get(0);style=slot.x()+slot.width()/2<50?"image-left":"image-right";}else if(textShapes<=9)style="title-only-centered";else style="text-centered-safe";
        return new LayoutAnalysis(style,hasImage,left,right,safe,center,titleAlign,contentAlign);
    }
    private boolean isEdgeDecoration(UsableRegion r,boolean left){double center=r.x()+r.width()/2;return r.height()>=25&&r.width()<=32&&(left?center<=20:center>=80);}
    private boolean isContentImageSlot(UsableRegion r){double area=r.width()*r.height();double center=r.x()+r.width()/2;return area>=550&&r.width()>=22&&r.height()>=18&&center>22&&center<78&&r.x()>12&&r.x()+r.width()<94;}
    private List<TemplateAssetMetadata> classifyAssets(List<UsableRegion> regions){List<TemplateAssetMetadata> out=new ArrayList<>();int index=1;for(UsableRegion region:regions){if(!"image".equals(region.usage()))continue;String role=isContentImageSlot(region)?"content_asset":"background_asset";out.add(new TemplateAssetMetadata("asset_"+index++,role,region.x(),region.y(),region.width(),region.height()));}return out;}
    private double decorationWeight(List<UsableRegion> images,boolean left){return images.stream().filter(r->isEdgeDecoration(r,left)).mapToDouble(r->r.width()*r.height()).sum();}
    private SafeArea computeSafeArea(boolean left,boolean right,double leftWeight,double rightWeight){double l=left?0.18:0.12,r=right?0.82:0.88;if(leftWeight>rightWeight*1.35){l=Math.min(0.22,l+0.03);r=Math.min(0.86,r+0.02);}else if(rightWeight>leftWeight*1.35){l=Math.max(0.14,l-0.02);r=Math.max(0.78,r-0.03);}return new SafeArea(round(l),round(r),0.14,0.86);}
    private double computeVisualCenter(SafeArea safe,double leftWeight,double rightWeight){double center=(safe.left()+safe.right())/2;if(leftWeight>rightWeight*1.35)center+=0.025;else if(rightWeight>leftWeight*1.35)center-=0.025;return round(Math.max(safe.left()+0.1,Math.min(safe.right()-0.1,center)));}
    private String resolveTitleAlignment(boolean hasImage,boolean centeredText){return !hasImage&&centeredText?"center":"left";}
    private void addRegion(List<UsableRegion> regions,XSLFShape shape,String usage,XMLSlideShow deck){Rectangle2D a=shape.getAnchor();if(a==null||a.getWidth()<20||a.getHeight()<12)return;double w=deck.getPageSize().getWidth(),h=deck.getPageSize().getHeight();regions.add(new UsableRegion(round(a.getX()*100/w),round(a.getY()*100/h),round(a.getWidth()*100/w),round(a.getHeight()*100/h),usage));}
    private double round(double value){return Math.round(value*100.0)/100.0;}
    private List<XSLFShape> allShapes(List<XSLFShape> source){List<XSLFShape> out=new ArrayList<>();for(XSLFShape shape:source)collect(shape,out);return out;}
    private void collect(XSLFShape shape,List<XSLFShape> out){out.add(shape);if(shape instanceof XSLFGroupShape group)for(XSLFShape child:group.getShapes())collect(child,out);}

    private PptxGenerator.TemplateProfile profile(String style,String templateId){
        String trusted=trustedPackOrBlank(firstNonBlank(templateId,style));
        if(ACADEMIC_PURPLE.equals(trusted))return new PptxGenerator.TemplateProfile(ACADEMIC_PURPLE,"#7257FF","#E85BB5","#20243A","#747B91","#F7F8FC","Microsoft YaHei","academic","Academic Purple",null);
        if(SMALL_BEAR_WATERCOLOR_BLUE_V1.equals(trusted))return new PptxGenerator.TemplateProfile(SMALL_BEAR_WATERCOLOR_BLUE_V1,"#4F86A6","#A8CEE0","#4A5A69","#718896","#F4FAFC","Microsoft YaHei","watercolor-blue","小熊熊·水彩蓝",null);
        if(templateId!=null&&!templateId.isBlank()){
            Long userId=AuthContext.requireUserId();List<Map<String,Object>> rows=jdbc.queryForList("SELECT metadata_json,file_path FROM ppt_template WHERE id=? AND user_id=? AND status='READY'",templateId,userId);if(rows.isEmpty())throw new IllegalArgumentException("自定义模板不存在或无权访问");Object raw=readJson(string(rows.get(0).get("metadata_json")));if(raw instanceof Map<?,?> m){TemplateMetadata meta=mapper.convertValue(m,TemplateMetadata.class);List<String> c=meta.colors();return new PptxGenerator.TemplateProfile(CUSTOM,color(c,0,"#6E4FFF"),color(c,1,"#FF55B0"),color(c,2,"#202438"),"#6D7285",color(c,3,"#F7F5FF"),meta.fonts().isEmpty()?"Microsoft YaHei":meta.fonts().get(0),meta.layoutVariant(),meta.templateName(),string(rows.get(0).get("file_path")));}}
        return switch(style){
            case TECH_DEFENSE->new PptxGenerator.TemplateProfile(style,"#2563EB","#22D3EE","#0F172A","#64748B","#EFF6FF","Microsoft YaHei","tech","科技答辩",null);
            case SIMPLE_ACADEMIC->new PptxGenerator.TemplateProfile(style,"#6E4FFF","#A78BFA","#22263A","#74798B","#F8F7FC","Microsoft YaHei","academic","简约学术",null);
            case ENVIRONMENT_DESIGN->new PptxGenerator.TemplateProfile(style,"#397C62","#B4D58C","#21352D","#718078","#F1F7F2","Microsoft YaHei","environment","环境设计",null);
            case VISUAL_COMMUNICATION->new PptxGenerator.TemplateProfile(style,"#7C3AED","#FF4DA6","#241C35","#7E718B","#FFF0F8","Microsoft YaHei","visual","视觉传达",null);
            case BUSINESS->new PptxGenerator.TemplateProfile(style,"#1E3A5F","#D9A441","#172033","#657087","#F3F6FA","Microsoft YaHei","business","商务汇报",null);
            case MINIMAL_PREMIUM->new PptxGenerator.TemplateProfile(style,"#22242A","#B9A27B","#17181C","#77736D","#F6F3EE","Microsoft YaHei","minimal","极简高级",null);
            default->new PptxGenerator.TemplateProfile(SIMPLE_ACADEMIC,"#6E4FFF","#FF55B0","#202438","#676C81","#F7F5FF","Microsoft YaHei","academic","简约学术",null);
        };
    }

    private List<Map<String,Object>> builtIns(){return List.of(builtin(ACADEMIC_PURPLE,"Academic Purple","DokiAI Academic 紫色学术基线，适合稳健、清晰的论文答辩","通用学术"),builtin(SMALL_BEAR_WATERCOLOR_BLUE_V1,"小熊熊·水彩蓝","来自已审计小熊熊模板的可信水彩蓝 TemplatePack，适合论文答辩与图片展示","论文答辩/计算机/工科"));}
    private Map<String,Object> builtin(String id,String name,String description,String major){Map<String,Object> row=new LinkedHashMap<>();row.put("id",id);row.put("templateId",id);row.put("templatePackId",id);row.put("templateName",name);row.put("style",id);row.put("description",description);row.put("suitableMajor",major);row.put("builtIn",true);row.put("trusted",true);row.put("renderingV1Supported",true);return row;}
    private String recommendTemplatePack(Map<String,Object> project){String source=string(project.get("source_file_name")).toLowerCase(Locale.ROOT),topic=string(project.get("topic")),analysis=string(project.get("analysis_json"));boolean thesis=source.endsWith(".docx")||source.endsWith(".pdf")||source.contains("论文")||source.contains("答辩")||source.contains("毕业设计")||topic.contains("设计与实现")||analysis.contains("毕业设计");return thesis?SMALL_BEAR_WATERCOLOR_BLUE_V1:ACADEMIC_PURPLE;}
    private String recommendReason(String style,Map<String,Object> project){String major=string(project.get("major"));if(major.isBlank()||major.matches("[?？]+"))major="通用学术";String source=string(project.get("source_file_name"));int images=integer(project.get("image_count"),0),slides=Math.max(1,integer(project.get("target_slide_count"),16));String type=source.contains("开题")?"开题报告":source.toLowerCase(Locale.ROOT).endsWith(".docx")||source.contains("论文")?"论文":source.contains("实施")?"实施报告":"项目文档";String display=SMALL_BEAR_WATERCOLOR_BLUE_V1.equals(style)?"小熊熊·水彩蓝":"Academic Purple";return "根据专业“"+major+"”、"+type+"、图片比例 "+images+"/"+slides+" 与内容类型，推荐可信模板 "+display;}
    static boolean isBearTemplate(String name){return name!=null&&name.replace(" ","").contains("小熊熊");}
    private String classifyStyle(String name,List<String> colors,int pictures,int slides){String n=name.toLowerCase(Locale.ROOT);if(n.contains("科技")||n.contains("计算机"))return TECH_DEFENSE;if(n.contains("环境")||n.contains("景观")||n.contains("建筑")||n.contains("室内"))return ENVIRONMENT_DESIGN;if(n.contains("视觉")||n.contains("艺术")||n.contains("设计"))return VISUAL_COMMUNICATION;if(n.contains("商务")||n.contains("汇报")||n.contains("实施"))return BUSINESS;if(n.contains("极简")||n.contains("高级")||pictures>slides*2)return MINIMAL_PREMIUM;return SIMPLE_ACADEMIC;}
    private String suitableMajor(String style,String name){if(TECH_DEFENSE.equals(style))return "计算机/软件/工科";if(ENVIRONMENT_DESIGN.equals(style))return "环境/景观/室内";if(VISUAL_COMMUNICATION.equals(style))return "视觉传达/艺术设计";if(BUSINESS.equals(style))return "管理/商务/项目汇报";return name.contains("教师")?"教育/说课":"通用学术";}
    private String layoutVariant(String style){return switch(style){case TECH_DEFENSE->"tech";case ENVIRONMENT_DESIGN->"environment";case VISUAL_COMMUNICATION->"visual";case BUSINESS->"business";case MINIMAL_PREMIUM->"minimal";default->"academic";};}
    private Map<String,Object> project(String id,Long userId){List<Map<String,Object>> rows=jdbc.queryForList("SELECT p.*,(SELECT COUNT(*) FROM ppt_asset a WHERE a.project_id=p.id) image_count FROM ppt_project p WHERE p.id=? AND p.user_id=?",id,userId);if(rows.isEmpty())throw new IllegalArgumentException("PPT项目不存在或无权访问");return rows.get(0);}
    private void expandJson(Map<String,Object> row){if(row.containsKey("template_name"))row.put("templateName",row.get("template_name"));if(row.containsKey("suitable_major"))row.put("suitableMajor",row.get("suitable_major"));row.put("priorityTemplate",isBearTemplate(string(row.get("template_name"))));row.putIfAbsent("renderingV1Supported",false);row.putIfAbsent("trusted",false);Object m=readJson(string(row.get("metadata_json")));if(m instanceof Map<?,?> map&&!map.isEmpty())row.put("metadata",m);Object s=readJson(string(row.get("slide_types_json")));if(s instanceof List<?> list&&!list.isEmpty())row.put("slideTypes",s);}
    private String requireTrustedPack(String value){String trusted=trustedPackOrBlank(value);if(trusted.isBlank())throw new IllegalArgumentException("该模板尚未编译并通过 Rendering V1 安全校验，不能用于正式生成");return trusted;}
    private String trustedPackOrBlank(String value){if(ACADEMIC_PURPLE.equalsIgnoreCase(value))return ACADEMIC_PURPLE;if(SMALL_BEAR_WATERCOLOR_BLUE_V1.equalsIgnoreCase(value))return SMALL_BEAR_WATERCOLOR_BLUE_V1;return "";}
    private String firstNonBlank(String... values){for(String value:values)if(value!=null&&!value.isBlank())return value;return "";}
    private Map<String,Object> selectionMetadata(String templatePackId,PptxGenerator.TemplateProfile profile){Map<String,Object> metadata=new LinkedHashMap<>();metadata.put("schemaVersion","rendering-template-selection.v1");metadata.put("templatePackId",templatePackId);metadata.put("renderingV1Supported",true);metadata.put("trusted",true);metadata.put("profile",profile);return metadata;}
    private String color(List<String> colors,int index,String fallback){return colors!=null&&index<colors.size()?colors.get(index):fallback;}
    private String hex(Color c){return String.format("#%02X%02X%02X",c.getRed(),c.getGreen(),c.getBlue());}
    private Color paintColor(PaintStyle paint){if(paint instanceof PaintStyle.SolidPaint solid)return solid.getSolidColor().getColor();return null;}
    private String stripExtension(String name){int dot=name.lastIndexOf('.');return dot>0?name.substring(0,dot):name;}
    private Path inside(Path p){Path n=p.toAbsolutePath().normalize();if(!n.startsWith(root))throw new IllegalArgumentException("非法模板路径");return n;}
    private String json(Object value){try{return mapper.writeValueAsString(value);}catch(Exception e){throw new IllegalStateException("模板元数据序列化失败",e);}}
    private Object readJson(String value){try{return value==null||value.isBlank()?Map.of():mapper.readValue(value,new TypeReference<Object>(){});}catch(Exception e){return Map.of();}}
    private String string(Object value){return value==null?"":String.valueOf(value);}
    private int integer(Object value,int fallback){try{return value instanceof Number n?n.intValue():Integer.parseInt(string(value));}catch(Exception e){return fallback;}}

    private record LayoutAnalysis(String layoutStyle,boolean hasImageSlot,boolean hasLeftDecoration,boolean hasRightDecoration,SafeArea safeArea,double visualCenterX,String titleAlign,String contentAlign){}
    public record SafeArea(double left,double right,double top,double bottom){}
    public record UsableRegion(double x,double y,double width,double height,String usage){}
    public record TemplateAssetMetadata(String assetId,String assetRole,double x,double y,double width,double height){}
    public record TemplateSlideMetadata(int pageNumber,String slideId,String pageType,List<String> colors,String layout,List<UsableRegion> usableRegions,List<TemplateAssetMetadata> assets,int textShapeCount,int pictureCount,int chartCount,boolean hasTable,boolean hasImageSlot,boolean hasLeftDecoration,boolean hasRightDecoration,SafeArea safeArea,double visualCenterX,String titleAlign,String contentAlign){}
    public record TemplateMetadata(String templateName,String style,List<String> colors,List<String> fonts,String suitableMajor,List<String> slideTypes,int pageWidth,int pageHeight,int slideCount,int pictureCount,int chartCount,String layoutVariant,List<TemplateSlideMetadata> slides){}
}
