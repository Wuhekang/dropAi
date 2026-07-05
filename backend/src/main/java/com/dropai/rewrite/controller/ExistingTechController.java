package com.dropai.rewrite.controller;

import com.dropai.rewrite.service.ExistingTechService;
import com.dropai.rewrite.service.PointService;
import com.dropai.rewrite.service.PointsNotEnoughException;
import com.dropai.rewrite.vo.Result;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.Map;

@RestController
@RequestMapping("/api/existing-tech")
public class ExistingTechController {
    private final ExistingTechService service;
    private final PointService pointService;

    public ExistingTechController(ExistingTechService service, PointService pointService) {
        this.service = service;
        this.pointService = pointService;
    }

    @PostMapping("/upload")
    public Result<ExistingTechService.UploadedFileInfo> upload(@RequestParam("file") MultipartFile file) {
        return Result.success(service.upload(file));
    }

    @PostMapping("/task")
    public Result<ExistingTechService.ExistingTechTask> submitTask(@RequestBody Map<String, Object> params) {
        return Result.success(pointService.chargeAfterSuccess(PointService.DOCX_GENERATE,
                "现有技术文本处理", () -> service.submit(params)));
    }

    @GetMapping("/task/{taskId}")
    public Result<ExistingTechService.ExistingTechTask> getTask(@PathVariable String taskId) {
        return Result.success(service.status(taskId));
    }

    @GetMapping("/result/{taskId}")
    public Result<ExistingTechService.ExistingTechResult> getResult(@PathVariable String taskId) {
        return Result.success(service.result(taskId));
    }

    @GetMapping("/download/{taskId}")
    public ResponseEntity<byte[]> download(@PathVariable String taskId) {
        byte[] bytes = service.download(taskId);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("text/plain;charset=UTF-8"))
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename("existing-tech-result.txt", StandardCharsets.UTF_8).build().toString())
                .contentLength(bytes.length)
                .body(bytes);
    }

    @org.springframework.web.bind.annotation.ExceptionHandler(PointsNotEnoughException.class)
    public Result<?> pointsNotEnough(PointsNotEnoughException exception) {
        return Result.fail("PAY_REQUIRED", "积分不足，需要充值", exception.toResponse());
    }
}
