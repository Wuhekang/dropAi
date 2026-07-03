package com.dropai.rewrite.modules.designReferenceAgent;

import com.dropai.rewrite.modules.model.DesignProject;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Service
public class DesignReferenceAgent {
    private static final String BASIS = "系统参考补全，待确认。";

    public DesignProject complete(DesignProject project) {
        if (project == null) project = new DesignProject();
        DesignProject.DesignReference reference = referenceFor(project);
        project.setDesignReference(reference);
        for (String item : reference.getCommonStructures()) addUnique(project.getMainStructures(), item);
        for (String item : reference.getRecommendedDrawings()) addUnique(project.getDrawingViews(), item);
        for (DesignProject.Parameter parameter : reference.getRecommendedParameters()) addSuggested(project, parameter);
        for (String item : reference.getCompletionNotes()) addUnique(project.getEnhancementNotes(), item);
        addUnique(project.getVerificationItems(), "任务书未明确完整图纸规划，系统将按毕业设计版自动补全总装图和关键零件图，补全内容可在下一步修改确认。");
        return project;
    }

    private DesignProject.DesignReference referenceFor(DesignProject project) {
        String signature = normalize(project.getProjectTitle() + " " + project.getEquipmentName() + " " + project.getDesignType()
                + " " + String.join(" ", project.getMainFunctions()) + " " + String.join(" ", project.getMainStructures()));
        if (containsAny(signature, "重力沉降", "沉降室", "除尘")) {
            return reference(
                    List.of("重力沉降室 毕业设计 总装图", "重力沉降室 三视图", "重力沉降室 灰斗 法兰 支撑架", "gravity settling chamber assembly drawing"),
                    List.of("卧式矩形重力沉降室", "工业通风除尘沉降室", "含尘气流沉降箱体"),
                    List.of("卧式矩形箱体结构", "进气管", "扩散段", "导流板", "沉降室箱体", "灰斗", "卸灰口", "出气管", "支撑框架", "检修门", "观察孔", "加强筋", "进出口法兰"),
                    List.of("总装图", "总装三视图", "箱体零件图", "灰斗零件图", "进出口法兰图", "支撑架零件图", "检修门零件图"),
                    List.of(p("总长", 4200, "mm"), p("总宽", 1600, "mm"), p("总高", 1800, "mm"), p("箱体板厚", 4, "mm"),
                            p("灰斗角度", 55, "°"), p("进出口尺寸", "600×500", "mm"), p("设计风量", "3000~6000", "m³/h")),
                    "已按同类重力沉降室毕业设计规律补全结构、参数范围和图纸组织方式。");
        }
        if (containsAny(signature, "爬壁", "履带", "油罐", "磁吸", "机器人")) {
            return reference(
                    List.of("爬壁机器人 毕业设计 装配图", "履带式爬壁机器人 三视图", "磁吸附机器人 结构设计", "wall climbing robot track assembly"),
                    List.of("履带式油罐检测爬壁机器人", "磁吸附清扫检测机器人", "模块化壁面检测机器人"),
                    List.of("机架", "左右履带机构", "驱动轮", "从动轮", "支重轮", "履带板", "永磁吸附模块", "圆盘清扫刷", "清扫电机", "检测传感器安装架", "滑轨调节机构", "快拆结构", "防护外壳", "驱动电机", "减速器", "电池控制舱"),
                    List.of("总装图", "总装三视图", "履带机构装配图", "机架结构图", "清扫刷组件图", "磁吸附模块图", "检测支架图"),
                    List.of(p("整机长度", 800, "mm"), p("整机宽度", 600, "mm"), p("整机高度", 300, "mm"), p("吸附力", 200, "N"),
                            p("履带宽度", 80, "mm"), p("清扫刷直径", 180, "mm")),
                    "已按同类履带式磁吸附机器人毕业设计规律补全机构、零件和图纸组织方式。");
        }
        if (containsAny(signature, "输送机", "输送带", "滚筒")) {
            return reference(
                    List.of("带式输送机 毕业设计 总装图", "输送机 三视图", "输送机 滚筒 机架 零件图"),
                    List.of("带式输送机", "小型物料输送装置"),
                    List.of("输送带", "主动滚筒", "从动滚筒", "托辊", "张紧装置", "机架", "驱动电机", "减速器", "联轴器", "轴承座", "防护罩"),
                    List.of("总装图", "总装三视图", "机架结构图", "主动滚筒图", "从动滚筒图", "张紧装置图", "驱动装置图"),
                    List.of(p("输送长度", 5000, "mm"), p("带宽", 500, "mm"), p("输送速度", 1.0, "m/s"), p("滚筒直径", 240, "mm")),
                    "已按同类带式输送机毕业设计规律补全结构和图纸规划。");
        }
        return reference(
                List.of(project.getProjectTitle() + " 毕业设计 总装图", project.getProjectTitle() + " 三视图", project.getProjectTitle() + " 机械结构设计"),
                List.of(project.getEquipmentName().isBlank() ? project.getProjectTitle() : project.getEquipmentName()),
                List.of("主体结构", "支撑结构", "功能执行机构", "连接结构", "安装结构", "检修结构", "防护结构", "标准件连接"),
                List.of("总装图", "总装三视图", "主体零件图", "支撑结构图", "连接件图", "安装板图", "防护件图"),
                List.of(p("总长", 1200, "mm"), p("总宽", 800, "mm"), p("总高", 900, "mm"), p("材料", "Q235B", "")),
                "资料较少，已按机械类本科毕业设计通用图纸组织方式补全，需用户确认。");
    }

    private DesignProject.DesignReference reference(List<String> keywords, List<String> similarDevices, List<String> structures,
                                                    List<String> drawings, List<DesignProject.Parameter> parameters, String note) {
        DesignProject.DesignReference reference = new DesignProject.DesignReference();
        reference.setReferenceMode("graduation_design");
        reference.setSearchedKeywords(keywords);
        reference.setSimilarDevices(similarDevices);
        reference.setCommonStructures(structures);
        reference.setRecommendedDrawings(drawings);
        reference.setRecommendedParameters(parameters);
        reference.setCompletionNotes(List.of(
                note,
                "参考补全仅提取同类机械结构规律、图纸组织方式和参数范围，不复制网络正文或图纸内容。",
                "在线参考检索接口可后续接入；当前结果按内置毕业设计参考规律生成并标记待确认。"));
        return reference;
    }

    private DesignProject.Parameter p(String name, Object value, String unit) {
        return new DesignProject.Parameter(name, value, unit, "系统参考补全", BASIS);
    }

    private void addSuggested(DesignProject project, DesignProject.Parameter suggestion) {
        boolean exists = project.allParameters().stream().anyMatch(item -> suggestion.getName().equals(item.getName()));
        if (!exists) project.getSuggestedParameters().add(suggestion);
    }

    private void addUnique(List<String> list, String value) {
        if (value != null && !value.isBlank() && list.stream().noneMatch(value::equals)) list.add(value);
    }

    private boolean containsAny(String value, String... words) {
        for (String word : words) if (value.contains(normalize(word))) return true;
        return false;
    }

    private String normalize(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT).replaceAll("\\s+", "");
    }
}
