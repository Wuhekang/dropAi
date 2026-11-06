package com.dropai.rewrite.controller;

import com.dropai.rewrite.external.XuejieExternalDocumentRewriteService;
import com.dropai.rewrite.service.PointsNotEnoughException;
import com.dropai.rewrite.vo.DocumentRewriteJobVO;
import com.dropai.rewrite.vo.Result;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/document/rewrite/external")
public class XuejieExternalDocumentRewriteController {
    private final XuejieExternalDocumentRewriteService service;

    public XuejieExternalDocumentRewriteController(XuejieExternalDocumentRewriteService service) {
        this.service = service;
    }

    @PostMapping("/upload")
    public Result<DocumentRewriteJobVO> upload(
            @RequestParam("file") MultipartFile file,
            @RequestParam("mode") String mode,
            @RequestParam("platform") String platform,
            @RequestParam(value = "requestId", required = false) String requestId) {
        return Result.success(service.submit(file, mode, platform, requestId));
    }

    @ExceptionHandler(PointsNotEnoughException.class)
    public Result<?> pointsNotEnough(PointsNotEnoughException exception) {
        return Result.fail("PAY_REQUIRED", "积分不足，需要充值", exception.toResponse());
    }
}
