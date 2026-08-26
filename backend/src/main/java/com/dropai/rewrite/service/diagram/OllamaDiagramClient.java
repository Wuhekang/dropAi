package com.dropai.rewrite.service.diagram;

import com.dropai.rewrite.config.DiagramAssistantProperties;
import com.dropai.rewrite.service.diagram.DiagramIr.DiagramType;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.function.IntConsumer;

@Component
public class OllamaDiagramClient {
    private static final Logger log=LoggerFactory.getLogger(OllamaDiagramClient.class);
    private static final String SYSTEM="你是DROP-AI SemanticIR Lite关系规划器。只输出一个标准JSON对象，禁止Markdown、解释、DSL、ID、坐标、布局、样式和元数据。根对象只能包含type、nodes、relations；nodes只表达语义类型kind和文字text以及当前图类型必要的语义字段；relations使用节点text作为from和to，不得引用不存在的节点。";
    private final DiagramAssistantProperties properties;private final ObjectMapper mapper;private final SemanticIrLiteAdapter adapter;private final HttpClient http;
    @Autowired
    public OllamaDiagramClient(DiagramAssistantProperties properties,ObjectMapper mapper,SemanticIrLiteAdapter adapter){this(properties,mapper,adapter,HttpClient.newBuilder().connectTimeout(properties.getConnectTimeout()).build());}
    OllamaDiagramClient(DiagramAssistantProperties properties,ObjectMapper mapper,SemanticIrLiteAdapter adapter,HttpClient http){this.properties=properties;this.mapper=mapper;this.adapter=adapter;this.http=http;}

    public DoubaoDiagramClient.ModelResult generate(DiagramType type,String instruction,DiagramIr current,IntConsumer onDelta){
        long started=System.nanoTime();
        try{
            ObjectNode user=mapper.createObjectNode().put("task",current==null?"CREATE":"EDIT").put("diagramType",type.name()).put("instruction",Objects.toString(instruction,"")).put("outputSchema","SemanticIR-Lite-v3").put("outputContract",contract(type));
            if(current!=null)user.set("currentSemanticIr",adapter.toLite(current));
            Map<String,Object> body=new LinkedHashMap<>();body.put("model",properties.getOllamaModel());body.put("stream",false);body.put("format","json");body.put("keep_alive","15m");body.put("options",Map.of("temperature",0,"num_predict",Math.min(1800,properties.tokensFor(type)),"num_ctx",4096));body.put("messages",List.of(Map.of("role","system","content",SYSTEM),Map.of("role","user","content",mapper.writeValueAsString(user))));
            HttpRequest request=HttpRequest.newBuilder(URI.create(properties.getOllamaEndpoint())).timeout(properties.getHardLimit()).header("Content-Type","application/json").POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body),StandardCharsets.UTF_8)).build();
            HttpResponse<String> response=http.send(request,HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if(response.statusCode()/100!=2)throw new DiagramGenerationException("OLLAMA_HTTP_ERROR","本地绘图模型请求失败（HTTP "+response.statusCode()+"）。");
            JsonNode root=mapper.readTree(response.body());String content=root.path("message").path("content").asText("").trim();if(content.isBlank())content=root.path("response").asText("").trim();if(content.isBlank())throw new DiagramGenerationException("OLLAMA_EMPTY_RESPONSE","本地绘图模型没有返回SemanticIR JSON。");
            onDelta.accept(content.length());long total=(System.nanoTime()-started)/1_000_000;log.info("ollama diagram completed model={} totalMs={} outputChars={}",properties.getOllamaModel(),total,content.length());return new DoubaoDiagramClient.ModelResult(content,total,total,properties.getOllamaModel());
        }catch(DiagramGenerationException e){throw e;}catch(HttpTimeoutException e){throw new DiagramGenerationException("OLLAMA_TIMEOUT","本地绘图模型生成超时，原图已恢复。",true,e);}catch(Exception e){throw new DiagramGenerationException("OLLAMA_REQUEST_FAILED","无法连接本地绘图模型，原图已恢复。",true,e);}
    }

    private static String contract(DiagramType type){return switch(type){
        case FLOWCHART->"nodes.kind仅start/process/decision/end；relations.kind=flow；decision必须有两个不同非空label";
        case ER_DIAGRAM->"nodes仅entity且必须含table、attributes[{name,type,pk?,fk?,unique?,nullable?,ref?}]；relations.kind=relationship且必须含fromCardinality,toCardinality,fromField,toField,source(DECLARED或INFERRED)";
        case FUNCTION_MODULE->"恰好一个root，其余module；每个module必须由一条contains关系连接到唯一父节点";
        case ARCHITECTURE->"每个node必须含group，kind仅client/gateway/service/cache/message_queue/database/external_system；relation.kind仅call/data_flow/dependency";
        case USE_CASE->"kind仅actor/use_case；每个use_case必须含同一系统名group；relation.kind仅association/include/extend/generalization";
        case BLOCK_DIAGRAM->"kind仅input/output/block/controller/sensor/actuator/storage；relation.kind仅signal_flow/control_flow/data_flow/energy_flow/feedback";
        case SEQUENCE_DIAGRAM->"kind仅actor/lifeline；relation.kind仅sync/return/async，可选block.kind仅alt/loop/opt/par";
    };}
}
