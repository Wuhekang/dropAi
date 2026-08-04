package com.dropai.rewrite.mechanicalengine.controller;

import com.dropai.rewrite.vo.Result;
import com.dropai.rewrite.mechanicalengine.domain.MechanicalProject;
import com.dropai.rewrite.mechanicalengine.plugin.EngineeringPluginManager;
import com.dropai.rewrite.mechanicalengine.service.MechanicalChiefEngineer;
import com.dropai.rewrite.mechanicalengine.service.MechanicalEngineService;
import com.dropai.rewrite.modules.documentParser.DocumentParser;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/mechanical/projects")
public class MechanicalEngineController {
    private final MechanicalChiefEngineer chiefEngineer;
    private final MechanicalEngineService engineService;
    private final EngineeringPluginManager pluginManager;
    private final DocumentParser documentParser;

    public MechanicalEngineController(MechanicalChiefEngineer chiefEngineer, MechanicalEngineService engineService,
                                      EngineeringPluginManager pluginManager, DocumentParser documentParser) {
        this.chiefEngineer = chiefEngineer;
        this.engineService = engineService;
        this.pluginManager = pluginManager;
        this.documentParser = documentParser;
    }

    @PostMapping("/design")
    public Result<MechanicalProject> design(@RequestBody MechanicalRequest request) {
        return Result.success(chiefEngineer.design(request.requirement()));
    }

    @PostMapping("/execute")
    public Result<MechanicalProject> execute(@RequestBody MechanicalRequest request) {
        return Result.success(engineService.execute(request.requirement()));
    }

    @GetMapping("/tools")
    public Result<Map<String, String>> tools() { return Result.success(pluginManager.registry()); }

    @PostMapping("/requirement/extract")
    public Result<RequirementDocument> extractRequirement(@RequestParam("file") MultipartFile file) {
        DocumentParser.ParsedDocument parsed = documentParser.parse(java.util.List.of(file), java.util.List.of("TASK_BOOK")).get(0);
        if (!parsed.textReadable()) throw new IllegalArgumentException(parsed.failureReason());
        return Result.success(new RequirementDocument(parsed.fileName(), parsed.text()));
    }

    public record MechanicalRequest(String requirement) {}
    public record RequirementDocument(String fileName, String text) {}
}
