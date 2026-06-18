package com.dropai.rewrite.modules.drawingPlanBuilder;

import com.dropai.rewrite.modules.model.DesignProject;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

@Service
public class DrawingPlanBuilder {
    private static final int MAX_MAIN_PARTS = 12;
    private static final int MAX_TOP_PARTS = 10;
    private static final int MAX_SIDE_PARTS = 10;

    public DesignProject build(DesignProject project) {
        requireSource(project);

        DesignProject.DrawingPlan plan = new DesignProject.DrawingPlan();
        plan.setInputSource("DrawingPlan");
        plan.setTitleBlock(titleBlock(project));
        plan.setMainView(view(project, "mainView", "FRONT", "show frame, shell, track/drive train, cleaning and detection modules",
                viewport(55, 315, 430, 175), MAX_MAIN_PARTS, this::mainViewPart, mainDimensions(project)));
        plan.setTopView(view(project, "topView", "TOP", "show left/right layout, frame outline, battery bay, brush and sensor positions",
                viewport(55, 105, 430, 135), MAX_TOP_PARTS, this::topViewPart, topDimensions(project)));
        plan.setSideView(view(project, "sideView", "SIDE", "show track height, wheel train, magnetic modules, cover and sensor height",
                viewport(515, 215, 170, 175), MAX_SIDE_PARTS, this::sideViewPart, sideDimensions(project)));
        plan.setSectionViews(List.of(sectionView(project)));
        plan.setDetailViews(List.of(detailView(project)));
        plan.setIsometricView(isometricView(project));
        plan.setBomTable(bomForVisibleParts(project, plan));
        plan.setParameterTable(parameterTable(project));
        plan.setTechnicalRequirements(technicalRequirements(project));
        score(plan);
        qualityGate(plan);
        project.setDrawingPlan(plan);
        return project;
    }

    private void requireSource(DesignProject project) {
        if (project.getAssemblyTree() == null || project.getAssemblyTree().getChildren().isEmpty()) {
            throw new IllegalStateException("DrawingPlan build failed: AssemblyTree is empty");
        }
        if (project.getAssemblyConstraints() == null || project.getAssemblyConstraints().isEmpty()) {
            throw new IllegalStateException("DrawingPlan build failed: ConstraintList is empty");
        }
        if (project.getComponents() == null || project.getComponents().isEmpty()) {
            throw new IllegalStateException("DrawingPlan build failed: Components is empty");
        }
    }

    private DesignProject.DrawingViewPlan view(DesignProject project, String name, String orientation, String purpose,
                                               Map<String, Double> viewport, int maxParts,
                                               Predicate<DesignProject.Component> selector,
                                               List<DesignProject.DimensionChain> dimensions) {
        DesignProject.DrawingViewPlan view = new DesignProject.DrawingViewPlan(name);
        view.setPurpose(purpose);
        view.setLevelOfDetail("engineering_simplified");
        view.setViewport(viewport);
        List<DesignProject.Component> selected = select(project, selector, maxParts);
        view.setVisibleParts(selected.stream().map(DesignProject.Component::getPartId).toList());
        view.setHiddenParts(project.getComponents().stream()
                .map(DesignProject.Component::getPartId)
                .filter(id -> !view.getVisibleParts().contains(id)).toList());
        view.setDimensions(dimensions);
        view.setLabels(labels(selected, maxParts));
        view.setCenterLines(centerLines(selected, orientation));
        if ("FRONT".equals(orientation)) {
            view.setSectionMarkers(List.of("A-A section through wheel train / magnetic mounting zone"));
        }
        return view;
    }

    private List<DesignProject.Component> select(DesignProject project, Predicate<DesignProject.Component> selector, int maxParts) {
        List<DesignProject.Component> selected = project.getComponents().stream()
                .filter(selector)
                .sorted(Comparator.comparing(DesignProject.Component::isKeyPart).reversed()
                        .thenComparingInt(DesignProject.Component::getSequence))
                .toList();
        if (selected.size() < 3) {
            selected = project.getComponents().stream()
                    .filter(component -> component.isKeyPart() || isBaseStructure(component))
                    .sorted(Comparator.comparingInt(DesignProject.Component::getSequence))
                    .limit(maxParts)
                    .toList();
        }
        return uniqueByName(selected).stream().limit(maxParts).toList();
    }

