package com.dropai.rewrite;

import com.dropai.rewrite.modules.mechanicalWorkflowMemory.MechanicalWorkflowMemory;
import com.dropai.rewrite.modules.model.DesignProject;
import com.dropai.rewrite.modules.pluginDiscoveryAgent.PluginDiscoveryAgent;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MechanicalWorkflowRefactorTests {
    @Test
    void workflowMemoryReusesRulesWithoutInjectingDimensions() {
        DesignProject project = new DesignProject();
        project.setProjectTitle("油罐检测爬壁机器人");
        project.setMainFunctions(List.of("磁吸附", "壁面移动", "检测"));

        new MechanicalWorkflowMemory().apply(project);

        assertTrue(project.getEnhancementNotes().stream().anyMatch(note -> note.contains("wall-climbing-robot")));
        assertTrue(project.allParameters().isEmpty(), "workflow memory must not copy dimensions from a previous design");
    }

    @Test
    void pluginDiscoveryReportsGapsWithoutInstallingAnything() {
        PluginDiscoveryAgent.DiscoveryReport report = new PluginDiscoveryAgent().inspect(
                Set.of("STEP_EXPORT", "DXF_GENERATION", "FEA_SOLVER"));

        assertFalse(report.ready());
        assertEquals(List.of("FEA_SOLVER"), report.missing());
        assertTrue(report.action().contains("operator approval"));
    }
}
