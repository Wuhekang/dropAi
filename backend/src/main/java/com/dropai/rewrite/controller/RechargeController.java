package com.dropai.rewrite.controller;

import com.dropai.rewrite.dto.RechargeOrderCreateDTO;
import com.dropai.rewrite.service.RechargeService;
import com.dropai.rewrite.vo.RechargeOrderVO;
import com.dropai.rewrite.vo.RechargePlanVO;
import com.dropai.rewrite.vo.Result;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/recharge")
public class RechargeController {
    private final RechargeService rechargeService;

    public RechargeController(RechargeService rechargeService) {
        this.rechargeService = rechargeService;
    }

    @GetMapping("/plans")
    public Result<List<RechargePlanVO>> plans() {
        return Result.success(rechargeService.plans());
    }

    @PostMapping("/orders")
    public Result<RechargeOrderVO> create(@RequestBody RechargeOrderCreateDTO dto) {
        return Result.success(rechargeService.createOrder(dto));
    }

    @GetMapping("/orders")
    public Result<List<RechargeOrderVO>> orders() {
        return Result.success(rechargeService.myOrders());
    }

    @PostMapping("/orders/{orderNo}/mock-pay")
    public Result<RechargeOrderVO> mockPay(@PathVariable String orderNo) {
        return Result.success("\u652f\u4ed8\u6210\u529f\uff0c\u79ef\u5206\u5df2\u5230\u8d26", rechargeService.mockPay(orderNo));
    }
}
