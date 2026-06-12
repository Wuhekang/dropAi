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
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class DesignPackageService {
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
        List<DrawingArtifact> files = new ArrayList<>();
        files.add(new DrawingArtifact("paper.docx", paperEngine.generatePaper(project), "application/vnd.openxmlformats-officedocument.wordprocessingml.document"));
        files.add(new DrawingArtifact("design_calculation.docx", paperEngine.generateCalculationBook(project), "application/vnd.openxmlformats-officedocument.wordprocessingml.document"));
        files.addAll(drawingEngine.drawAssemblyDrawing(project));
        files.addAll(drawingEngine.drawPartDrawing(project));
        files.addAll(swMacroEngine.generate(project));
        files.add(new DrawingArtifact("sw_modeling_steps.docx", paperEngine.generateModelingSteps(project), "application/vnd.openxmlformats-officedocument.wordprocessingml.document"));
        files = exportEngine.appendManifests(project, files);
        files.add(new DrawingArtifact("project_package.zip", exportEngine.zip(files), "application/zip"));

        DesignPackageVO result = new DesignPackageVO(); result.setProject(project);
        List<DesignPackageVO.ArtifactVO> artifacts = new ArrayList<>();
        for (DrawingArtifact file : files) artifacts.add(persist(userId, project.getProjectTitle(), file));
        result.setArtifacts(artifacts); return result;
    }

    private DesignPackageVO.ArtifactVO persist(Long userId, String title, DrawingArtifact file) {
        String id = UUID.randomUUID().toString().replace("-", "");
        DocumentJobRecord record = new DocumentJobRecord();
        record.setJobId(id); record.setUserId(userId); record.setFileName(file.fileName()); record.setSourceFeature("DESIGN_PACKAGE");
        record.setMode(file.mediaType()); record.setModeName("毕业设计成果包"); record.setPlatform("ENGINEERING"); record.setPlatformName("完整成果包");
        record.setStatus("SUCCESS"); record.setTotalParagraphs(1); record.setProcessedParagraphs(1); record.setRewrittenParagraphs(1);
        record.setMessage(title + " 成果文件已生成"); record.setParagraphsJson("[]"); record.setOutputFile(file.content());
        record.setCreatedAt(LocalDateTime.now()); record.setUpdatedAt(LocalDateTime.now()); mapper.insert(record);
        return new DesignPackageVO.ArtifactVO(id, file.fileName(), file.mediaType(), "/api/documents/" + id + "/download");
    }
}
