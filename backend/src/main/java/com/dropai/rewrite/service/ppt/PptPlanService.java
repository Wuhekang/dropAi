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
    private final PptContentPlanner contentPlanner;
    private final PptContentPlannerV2InputAdapter inputAdapter;private final PptContentPlannerV2 plannerV2;private final PptContentSanitizerV1 sanitizer;private final PptOutlinePlannerV1 outlinePlanner;private final PptOutlineValidatorV1 outlineValidator;

    public PptPlanService(JdbcTemplate jdbc, ObjectMapper mapper, PptTextValidator validator, PptContentPlanner contentPlanner,PptContentPlannerV2InputAdapter inputAdapter,PptContentPlannerV2 plannerV2,PptContentSanitizerV1 sanitizer,PptOutlinePlannerV1 outlinePlanner,PptOutlineValidatorV1 outlineValidator) {
        this.jdbc = jdbc;
        this.mapper = mapper;
        this.validator = validator;
        this.contentPlanner = contentPlanner;
        this.inputAdapter=inputAdapter;this.plannerV2=plannerV2;this.sanitizer=sanitizer;this.outlinePlanner=outlinePlanner;this.outlineValidator=outlineValidator;
    }

    @Transactional
    public Map<String, Object> create(String projectId) {
        Long userId = AuthContext.requireUserId();
        Map<String, Object> project = requireProject(projectId, userId);
        if(plannerV2!=null)return createV2(projectId,userId,project);
        List<Map<String, Object>> sections = outline(projectId);
        if (sections.size() < 4) throw new IllegalStateException("请先确认至少4项目录");

        String thesisTitle=string(project.get("topic"));
        List<String> blocks = contentBlocks(stringList(readJson(string(project.get("analysis_json"))).get("blocks")),thesisTitle);
        List<Map<String, Object>> assets = jdbc.queryForList(
                "SELECT id,source_page,source_position,caption FROM ppt_asset WHERE project_id=? ORDER BY source_page,source_position,id", projectId);
        jdbc.update("DELETE FROM ppt_slide WHERE project_id=?", projectId);

        int order = 0;
        for (Map<String, Object> section : sections) {
            String sectionId = string(section.get("id"));
            String chapter = string(section.get("title"));
            int count = Math.max(1, integer(section.get("target_slides"), 2));
            List<PptContentPlanner.PagePlan> textPages=contentPlanner.planTextPages(thesisTitle,chapter,string(section.get("description")),blocks,count);
            for (PptContentPlanner.PagePlan page : textPages) {
                contentPlanner.requireValue(page);
                List<String> boxes = new ArrayList<>(page.keyPoints());boxes.add(page.description());
                List<String> assetIds = new ArrayList<>();
                String templateType = "content";
                String layoutType = "KEYWORDS";
                PptTextValidator.ValidationResult checked = validator.validateSlideTextLimits(page.title(), boxes);
                jdbc.update("INSERT INTO ppt_slide(id,project_id,section_id,slide_order,slide_type,title,body_boxes_json,asset_ids_json,speaker_notes,layout_type,validation_status,chapter_title,content_summary,template_type) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
                        UUID.randomUUID().toString(), projectId, sectionId, ++order, "CONTENT", checked.title(), json(checked.bodyBoxes()),
                        json(assetIds), page.sourceText(), layoutType, checked.status(), chapter, page.description(), templateType);
            }
            int sectionIndex=sections.indexOf(section);
            for(Map<String,Object> asset:assetsForSection(assets,sectionIndex,sections.size(),sourceChapterCount(blocks))){
                String caption=text(asset,"caption",chapter+"相关图示");PptContentPlanner.PagePlan imagePage=contentPlanner.planImagePage(chapter,caption);contentPlanner.requireValue(imagePage);
                PptTextValidator.ValidationResult checked=validator.validateSlideTextLimits(validator.compact(imagePage.title(),24),List.of(validator.compact(imagePage.description(),70)));
                jdbc.update("INSERT INTO ppt_slide(id,project_id,section_id,slide_order,slide_type,title,body_boxes_json,asset_ids_json,speaker_notes,layout_type,validation_status,chapter_title,content_summary,template_type) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
                        UUID.randomUUID().toString(),projectId,sectionId,++order,"IMAGE",checked.title(),json(checked.bodyBoxes()),json(List.of(string(asset.get("id")))),caption,"IMAGE_TEXT",checked.status(),chapter,imagePage.description(),"image");
            }
        }
        jdbc.update("UPDATE ppt_project SET status='PLANNED',current_stage='PPT方案待确认',progress=55,updated_at=? WHERE id=? AND user_id=?",
                LocalDateTime.now(), projectId, userId);
        return detail(projectId, userId);
    }

    private Map<String,Object> createV2(String projectId,Long userId,Map<String,Object> project){Map<String,Object> analysis=readJson(string(project.get("analysis_json")));List<Map<String,Object>> storedAssets=jdbc.queryForList("SELECT * FROM ppt_asset WHERE project_id=? ORDER BY source_page,source_position,id",projectId);List<PptDocumentParser.Asset> parsedAssets=new ArrayList<>();for(var a:storedAssets){String path=string(a.get("file_path"));if(!path.isBlank())parsedAssets.add(new PptDocumentParser.Asset(java.nio.file.Path.of(path),integer(a.get("source_page"),0),string(a.get("source_position")),string(a.get("caption")),integer(a.get("width"),0),integer(a.get("height"),0)));}List<String> headings=stringList(analysis.get("headings")),blocks=stringList(analysis.get("blocks"));var document=new PptDocumentParser.ParsedDocument(string(analysis.get("documentTitle")),headings,blocks,parsedAssets,integer(analysis.get("tableCount"),0),integer(analysis.get("characterCount"),0),integer(analysis.get("imageCount"),parsedAssets.size()),integer(analysis.get("filteredAssetCount"),0));var input=inputAdapter.fromParsedDocument(document,"computer");Map<String,String> metadata=new LinkedHashMap<>(input.metadata());put(metadata,"title",project.get("topic"));put(metadata,"englishTitle",project.get("english_topic"));put(metadata,"presenter",project.get("presenter"));put(metadata,"major",project.get("major"));put(metadata,"advisor",project.get("advisor"));put(metadata,"studentNumber",project.get("student_number"));input=new PptContentPlannerV2.PlannerInput(metadata,input.chapters(),input.assets(),input.tables(),input.majorType());var planned=sanitizer.sanitize(plannerV2.plan(input));List<PptContentPlannerV2.CandidatePage> candidates=planned.chapters().stream().flatMap(c->c.candidatePages().stream()).toList();int max=Math.max(8,integer(project.get("target_slide_count"),16)-3);var outline=outlinePlanner.plan(new PptOutlinePlannerV1.OutlineRequest(candidates,max));var validated=outlineValidator.validate(new PptOutlineValidatorV1.ValidationRequest(metadata,outline));outlineValidator.requireValid(validated);Map<String,String> assetIdByPath=new LinkedHashMap<>();for(var a:storedAssets)assetIdByPath.put(java.nio.file.Path.of(string(a.get("file_path"))).toAbsolutePath().normalize().toString(),string(a.get("id")));Map<String,String> figurePath=new LinkedHashMap<>();for(var a:input.assets())figurePath.put(string(a.get("id")),java.nio.file.Path.of(string(a.get("path"))).toAbsolutePath().normalize().toString());List<Map<String,Object>> sections=outline(projectId);jdbc.update("DELETE FROM ppt_slide WHERE project_id=?",projectId);int order=0,sectionCursor=0;for(var page:validated.slideTree()){if(List.of("COVER","AGENDA","THANKS").contains(page.pageType()))continue;String sectionId=sections.isEmpty()?null:string(sections.get(Math.min(sectionCursor,sections.size()-1)).get("id"));if(order>0&&order%Math.max(1,max/Math.max(1,sections.size()))==0)sectionCursor++;List<String> boxes=new ArrayList<>(page.keyPoints());if(!page.description().isBlank())boxes.add(page.description());List<String> assetIds=new ArrayList<>();if("IMAGE".equals(page.pageType())&&page.sourceRefs()!=null){String path=figurePath.get(page.sourceRefs().figureId());String assetId=assetIdByPath.get(path);if(assetId!=null)assetIds.add(assetId);}jdbc.update("INSERT INTO ppt_slide(id,project_id,section_id,slide_order,slide_type,title,body_boxes_json,asset_ids_json,speaker_notes,layout_type,validation_status,chapter_title,content_summary,template_type) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?)",UUID.randomUUID().toString(),projectId,sectionId,++order,page.pageType(),page.title(),json(boxes),json(assetIds),"",page.pageType(),"VALID",page.sourceChapter(),page.description(),page.pageType().toLowerCase());}jdbc.update("DELETE FROM ppt_page_task WHERE project_id=?",projectId);for(Map<String,Object> slide:jdbc.queryForList("SELECT id FROM ppt_slide WHERE project_id=? ORDER BY slide_order",projectId))jdbc.update("INSERT INTO ppt_page_task(id,project_id,slide_plan_id,status,progress,retry_count) VALUES(?,?,?,'WAITING',0,0)",UUID.randomUUID().toString(),projectId,slide.get("id"));jdbc.update("UPDATE ppt_project SET status='PLANNED',current_stage='PPT方案待确认',progress=55,updated_at=? WHERE id=? AND user_id=?",LocalDateTime.now(),projectId,userId);return detail(projectId,userId);}
    private void put(Map<String,String> target,String key,Object value){String text=string(value);if(!text.isBlank())target.put(key,text);}

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

    private List<String> contentBlocks(List<String> blocks,String thesisTitle) {
        List<String> out = new ArrayList<>();
        for (String block : blocks) {
            String clean = PptDocumentParser.clean(block);
            if (clean.length() < 8 || isFrontMatter(clean) || !contentPlanner.isContent(clean,thesisTitle)) continue;
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
    private List<Map<String,Object>> assetsForSection(List<Map<String,Object>> assets,int sectionIndex,int sectionCount,int sourceChapterCount){List<Map<String,Object>> out=new ArrayList<>();int maxChapter=Math.max(1,sourceChapterCount);for(Map<String,Object> asset:assets)maxChapter=Math.max(maxChapter,assetChapter(asset,1));for(Map<String,Object> asset:assets){int chapter=assetChapter(asset,sectionIndex+1);int mapped;if(chapter<=1||sectionCount<=1)mapped=0;else if(maxChapter<=sectionCount)mapped=Math.min(sectionCount-1,chapter-1);else mapped=1+(int)Math.floor((chapter-2.0)*(sectionCount-1)/Math.max(1,maxChapter-1));mapped=Math.min(sectionCount-1,Math.max(0,mapped));if(mapped==sectionIndex)out.add(asset);}return out;}
    private int assetChapter(Map<String,Object> asset,int fallback){var m=java.util.regex.Pattern.compile("chapter-(\\d+)-").matcher(string(asset.get("source_position")));return m.find()?Integer.parseInt(m.group(1)):fallback;}
    private int sourceChapterCount(List<String> blocks){int max=1;for(String block:blocks){var m=java.util.regex.Pattern.compile("(?m)^(?:第)?(\\d+)章|^(\\d+)(?:[.．]\\d+)+").matcher(PptDocumentParser.clean(block));if(m.find()){String n=m.group(1)!=null?m.group(1):m.group(2);max=Math.max(max,Integer.parseInt(n));}String v=PptDocumentParser.clean(block);for(int i=1;i<=10;i++)if(v.startsWith("第"+List.of("一","二","三","四","五","六","七","八","九","十").get(i-1)+"章"))max=Math.max(max,i);}return max;}
    private String imageTitle(String caption,String chapter){String clean=PptDocumentParser.clean(caption);var m=java.util.regex.Pattern.compile("(?:图|Figure|Fig\\.)\\s*\\d+(?:[-.．]\\d+)?\\s*(.*)",java.util.regex.Pattern.CASE_INSENSITIVE).matcher(clean);if(m.find()&&!m.group(1).isBlank())return m.group(1);return chapter+"图示";}
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
