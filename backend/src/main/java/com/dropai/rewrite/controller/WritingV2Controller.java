package com.dropai.rewrite.controller;

import com.dropai.rewrite.service.PointsNotEnoughException;
import com.dropai.rewrite.service.writing.WritingV2WorkspaceService;
import com.dropai.rewrite.vo.Result;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/writing-v2")
public class WritingV2Controller {
    private final WritingV2WorkspaceService service;

    public WritingV2Controller(WritingV2WorkspaceService service) {
        this.service = service;
    }

    @GetMapping("/templates")
    public Result<List<Map<String, Object>>> templates() {
        return Result.success(service.templates());
    }

    @PostMapping("/projects")
    public Result<Map<String, Object>> create(@RequestBody Map<String, Object> request) {
        return Result.success(service.createProject(request == null ? Map.of() : request));
    }

    @GetMapping("/projects/{id}")
    public Result<Map<String, Object>> detail(@PathVariable String id) {
        return Result.success(service.detail(id));
    }

    @PostMapping("/projects/{id}/settings")
    public Result<Map<String, Object>> settings(@PathVariable String id, @RequestBody Map<String, Object> request) {
        return Result.success(service.saveSettings(id, request == null ? Map.of() : request));
    }

    @PostMapping("/projects/{id}/case-materials")
    public Result<Map<String, Object>> caseMaterials(@PathVariable String id, @RequestBody Map<String, Object> request) {
        return Result.success(service.saveCaseMaterial(id, request == null ? Map.of() : request));
    }

    @PostMapping("/projects/{id}/image-materials")
    public Result<Map<String, Object>> imageMaterials(@PathVariable String id, @RequestParam(value = "files", required = false) List<MultipartFile> files) {
        return Result.success(service.uploadImages(id, files == null ? List.of() : files));
    }

    @PostMapping("/projects/{id}/outline")
    public Result<Map<String, Object>> outline(@PathVariable String id, @RequestBody Map<String, Object> request) {
        return Result.success(service.designOutline(id, request == null ? Map.of() : request));
    }

    @PostMapping("/projects/{id}/chapters/{chapterId}/resources")
    public Result<Map<String, Object>> chapterResources(@PathVariable String id, @PathVariable String chapterId,
                                                         @RequestBody Map<String, Object> request) {
        return Result.success(service.saveChapterResources(id, chapterId, request == null ? Map.of() : request));
    }

    @PostMapping("/projects/{id}/references")
    public Result<Map<String, Object>> references(@PathVariable String id, @RequestBody Map<String, Object> request) {
        return Result.success(service.saveReferences(id, request == null ? Map.of() : request));
    }

    @PostMapping("/projects/{id}/content")
    public Result<Map<String, Object>> content(@PathVariable String id) {
        return Result.success(service.generateContent(id));
    }

    @PostMapping("/projects/{id}/document/docx")
    public Result<Map<String, Object>> docx(@PathVariable String id) {
        return Result.success(service.exportDocx(id));
    }

    @ExceptionHandler(PointsNotEnoughException.class)
    public Result<?> pointsNotEnough(PointsNotEnoughException exception) {
        return Result.fail("PAY_REQUIRED", "积分不足", exception.toResponse());
    }

    @ExceptionHandler(Exception.class)
    public Result<Void> handle(Exception exception) {
        return Result.fail(exception.getMessage() == null ? "文字创作中心处理失败" : exception.getMessage());
    }
}
