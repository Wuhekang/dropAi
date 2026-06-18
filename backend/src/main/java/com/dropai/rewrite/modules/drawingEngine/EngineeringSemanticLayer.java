package com.dropai.rewrite.modules.drawingEngine;

import com.dropai.rewrite.modules.model.DesignProject;
import org.springframework.stereotype.Component;

import java.util.Locale;

@Component
public class EngineeringSemanticLayer {
    public SemanticPart semanticOf(DesignProject.Component component) {
        String geometry = text(component.getGeometry()).toUpperCase(Locale.ROOT);
        String name = text(component.getName()).toLowerCase(Locale.ROOT);
        String role = text(component.getRole()).toUpperCase(Locale.ROOT);
        if (has(geometry, name, "TRACK", "履带", "track", "belt")) return new SemanticPart("履带", "track", "FUNCTION");
        if (has(geometry, name, "WHEEL", "驱动轮", "drive wheel")) return new SemanticPart("驱动轮/从动轮", "wheel", "JOINT");
        if (has(geometry, name, "ROLLER", "支重轮", "roller")) return new SemanticPart("支重轮", "wheel", "JOINT");
        if (has(geometry, name, "FRAME", "机架", "frame")) return new SemanticPart("机架", "frame", "BODY");
        if (has(geometry, name, "COVER", "外壳", "防护", "cover", "shell")) return new SemanticPart("防护外壳", "cover", "BODY");
        if (has(geometry, name, "MOTOR", "电机", "motor")) return new SemanticPart("电机", "motor", "DRIVE");
        if (has(geometry, name, "GEAR", "REDUCER", "减速器", "reducer")) return new SemanticPart("减速器", "reducer", "DRIVE");
        if (has(geometry, name, "MAGNET", "磁", "吸附", "magnet")) return new SemanticPart("磁吸组件", "magnet", "FUNCTION");
        if (has(geometry, name, "SENSOR", "检测", "传感", "sensor")) return new SemanticPart("检测组件", "sensor", "FUNCTION");
        if (has(geometry, name, "RAIL", "导轨", "滑轨", "rail")) return new SemanticPart("检测导轨", "rail", "FUNCTION");
        if (has(geometry, name, "BRUSH", "刷", "清扫", "brush")) return new SemanticPart("清扫刷", "brush", "FUNCTION");
        if (has(geometry, name, "BEARING", "轴承", "bearing")) return new SemanticPart("轴承", "bearing", "JOINT");
        if (has(geometry, name, "COUPLING", "联轴器", "coupling")) return new SemanticPart("联轴器", "coupling", "JOINT");
        if (has(geometry, name, "BOLT", "螺栓", "bolt")) return new SemanticPart("螺栓连接", "bolt", "JOINT");
        if (has(geometry, name, "FLANGE", "法兰", "flange")) return new SemanticPart("法兰", "flange", "INTERFACE");
        if ("BODY".equals(role)) return new SemanticPart("主体结构", "frame", "BODY");
        if ("SUPPORT".equals(role) || "BASE".equals(role)) return new SemanticPart("支撑结构", "frame", "SUPPORT");
        if ("DRIVE".equals(role)) return new SemanticPart("驱动组件", "motor", "DRIVE");
        if ("FUNCTION".equals(role)) return new SemanticPart("功能组件", "sensor", "FUNCTION");
        return new SemanticPart("连接结构", "plate", "STRUCTURE");
    }

    public String drawingLabel(DesignProject.Component component) {
        return semanticOf(component).displayName();
    }

    public String material(DesignProject.Component component) {
        String material = text(component.getMaterial());
        return material.isBlank() || looksCorrupted(material) ? "Q235B" : material;
    }

    public boolean looksCorrupted(String value) {
        if (value == null) return false;
        return value.matches(".*[姣璁鎴鍥涓鏂灏烘灦妯熸湁]{4,}.*");
    }

    private boolean has(String geometry, String name, String... tokens) {
        for (String token : tokens) {
            String normalized = token.toLowerCase(Locale.ROOT);
            if (geometry.contains(token.toUpperCase(Locale.ROOT)) || name.contains(normalized)) {
                return true;
            }
        }
        return false;
    }

    private String text(String value) {
        return value == null ? "" : value;
    }

    public record SemanticPart(String displayName, String category, String layer) {
    }
}
