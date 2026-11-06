package com.dropai.rewrite.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class DiagramDownloadTicketServiceTest {
    @Test void ticketIsOpaqueAndCanOnlyBeConsumedOnce(){
        DiagramDownloadTicketService service=new DiagramDownloadTicketService();String token=service.issue(7L,"preview","png","测试.png");
        assertEquals(64,token.length());var ticket=service.consume(token);assertEquals(7L,ticket.userId());assertEquals("preview",ticket.previewId());assertEquals("测试.png",ticket.fileName());
        assertThrows(IllegalArgumentException.class,()->service.consume(token));
    }
}
