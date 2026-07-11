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
import com.dropai.rewrite.modules.standardPartSelector.MockOnlineStandardPartProvider;
import com.dropai.rewrite.modules.standardPartSelector.StandardPartCache;
import com.dropai.rewrite.modules.standardPartSelector.StandardPartSelector;
import com.dropai.rewrite.modules.stepExportEngine.StepExportEngine;
import com.dropai.rewrite.modules.structureEngine.StructureEngine;
import com.dropai.rewrite.modules.structureTreeBuilder.StructureTreeBuilder;
import com.dropai.rewrite.modules.swMacroEngine.SwMacroEngine;
import com.dropai.rewrite.modules.unknownPartResolver.UnknownPartResolver;
import com.dropai.rewrite.service.DesignPackageService;
import com.dropai.rewrite.service.PointService;
import com.dropai.rewrite.vo.DesignPackageVO;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DesignPackageRegressionTests {
    @AfterEach
    void clearAuth() {
        AuthContext.clear();
    }

    @Test
    void threeMechanicalProjectsGenerateDifferentValidatedPackages() {
        DesignPackageService service = service();
        AuthContext.setUserId(1L);

        DesignPackageVO robot = service.generate(project("油罐检测爬壁机器人结构设计",
                List.of("油罐壁面爬行", "永磁吸附", "检测云台安装"),
                List.of("履带机构", "永磁吸附模块", "检测云台", "驱动电机", "电控箱")));
        DesignPackageVO chamber = service.generate(project("重力沉降室设计",
                List.of("含尘气体沉降分离", "灰斗排灰", "检修维护"),
                List.of("箱体", "进口法兰", "出口接口", "排灰斗", "导流板", "检修门")));
        DesignPackageVO conveyor = service.generate(project("带式输送机结构设计",
                List.of("连续输送物料", "驱动滚筒传动", "张紧调节"),
                List.of("输送带", "主动滚筒", "从动滚筒", "托辊", "机架", "驱动电机", "张紧装置")));

        assertPackage(robot);
        assertPackage(chamber);
        assertPackage(conveyor);

        Set<String> robotStructures = structureNames(robot);
        Set<String> chamberStructures = structureNames(chamber);
        Set<String> conveyorStructures = structureNames(conveyor);
        assertNotEquals(robotStructures, chamberStructures);
        assertNotEquals(robotStructures, conveyorStructures);
        assertNotEquals(chamberStructures, conveyorStructures);
    }

    private void assertPackage(DesignPackageVO result) {
        assertEquals("success", result.getStatus());
        assertTrue(result.getArtifacts().stream().anyMatch(item -> "project_package.zip".equals(item.getName()) && item.getSize() > 0));
        assertTrue(result.getArtifacts().stream().anyMatch(item -> "assembly.step".equals(item.getName()) && item.getSize() > 300));
        assertTrue(result.getArtifacts().stream().anyMatch(item -> "assembly-validation.json".equals(item.getName()) && item.getSize() > 100));
        assertTrue(result.getArtifacts().stream().filter(item -> item.getName().matches("part_\\d{2}\\.dxf")).count() >= 5);
        assertTrue(result.getProject().getAssemblyModel().getComponents().size() >= 5);
        assertTrue(result.getProject().getAssemblyModel().getConstraints().size() >= 5);
        assertTrue(result.getProject().getBom().size() >= 5);
    }

    private Set<String> structureNames(DesignPackageVO result) {
        return result.getProject().getMechanicalDesignPlan().getSubsystems().stream()
                .map(item -> item.getName() + ":" + item.getFunction())
                .collect(Collectors.toSet());
    }

    private DesignProject project(String title, List<String> functions, List<String> structures) {
        DesignProject input = new DesignProject();
        input.setProjectTitle(title);
        input.setEquipmentName(title.replace("结构设计", "").replace("设计", ""));
        input.setMainFunctions(functions);
        input.setMainStructures(structures);
        input.getExplicitParameters().add(new DesignProject.Parameter("整机长度", 900, "mm", "test", null));
        input.getExplicitParameters().add(new DesignProject.Parameter("整机宽度", 420, "mm", "test", null));
        input.getExplicitParameters().add(new DesignProject.Parameter("整机高度", 260, "mm", "test", null));
        return input;
    }

    private DesignPackageService service() {
        DocumentJobMapper mapper = mock(DocumentJobMapper.class);
        when(mapper.insert(any(DocumentJobRecord.class))).thenReturn(1);
        PointService pointService = mock(PointService.class);
        when(pointService.chargeAfterSuccess(anyString(), anyString(), any())).thenAnswer(invocation ->
                ((Supplier<?>) invocation.getArgument(2)).get());
        ParameterEngine parameterEngine = new ParameterEngine();
        CalculationEngine calculationEngine = new CalculationEngine();
        StandardPartCache cache = new StandardPartCache(new ObjectMapper());
        TaskDrivenDesignPipeline pipeline = new TaskDrivenDesignPipeline(new ProjectSessionReset(), parameterEngine,
                new ProjectAnalyzer(), new StructureTreeBuilder(),
                new StandardPartSelector(cache, new MockOnlineStandardPartProvider(cache)),
                new NonStandardPartGenerator(new UnknownPartResolver()), new AssemblyBuilder(), new BOMGenerator(),
                calculationEngine, new DrawingPlanBuilder());
        return new DesignPackageService(
                parameterEngine, calculationEngine, new DesignEnhancementEngine(), new StructureEngine(), new DrawingEngine(), new SwMacroEngine(),
                new StepExportEngine(), new PaperEngine(), new ExportEngine(new ObjectMapper()), mapper, pipeline, pointService);
    }
}
