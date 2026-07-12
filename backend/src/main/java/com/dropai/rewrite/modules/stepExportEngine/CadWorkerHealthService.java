package com.dropai.rewrite.modules.stepExportEngine;

import com.dropai.rewrite.config.CadWorkerProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Service
public class CadWorkerHealthService {
    private final CadWorkerProperties properties;
    private final CadWorkerLocator locator;
    private final ObjectMapper objectMapper;

    public CadWorkerHealthService(CadWorkerProperties properties, CadWorkerLocator locator, ObjectMapper objectMapper) {
        this.properties = properties;
        this.locator = locator;
        this.objectMapper = objectMapper;
    }

    public Map<String, Object> health() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("enabled", properties.isEnabled());
        result.put("pythonCommand", properties.getPython());
        result.put("engine", properties.getEngine());
        result.put("lastCheckedAt", Instant.now().toString());
        if (!properties.isEnabled()) {
            result.put("status", "DOWN");
            result.put("errorCode", "CAD_WORKER_DISABLED");
            return result;
        }
        try {
            Path script = locator.locateScript();
            result.put("scriptLocated", true);
            result.put("scriptFileName", script.getFileName().toString());
            Process process = new ProcessBuilder(properties.getPython(), script.toAbsolutePath().toString(), "--health")
                    .redirectErrorStream(true)
                    .start();
            boolean finished = process.waitFor(Duration.ofSeconds(Math.min(30, Math.max(5, properties.getTimeoutSeconds()))).toMillis(), TimeUnit.MILLISECONDS);
            String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            if (!finished) {
                process.destroyForcibly();
                result.put("status", "DOWN");
                result.put("errorCode", "CAD_WORKER_HEALTH_TIMEOUT");
                result.put("message", "CAD Worker health check timed out");
                return result;
            }
            result.put("exitCode", process.exitValue());
            Map<?, ?> parsed = parseJson(output);
            if (!parsed.isEmpty()) {
                parsed.forEach((key, value) -> result.put(String.valueOf(key), value));
                result.remove("sysPath");
                result.put("sysPathCount", parsed.get("sysPath") instanceof java.util.List<?> list ? list.size() : 0);
            }
            if (process.exitValue() != 0) {
                result.put("status", "DOWN");
                result.putIfAbsent("errorCode", "CAD_WORKER_HEALTH_FAILED");
                result.putIfAbsent("message", sanitize(output));
                return result;
            }
            result.put("status", "UP".equals(parsed.get("status")) || Boolean.TRUE.equals(parsed.get("cadKernelAvailable")) ? "UP" : "DOWN");
            result.putIfAbsent("errorCode", "");
            result.putIfAbsent("message", "");
            return result;
        } catch (Exception exception) {
            result.put("status", "DOWN");
            result.put("errorCode", "CAD_WORKER_SCRIPT_NOT_FOUND");
            result.put("message", "CAD Worker script is not reachable from the configured path or packaged resource");
            return result;
        }
    }

    private String sanitize(String value) {
        if (value == null || value.isBlank()) return "";
        String clean = value.replaceAll("[\\r\\n\\t]+", " ").trim();
        return clean.length() > 500 ? clean.substring(0, 500) + "..." : clean;
    }

    private Map<?, ?> parseJson(String output) {
        if (output == null || output.isBlank()) return Map.of();
        try {
            return objectMapper.readValue(output, Map.class);
        } catch (Exception ignored) {
            return Map.of();
        }
    }
}
