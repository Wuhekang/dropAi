package com.dropai.rewrite;

import com.dropai.rewrite.service.ppt.PptGenerationSkillService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class PptGenerationSkillServiceTest {
    @Test void loadsRequiredSkillAndWritesStableGenerationLog()throws Exception{
        PptGenerationSkillService service=new PptGenerationSkillService(new ObjectMapper());var manifest=service.requireManifest();assertEquals("ppt-generation",manifest.name());assertEquals("1.0.0",manifest.version());Path dir=Path.of("target","ppt-skill-test");Files.createDirectories(dir);Path output=dir.resolve("demo.pptx");Files.write(output,new byte[]{1});Path log=service.writeLog(output,Map.of("title","测试"),List.of(Map.of("id","a1")),List.of(Map.of("outputPage",1,"pageType","cover")),Map.of("name","system"),"PASSED",List.of());assertTrue(Files.isRegularFile(log));String json=Files.readString(log);assertTrue(json.contains("ppt-generation"));assertTrue(json.contains("slidePlan"));assertTrue(json.contains("validation"));
    }

    @Test void writesComputerMechanicalAndEnvironmentWorkflowLogs()throws Exception{
        PptGenerationSkillService service=new PptGenerationSkillService(new ObjectMapper());Path dir=Path.of("../qa/ppt-generation-skill");Files.createDirectories(dir);for(String major:List.of("计算机科学与技术","机械设计制造及其自动化","环境设计")){String safe=major.replaceAll("[^\\p{L}\\p{N}]","_");Path output=dir.resolve(safe+"-skill-test.pptx");Files.write(output,new byte[]{1});List<Map<String,Object>> assets=List.of(Map.of("id","asset-1","chapter","第2章","page",12,"type",major.contains("机械")?"3D_MODEL":major.contains("环境")?"SITE_ANALYSIS":"ARCHITECTURE","description","来源文档图片","path","source/image-1.png"));List<Map<String,Object>> plan=List.of(Map.of("outputPage",1,"pageType","cover","chapter","","assetIds",List.of()),Map.of("outputPage",2,"pageType","catalog","chapter","","assetIds",List.of()),Map.of("outputPage",3,"pageType","section","chapter","第1章 绪论","assetIds",List.of()),Map.of("outputPage",4,"pageType","image_content","chapter","第2章","assetIds",List.of("asset-1")),Map.of("outputPage",5,"pageType","future","chapter","","assetIds",List.of()),Map.of("outputPage",6,"pageType","thanks","chapter","","assetIds",List.of()));Path log=service.writeLog(output,Map.of("title",major+"毕业设计","major",major,"headings",List.of("第1章 绪论","第2章 设计与实现"),"excludedHeadings",List.of("参考文献","致谢")),assets,plan,Map.of("name","系统模板","priorityReason","专业匹配"),"PASSED",List.of());assertTrue(Files.readString(log).contains(major));}
    }
}
