package com.dropai.rewrite.modules.drawingEngine;

import com.dropai.rewrite.modules.model.DesignProject;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;

@Component
public class DimensionSourceValidator {
    private static final List<String> ALLOWED = List.of(
            "taskbook",
            "calculation",
            "standard_part",
            "assembly_constraint"
    );

    public boolean isValid(DesignProject.DimensionChain dimension) {
        return classify(dimension).valid();
    }

    public SourceStatus classify(DesignProject.DimensionChain dimension) {
        String source = dimension == null || dimension.getSource() == null ? "" : dimension.getSource().toLowerCase(Locale.ROOT);
        if (source.contains("envelope") || source.contains("component.size") || source.contains("debug") || source.contains("包络")) {
            return new SourceStatus(false, "invalid");
        }
        for (String allowed : ALLOWED) {
            if (source.contains(allowed)) {
                return new SourceStatus(true, allowed);
            }
        }
        if (source.contains("任务书")) return new SourceStatus(true, "taskbook");
        if (source.contains("计算")) return new SourceStatus(true, "calculation");
        if (source.contains("标准件")) return new SourceStatus(true, "standard_part");
        if (source.contains("装配约束") || source.contains("安装距") || source.contains("孔距")) {
            return new SourceStatus(true, "assembly_constraint");
        }
        if (dimension != null && dimension.getValue() <= 0) {
            return new SourceStatus(true, "pending");
        }
        return new SourceStatus(false, "missing");
    }

    public void validateView(DesignProject.DrawingViewPlan view) {
        for (DesignProject.DimensionChain dimension : view.getDimensions()) {
            if (!isValid(dimension)) {
                throw new IllegalStateException("CAD尺寸来源不可信：" + dimension.getName());
            }
        }
    }

    public record SourceStatus(boolean valid, String sourceType) {
    }
}
