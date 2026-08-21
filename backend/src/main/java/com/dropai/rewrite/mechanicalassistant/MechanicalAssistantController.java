package com.dropai.rewrite.mechanicalassistant;

import com.dropai.rewrite.auth.AuthContext;
import com.dropai.rewrite.vo.Result;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/mechanical-assistant")
public class MechanicalAssistantController {
    private final MechanicalAssistantService service;
    public MechanicalAssistantController(MechanicalAssistantService service){this.service=service;}
    @PostMapping(value="/analyze",consumes=MediaType.MULTIPART_FORM_DATA_VALUE)
    public Result<Map<String,Object>> analyze(@RequestParam(required=false) String projectName,@RequestParam(required=false) String description,@RequestParam(required=false) List<MultipartFile> files)throws Exception{return Result.success(service.analyze(AuthContext.requireUserId(),projectName,description,files));}
    @GetMapping("/projects/{id}") public Result<Map<String,Object>> get(@PathVariable String id){return Result.success(service.get(id));}
    @GetMapping("/projects/{id}/report.docx") public ResponseEntity<byte[]> report(@PathVariable String id)throws Exception{
        return ResponseEntity.ok().header(HttpHeaders.CONTENT_DISPOSITION,"attachment; filename*=UTF-8''"+java.net.URLEncoder.encode("机械设计辅助报告.docx", StandardCharsets.UTF_8)).contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.wordprocessingml.document")).body(service.report(id));
    }
}
