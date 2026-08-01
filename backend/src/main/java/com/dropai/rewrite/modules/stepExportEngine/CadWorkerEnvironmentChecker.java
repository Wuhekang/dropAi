package com.dropai.rewrite.modules.stepExportEngine;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Service
public class CadWorkerEnvironmentChecker {
    private final CadWorkerLocator locator;
    private final ObjectMapper objectMapper;

    public CadWorkerEnvironmentChecker(CadWorkerLocator locator, ObjectMapper objectMapper) {
        this.locator = locator;
        this.objectMapper = objectMapper;
    }

    public Map<String, Object> check() {
        Map<String, Object> result = new LinkedHashMap<>();
        String python = locator.locatePython();
        result.put("pythonPath", python);
        result.put("isolatedEnvironment", locator.usingIsolatedPython());
        try {
            Process process = new ProcessBuilder(python, "-c", checkScript())
                    .redirectErrorStream(true)
                    .start();
            boolean finished = process.waitFor(Duration.ofSeconds(20).toMillis(), TimeUnit.MILLISECONDS);
            String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            if (!finished) {
                process.destroyForcibly();
                result.put("status", "DOWN");
                result.put("errorCode", "CAD_WORKER_ENV_CHECK_TIMEOUT");
                result.put("message", "CAD worker Python environment check timed out");
                return result;
            }
            result.put("exitCode", process.exitValue());
            Map<?, ?> parsed = parseJson(output);
            parsed.forEach((key, value) -> result.put(String.valueOf(key), value));
            if (process.exitValue() != 0) {
                result.put("status", "DOWN");
                result.putIfAbsent("errorCode", "CAD_WORKER_ENV_CHECK_FAILED");
                result.putIfAbsent("message", sanitize(output));
            }
            if (!Boolean.TRUE.equals(result.get("hasDelimitedList"))) {
                result.put("status", "DOWN");
                result.put("errorCode", "PY_PARSING_INCOMPATIBLE");
                result.put("message", "pyparsing is incompatible; install pyparsing>=3.1 in backend/cad_worker/.venv");
            }
            if (!Boolean.TRUE.equals(result.get("cadqueryImportOk"))) {
                result.put("status", "DOWN");
                result.putIfAbsent("errorCode", "CADQUERY_IMPORT_FAILED");
                result.putIfAbsent("message", "cadquery import failed");
            }
            result.putIfAbsent("status", "UP");
            result.putIfAbsent("errorCode", "");
            result.putIfAbsent("message", "");
            return result;
        } catch (Exception exception) {
            result.put("status", "DOWN");
            result.put("errorCode", "CAD_WORKER_PYTHON_NOT_RUNNABLE");
            result.put("message", sanitize(exception.getMessage()));
            return result;
        }
    }

    private String checkScript() {
        return """
                import json, sys
                result = {"python": sys.executable}
                try:
                    import pyparsing
                    result["pyparsingVersion"] = getattr(pyparsing, "__version__", "")
                    result["hasDelimitedList"] = hasattr(pyparsing, "DelimitedList")
                except Exception as exc:
                    result["pyparsingImportError"] = str(exc)
                    result["hasDelimitedList"] = False
                try:
                    import cadquery
                    result["cadqueryVersion"] = getattr(cadquery, "__version__", "")
                    result["cadqueryImportOk"] = True
                except Exception as exc:
                    result["cadqueryImportOk"] = False
                    result["cadqueryImportError"] = str(exc)
                print(json.dumps(result, ensure_ascii=False))
                sys.exit(0 if result.get("cadqueryImportOk") and result.get("hasDelimitedList") else 2)
                """;
    }

    private Map<?, ?> parseJson(String output) {
        if (output == null || output.isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(output.trim(), Map.class);
        } catch (Exception ignored) {
            return Map.of("rawOutput", sanitize(output));
        }
    }

    private String sanitize(String value) {
        if (value == null || value.isBlank()) return "";
        String clean = value.replaceAll("[\\r\\n\\t]+", " ").trim();
        return clean.length() > 700 ? clean.substring(0, 700) + "..." : clean;
    }
}
