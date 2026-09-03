package com.dropai.rewrite.controller;

import com.dropai.rewrite.service.wordformat.WordFormatJobService;
import com.dropai.rewrite.vo.Result;
import com.dropai.rewrite.vo.WordFormatJobVO;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.Map;

@RestController
@RequestMapping("/api/word-format")
public class WordFormatController {
    private static final MediaType DOCX = MediaType.parseMediaType(
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
    );

    private final WordFormatJobService service;

    public WordFormatController(WordFormatJobService service) {
        this.service = service;
    }

    @PostMapping(value = "/jobs", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Result<WordFormatJobVO> create(
            @RequestParam("template") MultipartFile template,
            @RequestParam("source") MultipartFile source,
            @RequestParam(value = "instructions", required = false, defaultValue = "") String instructions,
            @RequestParam(value = "useDoubao", required = false, defaultValue = "false") boolean useDoubao
    ) {
        return Result.success(service.submit(template, source, instructions, useDoubao));
    }

    @GetMapping("/jobs/{id}")
    public Result<WordFormatJobVO> get(@PathVariable String id) {
        return Result.success(service.get(id));
    }

    @PostMapping(value = "/jobs/{id}/confirm", consumes = MediaType.APPLICATION_JSON_VALUE)
    public Result<WordFormatJobVO> confirm(@PathVariable String id, @RequestBody Map<String, Object> editableRules) {
        return Result.success(service.confirm(id, editableRules));
    }

    @GetMapping("/jobs/{id}/download")
    public ResponseEntity<?> download(@PathVariable String id) {
        WordFormatJobService.DownloadFile file = service.download(id);
        return ResponseEntity.ok()
                .contentType(DOCX)
                .contentLength(file.size())
                .cacheControl(CacheControl.noStore())
                .header("X-Content-Type-Options", "nosniff")
                .header(
                        HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment()
                                .filename(file.fileName(), StandardCharsets.UTF_8)
                                .build()
                                .toString()
                )
                .body(file.resource());
    }

    @ExceptionHandler(WordFormatJobService.JobNotFoundException.class)
    public ResponseEntity<Result<Void>> notFound(WordFormatJobService.JobNotFoundException exception) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new Result<>(404, exception.getMessage(), null));
    }

    @ExceptionHandler(WordFormatJobService.JobNotReadyException.class)
    public ResponseEntity<Result<Void>> notReady(WordFormatJobService.JobNotReadyException exception) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new Result<>(409, exception.getMessage(), null));
    }

    @ExceptionHandler(WordFormatJobService.JobQueueFullException.class)
    public ResponseEntity<Result<Void>> queueFull(WordFormatJobService.JobQueueFullException exception) {
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .body(new Result<>(429, exception.getMessage(), null));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Result<Void>> badRequest(IllegalArgumentException exception) {
        return ResponseEntity.badRequest()
                .body(new Result<>(400, exception.getMessage(), null));
    }
}
