package com.dropai.rewrite.service.ppt;

import org.apache.poi.xslf.usermodel.XMLSlideShow;
import org.springframework.stereotype.Service;
import java.io.InputStream;
import java.nio.file.*;
import java.util.*;

@Service
public class PptRenderValidatorV1 {
    private static final List<String> FORBIDDEN=List.of("pagePurpose=","answerQuestion=","sourceChapter=","semanticAlignmentScore","mandatoryAsset","sourceRefs","Click to edit Master text styles","Second level","Third level","Fourth level","Fifth level","<#>","占位符","未填写");
    public Report validate(PptLayoutPlannerV1.LayoutResult tree,Path ppt,long renderTimeMs){int text=0,image=0,overlap=0,placeholder=0;List<String> issues=new ArrayList<>();
        for(var s:tree.slideTree()){if(s.pageNumber()<1||s.source().title()==null||s.source().title().isBlank())issues.add("第"+s.pageNumber()+"页缺少标题");if(s.elements().isEmpty())issues.add("第"+s.pageNumber()+"页为空");for(var e:s.elements()){if(e.x()<0||e.y()<0||e.x()+e.width()>PptLayoutPlannerV1.WIDTH+.01||e.y()+e.height()>PptLayoutPlannerV1.HEIGHT+.01){if("IMAGE".equals(e.type()))image++;else text++;}if(e.asset()!=null&&e.asset().placeholder())placeholder++;if(FORBIDDEN.stream().anyMatch(e.text()::contains))issues.add("第"+s.pageNumber()+"页包含模板残留");}}
        boolean open=false;try(InputStream in=Files.newInputStream(ppt);XMLSlideShow show=new XMLSlideShow(in)){open=show.getSlides().size()==tree.pageCount();for(int index=0;index<show.getSlides().size();index++){StringBuilder visible=new StringBuilder();for(var shape:show.getSlides().get(index).getShapes())if(shape instanceof org.apache.poi.xslf.usermodel.XSLFTextShape textShape)visible.append(textShape.getText()).append('\n');for(String forbidden:FORBIDDEN)if(visible.toString().contains(forbidden))issues.add("第"+(index+1)+"页包含禁止输出: "+forbidden);}}catch(Exception e){issues.add("PPTX无法打开: "+e.getMessage());}
        return new Report(tree.pageCount(),text,image,overlap,placeholder,open,renderTimeMs,issues);
    }
    public record Report(int pageCount,int textOverflow,int imageOverflow,int overlap,int placeholder,boolean pptOpenable,long renderTimeMs,List<String> issues){public boolean valid(){return textOverflow==0&&imageOverflow==0&&overlap==0&&placeholder==0&&pptOpenable&&issues.isEmpty();}}
}
