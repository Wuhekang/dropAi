package com.dropai.rewrite.service;

import com.dropai.rewrite.auth.AuthContext;
import com.dropai.rewrite.entity.DocumentJobRecord;
import com.dropai.rewrite.mapper.DocumentJobMapper;
import com.dropai.rewrite.modules.calculationEngine.CalculationEngine;
import com.dropai.rewrite.modules.drawingEngine.DrawingArtifact;
import com.dropai.rewrite.modules.drawingEngine.DrawingEngine;
import com.dropai.rewrite.modules.exportEngine.ExportEngine;
import com.dropai.rewrite.modules.model.DesignProject;
import com.dropai.rewrite.modules.paperEngine.PaperEngine;
import com.dropai.rewrite.modules.parameterEngine.ParameterEngine;
import com.dropai.rewrite.modules.swMacroEngine.SwMacroEngine;
import com.dropai.rewrite.vo.DesignPackageVO;
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
    private final DrawingEngine drawingEngine; private final SwMacroEngine swMacroEngine;
    private final PaperEngine paperEngine; private final ExportEngine exportEngine; private final DocumentJobMapper mapper;

    public DesignPackageService(ParameterEngine parameterEngine, CalculationEngine calculationEngine, DrawingEngine drawingEngine,
                                SwMacroEngine swMacroEngine, PaperEngine paperEngine, ExportEngine exportEngine, DocumentJobMapper mapper) {
        this.parameterEngine = parameterEngine; this.calculationEngine = calculationEngine; this.drawingEngine = drawingEngine;
        this.swMacroEngine = swMacroEngine; this.paperEngine = paperEngine; this.exportEngine = exportEngine; this.mapper = mapper;
    }

    public DesignPackageVO generate(DesignProject input) {
        Long userId = AuthContext.requireUserId();
        DesignProject project = calculationEngine.calculate(parameterEngine.normalize(input == null ? new DesignProject() : input));
        log.info("开始生成成果包 title={} parameters={}", project.getProjectTitle(), project.allParameters().size());
        List<Generated> generated = new ArrayList<>();
        generated.add(generateOne("paper.docx", DOCX, () -> paperEngine.generatePaper(project)));
        generated.add(generateOne("design_calculation.docx", DOCX, () -> paperEngine.generateCalculationBook(project)));
        generated.add(generateOne("sw_modeling_steps.docx", DOCX, () -> paperEngine.generateModelingSteps(project)));
        generated.addAll(generateGroup(List.of("assembly.dxf", "preview.svg", "cad_preview.png"), () -> drawingEngine.drawAssemblyDrawing(project)));
        generated.addAll(generateGroup(List.of("part_shell.dxf", "part_base.dxf", "part_inlet.dxf", "part_connector.dxf"), () -> drawingEngine.drawPartDrawing(project)));
        generated.addAll(generateGroup(List.of("sw_macro_shell.bas", "sw_macro_base.bas", "sw_macro_inlet.bas", "sw_modeling_steps.txt"), () -> swMacroEngine.generate(project)));
        generated.addAll(generateGroup(List.of("design_parameters.json", "preview.pdf"),
                () -> exportEngine.appendManifests(project, List.of())));

        List<DrawingArtifact> successfulFiles = generated.stream().filter(Generated::success).map(Generated::artifact).toList();
        generated.add(generateOne("project_package.zip", "application/zip", () -> exportEngine.zip(successfulFiles)));

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
