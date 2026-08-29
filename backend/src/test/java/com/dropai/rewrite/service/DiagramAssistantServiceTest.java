package com.dropai.rewrite.service;

import com.dropai.rewrite.config.DiagramAssistantProperties;
import com.dropai.rewrite.service.diagram.*;
import com.dropai.rewrite.service.diagram.DiagramIr.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

class DiagramAssistantServiceTest {
    private static final String LIGHT_CONTROL_SOURCE="""
            程序开始后首先执行系统初始化步骤。该步骤包括配置STM32的模数转换器ADC以连接光敏电阻信号输入引脚，设定参考电压和采样时间，同时初始化PWM输出引脚用于后续的灯光调档控制。初始化完成后，程序进入主循环，开始连续检测光照强度。
            程序通过ADC读取光敏电阻的分压值并换算照度，再与上下限阈值比较。若不在预设范围内则关灯；若在预设范围内则按照度分档调整PWM。执行完成后结束本轮处理，不停止时回到照度采集，停止时进入结束节点。
            """;
    @Test void flowchartIrIsNormalizedAndCompiledLocally() {
        var ir=new FlowchartIr("1.0",DiagramType.FLOWCHART,"订单流程",
                List.of(new FlowNode("N1",FlowNodeKind.START,"开始"),new FlowNode("N2",FlowNodeKind.DECISION,"支付成功？"),new FlowNode("N3",FlowNodeKind.PROCESS,"发货"),new FlowNode("N4",FlowNodeKind.END,"结束")),
                List.of(new Edge("E1","N1","N2","normal","",1),new Edge("E2","N2","N3","normal","是",2),new Edge("E3","N2","N4","normal","否",3),new Edge("E4","N3","N4","normal","",4)),List.of());
        var normalized=(FlowchartIr)new DiagramRuleEngine().normalize(ir);
        String dsl=new DiagramDslCodec().compile(normalized);
        assertTrue(dsl.startsWith("@Flowchart\n标题：订单流程"));
        assertTrue(dsl.contains("N2->N3|是"));
        assertTrue(dsl.contains("N2->N4|否"));
    }

    @Test void danglingReferenceIsRejectedWithoutSecondModelCall() {
        var ir=new FlowchartIr("1.0",DiagramType.FLOWCHART,"错误流程",
                List.of(new FlowNode("N1",FlowNodeKind.START,"开始"),new FlowNode("N2",FlowNodeKind.END,"结束")),
                List.of(new Edge("E1","N1","MISSING","normal","",1)),List.of());
        var error=assertThrows(DiagramGenerationException.class,()->new DiagramRuleEngine().normalize(ir));
        assertEquals("DIAGRAM_RELATION_INVALID",error.code());
    }

    @Test void flowchartDecisionWithMoreThanTwoBranchesIsRejected() {
        var ir=new FlowchartIr("1.0",DiagramType.FLOWCHART,"光照等级判定流程",
                List.of(new FlowNode("N1",FlowNodeKind.START,"开始"),new FlowNode("N2",FlowNodeKind.DECISION,"光照等级判定"),
                        new FlowNode("N3",FlowNodeKind.PROCESS,"调高PWM"),new FlowNode("N4",FlowNodeKind.PROCESS,"小幅提高PWM"),
                        new FlowNode("N5",FlowNodeKind.PROCESS,"微调PWM"),new FlowNode("N6",FlowNodeKind.PROCESS,"关闭LED"),
                        new FlowNode("N7",FlowNodeKind.END,"结束")),
                List.of(new Edge("E1","N1","N2","normal","",1),new Edge("E2","N2","N3","normal","明显不足",2),
                        new Edge("E3","N2","N4","normal","不足",3),new Edge("E4","N2","N5","normal","接近目标",4),
                        new Edge("E5","N2","N6","normal","充足",5),new Edge("E6","N3","N7","normal","",6),
                        new Edge("E7","N4","N7","normal","",7),new Edge("E8","N5","N7","normal","",8),
                        new Edge("E9","N6","N7","normal","",9)),List.of());
        var error=assertThrows(DiagramGenerationException.class,()->new DiagramRuleEngine().normalize(ir));
        assertTrue(error.getMessage().contains("最多只能有两个分支"));
    }

