package com.dropai.rewrite.service.ppt.enhancement;

import com.dropai.rewrite.RewriteApplication;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.poi.xslf.usermodel.XMLSlideShow;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.web.embedded.tomcat.TomcatServletWebServerFactory;
import org.springframework.boot.web.server.WebServerFactoryCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@EnabledIfSystemProperty(named = "ppt.enhancement.real.e2e", matches = "true")
@SpringBootTest(
    classes = {RewriteApplication.class, PptEnhancementRealLocalhostE2ETest.Nio2TestConfiguration.class},
    webEnvironment = SpringBootTest.WebEnvironment.DEFINED_PORT,
    properties = {
        "server.port=8080",
        "spring.main.lazy-initialization=true",
        "app.epay.mock-enabled=true",
        "app.epay.pid=localhost-e2e",
        "app.epay.key=localhost-e2e-not-a-secret",
        "app.epay.gateway=https://localhost.invalid/unused-localhost-payment"
    }
)
class PptEnhancementRealLocalhostE2ETest {
    private static final String BASE = "http://127.0.0.1:8080";
    private static final Path PPT_STORAGE = Path.of("storage", "ppt").toAbsolutePath().normalize();

    @Autowired ObjectMapper mapper;

    @DynamicPropertySource
    static void enhancementStorage(DynamicPropertyRegistry registry) {
        registry.add("ppt-enhancement.data-dir", PPT_STORAGE::toString);
    }

