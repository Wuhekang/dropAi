package com.dropai.rewrite.controller;

import com.dropai.rewrite.modules.stepExportEngine.CadWorkerHealthService;
import com.dropai.rewrite.vo.Result;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/mechanical/cad-worker")
public class MechanicalCadWorkerController {
    private final CadWorkerHealthService healthService;

    public MechanicalCadWorkerController(CadWorkerHealthService healthService) {
        this.healthService = healthService;
    }

    @GetMapping("/health")
    public Result<Map<String, Object>> health() {
        return Result.success(healthService.health());
    }
}
