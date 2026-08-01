package com.dropai.rewrite.modules.mechanicalReasoning;

import com.dropai.rewrite.modules.mechanicalDesignPlanner.MechanicalDesignPlan;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class ManufacturingReasoner {
    public MechanicalDesignPlan.ManufacturingPlan plan(MechanicalDesignPlan designPlan, List<String> materialDecisions) {
        MechanicalDesignPlan.ManufacturingPlan plan = new MechanicalDesignPlan.ManufacturingPlan();
        List<String> processes = new ArrayList<>();
        processes.add("CNC加工: 轴、安装板、关键定位面");
        processes.add("钣金/焊接: 机架、罩壳、支撑件");
        processes.add("标准件采购: 电机、轴承、导轨、紧固件");
        processes.add("3D打印/样件: 传感器支架、非承载护罩");
        plan.setProcesses(processes);
        plan.setMaterialDecisions(materialDecisions);
        plan.setManufacturabilityNotes(List.of(
                "关键安装面需统一基准，减少装配累积误差",
                "焊接件焊后校形，机加工面后加工",
                "运动副保留润滑、调节和拆装空间"
        ));
        return plan;
    }
}
