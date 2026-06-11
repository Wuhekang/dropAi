package com.dropai.rewrite.controller;

import com.dropai.rewrite.service.EngineeringWritingService;
import com.dropai.rewrite.vo.DocumentRewriteJobVO;
import com.dropai.rewrite.vo.Result;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.util.List;

@RestController
@RequestMapping("/api/engineering-writing")
public class EngineeringWritingController {
    private final EngineeringWritingService service;
    public EngineeringWritingController(EngineeringWritingService service) { this.service = service; }

    @PostMapping("/generate")
    public Result<DocumentRewriteJobVO> generate(
            @RequestParam String title,
            @RequestParam String outputType,
            @RequestParam(value = "requirements", defaultValue = "") String requirements,
            @RequestParam(value = "files", required = false) List<MultipartFile> files
    ) {
        return Result.success(service.generate(title, outputType, requirements, files == null ? List.of() : files));
    }
}
