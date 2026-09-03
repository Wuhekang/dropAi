package com.dropai.rewrite.service.ppt;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class PptGenerationSkillService {
    public static final String NAME = "ppt-generation";
    public static final String VERSION = "2.0.0";
    public static final String CLASSPATH_LOCATION = "skills/ppt-generation/SKILL.md";
    static final String PROVIDER_RULES_BEGIN = "<!-- DOUBAO_OUTLINE_RULES_BEGIN -->";
    static final String PROVIDER_RULES_END = "<!-- DOUBAO_OUTLINE_RULES_END -->";

    private final ObjectMapper mapper;

    public PptGenerationSkillService(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    /**
     * Loads the packaged, trusted skill contract. Runtime execution deliberately does not
     * depend on the process working directory, so the same bytes are used from an IDE and
     * from the production Spring Boot jar.
     */
    public SkillManifest requireManifest() {
        return requirePromptContract().manifest();
    }

    /**
     * Returns the complete manifest plus the bounded rules that may be sent to the AI
     * provider. The provider section is explicit so renderer, layout and template duties
     * cannot accidentally be delegated to a content-planning model.
     */
    public SkillPromptContract requirePromptContract() {
        try {
            ClassPathResource resource = new ClassPathResource(CLASSPATH_LOCATION);
            if (!resource.exists()) {
                throw new IllegalStateException("PPT生成规则缺失：classpath:/" + CLASSPATH_LOCATION);
            }
            String canonical;
            try (InputStream input = resource.getInputStream()) {
                canonical = canonicalize(new String(input.readAllBytes(), StandardCharsets.UTF_8));
            }
            validate(canonical);
            String providerRules = between(canonical, PROVIDER_RULES_BEGIN, PROVIDER_RULES_END).strip();
            SkillManifest manifest = new SkillManifest(
                NAME,
                VERSION,
                "classpath:/" + CLASSPATH_LOCATION,
                sha256(canonical),
                canonical.length()
            );
            return new SkillPromptContract(manifest, providerRules);
        } catch (IllegalStateException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IllegalStateException("PPT生成Skill读取失败", exception);
        }
    }

    public Path writeLog(
        Path output,
        Map<String, Object> documentAnalysis,
        List<Map<String, Object>> assets,
        List<Map<String, Object>> slidePlan,
        Map<String, Object> template,
        String validationStatus,
        List<String> autoFixes
    ) throws Exception {
        return writeLog(output, documentAnalysis, assets, slidePlan, template, validationStatus, autoFixes, Map.of());
    }

    public Path writeLog(
        Path output,
        Map<String, Object> documentAnalysis,
        List<Map<String, Object>> assets,
        List<Map<String, Object>> slidePlan,
        Map<String, Object> template,
        String validationStatus,
        List<String> autoFixes,
        Map<String, Object> providerAudit
    ) throws Exception {
        SkillManifest skill = requireManifest();
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("skillName", skill.name());
        root.put("skillVersion", skill.version());
        root.put("skillHash", skill.hash());
        root.put("provider", providerAudit.getOrDefault("provider", ""));
        root.put("model", providerAudit.getOrDefault("model", ""));
        root.put("providerStatus", providerAudit.getOrDefault("status", ""));
        root.put("documentAnalysis", documentAnalysis);
        root.put("assets", assets);
        root.put("slidePlan", slidePlan);
        root.put("template", template);
        root.put("validation", Map.of("status", validationStatus, "autoFixes", autoFixes));
        String name = output.getFileName().toString();
        int dot = name.lastIndexOf('.');
        Path reportDir = output.resolveSibling((dot > 0 ? name.substring(0, dot) : name) + "-debug");
        Files.createDirectories(reportDir);
        write(reportDir, "source-precheck-report.json", documentAnalysis.getOrDefault("precheck", Map.of()));
        write(reportDir, "source-chapters.json", documentAnalysis.getOrDefault("headings", List.of()));
        write(reportDir, "source-assets.json", assets);
        write(reportDir, "filtered-assets.json", Map.of("filteredCount", documentAnalysis.getOrDefault("filteredAssetCount", 0)));
        write(reportDir, "chapter-asset-map.json", assets);
        write(reportDir, "source-tables.json", Map.of("tableCount", documentAnalysis.getOrDefault("tableCount", 0)));
        write(reportDir, "ppt-outline.json", slidePlan.stream().map(x -> x.get("chapter")).filter(x -> x != null && !String.valueOf(x).isBlank()).distinct().toList());
        write(reportDir, "ppt-slide-plan.json", slidePlan);
        write(reportDir, "page-generation-log.json", slidePlan);
        write(reportDir, "layout-validation-report.json", Map.of("status", validationStatus, "autoFixes", autoFixes));
        write(reportDir, "final-quality-report.json", Map.of("status", validationStatus, "autoFixes", autoFixes, "editablePptx", true));
        Path log = reportDir.resolve("generation-summary.json");
        write(reportDir, "generation-summary.json", root);
        return log;
    }

    private void validate(String body) {
        List<String> required = List.of(
            "name: ppt-generation",
            "Skill version: 2.0.0",
            "DocumentParser",
            "ContentPlanner",
            "OutlinePlanner",
            "AssetMapper",
            "LayoutPlanner",
            "PureRenderer",
            "candidatePages",
            "answerQuestion",
            "metadata isolation",
            PROVIDER_RULES_BEGIN,
            PROVIDER_RULES_END
        );
        for (String token : required) {
            if (!body.contains(token)) {
                throw new IllegalStateException("PPT生成Skill缺少规则：" + token);
            }
        }
    }

    private String between(String body, String begin, String end) {
        int start = body.indexOf(begin);
        int finish = body.indexOf(end, start + begin.length());
        if (start < 0 || finish <= start) {
            throw new IllegalStateException("PPT生成Skill缺少豆包内容规划规则边界");
        }
        String result = body.substring(start + begin.length(), finish);
        if (result.length() < 1_000) {
            throw new IllegalStateException("PPT生成Skill中的豆包内容规划规则过短");
        }
        return result;
    }

    private String canonicalize(String body) {
        return body.replace("\r\n", "\n").replace('\r', '\n').strip() + "\n";
    }

    private String sha256(String body) throws Exception {
        byte[] digest = MessageDigest.getInstance("SHA-256").digest(body.getBytes(StandardCharsets.UTF_8));
        return HexFormat.of().formatHex(digest);
    }

    private void write(Path directory, String file, Object value) throws Exception {
        Files.writeString(directory.resolve(file), mapper.writerWithDefaultPrettyPrinter().writeValueAsString(value));
    }

    public record SkillManifest(String name, String version, String path, String hash, int characterCount) {}

    public record SkillPromptContract(SkillManifest manifest, String providerRules) {}
}
