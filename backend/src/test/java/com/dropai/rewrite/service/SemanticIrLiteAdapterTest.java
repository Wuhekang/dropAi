package com.dropai.rewrite.service;

import com.dropai.rewrite.service.diagram.*;
import com.dropai.rewrite.service.diagram.DiagramIr.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SemanticIrLiteAdapterTest {
    private final SemanticIrLiteAdapter adapter=new SemanticIrLiteAdapter(new ObjectMapper());
    private final DiagramDslCodec codec=new DiagramDslCodec();

    @Test void adaptsAllSevenSemanticIrTypesToCurrentDsl(){
        assertDsl(DiagramType.FLOWCHART,"流程", "{\"type\":\"FLOWCHART\",\"nodes\":[{\"kind\":\"start\",\"text\":\"开始\"},{\"kind\":\"decision\",\"text\":\"库存充足\"},{\"kind\":\"process\",\"text\":\"创建订单\"},{\"kind\":\"end\",\"text\":\"结束\"}],\"relations\":[{\"from\":\"开始\",\"to\":\"库存充足\",\"kind\":\"flow\"},{\"from\":\"库存充足\",\"to\":\"创建订单\",\"kind\":\"flow\",\"label\":\"是\"},{\"from\":\"创建订单\",\"to\":\"结束\",\"kind\":\"flow\"}]}" ,"@Flowchart");
        assertDsl(DiagramType.ER_DIAGRAM,"订单ER图","{\"type\":\"ER_DIAGRAM\",\"nodes\":[{\"kind\":\"entity\",\"text\":\"用户\",\"attributes\":[{\"name\":\"用户ID\",\"type\":\"BIGINT\",\"pk\":true}]},{\"kind\":\"entity\",\"text\":\"订单\",\"attributes\":[{\"name\":\"订单ID\",\"type\":\"BIGINT\",\"pk\":true},{\"name\":\"用户ID\",\"type\":\"BIGINT\",\"fk\":true}]}],\"relations\":[{\"from\":\"订单\",\"to\":\"用户\",\"kind\":\"relationship\",\"fromCardinality\":\"N\",\"toCardinality\":\"1\",\"fromField\":\"用户ID\",\"toField\":\"用户ID\",\"source\":\"DECLARED\"}]}" ,"@ERDiagram");
        assertDsl(DiagramType.FUNCTION_MODULE,"功能图","{\"type\":\"FUNCTION_MODULE\",\"nodes\":[{\"kind\":\"root\",\"text\":\"商城系统\"},{\"kind\":\"module\",\"text\":\"用户管理\"},{\"kind\":\"module\",\"text\":\"用户档案\"}],\"relations\":[{\"from\":\"商城系统\",\"to\":\"用户管理\",\"kind\":\"contains\"},{\"from\":\"用户管理\",\"to\":\"用户档案\",\"kind\":\"contains\"}]}" ,"@FunctionModule");
        assertDsl(DiagramType.ARCHITECTURE,"商城架构","{\"type\":\"ARCHITECTURE\",\"nodes\":[{\"kind\":\"client\",\"text\":\"Web端\",\"group\":\"表现层\"},{\"kind\":\"service\",\"text\":\"订单服务\",\"group\":\"服务层\"}],\"relations\":[{\"from\":\"Web端\",\"to\":\"订单服务\",\"kind\":\"call\"}]}" ,"@ArchitectureDiagram");
        assertDsl(DiagramType.USE_CASE,"商城用例","{\"type\":\"USE_CASE\",\"nodes\":[{\"kind\":\"actor\",\"text\":\"顾客\"},{\"kind\":\"use_case\",\"text\":\"提交订单\",\"group\":\"商城系统\"}],\"relations\":[{\"from\":\"顾客\",\"to\":\"提交订单\",\"kind\":\"association\"}]}" ,"@UseCaseDiagram");
        assertDsl(DiagramType.BLOCK_DIAGRAM,"控制框图","{\"type\":\"BLOCK_DIAGRAM\",\"nodes\":[{\"kind\":\"input\",\"text\":\"设定值\"},{\"kind\":\"controller\",\"text\":\"控制器\"},{\"kind\":\"output\",\"text\":\"输出\"}],\"relations\":[{\"from\":\"设定值\",\"to\":\"控制器\",\"kind\":\"control_flow\"},{\"from\":\"控制器\",\"to\":\"输出\",\"kind\":\"signal_flow\"}]}" ,"@BlockDiagram");
        assertDsl(DiagramType.SEQUENCE_DIAGRAM,"登录时序","{\"type\":\"SEQUENCE_DIAGRAM\",\"nodes\":[{\"kind\":\"actor\",\"text\":\"用户\"},{\"kind\":\"lifeline\",\"text\":\"登录页面\"},{\"kind\":\"lifeline\",\"text\":\"认证服务\"}],\"relations\":[{\"from\":\"用户\",\"to\":\"登录页面\",\"kind\":\"sync\",\"label\":\"提交登录\"},{\"from\":\"登录页面\",\"to\":\"认证服务\",\"kind\":\"async\",\"label\":\"认证\"},{\"from\":\"认证服务\",\"to\":\"登录页面\",\"kind\":\"return\",\"label\":\"结果\"}]}" ,"@SequenceDiagram");
    }

    @Test void rejectsUnknownRelationReferences(){
        String json="{\"type\":\"FLOWCHART\",\"nodes\":[{\"kind\":\"start\",\"text\":\"开始\"}],\"relations\":[{\"from\":\"开始\",\"to\":\"不存在\",\"kind\":\"flow\"}]}";
        assertThrows(DiagramGenerationException.class,()->adapter.adapt(DiagramType.FLOWCHART,json,"测试"));
    }

    @Test void downgradesIncompleteDecisionSoCompilerDoesNotRejectWholeRequest(){
        String json="{\"type\":\"FLOWCHART\",\"nodes\":[{\"kind\":\"process\",\"text\":\"开始\"},{\"kind\":\"decision\",\"text\":\"检查库存\"},{\"kind\":\"process\",\"text\":\"结束\"}],\"relations\":[{\"from\":\"开始\",\"to\":\"检查库存\",\"kind\":\"flow\"},{\"from\":\"检查库存\",\"to\":\"结束\",\"kind\":\"flow\",\"label\":\"通过\"}]}";
        DiagramIr ir=adapter.adapt(DiagramType.FLOWCHART,json,"库存流程");
        DiagramIr normalized=new DiagramRuleEngine().normalize(ir);
        assertTrue(codec.compile(normalized).contains("N2|process|检查库存"));
    }

    @Test void convertsExistingDiagramBackToLiteForEditRequests(){
        FlowchartIr current=(FlowchartIr)codec.parse("@Flowchart\n标题：订单流程\n[节点]\nN1|start|开始\nN2|process|提交订单\nN3|end|结束\n[连接]\nN1->N2\nN2->N3");
        var lite=adapter.toLite(current);
        assertEquals("FLOWCHART",lite.path("type").asText());
        assertEquals(3,lite.path("nodes").size());assertEquals(2,lite.path("relations").size());
        assertFalse(lite.toString().contains("N1"));assertFalse(lite.toString().contains("diagramType"));
        assertNotNull(adapter.adapt(DiagramType.FLOWCHART,lite.toString(),current.title()));
    }

    @Test void normalizesLegacyErRelationshipNodeAndSynthesizesRequiredKeys(){
        String json="{\"type\":\"ER_DIAGRAM\",\"nodes\":[{\"kind\":\"entity\",\"text\":\"患者\"},{\"kind\":\"entity\",\"text\":\"患者资料\"},{\"kind\":\"relationship\",\"text\":\"一对一\"}],\"relations\":[{\"from\":\"患者\",\"to\":\"患者资料\",\"kind\":\"一对一\",\"cardinality\":\"1\"}]}";
        ErDiagramIr ir=(ErDiagramIr)adapter.adapt(DiagramType.ER_DIAGRAM,json,"患者ER图");
        assertEquals(2,ir.entities().size());assertEquals(1,ir.relations().size());
        assertTrue(ir.entities().stream().allMatch(entity->!entity.attributes().isEmpty()));
        String dsl=codec.compile(new DiagramRuleEngine().normalize(ir));
        assertEquals(DiagramType.ER_DIAGRAM,codec.parse(dsl).diagramType());
    }

    @Test void capsAndDeduplicatesErAttributesFromLocalModel(){
        StringBuilder attributes=new StringBuilder();
        for(int i=0;i<12;i++){if(i>0)attributes.append(',');attributes.append("{\"name\":\"字段").append(i%9).append("\",\"type\":\"TEXT\"}");}
        String json="{\"type\":\"ER_DIAGRAM\",\"nodes\":[{\"kind\":\"entity\",\"text\":\"用户\",\"attributes\":["+attributes+"]},{\"kind\":\"entity\",\"text\":\"订单\"}],\"relations\":[]}";
        ErDiagramIr ir=(ErDiagramIr)adapter.adapt(DiagramType.ER_DIAGRAM,json,"测试ER图");
        assertEquals(8,ir.entities().get(0).attributes().size());
        assertEquals(8,ir.entities().get(0).attributes().stream().map(ErAttribute::name).distinct().count());
    }

    @Test void normalizesReversedFunctionModuleRelationsAndMissingLeafFunctions(){
        String json="{\"type\":\"FUNCTION_MODULE\",\"nodes\":[{\"kind\":\"root\",\"text\":\"商城系统\"},{\"kind\":\"module\",\"text\":\"用户管理\"},{\"kind\":\"module\",\"text\":\"订单管理\"},{\"kind\":\"module\",\"text\":\"用户档案\"}],\"relations\":[{\"from\":\"用户管理\",\"to\":\"商城系统\",\"kind\":\"contains\"},{\"from\":\"订单管理\",\"to\":\"商城系统\",\"kind\":\"contains\"},{\"from\":\"用户档案\",\"to\":\"用户管理\",\"kind\":\"contains\"}]}";
        FunctionModuleIr ir=(FunctionModuleIr)adapter.adapt(DiagramType.FUNCTION_MODULE,json,"商城功能图");
        assertEquals(2,ir.modules().size());
        assertTrue(ir.modules().stream().filter(module->module.name().equals("订单管理")).flatMap(module->module.functions().stream()).anyMatch("订单管理功能"::equals));
        String dsl=codec.compile(new DiagramRuleEngine().normalize(ir));
        assertEquals(DiagramType.FUNCTION_MODULE,codec.parse(dsl).diagramType());
        assertFalse(dsl.contains("功能：\n"),dsl);
    }

    @Test void synthesizesReadableSequenceMessagesWhenModelOmitsLabels(){
        String json="{\"type\":\"SEQUENCE_DIAGRAM\",\"nodes\":[{\"kind\":\"actor\",\"text\":\"用户\"},{\"kind\":\"lifeline\",\"text\":\"登录页面\"}],\"relations\":[{\"from\":\"用户\",\"to\":\"登录页面\",\"kind\":\"sync\"},{\"from\":\"登录页面\",\"to\":\"用户\",\"kind\":\"return\"}]}";
        SequenceDiagramIr ir=(SequenceDiagramIr)adapter.adapt(DiagramType.SEQUENCE_DIAGRAM,json,"登录时序");
        assertTrue(ir.messages().stream().allMatch(message->!message.text().isBlank()));
        String dsl=codec.compile(new DiagramRuleEngine().normalize(ir));
        assertEquals(DiagramType.SEQUENCE_DIAGRAM,codec.parse(dsl).diagramType());
    }

    private void assertDsl(DiagramType type,String title,String json,String header){
        DiagramIr ir=adapter.adapt(type,json,title);String dsl=codec.compile(ir);assertTrue(dsl.startsWith(header),dsl);assertNotNull(codec.parse(dsl));
    }
}
