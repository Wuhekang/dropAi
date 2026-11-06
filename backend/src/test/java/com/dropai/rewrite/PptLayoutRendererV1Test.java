package com.dropai.rewrite;

import com.dropai.rewrite.service.ppt.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import java.nio.file.*;
import static org.junit.jupiter.api.Assertions.*;

class PptLayoutRendererV1Test {
    private final ObjectMapper mapper=new ObjectMapper();
    @Test void generatesEditableFortyPageHealthDeck()throws Exception{
        Path source=Path.of("storage/ppt/content-planner-v2-local/health-management-asset-map-v1.json");
        Assumptions.assumeTrue(Files.isRegularFile(source));
        var mapped=mapper.readValue(source.toFile(),PptAssetMapperV1.MappingResult.class);
        var layout=new PptLayoutPlannerV1().plan(mapped);
        assertTrue(layout.renderReady(),layout.issues().toString());assertEquals(mapped.pageCount(),layout.pageCount());assertEquals("LAYOUT_TREE",layout.treeType());
        Path output=Path.of("storage/ppt/output/基于Spring Boot的个人健康管理系统的设计与实现.pptx");Path report=Path.of("storage/ppt/output/render-report.json");
        var result=new PptRendererV1(new PptRenderValidatorV1(),mapper).render(layout,output,report);
        assertEquals(40,result.report().pageCount());assertEquals(0,result.report().textOverflow());assertEquals(0,result.report().imageOverflow());assertEquals(0,result.report().placeholder());assertTrue(result.report().pptOpenable());assertTrue(Files.size(output)>100_000);
    }
}
