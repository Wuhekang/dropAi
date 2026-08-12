package com.dropai.rewrite.service.ppt;

import com.dropai.rewrite.auth.AuthContext;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class PptPlanService {
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;
    private final PptTextValidator validator;

    public PptPlanService(JdbcTemplate jdbc, ObjectMapper mapper, PptTextValidator validator) {
        this.jdbc = jdbc;
        this.mapper = mapper;
        this.validator = validator;
    }

    @Transactional
    public Map<String, Object> create(String projectId) {
        Long userId = AuthContext.requireUserId();
        Map<String, Object> project = requireProject(projectId, userId);
        List<Map<String, Object>> sections = outline(projectId);
        if (sections.size() < 4) throw new IllegalStateException("请先确认至少4项目录");

        List<String> blocks = contentBlocks(stringList(readJson(string(project.get("analysis_json"))).get("blocks")));
        List<Map<String, Object>> assets = jdbc.queryForList(
                "SELECT id,source_page,caption FROM ppt_asset WHERE project_id=? ORDER BY source_page,id", projectId);
        jdbc.update("DELETE FROM ppt_slide WHERE project_id=?", projectId);

        int order = 0, blockIndex = 0, assetIndex = 0;
        for (Map<String, Object> section : sections) {
            String sectionId = string(section.get("id"));
            String chapter = string(section.get("title"));
            int count = Math.max(1, integer(section.get("target_slides"), 2));
            for (int i = 0; i < count; i++) {
                String source = blocks.isEmpty() ? string(section.get("description")) : blocks.get(blockIndex++ % blocks.size());
                List<String> boxes = summarize(source);
                String title = i == 0 ? chapter : validator.compact(firstPhrase(source), 24);
                List<String> assetIds = new ArrayList<>();
                if (assetIndex < assets.size()) assetIds.add(string(assets.get(assetIndex++).get("id")));
                String templateType = assetIds.isEmpty() ? "content" : "image";
                String layoutType = assetIds.isEmpty() ? "KEYWORDS" : "IMAGE_TEXT";
                PptTextValidator.ValidationResult checked = validator.validateSlideTextLimits(title, boxes);
                jdbc.update("INSERT INTO ppt_slide(id,project_id,section_id,slide_order,slide_type,title,body_boxes_json,asset_ids_json,speaker_notes,layout_type,validation_status,chapter_title,content_summary,template_type) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
                        UUID.randomUUID().toString(), projectId, sectionId, ++order, "CONTENT", checked.title(), json(checked.bodyBoxes()),
                        json(assetIds), source, layoutType, checked.status(), chapter, String.join("；", checked.bodyBoxes()), templateType);
            }
        }
        jdbc.update("UPDATE ppt_project SET status='PLANNED',current_stage='PPT方案待确认',progress=55,updated_at=? WHERE id=? AND user_id=?",
                LocalDateTime.now(), projectId, userId);
        return detail(projectId, userId);
    }

    @Transactional
    public Map<String, Object> save(String projectId, List<Map<String, Object>> pages) {
        Long userId = AuthContext.requireUserId();
        requireProject(projectId, userId);
        if (pages == null || pages.isEmpty()) throw new IllegalArgumentException("PPT方案不能为空");
        int order = 0;
        for (Map<String, Object> page : pages) {
            String id = text(page, "id", "");
            if (id.isBlank()) continue;
            PptTextValidator.ValidationResult checked = validator.validateSlideTextLimits(
                    text(page, "title", "内容概览"), stringList(page.get("bodyBoxes")));
            jdbc.update("UPDATE ppt_slide SET slide_order=?,title=?,body_boxes_json=?,asset_ids_json=?,layout_type=?,validation_status=?,chapter_title=?,content_summary=?,template_type=? WHERE id=? AND project_id=?",
                    ++order, checked.title(), json(checked.bodyBoxes()), json(stringList(page.get("assetIds"))),
                    text(page, "layoutType", "KEYWORDS"), checked.status(), text(page, "chapterTitle", ""),
                    text(page, "contentSummary", String.join("；", checked.bodyBoxes())), text(page, "templateType", "content"), id, projectId);
        }
        jdbc.update("UPDATE ppt_project SET current_stage='PPT方案已确认',updated_at=? WHERE id=? AND user_id=?", LocalDateTime.now(), projectId, userId);
        return detail(projectId, userId);
    }

    private List<String> contentBlocks(List<String> blocks) {
        List<String> out = new ArrayList<>();
        for (String block : blocks) {
            String clean = PptDocumentParser.clean(block);
            if (clean.length() < 8 || isFrontMatter(clean)) continue;
            out.add(clean);
        }
        return out;
    }

    private boolean isFrontMatter(String value) {
        String s = value.replaceAll("\\s+", "");
        return s.length() < 100 && (s.contains("综合课程设计报告") || s.startsWith("学院") || s.startsWith("专业")
                || s.startsWith("学号") || s.startsWith("学生姓名") || s.startsWith("指导教师") || s.equals("目录")
                || s.matches(".*\\d{4}年\\d{1,2}月\\d{1,2}日.*"));
    }

    private List<String> summarize(String source) {
        List<String> out = new ArrayList<>();
        for (String part : PptDocumentParser.clean(source).split("[。！？；;.!?\\n]+")) {
            part = part.trim();
            if (part.length() < 2 || isFrontMatter(part)) continue;
            out.add(validator.compact(part, 20));
            if (out.size() == 4) break;
        }
        if (out.isEmpty()) out.add("依据源文档提炼");
        return out;
    }

    private String firstPhrase(String source) { return summarize(source).get(0); }
    private Map<String, Object> requireProject(String id, Long userId) { List<Map<String,Object>> rows=jdbc.queryForList("SELECT * FROM ppt_project WHERE id=? AND user_id=?",id,userId);if(rows.isEmpty())throw new IllegalArgumentException("PPT项目不存在或无权访问");return new LinkedHashMap<>(rows.get(0)); }
    private List<Map<String,Object>> outline(String id){return jdbc.queryForList("SELECT * FROM ppt_outline WHERE project_id=? ORDER BY section_order",id);}
    private Map<String,Object> detail(String id,Long userId){Map<String,Object> p=requireProject(id,userId);p.put("outline",outline(id));p.put("slides",jdbc.queryForList("SELECT * FROM ppt_slide WHERE project_id=? ORDER BY slide_order",id));p.put("assets",jdbc.queryForList("SELECT id,source_type,source_page,caption,width,height FROM ppt_asset WHERE project_id=? ORDER BY source_page,id",id));return p;}
    private Map<String,Object> readJson(String value){try{return value==null||value.isBlank()?new LinkedHashMap<>():mapper.readValue(value,new TypeReference<>(){});}catch(Exception e){return new LinkedHashMap<>();}}
    private String json(Object value){try{return mapper.writeValueAsString(value);}catch(Exception e){throw new IllegalStateException("JSON处理失败",e);}}
    private String text(Map<String,Object> map,String key,String fallback){String value=string(map.get(key));return value.isBlank()?fallback:value;}
    private String string(Object value){return value==null?"":String.valueOf(value);}
    private int integer(Object value,int fallback){try{return value instanceof Number n?n.intValue():Integer.parseInt(string(value));}catch(Exception e){return fallback;}}
    private List<String> stringList(Object value){if(value instanceof List<?> list)return list.stream().map(this::string).filter(s->!s.isBlank()).toList();return List.of();}
}