    @Test
    void realLocalhostUploadBaseGenerateAndPaidDoubaoEnhancement() throws Exception {
        Path source = requiredSource();
        Path evidence = Path.of(System.getProperty("ppt.enhancement.evidence.dir",
            "../.tmp-localhost-enhancement")).toAbsolutePath().normalize();
        Files.createDirectories(evidence);

        String phone = "1" + String.valueOf(System.currentTimeMillis()).substring(3, 13);
        String password = "PptE2e!" + UUID.randomUUID().toString().substring(0, 8);
        JsonNode registration = json("POST", "/api/auth/register", "",
            Map.of("phone", phone, "password", password));
        String token = requiredText(registration.path("data"), "token");

        JsonNode order = json("POST", "/api/recharge/orders", token,
            Map.of("amount", 100, "payMethod", "alipay"));
        String orderNo = requiredText(order.path("data"), "orderNo");
        json("POST", "/api/recharge/orders/" + orderNo + "/mock-pay", token, Map.of());

        JsonNode created = json("POST", "/api/ppt/projects", token, Map.of(
            "topic", "", "presenter", "", "major", "", "advisor", "", "studentNumber", "",
            "targetSlideCount", 20));
        String projectId = requiredText(created.path("data"), "id");
        upload(projectId, token, source);
        json("POST", "/api/ppt/projects/" + projectId + "/analyze", token, Map.of());
        json("POST", "/api/ppt/projects/" + projectId + "/outline", token, Map.of());
        json("PUT", "/api/ppt/projects/" + projectId + "/template", token,
            Map.of("templatePackId", "small-bear-watercolor-blue-v1"));
        json("POST", "/api/ppt/projects/" + projectId + "/plan", token, Map.of());
        JsonNode generated = json("POST", "/api/ppt/projects/" + projectId + "/generate", token, Map.of());
        JsonNode base = generated.path("data");
        String baseTaskId = requiredText(base, "generationTaskId");
        int baseCharged = base.path("chargedPoints").asInt(-1);
        int baseSlides = base.path("slideCount").asInt(-1);
        assertTrue(baseCharged > 0);
        assertTrue(baseSlides > 0);
        Path basePptx = evidence.resolve("localhost-base.pptx");
        download("/api/ppt/projects/" + projectId + "/download", token, basePptx);
        String baseHash = sha256(basePptx);
        assertEquals(requiredText(base, "outputSha256"), baseHash);

        JsonNode quote = json("GET", "/api/ppt/projects/" + projectId + "/enhancements/quote", token, null).path("data");
        assertEquals(baseTaskId, requiredText(quote, "baseGenerationTaskId"));
        assertEquals((baseCharged + 1) / 2, quote.path("costPoints").asInt());
        assertTrue(quote.path("providerConfigured").asBoolean(), "真实localhost必须配置豆包增幅模型");
        int pointsBeforeEnhancement = json("GET", "/api/points/me", token, null).path("data").path("points").asInt();

        JsonNode started = json("POST", "/api/ppt/projects/" + projectId + "/enhancements", token, Map.of(
            "baseGenerationTaskId", baseTaskId,
            "idempotencyKey", "localhost-e2e-" + UUID.randomUUID(),
            "profile", "balanced")).path("data");
        String enhancementTaskId = requiredText(started, "id");
        JsonNode completed = waitForEnhancement(projectId, enhancementTaskId, token, Duration.ofMinutes(20));
        assertEquals("SUCCESS", completed.path("status").asText(), completed.path("errorMessage").asText());
        assertTrue(completed.path("providerInvoked").asBoolean());
        assertEquals("SUCCESS", completed.path("providerStatus").asText());
        assertEquals("doubao_ark", completed.path("provider").asText());
        assertEquals("ppt-enhancement", completed.path("skillName").asText());
        assertEquals("1.2.0", completed.path("skillVersion").asText());
        assertEquals((baseCharged + 1) / 2, completed.path("costPoints").asInt());
        assertTrue(completed.path("pointsCharged").asBoolean());
        assertEquals(baseSlides, completed.path("slideCount").asInt());

        Path enhancedPptx = evidence.resolve("localhost-enhanced.pptx");
        download("/api/ppt/projects/" + projectId + "/enhancements/" + enhancementTaskId + "/download",
            token, enhancedPptx);
        assertEquals(requiredText(completed, "outputSha256"), sha256(enhancedPptx));
        assertNotEquals(baseHash, sha256(enhancedPptx));
        assertEquals(baseHash, sha256(basePptx), "增幅流程不得修改已下载的基础版");
        assertEquals(baseSlides, slideCount(basePptx));
        assertEquals(baseSlides, slideCount(enhancedPptx));

        int pointsAfterEnhancement = json("GET", "/api/points/me", token, null).path("data").path("points").asInt();
        assertEquals(pointsBeforeEnhancement - (baseCharged + 1) / 2, pointsAfterEnhancement);
        JsonNode project = json("GET", "/api/ppt/projects/" + projectId, token, null).path("data");
        assertEquals(baseHash, sha256(Path.of(requiredText(project, "output_path"))));

        Path taskRoot = PPT_STORAGE.resolve(phone.equals("never") ? "" : String.valueOf(registration.path("data").path("userId").asLong()))
            .resolve(projectId).resolve("enhancements").resolve(enhancementTaskId);
        assertTrue(Files.isRegularFile(taskRoot.resolve("enhancement-plan.json")));
        assertTrue(Files.isRegularFile(taskRoot.resolve("enhancement-log.json")));
        JsonNode log = mapper.readTree(taskRoot.resolve("enhancement-log.json").toFile());
        assertEquals("PASSED", log.path("validation").path("status").asText());
        assertEquals(baseSlides, log.path("validation").path("pageRenders").size());
        String checks = log.path("validation").path("checks").toString();
        assertTrue(checks.contains("MEDIA_SLIDES_BACKGROUND_ONLY_OR_SAFE_NOOP"));
        assertTrue(checks.contains("MEDIA_SLIDES_INHERITED_OBJECTS_EXACT_MATCH"));
        assertTrue(checks.contains("MEDIA_SLIDES_NO_FOREGROUND_ADDITIONS"));
        int mediaSlides = 0;
        for (JsonNode slide : log.path("slides")) {
            if (!slide.path("protectedMediaSlide").asBoolean()) continue;
            mediaSlides++;
            int page = slide.path("page").asInt(-1);
            assertTrue(slide.path("backgroundOnly").asBoolean(), "第" + page + "页必须为backgroundOnly");
            assertTrue(slide.path("foregroundObjectsUnchanged").asBoolean(), "第" + page + "页前景对象发生变化");
            assertTrue(slide.path("imageGeometryUnchanged").asBoolean(), "第" + page + "页图片几何发生变化");
            assertTrue(slide.path("cropUnchanged").asBoolean(), "第" + page + "页图片裁剪发生变化");
            assertTrue(slide.path("newObjectsBehindInherited").asBoolean(), "第" + page + "页新增对象未位于继承对象后方");
        }
        assertEquals(25, mediaSlides, "健康管理固定论文应仅将25个正文图片页识别为受保护媒体页");
        int mediaRenders = 0;
        boolean changedNonNoopMedia = false;
        for (JsonNode render : log.path("validation").path("pageRenders")) {
            if (!render.path("protectedMediaSlide").asBoolean()) continue;
            mediaRenders++;
            if (!render.path("safeNoop").asBoolean() && render.path("visuallyChanged").asBoolean()) {
                changedNonNoopMedia = true;
            }
        }
        assertEquals(25, mediaRenders, "质量门禁中的媒体页数量必须与计划一致");
        assertTrue(changedNonNoopMedia, "至少一个非safe-noop媒体页必须产生可见背景变化");
        assertFalse(log.toString().contains(password));
        assertFalse(log.toString().contains(token));

        mapper.writerWithDefaultPrettyPrinter().writeValue(
            evidence.resolve("localhost-enhancement-evidence.json").toFile(),
            Map.ofEntries(
                Map.entry("projectId", projectId),
                Map.entry("baseGenerationTaskId", baseTaskId),
                Map.entry("enhancementTaskId", enhancementTaskId),
                Map.entry("baseSlides", baseSlides),
                Map.entry("baseChargedPoints", baseCharged),
                Map.entry("enhancementChargedPoints", (baseCharged + 1) / 2),
                Map.entry("baseSha256", baseHash),
                Map.entry("enhancedSha256", sha256(enhancedPptx)),
                Map.entry("providerInvoked", true),
                Map.entry("providerStatus", completed.path("providerStatus").asText()),
                Map.entry("skillName", completed.path("skillName").asText()),
                Map.entry("skillVersion", completed.path("skillVersion").asText()),
                Map.entry("skillHash", completed.path("skillHash").asText()),
                Map.entry("qualityGate", "PASSED")));
    }

