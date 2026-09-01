package com.dropai.rewrite.external;

import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Loads the application-owned platform rewrite profiles from the classpath.
 *
 * <p>The catalog intentionally performs no prompt composition or model routing. It only loads
 * the application-owned Daya profile and caches its UTF-8 content.</p>
 */
@Component
public class PlatformRewriteSkillCatalog {
    private static final String DAYA_RESOURCE_PATH = "skills/platform-ai-daya/SKILL.md";

    private final ConcurrentMap<XuejiePlatform, String> cache = new ConcurrentHashMap<>();

    public String load(XuejiePlatform platform) {
        if (platform != XuejiePlatform.DAYA) throw new IllegalArgumentException("仅支持大雅平台");
        return cache.computeIfAbsent(platform, ignored -> read(DAYA_RESOURCE_PATH));
    }

    private String read(String resourcePath) {
        ClassPathResource resource = new ClassPathResource(resourcePath);
        try {
            return resource.getContentAsString(StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new IllegalStateException("平台 Skill 文件读取失败：" + resourcePath, exception);
        }
    }
}
