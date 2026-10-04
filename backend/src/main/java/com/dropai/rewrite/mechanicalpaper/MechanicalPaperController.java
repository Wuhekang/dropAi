package com.dropai.rewrite.mechanicalpaper;

import com.dropai.rewrite.auth.AuthContext;
import com.dropai.rewrite.vo.Result;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.*;
import java.util.*;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

@RestController
@RequestMapping("/api/mechanical-papers")
public class MechanicalPaperController {
    private final MechanicalPaperService service;
    public MechanicalPaperController(MechanicalPaperService service) {this.service=service;}
    public record Start(String title) {}
    @GetMapping("/readiness") public Result<Map<String,Object>> readiness(){return Result.success(service.readiness());}
    @GetMapping public Result<List<Map<String,Object>>> recent(){return Result.success(service.recent(AuthContext.requireUserId()));}
    @PostMapping public Result<Map<String,Object>> start(@RequestBody Start input){return Result.success(service.start(AuthContext.requireUserId(),input.title()));}
    @GetMapping("/{id}") public Result<Map<String,Object>> get(@PathVariable String id){return Result.success(service.get(AuthContext.requireUserId(),id));}
    @PostMapping("/{id}/retry") public Result<Map<String,Object>> retry(@PathVariable String id){return Result.success(service.retry(AuthContext.requireUserId(),id));}
    @GetMapping("/{id}/download") public ResponseEntity<byte[]> download(@PathVariable String id) throws Exception {
        Long user=AuthContext.requireUserId();String name=service.get(user,id).get("title")+".docx";
        return ResponseEntity.ok().header(HttpHeaders.CONTENT_DISPOSITION,"attachment; filename*=UTF-8''"+URLEncoder.encode(name,StandardCharsets.UTF_8).replace("+","%20"))
            .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.wordprocessingml.document")).body(service.download(user,id));
    }
    @GetMapping("/{id}/images/{figureId}") public ResponseEntity<byte[]> image(@PathVariable String id,@PathVariable String figureId) throws Exception {
        byte[] bytes=service.image(AuthContext.requireUserId(),id,figureId);
        boolean jpeg=bytes.length>2 && bytes[0]==(byte)0xff && bytes[1]==(byte)0xd8;
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).contentType(jpeg?MediaType.IMAGE_JPEG:MediaType.IMAGE_PNG).body(bytes);
    }
}
