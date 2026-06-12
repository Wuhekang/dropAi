package com.dropai.rewrite;

import com.dropai.rewrite.config.MatrixDesignProperties;
import com.dropai.rewrite.service.MatrixDesignService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MatrixDesignServiceTests {
    @Test
    void usesOpenAiCompatibleChatCompletionsProtocol() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper();
        AtomicReference<String> authorization = new AtomicReference<>();
        AtomicReference<JsonNode> requestBody = new AtomicReference<>();
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/api/v1/chat/completions", exchange -> {
            authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
            requestBody.set(objectMapper.readTree(exchange.getRequestBody()));
            byte[] response = "{\"choices\":[{\"message\":{\"content\":\"OK\"}}]}".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        server.start();

        try {
            MatrixDesignProperties properties = new MatrixDesignProperties();
            properties.setApiKey("matrix-key");
            properties.setModel("claude-opus-4-7");
            properties.setEndpoint("http://localhost:" + server.getAddress().getPort() + "/api/v1/chat/completions");
            MatrixDesignService service = new MatrixDesignService(properties, objectMapper, RestClient.builder());

            assertEquals("OK", service.generate("system prompt", "user prompt"));
            assertEquals("Bearer matrix-key", authorization.get());
            assertEquals("claude-opus-4-7", requestBody.get().path("model").asText());
            assertEquals("system", requestBody.get().path("messages").path(0).path("role").asText());
            assertEquals("system prompt", requestBody.get().path("messages").path(0).path("content").asText());
            assertEquals("user prompt", requestBody.get().path("messages").path(1).path("content").asText());
        } finally {
            server.stop(0);
        }
    }

    @Test
    void retriesEmptyResponsesAndReadsContentParts() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper();
        AtomicInteger requests = new AtomicInteger();
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/api/v1/chat/completions", exchange -> {
            exchange.getRequestBody().readAllBytes();
            String json = requests.incrementAndGet() == 1
                    ? "{\"choices\":[{\"message\":{\"role\":\"assistant\"},\"finish_reason\":\"stop\"}]}"
                    : "{\"choices\":[{\"message\":{\"content\":[{\"type\":\"text\",\"text\":\"设计结果\"}]}}]}";
            byte[] response = json.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        server.start();

        try {
            MatrixDesignProperties properties = new MatrixDesignProperties();
            properties.setApiKey("matrix-key");
            properties.setEndpoint("http://localhost:" + server.getAddress().getPort() + "/api/v1/chat/completions");
            MatrixDesignService service = new MatrixDesignService(properties, objectMapper, RestClient.builder());

            assertEquals("设计结果", service.generate("system prompt", "user prompt"));
            assertEquals(2, requests.get());
        } finally {
            server.stop(0);
        }
    }

    @Test
    void retries429AndReturnsChineseLimitMessage() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper();
        AtomicInteger requests = new AtomicInteger();
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/api/v1/chat/completions", exchange -> {
            requests.incrementAndGet();
            exchange.getRequestBody().readAllBytes();
            byte[] response = "{\"error\":\"rate limited\"}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(429, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        server.start();
        try {
            MatrixDesignProperties properties = new MatrixDesignProperties();
            properties.setApiKey("matrix-key");
            properties.setEndpoint("http://localhost:" + server.getAddress().getPort() + "/api/v1/chat/completions");
            MatrixDesignService service = new MatrixDesignService(properties, objectMapper, RestClient.builder());
            IllegalStateException error = assertThrows(IllegalStateException.class, () -> service.generate("system", "user"));
            assertEquals(3, requests.get());
            assertTrue(error.getMessage().contains("请求受限"));
        } finally {
            server.stop(0);
        }
    }
}
