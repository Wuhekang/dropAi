package com.dropai.rewrite.mechanicalpaper;

import com.dropai.rewrite.entity.DocumentJobRecord;
import com.dropai.rewrite.mapper.DocumentJobMapper;
import com.dropai.rewrite.service.*;
import com.dropai.rewrite.service.image.*;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.env.MockEnvironment;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class MechanicalPaperWorkflowTest {
    @TempDir Path dir;
    final ObjectMapper mapper=new ObjectMapper();
    MechanicalPaperService service;
    @AfterEach void close(){if(service!=null)service.close();}
    @Test void allSecondarySectionsRunConcurrentlyAndRestartPreservesCompletion() throws Exception {
        var repository=mock(DocumentJobMapper.class);var text=mock(MatrixDesignService.class);var images=mock(DoubaoImageProvider.class);
        var photos=mock(MechanicalPaperPhotoService.class);var exporter=mock(MechanicalPaperExporter.class);
        var points=mock(PointService.class);var billing=mock(MechanicalPaperBilling.class);
        var env=new MockEnvironment().withProperty("ai.doubao.web-search-enabled","true").withProperty("mechanical.paper.output-dir",dir.toString());
        ConcurrentMap<String,DocumentJobRecord> records=new ConcurrentHashMap<>();
        when(repository.selectList(any())).thenAnswer(call->List.of());
        when(repository.insert(any(DocumentJobRecord.class))).thenAnswer(call->{DocumentJobRecord record=call.getArgument(0);records.put(record.getJobId(),record);return 1;});
        when(repository.selectById(anyString())).thenAnswer(call->records.get(call.getArgument(0)));
        when(repository.updateById(any(DocumentJobRecord.class))).thenAnswer(call->{DocumentJobRecord update=call.getArgument(0),record=records.get(update.getJobId());record.setStatus(update.getStatus());record.setParagraphsJson(update.getParagraphsJson());return 1;});
        when(text.apiKeyConfigured()).thenReturn(true);when(text.modelName()).thenReturn("existing-text-model");
        when(text.inspectImage(anyString(),anyString(),anyString())).thenReturn("{\"accepted\":true}");
        var health=new ImageGenerationResult();health.setEnabled(true);health.setApiKeyConfigured(true);health.setEndpoint("https://provider.invalid/images");health.setModel("configured-image-model");when(images.health()).thenReturn(health);
        Path bitmap=dir.resolve("generated.png");Files.write(bitmap,new byte[]{1,2,3});
        var generated=new ImageGenerationResult();generated.setStatus("success");generated.setFilePath(bitmap.toString());when(images.generate(any())).thenReturn(generated);
        when(photos.find(anyString(),any())).thenReturn(new MechanicalPaperPhotoService.Photo("photo.png","https://manufacturer.invalid/item","https://manufacturer.invalid/item.png"));
        CountDownLatch started=new CountDownLatch(MechanicalPaperPlan.SECTIONS.size()),release=new CountDownLatch(1);
        var design=design();
        when(text.generateJson(anyString(),anyString())).thenAnswer(call->{
            String prompt=call.getArgument(1);
            if(prompt.contains("章节槽"))return design.toString();
            if(prompt.contains("根据实际正文写中英文摘要"))return "{\"abstract\":\"解析计算与结构方案摘要\",\"englishAbstract\":\"Analytical calculation and structural design.\"}";
            started.countDown();assertTrue(release.await(8,TimeUnit.SECONDS));
            var section=MechanicalPaperPlan.SECTIONS.stream().filter(s->prompt.contains("二级小节 "+s.id()+"，")).findFirst().orElseThrow();
            return section(section).toString();
        });
        service=new MechanicalPaperService(repository,mapper,text,images,photos,exporter,points,billing,env);
        var initial=service.start(1L,"板类零件钻孔夹具结构设计");String id=initial.get("id").toString();
        assertTrue(started.await(8,TimeUnit.SECONDS),"all 14 branches must enter the model before any is released");
        var progress=service.get(1L,id);assertTrue((int)progress.get("progress")<100);
        assertThrows(IllegalArgumentException.class,()->service.get(2L,id));
        release.countDown();
        long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(8);
        while(!"SUCCESS".equals(service.get(1L,id).get("status")) && System.nanoTime()<deadline)Thread.sleep(15);
        assertEquals("SUCCESS",service.get(1L,id).get("status"));
        verify(images,times(14)).generate(any());verify(photos,times(1)).find(anyString(),any());verify(exporter).export(any(),any());verify(billing,times(1)).complete(eq(1L),any());
        var record=records.get(id);var snapshot=mapper.readTree(record.getParagraphsJson());assertTrue(snapshot.path("wordCount").asInt()>=6000);
        service.close();service=null;
        ((ObjectNode)snapshot).put("status","RUNNING");record.setParagraphsJson(snapshot.toString());record.setStatus("RUNNING");
        service=new MechanicalPaperService(repository,mapper,text,images,photos,exporter,points,billing,env);
        var restored=service.get(1L,id);assertEquals("INTERRUPTED",restored.get("status"));assertTrue(restored.get("tasks").toString().contains("completed"));
        service.retry(1L,id);
        deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(8);
        while(!"SUCCESS".equals(service.get(1L,id).get("status")) && System.nanoTime()<deadline)Thread.sleep(15);
        assertEquals("SUCCESS",service.get(1L,id).get("status"));
        verify(images,times(14)).generate(any());verify(photos,times(1)).find(anyString(),any()); // no regeneration of finished work
    }
    @Test void missingImageConfigurationDoesNotCreateOrChargeTask() {
        var repository=mock(DocumentJobMapper.class);when(repository.selectList(any())).thenReturn(List.of());
        var images=mock(DoubaoImageProvider.class);when(images.health()).thenReturn(new ImageGenerationResult());
        var points=mock(PointService.class);
        service=new MechanicalPaperService(repository,mapper,mock(MatrixDesignService.class),images,mock(MechanicalPaperPhotoService.class),mock(MechanicalPaperExporter.class),points,mock(MechanicalPaperBilling.class),new MockEnvironment());
        assertThrows(IllegalStateException.class,()->service.start(1L,"夹具结构设计"));verify(repository,never()).insert(any(DocumentJobRecord.class));verify(points,never()).checkPoints(anyString());
    }
    JsonNode design() {
        var root=mapper.createObjectNode().put("inputs","结构参数与材料").put("calculations","解析计算公式").put("abstract","摘要").put("englishAbstract","Abstract");root.putArray("keywords").add("夹具");
        var parts=root.putArray("parts");for(int i=0;i<4;i++)parts.addObject().put("name","零件"+i).put("geometry","对应几何尺寸");
        var sections=root.putArray("sections");for(var s:MechanicalPaperPlan.SECTIONS)sections.addObject().put("id",s.id()).put("title",s.title());
        var figures=root.putArray("figures");for(var f:MechanicalPaperPlan.FIGURES)if(!f.kind().equals("photo"))figures.addObject().put("id",f.id()).put("caption",f.caption()).put("description","统一几何");
        return root;
    }
    JsonNode section(MechanicalPaperPlan.Section s) {
        var root=mapper.createObjectNode();root.putArray("paragraphs").add("结构".repeat(s.words()/2));
        var formulas=root.putArray("formulas");if(s.chapter()==3)formulas.add("F=100N").add("M=200Nmm").add("E=206000MPa");
        var tables=root.putArray("tables");if(List.of("2.1","4.1","5.3").contains(s.id())){var table=tables.addObject();table.putArray("headers").add("参数").add("值");table.putArray("rows").addArray().add("参数").add("1");}
        return root;
    }
}
