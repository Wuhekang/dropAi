package com.dropai.rewrite.service.ppt;

import com.dropai.rewrite.service.ppt.rendering.template.v1.RenderingTemplatePackRegistry;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Chooses one trusted visual direction for a new PPT project.
 *
 * <p>The choice varies between projects, but is a pure function of persisted project data. It is
 * therefore safe to freeze before RenderPlan compilation and is stable across retries, hosts and
 * JVM processes. This class never reads slide wording and never participates in rendering.</p>
 */
final class PptAutoVisualDirector {
    static final String MODE = "AUTO_VISUAL_DIRECTION_V1";
    static final String LAYOUT_STRATEGY = "SEMANTIC_LAYOUT_VARIATION_V1";
    static final String PALETTE_STRATEGY = "TRUSTED_TEMPLATE_PALETTE_V1";

    Direction direct(Map<String, Object> project) {
        String domain = domain(project);
        List<String> pool = candidatePool(domain, project);
        String seed = sha256(seedMaterial(project, domain));
        int index = Math.floorMod(firstInt(seed), pool.size());
        String selected = pool.get(index);
        String fingerprint = sha256(String.join("|", MODE, seed, selected,
                String.join(",", pool), LAYOUT_STRATEGY, PALETTE_STRATEGY));
        return new Direction(selected, seed, fingerprint, domain, pool,
                PALETTE_STRATEGY, LAYOUT_STRATEGY,
                "Doki 已根据专业、文档类型、图片比例与任务种子生成并冻结本次视觉方案");
    }

    private List<String> candidatePool(String domain, Map<String, Object> project) {
        List<String> base = switch (domain) {
            case "NURSING_MEDICAL" -> List.of(
                    RenderingTemplatePackRegistry.BEAR_MORANDI_CORAL_V1,
                    RenderingTemplatePackRegistry.BEAR_MORANDI_OLIVE_V1,
                    RenderingTemplatePackRegistry.BEAR_MINIMAL_TAN_V1,
                    RenderingTemplatePackRegistry.BEAR_EDITORIAL_OLIVE_V1,
                    RenderingTemplatePackRegistry.BEAR_FLORAL_BEIGE_V1,
                    RenderingTemplatePackRegistry.SMALL_BEAR_WATERCOLOR_BLUE_V1);
            case "ELECTRICAL_ENGINEERING" -> List.of(
                    RenderingTemplatePackRegistry.ELECTRICAL_COLLEGE_DEFENSE_V1,
                    RenderingTemplatePackRegistry.SMALL_BEAR_WATERCOLOR_BLUE_V1,
                    RenderingTemplatePackRegistry.BEAR_MINIMAL_TAN_V1);
            case "COMPUTER_ENGINEERING" -> List.of(
                    RenderingTemplatePackRegistry.BEAR_MINIMAL_TAN_V1,
                    RenderingTemplatePackRegistry.SMALL_BEAR_WATERCOLOR_BLUE_FULL_V1);
            case "BUSINESS_SOCIAL" -> List.of(
                    RenderingTemplatePackRegistry.BEAR_EDITORIAL_OLIVE_V1,
                    RenderingTemplatePackRegistry.BEAR_MORANDI_OLIVE_V1,
                    RenderingTemplatePackRegistry.BEAR_MINIMAL_TAN_V1,
                    RenderingTemplatePackRegistry.BEAR_MORANDI_CORAL_V1);
            case "DESIGN_ENVIRONMENT" -> List.of(
                    RenderingTemplatePackRegistry.BEAR_FLORAL_BEIGE_V1,
                    RenderingTemplatePackRegistry.BEAR_MORANDI_CORAL_V1,
                    RenderingTemplatePackRegistry.BEAR_MORANDI_OLIVE_V1,
                    RenderingTemplatePackRegistry.SMALL_BEAR_WATERCOLOR_BLUE_V1,
                    RenderingTemplatePackRegistry.BEAR_MINIMAL_TAN_V1);
            default -> List.of(
                    RenderingTemplatePackRegistry.SMALL_BEAR_WATERCOLOR_BLUE_V1,
                    RenderingTemplatePackRegistry.BEAR_MINIMAL_TAN_V1,
                    RenderingTemplatePackRegistry.BEAR_EDITORIAL_OLIVE_V1,
                    RenderingTemplatePackRegistry.BEAR_MORANDI_OLIVE_V1,
                    RenderingTemplatePackRegistry.BEAR_MORANDI_CORAL_V1,
                    RenderingTemplatePackRegistry.BEAR_FLORAL_BEIGE_V1);
        };
        int images = integer(project.get("image_count"), 0);
        int slides = Math.max(1, integer(project.get("target_slide_count"), 16));
        if (images * 2 < slides) return base;
        LinkedHashSet<String> imageFriendly = new LinkedHashSet<>(List.of(
                RenderingTemplatePackRegistry.SMALL_BEAR_WATERCOLOR_BLUE_V1,
                RenderingTemplatePackRegistry.BEAR_MORANDI_CORAL_V1,
                RenderingTemplatePackRegistry.BEAR_FLORAL_BEIGE_V1,
                RenderingTemplatePackRegistry.BEAR_EDITORIAL_OLIVE_V1));
        imageFriendly.retainAll(base);
        imageFriendly.addAll(base);
        return List.copyOf(imageFriendly);
    }

    private String domain(Map<String, Object> project) {
        String text = (string(project.get("major")) + " " + string(project.get("topic")) + " "
                + string(project.get("source_file_name")) + " " + string(project.get("analysis_json")))
                .toLowerCase(Locale.ROOT);
        if (containsAny(text, "护理", "医学", "患者", "临床", "营养", "康复")) return "NURSING_MEDICAL";
        if (containsAny(text, "电气", "自动化", "机器视觉", "电机", "电力", "控制工程")) return "ELECTRICAL_ENGINEERING";
        if (containsAny(text, "计算机", "软件", "spring boot", "java", "vue", "信息系统", "网络工程",
                "智能问答", "推荐系统", "知识图谱", "图书推荐")) return "COMPUTER_ENGINEERING";
        if (containsAny(text, "财务", "会计", "审计", "金融", "经济", "工商管理", "企业管理", "乡村治理", "法学", "教育学", "汉语言")) return "BUSINESS_SOCIAL";
        if (containsAny(text, "环境设计", "视觉传达", "景观", "室内", "建筑", "艺术设计", "数字媒体")) return "DESIGN_ENVIRONMENT";
        return "GENERAL_ACADEMIC";
    }

    private String seedMaterial(Map<String, Object> project, String domain) {
        return String.join("|", "dokiai-ppt-visual-v1", string(project.get("id")),
                string(project.get("source_file_name")), string(project.get("topic")),
                string(project.get("major")), domain,
                String.valueOf(integer(project.get("image_count"), 0)),
                String.valueOf(integer(project.get("target_slide_count"), 16)));
    }

    private boolean containsAny(String value, String... terms) {
        for (String term : terms) if (value.contains(term)) return true;
        return false;
    }

    private int firstInt(String sha256) {
        return (int) Long.parseUnsignedLong(sha256.substring(0, 8), 16);
    }

    private String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private String string(Object value) { return value == null ? "" : String.valueOf(value); }
    private int integer(Object value, int fallback) {
        try { return value instanceof Number number ? number.intValue() : Integer.parseInt(string(value)); }
        catch (Exception ignored) { return fallback; }
    }

    record Direction(
            String templatePackId,
            String visualSeed,
            String selectionFingerprint,
            String domain,
            List<String> candidatePool,
            String paletteStrategy,
            String layoutStrategy,
            String reason
    ) {}
}
