package com.dropai.rewrite.modules.assemblyBuilder;

import com.dropai.rewrite.modules.assemblyConstraintEngine.AssemblyConstraintEngine;
import com.dropai.rewrite.modules.model.DesignProject;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class AssemblyBuilder {
    private final AssemblyConstraintEngine constraintEngine;

    public AssemblyBuilder() {
        this(new AssemblyConstraintEngine());
    }

    public AssemblyBuilder(AssemblyConstraintEngine constraintEngine) {
        this.constraintEngine = constraintEngine;
    }

    public DesignProject build(DesignProject project) {
        AssemblyConstraintEngine.AssemblyResult solved = constraintEngine.solve(project);
        project.setComponents(solved.components());
        project.setAssemblyConstraints(solved.constraints());

        DesignProject.AssemblyNode root = new DesignProject.AssemblyNode("整机", "root");
        root.setAssemblyName((project.getEquipmentName() == null || project.getEquipmentName().isBlank() ? "机械设备" : project.getEquipmentName()) + "整机");
        root.setBasePart(basePart(project.getComponents()));
        root.setCoordinateSystem(new LinkedHashMap<>(Map.of("x", "length", "y", "width", "z", "height")));

        Map<String, DesignProject.AssemblyNode> parents = new LinkedHashMap<>();
        for (DesignProject.Component component : project.getComponents()) {
            String parentName = component.getParentAssembly() == null || component.getParentAssembly().isBlank()
                    ? "整机装配" : component.getParentAssembly();
            DesignProject.AssemblyNode parent = parents.computeIfAbsent(parentName, key -> {
                DesignProject.AssemblyNode node = new DesignProject.AssemblyNode(key, "subAssembly");
                root.getChildren().add(node);
                return node;
            });
            DesignProject.AssemblyNode child = new DesignProject.AssemblyNode(component.getName(), component.getConstraintType());
            child.setBasePart(component.getMountTo());
            parent.getChildren().add(child);
        }
        project.setAssemblyTree(root);
        validate(project);
        return project;
    }

    private String basePart(List<DesignProject.Component> components) {
        return components.stream().filter(c -> c.getName().contains("机架")).map(DesignProject.Component::getName)
                .findFirst().orElse(components.isEmpty() ? "" : components.get(0).getName());
    }

    private void validate(DesignProject project) {
        if (project.getComponents().isEmpty()) throw new IllegalStateException("装配约束缺失，模型未生成。");
        for (DesignProject.Component component : project.getComponents()) {
            if (component.getPartId() == null || component.getPartId().isBlank()
                    || component.getParentAssembly() == null || component.getParentAssembly().isBlank()
                    || component.getConstraintType() == null || component.getConstraintType().isBlank()) {
                throw new IllegalStateException("装配约束缺失，模型未生成。");
            }
        }
    }
}
