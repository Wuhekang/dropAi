package com.dropai.rewrite;

import com.dropai.rewrite.service.ppt.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.nio.file.Path;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class PptAssetMapperV1Test {
    @TempDir Path temp;
    @Test void mapsExactFigureWithoutChangingPageTree()throws Exception{
        Path image=temp.resolve("figure_4_10.png");ImageIO.write(new BufferedImage(1280,720,BufferedImage.TYPE_INT_RGB),"png",image.toFile());
        var validated=validatedTree("figure_4_10");var assets=List.<Map<String,Object>>of(Map.of("id","figure_4_10","path",image.toString(),"width",1280,"height",720));
        var result=new PptAssetMapperV1().map(new PptAssetMapperV1.MappingRequest(validated,assets,List.of(),""));
        assertTrue(result.assetPlanReady(),result.issues().toString());assertFalse(result.renderReady());assertEquals(validated.slideTree().size(),result.pageCount());
        var binding=result.slideTree().stream().flatMap(s->s.assets().stream()).findFirst().orElseThrow();assertEquals("EXACT_FIGURE_ID",binding.matchStrategy());assertEquals("SCREENSHOT",binding.type());assertEquals("LANDSCAPE",binding.aspectRatioType());assertFalse(binding.crop());assertFalse(binding.placeholder());
    }
    @Test void emitsPlaceholderAndErrorForMissingMandatoryImage(){var validated=validatedTree("figure_missing");var result=new PptAssetMapperV1().map(new PptAssetMapperV1.MappingRequest(validated,List.of(),List.of(),""));assertFalse(result.assetPlanReady());var binding=result.slideTree().stream().flatMap(s->s.assets().stream()).findFirst().orElseThrow();assertTrue(binding.placeholder());assertEquals(PptAssetMapperV1.DEFAULT_PLACEHOLDER,binding.source());assertTrue(result.issues().stream().anyMatch(i->i.code().equals("IMAGE_FILE_MISSING")));}
    private PptOutlineValidatorV1.ValidationResult validatedTree(String figureId){var background=new PptContentPlannerV2.CandidatePage("项目背景","BACKGROUND","为什么开展项目？",List.of("健康管理需求"),"项目面向健康数据统一管理需求，说明系统建设的现实价值。","第一章",.9);var image=new PptContentPlannerV2.CandidatePage("IMAGE_EVIDENCE","健康趋势可视化分析","RESULT","该图说明什么？",List.of("趋势图表"),"该图展示健康趋势可视化分析，说明指标变化及历史对比结果。","第四章",.95,"PROOF",new PptContentPlannerV2.SourceRefs("chapter_4",List.of(),figureId,List.of(),List.of()),true,List.of());var summary=new PptContentPlannerV2.CandidatePage("项目总结","SUMMARY","完成了什么？",List.of("完成核心功能"),"项目完成健康数据管理与智能分析功能，并通过测试验证运行效果。","第六章",.9);var outline=new PptOutlinePlannerV1().plan(new PptOutlinePlannerV1.OutlineRequest(List.of(background,image,summary),12));var metadata=Map.of("title","健康管理系统","englishTitle","Health Management System","presenter","测试学生","major","软件工程","advisor","测试教师","studentNumber","20260001");var validated=new PptOutlineValidatorV1().validate(new PptOutlineValidatorV1.ValidationRequest(metadata,outline));assertTrue(validated.valid(),validated.issues().toString());return validated;}
}
