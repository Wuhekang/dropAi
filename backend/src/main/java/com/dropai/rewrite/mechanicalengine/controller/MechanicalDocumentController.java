package com.dropai.rewrite.mechanicalengine.controller;

import com.dropai.rewrite.mechanicalengine.domain.DocumentGenerationResult;
import com.dropai.rewrite.mechanicalengine.service.DocumentGenerationService;
import com.dropai.rewrite.vo.Result;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/documents")
public class MechanicalDocumentController {
    private final DocumentGenerationService documents;

    public MechanicalDocumentController(DocumentGenerationService documents) {
        this.documents = documents;
    }

    @PostMapping("/generate")
    public Result<DocumentGenerationResult> generate(@RequestBody DocumentRequest request) {
        return Result.success(documents.start(request.mechanicalResultId(), request.documentType()));
    }

    @GetMapping("/jobs/{jobId}")
    public Result<DocumentGenerationResult> job(@PathVariable String jobId) {
        return Result.success(documents.get(jobId));
    }

    public record DocumentRequest(String mechanicalResultId, String documentType) {}
}
