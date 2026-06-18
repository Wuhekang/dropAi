package com.dropai.rewrite.controller;

import com.dropai.rewrite.service.DocumentRewriteService;
import com.dropai.rewrite.service.PointService;
import com.dropai.rewrite.service.PointsNotEnoughException;
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
    private final PointService pointService;

    public DocumentRewriteController(DocumentRewriteService documentRewriteService, PointService pointService) {
        this.documentRewriteService = documentRewriteService;
        this.pointService = pointService;
    }

    @PostMapping("/upload")
    public Result<DocumentRewriteJobVO> upload(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "mode", defaultValue = "FULL_AI_REDUCE") String mode,
            @RequestParam(value = "platform", defaultValue = "GENERAL") String platform
    ) {
        return Result.success(pointService.chargeAfterSuccess(PointService.DOCX_GENERATE,
                "提交文档改写任务", () -> documentRewriteService.submit(file, mode, platform)));
    }

    @GetMapping("/job/{jobId}")
    public Result<DocumentRewriteJobVO> job(
            @PathVariable String jobId,
            @RequestParam(value = "includeParagraphs", defaultValue = "false") boolean includeParagraphs
    ) {
        return Result.success(snapshot(documentRewriteService.getJob(jobId), includeParagraphs));
    }

    @GetMapping("/jobs")
    public Result<List<DocumentRewriteJobVO>> jobs() {
        return Result.success(documentRewriteService.listJobs().stream()
                .map(job -> snapshot(job, false))
                .toList());
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

    private DocumentRewriteJobVO snapshot(DocumentRewriteJobVO source, boolean includeParagraphs) {
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
        if (includeParagraphs) {
            target.setParagraphs(source.getParagraphs());
        }
        return target;
    }

    @org.springframework.web.bind.annotation.ExceptionHandler(PointsNotEnoughException.class)
    public Result<Void> pointsNotEnough(PointsNotEnoughException exception) {
        return Result.fail("POINTS_NOT_ENOUGH", exception.getMessage());
    }
}
