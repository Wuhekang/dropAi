package com.dropai.rewrite.service;

import com.dropai.rewrite.auth.AuthContext;
import com.dropai.rewrite.entity.DocumentJobRecord;
import com.dropai.rewrite.mapper.DocumentJobMapper;
import com.dropai.rewrite.modules.calculationEngine.CalculationEngine;
import com.dropai.rewrite.modules.designEnhancementEngine.DesignEnhancementEngine;
import com.dropai.rewrite.modules.designPipeline.TaskDrivenDesignPipeline;
import com.dropai.rewrite.modules.drawingEngine.DrawingArtifact;
import com.dropai.rewrite.modules.drawingEngine.DrawingEngine;
import com.dropai.rewrite.modules.exportEngine.ExportEngine;
import com.dropai.rewrite.modules.model.DesignProject;
import com.dropai.rewrite.modules.paperEngine.PaperEngine;
import com.dropai.rewrite.modules.parameterEngine.ParameterEngine;
import com.dropai.rewrite.modules.stepExportEngine.StepExportEngine;
import com.dropai.rewrite.modules.swMacroEngine.SwMacroEngine;
import com.dropai.rewrite.modules.structureEngine.StructureEngine;
import com.dropai.rewrite.vo.DesignPackageVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

@Service
public class DesignPackageService {
    private static final Logger log = LoggerFactory.getLogger(DesignPackageService.class);
    private static final String DOCX = "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
    private final ParameterEngine parameterEngine; private final CalculationEngine calculationEngine;
    private final DesignEnhancementEngine designEnhancementEngine; private final StructureEngine structureEngine; private final DrawingEngine drawingEngine; private final SwMacroEngine swMacroEngine;
    private final StepExportEngine stepExportEngine;
    private final PaperEngine paperEngine; private final ExportEngine exportEngine; private final DocumentJobMapper mapper;
    private final TaskDrivenDesignPipeline designPipeline;
    private final PointService pointService;

    @Autowired
    public DesignPackageService(ParameterEngine parameterEngine, CalculationEngine calculationEngine, DesignEnhancementEngine designEnhancementEngine, StructureEngine structureEngine, DrawingEngine drawingEngine,
                                SwMacroEngine swMacroEngine, StepExportEngine stepExportEngine, PaperEngine paperEngine, ExportEngine exportEngine, DocumentJobMapper mapper,
                                TaskDrivenDesignPipeline designPipeline, PointService pointService) {
        this.parameterEngine = parameterEngine; this.calculationEngine = calculationEngine; this.designEnhancementEngine = designEnhancementEngine; this.structureEngine = structureEngine; this.drawingEngine = drawingEngine;
        this.swMacroEngine = swMacroEngine; this.stepExportEngine = stepExportEngine; this.paperEngine = paperEngine; this.exportEngine = exportEngine; this.mapper = mapper;
        this.designPipeline = designPipeline;
        this.pointService = pointService;
    }

    public DesignPackageVO generate(DesignProject input) {
        return pointService.chargeAfterSuccess(PointService.DESIGN_GENERATE, "生成毕业设计成果包", () -> doGenerate(input));
    }

    private DesignPackageVO doGenerate(DesignProject input) {
        Long userId = AuthContext.requireUserId();
        DesignProject project = designPipeline.generateCurrentTask(input == null ? new DesignProject() : input);
        log.info("开始生成成果包 title={} parameters={}", project.getProjectTitle(), project.allParameters().size());
        List<Generated> generated = new ArrayList<>();
        generated.add(generateOne("MechanicalDesignPlan.json", "application/json", () -> exportEngine.mechanicalDesignPlan(project)));
        generated.add(generateOne("model_3d.json", "application/json", () -> exportEngine.model3d(project)));
        generated.addAll(generateGroup(List.of("assembly.step", "part_01.step", "part_02.step", "part_03.step", "part_04.step", "part_05.step"), () -> stepExportEngine.export(project)));
        generated.addAll(generateGroup(List.of("assembly.dxf"), () -> drawingEngine.drawAssemblyDrawing(project)));
        generated.addAll(generateGroup(List.of("cad_preview.svg", "cad_preview.png"), () -> drawingEngine.drawAssemblyPreview(project)));
        generated.addAll(generateGroup(List.of("part_01.dxf", "part_02.dxf", "part_03.dxf", "part_04.dxf", "part_05.dxf"), () -> drawingEngine.drawPartDrawing(project)));
        generated.add(generateOne("paper.docx", DOCX, () -> paperEngine.generatePaper(project)));
        generated.add(generateOne("project_package.zip", "application/zip", () -> exportEngine.zip(generated.stream()
                .filter(Generated::success)
                .map(Generated::artifact)
                .toList())));

        DesignPackageVO result = new DesignPackageVO();
        result.setProject(project);
        List<DesignPackageVO.ArtifactVO> artifacts = generated.stream().map(item -> toArtifact(userId, project.getProjectTitle(), item)).toList();
        result.setArtifacts(artifacts);
        long failed = artifacts.stream().filter(item -> "failed".equals(item.getStatus())).count();
        result.setStatus(failed == 0 ? "success" : failed == artifacts.size() ? "failed" : "partial_success");
        result.setMessage(failed == 0 ? "全部成果文件生成成功" : "有 " + failed + " 个文件生成失败，请查看失败原因");
        return result;
    }

