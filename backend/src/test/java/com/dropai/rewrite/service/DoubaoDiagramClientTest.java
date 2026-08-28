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
    @Test void summarizesLongSourceToHardCharacterLimitBeforeDiagramGeneration() throws Exception {
        AtomicReference<String> requestBody=new AtomicReference<>();
        HttpServer server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        String longSummary="主题=光敏电阻调光；起点=初始化ADC与PWM；主链=采集照度>换算>比较；分支=范围外关灯/范围内调PWM；循环/终点=继续则采集/停止则结束";
        server.createContext("/chat",exchange->{requestBody.set(new String(exchange.getRequestBody().readAllBytes(),StandardCharsets.UTF_8));String content=new ObjectMapper().writeValueAsString(java.util.Map.of("summary",longSummary));byte[] body=new ObjectMapper().writeValueAsBytes(java.util.Map.of("choices",java.util.List.of(java.util.Map.of("message",java.util.Map.of("content",content)))));exchange.getResponseHeaders().set("Content-Type","application/json");exchange.sendResponseHeaders(200,body.length);exchange.getResponseBody().write(body);exchange.close();});server.start();
        try{
            DiagramAssistantProperties p=new DiagramAssistantProperties();p.setApiKey("secret-test-key");p.setEndpoint("http://127.0.0.1:"+server.getAddress().getPort()+"/chat");p.setHardLimit(Duration.ofSeconds(3));
            var result=new DoubaoDiagramClient(p,new ObjectMapper()).summarize("一段很长的原始业务说明",80,DiagramType.FLOWCHART);
            assertTrue(result.summary().length()<=80);
            assertTrue(result.summary().contains("主题="));
            assertTrue(result.summary().contains("分支="));
            assertTrue(result.summary().endsWith("结束"));
            var json=new ObjectMapper().readTree(requestBody.get());
            assertFalse(json.path("stream").asBoolean());
            assertEquals(256,json.path("max_completion_tokens").asInt());
            assertFalse(requestBody.get().contains("secret-test-key"));
        }finally{server.stop(0);}
    }

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
            assertEquals(0,json.path("temperature").asDouble());
            assertEquals(4096,json.path("max_completion_tokens").asInt());
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
