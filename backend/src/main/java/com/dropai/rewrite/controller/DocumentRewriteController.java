package com.dropai.rewrite.controller;

import com.dropai.rewrite.service.DocumentRewriteService;
import com.dropai.rewrite.vo.DocumentRewriteJobVO;
import com.dropai.rewrite.vo.Result;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;

@RestController
@RequestMapping("/api/document/rewrite")
public class DocumentRewriteController {

    private final DocumentRewriteService documentRewriteService;

    public DocumentRewriteController(DocumentRewriteService documentRewriteService) {
        this.documentRewriteService = documentRewriteService;
    }

    @PostMapping("/upload")
    public Result<DocumentRewriteJobVO> upload(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "mode", defaultValue = "FULL_AI_REDUCE") String mode
    ) {
        return Result.success(documentRewriteService.submit(file, mode));
    }

    @GetMapping("/job/{jobId}")
    public Result<DocumentRewriteJobVO> job(@PathVariable String jobId) {
        return Result.success(documentRewriteService.getJob(jobId));
    }

    @GetMapping("/jobs")
    public Result<List<DocumentRewriteJobVO>> jobs() {
        return Result.success(documentRewriteService.listJobs());
    }

    @GetMapping("/download/{jobId}")
    public ResponseEntity<Resource> download(@PathVariable String jobId) {
        String fileName = URLEncoder.encode(documentRewriteService.downloadFileName(jobId), StandardCharsets.UTF_8)
                .replace("+", "%20");
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''" + fileName)
                .body(documentRewriteService.download(jobId));
    }
}
