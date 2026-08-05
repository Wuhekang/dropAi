package com.dropai.rewrite.mechanicalengine.controller;

import com.dropai.rewrite.mechanicalengine.domain.MechanicalJobSnapshot;
import com.dropai.rewrite.mechanicalengine.service.MechanicalJobService;
import com.dropai.rewrite.vo.Result;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.PostMapping;

@RestController
@RequestMapping("/api/mechanical/jobs")
public class MechanicalJobController {
    private final MechanicalJobService jobs;

    public MechanicalJobController(MechanicalJobService jobs) {
        this.jobs = jobs;
    }

    @GetMapping("/{jobId}")
    public Result<MechanicalJobSnapshot> get(@PathVariable String jobId) {
        return Result.success(jobs.get(jobId));
    }

    @PostMapping("/{jobId}/continue")
    public Result<MechanicalJobSnapshot> resume(@PathVariable String jobId) {
        return Result.success(jobs.resume(jobId));
    }
}
