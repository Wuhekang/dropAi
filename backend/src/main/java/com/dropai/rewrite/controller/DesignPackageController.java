package com.dropai.rewrite.controller;

import com.dropai.rewrite.modules.model.DesignProject;
import com.dropai.rewrite.modules.documentParser.DocumentParser;
import com.dropai.rewrite.modules.designAnalyzer.DesignAnalyzer;
import com.dropai.rewrite.modules.parameterEngine.ParameterEngine;
import com.dropai.rewrite.service.DesignPackageService;
import com.dropai.rewrite.vo.DesignPackageVO;
import com.dropai.rewrite.vo.Result;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.util.List;

@RestController
@RequestMapping("/api/design-packages")
public class DesignPackageController {
    private final DesignPackageService service;
    private final DocumentParser documentParser;
    private final DesignAnalyzer designAnalyzer;
    private final ParameterEngine parameterEngine;
    public DesignPackageController(DesignPackageService service, DocumentParser documentParser, DesignAnalyzer designAnalyzer, ParameterEngine parameterEngine) {
        this.service = service; this.documentParser = documentParser; this.designAnalyzer = designAnalyzer; this.parameterEngine = parameterEngine;
    }

    @PostMapping("/generate")
    public Result<DesignPackageVO> generate(@RequestBody DesignProject project) { return Result.success(service.generate(project)); }

    @PostMapping("/analyze")
    public Result<DesignProject> analyze(@RequestParam(defaultValue = "") String title, @RequestParam("files") List<MultipartFile> files) {
        return Result.success(parameterEngine.normalize(designAnalyzer.analyze(title, documentParser.parse(files))));
    }
}
