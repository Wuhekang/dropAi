package com.dropai.rewrite.mechanicalpaper;

import com.dropai.rewrite.config.*;
import com.dropai.rewrite.entity.DocumentJobRecord;
import com.dropai.rewrite.mapper.DocumentJobMapper;
import com.dropai.rewrite.service.*;
import com.dropai.rewrite.service.image.DoubaoImageProvider;
import com.dropai.rewrite.service.writing.DoubaoWebSearchProvider;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.boot.SpringApplication;
import org.springframework.core.env.*;
import org.springframework.web.client.RestClient;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Explicit paid smoke: real text/search/images/export, isolated task store and mocked billing. */
@EnabledIfSystemProperty(named="mechanical.paper.ark-smoke",matches="true")
class MechanicalPaperArkSmokeTest {
    @Test void realModelsGenerateTitleOnlyPaper() throws Exception {
        var mapper=new ObjectMapper();
        var env=new StandardEnvironment();
        new MechanicalPaperDesktopEnvironment().postProcessEnvironment(env,new SpringApplication());
        Path root=Path.of("target","mechanical-paper-live",System.getProperty("mechanical.paper.ark-run","run-"+System.currentTimeMillis())).toAbsolutePath();
        Files.createDirectories(root);
        env.getPropertySources().addFirst(new MapPropertySource("smoke",Map.of(
            "ai.doubao.web-search-enabled","true","mechanical.paper.output-dir",root.toString(),"DOUBAO_IMAGE_OUTPUT_DIR",root.resolve("provider-images").toString())));
        var props=new DoubaoProperties();
        props.setApiKey(env.getProperty("DOUBAO_API_KEY"));
        props.setModel(env.getProperty("DOUBAO_MODEL",props.getModel()));
        props.setWebSearchModel(env.getProperty("DOUBAO_WEB_SEARCH_MODEL",props.getWebSearchModel()));
        props.setWebSearchEnabled(true);props.setReadTimeoutSeconds(180);
        var text=new MatrixDesignService(props,mapper,RestClient.builder());
        var image=new DoubaoImageProvider(env);
        var search=new DoubaoWebSearchProvider(props,RestClient.builder(),mapper);
        var photos=new MechanicalPaperPhotoService(search,text);
        var exporter=new MechanicalPaperExporter(new MechanicalPaperFieldUpdater(env));
        var repository=mock(DocumentJobMapper.class);
        var points=mock(PointService.class);var billing=mock(MechanicalPaperBilling.class);
        ConcurrentMap<String,DocumentJobRecord> records=new ConcurrentHashMap<>();
        when(repository.selectList(any())).thenReturn(List.of());
        when(repository.insert(any(DocumentJobRecord.class))).thenAnswer(call->{var record=(DocumentJobRecord)call.getArgument(0);records.put(record.getJobId(),record);return 1;});
        when(repository.selectById(anyString())).thenAnswer(call->records.get(call.getArgument(0)));
        when(repository.updateById(any(DocumentJobRecord.class))).thenAnswer(call->{
            var update=(DocumentJobRecord)call.getArgument(0);var record=records.get(update.getJobId());
            record.setStatus(update.getStatus());record.setParagraphsJson(update.getParagraphsJson());
            Files.writeString(root.resolve("state.json"),update.getParagraphsJson());return 1;
        });
        var service=new MechanicalPaperService(repository,mapper,text,image,photos,exporter,points,billing,env);
        try {
            assertEquals(true,service.readiness().get("ready"));
            String id;
            Path snapshot=root.resolve("state.json");
            if(Files.isRegularFile(snapshot)) {
                var state=mapper.readValue(Files.readString(snapshot),MechanicalPaperState.class);
                for(String figureId:System.getProperty("mechanical.paper.ark-redo","").split(",")) {
                    var task=state.tasks.get("image:"+figureId);if(task!=null)task.status="waiting";
                }
                if(!System.getProperty("mechanical.paper.ark-redo","").isBlank())state.status="FAILED";
                if(!System.getProperty("mechanical.paper.ark-redo","").isBlank()||Boolean.getBoolean("mechanical.paper.ark-export")) {
                    state.status="FAILED";state.tasks.get("export").status="waiting";
                }
                var record=new DocumentJobRecord();record.setJobId(state.id);record.setUserId(1L);record.setSourceFeature(MechanicalPaperService.FEATURE);record.setStatus(state.status);record.setParagraphsJson(mapper.writeValueAsString(state));records.put(state.id,record);
                id=state.id;service.retry(1L,id);
            } else id=service.start(1L,"板类零件钻孔夹具结构设计").get("id").toString();
            long deadline=System.nanoTime()+TimeUnit.MINUTES.toNanos(12);int previous=-1;
            Map<String,Object> state;
            do {
                state=service.get(1L,id);int progress=(int)state.get("progress");
                if(progress!=previous){System.out.println("ARK_SMOKE progress="+progress+" stage="+state.get("stage"));previous=progress;}
                if(!"RUNNING".equals(state.get("status")))break;
                Thread.sleep(1500);
            }while(System.nanoTime()<deadline);
            System.out.println("ARK_SMOKE output="+root.resolve(id));
            assertEquals("SUCCESS",state.get("status"),state.toString());
            assertTrue(Files.size(root.resolve(id).resolve("paper.docx"))>100_000);
            var completed=mapper.readValue(records.get(id).getParagraphsJson(),MechanicalPaperState.class);
            assertTrue(completed.wordCount>=6000&&completed.wordCount<=9000);
            assertEquals(15,completed.tasks.entrySet().stream().filter(e->e.getKey().startsWith("image:")&&"completed".equals(e.getValue().status)).count());
        }finally{service.close();}
    }
}
