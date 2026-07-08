package com.dropai.rewrite;

import com.dropai.rewrite.auth.AuthContext;
import com.dropai.rewrite.entity.DocumentJobRecord;
import com.dropai.rewrite.mapper.DocumentJobMapper;
import com.dropai.rewrite.modules.assemblyBuilder.AssemblyBuilder;
import com.dropai.rewrite.modules.bomGenerator.BOMGenerator;
import com.dropai.rewrite.modules.calculationEngine.CalculationEngine;
import com.dropai.rewrite.modules.designEnhancementEngine.DesignEnhancementEngine;
import com.dropai.rewrite.modules.designPipeline.TaskDrivenDesignPipeline;
import com.dropai.rewrite.modules.drawingEngine.DrawingEngine;
import com.dropai.rewrite.modules.drawingPlanBuilder.DrawingPlanBuilder;
import com.dropai.rewrite.modules.exportEngine.ExportEngine;
import com.dropai.rewrite.modules.model.DesignProject;
import com.dropai.rewrite.modules.nonStandardPartGenerator.NonStandardPartGenerator;
import com.dropai.rewrite.modules.paperEngine.PaperEngine;
import com.dropai.rewrite.modules.parameterEngine.ParameterEngine;
import com.dropai.rewrite.modules.projectAnalyzer.ProjectAnalyzer;
import com.dropai.rewrite.modules.projectSessionReset.ProjectSessionReset;
import com.dropai.rewrite.modules.stepExportEngine.StepExportEngine;
import com.dropai.rewrite.modules.standardPartSelector.MockOnlineStandardPartProvider;
import com.dropai.rewrite.modules.standardPartSelector.StandardPartCache;
import com.dropai.rewrite.modules.standardPartSelector.StandardPartSelector;
import com.dropai.rewrite.modules.structureEngine.StructureEngine;
import com.dropai.rewrite.modules.structureTreeBuilder.StructureTreeBuilder;
import com.dropai.rewrite.modules.swMacroEngine.SwMacroEngine;
import com.dropai.rewrite.modules.unknownPartResolver.UnknownPartResolver;
import com.dropai.rewrite.service.PointService;
import com.dropai.rewrite.service.DesignPackageService;
import com.dropai.rewrite.vo.DesignPackageVO;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DesignPackageServiceTests {
    @AfterEach
    void clearAuth() {
        AuthContext.clear();
    }

    @Test
    void successfulArtifactsHaveRealDownloadMetadata() {
        DocumentJobMapper mapper = mock(DocumentJobMapper.class);
        when(mapper.insert(any(DocumentJobRecord.class))).thenReturn(1);
        ParameterEngine parameterEngine = new ParameterEngine();
        CalculationEngine calculationEngine = new CalculationEngine();
        StandardPartCache cache = new StandardPartCache(new ObjectMapper());
        TaskDrivenDesignPipeline pipeline = new TaskDrivenDesignPipeline(new ProjectSessionReset(), parameterEngine,
                new ProjectAnalyzer(), new StructureTreeBuilder(),
                new StandardPartSelector(cache, new MockOnlineStandardPartProvider(cache)),
                new NonStandardPartGenerator(new UnknownPartResolver()), new AssemblyBuilder(), new BOMGenerator(),
                calculationEngine, new DrawingPlanBuilder());
        PointService pointService = mock(PointService.class);
        when(pointService.chargeAfterSuccess(anyString(), anyString(), any())).thenAnswer(invocation ->
                ((java.util.function.Supplier<?>) invocation.getArgument(2)).get());
        DesignPackageService service = new DesignPackageService(
                parameterEngine, calculationEngine, new DesignEnhancementEngine(), new StructureEngine(), new DrawingEngine(), new SwMacroEngine(),
                new StepExportEngine(), new PaperEngine(), new ExportEngine(new ObjectMapper()), mapper, pipeline, pointService);
        AuthContext.setUserId(1L);

        DesignPackageVO result = service.generate(validProject());

        assertEquals("success", result.getStatus());
        assertTrue(result.getArtifacts().stream().allMatch(item -> "success".equals(item.getStatus())));
        assertTrue(result.getArtifacts().stream().allMatch(item -> item.getSize() > 0 && item.getDownloadUrl() != null));
        assertEquals(21, result.getArtifacts().size());
        assertTrue(result.getArtifacts().stream().anyMatch(item -> "MechanicalDesignPlan.json".equals(item.getName())));
        assertTrue(result.getArtifacts().stream().anyMatch(item -> "mechanical-pipeline-audit.json".equals(item.getName())));
        assertTrue(result.getArtifacts().stream().anyMatch(item -> "assembly-model.json".equals(item.getName())));
        assertTrue(result.getArtifacts().stream().anyMatch(item -> "model-generation-report.json".equals(item.getName())));
        assertTrue(result.getArtifacts().stream().anyMatch(item -> "model_3d.json".equals(item.getName())));
        assertTrue(result.getArtifacts().stream().anyMatch(item -> "assembly.step".equals(item.getName())));
        assertTrue(result.getArtifacts().stream().anyMatch(item -> "part_05.step".equals(item.getName())));
        assertTrue(result.getArtifacts().stream().anyMatch(item -> "paper.docx".equals(item.getName())));
        assertTrue(result.getArtifacts().stream().anyMatch(item -> "project_package.zip".equals(item.getName())));
        assertTrue(result.getArtifacts().stream().anyMatch(item -> "assembly.dxf".equals(item.getName())));
        assertTrue(result.getArtifacts().stream().anyMatch(item -> "cad_preview.svg".equals(item.getName())));
        assertTrue(result.getArtifacts().stream().anyMatch(item -> "cad_preview.png".equals(item.getName())));
        assertTrue(result.getArtifacts().stream().anyMatch(item -> "part_05.dxf".equals(item.getName())));
        assertTrue(result.getProject().getBom().size() >= 5);
        assertTrue(result.getProject().getAssemblyModel().getComponents().size() >= 5);
        assertTrue(result.getProject().getAssemblyModel().getConstraints().size() >= 5);
        assertTrue(result.getProject().getStructureTree().getChildren().size() >= 3);
        assertTrue(result.getProject().getAssemblyTree().getChildren().size() >= 1);
        assertTrue(result.getArtifacts().stream().noneMatch(item -> item.getName().contains("track_mechanism")));

        ArgumentCaptor<DocumentJobRecord> captor = ArgumentCaptor.forClass(DocumentJobRecord.class);
        verify(mapper, org.mockito.Mockito.atLeastOnce()).insert(captor.capture());
        assertTrue(captor.getAllValues().stream().allMatch(record -> record.getMode() != null && record.getMode().length() <= 10));
        assertTrue(captor.getAllValues().stream().filter(record -> record.getFileName().endsWith(".docx")).allMatch(record -> "docx".equals(record.getMode())));
    }

    private DesignProject validProject() {
        DesignProject input = new DesignProject();
        input.setProjectTitle("油罐检测爬壁机器人结构设计");
        input.setEquipmentName("油罐检测爬壁机器人");
        input.setDesignType("机器人结构设计 / 机电一体化设计");
        input.setMainFunctions(List.of("油罐壁面爬行", "磁吸附稳定附着", "表面清扫", "检测模块安装", "模块化维护"));
        input.setMainStructures(List.of("履带机构", "驱动轮", "从动轮", "支重轮", "永磁吸附模块", "圆盘清扫刷", "检测传感器安装架", "滑轨调节机构", "机架", "防护外壳", "驱动电机", "减速器"));
        input.getExplicitParameters().add(new DesignProject.Parameter("整机长度", 800, "mm", "任务书技术指标：整机尺寸≤800×600×300mm", null));
        input.getExplicitParameters().add(new DesignProject.Parameter("整机宽度", 600, "mm", "任务书技术指标：整机尺寸≤800×600×300mm", null));
        input.getExplicitParameters().add(new DesignProject.Parameter("整机高度", 300, "mm", "任务书技术指标：整机尺寸≤800×600×300mm", null));
        return input;
    }
}
