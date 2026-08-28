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

import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
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
    public OllamaDiagramClient(DiagramAssistantProperties properties,ObjectMapper mapper,SemanticIrLiteAdapter adapter){this(properties,mapper,adapter,null);}
    OllamaDiagramClient(DiagramAssistantProperties properties,ObjectMapper mapper,SemanticIrLiteAdapter adapter,HttpClient http){this.properties=properties;this.mapper=mapper;this.adapter=adapter;this.http=http;}

    public DoubaoDiagramClient.ModelResult generate(DiagramType type,String instruction,DiagramIr current,IntConsumer onDelta){
        long started=System.nanoTime();
        try{
            ObjectNode user=mapper.createObjectNode().put("task",current==null?"CREATE":"EDIT").put("diagramType",type.name()).put("instruction",Objects.toString(instruction,"")).put("outputSchema","SemanticIR-Lite-v3").put("outputContract",contract(type));
            if(current!=null)user.set("currentSemanticIr",adapter.toLite(current));
            Map<String,Object> body=new LinkedHashMap<>();body.put("model",properties.getOllamaModel());body.put("stream",false);body.put("format","json");body.put("keep_alive","15m");body.put("options",Map.of("temperature",0,"seed",42,"repeat_penalty",1.15,"repeat_last_n",256,"num_predict",Math.min(1800,properties.tokensFor(type)),"num_ctx",4096));body.put("messages",List.of(Map.of("role","system","content",SYSTEM),Map.of("role","user","content",mapper.writeValueAsString(user))));
            String requestBody=mapper.writeValueAsString(body);int status;String responseBody;
            if(http!=null){HttpRequest request=HttpRequest.newBuilder(URI.create(properties.getOllamaEndpoint())).timeout(properties.getHardLimit()).header("Content-Type","application/json").POST(HttpRequest.BodyPublishers.ofString(requestBody,StandardCharsets.UTF_8)).build();HttpResponse<String> response=http.send(request,HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));status=response.statusCode();responseBody=response.body();}
            else{HttpURLConnection connection=(HttpURLConnection)new URL(properties.getOllamaEndpoint()).openConnection();connection.setRequestMethod("POST");connection.setDoOutput(true);connection.setConnectTimeout((int)Math.min(Integer.MAX_VALUE,properties.getConnectTimeout().toMillis()));connection.setReadTimeout((int)Math.min(Integer.MAX_VALUE,properties.getHardLimit().toMillis()));connection.setRequestProperty("Content-Type","application/json");byte[] bytes=requestBody.getBytes(StandardCharsets.UTF_8);connection.setFixedLengthStreamingMode(bytes.length);try(OutputStream output=connection.getOutputStream()){output.write(bytes);}status=connection.getResponseCode();try(InputStream input=status>=400?connection.getErrorStream():connection.getInputStream()){responseBody=input==null?"":new String(input.readAllBytes(),StandardCharsets.UTF_8);}finally{connection.disconnect();}}
            if(status/100!=2)throw new DiagramGenerationException("OLLAMA_HTTP_ERROR","本地绘图模型请求失败（HTTP "+status+"）。");
            JsonNode root=mapper.readTree(responseBody);String content=root.path("message").path("content").asText("").trim();if(content.isBlank())content=root.path("response").asText("").trim();if(content.isBlank())throw new DiagramGenerationException("OLLAMA_EMPTY_RESPONSE","本地绘图模型没有返回SemanticIR JSON。");
            onDelta.accept(content.length());long total=(System.nanoTime()-started)/1_000_000;log.info("ollama diagram completed model={} totalMs={} outputChars={}",properties.getOllamaModel(),total,content.length());return new DoubaoDiagramClient.ModelResult(content,total,total,properties.getOllamaModel());
        }catch(DiagramGenerationException e){throw e;}catch(HttpTimeoutException e){throw new DiagramGenerationException("OLLAMA_TIMEOUT","本地绘图模型生成超时，原图已恢复。",true,e);}catch(Exception e){throw new DiagramGenerationException("OLLAMA_REQUEST_FAILED","无法连接本地绘图模型，原图已恢复。",true,e);}
    }

    private static String contract(DiagramType type){return switch(type){
        case FLOWCHART->"nodes.kind仅start/process/decision/end；relations.kind=flow；decision必须有两个不同非空label";
        case ER_DIAGRAM->"nodes仅entity且必须含table、attributes[{name,type,pk?,fk?,unique?,nullable?,ref?}]；只生成用户明确提到的实体，每个实体最多6个不重复属性；relations.kind=relationship且必须含fromCardinality,toCardinality,fromField,toField,source(DECLARED或INFERRED)";
        case FUNCTION_MODULE->"恰好一个root，其余module；每个module必须由一条contains关系连接到唯一父节点";
        case ARCHITECTURE->"每个node必须含group，kind仅client/gateway/service/cache/message_queue/database/external_system；relation.kind仅call/data_flow/dependency";
        case USE_CASE->"kind仅actor/use_case；每个use_case必须含同一系统名group；relation.kind仅association/include/extend/generalization";
        case BLOCK_DIAGRAM->"kind仅input/output/block/controller/sensor/actuator/storage；relation.kind仅signal_flow/control_flow/data_flow/energy_flow/feedback";
        case SEQUENCE_DIAGRAM->"kind仅actor/lifeline；relation.kind仅sync/return/async，可选block.kind仅alt/loop/opt/par";
    };}
}
