package com.dropai.rewrite.controller;

import com.dropai.rewrite.service.EngineeringWritingService;
import com.dropai.rewrite.service.MatrixDesignService;
import com.dropai.rewrite.service.PointService;
import com.dropai.rewrite.service.PointsNotEnoughException;
import com.dropai.rewrite.service.image.DoubaoImageProvider;
import com.dropai.rewrite.service.image.ImageGenerationRequest;
import com.dropai.rewrite.service.image.ImageGenerationResult;
import com.dropai.rewrite.vo.AiProviderStatusVO;
import com.dropai.rewrite.vo.DesignAnalysisVO;
import com.dropai.rewrite.vo.DocumentRewriteJobVO;
import com.dropai.rewrite.vo.Result;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/engineering-writing")
public class EngineeringWritingController {
    private final EngineeringWritingService service;
    private final MatrixDesignService matrixDesignService;
    private final PointService pointService;
    private final DoubaoImageProvider imageProvider;

    public EngineeringWritingController(EngineeringWritingService service, MatrixDesignService matrixDesignService,
                                        PointService pointService, DoubaoImageProvider imageProvider) {
        this.service = service;
        this.matrixDesignService = matrixDesignService;
        this.pointService = pointService;
        this.imageProvider = imageProvider;
    }

    @GetMapping("/ai/status")
    public Result<AiProviderStatusVO> aiStatus() {
        AiProviderStatusVO status = new AiProviderStatusVO();
        status.setProvider("Doubao Ark Chat Completions API");
        status.setModel(matrixDesignService.modelName());
        status.setEndpoint(matrixDesignService.endpoint());
        status.setApiKeyConfigured(matrixDesignService.apiKeyConfigured());
        if (!status.isApiKeyConfigured()) {
            status.setTestStatus("failed");
            status.setTestMessage("DOUBAO_API_KEY is not configured");
            return Result.success(status);
        }
        try {
            status.setTestStatus("success");
            status.setTestMessage("Doubao connection succeeded: " + matrixDesignService.generate("Only output OK", "connection test"));
        } catch (Exception exception) {
            status.setTestStatus("failed");
            status.setTestMessage(exception.getMessage());
        }
        return Result.success(status);
    }

    @GetMapping("/ai/models")
    public Result<List<String>> aiModels() { return Result.success(matrixDesignService.availableModels()); }

    @GetMapping("/image/status")
    public Result<ImageGenerationResult> imageStatus() { return Result.success(imageProvider.health()); }

    @PostMapping("/image/test")
    public Result<ImageGenerationResult> imageTest(@RequestParam(defaultValue = "mechanical product rendering, clean white background") String prompt) {
        ImageGenerationRequest request = new ImageGenerationRequest();
        request.setPrompt(prompt);
        return Result.success(imageProvider.generate(request));
    }

    @PostMapping("/analyze")
    public Result<DesignAnalysisVO> analyze(@RequestParam(value = "title", defaultValue = "") String title,
                                            @RequestParam("files") List<MultipartFile> files) {
        if (files.isEmpty()) throw new IllegalArgumentException("Upload design source files first");
        return Result.success(service.analyze(title, files));
    }

    @PostMapping("/generate")
    public Result<DocumentRewriteJobVO> generate(@RequestParam String title, @RequestParam String outputType,
                                                  @RequestParam(value = "requirements", defaultValue = "") String requirements,
                                                  @RequestParam(value = "files", required = false) List<MultipartFile> files) {
        return Result.success(pointService.chargeAfterSuccess(PointService.DOCX_GENERATE,
                "Engineering document generation", () -> service.generate(title, outputType, requirements,
                        files == null ? List.of() : files)));
    }

    @ExceptionHandler(PointsNotEnoughException.class)
    public Result<?> pointsNotEnough(PointsNotEnoughException exception) {
        return Result.fail("PAY_REQUIRED", "Insufficient points", exception.toResponse());
    }

    @ExceptionHandler(Exception.class)
    public Result<Void> handleException(Exception exception) {
        return Result.fail(exception.getMessage() == null ? "Engineering request failed" : exception.getMessage());
    }
}
