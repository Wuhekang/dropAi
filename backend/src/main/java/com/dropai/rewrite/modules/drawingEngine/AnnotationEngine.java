package com.dropai.rewrite.modules.drawingEngine;

import com.dropai.rewrite.modules.model.DesignProject;
import org.springframework.stereotype.Service;

@Service
public class AnnotationEngine {
    public void drawAssemblyAnnotations(DrawingEngine.Canvas c, DesignProject p) {
        c.text("ANNOTATION", 88, 300, 3.2, "基准A：机架安装底面");
        c.text("ANNOTATION", 88, 284, 3.2, "基准B：履带中心平面");
        c.rect("TOLERANCE", 690, 215, 86, 14);
        c.line("TOLERANCE", 718, 215, 718, 229);
        c.line("TOLERANCE", 748, 215, 748, 229);
        c.text("TOLERANCE", 694, 219, 3, "⊥");
        c.text("TOLERANCE", 724, 219, 3, "0.10");
        c.text("TOLERANCE", 754, 219, 3, "A");
        c.text("ANNOTATION", 690, 195, 3.2, "粗糙度：未注Ra6.3，安装面Ra3.2");
        c.text("ANNOTATION", 690, 177, 3.2, "主要材料：" + p.getComponents().stream()
                .findFirst().map(DesignProject.Component::getMaterial).orElse("Q235B"));
    }

    public void drawPartAnnotations(DrawingEngine.Canvas c, DesignProject.Component p) {
        c.text("ANNOTATION", 510, 305, 3.2, "形位公差：安装孔位置度0.20");
        c.text("ANNOTATION", 510, 288, 3.2, "粗糙度：配合面Ra3.2，其余Ra6.3");
        c.text("ANNOTATION", 510, 271, 3.2, "材料：" + p.getMaterial());
        c.text("ANNOTATION", 510, 254, 3.2, "表面处理按任务书和装配要求确定");
    }
}
