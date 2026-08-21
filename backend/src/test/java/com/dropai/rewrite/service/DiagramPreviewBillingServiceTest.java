package com.dropai.rewrite.service;

import com.dropai.rewrite.config.DiagramPreviewSchemaInitializer;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class DiagramPreviewBillingServiceTest {
    JdbcTemplate jdbc; PointService points; DiagramPreviewBillingService service;
    @BeforeEach void setup() throws Exception {
        JdbcDataSource ds=new JdbcDataSource();ds.setURL("jdbc:h2:mem:diagram"+System.nanoTime()+";MODE=MySQL;DB_CLOSE_DELAY=-1");
        jdbc=new JdbcTemplate(ds);new DiagramPreviewSchemaInitializer(jdbc,ds).run(null);
        points=mock(PointService.class);when(points.currentPoints(1L)).thenReturn(10);when(points.deductDiagramPreview(anyLong(),anyString(),anyString())).thenReturn(99L);
        service=new DiagramPreviewBillingService(jdbc,points);
    }
    @Test void concurrentSameHashCreatesOnlyOneRenderTask(){
        assertNotNull(service.createTask(1L,2L,"flowchart","abc","v1"));
        assertNull(service.createTask(1L,2L,"flowchart","abc","v1"));
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM diagram_render_task",Integer.class));
    }
    @Test void successfulFinalizationChargesOnceAndCanBeReused(){
        String task=service.createTask(1L,2L,"flowchart","abc","v1");service.rendered(task);
        var first=service.finalizeRendered(task,"preview",1L,2L,"flowchart","abc","v1","@Flowchart","<svg/>", List.of(new DiagramPreviewBillingService.ArtifactDraft("svg","READY","x",7,null)));
        assertTrue(first.charged());service.published(task,"preview");var second=service.finalizeRendered(task,"ignored",1L,2L,"flowchart","abc","v1","@Flowchart","<svg/>",List.of());assertFalse(second.charged());
        verify(points,times(1)).deductDiagramPreview(eq(1L),eq(task),anyString());
    }
    @Test void publicationFailureRefundsExactlyOnce(){
        when(points.refundDiagramPreview(anyLong(),anyString(),anyString())).thenReturn(100L);
        String task=service.createTask(1L,2L,"flowchart","refund-hash","v1");service.rendered(task);
        service.finalizeRendered(task,"refund-preview",1L,2L,"flowchart","refund-hash","v1","@Flowchart","<svg/>",List.of());
        service.refundPublishFailure(task,"refund-preview",1L,"disk full");
        service.refundPublishFailure(task,"refund-preview",1L,"retry");
        verify(points,times(1)).refundDiagramPreview(eq(1L),eq(task),anyString());
        assertEquals("REFUNDED",jdbc.queryForObject("SELECT status FROM diagram_render_task WHERE id=?",String.class,task));
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM diagram_preview_charge WHERE task_id=? AND kind='REFUND'",Integer.class,task));
    }
    @Test void previewOwnershipCannotCrossUsers(){
        String task=service.createTask(1L,2L,"flowchart","owned-hash","v1");service.rendered(task);
        service.finalizeRendered(task,"owned-preview",1L,2L,"flowchart","owned-hash","v1","@Flowchart","<svg/>",List.of());
        service.published(task,"owned-preview");
        assertNotNull(service.ownedPreview("owned-preview",1L));
        assertNull(service.ownedPreview("owned-preview",9L));
    }
}
