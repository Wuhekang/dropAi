package com.dropai.rewrite.service.image;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.env.MockEnvironment;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;
import java.nio.file.Path;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.jupiter.api.Assertions.*;

class DoubaoImageProviderTest {
    @TempDir Path dir;
    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings={"seedream-configured","doubao-seedream-5-0-flash-260915"})
    void arkPayloadUsesSingleImageAndReferenceWithoutLeakingReferenceInAudit(String model) throws Exception {
        var mapper=new ObjectMapper();var seen=new AtomicReference<String>();
        var bitmap=new ByteArrayOutputStream();ImageIO.write(new BufferedImage(128,128,BufferedImage.TYPE_INT_RGB),"png",bitmap);
        var body=mapper.createObjectNode();body.putArray("data").addObject().put("b64_json",Base64.getEncoder().encodeToString(bitmap.toByteArray()));
        var builder=RestClient.builder();var server=MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("https://test.invalid/images/generations"))
            .andExpect(request->seen.set(((org.springframework.mock.http.client.MockClientHttpRequest)request).getBodyAsString()))
            .andRespond(withSuccess(mapper.writeValueAsString(body),org.springframework.http.MediaType.APPLICATION_JSON));
            var env=new MockEnvironment().withProperty("DOUBAO_IMAGE_ENABLED","true").withProperty("DOUBAO_API_KEY","test-key")
                .withProperty("DOUBAO_IMAGE_MODEL",model).withProperty("DOUBAO_IMAGE_ENDPOINT","https://test.invalid/images/generations")
                .withProperty("DOUBAO_IMAGE_OUTPUT_DIR",dir.toString());
            var request=new ImageGenerationRequest();request.setPrompt("test drawing");request.setReferenceImageUrls(List.of("data:image/png;base64,reference"));
            var result=new DoubaoImageProvider(env,builder.build()).generate(request);
            assertEquals("success",result.getStatus(),result.getMessage());var payload=mapper.readTree(seen.get());
            assertEquals("2K",payload.path("size").asText());
            if(model.contains("5-0-flash"))assertFalse(payload.has("sequential_image_generation"));
            else assertEquals("disabled",payload.path("sequential_image_generation").asText());
            assertEquals("b64_json",payload.path("response_format").asText());assertFalse(payload.path("watermark").asBoolean(true));
            assertFalse(payload.has("n"));assertEquals(request.getReferenceImageUrls().get(0),payload.path("image").get(0).asText());
            String audit=java.nio.file.Files.readString(dir.resolve("image-generation-audit.json"));
            assertFalse(audit.contains("test-key"));assertFalse(audit.contains("data:image/png;base64,reference"));
            server.verify();
    }
    @Test void missingImageModelNeverFallsBackToTextModel() {
        var env=new MockEnvironment().withProperty("DOUBAO_MODEL","text-model-only");
        assertEquals("",new DoubaoImageProvider(env).health().getModel());
    }
}
