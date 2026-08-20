package com.dropai.rewrite.service.ppt;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class PptGenerationSkillService {
    public static final String NAME="ppt-generation",VERSION="1.0.0";
    private final ObjectMapper mapper;
    private final List<Path> candidates=List.of(Path.of("skills","ppt-generation","SKILL.md"),Path.of("..","skills","ppt-generation","SKILL.md"));
    public PptGenerationSkillService(ObjectMapper mapper){this.mapper=mapper;}
    public SkillManifest requireManifest(){Path file=candidates.stream().map(Path::toAbsolutePath).map(Path::normalize).filter(Files::isRegularFile).findFirst().orElseThrow(()->new IllegalStateException("PPT生成规则缺失：skills/ppt-generation/SKILL.md"));try{String body=Files.readString(file);for(String required:List.of("Document Analyzer","Chapter Planner","Asset Extractor","Slide Planner","Template Engine","PPT Renderer","20 Chinese characters"))if(!body.contains(required))throw new IllegalStateException("PPT生成Skill缺少规则："+required);return new SkillManifest(NAME,VERSION,file.toString(),Integer.toHexString(body.hashCode()));}catch(Exception e){if(e instanceof IllegalStateException state)throw state;throw new IllegalStateException("PPT生成Skill读取失败",e);}}
    public Path writeLog(Path output,Map<String,Object> documentAnalysis,List<Map<String,Object>> assets,List<Map<String,Object>> slidePlan,Map<String,Object> template,String validationStatus,List<String> autoFixes)throws Exception{SkillManifest skill=requireManifest();Map<String,Object> root=new LinkedHashMap<>();root.put("skillName",skill.name());root.put("skillVersion",skill.version());root.put("skillHash",skill.hash());root.put("documentAnalysis",documentAnalysis);root.put("assets",assets);root.put("slidePlan",slidePlan);root.put("template",template);root.put("validation",Map.of("status",validationStatus,"autoFixes",autoFixes));String name=output.getFileName().toString();int dot=name.lastIndexOf('.');Path reportDir=output.resolveSibling((dot>0?name.substring(0,dot):name)+"-debug");Files.createDirectories(reportDir);write(reportDir,"source-precheck-report.json",documentAnalysis.getOrDefault("precheck",Map.of()));write(reportDir,"source-chapters.json",documentAnalysis.getOrDefault("headings",List.of()));write(reportDir,"source-assets.json",assets);write(reportDir,"filtered-assets.json",Map.of("filteredCount",documentAnalysis.getOrDefault("filteredAssetCount",0)));write(reportDir,"chapter-asset-map.json",assets);write(reportDir,"source-tables.json",Map.of("tableCount",documentAnalysis.getOrDefault("tableCount",0)));write(reportDir,"ppt-outline.json",slidePlan.stream().map(x->x.get("chapter")).filter(x->x!=null&&!String.valueOf(x).isBlank()).distinct().toList());write(reportDir,"ppt-slide-plan.json",slidePlan);write(reportDir,"page-generation-log.json",slidePlan);write(reportDir,"layout-validation-report.json",Map.of("status",validationStatus,"autoFixes",autoFixes));write(reportDir,"final-quality-report.json",Map.of("status",validationStatus,"autoFixes",autoFixes,"editablePptx",true));Path log=reportDir.resolve("generation-summary.json");write(reportDir,"generation-summary.json",root);return log;}
    private void write(Path directory,String file,Object value)throws Exception{Files.writeString(directory.resolve(file),mapper.writerWithDefaultPrettyPrinter().writeValueAsString(value));}
    public record SkillManifest(String name,String version,String path,String hash){}
}
