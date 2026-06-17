package com.dropai.rewrite.modules.bomGenerator;

import com.dropai.rewrite.modules.model.DesignProject;
import org.springframework.stereotype.Service;

@Service
public class BOMGenerator {
    public DesignProject generate(DesignProject project) {
        project.setBom(project.getComponents().stream()
                .map(component -> new DesignProject.BomItem(component.getSequence(), component.getName(),
                        component.getMaterial(), component.getQuantity(), component.getFunction()))
                .toList());
        project.getTechnicalRequirements().removeIf(item -> item != null && item.contains("BOM"));
        project.getTechnicalRequirements().add("BOM中的零件必须能在总装图、零件图和论文结构图中找到对应序号。");
        project.getTechnicalRequirements().add("图纸中的部件编号必须与BOM序号一致，禁止出现无来源结构。");
        return project;
    }
}
