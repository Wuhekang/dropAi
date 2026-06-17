package com.dropai.rewrite;

import com.dropai.rewrite.modules.calculationEngine.CalculationEngine;
import com.dropai.rewrite.modules.drawingEngine.DrawingEngine;
import com.dropai.rewrite.modules.designAnalyzer.DesignAnalyzer;
import com.dropai.rewrite.modules.documentParser.DocumentParser;
import com.dropai.rewrite.modules.model.DesignProject;
import com.dropai.rewrite.modules.parameterEngine.ParameterEngine;
import com.dropai.rewrite.modules.paperEngine.PaperEngine;
import com.dropai.rewrite.modules.structureEngine.StructureEngine;
import org.junit.jupiter.api.Test;
import org.apache.poi.xwpf.usermodel.XWPFDocument;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

class DesignPackageModuleTests {
    @Test
    void calculationsWriteBackIntoSharedProjectModel() {
        DesignProject project = structuredProject();
        assertTrue(project.getCalculations().size() >= 4);
        assertTrue(project.allParameters().stream().anyMatch(p -> "壳体板厚".equals(p.getName())));
    }

    @Test
    void assemblyDxfContainsEngineeringDrawingLayersAndAnnotations() {
        DesignProject project = structuredProject();
        String dxf = new String(new DrawingEngine().drawAssemblyDrawing(project).get(0).content(), StandardCharsets.UTF_8);
        assertTrue(dxf.contains("2\nDIMENSION\n"));
        assertTrue(dxf.contains("2\nTITLE\n"));
        assertTrue(dxf.contains("2\nBODY\n"));
        assertTrue(dxf.contains("2\nSUPPORT\n"));
        assertTrue(dxf.contains("2\nSECTION\n"));
        assertTrue(dxf.contains("2\nHATCH\n"));
        assertTrue(dxf.contains("2\nCUTTING\n"));
        assertTrue(dxf.contains("2\nTOLERANCE\n"));
        assertTrue(dxf.contains("2\nJOINT\n"));
        assertTrue(dxf.contains("A-A\u5256\u9762"));
        assertTrue(dxf.contains("B-B\u5256\u9762"));
        assertTrue(dxf.contains("\u8f74\u6d4b\u8f85\u52a9\u56fe"));
        assertTrue(dxf.contains("\u5b89\u88c5\u5b54"));
        assertTrue(dxf.contains("\u7c97\u7cd9\u5ea6"));
        assertTrue(dxf.contains("技术要求"));
        assertTrue(dxf.contains("总装图"));
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
            String text = document.getParagraphs().stream().map(p -> p.getText()).reduce("", (a, b) -> a + b);
            assertTrue(text.contains("第1章 绪论"));
            assertTrue(text.contains("第6章 结论与展望"));
            assertTrue(text.contains("参考文献"));
            assertTrue(text.replaceAll("\\s+", "").length() >= 8000);
        }
    }

    @Test
    void semanticArchitecturesProduceRecognizableComponents() {
        assertArchitecture("重力沉降室设计", "沉降分离设备", "排灰斗", "HOPPER");
        assertArchitecture("带式输送机设计", "带式输送设备", "输送带", "BELT");
        assertArchitecture("六自由度机械手设计", "关节机械手", "夹爪", "CLAW");
    }

    private void assertArchitecture(String title, String architecture, String componentName, String geometry) {
        DesignProject analyzed = new DesignAnalyzer().analyze(title,
                List.of(new DocumentParser.ParsedDocument("任务书.txt", "TASK_BOOK", title)));
        DesignProject project = new StructureEngine().design(new ParameterEngine().normalize(analyzed));
        assertTrue(architecture.equals(project.getDesignType()));
        assertTrue(project.getComponents().stream().anyMatch(c -> componentName.equals(c.getName()) && geometry.equals(c.getGeometry())));
        assertTrue(project.getBom().stream().anyMatch(item -> componentName.equals(item.getName())));
    }

    private DesignProject structuredProject() {
        return new StructureEngine().design(new CalculationEngine().calculate(new ParameterEngine().normalize(new DesignProject())));
    }
}
