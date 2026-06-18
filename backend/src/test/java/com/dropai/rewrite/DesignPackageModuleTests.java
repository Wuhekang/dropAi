package com.dropai.rewrite;

import com.dropai.rewrite.modules.calculationEngine.CalculationEngine;
import com.dropai.rewrite.modules.assemblyBuilder.AssemblyBuilder;
import com.dropai.rewrite.modules.bomGenerator.BOMGenerator;
import com.dropai.rewrite.modules.designAnalyzer.DesignAnalyzer;
import com.dropai.rewrite.modules.designEnhancementEngine.DesignEnhancementEngine;
import com.dropai.rewrite.modules.designPipeline.TaskDrivenDesignPipeline;
import com.dropai.rewrite.modules.documentParser.DocumentParser;
import com.dropai.rewrite.modules.drawingEngine.DrawingEngine;
import com.dropai.rewrite.modules.drawingPlanBuilder.DrawingPlanBuilder;
import com.dropai.rewrite.modules.model.DesignProject;
import com.dropai.rewrite.modules.nonStandardPartGenerator.NonStandardPartGenerator;
import com.dropai.rewrite.modules.paperEngine.PaperEngine;
import com.dropai.rewrite.modules.parameterEngine.ParameterEngine;
import com.dropai.rewrite.modules.projectAnalyzer.ProjectAnalyzer;
import com.dropai.rewrite.modules.projectSessionReset.ProjectSessionReset;
import com.dropai.rewrite.modules.standardPartSelector.MockOnlineStandardPartProvider;
import com.dropai.rewrite.modules.standardPartSelector.StandardPartCache;
import com.dropai.rewrite.modules.standardPartSelector.StandardPartSelector;
import com.dropai.rewrite.modules.structureEngine.StructureEngine;
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
import static org.junit.jupiter.api.Assertions.assertTrue;

class DesignPackageModuleTests {
    @Test
    void calculationsWriteBackIntoSharedProjectModel() {
        DesignProject project = structuredProject();
        assertTrue(project.getCalculations().size() >= 4);
        assertTrue(project.allParameters().size() >= 8);
    }

    @Test
    void assemblyDxfContainsEngineeringDrawingLayersAndAnnotations() {
        DesignProject project = structuredProject();
        String dxf = new String(new DrawingEngine().drawAssemblyDrawing(project).get(0).content(), StandardCharsets.UTF_8);
        assertTrue("DrawingPlan".equals(project.getDrawingPlan().getInputSource()));
        assertFalse(project.getDrawingPlan().getMainView().getVisibleParts().isEmpty());
        assertFalse(project.getDrawingPlan().getTopView().getVisibleParts().isEmpty());
        assertFalse(project.getDrawingPlan().getSideView().getVisibleParts().isEmpty());
        assertTrue(project.getDrawingPlan().getSectionViews().isEmpty());
        assertTrue(project.getDrawingPlan().getDetailViews().isEmpty());
        assertTrue(dxf.contains("2\nDIMENSION\n"));
        assertTrue(dxf.contains("2\nTITLE\n"));
        assertTrue(dxf.contains("2\nTABLE\n"));
        assertTrue(dxf.contains("2\nJOINT\n"));
        assertFalse(dxf.contains("CAD input: DrawingPlan"));
        assertFalse(dxf.contains("source:"));
        assertFalse(dxf.contains("A-A"));
        assertFalse(dxf.contains("Detail I"));
        assertFalse(dxf.contains("Auxiliary"));
        assertFalse(dxf.contains("mainView"));
        assertFalse(dxf.contains("topView"));
        assertFalse(dxf.contains("sideView"));
        assertTrue(project.getDrawingPlan().getMainView().getVisibleParts().size() <= 12);
        assertTrue(project.getDrawingPlan().getTopView().getVisibleParts().size() <= 10);
        assertTrue(project.getDrawingPlan().getSideView().getVisibleParts().size() <= 10);
        assertTrue(project.getDrawingPlan().getQualityScore() >= 70);
        assertTrue(new DrawingEngine().drawAssemblyDrawing(project).stream()
                .filter(file -> "cad_preview.png".equals(file.fileName())).findFirst().orElseThrow().content().length > 1000);
        assertTrue(new DrawingEngine().drawAssemblyDrawing(project).stream()
                .filter(file -> "preview.png".equals(file.fileName())).findFirst().orElseThrow().content().length > 1000);
    }
    @Test
    void fallbackPaperMeetsMinimumStructureAndLength() throws Exception {
        DesignProject project = structuredProject();
        byte[] bytes = new PaperEngine().generatePaper(project);
        try (XWPFDocument document = new XWPFDocument(new ByteArrayInputStream(bytes))) {
            StringBuilder builder = new StringBuilder();
            document.getParagraphs().forEach(paragraph -> builder.append(paragraph.getText()));
            document.getTables().forEach(table -> table.getRows().forEach(row -> row.getTableCells()
                    .forEach(cell -> cell.getParagraphs().forEach(paragraph -> builder.append(paragraph.getText())))));
            String text = builder.toString();
            assertTrue(text.replaceAll("\\s+", "").length() >= 20000);
            assertTrue(text.contains("第三章 主要零件的计算"));
            assertTrue(text.contains("（3-1）"));
            assertTrue(text.contains("此处插入图3-1"));
            assertTrue(document.getAllPictures().size() >= 8);
        }
    }