    private Path requiredSource() {
        String configured = System.getProperty("ppt.enhancement.source", "").trim();
        if (configured.isBlank()) throw new IllegalStateException("必须通过 -Dppt.enhancement.source 指定真实论文DOCX");
        Path source = Path.of(configured).toAbsolutePath().normalize();
        if (!Files.isRegularFile(source)) throw new IllegalStateException("真实论文不存在：" + source);
        return source;
    }

    private JsonNode waitForEnhancement(String projectId, String taskId, String token, Duration timeout) throws Exception {
        Instant deadline = Instant.now().plus(timeout);
        JsonNode latest = null;
        while (Instant.now().isBefore(deadline)) {
            latest = json("GET", "/api/ppt/projects/" + projectId + "/enhancements/" + taskId, token, null).path("data");
            String status = latest.path("status").asText();
            if ("SUCCESS".equals(status) || "FAILED".equals(status)) return latest;
            Thread.sleep(2_000L);
        }
        throw new IllegalStateException("localhost增幅任务超时，最后状态=" + (latest == null ? "" : latest.path("status").asText()));
    }

    private JsonNode json(String method, String path, String token, Object body) throws Exception {
        HttpURLConnection connection = open(method, path, token);
        if (body != null) {
            connection.setDoOutput(true);
            connection.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
            connection.getOutputStream().write(mapper.writeValueAsBytes(body));
        }
        int status = connection.getResponseCode();
        byte[] bytes = readResponse(connection, status);
        JsonNode result = mapper.readTree(bytes);
        if (status < 200 || status >= 300 || !"200".equals(result.path("code").asText())) {
            throw new IllegalStateException(method + " " + path + "失败：HTTP " + status + " " + result.path("message").asText());
        }
        return result;
    }

    private void upload(String projectId, String token, Path source) throws Exception {
        String boundary = "----DokiAiPptE2e" + UUID.randomUUID().toString().replace("-", "");
        HttpURLConnection connection = open("POST", "/api/ppt/projects/" + projectId + "/upload", token);
        connection.setDoOutput(true);
        connection.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + boundary);
        try (var output = connection.getOutputStream()) {
            output.write(("--" + boundary + "\r\nContent-Disposition: form-data; name=\"file\"; filename=\""
                + source.getFileName() + "\"\r\nContent-Type: application/vnd.openxmlformats-officedocument.wordprocessingml.document\r\n\r\n")
                .getBytes(StandardCharsets.UTF_8));
            Files.copy(source, output);
            output.write(("\r\n--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));
        }
        int status = connection.getResponseCode();
        JsonNode result = mapper.readTree(readResponse(connection, status));
        if (status < 200 || status >= 300 || !"200".equals(result.path("code").asText())) {
            throw new IllegalStateException("真实论文上传失败：" + result.path("message").asText());
        }
    }

    private void download(String path, String token, Path target) throws Exception {
        HttpURLConnection connection = open("GET", path, token);
        int status = connection.getResponseCode();
        if (status != 200) throw new IllegalStateException("下载失败：HTTP " + status);
        try (InputStream input = connection.getInputStream()) {
            Files.copy(input, target, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        }
        assertTrue(Files.size(target) > 0);
    }

    private HttpURLConnection open(String method, String path, String token) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) URI.create(BASE + path).toURL().openConnection();
        connection.setRequestMethod(method);
        connection.setConnectTimeout(30_000);
        connection.setReadTimeout(1_200_000);
        if (token != null && !token.isBlank()) connection.setRequestProperty("Authorization", "Bearer " + token);
        return connection;
    }

    private byte[] readResponse(HttpURLConnection connection, int status) throws Exception {
        InputStream input = status >= 400 ? connection.getErrorStream() : connection.getInputStream();
        if (input == null) return new byte[0];
        try (input; ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            input.transferTo(output);
            return output.toByteArray();
        }
    }

    private String requiredText(JsonNode node, String field) {
        String value = node.path(field).asText("").trim();
        if (value.isBlank()) throw new IllegalStateException("localhost响应缺少字段：" + field);
        return value;
    }

    private String sha256(Path path) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (InputStream input = Files.newInputStream(path)) {
            byte[] buffer = new byte[8192];
            for (int read; (read = input.read(buffer)) >= 0; ) if (read > 0) digest.update(buffer, 0, read);
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    private int slideCount(Path path) throws Exception {
        try (InputStream input = Files.newInputStream(path); XMLSlideShow show = new XMLSlideShow(input)) {
            return show.getSlides().size();
        }
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class Nio2TestConfiguration {
        @Bean
        WebServerFactoryCustomizer<TomcatServletWebServerFactory> localhostNio2Protocol() {
            return factory -> factory.setProtocol("org.apache.coyote.http11.Http11Nio2Protocol");
        }
    }
}
