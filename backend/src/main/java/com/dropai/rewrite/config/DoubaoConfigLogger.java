package com.dropai.rewrite.config;

import com.dropai.rewrite.service.ai.AiRequestType;
import com.dropai.rewrite.service.ai.DoubaoModelRouter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Component
public class DoubaoConfigLogger implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(DoubaoConfigLogger.class);

    private final DoubaoProperties properties;
    private final DoubaoModelRouter modelRouter;

    public DoubaoConfigLogger(DoubaoProperties properties, DoubaoModelRouter modelRouter) {
        this.properties = properties;
        this.modelRouter = modelRouter;
    }

    @Override
    public void run(ApplicationArguments args) {
        log.info("========== DOUBAO CONFIG ==========");
        log.info("Endpoint: {}", properties.getEndpoint());
        log.info("Text Model: {}", modelRouter.resolveModel(AiRequestType.TEXT));
        log.info("Mechanical Vision Model: {}", safeMechanicalModel());
        log.info("API Key: {}", hasText(properties.getApiKey()) ? "configured" : "missing");
        log.info("===================================");
    }

    private String safeMechanicalModel() {
        try {
            return modelRouter.resolveModel(AiRequestType.MECHANICAL_VISION);
        } catch (RuntimeException exception) {
            return "invalid: " + exception.getMessage();
        }
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
