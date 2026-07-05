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
                .addPathPatterns("/api/rewrite/**", "/api/document/**", "/api/documents/**", "/api/engineering-writing/**", "/api/design-packages/**", "/api/points/**", "/api/recharge/**", "/api/notices/**", "/api/admin/**", "/api/existing-tech/**", "/api/computer-generator/**")
                .excludePathPatterns("/api/rewrite/ai/status", "/api/computer-generator/preview-content/**", "/api/recharge/notify");
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
