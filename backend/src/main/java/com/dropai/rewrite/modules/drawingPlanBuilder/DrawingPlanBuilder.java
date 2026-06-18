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
        plan.setMainView(view(project, "主视图", viewport(55, 320, 430, 165), MAX_MAIN_PARTS,
                this::mainViewPart, mainDimensions(project)));
        plan.setTopView(view(project, "俯视图", viewport(55, 105, 430, 135), MAX_TOP_PARTS,
                this::topViewPart, topDimensions(project)));
        plan.setSideView(view(project, "侧视图", viewport(515, 225, 170, 170), MAX_SIDE_PARTS,
                this::sideViewPart, sideDimensions(project)));
        plan.setSectionViews(List.of());
        plan.setDetailViews(List.of());
        plan.setIsometricView(new DesignProject.DrawingViewPlan("三维展示另见网页预览"));
        plan.setBomTable(bomForThreeViews(project, plan));
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

    private DesignProject.DrawingViewPlan view(DesignProject project, String name, Map<String, Double> viewport,
                                               int maxParts, Predicate<DesignProject.Component> selector,
                                               List<DesignProject.DimensionChain> dimensions) {
        DesignProject.DrawingViewPlan view = new DesignProject.DrawingViewPlan(name);
        view.setPurpose(name + "用于表达总装关键结构和主要尺寸");
        view.setLevelOfDetail("工程化简化表达");
        view.setViewport(viewport);
        List<DesignProject.Component> selected = select(project, selector, maxParts);
        view.setVisibleParts(selected.stream().map(DesignProject.Component::getPartId).toList());
        view.setHiddenParts(project.getComponents().stream()
                .map(DesignProject.Component::getPartId)
                .filter(id -> !view.getVisibleParts().contains(id))
                .toList());
        view.setDimensions(dimensions);
        view.setLabels(selected.stream().filter(DesignProject.Component::isKeyPart)
                .limit(5).map(c -> c.getSequence() + " " + c.getName()).toList());
        view.setCenterLines(List.of("主要回转件和对称结构设置中心线"));
        view.setSectionMarkers(List.of());
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
                    .filter(component -> component.isKeyPart() || isFrame(component) || isCover(component) || isTrack(component))
                    .sorted(Comparator.comparing(DesignProject.Component::isKeyPart).reversed()
                            .thenComparingInt(DesignProject.Component::getSequence))
                    .toList();
        }
        return uniqueByName(selected).stream().limit(maxParts).toList();
    }

    private boolean mainViewPart(DesignProject.Component c) {
        return isFrame(c) || isCover(c) || isTrack(c) || isWheel(c) || isBrush(c) || isSensor(c) || isMotor(c);
    }

    private boolean topViewPart(DesignProject.Component c) {
        return isTrack(c) || isFrame(c) || isCover(c) || isSensor(c) || isBrush(c) || isBatteryOrControl(c);
    }

    private boolean sideViewPart(DesignProject.Component c) {
        return isTrack(c) || isWheel(c) || isMagnet(c) || isCover(c) || isSensor(c) || isFrame(c) || isMotor(c);
    }

    private List<DesignProject.DimensionChain> mainDimensions(DesignProject project) {
        return List.of(
                dimension("整机长度", confirmedNumber(project, List.of("整机长度", "总长", "整机尺寸"), 0), "mm", "整机", sourceFor(project, List.of("整机长度", "总长", "整机尺寸"))),
                dimension("整机高度", confirmedNumber(project, List.of("整机高度", "总高", "整机尺寸"), 0), "mm", "整机", sourceFor(project, List.of("整机高度", "总高", "整机尺寸"))),
                dimension("轮径", standardOrConstraintDimension(project, this::isWheel), "mm", "轮系", sourceForComponent(project, this::isWheel)),
                dimension("履带长度", standardOrConstraintDimension(project, this::isTrack), "mm", "履带机构", sourceForComponent(project, this::isTrack))
        );
    }

    private List<DesignProject.DimensionChain> topDimensions(DesignProject project) {
        return List.of(
                dimension("整机宽度", confirmedNumber(project, List.of("整机宽度", "总宽", "整机尺寸"), 0), "mm", "整机", sourceFor(project, List.of("整机宽度", "总宽", "整机尺寸"))),
                dimension("履带宽度", standardOrConstraintDimension(project, this::isTrack), "mm", "履带机构", sourceForComponent(project, this::isTrack)),
                dimension("左右履带间距", confirmedConstraintDistance(project, "TRACK"), "mm", "履带机构", "装配约束距离：左右履带中心面对称"),
                dimension("模块安装位置", confirmedConstraintDistance(project, "SENSOR"), "mm", "检测/清扫模块", "装配约束距离：功能模块安装位置")
        );
    }

    private List<DesignProject.DimensionChain> sideDimensions(DesignProject project) {
        return List.of(
                dimension("整机高度", confirmedNumber(project, List.of("整机高度", "总高", "整机尺寸"), 0), "mm", "整机", sourceFor(project, List.of("整机高度", "总高", "整机尺寸"))),
                dimension("履带高度", standardOrConstraintDimension(project, this::isTrack), "mm", "履带机构", sourceForComponent(project, this::isTrack)),
                dimension("磁吸附模块安装高度", confirmedConstraintDistance(project, "MAGNET"), "mm", "磁吸附模块", "装配约束距离：磁吸附模块底部安装面"),
                dimension("检测支架高度", confirmedConstraintDistance(project, "SENSOR"), "mm", "检测机构", "装配约束距离：检测支架安装面")
        );
    }

    private List<DesignProject.BomItem> bomForThreeViews(DesignProject project, DesignProject.DrawingPlan plan) {
        Set<String> visibleIds = new LinkedHashSet<>();
        visibleIds.addAll(plan.getMainView().getVisibleParts());
        visibleIds.addAll(plan.getTopView().getVisibleParts());
        visibleIds.addAll(plan.getSideView().getVisibleParts());
        return project.getComponents().stream()
                .filter(component -> visibleIds.contains(component.getPartId()))
                .sorted(Comparator.comparingInt(DesignProject.Component::getSequence))
                .map(component -> new DesignProject.BomItem(component.getSequence(), component.getName(),
                        component.getMaterial(), component.getQuantity(), component.getFunction()))
                .limit(14)
                .toList();
    }

    private List<DesignProject.Parameter> parameterTable(DesignProject project) {
        return project.allParameters().stream().limit(6).toList();
    }

    private List<String> technicalRequirements(DesignProject project) {
        List<String> result = new ArrayList<>();
        result.add("图纸为本科毕业设计总装三视图，未注尺寸应按设计说明书和用户确认参数复核。");
        result.add("所有标准件参数为模拟推荐时，需在接入真实标准件平台后复核型号、孔距和安装尺寸。");
        result.add("图中序号必须与BOM明细表一致，装配时保证履带、轮系、机架和功能模块安装位置。");
        result.add("焊接件焊缝应连续均匀，安装面去毛刺，运动部件装配后转动灵活。");
        result.addAll(project.getTechnicalRequirements().stream().limit(2).toList());
        return result.stream().filter(item -> item != null && !item.isBlank()).distinct().limit(6).toList();
    }

    private Map<String, String> titleBlock(DesignProject project) {
        Map<String, String> title = new LinkedHashMap<>();
        title.put("drawingName", "总装三视图");
        title.put("drawingNo", "ZD-00");
        title.put("scale", "1:10");
        title.put("projectTitle", project.getProjectTitle());
        title.put("equipmentName", project.getEquipmentName());
        return title;
    }

    private void score(DesignProject.DrawingPlan plan) {
        int score = 100;
        score -= Math.max(0, plan.getMainView().getVisibleParts().size() - MAX_MAIN_PARTS) * 4;
        score -= Math.max(0, plan.getTopView().getVisibleParts().size() - MAX_TOP_PARTS) * 4;
        score -= Math.max(0, plan.getSideView().getVisibleParts().size() - MAX_SIDE_PARTS) * 4;
        if (plan.getMainView().getVisibleParts().size() < 3) score -= 20;
        if (plan.getTopView().getVisibleParts().size() < 3) score -= 20;
        if (plan.getSideView().getVisibleParts().size() < 3) score -= 20;
        if (plan.getBomTable().isEmpty()) score -= 20;
        plan.setQualityScore(Math.max(0, score));
        plan.setQualityNotes(List.of(
                "主视图零件数=" + plan.getMainView().getVisibleParts().size(),
                "俯视图零件数=" + plan.getTopView().getVisibleParts().size(),
                "侧视图零件数=" + plan.getSideView().getVisibleParts().size(),
                "质量分=" + Math.max(0, score)));
    }

    private void qualityGate(DesignProject.DrawingPlan plan) {
        if (plan.getMainView().getVisibleParts().size() < 3
                || plan.getTopView().getVisibleParts().size() < 3
                || plan.getSideView().getVisibleParts().size() < 3
                || plan.getMainView().getVisibleParts().size() > MAX_MAIN_PARTS
                || plan.getTopView().getVisibleParts().size() > MAX_TOP_PARTS
                || plan.getSideView().getVisibleParts().size() > MAX_SIDE_PARTS
                || plan.getBomTable().isEmpty()) {
            throw new IllegalStateException("DrawingPlan quality gate failed: three-view drawing is not clear enough");
        }
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
        if (value <= 0) return new DesignProject.DimensionChain(name, 0, unit, related, "待校核：缺少任务书明确参数、设计计算结果、标准件尺寸、装配约束距离或用户确认参数");
        return new DesignProject.DimensionChain(name, value, unit, related, source);
    }

    private double confirmedNumber(DesignProject project, List<String> names, double fallback) {
        for (String name : names) {
            double value = project.number(name, Double.NaN);
            if (!Double.isNaN(value)) return value;
        }
        return fallback;
    }

    private String sourceFor(DesignProject project, List<String> names) {
        for (DesignProject.Parameter p : project.getExplicitParameters()) {
            if (names.stream().anyMatch(name -> safe(p.getName()).contains(name) || name.contains(safe(p.getName())))) {
                return "任务书明确参数：" + blankTo(p.getSource(), p.getName());
            }
        }
        for (DesignProject.Parameter p : project.getDerivedParameters()) {
            if (names.stream().anyMatch(name -> safe(p.getName()).contains(name) || name.contains(safe(p.getName())))) {
                return "设计计算结果：" + blankTo(p.getBasis(), p.getName());
            }
        }
        for (DesignProject.Parameter p : project.getSuggestedParameters()) {
            if (names.stream().anyMatch(name -> safe(p.getName()).contains(name) || name.contains(safe(p.getName())))) {
                return "待校核：" + blankTo(p.getBasis(), p.getName());
            }
        }
        return "待校核：缺少任务书明确参数或用户确认参数";
    }

    private double standardOrConstraintDimension(DesignProject project, Predicate<DesignProject.Component> selector) {
        return project.getComponents().stream().filter(selector)
                .mapToDouble(component -> Math.max(component.getLength(), Math.max(component.getWidth(), component.getHeight())))
                .findFirst().orElse(0);
    }

    private String sourceForComponent(DesignProject project, Predicate<DesignProject.Component> selector) {
        return project.getComponents().stream().filter(selector).findFirst()
                .map(component -> "标准件尺寸或装配约束距离：" + blankTo(component.getMountTo(), component.getName()))
                .orElse("待校核：缺少标准件尺寸或装配约束距离");
    }

    private double confirmedConstraintDistance(DesignProject project, String token) {
        String upper = token.toUpperCase();
        return project.getComponents().stream()
                .filter(c -> safe(c.getGeometry()).toUpperCase().contains(upper) || safe(c.getPartId()).toUpperCase().contains(upper))
                .mapToDouble(c -> Math.max(Math.abs(c.getX()), Math.max(Math.abs(c.getY()), Math.abs(c.getZ()))))
                .filter(v -> v > 0)
                .findFirst().orElse(0);
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

    private boolean isFrame(DesignProject.Component c) { return geom(c, "FRAME") || nameHas(c, "机架", "frame", "底座", "支架"); }
    private boolean isCover(DesignProject.Component c) { return geom(c, "COVER") || nameHas(c, "外壳", "防护", "cover", "舱"); }
    private boolean isTrack(DesignProject.Component c) { return geom(c, "TRACK") || nameHas(c, "履带", "track", "belt"); }
    private boolean isWheel(DesignProject.Component c) { return geom(c, "WHEEL") || nameHas(c, "轮", "roller", "wheel"); }
    private boolean isBrush(DesignProject.Component c) { return geom(c, "BRUSH") || nameHas(c, "刷", "清扫", "brush"); }
    private boolean isSensor(DesignProject.Component c) { return geom(c, "SENSOR") || geom(c, "RAIL") || nameHas(c, "检测", "传感", "导轨", "滑轨", "sensor", "rail"); }
    private boolean isMagnet(DesignProject.Component c) { return geom(c, "MAGNET") || nameHas(c, "磁", "吸附", "magnet"); }
    private boolean isMotor(DesignProject.Component c) { return geom(c, "MOTOR") || geom(c, "GEAR") || nameHas(c, "电机", "减速", "motor", "reducer"); }
    private boolean isBatteryOrControl(DesignProject.Component c) { return nameHas(c, "电池", "控制", "battery", "control"); }

    private boolean geom(DesignProject.Component c, String token) {
        return c.getGeometry() != null && c.getGeometry().toUpperCase().contains(token);
    }

    private boolean nameHas(DesignProject.Component c, String... words) {
        String value = normalized(c.getName());
        for (String word : words) {
            if (value.contains(normalized(word))) return true;
        }
        return false;
    }

    private String normalized(String value) { return value == null ? "" : value.toLowerCase(); }
    private String safe(String value) { return value == null ? "" : value; }
    private String blankTo(String value, String fallback) { return value == null || value.isBlank() ? fallback : value; }
}
