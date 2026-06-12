package com.dropai.rewrite;

import com.dropai.rewrite.modules.calculationEngine.CalculationEngine;
import com.dropai.rewrite.modules.drawingEngine.DrawingEngine;
import com.dropai.rewrite.modules.model.DesignProject;
import com.dropai.rewrite.modules.parameterEngine.ParameterEngine;
import com.dropai.rewrite.modules.paperEngine.PaperEngine;
import org.junit.jupiter.api.Test;
import org.apache.poi.xwpf.usermodel.XWPFDocument;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertTrue;

class DesignPackageModuleTests {
    @Test
    void calculationsWriteBackIntoSharedProjectModel() {
        DesignProject project = new CalculationEngine().calculate(new ParameterEngine().normalize(new DesignProject()));
        assertTrue(project.getCalculations().size() >= 4);
        assertTrue(project.allParameters().stream().anyMatch(p -> "壳体板厚".equals(p.getName())));
    }

    @Test
    void assemblyDxfContainsEngineeringDrawingLayersAndAnnotations() {
        DesignProject project = new CalculationEngine().calculate(new ParameterEngine().normalize(new DesignProject()));
        String dxf = new String(new DrawingEngine().drawAssemblyDrawing(project).get(0).content(), StandardCharsets.UTF_8);
        assertTrue(dxf.contains("2\nDIMENSION\n"));
        assertTrue(dxf.contains("2\nTITLE\n"));
        assertTrue(dxf.contains("技术要求"));
        assertTrue(dxf.contains("总装图"));
        assertTrue(new DrawingEngine().drawAssemblyDrawing(project).stream()
                .filter(file -> "cad_preview.png".equals(file.fileName())).findFirst().orElseThrow().content().length > 1000);
    }

    @Test
    void fallbackPaperMeetsMinimumStructureAndLength() throws Exception {
        DesignProject project = new CalculationEngine().calculate(new ParameterEngine().normalize(new DesignProject()));
        byte[] bytes = new PaperEngine().generatePaper(project);
        try (XWPFDocument document = new XWPFDocument(new ByteArrayInputStream(bytes))) {
            String text = document.getParagraphs().stream().map(p -> p.getText()).reduce("", (a, b) -> a + b);
            assertTrue(text.contains("第1章 绪论"));
            assertTrue(text.contains("第6章 结论与展望"));
            assertTrue(text.contains("参考文献"));
            assertTrue(text.replaceAll("\\s+", "").length() >= 8000);
        }
    }
}
