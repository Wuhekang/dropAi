package com.dropai.rewrite.mechanicalengine.service;

import com.dropai.rewrite.mechanicalengine.domain.MechanicalDesignSpec;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
public class MechanicalDesignPlanner {
    public MechanicalDesignSpec plan(String requirement) {
        if (requirement == null || requirement.isBlank()) throw new IllegalArgumentException("Mechanical requirement cannot be empty");
        String normalized = requirement.toLowerCase();
        if (!normalized.contains("clamp") && !requirement.contains("夹具")) {
            throw new IllegalArgumentException("UNSUPPORTED_MECHANICAL_PRODUCT: phase 1 supports automatic clamp design");
        }
        return automaticClamp();
    }

    private MechanicalDesignSpec automaticClamp() {
        List<MechanicalDesignSpec.Module> modules = List.of(
                module("M01", "底座模块", "承受夹紧反力并连接机台", List.of("机台安装面", "导向安装面"), "通过四组螺栓孔固定于机台"),
                module("M02", "夹持模块", "建立固定基准并向工件施加夹紧力", List.of("固定钳口基准面", "移动钳口夹持面"), "钳口安装于底座上表面"),
                module("M03", "驱动模块", "将输入转矩转换为轴向夹紧力", List.of("丝杆轴线", "移动钳口螺纹接口"), "丝杆与移动钳口同轴连接"),
                module("M04", "导向模块", "限制移动钳口转动和横向偏移", List.of("底座导向面", "移动钳口导向面"), "滑动配合安装于底座导向面")
        );
        List<MechanicalDesignSpec.PartPlan> parts = List.of(
                part("P001", "底座", "M01", "承载夹紧机构并传递反力", "Q235B", "CNC铣削",
                        feature(1,"SKETCH","建立受约束的底座平面轮廓",Map.of("profile","rectangle","length",260,"width",120)),
                        feature(2,"PAD","形成20 mm承载板厚",Map.of("length",20)),
                        feature(3,"HOLE","形成机台安装孔",Map.of("diameter",12,"depth",20)),
                        feature(4,"FILLET","降低底座边缘应力集中",Map.of("radius",4))),
                part("P002", "固定钳口", "M02", "提供工件定位基准和固定夹持面", "45钢", "CNC铣削",
                        feature(1,"SKETCH","建立固定钳口轮廓",Map.of("profile","rectangle","length",40,"width",120)),
                        feature(2,"PAD","形成65 mm钳口高度",Map.of("length",65)),
                        feature(3,"HOLE","形成与底座连接孔",Map.of("diameter",10,"depth",65)),
                        feature(4,"CHAMFER","去除装配锐边",Map.of("distance",1))),
                part("P003", "移动钳口", "M02", "沿导向方向移动并施加夹紧力", "45钢", "CNC铣削",
                        feature(1,"SKETCH","建立移动钳口轮廓",Map.of("profile","rectangle","length",45,"width",110)),
                        feature(2,"PAD","形成60 mm钳口高度",Map.of("length",60)),
                        feature(3,"HOLE","形成丝杆连接通孔",Map.of("diameter",22,"depth",60)),
                        feature(4,"FILLET","降低交变载荷处应力集中",Map.of("radius",3))),
                part("P004", "梯形丝杆", "M03", "把手转矩转换为自锁轴向推力", "40Cr", "车削及滚丝",
                        feature(1,"SKETCH","建立丝杆轴截面",Map.of("profile","circle","diameter",20)),
                        feature(2,"PAD","形成220 mm丝杆毛坯",Map.of("length",220)),
                        feature(3,"HOLE","形成把手横孔",Map.of("diameter",10,"depth",30)),
                        feature(4,"CHAMFER","形成轴端装配倒角",Map.of("distance",1))),
                part("P005", "旋转把手", "M03", "向丝杆输入人工转矩", "Q235", "车削",
                        feature(1,"SKETCH","建立把手截面",Map.of("profile","circle","diameter",10)),
                        feature(2,"PAD","形成180 mm把手杆",Map.of("length",180)),
                        feature(3,"FILLET","消除握持端锐边",Map.of("radius",2)))
        );
        return new MechanicalDesignSpec(
                new MechanicalDesignSpec.Product("automatic_clamp", "可调梯形丝杆夹具", "工件定位与可靠夹紧", "室内机加工环境", "梯形丝杆将输入转矩转换为移动钳口轴向夹紧力", List.of("定位", "夹紧", "自锁", "快速维护")),
                new MechanicalDesignSpec.Requirements(List.of("夹持宽度可调", "夹紧后保持自锁"), List.of("夹紧力不低于3 kN", "安全系数不低于2"), List.of("机加工振动", "切削液飞溅"), List.of("任务书尺寸仅作需求背景", "所有尺寸由载荷和安装关系确定")),
                List.of(new MechanicalDesignSpec.FunctionNode("夹具总功能", "定位并夹紧工件", List.of(
                        leaf("承载功能"), leaf("定位功能"), leaf("夹持功能"), leaf("驱动功能"), leaf("导向功能")))),
                new MechanicalDesignSpec.Architecture("梯形丝杆驱动双钳口夹具", "具备自锁、夹紧力可控、制造简单和维护方便的综合优势", List.of("把手", "丝杆", "移动钳口", "工件", "固定钳口", "底座", "机台"), List.of("把手旋转", "丝杆旋转", "移动钳口直线移动")),
                modules, parts,
                List.of(
                        intent("FIXED","P001","安装基准","ASSEMBLY","机台基准","建立总装固定基准"),
                        intent("COINCIDENT","P002","底面","P001","上表面","安装固定钳口"),
                        intent("SLIDER","P003","导向面","P001","纵向导轨","限制移动钳口为单自由度"),
                        intent("CONCENTRIC","P004","轴线","P003","丝杆孔轴线","传递轴向夹紧力"),
                        intent("CONCENTRIC","P005","轴线","P004","横孔轴线","传递人工转矩")),
                List.of(
                        parameter("maximum_workpiece_width",120,"mm","覆盖常见小型工件并保留约20%的调节余量"),
                        parameter("design_clamping_force",3000,"N","按600 N切削扰动力、0.25摩擦系数和安全裕量确定"),
                        parameter("base_length",260,"mm","容纳120 mm夹持行程、钳口厚度和丝杆支承区"),
                        parameter("base_width",120,"mm","确保横向稳定并适配常见机台安装空间"),
                        parameter("design_safety_factor",2.2,"","覆盖载荷波动和人工过度拧紧")),
                parts.stream().map(part -> new MechanicalDesignSpec.MaterialDecision(part.partNumber(),part.material(),"满足该零件载荷、耐磨性和成本要求")).toList(),
                parts.stream().map(part -> new MechanicalDesignSpec.ManufacturingDecision(part.partNumber(),part.manufacturing(),"与零件形状、材料和批量匹配")).toList()
        );
    }

    private MechanicalDesignSpec.Module module(String id,String name,String function,List<String> interfaces,String installation){return new MechanicalDesignSpec.Module(id,name,function,interfaces,installation);}
    private MechanicalDesignSpec.PartPlan part(String number,String name,String module,String function,String material,String manufacturing,MechanicalDesignSpec.FeatureRequirement... features){return new MechanicalDesignSpec.PartPlan(number,name,module,function,material,manufacturing,List.of(features));}
    private MechanicalDesignSpec.FeatureRequirement feature(int order,String type,String intent,Map<String,Object> parameters){return new MechanicalDesignSpec.FeatureRequirement(order,type,intent,parameters);}
    private MechanicalDesignSpec.FunctionNode leaf(String name){return new MechanicalDesignSpec.FunctionNode(name,name,List.of());}
    private MechanicalDesignSpec.AssemblyIntent intent(String type,String a,String ar,String b,String br,String purpose){return new MechanicalDesignSpec.AssemblyIntent(type,a,ar,b,br,purpose);}
    private MechanicalDesignSpec.Parameter parameter(String name,double value,String unit,String reason){return new MechanicalDesignSpec.Parameter(name,value,unit,reason);}
}
