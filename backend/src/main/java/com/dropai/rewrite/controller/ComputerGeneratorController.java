package com.dropai.rewrite.controller;

import com.dropai.rewrite.service.ComputerGeneratorService;
import com.dropai.rewrite.service.ComputerGeneratorService.ComputerAnalyzeVO;
import com.dropai.rewrite.service.ComputerGeneratorService.ComputerGenerationConfig;
import com.dropai.rewrite.service.ComputerGeneratorService.ComputerJobVO;
import com.dropai.rewrite.service.ComputerGeneratorService.CreateComputerJobRequest;
import com.dropai.rewrite.service.PointsNotEnoughException;
import com.dropai.rewrite.vo.Result;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.List;

@RestController
@RequestMapping("/api/computer-generator")
public class ComputerGeneratorController {
    private final ComputerGeneratorService service;

    public ComputerGeneratorController(ComputerGeneratorService service) {
        this.service = service;
    }

    @PostMapping("/create")
    public Result<ComputerJobVO> create(@RequestBody CreateComputerJobRequest request) {
        return Result.success(service.create(request));
    }

    @PostMapping("/analyze")
    public Result<ComputerAnalyzeVO> analyze(@RequestParam("files") List<MultipartFile> files) {
        return Result.success(service.analyze(files));
    }

    @PostMapping("/upload")
    public Result<ComputerJobVO> upload(@RequestParam String jobId,
                                        @RequestParam(value = "files", required = false) List<MultipartFile> files) {
        return Result.success(service.upload(jobId, files));
    }

    @PostMapping("/start/{jobId}")
    public Result<ComputerJobVO> start(@PathVariable String jobId,
                                       @RequestBody(required = false) ComputerGenerationConfig config) {
        return Result.success(service.start(jobId, config));
    }

    @GetMapping("/status/{jobId}")
    public Result<ComputerJobVO> status(@PathVariable String jobId) {
        return Result.success(service.status(jobId));
    }

    @GetMapping("/result/{jobId}")
    public Result<ComputerJobVO> result(@PathVariable String jobId) {
        return Result.success(service.result(jobId));
    }

    @GetMapping("/history")
    public Result<List<ComputerJobVO>> history() {
        return Result.success(service.history());
    }

    @DeleteMapping("/{jobId}")
    public Result<Void> delete(@PathVariable String jobId) {
        service.delete(jobId);
        return Result.success(null);
    }

    @GetMapping("/preview/{jobId}")
    public Result<String> preview(@PathVariable String jobId) {
        return Result.success(service.result(jobId).activePreviewUrl());
    }

    @GetMapping("/download/{jobId}")
    public ResponseEntity<Resource> download(@PathVariable String jobId) {
        Resource resource = service.download(jobId);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment()
                                .filename("computer-project.zip", StandardCharsets.UTF_8)
                                .build().toString())
                .body(resource);
    }

    @GetMapping("/download-file/{jobId}")
    public ResponseEntity<Resource> downloadFile(@PathVariable String jobId, @RequestParam String fileName) {
        Resource resource = service.downloadFile(jobId, fileName);
        String name = fileName.contains("/") ? fileName.substring(fileName.lastIndexOf('/') + 1) : fileName;
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(name, StandardCharsets.UTF_8).build().toString())
                .body(resource);
    }

    @GetMapping("/preview-content/{previewId}/{fileName}")
    public ResponseEntity<Resource> previewContent(@PathVariable String previewId, @PathVariable String fileName) {
        return ResponseEntity.ok()
                .contentType(MediaType.TEXT_HTML)
                .body(service.previewFile(previewId, fileName));
    }

    @ExceptionHandler(PointsNotEnoughException.class)
    public Result<?> pointsNotEnough(PointsNotEnoughException exception) {
        return Result.fail("PAY_REQUIRED", "积分不足，需要充值", exception.toResponse());
    }

    @ExceptionHandler(Exception.class)
    public Result<Void> handle(Exception exception) {
        String message = exception.getMessage();
        return Result.fail(message == null || message.isBlank() ? "计算机程序包生成失败" : message);
    }
}
