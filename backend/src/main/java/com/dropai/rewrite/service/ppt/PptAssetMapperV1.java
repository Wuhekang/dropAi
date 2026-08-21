package com.dropai.rewrite.service.ppt;

import org.springframework.stereotype.Service;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/** Exact, read-only page-to-asset binding. It never changes page copy, order or count. */
@Service
public class PptAssetMapperV1 {
    public static final String DEFAULT_PLACEHOLDER="classpath:knowledge/ppt/missing-image-placeholder.svg";

    public MappingResult map(MappingRequest request){
        if(request==null||request.presentationTree()==null)throw new IllegalArgumentException("AssetMapper输入页面树不能为空");
        Map<String,Map<String,Object>> images=registry(request.assets(),"id");Map<String,Map<String,Object>> tables=registry(request.tables(),"id");List<MappingIssue> issues=new ArrayList<>();List<MappedSlideNode> slides=new ArrayList<>();Set<String> boundFigures=new LinkedHashSet<>();
        for(var page:safe(request.presentationTree().slideTree())){List<AssetBinding> bindings=new ArrayList<>();if("IMAGE".equals(page.pageType()))bindings.add(bindImage(page,images,boundFigures,issues,placeholder(request.placeholderSource())));if("TABLE".equals(page.pageType()))bindings.addAll(bindTables(page,tables,issues));slides.add(new MappedSlideNode(page,bindings));}
        if(slides.size()!=request.presentationTree().slideTree().size())issues.add(issue("ERROR","PAGE_COUNT_CHANGED",0,"AssetMapper不得增加或删除页面"));for(int i=0;i<slides.size();i++)if(slides.get(i).page().pageNumber()!=request.presentationTree().slideTree().get(i).pageNumber())issues.add(issue("ERROR","PAGE_ORDER_CHANGED",slides.get(i).page().pageNumber(),"AssetMapper不得改变页面顺序"));boolean ready=issues.stream().noneMatch(i->"ERROR".equals(i.severity()));return new MappingResult(ready,false,"ASSET_MAPPED_TREE",slides.size(),slides,issues);
    }

    private AssetBinding bindImage(PptOutlineValidatorV1.FullSlideNode page,Map<String,Map<String,Object>> registry,Set<String> bound,List<MappingIssue> issues,String placeholder){String id=page.sourceRefs()==null?"":page.sourceRefs().figureId();if(id.isBlank()||!bound.add(id)){String code=id.isBlank()?"FIGURE_ID_MISSING":"FIGURE_BOUND_MORE_THAN_ONCE";issues.add(issue("ERROR",code,page.pageNumber(),"图片页必须且只能绑定一个figureId"));return placeholder(id,placeholder);}
        Map<String,Object> asset=registry.get(id);if(asset==null){issues.add(issue(page.mandatoryAsset()?"ERROR":"WARNING","IMAGE_FILE_MISSING",page.pageNumber(),"未找到图片素材: "+id));return placeholder(id,placeholder);}Path path=path(asset.get("path"));if(path==null||!Files.isRegularFile(path)){issues.add(issue(page.mandatoryAsset()?"ERROR":"WARNING","IMAGE_FILE_MISSING",page.pageNumber(),"图片文件不存在: "+id));return placeholder(id,placeholder);}int[] size=imageSize(path,asset);if(size[0]<=0||size[1]<=0){issues.add(issue("ERROR","IMAGE_UNREADABLE",page.pageNumber(),"图片无法读取: "+id));return placeholder(id,placeholder);}double ratio=(double)size[0]/size[1];String ratioType=ratio>=1.2?"LANDSCAPE":ratio<=.83?"PORTRAIT":"SQUARE";int quality=100;if(size[0]<640||size[1]<360){quality=55;issues.add(issue("WARNING","IMAGE_LOW_RESOLUTION",page.pageNumber(),"图片分辨率偏低: "+size[0]+"x"+size[1]));}if(ratio>3.2||ratio<.32){quality=Math.min(quality,60);issues.add(issue("WARNING","IMAGE_EXTREME_ASPECT_RATIO",page.pageNumber(),"图片比例异常: "+String.format(Locale.ROOT,"%.2f",ratio)));}String type=switch(page.imageRole()){case"PROOF"->"SCREENSHOT";case"EFFECT"->"EFFECT_IMAGE";default->"DIAGRAM";};return new AssetBinding(id,path.toAbsolutePath().normalize().toString(),type,size[0],size[1],ratio,ratioType,false,false,quality,"EXACT_FIGURE_ID");}
    private List<AssetBinding> bindTables(PptOutlineValidatorV1.FullSlideNode page,Map<String,Map<String,Object>> registry,List<MappingIssue> issues){List<String> ids=page.sourceRefs()==null?List.of():safe(page.sourceRefs().tableIds());List<AssetBinding> out=new ArrayList<>();for(String id:ids){if(!registry.containsKey(id)){issues.add(issue("WARNING","TABLE_SOURCE_MISSING",page.pageNumber(),"未找到表格素材: "+id));continue;}out.add(new AssetBinding(id,"document://"+id,"TABLE_DATA",0,0,0,"TABLE",false,false,100,"EXACT_TABLE_ID"));}return out;}
    private Map<String,Map<String,Object>> registry(List<Map<String,Object>> values,String key){Map<String,Map<String,Object>> out=new LinkedHashMap<>();for(Map<String,Object> value:safe(values)){String id=string(value.get(key));if(!id.isBlank())out.putIfAbsent(id,value);}return out;}
    private int[] imageSize(Path path,Map<String,Object> asset){try{BufferedImage image=ImageIO.read(path.toFile());if(image!=null)return new int[]{image.getWidth(),image.getHeight()};}catch(Exception ignored){}return new int[]{integer(asset.get("width")),integer(asset.get("height"))};}
    private AssetBinding placeholder(String id,String source){return new AssetBinding(id,source,"PLACEHOLDER",1280,720,16d/9,"LANDSCAPE",false,true,0,"PLACEHOLDER");}
    private String placeholder(String value){return value==null||value.isBlank()?DEFAULT_PLACEHOLDER:value;}
    private MappingIssue issue(String severity,String code,int page,String message){return new MappingIssue(severity,code,page,message);}private Path path(Object value){try{return value==null?null:Path.of(String.valueOf(value));}catch(Exception e){return null;}}private int integer(Object value){try{return value instanceof Number n?n.intValue():Integer.parseInt(String.valueOf(value));}catch(Exception e){return 0;}}private String string(Object value){return value==null?"":String.valueOf(value);}private <T> List<T> safe(List<T> value){return value==null?List.of():value;}
    public record MappingRequest(PptOutlineValidatorV1.ValidationResult presentationTree,List<Map<String,Object>> assets,List<Map<String,Object>> tables,String placeholderSource){}
    public record AssetBinding(String assetId,String source,String type,int width,int height,double aspectRatio,String aspectRatioType,boolean crop,boolean placeholder,int qualityScore,String matchStrategy){}
    public record MappedSlideNode(PptOutlineValidatorV1.FullSlideNode page,List<AssetBinding> assets){}
    public record MappingIssue(String severity,String code,int pageNumber,String message){}
    public record MappingResult(boolean assetPlanReady,boolean renderReady,String treeType,int pageCount,List<MappedSlideNode> slideTree,List<MappingIssue> issues){}
}
