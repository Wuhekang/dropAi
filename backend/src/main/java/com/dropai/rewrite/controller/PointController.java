package com.dropai.rewrite.controller;

import com.dropai.rewrite.dto.FeaturePricingUpdateDTO;
import com.dropai.rewrite.service.PointService;
import com.dropai.rewrite.service.PointsNotEnoughException;
import com.dropai.rewrite.vo.FeaturePricingVO;
import com.dropai.rewrite.vo.PointAccountVO;
import com.dropai.rewrite.vo.PointTransactionVO;
import com.dropai.rewrite.vo.Result;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/points")
public class PointController {
    private final PointService pointService;

    public PointController(PointService pointService) {
        this.pointService = pointService;
    }

    @GetMapping("/me")
    public Result<PointAccountVO> me() {
        return Result.success(pointService.myAccount());
    }

    @GetMapping("/transactions")
    public Result<List<PointTransactionVO>> transactions() {
        return Result.success(pointService.myTransactions());
    }

    @GetMapping("/pricing")
    public Result<List<FeaturePricingVO>> pricing() {
        return Result.success(pointService.listPricing());
    }

    @PutMapping("/pricing/{featureCode}")
    public Result<FeaturePricingVO> updatePricing(@PathVariable String featureCode, @RequestBody FeaturePricingUpdateDTO dto) {
        return Result.success(pointService.updatePricing(featureCode, dto));
    }

    @PostMapping("/recharge")
    public Result<Void> rechargePlaceholder() {
        return Result.fail("POINTS_PAYMENT_NOT_ENABLED", "充值接口已预留，当前暂未接入支付");
    }

    @GetMapping("/orders")
    public Result<List<Object>> ordersPlaceholder() {
        return Result.success(List.of());
    }

    @PostMapping("/payment")
    public Result<Void> paymentPlaceholder() {
        return Result.fail("POINTS_PAYMENT_NOT_ENABLED", "支付接口已预留，当前暂未接入微信支付");
    }

    @ExceptionHandler(PointsNotEnoughException.class)
    public Result<?> pointsNotEnough(PointsNotEnoughException exception) {
        return Result.fail("PAY_REQUIRED", "积分不足，需要充值", exception.toResponse());
    }

    @ExceptionHandler(Exception.class)
    public Result<Void> handle(Exception exception) {
        String message = exception.getMessage();
        return Result.fail(message == null || message.isBlank() ? "积分服务请求失败" : message);
    }
}
