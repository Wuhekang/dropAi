package com.dropai.rewrite.controller;

import com.dropai.rewrite.service.DocumentRewriteService;
import com.dropai.rewrite.vo.DocumentPrecheckVO;
import com.dropai.rewrite.vo.Result;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/document")
public class DocumentPrecheckController {
    private final DocumentRewriteService documentRewriteService;

    public DocumentPrecheckController(DocumentRewriteService documentRewriteService) {
        this.documentRewriteService = documentRewriteService;
    }

    @PostMapping("/precheck")
    public Result<DocumentPrecheckVO> precheck(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "mode", defaultValue = "FULL_AI_REDUCE") String mode
    ) {
        return Result.success(documentRewriteService.precheck(file, mode));
    }
}
