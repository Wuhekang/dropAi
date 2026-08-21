package com.dropai.rewrite.controller;

import com.dropai.rewrite.auth.AuthContext;
import com.dropai.rewrite.service.diagram.DiagramGenerationService;
import com.dropai.rewrite.service.diagram.DiagramGenerationException;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import java.util.concurrent.*;

@RestController
@RequestMapping("/api/diagram")
public class DiagramAssistantController {
    private final DiagramGenerationService generation;private final ScheduledExecutorService heartbeat=Executors.newScheduledThreadPool(1,r->{Thread t=new Thread(r,"diagram-sse-heartbeat");t.setDaemon(true);return t;});
    public DiagramAssistantController(DiagramGenerationService generation){this.generation=generation;}
    @PostMapping(value="/assistant/stream",produces=MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(@RequestBody DiagramGenerationService.Request request){long userId=AuthContext.requireUserId();SseEmitter emitter=new SseEmitter(100_000L);CompletableFuture<Void> job=CompletableFuture.runAsync(()->{try{generation.generate(userId,request,event->send(emitter,event.event(),event));emitter.complete();}catch(DiagramGenerationException e){emitter.complete();}catch(Exception e){send(emitter,"error",new DiagramGenerationService.ErrorData("INTERNAL_ERROR","生成失败，原图已恢复。",false));emitter.complete();}});ScheduledFuture<?> beat=heartbeat.scheduleAtFixedRate(()->send(emitter,"heartbeat",new DiagramGenerationService.Event("heartbeat",request.requestId(),"处理中",null)),8,8,TimeUnit.SECONDS);Runnable cancel=()->{beat.cancel(true);job.cancel(true);};emitter.onCompletion(cancel);emitter.onTimeout(cancel);emitter.onError(e->cancel.run());return emitter;}
    private void send(SseEmitter emitter,String name,Object data){try{emitter.send(SseEmitter.event().name(name).data(data,MediaType.APPLICATION_JSON));}catch(Exception ignored){}}
}
