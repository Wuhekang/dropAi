package com.dropai.rewrite.service.diagram;

import com.dropai.rewrite.config.DiagramAssistantProperties;
import com.dropai.rewrite.service.diagram.DiagramIr.DiagramType;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class OllamaDiagramLiveE2ETest {
    @Test void generatesCompilesAndParsesAllSevenTypesWithLocalOllama() throws Exception{
        Assumptions.assumeTrue("1".equals(System.getenv("DROP_OLLAMA_LIVE_TEST")));
        DiagramAssistantProperties properties=new DiagramAssistantProperties();
        properties.setOllamaEndpoint("http://127.0.0.1:11434/api/chat");
        properties.setOllamaModel("dropai-diagram-ir:first-wave");
        properties.setConnectTimeout(Duration.ofSeconds(10));properties.setHardLimit(Duration.ofMinutes(3));
        ObjectMapper mapper=new ObjectMapper();SemanticIrLiteAdapter adapter=new SemanticIrLiteAdapter(mapper);
        OllamaDiagramClient client=new OllamaDiagramClient(properties,mapper,adapter);
        DiagramRuleEngine rules=new DiagramRuleEngine();DiagramDslCodec codec=new DiagramDslCodec();
        Map<DiagramType,String> prompts=new LinkedHashMap<>();
        prompts.put(DiagramType.FLOWCHART,"用户提交订单，系统判断库存，充足则创建订单，不足则提示后结束");
        prompts.put(DiagramType.ER_DIAGRAM,"患者和患者资料一对一，包含主键与外键");
        prompts.put(DiagramType.FUNCTION_MODULE,"商城系统包含用户管理、订单管理和支付管理，每个模块包含两个功能");
        prompts.put(DiagramType.ARCHITECTURE,"Web端经API网关调用订单服务，订单服务使用缓存、消息队列和数据库");
        prompts.put(DiagramType.USE_CASE,"顾客结算订单，可选使用优惠券");
        prompts.put(DiagramType.BLOCK_DIAGRAM,"传感器采集功率，处理器处理后写入历史库并输出到监控平台");
        prompts.put(DiagramType.SEQUENCE_DIAGRAM,"用户从登录页面提交账号，经认证服务查询数据库并返回结果");
        Path output=Path.of("target","ollama-live");Files.createDirectories(output);
        for(var entry:prompts.entrySet()){
            var result=client.generate(entry.getKey(),entry.getValue(),null,ignored->{});
            Files.writeString(output.resolve(entry.getKey().name().toLowerCase()+".json"),result.json(),StandardCharsets.UTF_8);
            DiagramIr ir=rules.normalize(adapter.adapt(entry.getKey(),result.json(),entry.getKey().name()));
            String dsl=codec.compile(ir);
            assertFalse(dsl.isBlank(),entry.getKey()+" produced empty DSL");
            assertEquals(entry.getKey(),codec.parse(dsl).diagramType(),entry.getKey()+" round trip failed\n"+dsl);
            Files.writeString(output.resolve(entry.getKey().name().toLowerCase()+".dsl"),dsl,StandardCharsets.UTF_8);
        }
    }
}