    @Test
    void sparseTaskBookIsEnhancedBeforeDrawing() {
        DesignProject analyzed = new DesignAnalyzer().analyze("重力沉降室设计",
                List.of(new DocumentParser.ParsedDocument("任务书.txt", "TASK_BOOK",
                        "题目：重力沉降室设计\n要求：完成结构设计\n绘制CAD图\n完成论文")));
        DesignProject project = new DesignEnhancementEngine().enhance(new ParameterEngine().normalize(analyzed));
        assertTrue(project.getDetailScore() >= 80);
        assertTrue(project.getComponents().size() >= 15);
        assertTrue(project.getFeatureCount() >= 30);
        assertTrue(project.getComponents().stream().anyMatch(c -> "进风口".equals(c.getName())));
        assertTrue(project.getComponents().stream().anyMatch(c -> "导流板".equals(c.getName())));
        assertTrue(project.getComponents().stream().anyMatch(c -> "顶部护栏".equals(c.getName())));
        assertTrue(project.getComponents().stream().anyMatch(c -> "爬梯".equals(c.getName())));
        assertTrue(project.getComponents().stream().anyMatch(c -> "卸灰口".equals(c.getName())));
        assertTrue(project.allParameters().stream().anyMatch(p -> "处理风量".equals(p.getName())));
        assertTrue(project.getDrawingViews().size() >= 8);
        assertTrue(project.getAnnotationList().size() >= 8);
    }

    @Test
    void drawingPlanDrivesCadPreview() throws Exception {
        DesignProject project = structuredProject();
        assertTrue(project.getCalculations().size() >= 4);
        assertTrue(project.getDrawingPlan().getMainView().getVisibleParts().size() > 0);
        assertTrue(project.getDrawingPlan().getTopView().getVisibleParts().size() > 0);
        assertTrue(project.getDrawingPlan().getSideView().getVisibleParts().size() > 0);
        assertTrue(project.getDrawingPlan().getSectionViews().isEmpty());
        assertTrue(project.getDrawingPlan().getDetailViews().isEmpty());
        byte[] png = new DrawingEngine().drawAssemblyDrawing(project).stream()
                .filter(file -> "cad_preview.png".equals(file.fileName())).findFirst().orElseThrow().content();
        assertTrue(png.length > 1000);
        var image = ImageIO.read(new ByteArrayInputStream(png));
        assertTrue(image.getWidth() >= 1600);
        assertTrue(image.getHeight() >= 1100);
    }
    @Test
    void semanticArchitecturesProduceRecognizableComponents() {
        assertArchitecture("重力沉降室设计", "环保设备结构设计", "排灰斗", "HOPPER");
        assertArchitecture("带式输送机设计", "输送设备设计", "输送带", "BELT");
        assertArchitecture("六自由度机械手设计", "自动化设备设计", "夹爪", "CLAW");
    }

    @Test
    void wallCrawlerRobotTaskDoesNotReuseSedimentationStructures() {
        String task = """
                课题名称：油罐检测爬壁机器人结构设计
                主要功能：油罐壁面爬行、磁吸附稳定附着、表面清扫、检测模块安装、壁面缺陷检测、模块化维护
                技术指标：适用壁面为碳钢、不锈钢；爬行速度0.1～0.5 m/min；吸附力≥200 N；清扫效率≥95%；检测精度≤±0.1 mm；续航时间≥4 h；防护等级IP65；整机尺寸≤800×600×300 mm。
                """;
        DesignProject analyzed = new DesignAnalyzer().analyze("",
                List.of(new DocumentParser.ParsedDocument("任务书.txt", "TASK_BOOK", task)));
        DesignEnhancementEngine enhancementEngine = new DesignEnhancementEngine();
        DesignProject project = enhancementEngine.enhance(new StructureEngine().design(
                new CalculationEngine().calculate(enhancementEngine.enhance(new ParameterEngine().normalize(analyzed)))));
        assertTrue(project.getProjectId().startsWith("dp-"));
        assertTrue(project.getProjectTitle().contains("油罐检测爬壁机器人结构设计"));
        assertTrue(project.getEquipmentName().contains("油罐检测爬壁机器人"));
        assertTrue(project.getDesignType().contains("机器人结构设计"));
        assertTrue(project.getComponents().stream().anyMatch(c -> "履带行走机构".equals(c.getName()) || "左侧履带组件".equals(c.getName())));
        assertTrue(project.getComponents().stream().anyMatch(c -> c.getName().contains("磁吸附")));
        assertTrue(project.getComponents().stream().anyMatch(c -> c.getName().contains("圆盘清扫刷")));
        assertTrue(project.getComponents().stream().anyMatch(c -> c.getName().contains("检测传感器安装架")));
        assertFalse(project.getBom().stream().anyMatch(item -> item.getName().contains("进风口") || item.getName().contains("出风口") || item.getName().contains("排灰斗")));
        assertTrue(project.getCalculations().stream().anyMatch(item -> item.getName().contains("吸附力")));
    }

