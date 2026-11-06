package com.dropai.rewrite.service.ppt;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Converts persisted, approved slide plans into the frozen renderer contract without generating content. */
@Service
public class PptProductionTreeAdapter {
    private final ObjectMapper mapper;

    public PptProductionTreeAdapter(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    public PptAssetMapperV1.MappingResult adapt(
            Map<String, Object> project,
            List<Map<String, Object>> sections,
            List<Map<String, Object>> slides,
            List<Map<String, Object>> assets) {
        Map<String, Map<String, Object>> assetById = new LinkedHashMap<>();
        for (var asset : assets) assetById.put(s(asset.get("id")), asset);
        List<PptAssetMapperV1.MappedSlideNode> out = new ArrayList<>();
        int pageNumber = 1;
        var payload = new PptOutlineValidatorV1.FixedPayload(
                required(project, "topic"), required(project, "english_topic"),
                required(project, "presenter"), required(project, "major"),
                required(project, "advisor"), required(project, "student_number"));
        out.add(mapped(fixed(pageNumber++, "COVER", payload.title(), payload, List.of()), List.of()));

        List<PptOutlineValidatorV1.AgendaItem> agenda = new ArrayList<>();
        int start = 3;
        int sectionIndex = 0;
        for (var section : sections) {
            String sectionId = s(section.get("id"));
            long count = slides.stream().filter(slide -> sectionId.equals(s(slide.get("section_id")))).count();
            if (count > 0) {
                agenda.add(new PptOutlineValidatorV1.AgendaItem(
                        ++sectionIndex, s(section.get("title")), "SECTION_" + sectionIndex,
                        List.of(start, start + (int) count - 1)));
                start += (int) count;
            }
        }
        out.add(mapped(fixed(pageNumber++, "AGENDA", "目录", null, agenda), List.of()));

        for (var slide : slides) {
            String title = required(slide, "title");
            List<String> boxes = list(slide.get("body_boxes_json"));
            String description = s(slide.get("content_summary"));
            if (description.isBlank() && !boxes.isEmpty()) description = boxes.get(boxes.size() - 1);
            final String summary = description;
            List<String> points = boxes.stream().filter(value -> !value.equals(summary)).limit(3).toList();
            if (points.isEmpty()) points = boxes.stream().limit(3).toList();
            String pageType = "IMAGE".equalsIgnoreCase(s(slide.get("slide_type"))) ? "IMAGE" : "CONTENT";
            String purpose = purpose(title, pageType);
            var refs = new PptContentPlannerV2.SourceRefs(
                    s(slide.get("chapter_title")), List.of(), "", List.of(), List.of());
            List<PptAssetMapperV1.AssetBinding> bindings = new ArrayList<>();
            if ("IMAGE".equals(pageType)) {
                List<String> ids = list(slide.get("asset_ids_json"));
                if (ids.isEmpty()) throw new IllegalStateException("图片页缺少素材绑定: " + title);
                String id = ids.get(0);
                Map<String, Object> asset = assetById.get(id);
                if (asset == null) throw new IllegalStateException("图片素材不存在: " + id);
                Path path = Path.of(s(asset.get("file_path")));
                if (!Files.isRegularFile(path)) throw new IllegalStateException("图片文件不存在: " + path);
                int width = i(asset.get("width"));
                int height = i(asset.get("height"));
                double ratio = width > 0 && height > 0 ? (double) width / height : 16d / 9;
                bindings.add(new PptAssetMapperV1.AssetBinding(
                        id, path.toAbsolutePath().normalize().toString(), "SCREENSHOT", width, height, ratio,
                        ratio >= 1.2 ? "LANDSCAPE" : ratio <= .83 ? "PORTRAIT" : "SQUARE",
                        false, false, 100, "PERSISTED_ASSET_ID"));
                refs = new PptContentPlannerV2.SourceRefs(
                        s(slide.get("chapter_title")), List.of(), id, List.of(), List.of());
            }
            var page = new PptOutlineValidatorV1.FullSlideNode(
                    pageNumber++, pageType, "CONTENT", title, purpose, "", points, description,
                    s(slide.get("chapter_title")), null, null, List.of(),
                    "IMAGE".equals(pageType) ? "FIGURE" : contentType(title),
                    "IMAGE".equals(pageType) ? "PROOF" : "", refs,
                    "IMAGE".equals(pageType), List.of(), 1);
            out.add(mapped(page, bindings));
        }
        out.add(mapped(fixed(pageNumber, "THANKS", "谢谢大家", payload, List.of()), List.of()));
        return new PptAssetMapperV1.MappingResult(
                true, false, "ASSET_MAPPED_TREE", out.size(), out, List.of());
    }

    private PptOutlineValidatorV1.FullSlideNode fixed(
            int number, String type, String title, PptOutlineValidatorV1.FixedPayload payload,
            List<PptOutlineValidatorV1.AgendaItem> agenda) {
        String contentType = "AGENDA".equals(type) ? "KEY_POINTS" : "NARRATIVE";
        return new PptOutlineValidatorV1.FullSlideNode(
                number, type, type, title, "", "", List.of(), "", "", null, payload, agenda,
                contentType, "", null, false, List.of(), 1);
    }

    private PptAssetMapperV1.MappedSlideNode mapped(
            PptOutlineValidatorV1.FullSlideNode page, List<PptAssetMapperV1.AssetBinding> assets) {
        return new PptAssetMapperV1.MappedSlideNode(page, assets);
    }

    private String required(Map<String, Object> values, String key) {
        String value = s(values.get(key));
        if (value.isBlank()) throw new IllegalStateException("PPT封面信息缺失: " + key);
        return value;
    }

    private String purpose(String title, String type) {
        if ("IMAGE".equals(type)) return "RESULT";
        if (title.contains("测试")) return "TEST";
        if (title.contains("总结") || title.contains("展望")) return "SUMMARY";
        if (title.contains("设计") || title.contains("架构") || title.contains("数据库")) return "DESIGN";
        return "IMPLEMENTATION";
    }

    private String contentType(String title) {
        if (title.matches(".*(流程|路径|步骤|进度|计划|路线).*")) return "PROCESS";
        if (title.matches(".*(对比|比较|差异|现状|问题|文献).*")) return "COMPARISON";
        if (title.matches(".*(架构|框架|体系|结构|模块).*")) return "ARCHITECTURE";
        if (title.matches(".*(指标|数据|趋势|比例|统计).*")) return "METRICS";
        return "KEY_POINTS";
    }

    private List<String> list(Object value) {
        try {
            Object decoded = value instanceof String text ? mapper.readValue(text, Object.class) : value;
            if (decoded instanceof List<?> items) {
                return items.stream().map(this::s).filter(item -> !item.isBlank()).toList();
            }
        } catch (Exception ignored) {
        }
        return List.of();
    }

    private String s(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    private int i(Object value) {
        try {
            return value instanceof Number number ? number.intValue() : Integer.parseInt(s(value));
        } catch (Exception exception) {
            return 0;
        }
    }
}
