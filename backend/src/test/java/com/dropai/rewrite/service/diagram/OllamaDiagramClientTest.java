package com.dropai.rewrite.service.diagram;

import com.dropai.rewrite.config.DiagramAssistantProperties;
import com.dropai.rewrite.service.diagram.DiagramIr.DiagramType;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.io.ByteArrayOutputStream;
import java.net.http.HttpRequest;
import java.net.http.HttpClient;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Flow;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class OllamaDiagramClientTest {
    @Test
    void sendsChatContractAndReadsSemanticIrJson() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        String semantic = "{\"type\":\"FLOWCHART\",\"nodes\":[{\"kind\":\"start\",\"text\":\"开始\"},{\"kind\":\"end\",\"text\":\"结束\"}],\"relations\":[{\"from\":\"开始\",\"to\":\"结束\",\"kind\":\"flow\"}]}";
        String responseBody=mapper.writeValueAsString(mapper.createObjectNode().set("message",mapper.createObjectNode().put("content",semantic)));
        HttpClient http=mock(HttpClient.class);
        @SuppressWarnings("unchecked") HttpResponse<String> response=mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(200);when(response.body()).thenReturn(responseBody);
        when(http.send(any(HttpRequest.class),any(HttpResponse.BodyHandler.class))).thenReturn(response);
        DiagramAssistantProperties properties = new DiagramAssistantProperties();
        properties.setOllamaEndpoint("http://127.0.0.1:11434/api/chat");
        properties.setOllamaModel("dropai-diagram-ir:first-wave");
        SemanticIrLiteAdapter adapter = new SemanticIrLiteAdapter(mapper);
        OllamaDiagramClient client = new OllamaDiagramClient(properties, mapper, adapter, http);

        DoubaoDiagramClient.ModelResult result = client.generate(DiagramType.FLOWCHART, "画一个开始到结束的流程", null, ignored -> {});
        assertEquals("dropai-diagram-ir:first-wave", result.model());
        assertEquals(DiagramType.FLOWCHART, adapter.adapt(DiagramType.FLOWCHART, result.json(), "测试").diagramType());

        ArgumentCaptor<HttpRequest> requestCaptor=ArgumentCaptor.forClass(HttpRequest.class);
        verify(http).send(requestCaptor.capture(),any(HttpResponse.BodyHandler.class));
        JsonNode request = mapper.readTree(readBody(requestCaptor.getValue()));
        assertEquals(Duration.ofSeconds(5), requestCaptor.getValue().timeout().orElseThrow());
        assertEquals("dropai-diagram-ir:first-wave", request.path("model").asText());
        assertFalse(request.path("stream").asBoolean(true));
        assertEquals("json", request.path("format").asText());
        assertEquals(2, request.path("messages").size());
        assertTrue(request.path("messages").get(1).path("content").asText().contains("FLOWCHART"));
        assertFalse(request.has("api_key"));
    }

    private static String readBody(HttpRequest request)throws Exception{
        ByteArrayOutputStream output=new ByteArrayOutputStream();CompletableFuture<Void> completed=new CompletableFuture<>();
        request.bodyPublisher().orElseThrow().subscribe(new Flow.Subscriber<>(){
            public void onSubscribe(Flow.Subscription subscription){subscription.request(Long.MAX_VALUE);}
            public void onNext(ByteBuffer buffer){byte[] bytes=new byte[buffer.remaining()];buffer.get(bytes);output.writeBytes(bytes);}
            public void onError(Throwable throwable){completed.completeExceptionally(throwable);}
            public void onComplete(){completed.complete(null);}
        });
        completed.get();return output.toString(StandardCharsets.UTF_8);
    }
}
