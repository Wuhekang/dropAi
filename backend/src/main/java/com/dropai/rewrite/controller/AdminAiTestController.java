package com.dropai.rewrite.controller;

import com.dropai.rewrite.service.ai.DoubaoMechanicalVisionService;
import com.dropai.rewrite.service.ai.MechanicalVisionAnalysisResult;
import com.dropai.rewrite.vo.Result;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/ai/test")
public class AdminAiTestController {
    private final DoubaoMechanicalVisionService mechanicalVisionService;

    public AdminAiTestController(DoubaoMechanicalVisionService mechanicalVisionService) {
        this.mechanicalVisionService = mechanicalVisionService;
    }

    @PostMapping("/mechanical-vision")
    public Result<Map<String, Object>> testMechanicalVision(
            @RequestParam("image") MultipartFile image,
            @RequestParam(value = "prompt", required = false) String prompt
    ) {
        List<String> errors = new ArrayList<>();
        try {
            DoubaoMechanicalVisionService.MechanicalVisionResponse response = mechanicalVisionService.analyze(
                    image.getBytes(),
                    image.getOriginalFilename(),
                    prompt
            );
            MechanicalVisionAnalysisResult result = response.result();
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("passed", response.requestContainsImage()
                    && result != null
                    && (result.getQualityScore() > 0 || !result.getComponents().isEmpty() || !result.getEvidence().isEmpty()));
            data.put("model", response.model());
            data.put("endpoint", response.endpoint());
            data.put("requestContainsImage", response.requestContainsImage());
            data.put("responseIsJson", result != null);
            data.put("componentCount", result == null ? 0 : result.getComponents().size());
            data.put("qualityScore", result == null ? 0 : result.getQualityScore());
            data.put("apiKey", "masked");
            data.put("errors", errors);
            data.put("result", result);
            return Result.success(data);
        } catch (Exception exception) {
            errors.add(exception.getMessage());
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("passed", false);
            data.put("requestContainsImage", false);
            data.put("responseIsJson", false);
            data.put("apiKey", "masked");
            data.put("errors", errors);
            data.put("diagnostic", safeDiagnostic());
            return Result.fail("MECHANICAL_VISION_TEST_FAILED", "mechanical vision test failed", data);
        }
    }

    private Map<String, Object> safeDiagnostic() {
        try {
            return mechanicalVisionService.diagnosticConfig();
        } catch (Exception exception) {
            return Map.of("error", exception.getMessage());
        }
    }
}