    @Test void missingFlowchartEndIsAddedAndAllTerminalBranchesAreConnected() {
        var ir=new FlowchartIr("1.0",DiagramType.FLOWCHART,"光敏电阻调光控制流程",
                List.of(new FlowNode("N1",FlowNodeKind.START,"系统启动"),new FlowNode("N2",FlowNodeKind.PROCESS,"初始化ADC与PWM"),
                        new FlowNode("N3",FlowNodeKind.PROCESS,"读ADC换算照度"),new FlowNode("N4",FlowNodeKind.DECISION,"是否在预设范围"),
                        new FlowNode("N5",FlowNodeKind.PROCESS,"关灯"),new FlowNode("N6",FlowNodeKind.PROCESS,"按照度分档调PWM")),
                List.of(new Edge("E1","N1","N2","normal","",1),new Edge("E2","N2","N3","normal","",2),
                        new Edge("E3","N3","N4","normal","",3),new Edge("E4","N4","N5","normal","否",4),
                        new Edge("E5","N4","N6","normal","是",5)),List.of());
        var normalized=(FlowchartIr)new DiagramRuleEngine().normalize(ir);
        var end=normalized.nodes().stream().filter(n->n.kind()==FlowNodeKind.END).findFirst().orElseThrow();
        assertEquals(7,normalized.nodes().size());
        assertTrue(normalized.edges().stream().anyMatch(e->e.from().equals("N5")&&e.to().equals(end.id())));
        assertTrue(normalized.edges().stream().anyMatch(e->e.from().equals("N6")&&e.to().equals(end.id())));
        assertTrue(normalized.warnings().stream().anyMatch(w->w.code().equals("FLOW_END_ADDED")));
        assertTrue(new DiagramDslCodec().compile(normalized).contains("|end|结束"));
    }

    @Test void endlessCycleWithoutExplicitStopExitIsRejected() {
        var ir=new FlowchartIr("1.0",DiagramType.FLOWCHART,"无结束循环",
                List.of(new FlowNode("N1",FlowNodeKind.START,"开始"),new FlowNode("N2",FlowNodeKind.PROCESS,"采集"),new FlowNode("N3",FlowNodeKind.PROCESS,"处理")),
                List.of(new Edge("E1","N1","N2","normal","",1),new Edge("E2","N2","N3","normal","",2),new Edge("E3","N3","N2","normal","",3)),List.of());
        var error=assertThrows(DiagramGenerationException.class,()->new DiagramRuleEngine().normalize(ir));
        assertTrue(error.getMessage().contains("是否停止"));
    }

    @Test void overlongSummaryIsCompactedBySemanticSlotsInsteadOfCuttingOffTheEnding() throws Exception {
        String value="主题=光敏电阻调光；起点=初始化ADC与PWM；主链=采集照度>换算>比较；分支=范围外关灯/范围内调PWM；循环/终点=继续则采集/停止则结束";
        var method=DoubaoDiagramClient.class.getDeclaredMethod("limit",String.class,int.class);method.setAccessible(true);
        String compact=(String)method.invoke(null,value,80);
        assertTrue(compact.length()<=80);
        assertTrue(compact.contains("主题="));
        assertTrue(compact.contains("分支="));
        assertTrue(compact.endsWith("结束"));
    }

    @Test void summaryLimitRedistributesUnusedSlotBudget() throws Exception {
        String value="主题=商城系统功能模块；系统=商城端>农户审核端>管理员后台；直属模块=认证与用户>商品与审核>交易流程>后台运营>内容统计；模块功能=登录注册/商品检索/规格库存/购物车/订单状态/农户提交/管理员审核/文件管理/营收统计；归属=无";
        var method=DoubaoDiagramClient.class.getDeclaredMethod("limit",String.class,int.class);method.setAccessible(true);
        String compact=(String)method.invoke(null,value,100);
        assertEquals(100,compact.length());
        assertTrue(compact.contains("直属模块="));assertTrue(compact.contains("模块功能="));assertTrue(compact.endsWith("归属=无"));
    }