    private boolean mainViewPart(DesignProject.Component component) {
        return isFrame(component) || isCover(component) || isTrack(component) || isWheel(component)
                || isBrush(component) || isSensor(component) || isMotor(component) || isMount(component);
    }

    private boolean topViewPart(DesignProject.Component component) {
        return isTrack(component) || isFrame(component) || isCover(component) || isSensor(component)
                || isBrush(component) || nameHas(component, "电池", "控制", "battery", "control");
    }

    private boolean sideViewPart(DesignProject.Component component) {
        return isTrack(component) || isWheel(component) || isMagnet(component) || isCover(component)
                || isSensor(component) || isFrame(component) || isMotor(component);
    }

    private DesignProject.DrawingViewPlan sectionView(DesignProject project) {
        List<DesignProject.Component> selected = select(project,
                component -> isWheel(component) || isTrack(component) || isMagnet(component) || isFrame(component),
                6);
        DesignProject.DrawingViewPlan view = new DesignProject.DrawingViewPlan("A-A section");
        view.setPurpose("express one key internal mechanism instead of cutting the whole machine");
        view.setLevelOfDetail("section_simplified");
        view.setViewport(viewport(360, 465, 135, 65));
        view.setVisibleParts(selected.stream().map(DesignProject.Component::getPartId).toList());
        view.setLabels(List.of("A-A: wheel/track or magnetic mounting section", "hatch shows cut plates only"));
        view.setCenterLines(centerLines(selected, "SECTION"));
        view.setDimensions(List.of(dimension("section plate thickness", plateThickness(project), "mm", "A-A", "component.size + material/process requirement")));
        return view;
    }

    private DesignProject.DrawingViewPlan detailView(DesignProject project) {
        List<DesignProject.Component> selected = select(project,
                component -> isMagnet(component) || isWheel(component) || isMotor(component) || nameHas(component, "孔", "座", "法兰", "轴承", "连接"),
                4);
        DesignProject.DrawingViewPlan view = new DesignProject.DrawingViewPlan("Detail I");
        view.setPurpose("enlarge one connection / hole / mounting seat");
        view.setLevelOfDetail("detail_symbolic");
        view.setViewport(viewport(525, 445, 130, 55));
        view.setVisibleParts(selected.stream().map(DesignProject.Component::getPartId).toList());
        view.setLabels(List.of("Detail I: mounting holes, axis and fasteners", "not a whole-machine projection"));
        view.setDimensions(List.of(dimension("mounting hole pitch", mountingDistance(selected, project), "mm", "Detail I", "assemblyConstraints + mounting distances")));
        return view;
    }

    private DesignProject.DrawingViewPlan isometricView(DesignProject project) {
        List<DesignProject.Component> selected = select(project,
                component -> component.isKeyPart() && (isFrame(component) || isTrack(component) || isCover(component) || isBrush(component) || isSensor(component) || isMagnet(component)),
                7);
        DesignProject.DrawingViewPlan view = new DesignProject.DrawingViewPlan("isometricView");
        view.setPurpose("auxiliary structure understanding only");
        view.setLevelOfDetail("concept_isometric");
        view.setViewport(viewport(690, 390, 105, 105));
        view.setVisibleParts(selected.stream().map(DesignProject.Component::getPartId).toList());
        view.setLabels(List.of("isometric auxiliary view", "key assemblies only"));
        return view;
    }

    private List<DesignProject.DimensionChain> mainDimensions(DesignProject project) {
        return List.of(
                dimension("overall length", overallLength(project), "mm", "whole machine", source(project, "整机长度", "component envelope")),
                dimension("overall height", overallHeight(project), "mm", "whole machine", source(project, "整机高度", "component envelope")),
                dimension("track center distance", trackCenterDistance(project), "mm", "track assembly", "component.position + assembly constraints"),
                dimension("brush diameter", brushSize(project), "mm", "brush", "component.size")
        );
    }

