package com.dropai.rewrite.service.ppt.rendering.production.v1;

import com.dropai.rewrite.service.ppt.PptAssetMapperV1;
import com.dropai.rewrite.service.ppt.PptOutlineValidatorV1;
import com.dropai.rewrite.service.ppt.rendering.compiler.v1.RenderingAssetBundle;
import com.dropai.rewrite.service.ppt.rendering.compiler.v1.ValidatedPresentationTree;
import com.dropai.rewrite.service.ppt.rendering.renderer.v1.AssetBinaryResolver;
import com.dropai.rewrite.service.ppt.rendering.renderer.v1.VerifiedAssetBytes;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.*;

/** Lossless adapter from the already validated V1 outline and exact mapper bindings. */
public final class ProductionPresentationAdapter {
    private final ObjectMapper mapper;

    ProductionPresentationAdapter(ObjectMapper mapper) { this.mapper = mapper; }

    Adapted adapt(ProductionRenderPlanRequest request, PptAssetMapperV1.MappingResult mapped) {
        requireMetadata(request.metadata(), "title", "englishTitle", "presenter", "major", "advisor", "studentNumber",
                "institution", "date");
        requireValidatedTree(request);
        requireCoverMetadataMatches(request);
        if (!mapped.assetPlanReady() || mapped.issues().stream().anyMatch(i -> "ERROR".equals(i.severity()))) {
            throw new IllegalStateException("PPT Rendering V1 asset mapping failed: " + mapped.issues());
        }
        ObjectNode tree = JsonNodeFactory.instance.objectNode();
        tree.put("treeType", "FULL_PRESENTATION_TREE");
        tree.put("fixtureSchemaVersion", "validated-presentation-tree.v1");
        ObjectNode metadata = tree.putObject("metadata");
        request.metadata().entrySet().stream().sorted(Map.Entry.comparingByKey())
                .forEach(e -> { if (!e.getValue().isBlank()) metadata.put(e.getKey(), e.getValue()); });
        ArrayNode agenda = tree.putArray("agendaSections");
        Map<String, List<String>> sectionPages = new LinkedHashMap<>();
        Map<String, String> sectionTitles = new LinkedHashMap<>();
        Map<String, String> displayTitles = new LinkedHashMap<>();
        mapped.slideTree().stream().filter(s -> "AGENDA".equals(s.page().pageType())).findFirst()
                .ifPresent(s -> s.page().agendaItems().forEach(i -> displayTitles.put(i.section(), i.title())));
        for (var slide : mapped.slideTree()) {
            var page = slide.page();
            if (!List.of("COVER", "AGENDA", "THANKS").contains(page.pageType())) {
                String sourceSection=required(page.section(), "page "+page.pageNumber()+" section");
                String sectionId = portable(sourceSection);
                String sectionTitle=displayTitles.get(sourceSection);
                if(sectionTitle==null||sectionTitle.isBlank())
                    throw new IllegalStateException("Page "+page.pageNumber()+" section is absent from the validated agenda: "+sourceSection);
                sectionTitles.putIfAbsent(sectionId, sectionTitle);
                sectionPages.computeIfAbsent(sectionId, ignored -> new ArrayList<>()).add(pageId(page.pageNumber()));
            }
        }
        sectionPages.forEach((id, ids) -> {
            ObjectNode node = agenda.addObject(); node.put("sectionId", id);
            node.put("title", sectionTitles.get(id)); ArrayNode pages = node.putArray("sourcePageIds");
            ids.forEach(pages::add);
        });

        ArrayNode assets = JsonNodeFactory.instance.arrayNode();
        Map<String, ObjectNode> tables = new LinkedHashMap<>();
        Map<String, VerifiedAssetBytes> bytesById = new LinkedHashMap<>();
        ArrayNode pages = tree.putArray("pages");
        for (var mappedPage : mapped.slideTree()) {
            var page = mappedPage.page();
            ObjectNode node = pages.addObject();
            node.put("index", page.pageNumber()); node.put("sourcePageId", pageId(page.pageNumber()));
            node.put("pageType", page.pageType()); node.put("section", section(page));
            node.put("title", required(page.title(), "page title"));
            node.put("pagePurpose", purpose(page));
            node.put("answerQuestion", answerQuestion(page));
            ArrayNode points = node.putArray("keyPoints");
            List<String> keys = page.keyPoints() == null ? List.of() : page.keyPoints();
            if (keys.isEmpty()) keys = fixedKeyPoints(page, request.metadata());
            keys.forEach(points::add);
            node.put("description", description(page));
            node.put("sourceChapter", sourceChapter(page));
            node.put("contentType", contentType(page));
            ArrayNode pageAssets = node.putArray("assets");
            for (var binding : mappedPage.assets()) {
                if ("TABLE_DATA".equals(binding.type())) continue;
                if (binding.placeholder()) throw new IllegalStateException("Placeholder asset forbidden: " + binding.assetId());
                Path source = Path.of(binding.source()).toAbsolutePath().normalize();
                byte[] raw;
                try { raw = Files.readAllBytes(source); }
                catch (Exception e) { throw new IllegalStateException("Cannot read PPT asset " + source, e); }
                String mime = mime(raw, source); String extension = "image/png".equals(mime) ? "png" : "jpg";
                String bundlePath = "assets/" + portable(binding.assetId()) + "." + extension;
                String hash = sha(raw);
                ObjectNode item = assets.addObject(); item.put("assetId", binding.assetId());
                item.put("assetKind", assetKind(binding.type())); item.put("bundlePath", bundlePath);
                item.put("widthPx", binding.width()); item.put("heightPx", binding.height());
                item.put("imageRole", imageRole(page.imageRole())); item.put("mandatory", page.mandatoryAsset());
                item.put("mimeType", mime); item.put("sha256", hash);
                ObjectNode ref = pageAssets.addObject(); ref.put("assetId", binding.assetId());
                ref.put("assetKind", assetKind(binding.type())); ref.put("imageRole", imageRole(page.imageRole()));
                ref.put("mandatory", page.mandatoryAsset());
                bytesById.put(binding.assetId(), new VerifiedAssetBytes(binding.assetId(), bundlePath, hash, mime, raw));
            }
            ArrayNode pageTables = node.putArray("tables");
            if ("TABLE".equals(page.pageType())) {
                if (page.tableSummary() == null || page.tableSummary().isEmpty())
                    throw new IllegalStateException("TABLE page lacks structured tableSummary: " + page.title());
                String tableId = "table-page-" + String.format(Locale.ROOT, "%03d", page.pageNumber());
                String kind = "DATABASE".equals(page.pagePurpose()) ? "ENTITY_PURPOSE" : "GENERIC";
                ObjectNode table = JsonNodeFactory.instance.objectNode(); table.put("tableId", tableId);
                table.put("title", page.title()); table.put("tableKind", kind);
                ArrayNode columns = table.putArray("columns");
                int count = page.tableSummary().stream().mapToInt(List::size).max().orElse(2);
                for (int i=0;i<count;i++) columns.add(i==0 ? ("ENTITY_PURPOSE".equals(kind)?"业务表":"项目") : (i==1?"说明":"内容"+(i+1)));
                ArrayNode rows = table.putArray("rows");
                for (List<String> row : page.tableSummary()) { ArrayNode r=rows.addArray(); for(int i=0;i<count;i++) r.add(i<row.size()?row.get(i):""); }
                tables.put(tableId, table);
                ObjectNode ref=pageTables.addObject(); ref.put("tableId",tableId); ref.put("tableKind",kind);
            }
        }
        String presentationId = presentationIdForProject(request.projectId());
        tree.put("fixtureId", presentationId);
        String sourceHash = sha(canonical(tree));
        ValidatedPresentationTree validated = new ValidatedPresentationTree(tree, presentationId, sourceHash);
        RenderingAssetBundle bundle = new RenderingAssetBundle(assets, tables);
        AssetBinaryResolver resolver = (assetId, bundlePath, expectedSha) -> {
            VerifiedAssetBytes found = bytesById.get(assetId);
            return found != null && found.bundlePath().equals(bundlePath) && found.sha256().equals(expectedSha) ? found : null;
        };
        return new Adapted(validated, bundle, resolver);
    }

