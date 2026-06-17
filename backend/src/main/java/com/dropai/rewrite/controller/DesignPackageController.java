package com.dropai.rewrite.controller;

import com.dropai.rewrite.modules.model.DesignProject;
import com.dropai.rewrite.modules.documentParser.DocumentParser;
import com.dropai.rewrite.modules.designAnalyzer.DesignAnalyzer;
import com.dropai.rewrite.modules.designPipeline.TaskDrivenDesignPipeline;
import com.dropai.rewrite.service.DesignPackageService;
import com.dropai.rewrite.vo.DesignAnalysisResultVO;
import com.dropai.rewrite.vo.DesignPackageVO;
import com.dropai.rewrite.vo.Result;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.util.List;

@RestController
@RequestMapping("/api/design-packages")
public class DesignPackageController {
    private final DesignPackageService service;
    private final DocumentParser documentParser;
    private final DesignAnalyzer designAnalyzer;
    private final TaskDrivenDesignPipeline designPipeline;
    public DesignPackageController(DesignPackageService service, DocumentParser documentParser, DesignAnalyzer designAnalyzer,
                                   TaskDrivenDesignPipeline designPipeline) {
        this.service = service; this.documentParser = documentParser; this.designAnalyzer = designAnalyzer;
        this.designPipeline = designPipeline;
    }

    @PostMapping("/generate")
    public Result<DesignPackageVO> generate(@RequestBody DesignProject project) { return Result.success(service.generate(project)); }

    @PostMapping("/analyze")
    public Result<DesignAnalysisResultVO> analyze(@RequestParam(defaultValue = "") String title, @RequestParam("files") List<MultipartFile> files) {
        List<DocumentParser.ParsedDocument> documents = documentParser.parse(files);
        DesignProject project = designPipeline.analyzeNewTask(designAnalyzer.analyze(title, documents));
        return Result.success(DesignAnalysisResultVO.of(project, documents));
    }

    @ExceptionHandler(Exception.class)
    public Result<Void> handleException(Exception exception) {
        String message = exception.getMessage();
        return Result.fail(message == null || message.isBlank() ? "成果生成失败，请稍后重试" : message);
    }
}
