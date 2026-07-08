package com.dropai.rewrite.modules.drawingPlanBuilder;

import com.dropai.rewrite.modules.drawingEngine.DimensionSourceValidator;
import com.dropai.rewrite.modules.drawingEngine.EngineeringSemanticLayer;
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
    private static final int MAX_MAIN_PARTS = 8;
    private static final int MAX_TOP_PARTS = 7;
    private static final int MAX_SIDE_PARTS = 7;
    private static final int MAX_BOM_ITEMS = 10;

    private final EngineeringSemanticLayer semanticLayer = new EngineeringSemanticLayer();
    private final DimensionSourceValidator dimensionSourceValidator = new DimensionSourceValidator();

    public DesignProject build(DesignProject project) {
        requireSource(project);

        DesignProject.DrawingPlan plan = new DesignProject.DrawingPlan();
        plan.setInputSource("DrawingPlan");
        plan.setTitleBlock(titleBlock(project));
        plan.setMainView(view(project, "主视图", viewport(55, 322, 420, 158), MAX_MAIN_PARTS,
                this::mainViewPart, mainDimensions(project)));
        plan.setTopView(view(project, "俯视图", viewport(55, 108, 420, 132), MAX_TOP_PARTS,
                this::topViewPart, topDimensions(project)));
        plan.setSideView(view(project, "侧视图", viewport(520, 230, 165, 158), MAX_SIDE_PARTS,
                this::sideViewPart, sideDimensions(project)));
        plan.setSectionViews(List.of());
        plan.setDetailViews(List.of());
        plan.setIsometricView(new DesignProject.DrawingViewPlan("网页三维展示"));
        plan.setBomTable(bomForThreeViews(project, plan));
        plan.setParameterTable(parameterTable(project));
        plan.setTechnicalRequirements(technicalRequirements());
        score(plan);
        plan.getQualityNotes().add("CAD input source: AssemblyModel components=" + project.getAssemblyModel().getComponents().size()
                + ", constraints=" + project.getAssemblyModel().getConstraints().size());
        qualityGate(project, plan);
        project.setDrawingPlan(plan);
        return project;
    }

    private void requireSource(DesignProject project) {
        if (project.getAssemblyTree() == null || project.getAssemblyTree().getChildren().isEmpty()) {
            throw new IllegalStateException("装配树为空，禁止生成CAD图纸");
        }
        if (project.getAssemblyConstraints() == null || project.getAssemblyConstraints().isEmpty()) {
            throw new IllegalStateException("装配约束为空，禁止生成CAD图纸");
        }
        if (project.getComponents() == null || project.getComponents().isEmpty()) {
            throw new IllegalStateException("组件为空，禁止生成CAD图纸");
        }
        if (project.getAssemblyModel() == null || project.getAssemblyModel().getComponents().isEmpty()
                || project.getAssemblyModel().getConstraints().isEmpty()) {
            throw new IllegalStateException("AssemblyModel为空或装配约束不足，禁止生成CAD图纸");
        }
    }

    private DesignProject.DrawingViewPlan view(DesignProject project, String name, Map<String, Double> viewport,
                                               int maxParts, Predicate<DesignProject.Component> selector,
                                               List<DesignProject.DimensionChain> dimensions) {
        DesignProject.DrawingViewPlan view = new DesignProject.DrawingViewPlan(name);
        view.setPurpose(name + "用于表达总装关键结构、外形尺寸和装配关系");
        view.setLevelOfDetail("engineering_simplified");
        view.setViewport(viewport);
        List<DesignProject.Component> selected = select(project, selector, maxParts);
        view.setVisibleParts(selected.stream().map(DesignProject.Component::getPartId).toList());
        view.setHiddenParts(project.getComponents().stream()
                .map(DesignProject.Component::getPartId)
                .filter(id -> !view.getVisibleParts().contains(id))
                .toList());
        view.setDimensions(dimensions.stream().filter(dimensionSourceValidator::isValid).limit(3).toList());
        view.setLabels(selected.stream()
                .filter(component -> component.isKeyPart() || priority(component) <= 4)
                .limit(4)
                .map(component -> component.getSequence() + " " + semanticLayer.drawingLabel(component))
                .toList());
        view.setCenterLines(List.of("主要回转件和对称结构设置中心线"));
        view.setSectionMarkers(List.of());
        dimensionSourceValidator.validateView(view);
        return view;
    }

    private List<DesignProject.Component> select(DesignProject project, Predicate<DesignProject.Component> selector, int maxParts) {
        List<DesignProject.Component> selected = project.getComponents().stream()
                .filter(selector)
                .sorted(componentOrder())
                .toList();
        if (selected.size() < 3) {
            selected = project.getComponents().stream()
                    .filter(component -> component.isKeyPart() || priority(component) <= 8)
                    .sorted(componentOrder())
                    .toList();
        }
        return uniqueBySemantic(selected).stream().limit(maxParts).toList();
    }

    private Comparator<DesignProject.Component> componentOrder() {
        return Comparator.comparing(DesignProject.Component::isKeyPart).reversed()
                .thenComparingInt(this::priority)
                .thenComparingInt(DesignProject.Component::getSequence);
    }

    private int priority(DesignProject.Component c) {
        return switch (semanticLayer.semanticOf(c).category()) {
            case "frame" -> 1;
            case "shell" -> 1;
            case "interface" -> 2;
            case "hopper" -> 3;
            case "door" -> 4;
            case "support" -> 5;
            case "rib" -> 6;
            case "track" -> 2;
            case "wheel" -> 3;
            case "motor" -> 4;
            case "reducer" -> 5;
            case "brush" -> 6;
            case "sensor", "rail" -> 7;
            case "magnet" -> 8;
            case "cover" -> 9;
            case "bearing", "coupling" -> 10;
            case "bolt", "flange" -> 12;
            default -> 20;
        };
    }

    private boolean mainViewPart(DesignProject.Component c) {
        return hasSemantic(c, "frame", "cover", "track", "wheel", "brush", "sensor", "rail", "motor", "reducer",
                "shell", "interface", "hopper", "door", "support", "rib");
    }

    private boolean topViewPart(DesignProject.Component c) {
        return hasSemantic(c, "track", "frame", "cover", "sensor", "rail", "brush", "motor", "reducer",
                "shell", "interface", "door", "rib");
    }

    private boolean sideViewPart(DesignProject.Component c) {
        return hasSemantic(c, "track", "wheel", "magnet", "cover", "sensor", "rail", "frame", "motor", "reducer",
                "shell", "interface", "hopper", "support", "door");
    }

    private List<DesignProject.DimensionChain> mainDimensions(DesignProject project) {
        return List.of(
                dimension("整机长度", confirmedNumber(project, List.of("整机长度", "总长", "长度"), 0), "mm", "整机", sourceFor(project, List.of("整机长度", "总长", "长度"))),
                dimension("整机高度", confirmedNumber(project, List.of("整机高度", "总高", "高度"), 0), "mm", "整机", sourceFor(project, List.of("整机高度", "总高", "高度"))),
                dimension("履带长度", semanticDimension(project, "track"), "mm", "履带机构", sourceForComponent(project, "track"))
        );
    }

    private List<DesignProject.DimensionChain> topDimensions(DesignProject project) {
        return List.of(
                dimension("整机宽度", confirmedNumber(project, List.of("整机宽度", "总宽", "宽度"), 0), "mm", "整机", sourceFor(project, List.of("整机宽度", "总宽", "宽度"))),
                dimension("履带宽度", semanticDimension(project, "track"), "mm", "履带机构", sourceForComponent(project, "track")),
                dimension("左右履带间距", confirmedConstraintDistance(project, "TRACK"), "mm", "履带机构", "assembly_constraint: 左右履带中心距")
        );
    }

    private List<DesignProject.DimensionChain> sideDimensions(DesignProject project) {
        return List.of(
                dimension("整机高度", confirmedNumber(project, List.of("整机高度", "总高", "高度"), 0), "mm", "整机", sourceFor(project, List.of("整机高度", "总高", "高度"))),
                dimension("履带高度", semanticDimension(project, "track"), "mm", "履带机构", sourceForComponent(project, "track")),
                dimension("磁吸模块安装高度", confirmedConstraintDistance(project, "MAGNET"), "mm", "磁吸组件", "assembly_constraint: 磁吸组件底部安装面")
        );
    }

    private List<DesignProject.BomItem> bomForThreeViews(DesignProject project, DesignProject.DrawingPlan plan) {
        Set<String> visibleIds = new LinkedHashSet<>();
        visibleIds.addAll(plan.getMainView().getVisibleParts());
        visibleIds.addAll(plan.getTopView().getVisibleParts());
        visibleIds.addAll(plan.getSideView().getVisibleParts());
        Set<String> semanticNames = new LinkedHashSet<>();
        List<DesignProject.BomItem> result = new ArrayList<>();
        for (DesignProject.Component component : project.getComponents().stream()
                .filter(component -> visibleIds.contains(component.getPartId()))
                .sorted(componentOrder())
                .toList()) {
            String name = semanticLayer.drawingLabel(component);
            if (semanticNames.add(name)) {
                result.add(new DesignProject.BomItem(component.getSequence(), name,
                        semanticLayer.material(component), Math.max(1, component.getQuantity()), semanticRemark(component)));
            }
            if (result.size() >= MAX_BOM_ITEMS) break;
        }
        return result;
    }

    private String semanticRemark(DesignProject.Component component) {
        String type = semanticLayer.semanticOf(component).category();
        return switch (type) {
            case "motor", "reducer", "bearing", "rail", "bolt", "coupling" -> "标准件";
            case "track", "wheel" -> "行走机构";
            case "magnet" -> "吸附模块";
            case "sensor" -> "检测模块";
            default -> "自制件";
        };
    }

    private List<DesignProject.Parameter> parameterTable(DesignProject project) {
        return project.allParameters().stream()
                .filter(parameter -> parameter.getName() != null && !semanticLayer.looksCorrupted(parameter.getName()))
                .limit(5)
                .toList();
    }

    private List<String> technicalRequirements() {
        return List.of(
                "未注尺寸公差按 GB/T 1804-m 执行，未注倒角 C1。",
                "基准A为关键安装面，基准B为履带或导轨中心平面，基准C为主要轴孔中心线。",
                "安装孔位置度按装配要求控制，孔口倒角并去毛刺。",
                "焊接件焊缝应连续均匀，焊后清理飞溅并进行防锈处理。",
                "装配后履带、清扫刷、检测支架运动应平稳，无明显卡滞。"
        );
    }

    private Map<String, String> titleBlock(DesignProject project) {
        Map<String, String> title = new LinkedHashMap<>();
        title.put("drawingName", "总装三视图");
        title.put("drawingNo", "ZD-00");
        title.put("scale", "1:10");
        title.put("projectTitle", clean(project.getProjectTitle(), "本科毕业设计"));
        title.put("equipmentName", clean(project.getEquipmentName(), "机械设备"));
        return title;
    }

    private void score(DesignProject.DrawingPlan plan) {
        int score = 100;
        score -= Math.max(0, plan.getMainView().getVisibleParts().size() - MAX_MAIN_PARTS) * 6;
        score -= Math.max(0, plan.getTopView().getVisibleParts().size() - MAX_TOP_PARTS) * 6;
        score -= Math.max(0, plan.getSideView().getVisibleParts().size() - MAX_SIDE_PARTS) * 6;
        if (plan.getMainView().getVisibleParts().size() < 3) score -= 20;
        if (plan.getTopView().getVisibleParts().size() < 3) score -= 20;
        if (plan.getSideView().getVisibleParts().size() < 3) score -= 20;
        if (plan.getBomTable().isEmpty()) score -= 20;
        plan.setQualityScore(Math.max(0, score));
        plan.setQualityNotes(List.of(
                "主视图核心部件数=" + plan.getMainView().getVisibleParts().size(),
                "俯视图核心部件数=" + plan.getTopView().getVisibleParts().size(),
                "侧视图核心部件数=" + plan.getSideView().getVisibleParts().size(),
                "BOM核心部件数=" + plan.getBomTable().size(),
                "质量分=" + Math.max(0, score)));
    }

    private void qualityGate(DesignProject project, DesignProject.DrawingPlan plan) {
        if (plan.getMainView().getVisibleParts().size() < 3
                || plan.getTopView().getVisibleParts().size() < 3
                || plan.getSideView().getVisibleParts().size() < 3
                || plan.getMainView().getVisibleParts().size() > MAX_MAIN_PARTS
                || plan.getTopView().getVisibleParts().size() > MAX_TOP_PARTS
                || plan.getSideView().getVisibleParts().size() > MAX_SIDE_PARTS
                || plan.getBomTable().isEmpty()) {
            if ("engineering".equalsIgnoreCase(project.getDesignDepth())) {
                throw new IllegalStateException("工程版需要补充完整图纸规划后生成。");
            }
            plan.getQualityNotes().add("任务书未明确完整图纸规划，系统将按毕业设计版自动补全总装图和关键零件图，补全内容可在下一步修改确认。");
            project.getEnhancementNotes().add("毕业设计版CAD门禁已切换为参考补全模式：资料不完整时继续生成方案级三视图和关键零件图。");
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
        if (value <= 0) {
            return new DesignProject.DimensionChain(name, 0, unit, related, "assembly_constraint: 待校核");
        }
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
            if (matchesAny(p.getName(), names)) return "taskbook: " + clean(p.getSource(), p.getName());
        }
        for (DesignProject.Parameter p : project.getDerivedParameters()) {
            if (matchesAny(p.getName(), names)) return "calculation: " + clean(p.getBasis(), p.getName());
        }
        return "assembly_constraint: 待校核";
    }

    private double semanticDimension(DesignProject project, String category) {
        return project.getComponents().stream().filter(component -> hasSemantic(component, category))
                .mapToDouble(component -> Math.max(component.getLength(), Math.max(component.getWidth(), component.getHeight())))
                .findFirst().orElse(0);
    }

    private String sourceForComponent(DesignProject project, String category) {
        return project.getComponents().stream().filter(component -> hasSemantic(component, category)).findFirst()
                .map(component -> isStandardSemantic(component) ? "standard_part: " + semanticLayer.drawingLabel(component) : "assembly_constraint: " + semanticLayer.drawingLabel(component))
                .orElse("assembly_constraint: 待校核");
    }

    private boolean isStandardSemantic(DesignProject.Component component) {
        String category = semanticLayer.semanticOf(component).category();
        return List.of("motor", "reducer", "bearing", "rail", "bolt", "coupling", "wheel").contains(category);
    }

    private double confirmedConstraintDistance(DesignProject project, String token) {
        String upper = token.toUpperCase();
        return project.getComponents().stream()
                .filter(c -> safe(c.getGeometry()).toUpperCase().contains(upper) || safe(c.getPartId()).toUpperCase().contains(upper))
                .mapToDouble(c -> Math.max(Math.abs(c.getX()), Math.max(Math.abs(c.getY()), Math.abs(c.getZ()))))
                .filter(v -> v > 0)
                .findFirst().orElse(0);
    }

    private List<DesignProject.Component> uniqueBySemantic(List<DesignProject.Component> input) {
        Set<String> names = new LinkedHashSet<>();
        List<DesignProject.Component> result = new ArrayList<>();
        for (DesignProject.Component component : input) {
            String semantic = semanticLayer.semanticOf(component).category();
            String key = ("track".equals(semantic) || "wheel".equals(semantic))
                    ? semantic + ":" + sideKey(component)
                    : semanticLayer.drawingLabel(component);
            if (names.add(key)) result.add(component);
        }
        return result;
    }

    private String sideKey(DesignProject.Component component) {
        String text = (safe(component.getName()) + safe(component.getPartId())).toLowerCase();
        if (text.contains("left") || text.contains("左")) return "left";
        if (text.contains("right") || text.contains("右")) return "right";
        return String.valueOf(component.getSequence());
    }

    private boolean hasSemantic(DesignProject.Component c, String... categories) {
        String category = semanticLayer.semanticOf(c).category();
        for (String item : categories) if (item.equals(category)) return true;
        return false;
    }

    private boolean matchesAny(String value, List<String> names) {
        String safeValue = safe(value);
        return names.stream().anyMatch(name -> safeValue.contains(name) || name.contains(safeValue));
    }

    private String clean(String value, String fallback) {
        return value == null || value.isBlank() || semanticLayer.looksCorrupted(value) ? fallback : value;
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }
}