    @Test void sixNonFlowDiagramIrVariantsCompileAndRenderWithAuthoritativeWorkers() throws Exception {
        var rules=new DiagramRuleEngine();var codec=new DiagramDslCodec();
        List<DiagramIr> inputs=List.of(
                new FunctionModuleIr("1.0",DiagramType.FUNCTION_MODULE,"农产品商城系统功能模块图",
                        List.of(new ModuleNode("ROOT","农产品商城系统",null,List.of()),
                                new ModuleNode("M1","认证与用户","ROOT",List.of("登录注册","权限管理")),
                                new ModuleNode("M2","商品与审核","ROOT",List.of("商品检索","农户提交","管理员审核"))),List.of()),
                new ArchitectureIr("1.0",DiagramType.ARCHITECTURE,"商城系统架构图",
                        List.of(new ArchitectureLayer("L1","表现层",List.of("商城端","农户审核端","管理后台")),
                                new ArchitectureLayer("L2","服务层",List.of("用户服务","商品服务","订单服务")),
                                new ArchitectureLayer("L3","数据层",List.of("MySQL数据库"))),List.of(new Edge("E1","L1","L2","depends","",1)),List.of()),
                new UseCaseIr("1.0",DiagramType.USE_CASE,"商城系统用例图","shop","商城系统",
                        List.of(new Actor("farmer","农户",null),new Actor("admin","管理员",null)),
                        List.of(new UseCase("submit","shop","提交商品"),new UseCase("review","shop","审核商品")),
                        List.of(new UseCaseRelation("R1","submit","farmer",UseCaseRelationKind.ASSOCIATION),
                                new UseCaseRelation("R2","admin","review",UseCaseRelationKind.ASSOCIATION),
                                new UseCaseRelation("R3","review","submit",UseCaseRelationKind.EXTEND)),List.of()),
                new BlockDiagramIr("1.0",DiagramType.BLOCK_DIAGRAM,"照明控制系统框图",
                        List.of(new Block("sensor","LEFT","人员与光照传感器"),new Block("controller","CENTER","STM32控制器"),new Block("led","RIGHT","LED照明输出")),
                        List.of(new Edge("E1","sensor","controller","signal","",1),new Edge("E2","controller","led","control","",2)),List.of()),
                new SequenceDiagramIr("1.0",DiagramType.SEQUENCE_DIAGRAM,"商品审核时序图",
                        List.of(new Participant("farmer","ACTOR","农户"),new Participant("service","SERVICE","商品服务"),new Participant("db","DATABASE","商品数据库")),
                        List.of(new Message("M1","farmer","service",MessageKind.CALL,"提交商品",1),new Message("M2","service","db",MessageKind.CALL,"保存待审商品",2),new Message("M3","db","service",MessageKind.RETURN,"返回保存结果",3),new Message("M4","service","farmer",MessageKind.RETURN,"返回提交结果",4)),List.of()),
                new ErDiagramIr("1.0",DiagramType.ER_DIAGRAM,"商城核心ER图",
                        List.of(new ErEntity("user","用户",List.of(new ErAttribute("userId","用户ID","",true,false,true,false),new ErAttribute("name","用户名","",false,false,false,false)),false),
                                new ErEntity("order","订单",List.of(new ErAttribute("orderId","订单ID","",true,false,true,false),new ErAttribute("amount","订单金额","",false,false,false,false)),false)),
                        List.of(new ErRelation("R1","user","用户ID","order","订单ID","1","N","创建",RelationSource.USER,1d)),List.of("user","order"),List.of())
        );
        Map<DiagramType,String> expectedHeaders=Map.of(
                DiagramType.FUNCTION_MODULE,"@FunctionModule",DiagramType.ARCHITECTURE,"@ArchitectureDiagram",DiagramType.USE_CASE,"@UseCaseDiagram",
                DiagramType.BLOCK_DIAGRAM,"@BlockDiagram",DiagramType.SEQUENCE_DIAGRAM,"@SequenceDiagram",DiagramType.ER_DIAGRAM,"@ERDiagram");
        for(DiagramIr input:inputs){
            DiagramIr normalized=rules.normalize(input);String dsl=codec.compile(normalized);
            assertTrue(dsl.startsWith(expectedHeaders.get(input.diagramType())),dsl);
            assertFalse(dsl.matches("(?ms).*^(?:功能|组件)：\\s*$.*"),dsl);
            var rendered=renderWithWorker(dsl);
            assertTrue(rendered.path("valid").asBoolean(),input.diagramType()+" "+rendered.path("issues"));
            assertTrue(rendered.path("ok").asBoolean(),input.diagramType()+" "+rendered);
            assertTrue(rendered.path("svg").asText().contains("<svg"));
        }
    }

    @Test void modelSchemaOnlyOffersSyntaxSupportedByCurrentWorkers() {
        var mapper=new ObjectMapper();var schemas=new DiagramSchemaFactory(mapper);
        String function=schemas.schema(DiagramType.FUNCTION_MODULE).toString();
        String architecture=schemas.schema(DiagramType.ARCHITECTURE).toString();
        String useCase=schemas.schema(DiagramType.USE_CASE).toString();
        String sequence=schemas.schema(DiagramType.SEQUENCE_DIAGRAM).toString();
        assertFalse(function.contains("parentId"));
        assertFalse(architecture.contains("dependencies"));
        assertFalse(useCase.contains("GENERALIZATION"));
        assertFalse(sequence.contains("ASYNC"));
    }

