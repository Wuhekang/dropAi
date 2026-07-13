package com.dropai.rewrite;

import com.dropai.rewrite.config.DoubaoProperties;
import com.dropai.rewrite.service.ai.AiRequestType;
import com.dropai.rewrite.service.ai.DoubaoMechanicalVisionService;
import com.dropai.rewrite.service.ai.DoubaoModelRouter;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DoubaoMechanicalVisionServiceTests {
    @Test
    void mechanicalVisionUsesExactSeed21Model() {
        DoubaoProperties properties = new DoubaoProperties();
        properties.setModel("doubao-seed-2-0-lite-260428");
        properties.setTextModel("doubao-seed-2-0-lite-260428");
        properties.setMechanicalVisionModel("doubao-seed-2-1-turbo-260628");

        DoubaoModelRouter router = new DoubaoModelRouter(properties);

        assertEquals("doubao-seed-2-0-lite-260428", router.resolveModel(AiRequestType.TEXT));
        assertEquals("doubao-seed-2-1-turbo-260628", router.resolveModel(AiRequestType.MECHANICAL_VISION));
    }

    @Test
    void mechanicalVisionDoesNotFallbackToTextModel() {
        DoubaoProperties properties = new DoubaoProperties();
        properties.setTextModel("doubao-seed-2-0-lite-260428");
        properties.setMechanicalVisionModel("");

        DoubaoModelRouter router = new DoubaoModelRouter(properties);

        assertThrows(IllegalStateException.class, () -> router.resolveModel(AiRequestType.MECHANICAL_VISION));
    }

    @Test
    void mechanicalVisionRejectsLooseModelAlias() {
        DoubaoProperties properties = new DoubaoProperties();
        properties.setMechanicalVisionModel("doubao-seed-2-1-turbo");

        DoubaoModelRouter router = new DoubaoModelRouter(properties);

        assertThrows(IllegalStateException.class, () -> router.resolveModel(AiRequestType.MECHANICAL_VISION));
    }

    @Test
    void imagePayloadIsBase64DataUrlNotLocalPathText() {
        DoubaoProperties properties = new DoubaoProperties();
        DoubaoModelRouter router = new DoubaoModelRouter(properties);
        DoubaoMechanicalVisionService service = new DoubaoMechanicalVisionService(
                properties,
                router,
                new ObjectMapper(),
                RestClient.builder()
        );

        String imageUrl = service.buildImageDataUrl(new byte[]{1, 2, 3, 4}, "assembly.png");

        assertTrue(imageUrl.startsWith("data:image/png;base64,"));
        assertTrue(!imageUrl.contains("C:\\"));
    }

    @Test
    void unsupportedImageTypesAreRejected() {
        DoubaoProperties properties = new DoubaoProperties();
        DoubaoModelRouter router = new DoubaoModelRouter(properties);
        DoubaoMechanicalVisionService service = new DoubaoMechanicalVisionService(
                properties,
                router,
                new ObjectMapper(),
                RestClient.builder()
        );

        assertThrows(IllegalArgumentException.class, () -> service.buildImageDataUrl(new byte[]{1, 2, 3}, "part.step"));
    }
}
