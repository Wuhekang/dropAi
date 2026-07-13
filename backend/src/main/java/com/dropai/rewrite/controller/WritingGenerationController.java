package com.dropai.rewrite.controller;

import com.dropai.rewrite.auth.AuthContext;
import com.dropai.rewrite.service.PointsNotEnoughException;
import com.dropai.rewrite.service.writing.ChineseReferenceImportService;
import com.dropai.rewrite.service.writing.ReferenceSearchService;
import com.dropai.rewrite.service.writing.WritingGenerationService;
import com.dropai.rewrite.service.writing.WritingProjectService;
import com.dropai.rewrite.vo.Result;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/writing")
public class WritingGenerationController {
    private final WritingProjectService projectService;
    private final ReferenceSearchService referenceSearchService;
    private final WritingGenerationService generationService;
    private final ChineseReferenceImportService importService;

    public WritingGenerationController(WritingProjectService projectService,
                                       ReferenceSearchService referenceSearchService,
                                       WritingGenerationService generationService,
                                       ChineseReferenceImportService importService) {
        this.projectService = projectService;
        this.referenceSearchService = referenceSearchService;
        this.generationService = generationService;
        this.importService = importService;
    }

    @PostMapping("/projects")
    public Result<Map<String, Object>> create(@RequestBody Map<String, Object> request) {
        return Result.success(projectService.create(request));
    }

    @GetMapping("/projects/{id}")
    public Result<Map<String, Object>> get(@PathVariable String id) {
        return Result.success(projectService.detail(id));
    }

    @PutMapping("/projects/{id}")
    public Result<Map<String, Object>> update(@PathVariable String id, @RequestBody Map<String, Object> request) {
        return Result.success(projectService.updateProject(id, request));
    }

    @PostMapping("/projects/{id}/outline/generate")
    public Result<Map<String, Object>> outline(@PathVariable String id) {
        return Result.success(projectService.generateOutline(id));
    }

    @PutMapping("/projects/{id}/outline")
    public Result<Map<String, Object>> saveOutline(@PathVariable String id, @RequestBody Map<String, Object> request) {
        return Result.success(projectService.updateProject(id, request));
    }

    @GetMapping("/projects/{id}/chapters")
    public Result<Object> chapters(@PathVariable String id) {
        return Result.success(projectService.detail(id).get("chapters"));
    }

    @PostMapping("/projects/{id}/chapters")
    public Result<Map<String, Object>> addChapter(@PathVariable String id, @RequestBody Map<String, Object> request) {
        return Result.success(projectService.addChapter(id, request));
    }

    @PutMapping("/projects/{id}/chapters/{chapterId}")
    public Result<Map<String, Object>> updateChapter(@PathVariable String id, @PathVariable String chapterId, @RequestBody Map<String, Object> request) {
        return Result.success(projectService.updateChapter(id, chapterId, request));
    }

    @DeleteMapping("/projects/{id}/chapters/{chapterId}")
    public Result<Map<String, Object>> deleteChapter(@PathVariable String id, @PathVariable String chapterId) {
        return Result.success(projectService.deleteChapter(id, chapterId));
    }

    @PutMapping("/projects/{id}/chapters/reorder")
    public Result<Map<String, Object>> reorderChapters(@PathVariable String id, @RequestBody List<String> ids) {
        return Result.success(projectService.reorderChapters(id, ids));
    }

    @PostMapping("/projects/{id}/chapters/{chapterId}/sections")
    public Result<Map<String, Object>> addSection(@PathVariable String id, @PathVariable String chapterId, @RequestBody Map<String, Object> request) {
        return Result.success(projectService.addSection(id, chapterId, request));
    }

    @PutMapping("/projects/{id}/sections/{sectionId}")
    public Result<Map<String, Object>> updateSection(@PathVariable String id, @PathVariable String sectionId, @RequestBody Map<String, Object> request) {
        return Result.success(projectService.updateSection(id, sectionId, request));
    }

    @DeleteMapping("/projects/{id}/sections/{sectionId}")
    public Result<Map<String, Object>> deleteSection(@PathVariable String id, @PathVariable String sectionId) {
        return Result.success(projectService.deleteSection(id, sectionId));
    }

    @PutMapping("/projects/{id}/sections/reorder")
    public Result<Map<String, Object>> reorderSections(@PathVariable String id, @RequestBody List<String> ids) {
        return Result.success(projectService.reorderSections(id, ids));
    }

