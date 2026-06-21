package com.dropai.rewrite.config;

import jakarta.annotation.PostConstruct;
import org.apache.poi.openxml4j.util.ZipSecureFile;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

@Configuration
public class PoiZipSecurityConfig {

    private final double minInflateRatio;

    public PoiZipSecurityConfig(
            @Value("${app.poi.min-inflate-ratio:${POI_MIN_INFLATE_RATIO:0.001}}") double minInflateRatio
    ) {
        this.minInflateRatio = minInflateRatio;
    }

    @PostConstruct
    public void configureZipSecurity() {
        if (minInflateRatio <= 0) {
            throw new IllegalStateException("app.poi.min-inflate-ratio must be greater than 0 to keep zip bomb protection enabled");
        }
        ZipSecureFile.setMinInflateRatio(minInflateRatio);
    }
}
