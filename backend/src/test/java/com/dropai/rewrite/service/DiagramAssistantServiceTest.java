package com.dropai.rewrite.service;

import com.dropai.rewrite.config.DiagramAssistantProperties;
import com.dropai.rewrite.service.diagram.*;
import com.dropai.rewrite.service.diagram.DiagramIr.*;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class DiagramAssistantServiceTest {
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