    @PostMapping("/projects/{id}/chapters/{chapterId}/charts")
    public Result<Map<String, Object>> addChart(@PathVariable String id, @PathVariable String chapterId, @RequestBody Map<String, Object> request) {
        return Result.success(projectService.addChart(id, chapterId, request));
    }

    @PutMapping("/projects/{id}/charts/{chartId}")
    public Result<Map<String, Object>> updateChart(@PathVariable String id, @PathVariable String chartId, @RequestBody Map<String, Object> request) {
        return Result.success(projectService.updateChart(id, chartId, request));
    }

    @DeleteMapping("/projects/{id}/charts/{chartId}")
    public Result<Map<String, Object>> deleteChart(@PathVariable String id, @PathVariable String chartId) {
        return Result.success(projectService.deleteChart(id, chartId));
    }

    @PostMapping("/projects/{id}/charts/{chartId}/series")
    public Result<Map<String, Object>> addSeries(@PathVariable String id, @PathVariable String chartId, @RequestBody Map<String, Object> request) {
        return Result.success(projectService.addSeries(id, chartId, request));
    }

    @PutMapping("/projects/{id}/chart-series/{seriesId}")
    public Result<Map<String, Object>> updateSeries(@PathVariable String id, @PathVariable String seriesId, @RequestBody Map<String, Object> request) {
        return Result.success(projectService.updateSeries(id, seriesId, request));
    }

    @DeleteMapping("/projects/{id}/chart-series/{seriesId}")
    public Result<Map<String, Object>> deleteSeries(@PathVariable String id, @PathVariable String seriesId) {
        return Result.success(projectService.deleteSeries(id, seriesId));
    }

    @PostMapping("/projects/{id}/chapters/{chapterId}/tables")
    public Result<Map<String, Object>> addTable(@PathVariable String id, @PathVariable String chapterId, @RequestBody Map<String, Object> request) {
        return Result.success(projectService.addTable(id, chapterId, request));
    }

    @PutMapping("/projects/{id}/tables/{tableId}")
    public Result<Map<String, Object>> updateTable(@PathVariable String id, @PathVariable String tableId, @RequestBody Map<String, Object> request) {
        return Result.success(projectService.updateTable(id, tableId, request));
    }

    @DeleteMapping("/projects/{id}/tables/{tableId}")
    public Result<Map<String, Object>> deleteTable(@PathVariable String id, @PathVariable String tableId) {
        return Result.success(projectService.deleteTable(id, tableId));
    }

    @PostMapping("/projects/{id}/references/search")
    public Result<List<Map<String, Object>>> searchReferences(@PathVariable String id) {
        return Result.success(referenceSearchService.searchAndSave(AuthContext.requireUserId(), id, null));
    }

    @GetMapping("/reference-search/providers")
    public Result<Object> referenceSearchProviders() {
        return Result.success(referenceSearchService.providers());
    }

    @PostMapping("/projects/{id}/references/search/chinese")
    public Result<List<Map<String, Object>>> searchChineseReferences(@PathVariable String id, @RequestBody Map<String, Object> request) {
        return Result.success(referenceSearchService.searchAndSaveLanguage(AuthContext.requireUserId(), id, "ZH", request == null ? Map.of() : request));
    }

    @PostMapping("/projects/{id}/references/search-plan")
    public Result<Map<String, Object>> referenceSearchPlan(@PathVariable String id, @RequestBody Map<String, Object> request) {
        return Result.success(referenceSearchService.searchPlan(AuthContext.requireUserId(), id, request == null ? Map.of() : request));
    }

    @PostMapping("/projects/{id}/references/search/english")
    public Result<List<Map<String, Object>>> searchEnglishReferences(@PathVariable String id, @RequestBody Map<String, Object> request) {
        return Result.success(referenceSearchService.searchAndSaveLanguage(AuthContext.requireUserId(), id, "EN", request == null ? Map.of() : request));
    }

    @PostMapping("/projects/{id}/references/search-by-chapter")
    public Result<List<Map<String, Object>>> searchReferencesByChapter(@PathVariable String id, @RequestBody Map<String, Object> request) {
        return Result.success(referenceSearchService.searchAndSave(AuthContext.requireUserId(), id, ((Number) request.getOrDefault("chapterNo", 1)).intValue()));
    }

    @PostMapping("/projects/{id}/references/verify")
    public Result<List<Map<String, Object>>> verifyReferences(@PathVariable String id) {
        return Result.success(referenceSearchService.verifySavedReferences(AuthContext.requireUserId(), id));
    }

