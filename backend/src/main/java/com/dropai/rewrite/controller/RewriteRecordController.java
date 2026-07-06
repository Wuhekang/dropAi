package com.dropai.rewrite.controller;

import com.dropai.rewrite.dto.AnalyzeTextDTO;
import com.dropai.rewrite.dto.RewriteSubmitDTO;
import com.dropai.rewrite.service.AiRewriteService;
import com.dropai.rewrite.service.PointService;
import com.dropai.rewrite.service.PointsNotEnoughException;
import com.dropai.rewrite.service.RewriteRecordService;
import com.dropai.rewrite.vo.AiAnalyzeVO;
import com.dropai.rewrite.vo.AiProviderStatusVO;
import com.dropai.rewrite.vo.Result;
import com.dropai.rewrite.vo.RewriteResultVO;
import jakarta.validation.Valid;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Validated
@RestController
@RequestMapping("/api/rewrite")
public class RewriteRecordController {

    private final RewriteRecordService rewriteRecordService;
    private final AiRewriteService aiRewriteService;
    private final PointService pointService;

    public RewriteRecordController(RewriteRecordService rewriteRecordService, AiRewriteService aiRewriteService, PointService pointService) {
        this.rewriteRecordService = rewriteRecordService;
        this.aiRewriteService = aiRewriteService;
        this.pointService = pointService;
    }

    @PostMapping("/submit")
    public Result<RewriteResultVO> submit(@Valid @RequestBody RewriteSubmitDTO dto) {
        String featureCode = textFeatureCode(dto.getRewriteType());
        int charCount = dto.getOriginalText() == null ? 0 : dto.getOriginalText().length();
        int costPoints = pointService.usageCostPoints(featureCode, charCount);
        return Result.success(pointService.chargeCustomAfterSuccess(featureCode, costPoints,
                "文本优化：" + charCount + " 字，" + dto.getRewriteType(), () -> rewriteRecordService.submit(dto)));
    }

    @PostMapping("/analyze")
    public Result<AiAnalyzeVO> analyze(@Valid @RequestBody AnalyzeTextDTO dto) {
        return Result.success(rewriteRecordService.analyze(dto.getOriginalText()));
    }

    @GetMapping("/ai/status")
    public Result<AiProviderStatusVO> aiStatus() {
        AiProviderStatusVO vo = new AiProviderStatusVO();
        vo.setProvider(aiRewriteService.providerName());
        vo.setModel(aiRewriteService.modelName());
        vo.setEndpoint(aiRewriteService.endpoint());
        vo.setApiKeyConfigured(aiRewriteService.apiKeyConfigured());

        if (!vo.isApiKeyConfigured()) {
            vo.setTestStatus("failed");
            vo.setTestMessage("未读取到 DOUBAO_API_KEY，请在 Render 的 Environment 中配置后重新部署");
            return Result.success(vo);
        }

        try {
            String sample = aiRewriteService.rewrite("本文研究这个问题。", "学术化润色");
            vo.setTestStatus("success");
            vo.setTestMessage(aiRewriteService.lastCallProvider() + "；返回预览：" + preview(sample));
        } catch (Exception exception) {
            vo.setTestStatus("failed");
            vo.setTestMessage(exception.getMessage());
        }
        return Result.success(vo);
    }

    @GetMapping("/list")
    public Result<List<RewriteResultVO>> list() {
        return Result.success(rewriteRecordService.listRecords());
    }

    @GetMapping("/{id}")
    public Result<RewriteResultVO> detail(@PathVariable Long id) {
        RewriteResultVO vo = rewriteRecordService.detail(id);
        if (vo == null) {
            return Result.fail("记录不存在");
        }
        return Result.success(vo);
    }

    @DeleteMapping("/{id}")
    public Result<Boolean> delete(@PathVariable Long id) {
        return Result.success("删除成功", rewriteRecordService.deleteRecord(id));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public Result<Void> handleValidation(MethodArgumentNotValidException exception) {
        String message = exception.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(error -> error.getDefaultMessage() == null ? "参数错误" : error.getDefaultMessage())
                .orElse("参数错误");
        return Result.fail(message);
    }

    @ExceptionHandler(Exception.class)
    public Result<Void> handleException(Exception exception) {
        return Result.fail(exception.getMessage());
    }

    @ExceptionHandler(PointsNotEnoughException.class)
    public Result<?> pointsNotEnough(PointsNotEnoughException exception) {
        return Result.fail("PAY_REQUIRED", "积分不足，需要充值", exception.toResponse());
    }

    private String preview(String text) {
        if (text == null || text.isBlank()) {
            return "";
        }
        return text.length() > 80 ? text.substring(0, 80) + "..." : text;
    }

    private String textFeatureCode(String rewriteType) {
        String value = rewriteType == null ? "" : rewriteType.toLowerCase();
        if (value.contains("双") || value.contains("double")) {
            return PointService.DOCUMENT_DOUBLE;
        }
        if (value.contains("ai") || value.contains("humanize") || value.contains("精准")) {
            return PointService.DOCUMENT_HUMANIZE;
        }
        return PointService.DOCUMENT_REWRITE;
    }
}
