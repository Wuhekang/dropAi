package com.dropai.rewrite.modules.standardPartSelector;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Optional;

@Service
public class StandardPartCache {
    private final ObjectMapper objectMapper;
    private final Path cacheDir = Path.of("data", "standard-parts", "cache");

    public StandardPartCache(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public Optional<StandardPartResult> find(StandardPartQuery query) {
        Path file = cacheDir.resolve(key(query) + ".json");
        if (!Files.exists(file)) return Optional.empty();
        try {
            return Optional.of(objectMapper.readValue(file.toFile(), StandardPartResult.class));
        } catch (Exception ignored) {
            return Optional.empty();
        }
    }

    public void save(StandardPartQuery query, StandardPartResult result) {
        try {
            Files.createDirectories(cacheDir);
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(cacheDir.resolve(key(query) + ".json").toFile(), result);
        } catch (Exception ignored) {
            // Cache writes are best-effort and must not block generation.
        }
    }

    private String key(StandardPartQuery query) {
        try {
            String raw = query.getCategory() + "|" + query.getName() + "|" + query.getRequirements();
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(raw.getBytes(StandardCharsets.UTF_8))).substring(0, 24);
        } catch (Exception e) {
            return String.valueOf(Math.abs((query.getCategory() + query.getName()).hashCode()));
        }
    }
}
