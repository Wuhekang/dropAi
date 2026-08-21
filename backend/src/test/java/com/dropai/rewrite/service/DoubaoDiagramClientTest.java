package com.dropai.rewrite.service;

import com.dropai.rewrite.config.DiagramAssistantProperties;
import com.dropai.rewrite.service.diagram.*;
import com.dropai.rewrite.service.diagram.DiagramIr.DiagramType;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class DoubaoDiagramClientTest {
    @Test void sendsSingleStreamingStructuredRequestWithConfiguredLimits() throws Exception {
        AtomicReference<String> requestBody=new AtomicReference<>();
        HttpServer server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        server.createContext("/chat",exchange->{requestBody.set(new String(exchange.getRequestBody().readAllBytes(),StandardCharsets.UTF_8));byte[] body=("data: {\"choices\":[{\"delta\":{\"content\":\"{\\\"version\\\":\\\"1.0\\\"}\"}}]}\n\n"+"data: [DONE]\n\n").getBytes(StandardCharsets.UTF_8);exchange.getResponseHeaders().set("Content-Type","text/event-stream");exchange.sendResponseHeaders(200,body.length);exchange.getResponseBody().write(body);exchange.close();});server.start();
        try{
            DiagramAssistantProperties p=new DiagramAssistantProperties();p.setApiKey("test-only-key");p.setEndpoint("http://127.0.0.1:"+server.getAddress().getPort()+"/chat");p.setConnectTimeout(Duration.ofSeconds(1));p.setFirstByteTimeout(Duration.ofSeconds(1));p.setReadIdleTimeout(Duration.ofSeconds(1));p.setHardLimit(Duration.ofSeconds(3));
            var prompt=new DiagramPromptFactory.Prompt("system","user",new ObjectMapper().createObjectNode(),10);
            var result=new DoubaoDiagramClient(p,new ObjectMapper()).generate(DiagramType.FLOWCHART,prompt,n->{});
            assertEquals("{\"version\":\"1.0\"}",result.json());
            var json=new ObjectMapper().readTree(requestBody.get());
            assertTrue(json.path("stream").asBoolean());
            assertEquals("disabled",json.path("thinking").path("type").asText());
            assertEquals(.1,json.path("temperature").asDouble());
            assertEquals(1800,json.path("max_completion_tokens").asInt());
            assertEquals("json_object",json.path("response_format").path("type").asText());
            assertFalse(requestBody.get().contains("test-only-key"));
        }finally{server.stop(0);}
    }

    @Test void upstreamHeartbeatsDoNotHideTrueDataIdleTimeout() throws Exception {
        HttpServer server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        server.createContext("/chat",exchange->{exchange.getResponseHeaders().set("Content-Type","text/event-stream");exchange.sendResponseHeaders(200,0);try{for(int i=0;i<8;i++){exchange.getResponseBody().write(": keepalive\n\n".getBytes(StandardCharsets.UTF_8));exchange.getResponseBody().flush();Thread.sleep(25);}}catch(Exception ignored){}finally{exchange.close();}});server.start();
        try{
            DiagramAssistantProperties p=new DiagramAssistantProperties();p.setApiKey("x");p.setEndpoint("http://127.0.0.1:"+server.getAddress().getPort()+"/chat");p.setConnectTimeout(Duration.ofSeconds(1));p.setFirstByteTimeout(Duration.ofSeconds(1));p.setReadIdleTimeout(Duration.ofMillis(100));p.setHardLimit(Duration.ofSeconds(2));
            var prompt=new DiagramPromptFactory.Prompt("s","u",new ObjectMapper().createObjectNode(),2);
            var error=assertThrows(DiagramGenerationException.class,()->new DoubaoDiagramClient(p,new ObjectMapper()).generate(DiagramType.FLOWCHART,prompt,n->{}));
            assertEquals("MODEL_IDLE_TIMEOUT",error.code());
        }finally{server.stop(0);}
    }
}