    public static String presentationIdForProject(String projectId) {
        Objects.requireNonNull(projectId, "projectId");
        if (projectId.isBlank()) {
            throw new IllegalArgumentException("projectId must not be blank");
        }
        return "presentation-" + portable(projectId);
    }

    public static boolean belongsToProject(String presentationId, String projectId) {
        return Objects.equals(presentationId, projectId)
                || Objects.equals(presentationId, presentationIdForProject(projectId));
    }

    private byte[] canonical(JsonNode value) { try { return mapper.writer().writeValueAsBytes(sort(value)); } catch(Exception e){throw new IllegalStateException(e);} }
    private JsonNode sort(JsonNode n){ if(n.isObject()){ObjectNode o=JsonNodeFactory.instance.objectNode();List<String>k=new ArrayList<>();n.fieldNames().forEachRemaining(k::add);Collections.sort(k);k.forEach(x->o.set(x,sort(n.get(x))));return o;}if(n.isArray()){ArrayNode a=JsonNodeFactory.instance.arrayNode();n.forEach(x->a.add(sort(x)));return a;}return n; }
    private static void requireMetadata(Map<String,String> m,String... keys){List<String>missing=Arrays.stream(keys).filter(k->m.getOrDefault(k,"").isBlank()).toList();if(!missing.isEmpty())throw new IllegalStateException("PPT Rendering V1 metadata missing: "+missing);}
    private static void requireValidatedTree(ProductionRenderPlanRequest request){
        var tree=request.validatedTree();
        if(tree==null||!tree.valid()||!"FULL_PRESENTATION_TREE".equals(tree.treeType())||tree.issues()==null||!tree.issues().isEmpty())
            throw new IllegalStateException("PPT Rendering V1 requires an issue-free validated FULL_PRESENTATION_TREE");
    }
    private static void requireCoverMetadataMatches(ProductionRenderPlanRequest request){
        var covers=request.validatedTree().slideTree().stream().filter(page->"COVER".equals(page.pageType())).toList();
        if(covers.size()!=1||covers.get(0).payload()==null)
            throw new IllegalStateException("Validated presentation tree must contain one COVER payload");
        var payload=covers.get(0).payload();
        Map<String,String> values=new LinkedHashMap<>();
        values.put("title",payload.title());values.put("englishTitle",payload.englishTitle());
        values.put("presenter",payload.presenter());values.put("major",payload.major());
        values.put("advisor",payload.advisor());values.put("studentNumber",payload.studentNumber());
        values.forEach((key,value)->{
            if(!Objects.equals(value,request.metadata().get(key)))
                throw new IllegalStateException("Validated COVER metadata mismatch: "+key);
        });
    }
    private static String pageId(int n){return "page-"+String.format(Locale.ROOT,"%03d",n);} private static String portable(String s){String v=s.replaceAll("[^A-Za-z0-9._-]+","-").replaceAll("^-+|-+$","");return v.isBlank()?"section":v;}
    private static String section(PptOutlineValidatorV1.FullSlideNode p){return switch(p.pageType()){case"COVER","AGENDA"->"opening";case"THANKS"->"closing";default->portable(required(p.section(),"page "+p.pageNumber()+" section"));};}
    private static String purpose(PptOutlineValidatorV1.FullSlideNode p){if(!p.pagePurpose().isBlank())return p.pagePurpose();return switch(p.pageType()){case"COVER"->"BACKGROUND";case"AGENDA"->"METHOD";case"THANKS"->"SUMMARY";default->throw missing(p,"pagePurpose");};}
    private static String answerQuestion(PptOutlineValidatorV1.FullSlideNode p){if(!p.answerQuestion().isBlank())return p.answerQuestion();return switch(p.pageType()){case"COVER"->"本次答辩围绕什么课题展开？";case"AGENDA"->"本次答辩如何展开？";case"THANKS"->"本次答辩如何结束？";default->throw missing(p,"answerQuestion");};}
    private static List<String> fixedKeyPoints(PptOutlineValidatorV1.FullSlideNode p,Map<String,String>m){return switch(p.pageType()){case"COVER"->List.of(m.get("presenter"),m.get("major"),m.get("advisor"));case"AGENDA"->{if(p.agendaItems()==null||p.agendaItems().isEmpty())throw missing(p,"agendaItems");yield p.agendaItems().stream().map(PptOutlineValidatorV1.AgendaItem::title).toList();}case"THANKS"->List.of("感谢各位老师聆听","敬请批评指正");default->throw missing(p,"keyPoints");};}
    private static String description(PptOutlineValidatorV1.FullSlideNode p){if(!p.description().isBlank())return p.description();return switch(p.pageType()){case"COVER"->"本次答辩介绍课题背景、方案设计、实现成果与测试结论。";case"AGENDA"->"答辩按照背景、设计、实现、验证和总结的逻辑展开。";case"THANKS"->"答辩汇报结束，感谢各位老师的聆听与指导。";default->throw missing(p,"description");};}
    private static String sourceChapter(PptOutlineValidatorV1.FullSlideNode p){if(!p.sourceChapter().isBlank())return p.sourceChapter();return switch(p.pageType()){case"COVER"->"封面";case"AGENDA"->"目录";case"THANKS"->"结束页";default->throw missing(p,"sourceChapter");};}
    private static String contentType(PptOutlineValidatorV1.FullSlideNode p){return switch(p.pageType()){case"COVER","THANKS"->"NARRATIVE";case"IMAGE"->"FIGURE";case"TABLE"->"TABULAR";default->"KEY_POINTS";};}
    private static String imageRole(String r){if(!List.of("INFORMATION","PROOF","EFFECT").contains(r))throw new IllegalStateException("Unknown imageRole: "+r);return r;}
    private static String assetKind(String t){return switch(t){case"SCREENSHOT"->"SCREENSHOT";case"EFFECT_IMAGE"->"DESIGN_RENDER";case"DIAGRAM"->"DIAGRAM";default->throw new IllegalStateException("Unknown mapped asset type: "+t);};}
    private static String mime(byte[] b,Path p){if(b.length>=8&&b[0]==(byte)0x89&&b[1]==0x50&&b[2]==0x4e&&b[3]==0x47)return"image/png";if(b.length>=3&&b[0]==(byte)0xff&&b[1]==(byte)0xd8&&b[2]==(byte)0xff)return"image/jpeg";throw new IllegalStateException("Unsupported or mismatched PPT image: "+p);}
    private static String sha(byte[] b){try{return"sha256:"+HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(b));}catch(Exception e){throw new IllegalStateException(e);}}
    private static String required(String v,String f){if(v==null||v.isBlank())throw new IllegalStateException(f+" is missing");return v;}
    private static IllegalStateException missing(PptOutlineValidatorV1.FullSlideNode p,String field){return new IllegalStateException("Page "+p.pageNumber()+" "+field+" is missing after outline validation");}
    record Adapted(ValidatedPresentationTree tree, RenderingAssetBundle assets, AssetBinaryResolver resolver){}
}
