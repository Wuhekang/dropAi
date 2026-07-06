package com.dropai.rewrite.controller;

import com.dropai.rewrite.modules.documentParser.DocumentParser;
import com.dropai.rewrite.service.DocumentRewriteService;
import com.dropai.rewrite.vo.DocumentExtractVO;
import com.dropai.rewrite.vo.DocumentPrecheckVO;
import com.dropai.rewrite.vo.Result;

import java.util.List;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/document")
public class DocumentPrecheckController {
    private final DocumentRewriteService documentRewriteService;
    private final DocumentParser documentParser;

    public DocumentPrecheckController(DocumentRewriteService documentRewriteService, DocumentParser documentParser) {
        this.documentRewriteService = documentRewriteService;
        this.documentParser = documentParser;
    }

    @PostMapping("/precheck")
    public Result<DocumentPrecheckVO> precheck(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "mode", defaultValue = "FULL_AI_REDUCE") String mode
    ) {
        return Result.success(documentRewriteService.precheck(file, mode));
    }

    @PostMapping("/extract")
    public Result<DocumentExtractVO> extract(@RequestParam("file") MultipartFile file) {
        DocumentParser.ParsedDocument parsed = documentParser.parse(List.of(file), List.of("TASK_BOOK")).get(0);
        return Result.success(new DocumentExtractVO(
                parsed.fileName(),
                parsed.text(),
                parsed.textReadable(),
                parsed.textReadable() ? "文档解析完成" : parsed.failureReason()
        ));
    }
}
