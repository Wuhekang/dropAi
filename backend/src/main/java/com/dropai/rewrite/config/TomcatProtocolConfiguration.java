package com.dropai.rewrite.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.web.embedded.tomcat.TomcatServletWebServerFactory;
import org.springframework.boot.web.server.WebServerFactoryCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnProperty(name = "dropai.tomcat.nio2", havingValue = "true")
public class TomcatProtocolConfiguration {
    @Bean
    WebServerFactoryCustomizer<TomcatServletWebServerFactory> tomcatNio2Protocol() {
        return factory -> factory.setProtocol("org.apache.coyote.http11.Http11Nio2Protocol");
    }
}
