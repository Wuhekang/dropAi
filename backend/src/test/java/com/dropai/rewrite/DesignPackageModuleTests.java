package com.dropai.rewrite;

import com.dropai.rewrite.modules.assemblyBuilder.AssemblyBuilder;
import com.dropai.rewrite.modules.bomGenerator.BOMGenerator;
import com.dropai.rewrite.modules.calculationEngine.CalculationEngine;
import com.dropai.rewrite.modules.designPipeline.TaskDrivenDesignPipeline;
import com.dropai.rewrite.modules.designAnalyzer.DesignAnalyzer;
import com.dropai.rewrite.modules.drawingEngine.DrawingArtifact;
import com.dropai.rewrite.modules.drawingEngine.DrawingEngine;
import com.dropai.rewrite.modules.drawingPlanBuilder.DrawingPlanBuilder;
import com.dropai.rewrite.modules.documentParser.DocumentParser;
import com.dropai.rewrite.modules.model.DesignProject;
import com.dropai.rewrite.modules.nonStandardPartGenerator.NonStandardPartGenerator;
import com.dropai.rewrite.modules.paperEngine.PaperEngine;
import com.dropai.rewrite.modules.parameterEngine.ParameterEngine;
import com.dropai.rewrite.modules.projectAnalyzer.ProjectAnalyzer;
import com.dropai.rewrite.modules.projectSessionReset.ProjectSessionReset;
import com.dropai.rewrite.modules.standardPartSelector.MockOnlineStandardPartProvider;
import com.dropai.rewrite.modules.standardPartSelector.StandardPartCache;
import com.dropai.rewrite.modules.standardPartSelector.StandardPartSelector;
import com.dropai.rewrite.modules.structureTreeBuilder.StructureTreeBuilder;
import com.dropai.rewrite.modules.unknownPartResolver.UnknownPartResolver;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DesignPackageModuleTests {
    @Test
    void emptyProjectDoesNotEnterDefaultGeneration() {
        IllegalStateException error = assertThrows(IllegalStateException.class, () -> pipeline().analyzeNewTask(new DesignProject()));
        assertTrue(error.getMessage().contains("缺少题目"));
    }

    @Test
    void designAnalyzerIgnoresArchivedNonWhitelistDocuments() {
        DesignProject project = new DesignAnalyzer().analyze("", List.of(
                new DocumentParser.ParsedDocument("任务书.docx", "TASK_BOOK", "题目：重力沉降室设计\n总长 4200mm"),
                new DocumentParser.ParsedDocument("旧论文.docx", "DOCUMENT", "题目：油罐检测爬壁机器人结构设计\n履带机构 吸附力 200N", "archived", false, "仅存档")
        ));

        assertTrue(project.getProjectTitle().contains("重力沉降室"));
        assertFalse(project.getProjectTitle().contains("油罐检测爬壁机器人"));
        assertFalse(project.getMainStructures().stream().anyMatch(item -> item.contains("履带")));
        assertTrue(project.getExplicitParameters().stream().anyMatch(item -> "总长".equals(item.getName())));
        assertFalse(project.getExplicitParameters().stream().anyMatch(item -> "吸附力".equals(item.getName())));
    }

    @Test
    void incompleteSettlingChamberTaskIsCompletedAsSuggestedPlan() {
        DesignProject input = new DesignProject();
        input.setProjectTitle("重力沉降室设计");

        DesignProject project = pipeline().analyzeNewTask(input);

        assertTrue(project.getEquipmentName().contains("重力沉降室"));
        assertTrue(project.getDesignType().contains("环保设备"));
        assertTrue(project.getMainStructures().contains("进气管"));
        assertTrue(project.getMainStructures().contains("灰斗"));
        assertTrue(project.getMainStructures().contains("检修门"));
        assertTrue(project.getMainStructures().contains("加强筋"));
        assertTrue(project.getSuggestedParameters().stream().anyMatch(p -> "总长".equals(p.getName()) && "系统参考补全".equals(p.getSource())));
        assertTrue(project.getSuggestedParameters().stream().anyMatch(p -> "设计风量".equals(p.getName()) && String.valueOf(p.getValue()).contains("3000~6000")));
        assertTrue(project.getSuggestedParameters().stream()
                .filter(p -> List.of("总长", "总宽", "总高", "箱体板厚", "灰斗角度", "进出口尺寸", "设计风量", "气流速度", "停留时间").contains(p.getName()))
                .allMatch(p -> p.getBasis() != null && (p.getBasis().contains("系统参考补全") || p.getBasis().contains("方案级建议"))));
        assertTrue(project.getVerificationItems().contains("任务书部分参数未明确，系统已生成方案级建议值，可在下一步修改确认。"));
        assertTrue(project.getVerificationItems().contains("任务书未明确完整图纸规划，系统将按毕业设计版自动补全总装图和关键零件图，补全内容可在下一步修改确认。"));
        assertTrue(project.getDesignReference().getReferenceMode().equals("graduation_design"));
        assertTrue(project.getDesignReference().getRecommendedDrawings().contains("箱体零件图"));

        List<String> chamberFiles = fileNames(new DrawingEngine().drawAssemblyDrawing(project));
        assertTrue(chamberFiles.contains("assembly.dxf"), chamberFiles::toString);
        assertFalse(chamberFiles.contains("shell_structure.png"));
        assertFalse(chamberFiles.contains("inlet_outlet.png"));
        assertFalse(chamberFiles.contains("track_mechanism.png"));
    }

    @Test
    void engineeringDepthRequiresCompleteInputs() {
        DesignProject input = new DesignProject();
        input.setDesignDepth("engineering");
        input.setProjectTitle("重力沉降室设计");

        IllegalStateException error = assertThrows(IllegalStateException.class, () -> pipeline().analyzeNewTask(input));

        assertTrue(error.getMessage().contains("工程版需要补充"));
    }

    @Test
    void taskDrivenPipelineBuildsTrustedRobotChain() {
        DesignProject project = structuredProject();
        assertTrue(project.getProjectTitle().contains("油罐检测爬壁机器人"));
        assertTrue(project.getStructureTree().getChildren().size() >= 8);
        assertTrue(project.getStructureTree().getChildren().size() <= 15);
        assertTrue(project.getStructureTree().getChildren().stream().allMatch(n -> n.getSource() != null && !n.getSource().isBlank() && n.isRequired()));
        assertTrue(project.getResolvedParts().stream().anyMatch(p -> "standard".equals(p.getPartType())));
        assertTrue(project.getResolvedParts().stream().filter(p -> "standard".equals(p.getPartType())).allMatch(p -> !"online_found".equals(p.getRetrievalStatus())));
        assertTrue(project.getResolvedParts().stream().filter(p -> "standard".equals(p.getPartType())).anyMatch(p -> "mock".equals(p.getRetrievalStatus())));
        assertTrue(project.getAssemblyConstraints().stream().allMatch(c -> c.getSource() != null && !c.getSource().isBlank()));
        assertTrue(project.getAssemblyConstraints().stream().anyMatch(c -> c.getMountingFace() != null && !c.getMountingFace().isBlank()));
        assertTrue(project.getBom().size() == project.getComponents().size());
    }

    @Test
    void drawingPlanDrivesCleanThreeViewCad() throws Exception {
        DesignProject project = structuredProject();
        List<DrawingArtifact> drawings = new DrawingEngine().drawAssemblyDrawing(project);
        assertTrue(drawings.stream().anyMatch(file -> "assembly.dxf".equals(file.fileName())));
        assertFalse(drawings.stream().anyMatch(file -> file.fileName().contains("track_mechanism")));
        assertFalse(drawings.stream().anyMatch(file -> file.fileName().contains("preview")));
        String dxf = new String(drawings.stream().filter(file -> "assembly.dxf".equals(file.fileName())).findFirst().orElseThrow().content(), StandardCharsets.UTF_8);
        assertTrue("DrawingPlan".equals(project.getDrawingPlan().getInputSource()));
        assertFalse(project.getDrawingPlan().getMainView().getVisibleParts().isEmpty());
        assertFalse(project.getDrawingPlan().getTopView().getVisibleParts().isEmpty());
        assertFalse(project.getDrawingPlan().getSideView().getVisibleParts().isEmpty());
        assertTrue(project.getDrawingPlan().getSectionViews().isEmpty());
        assertTrue(project.getDrawingPlan().getDetailViews().isEmpty());
        assertFalse(dxf.contains("source:"));
        assertFalse(dxf.contains("component envelope"));
        assertFalse(dxf.contains("mainView"));
        assertFalse(dxf.contains("topView"));
        assertFalse(dxf.contains("sideView"));
        assertFalse(dxf.contains("P001"));
        assertFalse(dxf.contains("P002"));
        assertFalse(dxf.contains("DrawingPlan"));
        assertFalse(dxf.contains("debug"));
        assertTrue(dxf.contains("Front view"));
        assertTrue(dxf.contains("Top view"));
        assertTrue(dxf.contains("Side view"));
        assertTrue(dxf.contains("Core BOM"));
    }

    @Test
    void assemblyOutputDoesNotEmitChapterOrAuxiliaryDrawings() {
        DesignProject conveyor = projectWithStructure("输送机结构设计", List.of("输送带", "主动滚筒", "从动滚筒", "机架", "驱动电机", "减速器"));
        List<String> conveyorFiles = fileNames(new DrawingEngine().drawAssemblyDrawing(conveyor));
        assertTrue(conveyorFiles.contains("assembly.dxf"));
        assertFalse(conveyorFiles.contains("conveyor_belt.png"));
        assertFalse(conveyorFiles.contains("roller_mechanism.png"));
        assertFalse(conveyorFiles.contains("track_mechanism.png"));
        assertFalse(conveyorFiles.contains("cleaning_mechanism.png"));

        DesignProject chamber = projectWithStructure("重力沉降室设计", List.of("壳体", "进口法兰", "出口接口", "排灰斗", "检修门", "支撑架"));
        List<String> chamberFiles = fileNames(new DrawingEngine().drawAssemblyDrawing(chamber));
        assertTrue(chamberFiles.contains("assembly.dxf"));
        assertFalse(chamberFiles.contains("shell_structure.png"));
        assertFalse(chamberFiles.contains("ash_hopper.png"));
        assertFalse(chamberFiles.contains("track_mechanism.png"));

        DesignProject manipulator = projectWithStructure("机械手结构设计", List.of("底座", "大臂", "小臂", "夹爪", "关节伺服驱动"));
        List<String> manipulatorFiles = fileNames(new DrawingEngine().drawAssemblyDrawing(manipulator));
        assertTrue(manipulatorFiles.contains("assembly.dxf"));
        assertFalse(manipulatorFiles.contains("base_structure.png"));
        assertFalse(manipulatorFiles.contains("gripper.png"));
        assertFalse(manipulatorFiles.contains("cleaning_mechanism.png"));
    }

    @Test
    void partDrawingEngineProducesMajorEngineeringPartDrawings() {
        List<DrawingArtifact> partDrawings = new DrawingEngine().drawPartDrawing(structuredProject());
        assertTrue(partDrawings.size() == 5);
        String combined = partDrawings.stream()
                .map(file -> new String(file.content(), StandardCharsets.UTF_8))
                .reduce("", String::concat);
        assertTrue(combined.contains("结构特征"));
        assertTrue(combined.contains("未注尺寸公差"));
        assertTrue(combined.contains("基准A"));
        assertTrue(combined.contains("位置度"));
    }

    @Test
    void paperGeneratorProducesDownloadableDocx() throws Exception {
        byte[] bytes = new PaperEngine().generatePaper(structuredProject());
        assertTrue(bytes.length > 1000);
        try (XWPFDocument document = new XWPFDocument(new ByteArrayInputStream(bytes))) {
            StringBuilder text = new StringBuilder();
            document.getParagraphs().forEach(p -> text.append(p.getText()));
            assertTrue(text.length() > 1000);
            assertFalse(text.toString().contains("第1段说明"));
            assertFalse(text.toString().contains("该段落需要围绕"));
            assertFalse(text.toString().contains("进一步展开"));
            assertFalse(text.toString().contains("定稿时可根据"));
        }
    }

    private DesignProject structuredProject() {
        DesignProject input = new DesignProject();
        input.setProjectTitle("油罐检测爬壁机器人结构设计");
        input.setEquipmentName("油罐检测爬壁机器人");
        input.setDesignType("机器人结构设计 / 机电一体化设计");
        input.setProjectCategory("机械类毕业设计");
        input.setMainFunctions(List.of("油罐壁面爬行", "磁吸附稳定附着", "表面清扫", "检测模块安装", "模块化维护"));
        input.setMainStructures(List.of("履带机构", "驱动轮", "从动轮", "支重轮", "永磁吸附模块", "圆盘清扫刷", "检测传感器安装架", "滑轨调节机构", "机架", "防护外壳", "驱动电机", "减速器"));
        input.getExplicitParameters().add(new DesignProject.Parameter("整机长度", 800, "mm", "任务书技术指标：整机尺寸≤800×600×300mm", null));
        input.getExplicitParameters().add(new DesignProject.Parameter("整机宽度", 600, "mm", "任务书技术指标：整机尺寸≤800×600×300mm", null));
        input.getExplicitParameters().add(new DesignProject.Parameter("整机高度", 300, "mm", "任务书技术指标：整机尺寸≤800×600×300mm", null));
        input.getExplicitParameters().add(new DesignProject.Parameter("吸附力", 200, "N", "任务书技术指标：吸附力≥200N", null));
        return pipeline().analyzeNewTask(input);
    }

    private DesignProject projectWithStructure(String title, List<String> nodes) {
        DesignProject project = structuredProject();
        project.setProjectTitle(title);
        DesignProject.StructureNode root = new DesignProject.StructureNode("整机", "root", "test", 1.0);
        root.setChildren(nodes.stream()
                .map(name -> new DesignProject.StructureNode(name, "mechanism", "test-structure-tree", 1.0))
                .toList());
        project.setStructureTree(root);
        return project;
    }

    private List<String> fileNames(List<DrawingArtifact> drawings) {
        return drawings.stream().map(DrawingArtifact::fileName).toList();
    }

    private TaskDrivenDesignPipeline pipeline() {
        ParameterEngine parameterEngine = new ParameterEngine();
        CalculationEngine calculationEngine = new CalculationEngine();
        StandardPartCache cache = new StandardPartCache(new ObjectMapper());
        return new TaskDrivenDesignPipeline(new ProjectSessionReset(), parameterEngine, new ProjectAnalyzer(),
                new StructureTreeBuilder(), new StandardPartSelector(cache, new MockOnlineStandardPartProvider(cache)),
                new NonStandardPartGenerator(new UnknownPartResolver()), new AssemblyBuilder(), new BOMGenerator(),
                calculationEngine, new DrawingPlanBuilder());
    }
}
