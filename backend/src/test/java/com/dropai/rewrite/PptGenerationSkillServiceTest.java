package com.dropai.rewrite;

import com.dropai.rewrite.service.ppt.PptGenerationSkillService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class PptGenerationSkillServiceTest {
    @Test void loadsRequiredSkillAndWritesStableGenerationLog()throws Exception{
        PptGenerationSkillService service=new PptGenerationSkillService(new ObjectMapper());var manifest=service.requireManifest();assertEquals("ppt-generation",manifest.name());assertEquals("2.0.0",manifest.version());assertEquals("classpath:/skills/ppt-generation/SKILL.md",manifest.path());assertTrue(manifest.hash().matches("[0-9a-f]{64}"));assertTrue(manifest.characterCount()>8_000);Path dir=Path.of("target","ppt-skill-test");Files.createDirectories(dir);Path output=dir.resolve("demo.pptx");Files.write(output,new byte[]{1});Path log=service.writeLog(output,Map.of("title","测试"),List.of(Map.of("id","a1")),List.of(Map.of("outputPage",1,"pageType","cover")),Map.of("name","system"),"PASSED",List.of(),Map.of("provider","kimi_ark","model","test-model","status","SUCCESS"));assertTrue(Files.isRegularFile(log));String json=Files.readString(log);assertTrue(json.contains("ppt-generation"));assertTrue(json.contains("slidePlan"));assertTrue(json.contains("validation"));assertTrue(json.contains("kimi_ark"));assertTrue(json.contains("test-model"));assertTrue(json.contains(manifest.hash()));
    }

    @Test void packagedSkillMatchesDetailedRepositoryCopyAndHasStableSha256()throws Exception{
        PptGenerationSkillService service=new PptGenerationSkillService(new ObjectMapper());var first=service.requirePromptContract();var second=service.requirePromptContract();assertEquals(first.manifest().hash(),second.manifest().hash());assertEquals(first.providerRules(),second.providerRules());assertTrue(first.providerRules().length()>2_000);assertTrue(first.providerRules().contains("metadata isolation"));assertTrue(first.providerRules().contains("唯一允许的返回格式"));String repository=Files.readString(Path.of("..","skills","ppt-generation","SKILL.md")).replace("\r\n","\n").replace('\r','\n').strip()+"\n";String expected=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(repository.getBytes(java.nio.charset.StandardCharsets.UTF_8)));assertEquals(expected,first.manifest().hash(),"仓库Skill与打包到classpath的Skill发生漂移");
    }

    @Test void writesComputerMechanicalAndEnvironmentWorkflowLogs()throws Exception{
        PptGenerationSkillService service=new PptGenerationSkillService(new ObjectMapper());Path dir=Path.of("../qa/ppt-generation-skill");Files.createDirectories(dir);for(String major:List.of("计算机科学与技术","机械设计制造及其自动化","环境设计")){String safe=major.replaceAll("[^\\p{L}\\p{N}]","_");Path output=dir.resolve(safe+"-skill-test.pptx");Files.write(output,new byte[]{1});List<Map<String,Object>> assets=List.of(Map.of("id","asset-1","chapter","第2章","page",12,"type",major.contains("机械")?"3D_MODEL":major.contains("环境")?"SITE_ANALYSIS":"ARCHITECTURE","description","来源文档图片","path","source/image-1.png"));List<Map<String,Object>> plan=List.of(Map.of("outputPage",1,"pageType","cover","chapter","","assetIds",List.of()),Map.of("outputPage",2,"pageType","catalog","chapter","","assetIds",List.of()),Map.of("outputPage",3,"pageType","section","chapter","第1章 绪论","assetIds",List.of()),Map.of("outputPage",4,"pageType","image_content","chapter","第2章","assetIds",List.of("asset-1")),Map.of("outputPage",5,"pageType","future","chapter","","assetIds",List.of()),Map.of("outputPage",6,"pageType","thanks","chapter","","assetIds",List.of()));Path log=service.writeLog(output,Map.of("title",major+"毕业设计","major",major,"headings",List.of("第1章 绪论","第2章 设计与实现"),"excludedHeadings",List.of("参考文献","致谢")),assets,plan,Map.of("name","系统模板","priorityReason","专业匹配"),"PASSED",List.of());assertTrue(Files.readString(log).contains(major));}
    }
}
