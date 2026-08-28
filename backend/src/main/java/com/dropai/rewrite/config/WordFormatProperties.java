package com.dropai.rewrite.config;

import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.util.Locale;

@Component
public class WordFormatProperties {
    private final Environment environment;

    public WordFormatProperties(Environment environment) {
        this.environment = environment;
    }

    public boolean enabled() {
        return bool("word-format.enabled", "WORD_FORMAT_ENABLED", true);
    }

    public String python() {
        return value("word-format.python", "WORD_FORMAT_PYTHON", "python");
    }

    public String worker() {
        return value("word-format.worker", "WORD_FORMAT_WORKER", "document-format-tool/format_cli.py");
    }

    public Path dataDir() {
        return Path.of(value("word-format.data-dir", "WORD_FORMAT_DATA_DIR", "storage/word-format"))
                .toAbsolutePath()
                .normalize();
    }

    public long maxSourceBytes() {
        return number("word-format.max-source-bytes", "WORD_FORMAT_MAX_SOURCE_BYTES", 100L * 1024 * 1024);
    }

    public long maxTemplateBytes() {
        return number("word-format.max-template-bytes", "WORD_FORMAT_MAX_TEMPLATE_BYTES", 30L * 1024 * 1024);
    }

    public boolean legacyTemplatesEnabled() {
        boolean windows = System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("windows");
        return bool("word-format.legacy-templates-enabled", "WORD_FORMAT_LEGACY_TEMPLATES_ENABLED", windows);
    }

    public long maxExpandedBytes() {
        return number("word-format.max-expanded-bytes", "WORD_FORMAT_MAX_EXPANDED_BYTES", 1024L * 1024 * 1024);
    }

    public int maxInstructionsChars() {
        return integer("word-format.max-instructions-chars", "WORD_FORMAT_MAX_INSTRUCTIONS_CHARS", 20_000);
    }

    public int timeoutSeconds() {
        return integer("word-format.timeout-seconds", "WORD_FORMAT_TIMEOUT_SECONDS", 600);
    }

    public int maxConcurrent() {
        return Math.max(1, integer("word-format.max-concurrent", "WORD_FORMAT_MAX_CONCURRENT", 1));
    }

    public int queueCapacity() {
        return Math.max(1, integer("word-format.queue-capacity", "WORD_FORMAT_QUEUE_CAPACITY", 20));
    }

    private String value(String property, String environmentName, String fallback) {
        String result = environment.getProperty(property);
        if (result == null || result.isBlank()) {
            result = environment.getProperty(environmentName);
        }
        return result == null || result.isBlank() ? fallback : result.trim();
    }

    private int integer(String property, String environmentName, int fallback) {
        try {
            return Integer.parseInt(value(property, environmentName, String.valueOf(fallback)));
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private long number(String property, String environmentName, long fallback) {
        try {
            return Long.parseLong(value(property, environmentName, String.valueOf(fallback)));
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private boolean bool(String property, String environmentName, boolean fallback) {
        return Boolean.parseBoolean(value(property, environmentName, String.valueOf(fallback)));
    }
}
