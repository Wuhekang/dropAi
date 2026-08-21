package com.dropai.rewrite;

import com.dropai.rewrite.service.ppt.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.poi.xslf.usermodel.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class PptProductionEngineConsistencyTest {
    @TempDir Path temp;
    @Test void productionAdapterUsesLayoutRendererAndRejectsInternalFields()throws Exception{ObjectMapper mapper=new ObjectMapper();Map<String,Object> project=Map.of("topic","健康管理系统","english_topic","Health Management System","presenter","高瑞康","major","软件工程","advisor","蒋辉","student_number","6022203537");Map<String,Object> section=Map.of("id","s1","title","项目背景与需求");Map<String,Object> slide=new LinkedHashMap<>();slide.put("section_id","s1");slide.put("slide_type","CONTENT");slide.put("title","项目背景与价值");slide.put("body_boxes_json",mapper.writeValueAsString(List.of("健康数据统一管理","AI辅助个性化分析","提升服务效率")));slide.put("asset_ids_json","[]");slide.put("chapter_title","第一章");slide.put("content_summary","系统通过统一管理与智能分析提升个人健康服务能力。");slide.put("speaker_notes","pagePurpose=内部字段绝不能输出");var mapped=new PptProductionTreeAdapter(mapper).adapt(project,List.of(section),List.of(slide),List.of());var layout=new PptLayoutPlannerV1().plan(mapped);Path output=temp.resolve("production.pptx");var result=new PptRendererV1(new PptRenderValidatorV1(),mapper).render(layout,output,temp.resolve("render-report.json"));assertTrue(result.report().valid(),result.report().issues().toString());try(XMLSlideShow show=new XMLSlideShow(Files.newInputStream(output))){StringBuilder text=new StringBuilder();for(var page:show.getSlides())for(var shape:page.getShapes())if(shape instanceof XSLFTextShape t)text.append(t.getText());for(String forbidden:List.of("pagePurpose","answerQuestion","Click to edit Master","Second level","未填写"))assertFalse(text.toString().contains(forbidden),forbidden);assertTrue(text.toString().contains("高瑞康"));assertTrue(text.toString().contains("软件工程"));}}
}
