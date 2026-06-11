package com.dropai.rewrite.controller;

import com.dropai.rewrite.service.EngineeringWritingService;
import com.dropai.rewrite.service.MatrixDesignService;
import com.dropai.rewrite.vo.AiProviderStatusVO;
import com.dropai.rewrite.vo.DesignAnalysisVO;
import com.dropai.rewrite.vo.DocumentRewriteJobVO;
import com.dropai.rewrite.vo.Result;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.util.List;

@RestController
@RequestMapping("/api/engineering-writing")
public class EngineeringWritingController {
    private final EngineeringWritingService service;
    private final MatrixDesignService matrixDesignService;
    public EngineeringWritingController(EngineeringWritingService service, MatrixDesignService matrixDesignService) {
        this.service = service;
        this.matrixDesignService = matrixDesignService;
    }

    @GetMapping("/ai/status")
    public Result<AiProviderStatusVO> aiStatus() {
        AiProviderStatusVO status = new AiProviderStatusVO();
        status.setProvider("万量矩阵 Chat Completions API");
        status.setModel(matrixDesignService.modelName());
        status.setEndpoint(matrixDesignService.endpoint());
        status.setApiKeyConfigured(matrixDesignService.apiKeyConfigured());
        if (!status.isApiKeyConfigured()) {
            status.setTestStatus("failed");
            status.setTestMessage("未配置 MATRIX_API_KEY");
            return Result.success(status);
        }
        try {
            status.setTestStatus("success");
            status.setTestMessage("万量矩阵连接成功；返回：" + matrixDesignService.generate("只输出 OK", "连接测试"));
        } catch (Exception exception) {
            status.setTestStatus("failed");
            status.setTestMessage(exception.getMessage());
        }
        return Result.success(status);
    }

    @PostMapping("/analyze")
    public Result<DesignAnalysisVO> analyze(
            @RequestParam(value = "title", defaultValue = "") String title,
            @RequestParam("files") List<MultipartFile> files
    ) {
        if (files.isEmpty()) throw new IllegalArgumentException("请先上传任务书、开题报告或设计资料");
        return Result.success(service.analyze(title, files));
    }

    @PostMapping("/generate")
    public Result<DocumentRewriteJobVO> generate(
            @RequestParam String title,
            @RequestParam String outputType,
            @RequestParam(value = "requirements", defaultValue = "") String requirements,
            @RequestParam(value = "files", required = false) List<MultipartFile> files
    ) {
        return Result.success(service.generate(title, outputType, requirements, files == null ? List.of() : files));
    }

    @ExceptionHandler(Exception.class)
    public Result<Void> handleException(Exception exception) {
        return Result.fail(exception.getMessage() == null ? "设计生成请求失败" : exception.getMessage());
    }
}
