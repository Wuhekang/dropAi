package com.dropai.rewrite.controller;

import com.dropai.rewrite.service.DocumentRewriteService;
import com.dropai.rewrite.vo.DocumentRewriteJobVO;
import com.dropai.rewrite.vo.Result;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;

@RestController
@RequestMapping("/api/documents")
public class DocumentLibraryController {
    private final DocumentRewriteService documentService;
    public DocumentLibraryController(DocumentRewriteService documentService) { this.documentService = documentService; }

    @GetMapping
    public Result<List<DocumentRewriteJobVO>> documents() {
        return Result.success(documentService.listJobs().stream().map(this::summary).toList());
    }

    @GetMapping("/{jobId}/download")
    public ResponseEntity<Resource> download(@PathVariable String jobId) {
        String fileName = URLEncoder.encode(documentService.downloadFileName(jobId), StandardCharsets.UTF_8)
                .replace("+", "%20");
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''" + fileName)
                .body(documentService.download(jobId));
    }

    private DocumentRewriteJobVO summary(DocumentRewriteJobVO source) {
        DocumentRewriteJobVO target = new DocumentRewriteJobVO();
        target.setJobId(source.getJobId());
        target.setFileName(source.getFileName());
        target.setSourceFeature(source.getSourceFeature());
        target.setMode(source.getMode());
        target.setModeName(source.getModeName());
        target.setPlatform(source.getPlatform());
        target.setPlatformName(source.getPlatformName());
        target.setStatus(source.getStatus());
        target.setTotalParagraphs(source.getTotalParagraphs());
        target.setProcessedParagraphs(source.getProcessedParagraphs());
        target.setRewrittenParagraphs(source.getRewrittenParagraphs());
        target.setMessage(source.getMessage());
        target.setDownloadUrl(source.getDownloadUrl());
        target.setCreatedAt(source.getCreatedAt());
        target.setUpdatedAt(source.getUpdatedAt());
        return target;
    }
}
