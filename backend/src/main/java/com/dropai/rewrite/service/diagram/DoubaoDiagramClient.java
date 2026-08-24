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
        String key=cleanKey(properties.getApiKey());if(key.isBlank())throw new DiagramGenerationException("DOUBAO_AUTH_MISSING","未配置豆包API Key");
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
    public SummaryResult summarize(String source,int maxChars){
        String key=cleanKey(properties.getApiKey());if(key.isBlank())throw new DiagramGenerationException("DOUBAO_AUTH_MISSING","未配置豆包API Key");
        if(source==null||source.isBlank())return new SummaryResult("",0,properties.getModel());
        long started=System.nanoTime();
        try{
            String system="你是绘图需求压缩器。提取主体、关键步骤、判断分支和关系，删除背景、解释、举例和重复内容。只返回JSON对象：{\"summary\":\"...\"}。summary不得超过"+maxChars+"个中文字符，不生成DSL或图形代码。";
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
    private static String cleanKey(String value){String v=Objects.toString(value,"").trim();if(v.regionMatches(true,0,"Bearer ",0,7))v=v.substring(7);return v.replaceAll("[\\p{Cc}\\p{Z}\\s]","").replaceAll("^[\"']|[\"']$","");}
    private static String safe(String value){String v=value.replaceAll("(?i)bearer\\s+\\S+","Bearer ***").replaceAll("[\\r\\n]+"," ");return v.substring(0,Math.min(500,v.length()));}
    private static String limit(String value,int max){if(value.length()<=max)return value;String cut=value.substring(0,Math.max(1,max)).replaceAll("[，、；：\\s]+$","");return cut.length()<max?cut+"。":cut;}
    public record ModelResult(String json,long firstByteMs,long totalMs,String model){}
    public record SummaryResult(String summary,long totalMs,String model){}
}