    private List<DesignProject.DimensionChain> topDimensions(DesignProject project) {
        return List.of(
                dimension("overall width", overallWidth(project), "mm", "whole machine", source(project, "整机宽度", "component envelope")),
                dimension("track width", trackWidth(project), "mm", "track assembly", "component.size"),
                dimension("left/right track spacing", trackCenterDistance(project), "mm", "track assembly", "component.position + assembly constraints"),
                dimension("module offset", moduleOffset(project), "mm", "sensor/brush module", "component.position")
        );
    }

    private List<DesignProject.DimensionChain> sideDimensions(DesignProject project) {
        return List.of(
                dimension("overall height", overallHeight(project), "mm", "whole machine", source(project, "整机高度", "component envelope")),
                dimension("track height", trackHeight(project), "mm", "track assembly", "component.size"),
                dimension("magnet ground clearance", magnetClearance(project), "mm", "magnetic module", "component.position"),
                dimension("cover height", coverHeight(project), "mm", "cover", "component.size")
        );
    }

    private List<DesignProject.BomItem> bomForVisibleParts(DesignProject project, DesignProject.DrawingPlan plan) {
        Set<String> visibleIds = new LinkedHashSet<>();
        visibleIds.addAll(plan.getMainView().getVisibleParts());
        visibleIds.addAll(plan.getTopView().getVisibleParts());
        visibleIds.addAll(plan.getSideView().getVisibleParts());
        plan.getSectionViews().forEach(view -> visibleIds.addAll(view.getVisibleParts()));
        plan.getDetailViews().forEach(view -> visibleIds.addAll(view.getVisibleParts()));
        visibleIds.addAll(plan.getIsometricView().getVisibleParts());
        return project.getComponents().stream()
                .filter(component -> visibleIds.contains(component.getPartId()))
                .sorted(Comparator.comparingInt(DesignProject.Component::getSequence))
                .map(component -> new DesignProject.BomItem(component.getSequence(), component.getName(), component.getMaterial(), component.getQuantity(), component.getFunction()))
                .limit(18)
                .toList();
    }

    private List<DesignProject.Parameter> parameterTable(DesignProject project) {
        return project.allParameters().stream()
                .filter(parameter -> nameContains(parameter.getName(), "整机", "总", "速度", "吸附", "精度", "功率", "板厚"))
                .limit(8)
                .toList();
    }

    private List<String> technicalRequirements(DesignProject project) {
        List<String> result = new ArrayList<>();
        result.add("DrawingPlan uses key assemblies only; hidden small parts are expressed by symbols or detail views.");
        result.add("Dimensions are filtered to overall, mounting, interface and key mechanism dimensions.");
        result.add("BOM item numbers correspond to visible balloons in main/top/side/section/detail views.");
        result.add("Section and detail views express local structure, not whole-machine projection.");
        result.addAll(project.getTechnicalRequirements().stream().limit(4).toList());
        return result.stream().filter(item -> item != null && !item.isBlank()).distinct().limit(8).toList();
    }

    private Map<String, String> titleBlock(DesignProject project) {
        Map<String, String> title = new LinkedHashMap<>();
        title.put("drawingName", "Assembly Engineering Drawing");
        title.put("drawingNo", "ZD-00");
        title.put("scale", "1:10");
        title.put("projectTitle", project.getProjectTitle());
        title.put("equipmentName", project.getEquipmentName());
        title.put("source", "DrawingPlan");
        return title;
    }

    private void score(DesignProject.DrawingPlan plan) {
        int score = 100;
        score -= Math.max(0, plan.getMainView().getVisibleParts().size() - MAX_MAIN_PARTS) * 4;
        score -= Math.max(0, plan.getTopView().getVisibleParts().size() - MAX_TOP_PARTS) * 4;
        score -= Math.max(0, plan.getSideView().getVisibleParts().size() - MAX_SIDE_PARTS) * 4;
        if (plan.getSectionViews().isEmpty() || plan.getSectionViews().get(0).getVisibleParts().isEmpty()) score -= 25;
        if (plan.getDetailViews().isEmpty() || plan.getDetailViews().get(0).getVisibleParts().isEmpty()) score -= 25;
        if (plan.getBomTable().isEmpty()) score -= 15;
        plan.setQualityScore(Math.max(0, score));
        plan.setQualityNotes(List.of(
                "main visible=" + plan.getMainView().getVisibleParts().size(),
                "top visible=" + plan.getTopView().getVisibleParts().size(),
                "side visible=" + plan.getSideView().getVisibleParts().size(),
                "qualityScore=" + Math.max(0, score)));
    }

