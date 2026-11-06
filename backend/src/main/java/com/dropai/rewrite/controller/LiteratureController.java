package com.dropai.rewrite.controller;

import com.dropai.rewrite.service.PointsNotEnoughException;
import com.dropai.rewrite.service.writing.LiteratureSearchService;
import com.dropai.rewrite.vo.Result;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/literature")
public class LiteratureController {
    private final LiteratureSearchService service;

    public LiteratureController(LiteratureSearchService service) {
        this.service = service;
    }

    @PostMapping("/search")
    public Result<Map<String, Object>> search(@RequestBody Map<String, Object> request) {
        return Result.success(service.search(request == null ? Map.of() : request));
    }

    @ExceptionHandler(PointsNotEnoughException.class)
    public Result<?> pointsNotEnough(PointsNotEnoughException exception) {
        return Result.fail("PAY_REQUIRED", "积分不足", exception.toResponse());
    }

    @ExceptionHandler(Exception.class)
    public Result<Void> handle(Exception exception) {
        return Result.fail(exception.getMessage() == null ? "文献搜索失败" : exception.getMessage());
    }
}
