package com.dropai.rewrite.modules.stepExportEngine;

import com.dropai.rewrite.config.CadWorkerProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
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
        if (!properties.isEnabled()) {
            result.put("status", "UNAVAILABLE");
            result.put("errorCode", "CAD_WORKER_DISABLED");
            return result;
        }
        try {
            Path script = locator.locateScript();
            result.put("scriptLocated", true);
            result.put("scriptFileName", script.getFileName().toString());
            Process process = new ProcessBuilder(properties.getPython(), script.toString(), "--health")
                    .redirectErrorStream(true)
                    .start();
            boolean finished = process.waitFor(Duration.ofSeconds(Math.min(30, Math.max(5, properties.getTimeoutSeconds()))).toMillis(), TimeUnit.MILLISECONDS);
            String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            if (!finished) {
                process.destroyForcibly();
                result.put("status", "UNAVAILABLE");
                result.put("errorCode", "CAD_WORKER_HEALTH_TIMEOUT");
                result.put("message", "CAD Worker health check timed out");
                return result;
            }
            result.put("exitCode", process.exitValue());
            if (process.exitValue() != 0) {
                result.put("status", "UNAVAILABLE");
                result.put("errorCode", "CAD_WORKER_HEALTH_FAILED");
                result.put("message", sanitize(output));
                return result;
            }
            Map<?, ?> parsed = objectMapper.readValue(output, Map.class);
            parsed.forEach((key, value) -> result.put(String.valueOf(key), value));
            result.put("status", Boolean.TRUE.equals(parsed.get("cadKernelAvailable")) ? "READY" : "UNAVAILABLE");
            return result;
        } catch (Exception exception) {
            result.put("status", "UNAVAILABLE");
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
}
