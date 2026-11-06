package com.dropai.rewrite.mechanicalpaper;

import com.dropai.rewrite.service.PointService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class MechanicalPaperBillingTest {
    @Test void completedJobIsNotChargedTwice() throws Exception {
        var jdbc=mock(JdbcTemplate.class);var points=mock(PointService.class);var state=new MechanicalPaperState();state.id="mp_"+"a".repeat(32);state.title="夹具设计";
        when(jdbc.queryForMap(anyString(),eq(state.id))).thenReturn(Map.of("user_id",1L,"points_charged",false),Map.of("user_id",1L,"points_charged",true));
        var billing=new MechanicalPaperBilling(jdbc,points,new ObjectMapper());
        billing.complete(1L,state);billing.complete(1L,state);
        verify(points,times(1)).deductFeatureForJob(1L,state.id,PointService.MECHANICAL_PAPER,"机械说明书生成");
        assertEquals("RUNNING",state.status,"in-memory success must not be exposed before transaction commit");
    }
    @Test void billingRejectsWrongOwner() {
        var jdbc=mock(JdbcTemplate.class);var points=mock(PointService.class);var state=new MechanicalPaperState();state.id="mp_"+"a".repeat(32);
        when(jdbc.queryForMap(anyString(),eq(state.id))).thenReturn(Map.of("user_id",2L,"points_charged",false));
        assertThrows(IllegalArgumentException.class,()->new MechanicalPaperBilling(jdbc,points,new ObjectMapper()).complete(1L,state));
        verifyNoInteractions(points);
    }
}