    private static com.fasterxml.jackson.databind.JsonNode renderWithWorker(String dsl) throws Exception {
        Path worker=Path.of("diagram-worker","web_engine.py").toAbsolutePath().normalize();
        if(!Files.isRegularFile(worker))worker=Path.of("..","diagram-worker","web_engine.py").toAbsolutePath().normalize();
        assertTrue(Files.isRegularFile(worker),"找不到绘图器："+worker);
        Process process=new ProcessBuilder("python","-X","utf8",worker.toString()).redirectErrorStream(false).start();
        byte[] request=new ObjectMapper().writeValueAsBytes(new LinkedHashMap<>(Map.of("command","render","dsl",dsl)));
        process.getOutputStream().write(request);process.getOutputStream().close();
        CompletableFuture<byte[]> stdoutBytes=CompletableFuture.supplyAsync(()->readAll(process.getInputStream()));
        CompletableFuture<byte[]> stderrBytes=CompletableFuture.supplyAsync(()->readAll(process.getErrorStream()));
        assertTrue(process.waitFor(15,TimeUnit.SECONDS),"绘图器执行超时");
        String stdout=new String(stdoutBytes.get(2,TimeUnit.SECONDS),StandardCharsets.UTF_8).trim();
        String stderr=new String(stderrBytes.get(2,TimeUnit.SECONDS),StandardCharsets.UTF_8).trim();
        assertEquals(0,process.exitValue(),stderr);assertFalse(stdout.isBlank(),stderr);
        return new ObjectMapper().readTree(stdout);
    }

    private static byte[] readAll(java.io.InputStream stream){try{return stream.readAllBytes();}catch(java.io.IOException e){throw new java.io.UncheckedIOException(e);}}

    @Test
    @EnabledIfEnvironmentVariable(named="RUN_DIAGRAM_LIVE_TEST",matches="true")
    void realDoubaoLightControlCaseAlwaysCompilesWithAnEndNode() {
        var properties=new DiagramAssistantProperties();
        properties.setApiKey(System.getenv("DOUBAO_API_KEY"));
        properties.setTemperature(0);
        var mapper=new ObjectMapper();
        var client=new DoubaoDiagramClient(properties,mapper);
        var summary=client.summarize(LIGHT_CONTROL_SOURCE,100,DiagramType.FLOWCHART).summary();
        var prompt=new DiagramPromptFactory(new DiagramSchemaFactory(mapper),mapper).build(DiagramType.FLOWCHART,summary,null);
        var model=client.generate(DiagramType.FLOWCHART,prompt,ignored->{});
        FlowchartIr raw;
        try{raw=mapper.readValue(model.json(),FlowchartIr.class);}catch(Exception e){throw new AssertionError(e);}
        var normalized=(FlowchartIr)new DiagramRuleEngine().normalize(raw);
        assertTrue(normalized.nodes().stream().anyMatch(n->n.kind()==FlowNodeKind.END));
        assertTrue(new DiagramDslCodec().compile(normalized).contains("|end|"));
    }

    @Test
    @EnabledIfEnvironmentVariable(named="RUN_DIAGRAM_LIVE_TEST",matches="true")
    void realDoubaoSixNonFlowCasesCompileAndRender() throws Exception {
        var properties=new DiagramAssistantProperties();properties.setApiKey(System.getenv("DOUBAO_API_KEY"));properties.setTemperature(0);
        var mapper=new ObjectMapper();var client=new DoubaoDiagramClient(properties,mapper);var schemas=new DiagramSchemaFactory(mapper);var rules=new DiagramRuleEngine();var codec=new DiagramDslCodec();
        Map<DiagramType,String> samples=new LinkedHashMap<>();
        samples.put(DiagramType.FUNCTION_MODULE,"系统功能依据业务职责划分为认证与用户、商品与审核、交易流程、后台运营和内容统计五类模块，并进一步细分为登录注册、商品检索、规格与库存、购物车、订单状态、农户提交、管理员审核、文件管理和营收统计等功能。系统功能模块如图3-1所示。功能模块图统一展示商城端、农户审核端和管理员后台的功能层级。模块间通过用户标识、商品标识、审核标识和订单标识进行逻辑关联，前端页面与后端服务按职责组织。");
        samples.put(DiagramType.ER_DIAGRAM,"生成农产品商城ER图：用户创建订单，订单包含商品，农户发布商品，管理员审核商品；为每个实体给出主键和核心属性。");
        samples.put(DiagramType.ARCHITECTURE,"生成农产品商城系统架构图，包含表现层、接口层、业务服务层和数据层，并为每层列出核心组件。");
        samples.put(DiagramType.USE_CASE,"生成农产品商城用例图：买家登录、检索商品、加入购物车和下单；农户提交商品；管理员审核商品和查看营收统计。");
        samples.put(DiagramType.BLOCK_DIAGRAM,"生成智能照明系统框图：人体与光照传感器输入STM32控制器，控制器输出PWM到LED并把状态发送给OLED和Wi-Fi模块。");
        samples.put(DiagramType.SEQUENCE_DIAGRAM,"生成用户下单时序图：用户通过商城页面提交订单，订单服务保存订单到数据库，数据库返回结果，订单服务向用户返回下单结果。");
        for(var entry:samples.entrySet()){
            String instruction=entry.getValue();if(instruction.length()>100)instruction=client.summarize(instruction,100,entry.getKey()).summary();
            var prompt=new DiagramPromptFactory(schemas,mapper).build(entry.getKey(),instruction,null);var result=client.generate(entry.getKey(),prompt,ignored->{});
            DiagramIr raw=readIr(mapper,entry.getKey(),result.json());DiagramIr normalized=rules.normalize(raw);String dsl=codec.compile(normalized);var rendered=renderWithWorker(dsl);
            assertTrue(rendered.path("valid").asBoolean(),entry.getKey()+" "+rendered.path("issues")+"\n"+dsl);assertTrue(rendered.path("ok").asBoolean(),entry.getKey()+"\n"+dsl);
        }
    }

