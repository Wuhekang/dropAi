package com.dropai.rewrite.config;

import com.dropai.rewrite.auth.AuthInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class CorsConfig implements WebMvcConfigurer {
    private final AuthInterceptor authInterceptor;
    public CorsConfig(AuthInterceptor authInterceptor) { this.authInterceptor = authInterceptor; }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(authInterceptor)
                .addPathPatterns("/api/rewrite/**", "/api/document/**", "/api/documents/**", "/api/engineering-writing/**", "/api/mechanical/**", "/api/points/**", "/api/recharge/**", "/api/notices/**", "/api/admin/**", "/api/school-viewer/**", "/api/existing-tech/**", "/api/computer-generator/**", "/api/writing/**", "/api/literature/**", "/api/ppt/**", "/api/diagram/**", "/api/word-format/**")
                .excludePathPatterns("/api/rewrite/ai/status", "/api/computer-generator/preview-content/**", "/api/recharge/notify", "/api/diagram/download-ticket/**");
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOriginPatterns("*")
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .allowCredentials(false)
                .maxAge(3600);
    }
}
