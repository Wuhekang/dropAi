package com.dropai.rewrite.modules.drawingEngine;

import com.dropai.rewrite.modules.model.DesignProject;
import org.springframework.stereotype.Service;

@Service
public class AnnotationEngine {
    public void drawPartFeatureList(DrawingEngine.Canvas c, MechanicalFeatureLibrary.FeatureSet featureSet, double x, double y) {
        c.text("ANNOTATION", x, y, 4, "结构特征");
        int row = 0;
        for (String feature : featureSet.features().stream().limit(7).toList()) {
            c.text("ANNOTATION", x, y - 16 - row * 14, 3, "· " + feature);
            row++;
        }
    }

    public void drawMaterialBlock(DrawingEngine.Canvas c, DesignProject.Component part, double x, double y) {
        c.text("ANNOTATION", x, y, 3.2, "材料：" + material(part));
        c.text("ANNOTATION", x, y - 16, 3.2, "数量：" + Math.max(1, part.getQuantity()));
        c.text("ANNOTATION", x, y - 32, 3.2, "序号：" + Math.max(1, part.getSequence()));
    }

    private String material(DesignProject.Component part) {
        return part.getMaterial() == null || part.getMaterial().isBlank() ? "Q235B" : part.getMaterial();
    }
}
