package com.dropai.rewrite.service.ppt.enhancement;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Component
public class PptEnhancementPlanValidator {
    private static final Map<String, String> RECIPE_BY_ARCHETYPE = Map.of(
        "cover", "COVER_ACCENT",
        "catalog", "AGENDA_RAIL",
        "section", "SECTION_MOTIF",
        "content", "CONTENT_RAIL",
        "image", "IMAGE_FRAME",
        "table", "TABLE_RAIL",
        "summary", "SUMMARY_RAIL",
        "closing", "CLOSING_ECHO"
    );

    public PptEnhancementPlan validateAndExpand(
        JsonNode raw,
        PptEnhancementSkillService.SkillBundle skill,
        PptEnhancementAiService.AiPlanResponse audit,
        PptxBaselineInspector.DeckInventory inventory,
        String requestedProfile
    ) {
        if (raw == null || !raw.isObject()) throw new IllegalStateException("增幅计划不是JSON对象");
        requireEquals("schemaVersion", "1.0", raw.path("schemaVersion").asText());
        requireEquals("sourcePptxSha256", inventory.sourcePptxSha256(), raw.path("sourcePptxSha256").asText());
        requireEquals("mode", "polish", raw.path("mode").asText());
        requireEquals("profile", requestedProfile, raw.path("profile").asText());
        requireEquals("textPolicy", "locked", raw.path("textPolicy").asText());
        JsonNode slides = raw.path("slides");
        if (!slides.isArray() || slides.size() != inventory.slideCount()) {
            throw new IllegalStateException("豆包增幅计划页数不完整：应为" + inventory.slideCount() + "页");
        }
        Set<Integer> seen = new HashSet<>();
        Map<Integer, PptxBaselineInspector.SlideInventory> baseline = new HashMap<>();
        inventory.slides().forEach(slide -> baseline.put(slide.slideNumber(), slide));
        List<PptEnhancementPlan.SlidePlan> expanded = new ArrayList<>();
        int expected = 1;
        for (JsonNode slide : slides) {
            int number = slide.path("slideNumber").asInt(-1);
            if (number != expected++ || !seen.add(number)) throw new IllegalStateException("增幅计划页码必须完整、唯一并严格升序");
            String archetype = slide.path("archetype").asText("").toLowerCase();
            String recipe = slide.path("recipeId").asText("");
            if (!RECIPE_BY_ARCHETYPE.containsKey(archetype)) throw new IllegalStateException("未知页面类型：" + archetype);
            if (!RECIPE_BY_ARCHETYPE.get(archetype).equals(recipe)) throw new IllegalStateException("页面类型与美化配方不匹配：第" + number + "页");
            PptxBaselineInspector.SlideInventory source = baseline.get(number);
            if (source == null) throw new IllegalStateException("增幅计划引用了不存在的页面：" + number);
            if (!compatible(source.suggestedArchetype(), archetype)) {
                throw new IllegalStateException("豆包页面分类与基础PPT结构不一致：第" + number + "页");
            }
            expanded.add(expand(number, archetype, recipe));
        }
        return new PptEnhancementPlan("1.0", inventory.sourcePptxSha256(), skill.name(), skill.version(), skill.hash(),
            "polish", requestedProfile, "locked", audit.provider(), audit.model(), audit.providerInvoked(),
            audit.providerStatus(), List.copyOf(expanded));
    }

    private boolean compatible(String suggested, String actual) {
        if (suggested.equals(actual)) return true;
        return Set.of("content", "summary").contains(suggested) && Set.of("content", "summary").contains(actual);
    }

    private PptEnhancementPlan.SlidePlan expand(int page, String archetype, String recipe) {
        String focal = switch (recipe) {
            case "COVER_ACCENT" -> "强化封面标题与身份信息的视觉入口";
            case "AGENDA_RAIL" -> "建立目录页的章节阅读节奏";
            case "SECTION_MOTIF" -> "强化章节过渡与叙事停顿";
            case "IMAGE_FRAME" -> "以模板原生线条强化成果图片展示";
            case "TABLE_RAIL" -> "强化表格页的信息边界与扫描路径";
            case "SUMMARY_RAIL" -> "强化总结与展望的收束感";
            case "CLOSING_ECHO" -> "呼应封面视觉语言形成闭环";
            default -> "强化标题层级与页面阅读节奏";
        };
        List<String> details = switch (recipe) {
            case "AGENDA_RAIL", "TABLE_RAIL" -> List.of("细线导航轨", "模板色节点");
            case "SECTION_MOTIF" -> List.of("对称短线", "低强度几何节点");
            case "IMAGE_FRAME" -> List.of("图片角标", "边缘细线");
            default -> List.of("模板色强调线", "微型几何节点");
        };
        PptEnhancementPlan.Addition addition = new PptEnhancementPlan.Addition(
            "shape", "text-free template-native visual accent", "slide safe margin", true, false);
        return new PptEnhancementPlan.SlidePlan(page, page, archetype, recipe, focal, details, List.of(addition));
    }

    private void requireEquals(String field, String expected, String actual) {
        if (!expected.equals(actual)) throw new IllegalStateException("增幅计划字段" + field + "不符合契约");
    }
}
