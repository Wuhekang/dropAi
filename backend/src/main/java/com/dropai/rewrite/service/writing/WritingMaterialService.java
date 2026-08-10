package com.dropai.rewrite.service.writing;

import com.dropai.rewrite.auth.AuthContext;
import com.dropai.rewrite.service.ai.DoubaoMechanicalVisionService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.hwpf.HWPFDocument;
import org.apache.poi.hwpf.extractor.WordExtractor;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import javax.imageio.ImageIO;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class WritingMaterialService {
    private static final Pattern CHAPTER_LINE = Pattern.compile("^(?:第[一二三四五六七八九十百零〇两\\d]+章|\\d+[、.．])\\s*(.+)$");
    private static final Pattern SECTION_LINE = Pattern.compile("^(\\d+)[.．](\\d+)\\s*(.+)$");
    private static final Set<String> MODES = Set.of("general", "environment", "visual_communication", "interior_design");
    private final org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final OutlineNormalizeService outlineNormalizeService;
    private final WritingImageLibraryService imageLibraryService;
    private final DoubaoMechanicalVisionService visionService;
    private final Path storageRoot = Path.of("storage", "writing-materials").toAbsolutePath().normalize();

    public WritingMaterialService(org.springframework.jdbc.core.JdbcTemplate jdbcTemplate, ObjectMapper objectMapper,
                                  OutlineNormalizeService outlineNormalizeService, WritingImageLibraryService imageLibraryService,
                                  DoubaoMechanicalVisionService visionService) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
        this.outlineNormalizeService = outlineNormalizeService;
        this.imageLibraryService = imageLibraryService;
        this.visionService = visionService;
    }

    public List<Map<String, Object>> materials(String projectId) {
        ownedProject(projectId);
        return WritingJdbc.list(jdbcTemplate,
                "SELECT * FROM writing_image_material WHERE project_id=? ORDER BY created_at,id", projectId);
    }

    @Transactional
    public List<Map<String, Object>> upload(String projectId, List<MultipartFile> files) {
        Long userId = AuthContext.requireUserId();
        ownedProject(projectId);
        if (files == null || files.isEmpty()) throw new IllegalArgumentException("请选择需要上传的图片");
        Path projectDir = storageRoot.resolve(projectId).normalize();
        ensureInsideStorage(projectDir);
        try {
            Files.createDirectories(projectDir);
            for (MultipartFile file : files) {
                validateImage(file);
                String id = WritingJdbc.id("wim");
                String original = safeFileName(file.getOriginalFilename());
                String suffix = suffix(original);
                boolean webp = ".webp".equals(suffix);
                Path target = projectDir.resolve(id + (webp ? ".png" : suffix)).normalize();
                ensureInsideStorage(target);
                if (webp) {
                    var image = ImageIO.read(file.getInputStream());
                    if (image == null || !ImageIO.write(image, "png", target.toFile())) throw new IOException("WEBP图片转换失败");
                } else {
                    Files.copy(file.getInputStream(), target, StandardCopyOption.REPLACE_EXISTING);
                }
                LocalDateTime now = LocalDateTime.now();
                jdbcTemplate.update("""
                        INSERT INTO writing_image_material (id,project_id,user_id,file_path,url,original_name,display_name,
                        mime_type,file_size,source_type,analysis_status,created_at,updated_at)
                        VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?)
                        """, id, projectId, userId, target.toString(), "/api/writing/projects/" + projectId + "/materials/" + id + "/content",
                        original, withoutSuffix(original), webp ? "image/png" : file.getContentType(), file.getSize(), "USER_UPLOAD", "PENDING", now, now);
            }
        } catch (IOException exception) {
            throw new IllegalStateException("图片素材保存失败", exception);
        }
        return materials(projectId);
    }

    @Transactional
    public List<Map<String, Object>> updateMaterial(String projectId, String materialId, Map<String, Object> request) {
        ownedMaterial(projectId, materialId);
        String sectionId = text(request.get("userConfirmedSection"));
        String chapterId = "";
        if (!sectionId.isBlank()) {
            Map<String, Object> section = WritingJdbc.one(jdbcTemplate, "SELECT id,chapter_id FROM writing_section WHERE project_id=? AND id=?", projectId, sectionId);
            chapterId = WritingJdbc.text(section.get("chapter_id"));
            int bound = WritingJdbc.integer(WritingJdbc.one(jdbcTemplate,
                    "SELECT COUNT(*) AS n FROM writing_image_material WHERE project_id=? AND user_confirmed_section=? AND id<>?",
                    projectId, sectionId, materialId).get("n"), 0);
            if (bound >= 3) throw new IllegalArgumentException("每个二级标题最多绑定3张图片");
        }
        String displayName = textOr(request.get("displayName"), "未命名图片").trim();
        if (!sectionId.isBlank() && isPlaceholderImageName(displayName)) {
            throw new IllegalArgumentException("请确认图片内容并填写有意义的图片名称后再绑定章节");
        }
        jdbcTemplate.update("""
                UPDATE writing_image_material SET display_name=?,user_confirmed_chapter=?,user_confirmed_section=?,is_confirmed=?,display_order=?,updated_at=?
                WHERE project_id=? AND id=?
                """, displayName, chapterId,
                sectionId, !sectionId.isBlank(), Math.max(0, WritingJdbc.integer(request.get("displayOrder"), 0)),
                LocalDateTime.now(), projectId, materialId);
        if (!sectionId.isBlank()) {
            Map<String, Object> task = WritingJdbc.one(jdbcTemplate, """
                    SELECT id FROM writing_image_task WHERE project_id=? AND section_id=? AND status<>'CONFIRMED'
                    ORDER BY CASE WHEN material_id=? THEN 0 ELSE 1 END,sort_order LIMIT 1
                    """, projectId, sectionId, materialId);
            if (!task.isEmpty()) jdbcTemplate.update("UPDATE writing_image_task SET material_id=?,status='CONFIRMED',message=?,updated_at=? WHERE id=?",
                    materialId, "图片名称与章节已经用户确认", LocalDateTime.now(), task.get("id"));
        }
        return materials(projectId);
    }

    private boolean isPlaceholderImageName(String value) {
        if (value == null || value.isBlank() || "未命名图片".equals(value)) return true;
        return value.trim().matches("(?i)^(page|image|img|图片|截图)[-_ ]?\\d+$");
    }

    private void createImageTasks(String projectId, String mode) {
        jdbcTemplate.update("DELETE FROM writing_image_task WHERE project_id=?", projectId);
        for (Map<String, Object> section : sections(projectId)) {
            List<String> requirements = stringList(section.get("image_requirements_json"));
            int order = 0;
            for (String requirement : requirements.stream().limit(3).toList()) {
                String source = "environment".equalsIgnoreCase(mode)
                        && ("analysis".equals(WritingJdbc.text(section.get("content_type")))
                        || WritingJdbc.integer(section.get("chapter_no"), 0) == 2) ? "WEB_SEARCH" : "USER_UPLOAD";
                LocalDateTime now = LocalDateTime.now();
                jdbcTemplate.update("""
                        INSERT INTO writing_image_task (id,project_id,chapter_id,section_id,requirement_name,source_type,status,sort_order,created_at,updated_at)
                        VALUES (?,?,?,?,?,?,?,?,?,?)
                        """, WritingJdbc.id("wit"), projectId, section.get("chapter_id"), section.get("id"), requirement,
                        source, "PENDING", ++order, now, now);
            }
        }
    }

    private void syncSectionImageTasks(String projectId, String sectionId, List<String> requirements) {
        Map<String, Object> project = ownedProject(projectId);
        Map<String, Object> section = WritingJdbc.one(jdbcTemplate, """
                SELECT s.*,c.chapter_no FROM writing_section s JOIN writing_chapter c ON c.id=s.chapter_id
                WHERE s.project_id=? AND s.id=?
                """, projectId, sectionId);
        jdbcTemplate.update("DELETE FROM writing_image_task WHERE project_id=? AND section_id=? AND status<>'CONFIRMED'", projectId, sectionId);
        int order = 0;
        for (String requirement : requirements.stream().limit(3).toList()) {
            if (!WritingJdbc.list(jdbcTemplate, "SELECT id FROM writing_image_task WHERE project_id=? AND section_id=? AND requirement_name=?", projectId, sectionId, requirement).isEmpty()) continue;
            String mode = WritingJdbc.text(project.get("document_mode"));
            String source = "environment".equalsIgnoreCase(mode)
                    && ("analysis".equals(WritingJdbc.text(section.get("content_type"))) || WritingJdbc.integer(section.get("chapter_no"), 0) == 2)
                    ? "WEB_SEARCH" : "USER_UPLOAD";
            LocalDateTime now = LocalDateTime.now();
            jdbcTemplate.update("""
                    INSERT INTO writing_image_task (id,project_id,chapter_id,section_id,requirement_name,source_type,status,sort_order,created_at,updated_at)
                    VALUES (?,?,?,?,?,?,?,?,?,?)
                    """, WritingJdbc.id("wit"), projectId, section.get("chapter_id"), sectionId, requirement, source, "PENDING", ++order, now, now);
        }
    }

    private void synchronizeImageTasks(String projectId, Map<String, Object> report) {
        for (Map<String, Object> task : WritingJdbc.list(jdbcTemplate,
                "SELECT * FROM writing_image_task WHERE project_id=? AND source_type='WEB_SEARCH' AND status<>'CONFIRMED' ORDER BY section_id,sort_order", projectId)) {
            List<Map<String, Object>> candidates = WritingJdbc.list(jdbcTemplate, """
                    SELECT id FROM writing_image_material WHERE project_id=? AND ai_suggested_section=? AND UPPER(source_type)='WEB_SEARCH'
                    AND id NOT IN (SELECT material_id FROM writing_image_task WHERE project_id=? AND material_id IS NOT NULL)
                    ORDER BY created_at,id LIMIT 1
                    """, projectId, task.get("section_id"), projectId);
            if (!candidates.isEmpty()) {
                jdbcTemplate.update("UPDATE writing_image_task SET material_id=?,status='FOUND',message='已找到候选图片，等待用户确认',updated_at=? WHERE id=?",
                        candidates.get(0).get("id"), LocalDateTime.now(), task.get("id"));
            } else {
                jdbcTemplate.update("UPDATE writing_image_task SET status=?,message=?,updated_at=? WHERE id=?",
                        "EMPTY".equals(WritingJdbc.text(report.get("status"))) ? "EMPTY" : "FAILED",
                        "未找到可用候选图片，可重新搜索或上传图片", LocalDateTime.now(), task.get("id"));
            }
        }
    }

    @Transactional
    public List<Map<String, Object>> deleteMaterial(String projectId, String materialId) {
        Map<String, Object> row = ownedMaterial(projectId, materialId);
        String rawPath = WritingJdbc.text(row.get("file_path"));
        if (!rawPath.isBlank()) {
            try {
                Path path = Path.of(rawPath).toAbsolutePath().normalize();
                ensureInsideStorage(path);
                Files.deleteIfExists(path);
            } catch (IOException exception) {
                throw new IllegalStateException("图片文件删除失败", exception);
            }
        }
        jdbcTemplate.update("DELETE FROM writing_image_material WHERE project_id=? AND id=?", projectId, materialId);
        return materials(projectId);
    }

    public MaterialContent content(String projectId, String materialId) {
        Map<String, Object> row = ownedMaterial(projectId, materialId);
        try {
            Path path = Path.of(WritingJdbc.text(row.get("file_path"))).toAbsolutePath().normalize();
            ensureInsideStorage(path);
            return new MaterialContent(Files.readAllBytes(path), WritingJdbc.text(row.get("mime_type")), WritingJdbc.text(row.get("original_name")));
        } catch (IOException exception) {
            throw new IllegalStateException("图片素材不存在", exception);
        }
    }

    @Transactional
    public Map<String, Object> generateOutline(String projectId, Map<String, Object> request) {
        Map<String, Object> project = ownedProject(projectId);
        String mode = textOr(request.get("documentMode"), "general").toLowerCase(Locale.ROOT);
        if (!MODES.contains(mode)) throw new IllegalArgumentException("不支持的文档模式");
        String location = text(request.get("projectLocation"));
        jdbcTemplate.update("UPDATE writing_project SET document_mode=?,project_location=?,keywords_json=?,status=?,current_stage=?,updated_at=? WHERE id=?",
                mode, location, json(defaultKeywords(mode, WritingJdbc.text(project.get("title")))),
                "OUTLINE_READY", "模式与提纲已确认", LocalDateTime.now(), projectId);
        jdbcTemplate.update("DELETE FROM writing_chart_series WHERE chart_id IN (SELECT id FROM writing_chart WHERE project_id=?)", projectId);
        jdbcTemplate.update("DELETE FROM writing_chart WHERE project_id=?", projectId);
        jdbcTemplate.update("DELETE FROM writing_table WHERE project_id=?", projectId);
        jdbcTemplate.update("DELETE FROM writing_section WHERE project_id=?", projectId);
        jdbcTemplate.update("DELETE FROM writing_chapter WHERE project_id=?", projectId);
        int chapterNo = 0;
        for (ChapterTemplate chapter : templates(mode)) {
            chapterNo++;
            String chapterId = WritingJdbc.id("wc");
            LocalDateTime now = LocalDateTime.now();
            jdbcTemplate.update("""
                    INSERT INTO writing_chapter (id,project_id,chapter_no,title,chapter_type,target_word_count,section_count,image_count,
                    table_count,use_references,default_chart_type,status,sort_order,created_at,updated_at)
                    VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
                    """, chapterId, projectId, chapterNo, outlineNormalizeService.chapterTitle(chapter.title()), chapterType(chapter.title()), 1200, chapter.sections().size(), 0, 0, true,
                    "NONE", "DRAFT", chapterNo, now, now);
            int sectionOrder = 0;
            for (String sectionTitle : chapter.sections()) {
                sectionOrder++;
                String contentType = contentType(sectionTitle);
                String strategy = "general".equals(mode) ? "manual_count" : ("analysis".equals(contentType) ? "web_search" : "upload");
                jdbcTemplate.update("""
                        INSERT INTO writing_section (id,project_id,chapter_id,section_no,title,target_word_count,sort_order,status,
                        content_type,image_count,table_count,image_strategy,image_requirements_json,created_at,updated_at)
                        VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
                        """, WritingJdbc.id("ws"), projectId, chapterId, sectionNo(mode, chapterNo, sectionOrder), outlineNormalizeService.sectionTitle(sectionTitle),
                        500, sectionOrder, "DRAFT", contentType, 0, 0, strategy,
                        json(defaultImageRequirements(mode, chapterNo, sectionOrder, sectionTitle)), now, now);
            }
        }
        createImageTasks(projectId, mode);
        jdbcTemplate.update("UPDATE writing_project SET chapter_count=?,progress=?,updated_at=? WHERE id=?", chapterNo, 45, LocalDateTime.now(), projectId);
        if (!"general".equals(mode)) analyzeMaterials(projectId);
        if ("environment".equals(mode)) return searchWebImages(projectId);
        return detailForFlow(projectId, ownedProject(projectId));
    }

    @Transactional
    public Map<String, Object> replaceOutline(String projectId, MultipartFile file) {
        ownedProject(projectId);
        if (file == null || file.isEmpty()) throw new IllegalArgumentException("请选择 Word 或 PDF 目录文件");
        String extension = suffix(safeFileName(file.getOriginalFilename()));
        if (!Set.of(".doc", ".docx", ".pdf").contains(extension)) throw new IllegalArgumentException("目录仅支持 doc、docx、pdf");
        try {
            List<ParsedChapter> parsed = parseOutline(extractOutlineText(file, extension));
            if (parsed.isEmpty()) throw new IllegalArgumentException("未识别到一级标题，请检查目录编号");
            jdbcTemplate.update("DELETE FROM writing_chart_series WHERE chart_id IN (SELECT id FROM writing_chart WHERE project_id=?)", projectId);
            jdbcTemplate.update("DELETE FROM writing_chart WHERE project_id=?", projectId);
            jdbcTemplate.update("DELETE FROM writing_table WHERE project_id=?", projectId);
            jdbcTemplate.update("DELETE FROM writing_section WHERE project_id=?", projectId);
            jdbcTemplate.update("DELETE FROM writing_chapter WHERE project_id=?", projectId);
            int chapterNo = 0;
            for (ParsedChapter chapter : parsed) {
                chapterNo++;
                String chapterId = WritingJdbc.id("wc");
                LocalDateTime now = LocalDateTime.now();
                jdbcTemplate.update("""
                        INSERT INTO writing_chapter (id,project_id,chapter_no,title,chapter_type,target_word_count,section_count,image_count,
                        table_count,use_references,default_chart_type,status,sort_order,created_at,updated_at)
                        VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
                        """, chapterId, projectId, chapterNo, outlineNormalizeService.chapterTitle(chapter.title()), chapterType(chapter.title()),
                        1200, chapter.sections().size(), 0, 0, true, "NONE", "DRAFT", chapterNo, now, now);
                int order = 0;
                for (String title : chapter.sections()) {
                    order++;
                    String type = contentType(title);
                    jdbcTemplate.update("""
                            INSERT INTO writing_section (id,project_id,chapter_id,section_no,title,target_word_count,sort_order,status,
                            content_type,image_count,table_count,image_strategy,image_requirements_json,created_at,updated_at)
                            VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
                            """, WritingJdbc.id("ws"), projectId, chapterId, chapterNo + "." + order,
                            outlineNormalizeService.sectionTitle(title), 500, order, "DRAFT", type, 0, 0,
                            "analysis".equals(type) ? "web_search" : "upload", "[]", now, now);
                }
            }
            createImageTasks(projectId, WritingJdbc.text(ownedProject(projectId).get("document_mode")));
            jdbcTemplate.update("UPDATE writing_project SET chapter_count=?,status='OUTLINE_READY',current_stage=?,progress=45,updated_at=? WHERE id=?",
                    chapterNo, "上传目录已完全替换系统模板，等待用户确认", LocalDateTime.now(), projectId);
            analyzeMaterials(projectId);
            return flow(projectId);
        } catch (IOException exception) {
            throw new IllegalStateException("目录文件解析失败：" + exception.getMessage(), exception);
        }
    }

    private String extractOutlineText(MultipartFile file, String extension) throws IOException {
        byte[] bytes = file.getBytes();
        if (".pdf".equals(extension)) {
            try (var pdf = Loader.loadPDF(bytes)) { return new PDFTextStripper().getText(pdf); }
        }
        if (".doc".equals(extension)) {
            try (HWPFDocument doc = new HWPFDocument(new ByteArrayInputStream(bytes)); WordExtractor extractor = new WordExtractor(doc)) {
                return extractor.getText();
            }
        }
        try (XWPFDocument docx = new XWPFDocument(new ByteArrayInputStream(bytes))) {
            return docx.getParagraphs().stream().map(p -> p.getText()).collect(java.util.stream.Collectors.joining("\n"));
        }
    }

    private List<String> defaultKeywords(String mode, String title) {
        if ("environment".equals(mode)) return List.of("街道微更新", "适老化设计", "低碳公共空间", "社区营造");
        if ("visual_communication".equals(mode)) return List.of("视觉传达", "品牌设计", "信息传播", "应用设计");
        if ("interior_design".equals(mode)) return List.of("室内设计", "空间功能", "使用体验", "材料应用");
        String conciseTitle = title == null ? "研究主题" : title.replaceAll("[—–:：].*$", "").trim();
        return List.of(conciseTitle.isBlank() ? "研究主题" : conciseTitle, "现状分析", "设计策略", "实施路径");
    }

    private List<ParsedChapter> parseOutline(String text) {
        List<ParsedChapter> result = new ArrayList<>();
        ParsedChapter current = null;
        for (String raw : text.split("\\R")) {
            String line = raw.replaceAll("[.．·…\\s]+\\d+$", "").trim();
            if (line.isBlank()) continue;
            Matcher section = SECTION_LINE.matcher(line);
            if (section.matches() && current != null) {
                current.sections().add(section.group(3).trim());
                continue;
            }
            Matcher chapter = CHAPTER_LINE.matcher(line);
            if (chapter.matches()) {
                current = new ParsedChapter(chapter.group(1).trim(), new ArrayList<>());
                result.add(current);
            }
        }
        return result;
    }

    @Transactional
    public Map<String, Object> updateSectionConfig(String projectId, String sectionId, Map<String, Object> request) {
        ownedProject(projectId);
        int imageCount = Math.max(0, Math.min(3, WritingJdbc.integer(request.get("imageCount"), 0)));
        int tableCount = Math.max(0, Math.min(3, WritingJdbc.integer(request.get("tableCount"), 0)));
        List<String> requirements = stringList(request.get("imageRequirements"));
        jdbcTemplate.update("UPDATE writing_section SET image_count=?,table_count=?,image_requirements_json=?,updated_at=? WHERE project_id=? AND id=?",
                imageCount, tableCount, json(requirements), LocalDateTime.now(), projectId, sectionId);
        syncSectionImageTasks(projectId, sectionId, requirements);
        return flow(projectId);
    }

    @Transactional
    public Map<String, Object> analyzeMaterials(String projectId) {
        ownedProject(projectId);
        List<Map<String, Object>> sections = sections(projectId);
        for (Map<String, Object> material : materials(projectId)) {
            // 联网图片在检索阶段已经完成用途命名和章节推荐。这里保留检索证据，
            // 只让用户上传图片进入视觉模型，避免一次联网搜索触发多次视觉调用。
            if ("WEB_SEARCH".equalsIgnoreCase(WritingJdbc.text(material.get("source_type")))
                    && !WritingJdbc.text(material.get("ai_suggested_section")).isBlank()) {
                continue;
            }
            String name = (WritingJdbc.text(material.get("display_name")) + " " + WritingJdbc.text(material.get("original_name"))).toLowerCase(Locale.ROOT);
            String detectedType = "";
            String detectedName = "";
            String visionDescription = "";
            double visionConfidence = 0D;
            String analysisStatus = "VISION_ANALYZED";
            String path = WritingJdbc.text(material.get("file_path"));
            if (!path.isBlank() && Files.isRegularFile(Path.of(path))) {
                try {
                    var vision = visionService.analyze(Files.readAllBytes(Path.of(path)), WritingJdbc.text(material.get("original_name")), """
                            识别这张毕业设计或论文素材图片。请按既有JSON结构返回：
                            imageType填写图片类型（如平面图、效果图、区位图、场地现状图、交通分析图、设计草图）；
                            equipmentName仅在画面内容明确时填写简洁名称；detectedAnnotations填写确实可见的画面文字；
                            evidence只填写图中可直接观察到的客观事实，不得根据论文题目或章节猜测场地、边界、人流、区位等含义；
                            qualityScore按识别可靠度填写0到100。无法可靠识别时不要编造名称和事实。
                            """);
                    detectedType = vision.result().getImageType();
                    detectedName = vision.result().getEquipmentName();
                    visionDescription = String.join("；", vision.result().getEvidence());
                    visionConfidence = Math.max(0D, Math.min(1D, vision.result().getQualityScore() / 100D));
                    name = (name + " " + detectedType + " " + detectedName + " " + String.join(" ", vision.result().getDetectedAnnotations())).toLowerCase(Locale.ROOT);
                } catch (Exception ignored) {
                    analysisStatus = "FALLBACK_ANALYZED";
                }
            } else analysisStatus = "FALLBACK_ANALYZED";
            boolean reliable = visionConfidence >= 0.65D;
            Map<String, Object> best = reliable ? chooseSection(name, sections) : null;
            String category = reliable && !detectedType.isBlank() ? detectedType : "待人工确认图片";
            String usage = reliable && best != null
                    ? "视觉识别：" + (visionDescription.isBlank() ? "内容已识别，仍需用户确认" : visionDescription)
                    : "视觉识别置信度不足，请人工确认图片名称与章节；正文不得据此推断具体事实";
            jdbcTemplate.update("""
                    UPDATE writing_image_material SET display_name=CASE WHEN ?='' THEN display_name ELSE ? END,ai_category=?,ai_usage=?,ai_suggested_chapter=?,ai_suggested_section=?,
                    vision_description=?,vision_confidence=?,analysis_status=?,updated_at=? WHERE id=?
                    """, reliable ? detectedName : "", reliable ? detectedName : "", category, usage,
                    best == null ? null : best.get("chapter_id"), best == null ? null : best.get("id"),
                    visionDescription, visionConfidence, reliable ? analysisStatus : "PENDING_CONFIRMATION",
                    LocalDateTime.now(), material.get("id"));
        }
        return flow(projectId);
    }

    @Transactional
    public Map<String, Object> searchWebImages(String projectId) {
        Map<String, Object> project = ownedProject(projectId);
        Path root = storageRoot.resolve(projectId).resolve("web-search").normalize();
        ensureInsideStorage(root);
        jdbcTemplate.update("UPDATE writing_image_task SET status='SEARCHING',message='',updated_at=? WHERE project_id=? AND source_type='WEB_SEARCH' AND status<>'CONFIRMED'",
                LocalDateTime.now(), projectId);
        Map<String, Object> searchReport = imageLibraryService.prepare(projectId, AuthContext.requireUserId(), root);
        synchronizeImageTasks(projectId, searchReport);
        analyzeMaterials(projectId);
        Map<String, Object> result = new LinkedHashMap<>(flow(projectId));
        result.put("webImageSearch", searchReport);
        return result;
    }

    public Map<String, Object> flow(String projectId) {
        Map<String, Object> project = ownedProject(projectId);
        return detailForFlow(projectId, project);
    }

    private Map<String, Object> detailForFlow(String projectId, Map<String, Object> project) {
        Map<String, Object> result = new LinkedHashMap<>(project);
        List<Map<String, Object>> chapters = WritingJdbc.list(jdbcTemplate, "SELECT * FROM writing_chapter WHERE project_id=? ORDER BY sort_order", projectId);
        for (Map<String, Object> chapter : chapters) {
            chapter.put("sections", WritingJdbc.list(jdbcTemplate, "SELECT * FROM writing_section WHERE chapter_id=? ORDER BY sort_order", chapter.get("id")));
            chapter.put("tables", WritingJdbc.list(jdbcTemplate, "SELECT * FROM writing_table WHERE chapter_id=? ORDER BY sort_order", chapter.get("id")));
        }
        result.put("chapters", chapters);
        result.put("materials", materials(projectId));
        result.put("imageTasks", WritingJdbc.list(jdbcTemplate, """
                SELECT t.*,c.chapter_no,s.section_no,s.title AS section_title
                FROM writing_image_task t JOIN writing_chapter c ON c.id=t.chapter_id JOIN writing_section s ON s.id=t.section_id
                WHERE t.project_id=? ORDER BY c.sort_order,s.sort_order,t.sort_order
                """, projectId));
        result.put("bodyGenerationReady", !chapters.isEmpty());
        result.put("bodyGenerationImplemented", true);
        result.put("generationRules", Map.of(
                "imageReferencePattern", "如图X-X所示",
                "tableReferencePattern", "如表X-X所示",
                "insertMode", "inline_while_writing",
                "maxImagesPerSection", 3,
                "maxTablesPerSection", 3,
                "analysisImagePriority", "web_search",
                "designImagePriority", "upload"
        ));
        return result;
    }

    private List<Map<String, Object>> sections(String projectId) {
        return WritingJdbc.list(jdbcTemplate, "SELECT s.*,c.title AS chapter_title,c.chapter_no FROM writing_section s JOIN writing_chapter c ON c.id=s.chapter_id WHERE s.project_id=? ORDER BY c.sort_order,s.sort_order", projectId);
    }

    private Map<String, Object> chooseSection(String fileName, List<Map<String, Object>> sections) {
        Map<String, Object> fallback = sections.stream().filter(s -> "design".equals(WritingJdbc.text(s.get("content_type")))).findFirst().orElse(sections.isEmpty() ? null : sections.get(0));
        for (Map<String, Object> section : sections) {
            String title = WritingJdbc.text(section.get("title"));
            for (String token : List.of("平面", "功能", "流线", "效果", "展板", "模型", "草图", "区位", "基址", "周边", "现状", "logo", "包装", "海报", "视觉")) {
                if (fileName.contains(token.toLowerCase(Locale.ROOT)) && title.toLowerCase(Locale.ROOT).contains(token.toLowerCase(Locale.ROOT))) return section;
            }
        }
        return fallback;
    }

    private List<ChapterTemplate> templates(String mode) {
        if ("environment".equals(mode)) return List.of(
                c("第1章 绪论", "1.1 课题背景", "1.2 课题意义"), c("第2章 项目概况及基址分析", "2.1 项目概况", "2.2 基址分析"),
                c("第3章 设计依据及设计原则", "3.1 设计依据", "3.2 设计原则"), c("第4章 设计方案", "4.1 设计理念", "4.2 设计概况", "4.3 功能分区"),
                c("第5章 结论与展望"), c("参考文献"), c("致谢"));
        if ("visual_communication".equals(mode)) return List.of(
                c("一、绪论", "（一）研究背景", "（二）研究目的与意义", "（三）国内外研究现状"),
                c("二、项目背景分析与设计定位", "（一）项目概况分析", "（二）设计对象分析", "（三）用户需求分析", "（四）设计定位"),
                c("三、设计理念与基础设计", "（一）设计理念", "（二）设计元素提取与分析", "（三）核心视觉设计", "（四）辅助视觉系统设计"),
                c("四、设计方案与应用设计", "（一）基础应用设计", "（二）衍生产品设计", "（三）系列化设计拓展", "（四）设计效果展示与分析"),
                c("五、设计传播与应用策略", "（一）传播渠道构建", "（二）应用场景拓展"), c("结论"), c("参考文献"));
        if ("interior_design".equals(mode)) return List.of(
                c("一、绪论", "（一）研究背景", "（二）研究内容", "（三）研究目的和意义", "（四）国内外研究现状"),
                c("二、项目概况", "（一）项目现状", "（二）基地现状调研", "（三）周边环境分析"),
                c("三、设计策略与实践", "（一）策略", "（二）方案转换", "（三）设计定位"),
                c("四、设计方案展示", "（一）前期草图方案", "（二）作品平面图", "（三）作品功能分析", "（四）作品流线", "（五）作品效果图", "（六）作品展板图", "（七）作品模型图"),
                c("五、设计方案创新点、总结"));
        return List.of(c("第1章 绪论", "1.1 研究背景", "1.2 研究意义", "1.3 国内外研究现状"),
                c("第2章 理论基础", "2.1 核心概念", "2.2 理论依据"), c("第3章 现状与问题分析", "3.1 发展现状", "3.2 主要问题", "3.3 原因分析"),
                c("第4章 对策与实施路径", "4.1 总体思路", "4.2 实施路径", "4.3 保障措施"), c("第5章 结论与展望", "5.1 研究结论", "5.2 研究展望"));
    }

    private String contentType(String title) {
        if (containsAny(title, "分析", "调研", "现状", "概况", "背景", "区位", "基址", "周边", "人文", "用户需求", "研究现状")) return "analysis";
        if (containsAny(title, "设计", "方案", "草图", "平面图", "功能", "流线", "效果图", "展板", "模型", "应用")) return "design";
        if (containsAny(title, "结论", "展望", "总结")) return "conclusion";
        return "general";
    }

    private String chapterType(String title) {
        String normalized = text(title).toLowerCase(Locale.ROOT);
        if (normalized.contains("参考文献") || normalized.equals("references")) return "reference";
        if (normalized.contains("致谢") || normalized.equals("acknowledgement") || normalized.equals("acknowledgments")) return "acknowledgement";
        if (normalized.contains("结论") || normalized.contains("展望") || normalized.contains("总结")) return "conclusion";
        return "content";
    }

    private boolean containsAny(String value, String... terms) { for (String term : terms) if (value.contains(term)) return true; return false; }
    private String sectionNo(String mode, int chapter, int section) { return "environment".equals(mode) || "general".equals(mode) ? chapter + "." + section : chapter + "-" + section; }
    private ChapterTemplate c(String title, String... sections) { return new ChapterTemplate(title, List.of(sections)); }
    private Map<String, Object> ownedProject(String id) { return WritingJdbc.one(jdbcTemplate, "SELECT * FROM writing_project WHERE id=? AND user_id=?", id, AuthContext.requireUserId()); }
    private Map<String, Object> ownedMaterial(String projectId, String id) { ownedProject(projectId); return WritingJdbc.one(jdbcTemplate, "SELECT * FROM writing_image_material WHERE project_id=? AND id=?", projectId, id); }
    private void validateImage(MultipartFile file) {
        if (file == null || file.isEmpty()) throw new IllegalArgumentException("图片文件不能为空");
        if (file.getSize() > 15L * 1024 * 1024) throw new IllegalArgumentException("单张图片不能超过15MB");
        String type = text(file.getContentType()).toLowerCase(Locale.ROOT);
        String extension = suffix(safeFileName(file.getOriginalFilename()));
        if (!Set.of(".jpg", ".jpeg", ".png", ".webp").contains(extension)
                || !Set.of("image/jpeg", "image/png", "image/webp").contains(type)) {
            throw new IllegalArgumentException("仅支持 jpg、jpeg、png、webp 图片");
        }
    }
    private String safeFileName(String value) { String name = value == null ? "image" : Path.of(value).getFileName().toString(); return name.replaceAll("[\\r\\n]", "_"); }
    private String suffix(String name) { int index = name.lastIndexOf('.'); return index < 0 ? ".img" : name.substring(index).toLowerCase(Locale.ROOT); }
    private String withoutSuffix(String name) { int index = name.lastIndexOf('.'); return index <= 0 ? name : name.substring(0, index); }
    private void ensureInsideStorage(Path path) { if (!path.toAbsolutePath().normalize().startsWith(storageRoot)) throw new IllegalArgumentException("非法文件路径"); }
    private String text(Object value) { return value == null ? "" : String.valueOf(value).trim(); }
    private String textOr(Object value, String fallback) { String text = text(value); return text.isBlank() ? fallback : text; }

    private List<String> defaultImageRequirements(String mode, int chapter, int section, String title) {
        if ("general".equals(mode)) return List.of();
        if ("environment".equals(mode) && chapter == 2 && section == 1) return List.of("地理位置图", "区位图", "地图定位图");
        if ("environment".equals(mode) && chapter == 2 && section == 2) return List.of("场地现状图", "周边环境图", "交通分析图");
        if (chapter == 4 && section == 1) return List.of("设计草图");
        if (chapter == 4 && section == 2) return List.of("整体效果图");
        if (chapter == 4 && section == 3) return List.of("功能分区图", "平面图");
        return List.of();
    }

    private List<String> stringList(Object value) {
        if (value instanceof String raw && !raw.isBlank()) {
            try { value = objectMapper.readValue(raw, List.class); }
            catch (Exception ignored) { return List.of(); }
        }
        if (!(value instanceof List<?> list)) return List.of();
        return list.stream().map(this::text).filter(item -> !item.isBlank()).distinct().limit(3).toList();
    }

    private String json(Object value) {
        try { return objectMapper.writeValueAsString(value); }
        catch (Exception exception) { throw new IllegalStateException("图片需求序列化失败", exception); }
    }

    private record ChapterTemplate(String title, List<String> sections) {}
    private record ParsedChapter(String title, List<String> sections) {}
    public record MaterialContent(byte[] bytes, String contentType, String fileName) {}
}
