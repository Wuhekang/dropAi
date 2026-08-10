package com.dropai.rewrite.service.writing;

import com.dropai.rewrite.service.AiRewriteService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.awt.Color;
import java.awt.Graphics2D;
import java.io.ByteArrayInputStream;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

@Service
public class WritingImageLibraryService {
    private static final int MAX_IMAGES_PER_SECTION = 3;
    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final AiRewriteService aiRewriteService;
    private final HttpClient client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(6))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    public WritingImageLibraryService(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper,
                                      AiRewriteService aiRewriteService) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
        this.aiRewriteService = aiRewriteService;
    }

    public Map<String, Object> prepare(String projectId, Long userId, Path root) {
        Map<String, Object> project = WritingJdbc.one(jdbcTemplate,
                "SELECT * FROM writing_project WHERE id=?", projectId);
        validateUploads(projectId);
        String mode = WritingJdbc.text(project.get("document_mode"));
        String location = WritingJdbc.text(project.get("project_location"));
        List<String> messages = new ArrayList<>();
        int before = webImageCount(projectId);
        if (!"environment".equalsIgnoreCase(mode)) {
            return report(before, before, "SKIPPED", List.of("当前专业不自动搜索分析图片"));
        }
        if (location.isBlank()) {
            return report(before, before, "MISSING_LOCATION", List.of("请先填写项目地址，再搜索联网图片"));
        }
        List<Map<String, Object>> sections = WritingJdbc.list(jdbcTemplate, """
                SELECT s.*,c.chapter_no,c.title AS chapter_title
                FROM writing_section s JOIN writing_chapter c ON c.id=s.chapter_id
                WHERE s.project_id=? AND (s.content_type='analysis' OR c.chapter_no=2)
                ORDER BY c.sort_order,s.sort_order
                """, projectId);
        if (sections.isEmpty()) {
            return report(before, before, "NO_ANALYSIS_SECTION", List.of("提纲中没有可匹配的分析类章节"));
        }
        Filesystem.ensure(root);
        Map<String, List<String>> aiQueries = aiQueries(project, sections, location, messages);
        addMaps(projectId, userId, root, location, sections, messages);
        for (Map<String, Object> section : sections) {
            int remaining = MAX_IMAGES_PER_SECTION - sectionImageCount(projectId, section);
            if (remaining <= 0) continue;
            String sectionId = WritingJdbc.text(section.get("id"));
            List<String> queries = new ArrayList<>(aiQueries.getOrDefault(sectionId, List.of()).stream().limit(1).toList());
            queries.addAll(fallbackQueries(location, WritingJdbc.text(section.get("title"))));
            Set<String> unique = new LinkedHashSet<>(queries);
            for (String query : unique.stream().limit(3).toList()) {
                if (remaining <= 0) break;
                int saved = searchOpenverse(projectId, userId, section, root, query, remaining, messages);
                remaining -= saved;
                if (remaining > 0) remaining -= searchCommons(projectId, userId, section, root, query, remaining, messages);
            }
        }
        int after = webImageCount(projectId);
        String status = after > before ? "SUCCESS" : "EMPTY";
        if (after == before && messages.isEmpty()) messages.add("所有来源均未返回可用图片");
        return report(before, after, status, messages);
    }

    public void validateUploads(String projectId) {
        for (Map<String, Object> row : WritingJdbc.list(jdbcTemplate,
                "SELECT * FROM writing_image_material WHERE project_id=? AND UPPER(source_type) IN ('UPLOAD','USER_UPLOAD')", projectId)) {
            String path = WritingJdbc.text(row.get("file_path"));
            jdbcTemplate.update("UPDATE writing_image_material SET source_type='USER_UPLOAD',analysis_status=?,updated_at=? WHERE id=?",
                    !path.isBlank() && Files.isRegularFile(Path.of(path)) ? "READY" : "MISSING_FILE",
                    LocalDateTime.now(), row.get("id"));
        }
    }

    private Map<String, List<String>> aiQueries(Map<String, Object> project, List<Map<String, Object>> sections,
                                                String location, List<String> messages) {
        Map<String, List<String>> result = new LinkedHashMap<>();
        try {
            String sectionText = sections.stream().map(s -> WritingJdbc.text(s.get("id")) + "="
                    + WritingJdbc.text(s.get("chapter_title")) + "/" + WritingJdbc.text(s.get("title")))
                    .reduce((a, b) -> a + "\n" + b).orElse("");
            String response = CompletableFuture.supplyAsync(() -> aiRewriteService.rewrite("""
                    为环境设计论文生成联网图片搜索词。只输出JSON对象，不要Markdown。
                    格式：{"章节ID":["搜索词1","搜索词2","搜索词3"]}。
                    搜索词必须包含真实地点、图片类型和场景，优先地图、区位、场地现状、周边环境、交通、人文。
                    论文题目：%s
                    项目地址：%s
                    章节：
                    %s
                    """.formatted(WritingJdbc.text(project.get("title")), location, sectionText), "IMAGE_SEARCH_QUERY"))
                    .orTimeout(20, TimeUnit.SECONDS).join();
            int start = response.indexOf('{');
            int end = response.lastIndexOf('}');
            JsonNode root = objectMapper.readTree(response.substring(start, end + 1));
            for (Map<String, Object> section : sections) {
                String id = WritingJdbc.text(section.get("id"));
                List<String> values = new ArrayList<>();
                root.path(id).forEach(node -> { if (!node.asText("").isBlank()) values.add(node.asText()); });
                result.put(id, values.stream().limit(5).toList());
            }
        } catch (Exception exception) {
            messages.add("AI检索词生成失败，已使用章节语义检索：" + safeMessage(exception));
        }
        return result;
    }

    private void addMaps(String projectId, Long userId, Path root, String location,
                         List<Map<String, Object>> sections, List<String> messages) {
        Map<String, Object> overview = sections.stream()
                .filter(s -> WritingJdbc.text(s.get("title")).contains("概况"))
                .findFirst().orElse(sections.get(0));
        try {
            double[] point = geocode(location);
            saveMap(projectId, userId, overview, root, point, 13, "地理位置图", location + "地理位置");
            saveMap(projectId, userId, overview, root, point, 16, "区位定位图", location + "区位与道路关系");
        } catch (Exception exception) {
            messages.add("地图检索失败：" + safeMessage(exception));
        }
    }

    private int searchOpenverse(String projectId, Long userId, Map<String, Object> section, Path root,
                                String query, int limit, List<String> messages) {
        int saved = 0;
        try {
            String api = "https://api.openverse.org/v1/images/?page_size=5&q=" + encode(query);
            JsonNode results = objectMapper.readTree(get(api, "application/json")).path("results");
            for (JsonNode item : results) {
                if (saved >= limit) break;
                String url = item.path("thumbnail").asText("");
                if (!url.startsWith("http")) url = item.path("url").asText("");
                String title = cleanName(item.path("title").asText(query));
                if (url.startsWith("http") && saveRemote(projectId, userId, section, root, url,
                        title, "Openverse检索：" + query, "OPENVERSE")) saved++;
            }
        } catch (Exception exception) {
            messages.add("Openverse检索失败（" + query + "）：" + safeMessage(exception));
        }
        return saved;
    }

    private int searchCommons(String projectId, Long userId, Map<String, Object> section, Path root,
                              String query, int limit, List<String> messages) {
        int saved = 0;
        try {
            String api = "https://commons.wikimedia.org/w/api.php?action=query&generator=search&gsrnamespace=6&gsrlimit=5&prop=imageinfo&iiprop=url&iiurlwidth=1000&format=json&gsrsearch=" + encode(query);
            JsonNode pages = objectMapper.readTree(get(api, "application/json")).path("query").path("pages");
            if (!pages.isObject()) return 0;
            for (JsonNode page : pages) {
                if (saved >= limit) break;
                String url = page.path("imageinfo").path(0).path("thumburl").asText("");
                if (!url.startsWith("http")) url = page.path("imageinfo").path(0).path("url").asText("");
                String title = cleanName(page.path("title").asText(query).replaceFirst("(?i)^File:", ""));
                if (url.startsWith("https://upload.wikimedia.org/") && saveRemote(projectId, userId, section, root,
                        url, title, "Wikimedia Commons检索：" + query, "WIKIMEDIA")) saved++;
            }
        } catch (Exception exception) {
            messages.add("Wikimedia检索失败（" + query + "）：" + safeMessage(exception));
        }
        return saved;
    }

    private double[] geocode(String location) throws Exception {
        JsonNode array = objectMapper.readTree(get("https://nominatim.openstreetmap.org/search?format=json&limit=1&q=" + encode(location), "application/json"));
        if (!array.isArray() || array.isEmpty()) throw new IllegalStateException("地址无法定位");
        return new double[]{array.get(0).path("lat").asDouble(), array.get(0).path("lon").asDouble()};
    }

    private void saveMap(String projectId, Long userId, Map<String, Object> section, Path root,
                         double[] point, int zoom, String name, String usage) throws Exception {
        if (exists(projectId, section, name) || sectionImageCount(projectId, section) >= MAX_IMAGES_PER_SECTION) return;
        int n = 1 << zoom;
        int centerX = (int) Math.floor((point[1] + 180.0) / 360.0 * n);
        double latRad = Math.toRadians(point[0]);
        int centerY = (int) Math.floor((1.0 - Math.log(Math.tan(latRad) + 1.0 / Math.cos(latRad)) / Math.PI) / 2.0 * n);
        BufferedImage map = new BufferedImage(768, 512, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = map.createGraphics();
        try {
            for (int dx = -1; dx <= 1; dx++) {
                for (int dy = -1; dy <= 0; dy++) {
                    String tileUrl = "https://tile.openstreetmap.org/" + zoom + "/" + (centerX + dx) + "/" + (centerY + dy) + ".png";
                    BufferedImage tile = ImageIO.read(new ByteArrayInputStream(get(tileUrl, "image/")));
                    if (tile == null) throw new IllegalStateException("地图瓦片无法解析");
                    graphics.drawImage(tile, (dx + 1) * 256, (dy + 1) * 256, null);
                }
            }
            graphics.setColor(new Color(215, 38, 56));
            graphics.fillOval(372, 244, 24, 24);
            graphics.setColor(Color.WHITE);
            graphics.fillOval(380, 252, 8, 8);
        } finally {
            graphics.dispose();
        }
        saveBuffered(projectId, userId, section, root, map,
                "https://www.openstreetmap.org/?mlat=" + point[0] + "&mlon=" + point[1] + "#map=" + zoom + "/" + point[0] + "/" + point[1],
                name, usage, "OPENSTREETMAP");
    }

    private void saveBuffered(String projectId, Long userId, Map<String, Object> section, Path root,
                              BufferedImage image, String url, String name, String usage, String provider) throws Exception {
        Path dir = root.resolve("image-library").normalize();
        Files.createDirectories(dir);
        String id = WritingJdbc.id("wim");
        Path path = dir.resolve(id + ".png").normalize();
        if (!ImageIO.write(image, "png", path.toFile())) throw new IllegalStateException("图片保存失败");
        insertImage(projectId, userId, section, path, url, name, usage, provider, id);
    }

    private boolean saveRemote(String projectId, Long userId, Map<String, Object> section, Path root,
                               String url, String name, String usage, String provider) throws Exception {
        if (exists(projectId, section, name) || existsUrl(projectId, url)
                || sectionImageCount(projectId, section) >= MAX_IMAGES_PER_SECTION) return false;
        byte[] bytes = get(url, "image/");
        if (bytes.length < 2048 || bytes.length > 12 * 1024 * 1024) return false;
        BufferedImage image = ImageIO.read(new ByteArrayInputStream(bytes));
        if (image == null || image.getWidth() < 320 || image.getHeight() < 200) return false;
        Path dir = root.resolve("image-library").normalize();
        Files.createDirectories(dir);
        String id = WritingJdbc.id("wim");
        Path path = dir.resolve(id + ".png").normalize();
        if (!ImageIO.write(image, "png", path.toFile())) return false;
        insertImage(projectId, userId, section, path, url, name, usage, provider, id);
        return true;
    }

    private void insertImage(String projectId, Long userId, Map<String, Object> section, Path path,
                             String url, String name, String usage, String provider, String id) throws Exception {
        LocalDateTime now = LocalDateTime.now();
        jdbcTemplate.update("""
                INSERT INTO writing_image_material (id,project_id,user_id,file_path,url,original_name,display_name,mime_type,file_size,
                ai_category,ai_usage,ai_suggested_chapter,ai_suggested_section,user_confirmed_chapter,user_confirmed_section,
                source_type,analysis_status,is_confirmed,display_order,vision_description,vision_confidence,created_at,updated_at)
                VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
                """, id, projectId, userId, path.toString(), url, name + ".png", name, "image/png", Files.size(path),
                "分析类图片", usage + "；来源：" + provider, section.get("chapter_id"), section.get("id"), null, null,
                "WEB_SEARCH", "SEARCHED_PENDING_CONFIRMATION", 0, sectionImageCount(projectId, section),
                usage + "；检索来源：" + provider, 0.75D, now, now);
    }

    private List<String> fallbackQueries(String location, String sectionTitle) {
        String title = sectionTitle.isBlank() ? "场地分析" : sectionTitle;
        List<String> result = new ArrayList<>();
        result.add(location + " " + title);
        String place = location.contains("新竹") ? "Hsinchu Taiwan" : location;
        if (title.contains("概况") || title.contains("区位")) {
            result.add(place + " aerial view");
            result.add(place + " city map");
        } else {
            result.add(place + " street view");
            result.add(place + " public space");
            result.add(place + " community environment");
        }
        return result;
    }

    private int sectionImageCount(String projectId, Map<String, Object> section) {
        return WritingJdbc.integer(WritingJdbc.one(jdbcTemplate,
                "SELECT COUNT(*) AS n FROM writing_image_material WHERE project_id=? AND ai_suggested_section=? AND UPPER(source_type)='WEB_SEARCH'",
                projectId, section.get("id")).get("n"), 0);
    }

    private int webImageCount(String projectId) {
        return WritingJdbc.integer(WritingJdbc.one(jdbcTemplate,
                "SELECT COUNT(*) AS n FROM writing_image_material WHERE project_id=? AND UPPER(source_type)='WEB_SEARCH'", projectId).get("n"), 0);
    }

    private boolean exists(String projectId, Map<String, Object> section, String name) {
        return !WritingJdbc.list(jdbcTemplate,
                "SELECT id FROM writing_image_material WHERE project_id=? AND ai_suggested_section=? AND display_name=?",
                projectId, section.get("id"), name).isEmpty();
    }

    private boolean existsUrl(String projectId, String url) {
        return !WritingJdbc.list(jdbcTemplate,
                "SELECT id FROM writing_image_material WHERE project_id=? AND url=?", projectId, url).isEmpty();
    }

    private byte[] get(String url, String expectedType) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(8))
                .header("User-Agent", "DropAI/2.0 academic-image-search")
                .header("Accept", expectedType.equals("image/") ? "image/avif,image/webp,image/png,image/jpeg,*/*" : "application/json")
                .GET().build();
        HttpResponse<byte[]> response = client.send(request, HttpResponse.BodyHandlers.ofByteArray());
        String type = response.headers().firstValue("content-type").orElse("").toLowerCase(Locale.ROOT);
        if (response.statusCode() / 100 != 2 || !type.contains(expectedType.toLowerCase(Locale.ROOT))) {
            throw new IllegalStateException("HTTP " + response.statusCode() + "，内容类型 " + type);
        }
        return response.body();
    }

    private Map<String, Object> report(int before, int after, String status, List<String> messages) {
        return Map.of("status", status, "added", Math.max(0, after - before), "total", after,
                "messages", messages.stream().distinct().limit(12).toList());
    }

    private String cleanName(String value) {
        String cleaned = value == null ? "联网分析图片" : value.replaceAll("[\\r\\n\\t]+", " ").trim();
        return cleaned.isBlank() ? "联网分析图片" : cleaned.substring(0, Math.min(100, cleaned.length()));
    }

    private String safeMessage(Exception exception) {
        String message = exception.getMessage();
        if (message == null || message.isBlank()) return exception.getClass().getSimpleName();
        return message.substring(0, Math.min(180, message.length()));
    }

    private String encode(String value) { return URLEncoder.encode(value, StandardCharsets.UTF_8); }

    private static final class Filesystem {
        private static void ensure(Path root) {
            try { Files.createDirectories(root); }
            catch (Exception exception) { throw new IllegalStateException("无法创建联网图片目录", exception); }
        }
    }
}