    private void qualityGate(DesignProject.DrawingPlan plan) {
        if (plan.getMainView().getVisibleParts().size() < 3
                || plan.getTopView().getVisibleParts().size() < 3
                || plan.getSideView().getVisibleParts().size() < 3
                || plan.getMainView().getVisibleParts().size() > MAX_MAIN_PARTS
                || plan.getTopView().getVisibleParts().size() > MAX_TOP_PARTS
                || plan.getSideView().getVisibleParts().size() > MAX_SIDE_PARTS
                || plan.getSectionViews().isEmpty()
                || plan.getDetailViews().isEmpty()
                || plan.getBomTable().isEmpty()) {
            throw new IllegalStateException("DrawingPlan quality gate failed: view planning is not clear enough");
        }
    }

    private List<DesignProject.Component> uniqueByName(List<DesignProject.Component> input) {
        Set<String> names = new LinkedHashSet<>();
        List<DesignProject.Component> result = new ArrayList<>();
        for (DesignProject.Component component : input) {
            String key = normalized(component.getName());
            if (names.add(key)) result.add(component);
        }
        return result;
    }

    private List<String> labels(List<DesignProject.Component> selected, int max) {
        return selected.stream()
                .filter(DesignProject.Component::isKeyPart)
                .limit(Math.min(6, max))
                .map(component -> component.getSequence() + " " + component.getName())
                .toList();
    }

    private List<String> centerLines(List<DesignProject.Component> selected, String orientation) {
        return selected.stream()
                .filter(component -> isWheel(component) || isMotor(component) || isBrush(component))
                .limit(5)
                .map(component -> component.getPartId() + " axis / " + orientation)
                .toList();
    }

    private Map<String, Double> viewport(double x, double y, double w, double h) {
        Map<String, Double> map = new LinkedHashMap<>();
        map.put("x", x);
        map.put("y", y);
        map.put("width", w);
        map.put("height", h);
        return map;
    }

    private DesignProject.DimensionChain dimension(String name, double value, String unit, String related, String source) {
        return new DesignProject.DimensionChain(name, value, unit, related, source);
    }

    private String source(DesignProject project, String parameterName, String fallback) {
        return project.allParameters().stream()
                .filter(parameter -> parameterName.equals(parameter.getName()) || parameter.getName().contains(parameterName))
                .findFirst()
                .map(parameter -> {
                    String source = parameter.getSource() == null || parameter.getSource().isBlank() ? parameter.getBasis() : parameter.getSource();
                    return source == null || source.isBlank() ? fallback : source;
                })
                .orElse(fallback);
    }

    private double overallLength(DesignProject project) { return project.number("整机长度", project.number("总长", envelope(project, "x"))); }
    private double overallWidth(DesignProject project) { return project.number("整机宽度", project.number("总宽", envelope(project, "y"))); }
    private double overallHeight(DesignProject project) { return project.number("整机高度", project.number("总高", envelope(project, "z"))); }

    private double envelope(DesignProject project, String axis) {
        return project.getComponents().stream().mapToDouble(component -> switch (axis) {
            case "x" -> component.getX() + component.getLength();
            case "y" -> component.getY() + component.getWidth();
            default -> component.getZ() + component.getHeight();
        }).max().orElse(1000);
    }

    private double trackCenterDistance(DesignProject project) {
        List<DesignProject.Component> tracks = project.getComponents().stream().filter(this::isTrack).toList();
        if (tracks.size() >= 2) {
            double min = tracks.stream().mapToDouble(component -> component.getY() + component.getWidth() / 2).min().orElse(0);
            double max = tracks.stream().mapToDouble(component -> component.getY() + component.getWidth() / 2).max().orElse(min);
            return Math.abs(max - min);
        }
        return overallWidth(project) * 0.65;
    }

