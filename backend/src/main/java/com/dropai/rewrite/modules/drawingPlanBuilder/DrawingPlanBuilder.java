package com.dropai.rewrite.modules.drawingPlanBuilder;

import com.dropai.rewrite.modules.model.DesignProject;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class DrawingPlanBuilder {
    public DesignProject build(DesignProject project) {
        if (project.getAssemblyTree() == null || project.getAssemblyTree().getChildren().isEmpty()) {
            throw new IllegalStateException("DrawingPlan生成失败：AssemblyTree为空");
        }
        if (project.getAssemblyConstraints() == null || project.getAssemblyConstraints().isEmpty()) {
            throw new IllegalStateException("DrawingPlan生成失败：ConstraintList为空");
        }
        if (project.getComponents() == null || project.getComponents().isEmpty()) {
            throw new IllegalStateException("DrawingPlan生成失败：Components为空");
        }

        DesignProject.DrawingPlan plan = new DesignProject.DrawingPlan();
        plan.setInputSource("DrawingPlan");
        plan.setMainView(view("mainView", project, "front"));
        plan.setTopView(view("topView", project, "top"));
        plan.setSideView(view("sideView", project, "side"));
        plan.setSectionViews(List.of(sectionView(project)));
        plan.setDetailViews(List.of(detailView(project)));
        plan.setIsometricView(isometricView(project));
        plan.setBomTable(new ArrayList<>(project.getBom()));
        plan.setTechnicalRequirements(technicalRequirements(project));
        plan.setTitleBlock(titleBlock(project));
        plan.setParameterTable(project.allParameters().stream().limit(12).toList());
        project.setDrawingPlan(plan);
        ensureNonEmpty(plan);
        return project;
    }

    private DesignProject.DrawingViewPlan view(String name, DesignProject project, String orientation) {
        DesignProject.DrawingViewPlan view = new DesignProject.DrawingViewPlan(name);
        List<DesignProject.Component> components = project.getComponents();
        List<String> visible = components.stream()
                .filter(component -> visibleIn(orientation, component))
                .map(DesignProject.Component::getPartId)
                .distinct()
                .toList();
        if (visible.isEmpty()) visible = components.stream().limit(Math.min(12, components.size())).map(DesignProject.Component::getPartId).toList();
        final List<String> visibleIds = visible;
        view.setVisibleParts(visibleIds);
        view.setHiddenParts(components.stream().map(DesignProject.Component::getPartId).filter(id -> !visibleIds.contains(id)).toList());
        view.setDimensions(dimensions(project, orientation, visibleIds));
        view.setLabels(labels(project, visibleIds));
        view.setCenterLines(centerLines(project, visibleIds));
        if ("front".equals(orientation)) view.setSectionMarkers(List.of("A-A: pass through base frame and drive/mounting structure"));
        return view;
    }

    private boolean visibleIn(String orientation, DesignProject.Component component) {
        String role = component.getRole() == null ? "" : component.getRole();
        String geometry = component.getGeometry() == null ? "" : component.getGeometry();
        if ("front".equals(orientation)) return component.isKeyPart() || role.matches(".*(DRIVE|SUPPORT|FUNCTION|MOUNT).*");
        if ("top".equals(orientation)) return component.isKeyPart() || geometry.matches(".*(TRACK|SENSOR_RAIL|COVER|FRAME).*");
        return component.isKeyPart() || geometry.matches(".*(WHEEL|MOTOR|GEARBOX|MAGNET|TRACK).*");
    }

    private List<DesignProject.DimensionChain> dimensions(DesignProject project, String orientation, List<String> visible) {
        List<DesignProject.DimensionChain> result = new ArrayList<>();
        double length = project.number("整机长度", project.number("总长", project.number("总长度", 800)));
        double width = project.number("整机宽度", project.number("总宽", project.number("总宽度", 600)));
        double height = project.number("整机高度", project.number("总高", project.number("总高度", 300)));
        if ("front".equals(orientation)) {
            result.add(new DesignProject.DimensionChain("整机长度", length, "mm", "整机", source(project, "整机长度", "component.position + component.size")));
            result.add(new DesignProject.DimensionChain("整机高度", height, "mm", "整机", source(project, "整机高度", "component.position + component.size")));
        } else if ("top".equals(orientation)) {
            result.add(new DesignProject.DimensionChain("整机长度", length, "mm", "整机", source(project, "整机长度", "component.position + component.size")));
            result.add(new DesignProject.DimensionChain("整机宽度", width, "mm", "整机", source(project, "整机宽度", "component.position + component.size")));
        } else {
            result.add(new DesignProject.DimensionChain("整机宽度", width, "mm", "整机", source(project, "整机宽度", "component.position + component.size")));
            result.add(new DesignProject.DimensionChain("整机高度", height, "mm", "整机", source(project, "整机高度", "component.position + component.size")));
        }
        project.getComponents().stream()
                .filter(component -> visible.contains(component.getPartId()))
                .filter(DesignProject.Component::isKeyPart)
                .limit(5)
                .forEach(component -> result.add(new DesignProject.DimensionChain(component.getName() + "安装尺寸",
                        Math.max(component.getLength(), Math.max(component.getWidth(), component.getHeight())),
                        "mm", component.getPartId(),
                        "AssemblyConstraint: " + component.getConstraintType() + ", mountTo=" + component.getMountTo())));
        return result;
    }

    private String source(DesignProject project, String parameterName, String fallback) {
        return project.allParameters().stream()
                .filter(parameter -> parameterName.equals(parameter.getName()))
                .findFirst()
                .map(parameter -> {
                    String source = parameter.getSource() == null || parameter.getSource().isBlank() ? parameter.getBasis() : parameter.getSource();
                    return source == null || source.isBlank() ? fallback : source;
                })
                .orElse(fallback);
    }

    private List<String> labels(DesignProject project, List<String> visible) {
        return project.getComponents().stream()
                .filter(component -> visible.contains(component.getPartId()))
                .filter(DesignProject.Component::isKeyPart)
                .limit(10)
                .map(component -> component.getSequence() + " " + component.getName())
                .toList();
    }

    private List<String> centerLines(DesignProject project, List<String> visible) {
        return project.getComponents().stream()
                .filter(component -> visible.contains(component.getPartId()))
                .filter(component -> {
                    String geometry = component.getGeometry() == null ? "" : component.getGeometry();
                    return geometry.contains("WHEEL") || geometry.contains("MOTOR") || geometry.contains("GEARBOX") || geometry.contains("BRUSH");
                })
                .map(component -> component.getPartId() + ": axis")
                .toList();
    }

    private DesignProject.DrawingViewPlan sectionView(DesignProject project) {
        DesignProject.DrawingViewPlan view = new DesignProject.DrawingViewPlan("A-A section");
        List<String> ids = project.getComponents().stream()
                .filter(component -> {
                    String role = component.getRole() == null ? "" : component.getRole();
                    String geometry = component.getGeometry() == null ? "" : component.getGeometry();
                    return role.matches(".*(SUPPORT|DRIVE|MOUNT).*") || geometry.matches(".*(FRAME|WHEEL|MAGNET|MOTOR).*");
                })
                .map(DesignProject.Component::getPartId)
                .limit(10)
                .toList();
        view.setVisibleParts(ids);
        view.setLabels(List.of("A-A剖视图：表达内部安装板、轴/轮、支撑或壳体关系"));
        view.setCenterLines(ids.stream().map(id -> id + ": section center").toList());
        view.setDimensions(List.of(new DesignProject.DimensionChain("剖面板厚/安装高度", project.number("板厚", project.number("壳体板厚", 6)),
                "mm", "A-A", "component.size + material/process requirement")));
        return view;
    }

    private DesignProject.DrawingViewPlan detailView(DesignProject project) {
        DesignProject.DrawingViewPlan view = new DesignProject.DrawingViewPlan("Detail I");
        List<String> ids = project.getComponents().stream()
                .filter(component -> {
                    String name = component.getName() == null ? "" : component.getName();
                    String geometry = component.getGeometry() == null ? "" : component.getGeometry();
                    return name.contains("轮") || name.contains("磁") || name.contains("法兰") || name.contains("轴承") || geometry.matches(".*(WHEEL|MAGNET|FLANGE|BOLT).*");
                })
                .map(DesignProject.Component::getPartId)
                .limit(8)
                .toList();
        view.setVisibleParts(ids.isEmpty() ? project.getComponents().stream().limit(4).map(DesignProject.Component::getPartId).toList() : ids);
        view.setLabels(List.of("局部放大图：关键安装位置、孔位、轴线与连接件"));
        view.setDimensions(List.of(new DesignProject.DimensionChain("关键安装孔距", mountingDistance(project), "mm", "Detail I", "assemblyConstraints + mounting distances")));
        return view;
    }

    private double mountingDistance(DesignProject project) {
        return project.getComponents().stream().filter(DesignProject.Component::isKeyPart).limit(2)
                .mapToDouble(component -> component.getX() + component.getLength() / 2)
                .reduce((a, b) -> Math.abs(a - b)).orElse(project.number("安装孔距", 120));
    }

    private DesignProject.DrawingViewPlan isometricView(DesignProject project) {
        DesignProject.DrawingViewPlan view = new DesignProject.DrawingViewPlan("isometricView");
        view.setVisibleParts(project.getComponents().stream().filter(DesignProject.Component::isKeyPart)
                .limit(10).map(DesignProject.Component::getPartId).toList());
        view.setLabels(List.of("轴测图：辅助表达整机装配空间关系"));
        return view;
    }

    private List<String> technicalRequirements(DesignProject project) {
        List<String> result = new ArrayList<>(project.getTechnicalRequirements());
        result.add("CAD图纸由DrawingPlan驱动生成，视图、尺寸、BOM与装配树保持一致。");
        result.add("尺寸来源包括任务书参数、推导参数、零件尺寸、装配约束和安装距离。");
        return result.stream().filter(item -> item != null && !item.isBlank()).distinct().limit(8).toList();
    }

    private Map<String, String> titleBlock(DesignProject project) {
        Map<String, String> title = new LinkedHashMap<>();
        title.put("drawingName", "总装工程图");
        title.put("drawingNo", "ZD-00");
        title.put("scale", "1:10");
        title.put("projectTitle", project.getProjectTitle());
        title.put("equipmentName", project.getEquipmentName());
        title.put("source", "DrawingPlan");
        return title;
    }

    private void ensureNonEmpty(DesignProject.DrawingPlan plan) {
        if (plan.getMainView().getVisibleParts().isEmpty()
                || plan.getTopView().getVisibleParts().isEmpty()
                || plan.getSideView().getVisibleParts().isEmpty()
                || plan.getSectionViews().isEmpty()
                || plan.getDetailViews().isEmpty()) {
            throw new IllegalStateException("DrawingPlan为空，禁止生成CAD");
        }
    }
}
