package com.dropai.rewrite.service.diagram;

import com.dropai.rewrite.config.DiagramAssistantProperties;
import com.dropai.rewrite.service.DiagramService;
import com.dropai.rewrite.service.diagram.DiagramIr.DiagramType;
import com.dropai.rewrite.service.diagram.DiagramIr.Edge;
import com.dropai.rewrite.service.diagram.DiagramIr.FlowNode;
import com.dropai.rewrite.service.diagram.DiagramIr.FlowNodeKind;
import com.dropai.rewrite.service.diagram.DiagramIr.FlowchartIr;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class DiagramProviderFallbackTest {
    @Test
    void fallsBackToDoubaoOnceWhenOllamaDoesNotFinishWithinFiveSeconds() throws Exception {
        ObjectMapper mapper=new ObjectMapper();
        DiagramAssistantProperties properties=new DiagramAssistantProperties();
        properties.setProvider("ollama");
        properties.setOllamaFallbackEnabled(true);
        DoubaoDiagramClient doubao=mock(DoubaoDiagramClient.class);
        OllamaDiagramClient ollama=mock(OllamaDiagramClient.class);
        DiagramPromptFactory prompts=mock(DiagramPromptFactory.class);
        DiagramRuleEngine rules=mock(DiagramRuleEngine.class);
        DiagramDslCodec codec=mock(DiagramDslCodec.class);
        DiagramService diagrams=mock(DiagramService.class);
        FlowchartIr ir=new FlowchartIr("1.0",DiagramType.FLOWCHART,"测试流程",
                List.of(new FlowNode("N1",FlowNodeKind.START,"开始"),new FlowNode("N2",FlowNodeKind.END,"结束")),
                List.of(new Edge("E1","N1","N2","normal","",1)),List.of());
        DiagramPromptFactory.Prompt prompt=new DiagramPromptFactory.Prompt("system","user",mapper.createObjectNode(),10);

        when(ollama.generate(eq(DiagramType.FLOWCHART),anyString(),isNull(),any()))
                .thenThrow(new DiagramGenerationException("OLLAMA_TIMEOUT","本地绘图模型5秒内未返回完整结果。",true,null));
        when(prompts.build(eq(DiagramType.FLOWCHART),anyString(),isNull())).thenReturn(prompt);
        when(doubao.generate(eq(DiagramType.FLOWCHART),eq(prompt),any()))
                .thenReturn(new DoubaoDiagramClient.ModelResult(mapper.writeValueAsString(ir),20,80,"doubao"));
        when(rules.normalize(any())).thenAnswer(invocation->invocation.getArgument(0));
        when(codec.compile(any())).thenReturn("@Flowchart\n标题：测试流程\n\n[节点]\nN1|start|开始\nN2|end|结束\n\n[连接]\nN1->N2\n");
        when(diagrams.validate(anyString())).thenReturn(mapper.createObjectNode().put("valid",true));

        DiagramGenerationService service=new DiagramGenerationService(codec,prompts,doubao,ollama,
                new SemanticIrLiteAdapter(mapper),rules,mock(SqlSchemaExtractor.class),mock(SqlRelationResolver.class),diagrams,mapper,properties);
        List<DiagramGenerationService.Event> events=new ArrayList<>();
        DiagramGenerationService.Done done=service.generate(7L,new DiagramGenerationService.Request(
                "request-1","generation-1","conversation-1",DiagramGenerationService.hash(""),"FLOWCHART","创建测试流程","",""),events::add);

        assertEquals(2,done.metrics().modelCalls());
        assertTrue(events.stream().anyMatch(event->"provider_fallback".equals(event.event())));
        verify(ollama,times(1)).generate(eq(DiagramType.FLOWCHART),anyString(),isNull(),any());
        verify(doubao,times(1)).generate(eq(DiagramType.FLOWCHART),eq(prompt),any());
    }

    @Test
    void cancellationNeverCallsCloudFallback() {
        ObjectMapper mapper=new ObjectMapper();
        DiagramAssistantProperties properties=new DiagramAssistantProperties();
        properties.setProvider("ollama");
        DoubaoDiagramClient doubao=mock(DoubaoDiagramClient.class);
        OllamaDiagramClient ollama=mock(OllamaDiagramClient.class);
        when(ollama.generate(any(),anyString(),isNull(),any()))
                .thenThrow(new DiagramGenerationException("GENERATION_CANCELLED","生成已取消。",false,null));
        DiagramGenerationService service=new DiagramGenerationService(mock(DiagramDslCodec.class),mock(DiagramPromptFactory.class),doubao,ollama,
                new SemanticIrLiteAdapter(mapper),mock(DiagramRuleEngine.class),mock(SqlSchemaExtractor.class),mock(SqlRelationResolver.class),mock(DiagramService.class),mapper,properties);

        DiagramGenerationException error=assertThrows(DiagramGenerationException.class,()->service.generate(8L,
                new DiagramGenerationService.Request("request-2","generation-2","conversation-2",DiagramGenerationService.hash(""),"FLOWCHART","创建测试流程","",""),event->{}));

        assertEquals("GENERATION_CANCELLED",error.code());
        verifyNoInteractions(doubao);
    }
}