    private double trackWidth(DesignProject project) {
        return project.getComponents().stream().filter(this::isTrack).mapToDouble(DesignProject.Component::getWidth).findFirst().orElse(overallWidth(project) * 0.18);
    }

    private double trackHeight(DesignProject project) {
        return project.getComponents().stream().filter(this::isTrack).mapToDouble(DesignProject.Component::getHeight).findFirst().orElse(overallHeight(project) * 0.18);
    }

    private double brushSize(DesignProject project) {
        return project.getComponents().stream().filter(this::isBrush).mapToDouble(c -> Math.max(c.getWidth(), c.getHeight())).findFirst().orElse(overallHeight(project) * 0.25);
    }

    private double moduleOffset(DesignProject project) {
        return project.getComponents().stream().filter(component -> isSensor(component) || isBrush(component))
                .mapToDouble(component -> component.getX() + component.getLength() / 2).findFirst().orElse(overallLength(project) * 0.72);
    }

    private double magnetClearance(DesignProject project) {
        return project.getComponents().stream().filter(this::isMagnet).mapToDouble(DesignProject.Component::getZ).min().orElse(20);
    }

    private double coverHeight(DesignProject project) {
        return project.getComponents().stream().filter(this::isCover).mapToDouble(DesignProject.Component::getHeight).findFirst().orElse(overallHeight(project) * 0.35);
    }

    private double plateThickness(DesignProject project) {
        return project.number("板厚", project.number("壳体板厚", 6));
    }

    private double mountingDistance(List<DesignProject.Component> selected, DesignProject project) {
        if (selected.size() >= 2) {
            return Math.abs((selected.get(0).getX() + selected.get(0).getLength() / 2) - (selected.get(1).getX() + selected.get(1).getLength() / 2));
        }
        return project.number("安装孔距", 120);
    }

    private boolean isBaseStructure(DesignProject.Component c) { return isFrame(c) || isCover(c) || isTrack(c); }
    private boolean isFrame(DesignProject.Component c) { return geom(c, "FRAME") || nameHas(c, "机架", "frame", "底座", "支架"); }
    private boolean isCover(DesignProject.Component c) { return geom(c, "COVER") || nameHas(c, "外壳", "防护", "cover", "舱"); }
    private boolean isTrack(DesignProject.Component c) { return geom(c, "TRACK") || nameHas(c, "履带", "track"); }
    private boolean isWheel(DesignProject.Component c) { return geom(c, "WHEEL") || nameHas(c, "轮", "roller", "wheel"); }
    private boolean isBrush(DesignProject.Component c) { return geom(c, "BRUSH") || nameHas(c, "刷", "清扫", "brush"); }
    private boolean isSensor(DesignProject.Component c) { return geom(c, "SENSOR_RAIL") || nameHas(c, "检测", "传感", "导轨", "滑轨", "sensor", "rail"); }
    private boolean isMagnet(DesignProject.Component c) { return geom(c, "MAGNET") || nameHas(c, "磁", "吸附", "magnet"); }
    private boolean isMotor(DesignProject.Component c) { return geom(c, "MOTOR") || geom(c, "GEARBOX") || nameHas(c, "电机", "减速", "motor", "reducer"); }
    private boolean isMount(DesignProject.Component c) { return "MOUNT".equals(c.getRole()) || nameHas(c, "安装", "连接", "快拆", "mount"); }

    private boolean geom(DesignProject.Component c, String token) {
        return c.getGeometry() != null && c.getGeometry().toUpperCase().contains(token);
    }

    private boolean nameHas(DesignProject.Component c, String... words) {
        return nameContains(c.getName(), words);
    }

    private boolean nameContains(String value, String... words) {
        String normalized = normalized(value);
        for (String word : words) if (normalized.contains(normalized(word))) return true;
        return false;
    }

    private String normalized(String value) {
        return value == null ? "" : value.toLowerCase();
    }
}
