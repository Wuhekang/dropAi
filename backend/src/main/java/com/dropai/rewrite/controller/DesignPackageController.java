package com.dropai.rewrite.controller;

import com.dropai.rewrite.modules.designAnalyzer.DesignAnalyzer;
import com.dropai.rewrite.modules.designPipeline.TaskDrivenDesignPipeline;
import com.dropai.rewrite.modules.documentParser.DocumentParser;
import com.dropai.rewrite.modules.model.DesignProject;
import com.dropai.rewrite.service.DesignPackageService;
import com.dropai.rewrite.service.DesignPackageJobService;
import com.dropai.rewrite.service.PointsNotEnoughException;
import com.dropai.rewrite.service.ai.DoubaoMechanicalVisionService;
import com.dropai.rewrite.service.ai.MechanicalVisionAnalysisResult;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.dropai.rewrite.vo.DesignAnalysisResultVO;
import com.dropai.rewrite.vo.DesignPackageJobVO;
import com.dropai.rewrite.vo.DesignPackageVO;
import com.dropai.rewrite.vo.Result;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.List;

@RestController
@RequestMapping("/api/design-packages")
public class DesignPackageController {
    private final DesignPackageService service;
    private final DesignPackageJobService jobService;
    private final DocumentParser documentParser;
    private final DesignAnalyzer designAnalyzer;
    private final TaskDrivenDesignPipeline designPipeline;
    private final DoubaoMechanicalVisionService mechanicalVisionService;
    private final ObjectMapper objectMapper;

    public DesignPackageController(DesignPackageService service, DesignPackageJobService jobService,
                                   DocumentParser documentParser, DesignAnalyzer designAnalyzer,
                                   TaskDrivenDesignPipeline designPipeline,
                                   DoubaoMechanicalVisionService mechanicalVisionService,
                                   ObjectMapper objectMapper) {
        this.service = service;
        this.jobService = jobService;
        this.documentParser = documentParser;
        this.designAnalyzer = designAnalyzer;
        this.designPipeline = designPipeline;
        this.mechanicalVisionService = mechanicalVisionService;
        this.objectMapper = objectMapper;
    }

    @PostMapping("/generate")
    public Result<DesignPackageVO> generate(@RequestBody DesignProject project) {
        return Result.success(service.generate(project));
    }

    @PostMapping("/jobs")
    public Result<DesignPackageJobVO> createJob(@RequestBody DesignProject project) {
        return Result.success(jobService.create(project));
    }

    @GetMapping("/jobs/{jobId}")
    public Result<DesignPackageJobVO> getJob(@PathVariable String jobId) {
        return Result.success(jobService.get(jobId));
    }

    @PostMapping("/analyze")
    public Result<DesignAnalysisResultVO> analyze(@RequestParam(defaultValue = "") String title,
                                                  @RequestParam(defaultValue = "graduation") String designDepth,
                                                  @RequestParam("files") List<MultipartFile> files,
                                                  @RequestParam(value = "types", required = false) List<String> types) {
        List<DocumentParser.ParsedDocument> documents = documentParser.parse(files, types == null ? List.of() : types);
        List<DocumentParser.ParsedDocument> documentsWithVision = new ArrayList<>(documents);
        documentsWithVision.addAll(analyzeReferenceImages(files));
        List<DocumentParser.ParsedDocument> generationSources = documentsWithVision.stream()
                .filter(document -> documentParser.allowedForMechanicalDesign(document.type()))
                .toList();
        if (generationSources.stream().noneMatch(document -> "TASK_BOOK".equals(document.type()))) {
            throw new IllegalArgumentException("机械设计模块必须上传任务书；开题报告为可选补充资料。");
        }
        if (generationSources.stream().filter(document -> "TASK_BOOK".equals(document.type()))
                .noneMatch(DocumentParser.ParsedDocument::textReadable)) {
            throw new IllegalArgumentException("任务书未读取到可用文字内容，请上传可读取的任务书文档。");
        }
        DesignProject analyzed = designAnalyzer.analyze(title, generationSources);
        analyzed.setDesignDepth("engineering".equalsIgnoreCase(designDepth) ? "engineering" : "graduation");
        DesignProject project = designPipeline.analyzeNewTask(analyzed);
        return Result.success(DesignAnalysisResultVO.of(project, documentsWithVision));
    }

    @ExceptionHandler(PointsNotEnoughException.class)
    public Result<?> pointsNotEnough(PointsNotEnoughException exception) {
        return Result.fail("PAY_REQUIRED", "积分不足，需要充值", exception.toResponse());
    }

    @ExceptionHandler(Exception.class)
    public Result<Void> handleException(Exception exception) {
        String message = exception.getMessage();
        return Result.fail(message == null || message.isBlank() ? "成果生成失败，请稍后重试" : message);
    }

    private List<DocumentParser.ParsedDocument> analyzeReferenceImages(List<MultipartFile> files) {
        List<DocumentParser.ParsedDocument> result = new ArrayList<>();
        if (files == null) {
            return result;
        }
        for (MultipartFile file : files) {
            String fileName = file.getOriginalFilename() == null ? "image" : file.getOriginalFilename();
            if (!isSupportedImage(fileName)) {
                continue;
            }
            try {
                DoubaoMechanicalVisionService.MechanicalVisionResponse response = mechanicalVisionService.analyze(
                        file.getBytes(),
                        fileName,
                        "Analyze this uploaded mechanical reference image for the DropAI mechanical design workflow. Return strict JSON only."
                );
                MechanicalVisionAnalysisResult analysis = response.result();
                String text = "Mechanical vision model: " + response.model()
                        + "\nRequest contains image: " + response.requestContainsImage()
                        + "\nMechanical vision JSON:\n" + objectMapper.writeValueAsString(analysis);
                result.add(new DocumentParser.ParsedDocument(
                        fileName + ".mechanical-vision.json",
                        "PROPOSAL",
                        text,
                        "success",
                        true,
                        ""
                ));
            } catch (Exception exception) {
                result.add(new DocumentParser.ParsedDocument(
                        fileName,
                        "IMAGE_REFERENCE",
                        "",
                        "failed",
                        false,
                        "Mechanical vision analysis failed: " + compact(exception.getMessage())
                ));
            }
        }
        return result;
    }

    private boolean isSupportedImage(String fileName) {
        String lower = fileName == null ? "" : fileName.toLowerCase();
        return lower.endsWith(".png") || lower.endsWith(".jpg") || lower.endsWith(".jpeg") || lower.endsWith(".webp");
    }

    private String compact(String value) {
        if (value == null || value.isBlank()) {
            return "none";
        }
        String compacted = value.replaceAll("\\s+", " ").trim();
        return compacted.length() > 260 ? compacted.substring(0, 260) + "..." : compacted;
    }
}
