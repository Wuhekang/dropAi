package com.dropai.rewrite.modules.assemblyBuilder;

import com.dropai.rewrite.modules.assemblyConstraintEngine.AssemblyConstraintEngine;
import com.dropai.rewrite.modules.assemblyModel.AssemblyModel;
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
        project.setAssemblyModel(toAssemblyModel(project));
        validate(project);
        return project;
    }

    private AssemblyModel toAssemblyModel(DesignProject project) {
        AssemblyModel model = new AssemblyModel();
        model.setProjectName(project.getEquipmentName() == null || project.getEquipmentName().isBlank()
                ? project.getProjectTitle() : project.getEquipmentName());
        model.setCoordinateSystem(new LinkedHashMap<>(Map.of(
                "x", "overall length direction",
                "y", "overall width direction",
                "z", "overall height direction"
        )));
        model.setComponents(project.getComponents().stream().map(this::toAssemblyComponent).toList());
        model.setConstraints(project.getAssemblyConstraints().stream().map(this::toAssemblyConstraint).toList());
        if (model.getComponents().isEmpty()) model.getValidationMessages().add("assembly incomplete: components=0");
        if (!model.getComponents().isEmpty() && model.getConstraints().isEmpty()) model.getValidationMessages().add("assembly incomplete: constraints=0");
        return model;
    }

    private AssemblyModel.AssemblyComponent toAssemblyComponent(DesignProject.Component component) {
        AssemblyModel.AssemblyComponent item = new AssemblyModel.AssemblyComponent();
        item.setId(component.getPartId());
        item.setName(component.getName());
        item.setType(component.getRole());
        item.setSource(component.getParentAssembly());
        item.setPosition(new AssemblyModel.Pose(component.getX(), component.getY(), component.getZ()));
        item.setRotation(new AssemblyModel.Pose(component.getRotation().getX(), component.getRotation().getY(), component.getRotation().getZ()));
        item.setSize(new AssemblyModel.Size(component.getLength(), component.getWidth(), component.getHeight()));
        item.setParameters(new LinkedHashMap<>(Map.of(
                "geometry", component.getGeometry(),
                "quantity", component.getQuantity(),
                "mountTo", component.getMountTo(),
                "constraintType", component.getConstraintType(),
                "material", component.getMaterial(),
                "modelingMethod", component.getModelingMethod()
        )));
        return item;
    }

    private AssemblyModel.AssemblyConstraint toAssemblyConstraint(DesignProject.AssemblyConstraint constraint) {
        AssemblyModel.AssemblyConstraint item = new AssemblyModel.AssemblyConstraint();
        item.setType(constraint.getConstraintType());
        item.setComponentA(constraint.getPartId());
        item.setComponentB(constraint.getMountTo());
        item.setRelation(constraint.getPartName() + " -> " + constraint.getMountTo());
        return item;
    }

    private String basePart(List<DesignProject.Component> components) {
        return components.stream().filter(c -> c.getName().contains("机架")).map(DesignProject.Component::getName)
                .findFirst().orElse(components.isEmpty() ? "" : components.get(0).getName());
    }

    private void validate(DesignProject project) {
        if (project.getComponents().isEmpty()) throw new IllegalStateException("装配约束缺失，模型未生成。");
        if (project.getAssemblyConstraints().isEmpty()) throw new IllegalStateException("assembly incomplete: constraints=0");
        for (DesignProject.Component component : project.getComponents()) {
            if (component.getPartId() == null || component.getPartId().isBlank()
                    || component.getParentAssembly() == null || component.getParentAssembly().isBlank()
                    || component.getConstraintType() == null || component.getConstraintType().isBlank()) {
                throw new IllegalStateException("装配约束缺失，模型未生成。");
            }
        }
    }
}
