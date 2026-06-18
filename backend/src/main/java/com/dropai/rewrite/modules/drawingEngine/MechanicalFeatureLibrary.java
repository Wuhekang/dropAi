package com.dropai.rewrite.modules.drawingEngine;

import com.dropai.rewrite.modules.model.DesignProject;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Service
public class MechanicalFeatureLibrary {
    public FeatureSet resolve(DesignProject.Component component) {
        String name = text(component.getName());
        String geometry = text(component.getGeometry());
        String combined = (name + " " + geometry).toLowerCase(Locale.ROOT);

        if (containsAny(combined, "履带", "track", "驱动轮", "从动轮", "支重轮", "roller", "wheel")) {
            return new FeatureSet("履带机构", List.of("履带板", "履带节", "驱动轮", "从动轮", "支重轮", "轮辐", "轮毂", "轴孔", "键槽"));
        }
        if (containsAny(combined, "机架", "frame", "底座", "base", "横梁", "侧板")) {
            return new FeatureSet("机架结构", List.of("侧板", "横梁", "加强筋", "安装孔", "螺栓孔阵列", "折弯边", "连接板"));
        }
        if (containsAny(combined, "清扫", "刷", "brush")) {
            return new FeatureSet("清扫刷组件", List.of("圆盘刷", "刷毛阵列", "驱动电机安装座", "连接轴", "中心轴孔", "紧固螺栓"));
        }
        if (containsAny(combined, "磁", "吸附", "magnet")) {
            return new FeatureSet("磁吸附模块", List.of("磁吸附座", "磁块", "安装板", "调节孔", "防护盖", "固定孔"));
        }
        if (containsAny(combined, "检测", "传感", "导轨", "滑块", "sensor", "rail", "slide")) {
            return new FeatureSet("检测支架", List.of("传感器支架", "导轨", "滑块", "快拆孔", "调节槽", "安装孔"));
        }
        if (containsAny(combined, "电机", "motor")) {
            return new FeatureSet("电机安装结构", List.of("电机壳体", "安装法兰", "输出轴", "安装孔", "接线盒", "散热槽"));
        }
        if (containsAny(combined, "减速", "reducer", "gear")) {
            return new FeatureSet("减速器安装结构", List.of("箱体", "输入轴", "输出轴", "安装底脚", "螺栓孔", "加强筋"));
        }
        return new FeatureSet("通用机械零件", List.of("主体轮廓", "安装孔", "连接面", "定位边", "倒角", "材料标注"));
    }

    public List<DesignProject.Component> selectMajorDrawingTargets(DesignProject project) {
        List<DesignProject.Component> components = project.getComponents();
        List<DesignProject.Component> selected = new ArrayList<>();
        addFirst(selected, components, "履带", "track", "驱动轮", "从动轮", "支重轮");
        addFirst(selected, components, "机架", "frame", "底座");
        addFirst(selected, components, "清扫", "刷", "brush");
        addFirst(selected, components, "磁", "吸附", "magnet");
        addFirst(selected, components, "检测", "传感", "导轨", "滑块", "sensor", "rail");
        for (DesignProject.Component component : components) {
            if (selected.size() >= 5) break;
            if (component.isKeyPart() && selected.stream().noneMatch(item -> item.getPartId().equals(component.getPartId()))) {
                selected.add(component);
            }
        }
        return selected.stream().limit(5).toList();
    }

    private void addFirst(List<DesignProject.Component> selected, List<DesignProject.Component> components, String... keywords) {
        if (selected.size() >= 5) return;
        for (DesignProject.Component component : components) {
            if (selected.stream().anyMatch(item -> item.getPartId().equals(component.getPartId()))) continue;
            String text = (text(component.getName()) + " " + text(component.getGeometry())).toLowerCase(Locale.ROOT);
            if (containsAny(text, keywords)) {
                selected.add(component);
                return;
            }
        }
    }

    private boolean containsAny(String value, String... keywords) {
        for (String keyword : keywords) {
            if (value.contains(keyword.toLowerCase(Locale.ROOT))) return true;
        }
        return false;
    }

    private String text(String value) {
        return value == null ? "" : value;
    }

    public record FeatureSet(String family, List<String> features) {}
}