    private static DiagramIr readIr(ObjectMapper mapper,DiagramType type,String json) throws Exception {
        return switch(type){
            case FLOWCHART->mapper.readValue(json,FlowchartIr.class);case ER_DIAGRAM->mapper.readValue(json,ErDiagramIr.class);
            case FUNCTION_MODULE->mapper.readValue(json,FunctionModuleIr.class);case ARCHITECTURE->mapper.readValue(json,ArchitectureIr.class);
            case USE_CASE->mapper.readValue(json,UseCaseIr.class);case BLOCK_DIAGRAM->mapper.readValue(json,BlockDiagramIr.class);
            case SEQUENCE_DIAGRAM->mapper.readValue(json,SequenceDiagramIr.class);
        };
    }

    @Test void sqlRelationsAreResolvedLocallyIncludingUniqueAndCompositeKeys() {
        String sql="""
                CREATE TABLE users (id BIGINT PRIMARY KEY, name VARCHAR(64) NOT NULL);
                CREATE TABLE profiles (id BIGINT PRIMARY KEY, user_id BIGINT UNIQUE,
                  CONSTRAINT fk_profile_user FOREIGN KEY (user_id) REFERENCES users(id));
                CREATE TABLE roles (id BIGINT PRIMARY KEY);
                CREATE TABLE user_roles (user_id BIGINT, role_id BIGINT,
                  PRIMARY KEY(user_id, role_id),
                  FOREIGN KEY(user_id) REFERENCES users(id),
                  FOREIGN KEY(role_id) REFERENCES roles(id));
                """;
        var properties=new DiagramAssistantProperties();
        var schema=new SqlSchemaExtractor().extract(sql);
        var ir=new SqlRelationResolver(properties).resolve(schema,"权限数据库ER图");
        assertEquals(4,ir.entities().size());
        assertTrue(ir.entities().stream().filter(e->e.id().equals("user_roles")).findFirst().orElseThrow().associationEntity());
        assertTrue(ir.relations().stream().anyMatch(r->r.targetEntityId().equals("profiles")&&r.targetCardinality().equals("1")));
        String dsl=new DiagramDslCodec().compile(ir);
        assertTrue(dsl.contains("users：id*，name"));
        assertTrue(dsl.contains("关系：users|profiles|引用|1|1"));
    }

    @Test void alterTableForeignKeyAndMissingTargetAreHandledDeterministically() {
        String valid="CREATE TABLE users(id BIGINT PRIMARY KEY); CREATE TABLE orders(id BIGINT PRIMARY KEY,user_id BIGINT); ALTER TABLE orders ADD CONSTRAINT fk_u FOREIGN KEY(user_id) REFERENCES users(id);";
        var schema=new SqlSchemaExtractor().extract(valid);
        assertEquals(1,schema.tables().stream().filter(t->t.name().equals("orders")).findFirst().orElseThrow().foreignKeys().size());
        String invalid="CREATE TABLE orders(id BIGINT PRIMARY KEY,user_id BIGINT, FOREIGN KEY(user_id) REFERENCES missing(id));";
        var error=assertThrows(DiagramGenerationException.class,()->new SqlRelationResolver(new DiagramAssistantProperties()).resolve(new SqlSchemaExtractor().extract(invalid),"ER"));
        assertEquals("SQL_PARSE_FAILED",error.code());
    }
}
