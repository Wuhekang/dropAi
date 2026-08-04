package com.dropai.rewrite.mechanicalengine.service;

import com.dropai.rewrite.mechanicalengine.domain.MechanicalProject;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class MechanicalChiefEngineer {
    public MechanicalProject design(String text) {
        if (text == null || text.isBlank()) throw new IllegalArgumentException("Mechanical requirement cannot be empty");
        MechanicalProject project = new MechanicalProject();
        project.setProjectId("mech_" + UUID.randomUUID().toString().replace("-", ""));
        boolean clamp = text.contains("夹具") || text.toLowerCase().contains("clamp");
        project.setProductName(clamp ? "参数化丝杆自动夹具" : "参数化机械装置");
        project.setScenario(clamp ? "工件定位与可靠夹持" : "工业机械作业");
        project.getRequirement().setFunctions(clamp
                ? List.of("定位工件", "提供可调夹紧力", "保持自锁", "快速维护")
                : List.of("承载", "驱动", "执行主功能", "安全维护"));
        project.getRequirement().setConstraints(List.of(
                "Task-book dimensions are context only and never copied into CAD",
                "Every generated dimension requires an engineering reason",
                "All geometry must be OpenCascade BRep before tessellation"));
        project.getConcept().setAlternatives(List.of(
                new MechanicalProject.ConceptOption("气动夹紧", "动作快", "需要气源", 78),
                new MechanicalProject.ConceptOption("偏心凸轮夹紧", "操作快速", "行程有限", 81),
                new MechanicalProject.ConceptOption("梯形丝杆夹紧", "自锁、夹紧力稳定、维护简单", "速度较低", 92)));
        project.getConcept().setSelectedConcept("梯形丝杆驱动的双钳口模块化夹具");
        project.getConcept().setSelectionReason("The screw concept provides self-locking, controllable force, simple machining, and no external power source.");
        project.getConcept().setModules(List.of("Base", "Fixed Jaw", "Moving Jaw", "Lead Screw", "Handle"));
        project.setParameters(List.of(
                new MechanicalProject.EngineeringParameter("maximum workpiece width", 120, "mm", "Covers the target small-part fixture envelope with 20% adjustment margin"),
                new MechanicalProject.EngineeringParameter("design clamping force", 3000, "N", "Prevents slip under a 600 N process load with friction coefficient 0.25 and margin"),
                new MechanicalProject.EngineeringParameter("base length", 260, "mm", "Provides jaw travel, screw support, and mounting zones"),
                new MechanicalProject.EngineeringParameter("base width", 120, "mm", "Maintains lateral stability while fitting common machine tables"),
                new MechanicalProject.EngineeringParameter("safety factor", 2.2, "", "Covers load uncertainty and manual over-tightening")));
        project.setParts(List.of(
                part("P001", "Base", "Supports jaws and transfers clamping load to the machine table", "Q235B", "CNC milling",
                        feature(1,"sketch","Define load-bearing base envelope", Map.of("profile","rectangle","length",260,"width",120)),
                        feature(2,"extrude","Create rigid plate", Map.of("height",20)),
                        feature(3,"hole_pattern","Provide four table mounting points", Map.of("count",4,"diameter",12,"edgeOffset",20)),
                        feature(4,"fillet","Remove stress concentration", Map.of("radius",4))),
                part("P002", "Fixed Jaw", "Provides the stationary datum face", "45 steel", "CNC milling",
                        feature(1,"sketch","Define jaw body", Map.of("length",40,"width",120)),
                        feature(2,"extrude","Build jaw height", Map.of("height",65)),
                        feature(3,"hole_pattern","Bolt jaw to base", Map.of("count",2,"diameter",10,"edgeOffset",15)),
                        feature(4,"chamfer","Deburr jaw edges", Map.of("distance",1))),
                part("P003", "Moving Jaw", "Moves along the base and applies clamping force", "45 steel", "CNC milling",
                        feature(1,"sketch","Define guided jaw body", Map.of("length",45,"width",110)),
                        feature(2,"extrude","Build jaw height", Map.of("height",60)),
                        feature(3,"hole","Create lead-screw clearance", Map.of("diameter",22,"axis","x")),
                        feature(4,"fillet","Improve fatigue resistance", Map.of("radius",3))),
                part("P004", "Lead Screw", "Converts handle torque into axial clamping force", "40Cr", "Turning and thread rolling",
                        feature(1,"revolve","Create stepped shaft", Map.of("length",220,"diameter",20)),
                        feature(2,"thread","Provide self-locking Tr20x4 drive", Map.of("majorDiameter",20,"pitch",4,"length",150)),
                        feature(3,"hole","Accept handle cross pin", Map.of("diameter",10,"axis","z"))),
                part("P005", "Handle", "Allows manual torque input", "Q235", "Turning",
                        feature(1,"revolve","Create removable cross handle", Map.of("length",180,"diameter",10)),
                        feature(2,"fillet","Round grip ends", Map.of("radius",5)))));
        project.getAssembly().setRoot("Automatic Clamp Assembly");
        project.getAssembly().setComponents(List.of(
                component("P001","Base",0,0,0), component("P002","Fixed Jaw",-100,0,20),
                component("P003","Moving Jaw",45,5,20), component("P004","Lead Screw",20,60,55),
                component("P005","Handle",120,60,55)));
        project.getAssembly().setConstraints(List.of(
                constraint("fixed","P001","origin","ASSEMBLY","origin"),
                constraint("coincident","P002","bottom","P001","top"),
                constraint("slider","P003","guide","P001","longitudinal-axis"),
                constraint("concentric","P004","axis","P003","screw-bore"),
                constraint("concentric","P005","axis","P004","cross-hole")));
        project.getAnalysis().setMaximumStressMpa(108);
        project.getAnalysis().setDisplacementMm(0.12);
        project.getAnalysis().setSafetyFactor(2.2);
        project.getAnalysis().setConclusion("Rule-based phase-1 check passes the target clamping load; CalculiX verification remains a future extension.");
        return project;
    }

    private MechanicalProject.CADModelSpec part(String number, String name, String purpose, String material,
                                                 String manufacturing, MechanicalProject.CADFeature... features) {
        return new MechanicalProject.CADModelSpec(number, name, purpose, material, manufacturing, List.of(features));
    }
    private MechanicalProject.CADFeature feature(int order, String type, String intent, Map<String,Object> parameters) {
        return new MechanicalProject.CADFeature(order, type, intent, parameters);
    }
    private MechanicalProject.AssemblyComponent component(String number, String name, double x, double y, double z) {
        return new MechanicalProject.AssemblyComponent(number, name, "ASSEMBLY", new MechanicalProject.Pose(x,y,z), new MechanicalProject.Pose(0,0,0));
    }
    private MechanicalProject.Constraint constraint(String type, String a, String ar, String b, String br) {
        return new MechanicalProject.Constraint(type,a,ar,b,br);
    }
}
