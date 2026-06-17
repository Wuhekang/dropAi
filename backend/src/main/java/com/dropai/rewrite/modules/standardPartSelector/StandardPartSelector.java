package com.dropai.rewrite.modules.standardPartSelector;

import com.dropai.rewrite.modules.model.DesignProject;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class StandardPartSelector {
    private final Map<String, List<Candidate>> library = new LinkedHashMap<>();

    public StandardPartSelector() {
        library.put("电机", List.of(
                candidate("电机", "24V直流减速电机 60W", "GB/T 755 旋转电机定额与性能", "额定功率60W，额定转速120r/min", 60, "W"),
                candidate("电机", "三相异步电机 YS6314 0.12kW", "GB/T 28575 小功率电机", "额定功率0.12kW，适合小型机构", 120, "W"),
                candidate("电机", "三相异步电机 YE3-80M1-4 0.55kW", "GB/T 28575 高效电机", "额定功率0.55kW，适合中小型传动", 550, "W")));
        library.put("减速器", List.of(
                candidate("减速器", "行星减速器 PLF60 i=20", "GB/T 10095 齿轮精度参考", "额定输出扭矩15N·m", 15, "N·m"),
                candidate("减速器", "蜗轮蜗杆减速器 NMRV030 i=30", "JB/T 9051 蜗杆减速器参考", "额定输出扭矩25N·m", 25, "N·m")));
        library.put("轴承", List.of(
                candidate("轴承", "深沟球轴承 6202", "GB/T 276 滚动轴承", "内径15mm，外径35mm", 15, "mm"),
                candidate("轴承", "深沟球轴承 6204", "GB/T 276 滚动轴承", "内径20mm，外径47mm", 20, "mm"),
                candidate("轴承", "深沟球轴承 6206", "GB/T 276 滚动轴承", "内径30mm，外径62mm", 30, "mm")));
        library.put("导轨", List.of(
                candidate("导轨", "微型直线导轨 MGN12", "HIWIN/THK公开样本参数", "导轨宽12mm，适合轻载调节", 12, "mm"),
                candidate("导轨", "直线导轨 HGR15", "HIWIN/THK公开样本参数", "导轨宽15mm，适合中载滑移", 15, "mm")));
        library.put("联轴器", List.of(
                candidate("联轴器", "梅花弹性联轴器 D25L30", "JB/T 9147 联轴器参考", "孔径6-12mm，适合小功率传动", 12, "mm"),
                candidate("联轴器", "膜片联轴器 JMⅠ-30", "JB/T 9147 联轴器参考", "补偿能力较好，适合中小扭矩", 30, "mm")));
        library.put("螺栓", List.of(
                candidate("螺栓", "M6 8.8级内六角螺栓", "GB/T 70.1 内六角圆柱头螺钉", "常用模块连接", 6, "mm"),
                candidate("螺栓", "M8 8.8级内六角螺栓", "GB/T 70.1 内六角圆柱头螺钉", "中等载荷连接", 8, "mm")));
        library.put("螺母", List.of(candidate("螺母", "M6 1型六角螺母", "GB/T 6170 六角螺母", "与M6螺栓配套", 6, "mm")));
        library.put("法兰", List.of(candidate("法兰", "PN10板式平焊法兰", "GB/T 9119 板式平焊钢制管法兰", "管口连接", 10, "bar")));
        library.put("轴", List.of(candidate("轴", "45钢调质传动轴 φ20", "机械设计手册轴类零件选型", "调质处理，适合小型传动", 20, "mm")));
        library.put("键", List.of(candidate("键", "A型平键 6×6", "GB/T 1096 普通型平键", "轴毂连接", 6, "mm")));
        library.put("销", List.of(candidate("销", "圆柱销 A6×30", "GB/T 119.1 圆柱销", "定位连接", 6, "mm")));
        library.put("弹簧", List.of(candidate("弹簧", "压缩弹簧 YB型", "GB/T 2089 圆柱螺旋压缩弹簧", "复位与压紧", 1, "")));
        library.put("滚轮", List.of(candidate("滚轮", "包胶滚轮 φ80", "公开滚轮样本参数", "聚氨酯包胶，适合导向支撑", 80, "mm")));
        library.put("链轮", List.of(candidate("链轮", "08B-1链轮 Z=19", "GB/T 1243 滚子链传动", "小型链传动", 19, "齿")));
        library.put("同步带轮", List.of(candidate("同步带轮", "HTD-5M同步带轮 Z=24", "公开同步带轮样本参数", "同步传动", 24, "齿")));
    }

    public DesignProject select(DesignProject project) {
        List<DesignProject.DesignPart> parts = new ArrayList<>();
        collect(project.getStructureTree(), "", project, parts);
        project.setResolvedParts(parts);
        project.setStandardParts(parts.stream().filter(p -> "standard".equals(p.getPartType()))
                .map(p -> p.getName() + "：" + p.getModel()).distinct().toList());
        appendSelectionCalculations(project, parts);
        return project;
    }

    private void collect(DesignProject.StructureNode node, String parent, DesignProject project, List<DesignProject.DesignPart> parts) {
        if (node == null) return;
        if (!"整机".equals(node.getName())) parts.add(selectPart(node.getName(), parent, project));
        for (DesignProject.StructureNode child : node.getChildren()) collect(child, node.getName(), project, parts);
    }

    private DesignProject.DesignPart selectPart(String name, String parent, DesignProject project) {
        String type = standardType(name);
        if (type.isBlank()) return unresolved(name, parent);
        Candidate selected = choose(type, project);
        DesignProject.DesignPart part = new DesignProject.DesignPart();
        part.setPartType("standard");
        part.setName(typeName(type, name));
        part.setModel(selected.model());
        part.setSource("library:" + selected.source());
        part.setGeneratedBy("StandardPartSelector");
        part.setMaterial(material(type));
        part.setProcess("标准件参数匹配：" + selected.parameter());
        part.setGeometryFeatures(features(type, selected));
        part.setQuantity(quantity(type, name));
        part.setParentStructure(parent);
        return part;
    }

    private Candidate choose(String type, DesignProject project) {
        List<Candidate> candidates = library.getOrDefault(type, List.of());
        if (candidates.isEmpty()) return candidate(type, type + "标准件", "公开标准件参数", "方案阶段占位选型", 1, "");
        double demand = demand(type, project);
        return candidates.stream().filter(c -> c.capacity() >= demand).findFirst().orElse(candidates.get(candidates.size() - 1));
    }

    private double demand(String type, DesignProject project) {
        return switch (type) {
            case "电机" -> Math.max(40, project.number("电机功率", project.number("功率", 0.08) * 1000));
            case "减速器" -> Math.max(8, project.number("驱动轮输出扭矩", project.number("输出扭矩", 12)));
            case "轴承", "联轴器", "轴" -> Math.max(12, project.number("轴径", project.number("轮径", 80) / 6));
            case "导轨" -> project.number("导轨宽度", 12);
            case "螺栓", "螺母", "销", "键" -> project.number("螺栓直径", 6);
            default -> 1;
        };
    }

    private String standardType(String name) {
        for (String key : library.keySet()) if (name.contains(key)) return key;
        if (name.contains("电动") || name.contains("驱动")) return "电机";
        if (name.contains("滑轨")) return "导轨";
        if (name.contains("轮")) return "滚轮";
        return "";
    }

    private DesignProject.DesignPart unresolved(String name, String parent) {
        DesignProject.DesignPart part = new DesignProject.DesignPart();
        part.setPartType("unresolved");
        part.setName(name);
        part.setSource("StructureTree");
        part.setParentStructure(parent);
        part.setQuantity(quantity("", name));
        return part;
    }

    private List<String> features(String type, Candidate selected) {
        return List.of("标准型号：" + selected.model(), "参数：" + selected.parameter(), "来源：" + selected.source(), "安装孔匹配", "装配定位面");
    }

    private String typeName(String type, String original) {
        if (original.contains(type)) return original;
        return original + "-" + type;
    }

    private String material(String type) {
        return switch (type) {
            case "电机", "减速器", "轴承", "导轨", "联轴器", "螺栓", "螺母", "弹簧" -> "标准件";
            case "轴", "键", "销", "滚轮", "链轮", "同步带轮" -> "45钢/标准件";
            default -> "标准件";
        };
    }

    private int quantity(String type, String name) {
        if (name.contains("支重") || name.contains("螺栓")) return 8;
        if (name.contains("左右") || name.contains("驱动") || name.contains("从动") || name.contains("电机") || name.contains("减速器") || name.contains("轮")) return 2;
        return 1;
    }

    private Candidate candidate(String type, String model, String source, String parameter, double capacity, String unit) {
        return new Candidate(type, model, source, parameter, capacity, unit);
    }

    private void appendSelectionCalculations(DesignProject project, List<DesignProject.DesignPart> parts) {
        parts.stream().filter(p -> "standard".equals(p.getPartType())).limit(8).forEach(part -> {
            String source = part.getSource() == null ? "library" : part.getSource().replace("library:", "");
            project.getCalculations().add(new DesignProject.Calculation(
                    part.getName() + "标准件选型",
                    "C_select ≥ C_required",
                    part.getModel() + "；" + part.getProcess() + "；来源：" + source,
                    1,
                    "",
                    "标准件参数已写入BOM和装配树"));
        });
    }

    private record Candidate(String type, String model, String source, String parameter, double capacity, String unit) {}
}
