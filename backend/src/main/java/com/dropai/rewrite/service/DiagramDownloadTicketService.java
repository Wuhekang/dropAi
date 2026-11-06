package com.dropai.rewrite.service;

import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class DiagramDownloadTicketService {
    // Download links are prepared when a paid preview is restored. Keep them valid long
    // enough for the user to inspect the diagram, while retaining one-time bearer tokens.
    private static final long TTL_SECONDS=1800;
    private final Map<String,Ticket> tickets=new ConcurrentHashMap<>();

    public String issue(long userId,String previewId,String format,String fileName){
        cleanup();String token=UUID.randomUUID().toString().replace("-","")+UUID.randomUUID().toString().replace("-","");
        tickets.put(token,new Ticket(userId,previewId,format,fileName,Instant.now().plusSeconds(TTL_SECONDS)));return token;
    }

    public Ticket consume(String token){
        Ticket ticket=tickets.remove(token);if(ticket==null||ticket.expiresAt().isBefore(Instant.now()))throw new IllegalArgumentException("下载地址不存在或已失效，请重新点击下载按钮");return ticket;
    }

    public long ttlSeconds(){return TTL_SECONDS;}
    private void cleanup(){Instant now=Instant.now();tickets.entrySet().removeIf(entry->entry.getValue().expiresAt().isBefore(now));}
    public record Ticket(long userId,String previewId,String format,String fileName,Instant expiresAt){}
}