    @PostMapping("/projects/{id}/references/{referenceId}/complete-metadata")
    public Result<List<Map<String, Object>>> completeReferenceMetadata(@PathVariable String id, @PathVariable String referenceId) {
        return Result.success(referenceSearchService.completeMetadata(AuthContext.requireUserId(), id, referenceId));
    }

    @PostMapping("/projects/{id}/references/deduplicate")
    public Result<List<Map<String, Object>>> deduplicateReferences(@PathVariable String id) {
        return Result.success(referenceSearchService.deduplicateSavedReferences(AuthContext.requireUserId(), id));
    }

    @PostMapping("/projects/{id}/references/assign-to-chapters")
    public Result<List<Map<String, Object>>> assignReferencesToChapters(@PathVariable String id) {
        return Result.success(referenceSearchService.assignSavedReferencesToChapters(AuthContext.requireUserId(), id));
    }

    @PostMapping("/projects/{id}/references/import")
    public Result<Map<String, Object>> importReferences(@PathVariable String id,
                                                       @RequestParam("file") MultipartFile file,
                                                       @RequestParam(value = "sourcePlatform", defaultValue = "IMPORTED_OTHER") String sourcePlatform) {
        return Result.success(importService.importFile(AuthContext.requireUserId(), id, file, sourcePlatform));
    }

    @GetMapping("/projects/{id}/references/search-logs")
    public Result<List<Map<String, Object>>> referenceSearchLogs(@PathVariable String id) {
        return Result.success(referenceSearchService.searchLogs(AuthContext.requireUserId(), id));
    }

    @GetMapping("/projects/{id}/references")
    public Result<List<Map<String, Object>>> references(@PathVariable String id) {
        return Result.success(referenceSearchService.references(AuthContext.requireUserId(), id));
    }

    @DeleteMapping("/projects/{id}/references/{referenceId}")
    public Result<Void> deleteReference(@PathVariable String id, @PathVariable String referenceId) {
        referenceSearchService.deleteReference(AuthContext.requireUserId(), id, referenceId);
        return Result.success(null);
    }

    @GetMapping("/reference-search/status")
    public Result<Map<String, Object>> referenceSearchStatus() {
        return Result.success(referenceSearchService.status());
    }

    @PostMapping("/projects/{id}/generate")
    public Result<Map<String, Object>> generate(@PathVariable String id) {
        return Result.success(generationService.start(id));
    }

    @PostMapping("/projects/{id}/chapters/{chapterId}/regenerate")
    public Result<Map<String, Object>> regenerateChapter(@PathVariable String id, @PathVariable String chapterId) {
        return Result.success(generationService.start(id));
    }

    @PostMapping("/projects/{id}/charts/{chartId}/regenerate")
    public Result<Map<String, Object>> regenerateChart(@PathVariable String id, @PathVariable String chartId) {
        return Result.success(generationService.start(id));
    }

    @GetMapping("/projects/{id}/progress")
    public Result<Map<String, Object>> progress(@PathVariable String id) {
        return Result.success(generationService.status(AuthContext.requireUserId(), id));
    }

    @GetMapping("/projects/{id}/preview")
    public Result<String> preview(@PathVariable String id) {
        return Result.success(generationService.preview(AuthContext.requireUserId(), id));
    }

    @PostMapping("/projects/{id}/export/docx")
    public Result<Map<String, Object>> exportDocx(@PathVariable String id) {
        return Result.success(generationService.start(id));
    }

    @PostMapping("/projects/{id}/export/pdf")
    public Result<Map<String, Object>> exportPdf(@PathVariable String id) {
        return Result.fail("PDF_EXPORT_NOT_AVAILABLE", "PDF导出暂未启用，DOCX已支持完整导出");
    }

    @GetMapping("/projects/{id}/files")
    public Result<Object> files(@PathVariable String id) {
        return Result.success(projectService.detail(id).get("files"));
    }

    @ExceptionHandler(PointsNotEnoughException.class)
    public Result<?> pointsNotEnough(PointsNotEnoughException exception) {
        return Result.fail("PAY_REQUIRED", "积分不足", exception.toResponse());
    }

    @ExceptionHandler(Exception.class)
    public Result<Void> handle(Exception exception) {
        return Result.fail(exception.getMessage() == null ? "纯文字稿生成失败" : exception.getMessage());
    }
}
