package com.dropai.rewrite;

import com.dropai.rewrite.modules.calculationEngine.CalculationEngine;
import com.dropai.rewrite.modules.drawingEngine.DrawingEngine;
import com.dropai.rewrite.modules.model.DesignProject;
import com.dropai.rewrite.modules.parameterEngine.ParameterEngine;
import org.junit.jupiter.api.Test;

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
    }
}
