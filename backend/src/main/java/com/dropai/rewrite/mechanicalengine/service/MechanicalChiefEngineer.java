package com.dropai.rewrite.mechanicalengine.service;

import com.dropai.rewrite.mechanicalengine.domain.MechanicalProject;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class MechanicalChiefEngineer {
    public MechanicalProject design(String requirementText) {
        if (requirementText == null || requirementText.isBlank()) throw new IllegalArgumentException("机械需求不能为空");
        MechanicalProject project = new MechanicalProject();
        project.setProjectId("mech_" + UUID.randomUUID().toString().replace("-", ""));
        project.setProductName(productName(requirementText));
        project.setScenario(scenario(requirementText));
        project.getRequirement().setFunctions(functions(requirementText));
        project.getRequirement().setConstraints(List.of("任务书尺寸仅作为边界约束，不直接驱动零件几何", "所有关键参数必须由工程计算给出理由"));

        List<String> modules = modules(requirementText);
        project.getConcept().setSelectedConcept(concept(requirementText));
        project.getConcept().setSelectionReason("根据使用场景、可靠性、制造可行性和维护性选择；进入CAD前仍需工程师审批");
        project.getConcept().setModules(modules);
        project.setParameters(List.of(
                new MechanicalProject.EngineeringParameter("安全系数", 2.0, "", "覆盖未知载荷、冲击和制造偏差"),
                new MechanicalProject.EngineeringParameter("设计载荷", 1000, "N", "由目标工况和安全系数形成初始设计载荷"),
                new MechanicalProject.EngineeringParameter("目标速度", 0.2, "m/s", "兼顾作业效率、驱动功率和稳定性")
        ));
        buildAssembly(project, modules);
        buildParts(project, modules);
        project.getDrawings().setPartDrawings(project.getParts().stream().map(MechanicalProject.CADSpecification::partNumber).toList());
        return project;
    }

    private void buildAssembly(MechanicalProject project, List<String> modules) {
        List<MechanicalProject.AssemblyComponent> components = new ArrayList<>();
        List<MechanicalProject.Mate> mates = new ArrayList<>();
        int index = 1;
        for (String module : modules) {
            String id = "C%03d".formatted(index++);
            components.add(new MechanicalProject.AssemblyComponent(id, module, "Assembly",
                    new MechanicalProject.Pose((index - 2) * 120, 0, 0), new MechanicalProject.Pose(0, 0, 0)));
            mates.add(new MechanicalProject.Mate("Coincident", id, "Front Plane", "C001", "Front Plane"));
        }
        project.getAssembly().setRoot(project.getProductName() + " Assembly");
        project.getAssembly().setComponents(components);
        project.getAssembly().setMates(mates);
    }

    private void buildParts(MechanicalProject project, List<String> modules) {
        List<MechanicalProject.CADSpecification> parts = new ArrayList<>();
        int index = 1;
        for (String module : modules) {
            parts.add(new MechanicalProject.CADSpecification("P%03d".formatted(index++), module + "安装件", "Q235B",
                    List.of(new MechanicalProject.Feature(1, "Sketch", "Top Plane", "fully-defined profile"),
                            new MechanicalProject.Feature(2, "Extrude", "", "mid-plane thickness=8mm"),
                            new MechanicalProject.Feature(3, "HoleWizard", "", "mounting pattern from assembly interfaces"),
                            new MechanicalProject.Feature(4, "Fillet", "", "R2 manufacturing edge"),
                            new MechanicalProject.Feature(5, "Chamfer", "", "1x45deg assembly lead-in"))));
        }
        project.setParts(parts);
    }

    private String productName(String text) { return text.lines().findFirst().orElse("机械设备").trim(); }
    private String scenario(String text) { return text.contains("油罐") ? "油罐壁面检测" : text.contains("AGV") ? "工业搬运" : "工业机械作业"; }
    private String concept(String text) { return text.contains("爬壁") ? "双履带磁吸附模块化机器人" : text.contains("AGV") ? "差速轮式模块化AGV" : "模块化机电一体化设备"; }
    private List<String> functions(String text) { return text.contains("检测") ? List.of("移动", "稳定支撑", "检测", "数据/控制载荷承载", "维护") : List.of("执行主功能", "驱动", "支撑", "控制", "维护"); }
    private List<String> modules(String text) { return text.contains("爬壁") ? List.of("Frame", "Left Track", "Right Track", "Adhesion", "Sensor", "Control") : List.of("Frame", "Drive", "Transmission", "Working", "Sensor", "Control"); }
}
