package com.dropai.rewrite.service.diagram;

import com.dropai.rewrite.config.DiagramAssistantProperties;
import com.dropai.rewrite.service.diagram.DiagramIr.DiagramType;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class OllamaDiagramClientTest {
    @Test
    void sendsChatContractAndReadsSemanticIrJson() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        AtomicReference<String> captured = new AtomicReference<>();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/chat", exchange -> {
            captured.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            String semantic = "{\"type\":\"FLOWCHART\",\"nodes\":[{\"kind\":\"start\",\"text\":\"开始\"},{\"kind\":\"end\",\"text\":\"结束\"}],\"relations\":[{\"from\":\"开始\",\"to\":\"结束\",\"kind\":\"flow\"}]}";
            byte[] response = mapper.writeValueAsBytes(mapper.createObjectNode().set("message", mapper.createObjectNode().put("content", semantic)));
            exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
            exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        server.start();
        try {
            DiagramAssistantProperties properties = new DiagramAssistantProperties();
            properties.setOllamaEndpoint("http://127.0.0.1:" + server.getAddress().getPort() + "/api/chat");
            properties.setOllamaModel("dropai-diagram-ir:first-wave");
            properties.setHardLimit(Duration.ofSeconds(5));
            SemanticIrLiteAdapter adapter = new SemanticIrLiteAdapter(mapper);
            OllamaDiagramClient client = new OllamaDiagramClient(properties, mapper, adapter, HttpClient.newHttpClient());

            DoubaoDiagramClient.ModelResult result = client.generate(DiagramType.FLOWCHART, "画一个开始到结束的流程", null, ignored -> {});
            assertEquals("dropai-diagram-ir:first-wave", result.model());
            assertEquals(DiagramType.FLOWCHART, adapter.adapt(DiagramType.FLOWCHART, result.json(), "测试").diagramType());

            JsonNode request = mapper.readTree(captured.get());
            assertEquals("dropai-diagram-ir:first-wave", request.path("model").asText());
            assertFalse(request.path("stream").asBoolean(true));
            assertEquals("json", request.path("format").asText());
            assertEquals(2, request.path("messages").size());
            assertTrue(request.path("messages").get(1).path("content").asText().contains("FLOWCHART"));
            assertFalse(request.has("api_key"));
        } finally {
            server.stop(0);
        }
    }
}
