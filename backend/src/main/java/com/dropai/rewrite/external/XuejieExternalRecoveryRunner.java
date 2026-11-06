package com.dropai.rewrite.external;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.DependsOn;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@DependsOn({"documentRewriteServiceImpl", "xuejieExternalSchemaInitializer"})
@Order(Ordered.HIGHEST_PRECEDENCE + 100)
public class XuejieExternalRecoveryRunner implements ApplicationRunner {
    private final XuejieExternalJobStateRepository repository;
    private final XuejieExternalDocumentRewriteService service;

    public XuejieExternalRecoveryRunner(XuejieExternalJobStateRepository repository,
                                        XuejieExternalDocumentRewriteService service) {
        this.repository = repository;
        this.service = service;
    }

    @Override
    public void run(ApplicationArguments args) {
        repository.findRecoverable().stream()
                .filter(state -> XuejiePlatform.DAYA.name().equalsIgnoreCase(state.platform()))
                .forEach(service::recover);
    }
}