    @Test
    void taskDrivenPipelineBuildsUnknownFixtureFromStructureTree() {
        DesignProject input = new DesignProject();
        input.setProjectTitle("自动翻转夹具结构设计");
        input.setEquipmentName("自动翻转夹具");
        input.setDesignType("机械结构设计 / 机电一体化设计");
        input.setMainFunctions(List.of("工件夹紧定位", "自动翻转", "角度限位", "快速维护"));
        input.setMainStructures(List.of("夹紧机构", "翻转机构", "驱动电机", "减速器", "传动轴", "定位销", "机架", "防护罩"));
        DesignProject project = pipeline().analyzeNewTask(input);
        assertTrue(project.getStructureTree().getChildren().size() >= 3);
        assertTrue(project.getResolvedParts().stream().anyMatch(p -> "standard".equals(p.getPartType()) && p.getName().contains("电机")));
        assertTrue(project.getResolvedParts().stream().anyMatch(p -> "non_standard".equals(p.getPartType()) && p.getName().contains("夹紧")));
        assertTrue(project.getResolvedParts().stream().filter(p -> "standard".equals(p.getPartType()))
                .anyMatch(p -> p.getSource().contains("mock_provider_pending_real_api") || p.getSource().contains("local_cache")));
        assertTrue(project.getResolvedParts().stream().filter(p -> "standard".equals(p.getPartType()))
                .allMatch(p -> !p.getAvailableFormats().isEmpty() && p.getConfidence() > 0));
        assertTrue(project.getResolvedParts().stream().filter(p -> "standard".equals(p.getPartType()))
                .allMatch(p -> !p.getSourcePlatform().isBlank()
                        && !p.getRetrievalStatus().isBlank()
                        && !p.getAvailableModelFormats().isEmpty()
                        && !p.getTechnicalParams().isEmpty()));
        assertTrue(project.getBom().stream().anyMatch(item -> item.getRemark().contains("平台：") || item.getRemark().contains("状态：")));
        assertTrue(project.getResolvedParts().stream().filter(p -> "non_standard".equals(p.getPartType()))
                .allMatch(p -> "NonStandardPartGenerator".equals(p.getGeneratedBy()) && p.getGeometryFeatures().size() >= 4));
        assertTrue(project.getAssemblyTree().getChildren().size() >= 3);
        assertTrue(project.getBom().size() == project.getComponents().size());
        assertTrue(project.getDetailScore() >= 70);
    }

    private void assertArchitecture(String title, String architecture, String componentName, String geometry) {
        DesignProject analyzed = new DesignAnalyzer().analyze(title,
                List.of(new DocumentParser.ParsedDocument("任务书.txt", "TASK_BOOK", title)));
        DesignProject project = new DesignEnhancementEngine().enhance(new StructureEngine().design(
                new DesignEnhancementEngine().enhance(new ParameterEngine().normalize(analyzed))));
        assertTrue(project.getDesignType().contains(architecture));
        assertTrue(project.getComponents().stream().anyMatch(c -> componentName.equals(c.getName()) && geometry.equals(c.getGeometry())));
        assertTrue(project.getBom().stream().anyMatch(item -> componentName.equals(item.getName())));
    }

    private DesignProject structuredProject() {
        return pipeline().analyzeNewTask(new DesignProject());
    }

    private TaskDrivenDesignPipeline pipeline() {
        ParameterEngine parameterEngine = new ParameterEngine();
        CalculationEngine calculationEngine = new CalculationEngine();
        StandardPartCache cache = new StandardPartCache(new ObjectMapper());
        return new TaskDrivenDesignPipeline(new ProjectSessionReset(), parameterEngine, new ProjectAnalyzer(),
                new StructureTreeBuilder(), new StandardPartSelector(cache, new MockOnlineStandardPartProvider(cache)), new NonStandardPartGenerator(new UnknownPartResolver()),
                new AssemblyBuilder(), new BOMGenerator(), calculationEngine, new DrawingPlanBuilder());
    }
}
