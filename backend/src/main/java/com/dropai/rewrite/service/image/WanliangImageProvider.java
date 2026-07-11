package com.dropai.rewrite.service.image;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class WanliangImageProvider implements ImageGenerationProvider {
    private final Environment environment;
    private final ObjectMapper mapper = new ObjectMapper();
    private final HttpClient httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(20)).build();

    public WanliangImageProvider(Environment environment) {
        this.environment = environment;
    }

    @Override
    public ImageGenerationResult generate(ImageGenerationRequest request) {
        long start = System.currentTimeMillis();
        ImageGenerationResult result = health();
        Map<String, Object> audit = baseAudit(result);
        try {
            if (!result.isEnabled()) {
                result.setStatus("disabled");
                result.setMessage("WANLIANG_IMAGE_ENABLED is not true; image task skipped without blocking CAD deliverables.");
                writeAudit(audit, "disabled", result.getMessage(), 0, "");
                return result;
            }
            if (!result.isApiKeyConfigured()) {
                result.setStatus("failed");
                result.setMessage("WANLIANG_API_KEY or MATRIX_API_KEY is not configured.");
                writeAudit(audit, "failed", result.getMessage(), 0, "");
                return result;
            }
            if (result.getEndpoint().isBlank() || result.getModel().isBlank()) {
                result.setStatus("failed");
                result.setMessage("Wanliang image endpoint/model is incomplete.");
                writeAudit(audit, "failed", result.getMessage(), 0, "");
                return result;
            }

            String prompt = request == null || request.getPrompt().isBlank()
                    ? "mechanical product rendering, clean white background, engineering graduation design illustration"
                    : request.getPrompt();
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("model", result.getModel());
            payload.put("prompt", prompt);
            payload.put("size", value("WANLIANG_IMAGE_SIZE", "1024x1024"));
            payload.put("n", 1);
            audit.put("request", sanitizedPayload(payload));

            HttpRequest httpRequest = HttpRequest.newBuilder(URI.create(result.getEndpoint()))
                    .timeout(Duration.ofSeconds(90))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + apiKey())
                    .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(payload), StandardCharsets.UTF_8))
                    .build();
            HttpResponse<byte[]> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofByteArray());
            audit.put("httpStatus", response.statusCode());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                String body = truncate(new String(response.body(), StandardCharsets.UTF_8), 1200);
                result.setStatus("failed");
                result.setMessage("Wanliang image endpoint returned HTTP " + response.statusCode() + ": " + body);
                writeAudit(audit, "failed", result.getMessage(), response.statusCode(), body);
                return result;
            }

            JsonNode root = mapper.readTree(response.body());
            ImageBytes image = extractImage(root);
            if (image.taskId() != null && !image.taskId().isBlank()) {
                result.setStatus("pending");
                result.setMessage("Wanliang image request returned async taskId=" + image.taskId() + "; polling endpoint is not configured.");
                writeAudit(audit, "pending", result.getMessage(), response.statusCode(), truncate(root.toString(), 1200));
                return result;
            }
            if (image.bytes() == null || image.bytes().length == 0) {
                result.setStatus("failed");
                result.setMessage("Wanliang image response did not contain url, b64_json, base64, or image bytes.");
                writeAudit(audit, "failed", result.getMessage(), response.statusCode(), truncate(root.toString(), 1200));
                return result;
            }

            BufferedImage bufferedImage = ImageIO.read(new ByteArrayInputStream(image.bytes()));
            if (bufferedImage == null || bufferedImage.getWidth() < 64 || bufferedImage.getHeight() < 64) {
                result.setStatus("failed");
                result.setMessage("Downloaded/generated image is not a valid image or is too small.");
                writeAudit(audit, "failed", result.getMessage(), response.statusCode(), "invalid image bytes");
                return result;
            }
            Path imagePath = outputDir().resolve("wanliang-" + System.currentTimeMillis() + image.extension());
            Files.write(imagePath, image.bytes());
            result.setStatus("success");
            result.setFilePath(imagePath.toAbsolutePath().toString());
            result.setMessage("Wanliang image generated: " + bufferedImage.getWidth() + "x" + bufferedImage.getHeight());
            audit.put("mime", image.mime());
            audit.put("width", bufferedImage.getWidth());
            audit.put("height", bufferedImage.getHeight());
            audit.put("fileSize", image.bytes().length);
            audit.put("filePath", imagePath.toAbsolutePath().toString());
            writeAudit(audit, "success", result.getMessage(), response.statusCode(), "");
            return result;
        } catch (Exception exception) {
            result.setStatus("failed");
            result.setMessage("Wanliang image request failed: " + exception.getClass().getSimpleName() + ": " + truncate(exception.getMessage(), 500));
            writeAudit(audit, "failed", result.getMessage(), 0, "");
            return result;
        } finally {
            result.setElapsedMs(System.currentTimeMillis() - start);
        }
    }

    @Override
    public ImageGenerationResult health() {
        ImageGenerationResult result = new ImageGenerationResult();
        result.setEnabled(Boolean.parseBoolean(value("WANLIANG_IMAGE_ENABLED", "false")));
        result.setApiKeyConfigured(!apiKey().isBlank());
        result.setEndpoint(value("WANLIANG_IMAGE_ENDPOINT", defaultImageEndpoint()));
        result.setModel(value("WANLIANG_IMAGE_MODEL", value("MATRIX_IMAGE_MODEL", "gpt-image-1")));
        result.setStatus(result.isEnabled() && result.isApiKeyConfigured() ? "ready" : "disabled");
        result.setMessage(result.isEnabled()
                ? "Image provider configuration loaded; API key is kept server-side."
                : "Image generation is disabled and will not block mechanical deliverables.");
        return result;
    }

    private ImageBytes extractImage(JsonNode root) throws Exception {
        String taskId = text(root, "taskId", text(root, "task_id", text(root, "id", "")));
        JsonNode node = root;
        if (root.has("data") && root.get("data").isArray() && !root.get("data").isEmpty()) {
            node = root.get("data").get(0);
        } else if (root.has("images") && root.get("images").isArray() && !root.get("images").isEmpty()) {
            node = root.get("images").get(0);
        }
        String url = text(node, "url", "");
        if (!url.isBlank()) {
            HttpRequest download = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(60)).GET().build();
            HttpResponse<byte[]> response = httpClient.send(download, HttpResponse.BodyHandlers.ofByteArray());
            String mime = response.headers().firstValue("Content-Type").orElse("application/octet-stream");
            return new ImageBytes(response.body(), mime, extension(mime), "");
        }
        String b64 = text(node, "b64_json", text(node, "base64", text(node, "image", "")));
        if (!b64.isBlank()) {
            String pure = b64.contains(",") ? b64.substring(b64.indexOf(',') + 1) : b64;
            byte[] bytes = Base64.getDecoder().decode(pure);
            return new ImageBytes(bytes, sniff(bytes), extension(sniff(bytes)), "");
        }
        return new ImageBytes(null, "", ".png", taskId);
    }

    private Map<String, Object> baseAudit(ImageGenerationResult result) {
        Map<String, Object> audit = new LinkedHashMap<>();
        audit.put("time", LocalDateTime.now().toString());
        audit.put("provider", result.getProvider());
        audit.put("enabled", result.isEnabled());
        audit.put("apiKeyConfigured", result.isApiKeyConfigured());
        audit.put("endpoint", result.getEndpoint());
        audit.put("model", result.getModel());
        return audit;
    }

    private void writeAudit(Map<String, Object> audit, String status, String message, int httpStatus, String response) {
        try {
            audit.put("status", status);
            audit.put("message", message);
            if (httpStatus > 0) audit.put("httpStatus", httpStatus);
            if (response != null && !response.isBlank()) audit.put("response", response);
            Path path = outputDir().resolve("image-generation-audit.json");
            Files.writeString(path, mapper.writerWithDefaultPrettyPrinter().writeValueAsString(audit), StandardCharsets.UTF_8);
        } catch (Exception ignored) {
            // Image generation must never block CAD, drawing, or paper deliverables.
        }
    }

    private Map<String, Object> sanitizedPayload(Map<String, Object> payload) {
        Map<String, Object> sanitized = new LinkedHashMap<>(payload);
        sanitized.put("apiKey", "<server-side>");
        return sanitized;
    }

    private Path outputDir() throws Exception {
        Path dir = Path.of(value("WANLIANG_IMAGE_OUTPUT_DIR", "data/generated-images"));
        Files.createDirectories(dir);
        return dir;
    }

    private String apiKey() {
        return value("WANLIANG_API_KEY", value("MATRIX_API_KEY", ""));
    }

    private String defaultImageEndpoint() {
        String base = value("WANLIANG_BASE_URL", value("MATRIX_BASE_URL", ""));
        if (base.isBlank()) return "";
        String normalized = base.endsWith("/") ? base.substring(0, base.length() - 1) : base;
        return normalized + "/images/generations";
    }

    private String value(String key, String fallback) {
        String value = environment.getProperty(key);
        return value == null || value.isBlank() ? fallback : value.trim();
    }

    private String text(JsonNode node, String field, String fallback) {
        JsonNode value = node == null ? null : node.get(field);
        return value == null || value.isNull() ? fallback : value.asText("");
    }

    private String truncate(String value, int max) {
        if (value == null) return "";
        return value.length() > max ? value.substring(0, max) : value;
    }

    private String sniff(byte[] bytes) {
        if (bytes.length > 8 && bytes[0] == (byte) 0x89 && bytes[1] == 0x50) return "image/png";
        if (bytes.length > 3 && bytes[0] == (byte) 0xff && bytes[1] == (byte) 0xd8) return "image/jpeg";
        return "application/octet-stream";
    }

    private String extension(String mime) {
        return mime != null && mime.toLowerCase().contains("jpeg") ? ".jpg" : ".png";
    }

    private record ImageBytes(byte[] bytes, String mime, String extension, String taskId) {}
}
