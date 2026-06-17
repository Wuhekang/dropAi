package com.dropai.rewrite.modules.drawingEngine;

import com.dropai.rewrite.modules.model.DesignProject;
import org.springframework.stereotype.Service;

@Service
public class AnnotationEngine {
    public void drawAssemblyAnnotations(DrawingEngine.Canvas c, DesignProject p) {
        c.text("ANNOTATION", 88, 470, 3.2, "基准A：安装底面");
        c.text("ANNOTATION", 88, 452, 3.2, "基准B：接口中心线");
        c.rect("TOLERANCE", 690, 232, 86, 14);
        c.line("TOLERANCE", 718, 232, 718, 246);
        c.line("TOLERANCE", 748, 232, 748, 246);
        c.text("TOLERANCE", 694, 236, 3, "⊥");
        c.text("TOLERANCE", 724, 236, 3, "0.10");
        c.text("TOLERANCE", 754, 236, 3, "A");
        c.text("ANNOTATION", 690, 210, 3.2, "粗糙度：未注Ra6.3，安装面Ra3.2");
        c.text("ANNOTATION", 690, 190, 3.2, "材料：" + p.getComponents().stream().findFirst().map(DesignProject.Component::getMaterial).orElse("Q235B"));
    }

    public void drawPartAnnotations(DrawingEngine.Canvas c, DesignProject.Component p) {
        c.text("ANNOTATION", 510, 305, 3.2, "形位公差：安装孔位置度Φ0.20");
        c.text("ANNOTATION", 510, 288, 3.2, "粗糙度：配合面Ra3.2，其余Ra6.3");
        c.text("ANNOTATION", 510, 271, 3.2, "材料：" + p.getMaterial());
        c.text("ANNOTATION", 510, 254, 3.2, "热处理/表面处理按任务书确认");
    }
}
