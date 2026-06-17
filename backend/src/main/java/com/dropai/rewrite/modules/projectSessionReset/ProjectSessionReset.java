package com.dropai.rewrite.modules.projectSessionReset;

import com.dropai.rewrite.modules.model.DesignProject;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class ProjectSessionReset {
    public DesignProject resetForNewTask(DesignProject project) {
        if (project == null) project = new DesignProject();
        project.setProjectId("dp-" + UUID.randomUUID());
        clearGenerated(project);
        return project;
    }

    public DesignProject ensureCurrentSession(DesignProject project) {
        if (project == null) project = new DesignProject();
        if (project.getProjectId() == null || project.getProjectId().isBlank()) project.setProjectId("dp-" + UUID.randomUUID());
        return project;
    }

    private void clearGenerated(DesignProject project) {
        project.getComponents().clear();
        project.getBom().clear();
        project.getCalculations().clear();
        project.getDimensionChains().clear();
        project.getResolvedParts().clear();
        project.setStructureTree(new DesignProject.StructureNode("整机", "root", "ProjectSessionReset", 1.0));
        project.setAssemblyTree(new DesignProject.AssemblyNode("整机", "root"));
    }
}
