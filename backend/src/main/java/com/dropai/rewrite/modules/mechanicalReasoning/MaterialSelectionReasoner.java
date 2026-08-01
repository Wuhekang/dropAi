package com.dropai.rewrite.modules.mechanicalReasoning;

import com.dropai.rewrite.modules.mechanicalDesignPlanner.MechanicalDesignPlan;
import com.dropai.rewrite.modules.model.DesignProject;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class MaterialSelectionReasoner {
    public List<String> decide(DesignProject project, MechanicalDesignPlan plan) {
        List<String> decisions = new ArrayList<>();
        if (plan != null && !plan.getMaterialSelection().isEmpty()) {
            plan.getMaterialSelection().forEach((part, material) ->
                    decisions.add(part + ": " + material + "，依据强度、成本、加工性和使用环境选取"));
        }
        if (decisions.isEmpty()) {
            decisions.add("机架: Q235，成本低、焊接性好、适合承载结构");
            decisions.add("轴类: 45钢，调质后强度和韧性平衡");
            decisions.add("轻量化安装件: 6061铝合金，重量低且易加工");
        }
        return decisions;
    }
}
