package com.dropai.rewrite.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.web.embedded.tomcat.TomcatServletWebServerFactory;
import org.springframework.boot.web.server.WebServerFactoryCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Optional Windows compatibility switch for hosts where the JDK NIO selector
 * cannot create its internal loopback pipe. Production keeps Tomcat NIO unless
 * the switch is explicitly enabled.
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "dokiai.tomcat.nio2-enabled", havingValue = "true")
public class TomcatProtocolConfig {
    @Bean
    WebServerFactoryCustomizer<TomcatServletWebServerFactory> tomcatNio2Protocol() {
        return factory -> factory.setProtocol("org.apache.coyote.http11.Http11Nio2Protocol");
    }
}
