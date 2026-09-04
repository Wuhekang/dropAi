package com.dropai.rewrite.service.ppt.enhancement;

import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class PptEnhancementSkillService {
    public static final String NAME = "ppt-enhancement";
    public static final String VERSION = "1.2.0";
    public static final List<String> RESOURCES = List.of(
        "skills/ppt-enhancement/SKILL.md",
        "skills/ppt-enhancement/references/visual-recipes.md",
        "skills/ppt-enhancement/references/qa-and-logging.md"
    );

    public SkillBundle requireBundle() {
        try {
            List<SkillResource> loaded = new ArrayList<>();
            MessageDigest bundleDigest = MessageDigest.getInstance("SHA-256");
            StringBuilder prompt = new StringBuilder();
            for (String location : RESOURCES) {
                ClassPathResource resource = new ClassPathResource(location);
                if (!resource.exists()) {
                    throw new IllegalStateException("PPT增幅美化Skill资源缺失：classpath:/" + location);
                }
                String body;
                try (InputStream input = resource.getInputStream()) {
                    body = canonicalize(new String(input.readAllBytes(), StandardCharsets.UTF_8));
                }
                String hash = sha256(body.getBytes(StandardCharsets.UTF_8));
                loaded.add(new SkillResource(location, hash, body.length()));
                bundleDigest.update(location.getBytes(StandardCharsets.UTF_8));
                bundleDigest.update((byte) 0);
                bundleDigest.update(body.getBytes(StandardCharsets.UTF_8));
                prompt.append("\n--- RESOURCE: ").append(location).append(" ---\n").append(body);
            }
            String trustedPrompt = prompt.toString().strip() + "\n";
            validate(trustedPrompt);
            return new SkillBundle(NAME, VERSION, HexFormat.of().formatHex(bundleDigest.digest()),
                trustedPrompt, List.copyOf(loaded));
        } catch (IllegalStateException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IllegalStateException("PPT增幅美化Skill读取失败", exception);
        }
    }

    private void validate(String prompt) {
        List<String> required = List.of(
            "name: ppt-enhancement",
            "Skill version: " + VERSION,
            "polish",
            "text policy",
            "locked",
            "balanced",
            "Plan before editing",
            "Visual recipes",
            "Quality assurance and enhancement logging",
            "protectedTemplatePartsByteIdentical",
            "IMAGE_BACKGROUND",
            "backgroundOnly",
            "protectedMediaSlide",
            "DOUBAO_ENHANCEMENT_RULES_BEGIN",
            "DOUBAO_ENHANCEMENT_RULES_END"
        );
        for (String token : required) {
            if (!prompt.contains(token)) {
                throw new IllegalStateException("PPT增幅美化Skill缺少规则：" + token);
            }
        }
        if (prompt.length() < 15_000) {
            throw new IllegalStateException("PPT增幅美化Skill规则过短，不能作为详细豆包契约");
        }
    }

    private String canonicalize(String body) {
        return body.replace("\r\n", "\n").replace('\r', '\n').strip() + "\n";
    }

    private String sha256(byte[] bytes) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    }

    public Map<String, Object> manifestMap(SkillBundle bundle) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("skillName", bundle.name());
        result.put("skillVersion", bundle.version());
        result.put("skillHash", bundle.hash());
        result.put("resources", bundle.resources());
        return result;
    }

    public record SkillResource(String path, String sha256, int characterCount) {}

    public record SkillBundle(
        String name,
        String version,
        String hash,
        String trustedPrompt,
        List<SkillResource> resources
    ) {}
}