    private Generated generateOne(String name, String mediaType, Supplier<byte[]> supplier) {
        try {
            byte[] content = supplier.get();
            if (content == null || content.length == 0) throw new IllegalStateException("生成文件为空");
            DrawingArtifact artifact = new DrawingArtifact(name, content, mediaType);
            log.info("文件生成成功 name={} size={}", name, content.length);
            return new Generated(artifact, null);
        } catch (Exception exception) {
            String reason = readable(exception);
            log.error("文件生成失败 name={} reason={}", name, reason, exception);
            return new Generated(new DrawingArtifact(name, new byte[0], mediaType), reason);
        }
    }

    private List<Generated> generateGroup(List<String> expectedNames, Supplier<List<DrawingArtifact>> supplier) {
        try {
            List<DrawingArtifact> files = supplier.get();
            List<Generated> result = new ArrayList<>();
            for (String name : expectedNames) {
                DrawingArtifact file = files.stream().filter(item -> name.equals(item.fileName())).findFirst()
                        .orElseThrow(() -> new IllegalStateException("未生成预期文件：" + name));
                result.add(file.content() == null || file.content().length == 0
                        ? new Generated(file, "生成文件为空") : new Generated(file, null));
                log.info("文件生成结果 name={} success={} size={}", name, file.content() != null && file.content().length > 0, file.content() == null ? 0 : file.content().length);
            }
            return result;
        } catch (Exception exception) {
            String reason = readable(exception);
            log.error("文件组生成失败 names={} reason={}", expectedNames, reason, exception);
            return expectedNames.stream().map(name -> new Generated(new DrawingArtifact(name, new byte[0], mediaType(name)), reason)).toList();
        }
    }

    private List<Generated> generateDrawingGroup(Supplier<List<DrawingArtifact>> supplier) {
        try {
            List<DrawingArtifact> files = supplier.get();
            List<Generated> result = new ArrayList<>();
            for (DrawingArtifact file : files) {
                result.add(file.content() == null || file.content().length == 0
                        ? new Generated(file, "鐢熸垚鏂囦欢涓虹┖") : new Generated(file, null));
                log.info("鍥剧焊鐢熸垚缁撴灉 name={} success={} size={}", file.fileName(), file.content() != null && file.content().length > 0, file.content() == null ? 0 : file.content().length);
            }
            return result;
        } catch (Exception exception) {
            String reason = readable(exception);
            log.error("鍥剧焊缁勭敓鎴愬け璐?reason={}", reason, exception);
            return List.of(new Generated(new DrawingArtifact("cad_preview.png", new byte[0], "image/png"), reason));
        }
    }

    private DesignPackageVO.ArtifactVO toArtifact(Long userId, String title, Generated generated) {
        if (!generated.success()) {
            return new DesignPackageVO.ArtifactVO(null, generated.artifact().fileName(), generated.artifact().mediaType(), null,
                    "failed", 0, generated.failureReason());
        }
        try {
            return persist(userId, title, generated.artifact());
        } catch (Exception exception) {
            String reason = "文件保存失败：" + readable(exception);
            log.error("文件持久化失败 name={} reason={}", generated.artifact().fileName(), reason, exception);
            return new DesignPackageVO.ArtifactVO(null, generated.artifact().fileName(), generated.artifact().mediaType(), null,
                    "failed", 0, reason);
        }
    }

    private DesignPackageVO.ArtifactVO persist(Long userId, String title, DrawingArtifact file) {
        String id = UUID.randomUUID().toString().replace("-", "");
        DocumentJobRecord record = new DocumentJobRecord();
        record.setJobId(id); record.setUserId(userId); record.setFileName(file.fileName()); record.setSourceFeature("DESIGN_PACKAGE");
        record.setMode(fileType(file.fileName())); record.setModeName("毕业设计成果包"); record.setPlatform("ENGINEERING"); record.setPlatformName("完整成果包");
        record.setStatus("SUCCESS"); record.setTotalParagraphs(1); record.setProcessedParagraphs(1); record.setRewrittenParagraphs(1);
        record.setMessage(title + " 成果文件已生成，大小 " + file.content().length + " 字节"); record.setParagraphsJson("[]"); record.setOutputFile(file.content());
        record.setCreatedAt(LocalDateTime.now()); record.setUpdatedAt(LocalDateTime.now()); mapper.insert(record);
        String downloadUrl = "/api/documents/" + id + "/download";
        return new DesignPackageVO.ArtifactVO(id, file.fileName(), file.mediaType(), downloadUrl, "success", file.content().length, null);
    }

    private String mediaType(String name) {
        if (name.endsWith(".docx")) return DOCX;
        if (name.endsWith(".dxf")) return "application/dxf";
        if (name.endsWith(".svg")) return "image/svg+xml";
        if (name.endsWith(".png")) return "image/png";
        if (name.endsWith(".pdf")) return "application/pdf";
        if (name.endsWith(".zip")) return "application/zip";
        if (name.endsWith(".json")) return "application/json";
        if (name.endsWith(".step") || name.endsWith(".stp")) return "model/step";
        return "text/plain";
    }
    private String fileType(String name) {
        int dot = name == null ? -1 : name.lastIndexOf('.');
        return dot < 0 ? "file" : name.substring(dot + 1).toLowerCase();
    }
    private String readable(Exception exception) {
        String message = exception.getMessage();
        if (message == null || message.isBlank()) return exception.getClass().getSimpleName();
        if (message.contains("Data too long for column")) return "数据库字段长度不足，文件保存失败";
        int marker = message.indexOf("###");
        return marker > 0 ? message.substring(0, marker).trim() : message;
    }
    private record Generated(DrawingArtifact artifact, String failureReason) {
        boolean success() { return failureReason == null && artifact.content() != null && artifact.content().length > 0; }
    }
}
