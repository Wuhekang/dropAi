package com.dropai.rewrite.service.diagram;

import com.dropai.rewrite.config.DiagramAssistantProperties;
import com.dropai.rewrite.service.diagram.DiagramIr.DiagramType;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.*;
import java.net.URI;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.IntConsumer;

@Component
public class DoubaoDiagramClient {
    private static final Logger log=LoggerFactory.getLogger(DoubaoDiagramClient.class);
    private final DiagramAssistantProperties properties; private final ObjectMapper mapper; private final HttpClient http;
    public DoubaoDiagramClient(DiagramAssistantProperties properties,ObjectMapper mapper){this.properties=properties;this.mapper=mapper;this.http=HttpClient.newBuilder().connectTimeout(properties.getConnectTimeout()).build();}
    public ModelResult generate(DiagramType type,DiagramPromptFactory.Prompt prompt,IntConsumer onDelta){
        String key=configuredKey();if(key.isBlank())throw new DiagramGenerationException("DOUBAO_AUTH_MISSING","未配置豆包API Key");
        long started=System.nanoTime();
        try{
            Map<String,Object> body=new LinkedHashMap<>();body.put("model",properties.getModel());body.put("stream",true);body.put("thinking",Map.of("type","disabled"));body.put("temperature",properties.getTemperature());body.put("max_completion_tokens",properties.tokensFor(type));body.put("response_format",Map.of("type","json_object"));body.put("messages",List.of(Map.of("role","system","content",prompt.system()),Map.of("role","user","content",prompt.user())));
            HttpRequest request=HttpRequest.newBuilder(URI.create(properties.getEndpoint())).timeout(properties.getHardLimit()).header("Authorization","Bearer "+key).header("Content-Type","application/json").POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body),StandardCharsets.UTF_8)).build();
            HttpResponse<InputStream> response;
            try{response=http.sendAsync(request,HttpResponse.BodyHandlers.ofInputStream()).get(properties.getFirstByteTimeout().toMillis(),TimeUnit.MILLISECONDS);}
            catch(TimeoutException e){throw new DiagramGenerationException("MODEL_FIRST_BYTE_TIMEOUT","豆包20秒内未建立响应，本次生成已终止，原图已恢复。");}
            if(response.statusCode()/100!=2){String text=new String(response.body().readNBytes(1000),StandardCharsets.UTF_8);throw new DiagramGenerationException("DOUBAO_HTTP_ERROR","豆包请求失败（HTTP "+response.statusCode()+"）："+safe(text));}
            return consume(response.body(),started,onDelta);
        }catch(DiagramGenerationException e){throw e;}catch(HttpConnectTimeoutException e){throw new DiagramGenerationException("DOUBAO_CONNECT_TIMEOUT","连接豆包超过5秒，本次生成已终止，原图已恢复。");}
        catch(Exception e){if(Thread.currentThread().isInterrupted())throw new DiagramGenerationException("GENERATION_CANCELLED","生成已取消，原图已恢复。");throw new DiagramGenerationException("DOUBAO_REQUEST_FAILED","豆包请求失败，原图已恢复。",false,e);}
    }
    public SummaryResult summarize(String source,int maxChars){return summarize(source,maxChars,null);}
    public SummaryResult summarize(String source,int maxChars,DiagramType type){
        String key=configuredKey();if(key.isBlank())throw new DiagramGenerationException("DOUBAO_AUTH_MISSING","未配置豆包API Key");
        if(source==null||source.isBlank())return new SummaryResult("",0,properties.getModel());
        long started=System.nanoTime();
        try{
            String system="你是绘图需求语义压缩器。先在内部理解完整原文，再重组成可绘图的结构提纲，禁止直接截取原文前半段。"
                    +"删除背景、解释、举例和重复，但必须保留主题、核心对象/起点、主链、关系/分支以及循环/终点。"
                    +"当原文含有停止、返回、循环或结束语义时，最后一槽必须明确保留。按此图类型使用固定五槽："+summaryTemplate(type)+"。"
                    +"每槽用短语和 > / 表示顺序或分支，不适用写“无”。在输出前自行计数，summary整体不得超过"+maxChars+"个字符。"
                    +"只返回JSON对象：{\"summary\":\"...\"}，不生成DSL、图形代码、解释或思考过程。";
            Map<String,Object> body=new LinkedHashMap<>();body.put("model",properties.getModel());body.put("stream",false);body.put("thinking",Map.of("type","disabled"));body.put("temperature",0);body.put("max_completion_tokens",256);body.put("response_format",Map.of("type","json_object"));body.put("messages",List.of(Map.of("role","system","content",system),Map.of("role","user","content",source)));
            HttpRequest request=HttpRequest.newBuilder(URI.create(properties.getEndpoint())).timeout(properties.getHardLimit()).header("Authorization","Bearer "+key).header("Content-Type","application/json").POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body),StandardCharsets.UTF_8)).build();
            HttpResponse<String> response=http.send(request,HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if(response.statusCode()/100!=2)throw new DiagramGenerationException("DOUBAO_SUMMARY_FAILED","长文本压缩失败（HTTP "+response.statusCode()+"），尚未生成图形。");
            JsonNode root=mapper.readTree(response.body());String content=root.path("choices").path(0).path("message").path("content").asText("");String summary=mapper.readTree(content).path("summary").asText("").trim();
            if(summary.isBlank())throw new DiagramGenerationException("DOUBAO_SUMMARY_EMPTY","豆包没有返回有效的绘图摘要，尚未生成图形。");
            summary=limit(summary,maxChars);long elapsed=(System.nanoTime()-started)/1_000_000;log.info("diagram source summarized inputChars={} summaryChars={} totalMs={}",source.length(),summary.length(),elapsed);return new SummaryResult(summary,elapsed,properties.getModel());
        }catch(DiagramGenerationException e){throw e;}catch(Exception e){throw new DiagramGenerationException("DOUBAO_SUMMARY_FAILED","长文本压缩失败，尚未生成图形。",true,e);}
    }
    private ModelResult consume(InputStream stream,long started,IntConsumer onDelta)throws Exception{
        ExecutorService readerExecutor=Executors.newSingleThreadExecutor(r->{Thread t=new Thread(r,"doubao-diagram-sse-reader");t.setDaemon(true);return t;});
        long firstByte=-1,lastData=System.nanoTime(),hardDeadline=started+properties.getHardLimit().toNanos();StringBuilder content=new StringBuilder();boolean truncated=false;
        try(BufferedReader reader=new BufferedReader(new InputStreamReader(stream,StandardCharsets.UTF_8))){
            while(true){long now=System.nanoTime(),idleRemaining=properties.getReadIdleTimeout().toNanos()-(now-lastData),hardRemaining=hardDeadline-now;if(idleRemaining<=0)throw idle();if(hardRemaining<=0)throw new DiagramGenerationException("MODEL_HARD_TIMEOUT","豆包生成超过最大时限，本次生成已终止，原图已恢复。");
                Future<String> future=readerExecutor.submit(reader::readLine);String line;try{line=future.get(Math.min(idleRemaining,hardRemaining),TimeUnit.NANOSECONDS);}catch(TimeoutException e){future.cancel(true);throw idle();}
                if(line==null)break;if(!line.startsWith("data:"))continue;String data=line.substring(5).trim();if(data.isEmpty())continue;if("[DONE]".equals(data))break;
                JsonNode event=mapper.readTree(data);JsonNode choice=event.path("choices").path(0);String delta=choice.path("delta").path("content").asText("");if(!delta.isEmpty()){long received=System.nanoTime();if(firstByte<0)firstByte=(received-started)/1_000_000;lastData=received;content.append(delta);onDelta.accept(delta.length());}
                if("length".equals(choice.path("finish_reason").asText()))truncated=true;
            }
        }finally{readerExecutor.shutdownNow();}
        if(truncated)throw new DiagramGenerationException("MODEL_OUTPUT_TRUNCATED","豆包输出达到长度上限，原图已恢复。");if(content.isEmpty())throw new DiagramGenerationException("INVALID_MODEL_JSON","豆包没有返回DiagramIR JSON，原图已恢复。");
        long total=(System.nanoTime()-started)/1_000_000;log.info("diagram model completed model={} firstByteMs={} totalMs={} outputChars={}",properties.getModel(),firstByte,total,content.length());return new ModelResult(content.toString(),firstByte,total,properties.getModel());
    }
    private DiagramGenerationException idle(){return new DiagramGenerationException("MODEL_IDLE_TIMEOUT","豆包连续20秒未返回新数据，本次生成已终止，原图已恢复。");}
    private String configuredKey(){String key=cleanKey(properties.getApiKey());return key.isBlank()?cleanKey(System.getenv("DOUBAO_API_KEY")):key;}
    private static String cleanKey(String value){String v=Objects.toString(value,"").trim();if(v.regionMatches(true,0,"Bearer ",0,7))v=v.substring(7);return v.replaceAll("[\\p{Cc}\\p{Z}\\s]","").replaceAll("^[\"']|[\"']$","");}
    private static String safe(String value){String v=value.replaceAll("(?i)bearer\\s+\\S+","Bearer ***").replaceAll("[\\r\\n]+"," ");return v.substring(0,Math.min(500,v.length()));}
    private static String summaryTemplate(DiagramType type){return switch(type==null?DiagramType.FLOWCHART:type){
        case FLOWCHART->"主题=…；起点=…；主链=…；分支=…；循环/终点=…";
        case ER_DIAGRAM->"主题=…；实体=…；关键属性=…；关系=…；约束=…";
        case FUNCTION_MODULE->"主题=…；根模块=…；一级模块=…；下级功能=…；层级=…";
        case ARCHITECTURE->"主题=…；入口=…；分层=…；组件=…；依赖=…";
        case USE_CASE->"主题=…；系统=…；参与者=…；用例=…；关系=…";
        case BLOCK_DIAGRAM->"主题=…；输入=…；核心块=…；连接=…；输出=…";
        case SEQUENCE_DIAGRAM->"主题=…；参与者=…；起始消息=…；主调用=…；返回=…";};}
    private static String limit(String value,int max){
        String normalized=Objects.toString(value,"").replaceAll("[\\r\\n\\t]+","").replaceAll("\\s{2,}"," ").trim();
        if(normalized.length()<=max)return normalized;
        List<String> clauses=Arrays.stream(normalized.split("[；;]+" )).map(String::trim).filter(s->!s.isEmpty()).toList();
        if(clauses.size()<2)return headTail(normalized,max);
        int count=Math.min(5,clauses.size());List<String> selected=new ArrayList<>(clauses.subList(0,Math.min(count,clauses.size())));
        String last=clauses.get(clauses.size()-1);if(clauses.size()>count&&!selected.contains(last))selected.set(selected.size()-1,last);
        int available=Math.max(selected.size(),max-(selected.size()-1)),base=available/selected.size(),extra=available%selected.size();List<String> compact=new ArrayList<>();
        for(int i=0;i<selected.size();i++)compact.add(headTail(selected.get(i),base+(i<extra?1:0)));
        return String.join("；",compact);
    }
    private static String headTail(String value,int max){if(value.length()<=max)return value;if(max<=1)return value.substring(0,Math.max(0,max));int remaining=max-1,head=Math.max(1,(remaining*2)/3),tail=Math.max(0,remaining-head);return value.substring(0,head)+"…"+(tail==0?"":value.substring(value.length()-tail));}
    public record ModelResult(String json,long firstByteMs,long totalMs,String model){}
    public record SummaryResult(String summary,long totalMs,String model){}
}
