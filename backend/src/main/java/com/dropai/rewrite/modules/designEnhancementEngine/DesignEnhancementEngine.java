package com.dropai.rewrite.modules.designEnhancementEngine;

import com.dropai.rewrite.modules.model.DesignProject;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Stream;

@Service
public class DesignEnhancementEngine {
    private static final Map<String, RuleSet> RULES = new LinkedHashMap<>();

    static {
        RULES.put("sedimentation", new RuleSet(
                List.of("重力沉降室", "沉降室", "除尘", "灰斗", "沉降腔"),
                "重力沉降室", "环保设备结构设计", "工业除尘设备",
                List.of("含尘气流降速沉降", "颗粒物收集", "设备检修维护", "进出口法兰连接", "卸灰排灰"),
                List.of(
                        spec("INTERFACE", "进风口", "导入含尘气流并降低入口冲击", "Q235B", 1, "DUCT_X", true),
                        spec("BODY", "沉降腔", "提供低速沉降空间并形成主体承载结构", "Q235B", 1, "CHAMBER", true),
                        spec("FUNCTION", "导流板", "改善气流分布并降低局部涡流", "Q235B", 2, "PLATE", true),
                        spec("FUNCTION", "布流板", "均匀分配入口气流并形成稳定沉降区", "Q235B", 1, "PERFORATED_PLATE", true),
                        spec("FUNCTION", "排灰斗", "收集沉降颗粒并导向卸灰口", "Q235B", 2, "HOPPER", true),
                        spec("MAINTENANCE", "检修门", "提供内部检查和清灰通道", "Q235B", 2, "DOOR", true),
                        spec("MAINTENANCE", "观察窗", "观察内部积灰与运行状态", "有机玻璃", 1, "WINDOW", false),
                        spec("INTERFACE", "出风口", "导出净化后的气流", "Q235B", 1, "DUCT_X", true),
                        spec("SUPPORT", "支撑架", "承载箱体和灰斗并传递至基础", "Q235B", 4, "FRAME", true),
                        spec("INTERFACE", "法兰", "与外部管道螺栓连接", "Q235B", 2, "FLANGE", true),
                        spec("STRUCTURE", "加强筋", "提高大面积板件刚度", "Q235B", 8, "RIB", false),
                        spec("FUNCTION", "卸灰口", "连接星型卸料器或灰桶", "Q235B", 2, "DUCT_Z", true),
                        spec("FUNCTION", "排灰阀", "控制灰斗排灰并保持密封", "标准件", 2, "VALVE", true),
                        spec("SAFETY", "顶部护栏", "提供顶部检修安全防护", "Q235B", 1, "RAILING", false),
                        spec("MAINTENANCE", "爬梯", "提供顶部检修通道", "Q235B", 1, "LADDER", false),
                        spec("MOUNT", "底座板", "连接支腿和地脚螺栓", "Q235B", 4, "BASE_PLATE", false),
                        spec("MOUNT", "地脚螺栓", "固定设备基础", "标准件", 8, "ANCHOR_BOLT", false),
                        spec("LIFTING", "吊耳", "用于运输和安装吊装", "Q235B", 4, "LUG", false),
                        spec("CONNECT", "焊缝标识", "标识箱体、灰斗和支撑焊接位置", "焊缝", 1, "WELD", false)
                ),
                List.of(
                        param("处理风量", 10000, "m3/h", "按工业除尘设备常用范围8000~12000m3/h取中值"),
                        param("设计风速", 0.8, "m/s", "按重力沉降室常用沉降风速0.5~1.2m/s确定"),
                        param("停留时间", 3.3, "s", "按沉降室有效容积与处理风量估算"),
                        param("有效容积", 18.1, "m3", "按箱体有效长宽高计算"),
                        param("入口管径", "DN200", "", "按处理风量与入口风速校核建议"),
                        param("出口管径", "DN200", "", "按出口阻力和连接标准建议"),
                        param("法兰规格", "DN200 PN10", "", "按管口连接和密封要求建议"),
                        param("箱体材料", "Q235B", "", "焊接箱体常用碳素结构钢"),
                        param("壳体板厚", 8, "mm", "按箱体尺寸和刚度要求取6~10mm方案值"),
                        param("灰斗角度", 60, "°", "按粉尘顺利下滑的常用结构角确定"),
                        param("检修门数量", 2, "个", "按沉降腔两侧维护需求设置"),
                        param("观察窗数量", 1, "个", "按运行观察需求设置"),
                        param("加强筋数量", 10, "道", "按大面积侧板刚度和焊接结构布置"),
                        param("支撑腿数量", 4, "组", "按箱体稳定支撑和基础安装设置"),
                        param("卸灰方式", "星型卸料器", "", "按连续排灰和密封要求建议")
                )
        ));
        RULES.put("conveyor", new RuleSet(
                List.of("输送机", "输送带", "带式输送", "滚筒"),
                "带式输送机", "输送设备设计", "机械输送设备",
                List.of("连续输送物料", "承载物料", "张紧调偏", "动力传递", "设备支撑安装"),
                List.of(
                        spec("FUNCTION", "驱动滚筒", "驱动输送带运行", "45钢", 1, "CYLINDER_Y", true),
                        spec("FUNCTION", "从动滚筒", "改变输送带运行方向", "45钢", 1, "CYLINDER_Y", true),
                        spec("FUNCTION", "输送带", "承载并连续输送物料", "橡胶复合材料", 1, "BELT", true),
                        spec("SUPPORT", "机架", "支撑滚筒、托辊和输送带", "Q235B", 1, "TRUSS", true),
                        spec("DRIVE", "电机", "提供输送动力", "标准件", 1, "MOTOR", true),
                        spec("DRIVE", "减速器", "降低转速并提高输出扭矩", "标准件", 1, "GEARBOX", true),
                        spec("FUNCTION", "托辊组", "支撑输送带并减小运行阻力", "标准件", 6, "ROLLER", false),
                        spec("FUNCTION", "张紧装置", "调整输送带初张力", "Q235B", 1, "TENSIONER", true),
                        spec("SAFETY", "防护罩", "覆盖传动部位防止卷入", "Q235B", 1, "COVER", false),
                        spec("SUPPORT", "支腿", "将运行载荷传递至基础", "Q235B", 4, "FRAME", false)
                ),
                List.of(
                        param("输送量", 30, "t/h", "按小型带式输送机毕业设计常用工况取值"),
                        param("带速", 1.25, "m/s", "按普通物料输送常用带速确定"),
                        param("带宽", 500, "mm", "按输送量和物料粒度建议"),
                        param("滚筒直径", 320, "mm", "按带宽和张力初步选取"),
                        param("电机功率", 3, "kW", "按输送阻力和功率储备建议")
                )
        ));
        RULES.put("wallCrawlerRobot", new RuleSet(
                List.of("油罐检测爬壁机器人", "爬壁机器人", "爬壁", "履带", "磁吸附", "油罐检测", "清扫刷", "壁面检测"),
                "油罐检测爬壁机器人", "机械结构设计 / 机器人结构设计 / 机电一体化设计", "磁吸附履带式检测机器人",
                List.of("油罐壁面爬行", "磁吸附稳定附着", "表面清扫", "检测模块安装", "壁面缺陷检测", "模块化维护"),
                List.of(
                        spec("FUNCTION", "履带行走机构", "实现机器人沿油罐壁面低速稳定爬行", "橡胶复合材料", 2, "TRACK", true),
                        spec("FUNCTION", "驱动轮", "驱动履带并传递电机输出扭矩", "45钢包胶", 2, "WHEEL", true),
                        spec("FUNCTION", "从动轮", "支撑履带回转并保持张紧", "45钢包胶", 2, "WHEEL", true),
                        spec("FUNCTION", "支重轮", "分担机体载荷并稳定履带接触", "45钢包胶", 8, "SMALL_WHEEL", false),
                        spec("FUNCTION", "履带", "提供壁面接触和行走牵引", "耐磨橡胶", 2, "BELT", true),
                        spec("MOUNT", "永磁吸附机构", "提供持续吸附力并保持壁面附着", "钕铁硼磁钢+Q235B", 1, "MAGNET_ARRAY", true),
                        spec("MOUNT", "磁吸附模块", "按履带内侧分段布置磁吸单元", "钕铁硼磁钢", 8, "MAGNET_BLOCK", true),
                        spec("FUNCTION", "圆盘清扫刷", "清理检测区域表面浮尘和附着物", "尼龙刷丝", 1, "BRUSH", true),
                        spec("DRIVE", "清扫驱动电机", "驱动圆盘刷旋转清扫", "标准件", 1, "MOTOR", true),
                        spec("FUNCTION", "检测传感器安装架", "安装检测探头并保持检测距离", "6061铝合金", 1, "SENSOR_RAIL", true),
                        spec("FUNCTION", "滑轨调节机构", "调节检测模块高度和前后位置", "标准直线滑轨", 1, "SLIDER", true),
                        spec("CONNECT", "快拆结构", "便于清扫刷和检测模块快速维护", "不锈钢", 2, "QUICK_RELEASE", false),
                        spec("SUPPORT", "机架", "承载履带、磁吸、检测和清扫模块", "6061铝合金", 1, "FRAME", true),
                        spec("SAFETY", "防护外壳", "保护电气舱和传动部件", "ABS+铝板", 1, "COVER", true),
                        spec("DRIVE", "驱动电机", "为左右履带提供动力", "标准件", 2, "MOTOR", true),
                        spec("DRIVE", "减速器", "降低转速并提高履带输出扭矩", "标准件", 2, "GEARBOX", true),
                        spec("BODY", "电池/控制模块安装舱", "安装电池、控制器和通信模块", "6061铝合金", 1, "BATTERY_BOX", true),
                        spec("CONNECT", "螺栓连接组", "连接外壳、机架和功能模块", "8.8级螺栓", 16, "BOLT_GROUP", false),
                        spec("STRUCTURE", "加强筋", "提高机架和传感器支架刚度", "6061铝合金", 6, "RIB", false),
                        spec("MAINTENANCE", "模块检修盖", "提供电池和控制模块维护入口", "ABS", 1, "DOOR", false)
                ),
                List.of(
                        param("适用壁面", "碳钢、不锈钢", "", "任务书技术指标"),
                        param("爬行速度", "0.1～0.5", "m/min", "任务书技术指标"),
                        param("吸附力", 220, "N", "按任务书≥200N并留有安全裕量"),
                        param("清扫效率", 95, "%", "任务书技术指标"),
                        param("检测精度", 0.1, "mm", "任务书技术指标≤±0.1mm"),
                        param("续航时间", 4, "h", "任务书技术指标≥4h"),
                        param("工作温度", "-20℃～60℃", "", "任务书技术指标"),
                        param("防护等级", "IP65", "", "任务书技术指标"),
                        param("总长", 780, "mm", "按整机尺寸≤800×600×300mm确定"),
                        param("总宽", 560, "mm", "按整机尺寸≤800×600×300mm确定"),
                        param("总高", 260, "mm", "按整机尺寸≤800×600×300mm确定"),
                        param("履带长度", 680, "mm", "按整机长度和前后轮布置确定"),
                        param("履带宽度", 95, "mm", "按壁面附着面积和结构紧凑性确定"),
                        param("轮径", 120, "mm", "按履带回转半径和越障裕量确定"),
                        param("轮距", 460, "mm", "按左右履带布置和机架宽度确定"),
                        param("磁吸附模块安装间距", 90, "mm", "按磁块沿履带内侧均布确定"),
                        param("清扫刷直径", 180, "mm", "按检测前清扫覆盖宽度确定"),
                        param("检测模块安装高度", 130, "mm", "按传感器工作距离和调节范围确定"),
                        param("机架板厚", 6, "mm", "按铝合金机架刚度和轻量化要求确定")
                )
        ));
        RULES.put("manipulator", new RuleSet(
                List.of("机械手", "夹爪", "机械臂", "末端执行器"),
                "机械手", "自动化设备设计", "机电一体化设备",
                List.of("工件抓取", "姿态调整", "搬运定位", "夹持释放", "限位保护"),
                List.of(
                        spec("BASE", "底座", "固定机械手并承受倾覆力矩", "Q235B", 1, "CYLINDER_Z", true),
                        spec("SUPPORT", "立柱", "支撑回转和升降机构", "45钢", 1, "CYLINDER_Z", true),
                        spec("DRIVE", "回转机构", "实现水平回转运动", "标准件", 1, "JOINT", true),
                        spec("DRIVE", "升降机构", "实现竖直方向位置调整", "标准件", 1, "SLIDER", true),
                        spec("FUNCTION", "大臂", "形成主要工作半径", "铝合金", 1, "ARM_XZ", true),
                        spec("FUNCTION", "小臂", "扩展末端执行器工作范围", "铝合金", 1, "ARM_XZ", true),
                        spec("FUNCTION", "夹爪", "夹持和释放工件", "合金钢", 1, "CLAW", true),
                        spec("SAFETY", "限位装置", "限制行程并保护机构", "标准件", 2, "LIMIT", false),
                        spec("SUPPORT", "轴承座", "支撑回转轴和关节轴", "HT200", 3, "BEARING_SEAT", true),
                        spec("MOUNT", "安装孔", "用于底座与基础连接", "标准特征", 8, "HOLE", false)
                ),
                List.of(
                        param("额定抓取质量", 5, "kg", "按本科机械手设计常见轻载工况确定"),
                        param("工作半径", 800, "mm", "按桌面或工位搬运范围建议"),
                        param("自由度", 4, "个", "满足回转、升降、伸缩和夹持动作"),
                        param("夹持行程", 80, "mm", "按常见小型工件尺寸建议")
                )
        ));
        RULES.put("reducer", new RuleSet(
                List.of("减速器", "齿轮箱", "齿轮传动"),
                "减速器", "机械传动设计", "机械传动设备",
                List.of("降低转速", "增大输出扭矩", "支撑传动轴系", "润滑密封", "检修维护"),
                List.of(
                        spec("BODY", "箱体", "支撑轴系并容纳齿轮传动", "HT200", 1, "CHAMBER", true),
                        spec("BODY", "箱盖", "封闭箱体并便于装配检修", "HT200", 1, "COVER", true),
                        spec("FUNCTION", "输入轴", "输入动力并安装小齿轮", "45钢", 1, "CYLINDER_X", true),
                        spec("FUNCTION", "输出轴", "输出扭矩并安装大齿轮", "45钢", 1, "CYLINDER_X", true),
                        spec("FUNCTION", "齿轮副", "完成传动比转换", "45钢", 1, "GEAR", true),
                        spec("SUPPORT", "轴承盖", "定位轴承并实现端部密封", "HT200", 4, "BEARING_CAP", true),
                        spec("MAINTENANCE", "观察孔", "观察啮合和润滑状态", "Q235B", 1, "WINDOW", false),
                        spec("MAINTENANCE", "放油孔", "排出润滑油", "标准件", 1, "HOLE", false),
                        spec("MAINTENANCE", "透气塞", "平衡箱体内外压力", "标准件", 1, "VENT", false),
                        spec("STRUCTURE", "加强筋", "提高箱体刚度", "HT200", 6, "RIB", false),
                        spec("MOUNT", "定位销", "保证箱盖与箱座定位", "标准件", 2, "PIN", false),
                        spec("MOUNT", "密封槽", "安装密封件并防止漏油", "标准特征", 2, "SEAL_GROOVE", false)
                ),
                List.of(
                        param("传动比", 8, "", "按单级或两级减速器常见毕业设计范围确定"),
                        param("输入功率", 4, "kW", "按中小功率机械传动工况建议"),
                        param("输入转速", 960, "r/min", "按常用异步电机转速确定"),
                        param("齿轮材料", "45钢调质", "", "按普通闭式齿轮传动选用")
                )
        ));
    }

    public DesignProject enhance(DesignProject project) {
        if (project == null) project = new DesignProject();
        if (blank(project.getDesignDepth())) project.setDesignDepth("graduation");
        RuleSet rule = selectRule(project);
        applyIdentity(project, rule);
        applyFunctions(project, rule);
        applyParameters(project, rule);
        applyStructures(project, rule);
        applyEngineeringLists(project, rule);
        applyTechnicalRequirements(project, rule);
        score(project);
        if (!standardSatisfied(project)) {
            project.getEnhancementNotes().add("结构细节分低于目标值，已按" + depthName(project) + "继续补充标准件、检修件、连接件和制造特征。");
            addUniversalDetails(project);
            applyEngineeringLists(project, rule);
            score(project);
        }
        validateStandard(project);
        return project;
    }

    private RuleSet selectRule(DesignProject project) {
        String signature = (safe(project.getProjectTitle()) + " " + safe(project.getEquipmentName()) + " "
                + safe(project.getDesignType()) + " " + String.join(" ", project.getMainStructures())).toLowerCase(Locale.ROOT);
        return RULES.values().stream()
                .filter(rule -> rule.keywords().stream().anyMatch(signature::contains))
                .findFirst()
                .orElse(genericRule());
    }

    private void applyIdentity(DesignProject project, RuleSet rule) {
        if (strongRuleMatch(project, rule)) {
            project.setEquipmentName(rule.equipmentName());
            project.setDesignType(rule.designType());
        }
        if (blank(project.getProjectTitle())) project.setProjectTitle(rule.equipmentName() + "设计");
        if (blank(project.getEquipmentName()) || project.getEquipmentName().contains("通用")) project.setEquipmentName(rule.equipmentName());
        if (blank(project.getDesignType()) || project.getDesignType().contains("通用")) project.setDesignType(rule.designType());
        if (blank(project.getProjectCategory())) project.setProjectCategory("机械类毕业设计");
        if (blank(project.getWorkingPrinciple())) {
            project.setWorkingPrinciple(rule.equipmentName() + "由" + joinNames(rule.components(), 8)
                    + "等部分组成，工作时通过主体结构、功能结构、支撑结构、连接结构和检修结构协同完成设计任务。");
        }
    }

    private void applyFunctions(DesignProject project, RuleSet rule) {
        removeIncompatibleStructures(project, rule);
        for (String function : rule.functions()) addUnique(project.getMainFunctions(), function);
        for (ComponentSpec component : rule.components()) addUnique(project.getMainStructures(), component.name());
    }

    private boolean strongRuleMatch(DesignProject project, RuleSet rule) {
        String currentTask = safe(project.getProjectTitle()) + " " + String.join(" ", project.getMainStructures()) + " "
                + String.join(" ", project.getMainFunctions());
        return rule.keywords().stream().anyMatch(currentTask::contains);
    }

    private void removeIncompatibleStructures(DesignProject project, RuleSet rule) {
        String equipment = rule.equipmentName();
        if (equipment.contains("爬壁机器人")) {
            project.getMainStructures().removeIf(item -> containsAny(item, "沉降", "排灰", "进风", "出风", "灰斗", "箱体", "沉降腔"));
            project.getMainFunctions().removeIf(item -> containsAny(item, "含尘", "颗粒物", "卸灰", "沉降"));
        } else if (equipment.contains("沉降室")) {
            project.getMainStructures().removeIf(item -> containsAny(item, "履带", "磁吸", "圆盘刷", "传感器", "滑轨", "清扫"));
            project.getMainFunctions().removeIf(item -> containsAny(item, "壁面", "爬行", "清扫", "缺陷检测", "磁吸"));
        }
    }

    private void applyParameters(DesignProject project, RuleSet rule) {
        addSuggested(project, "总长", 4200, "mm", "任务书未明确时按毕业设计方案级设备外形取值");
        addSuggested(project, "总宽", 1600, "mm", "任务书未明确时按维护通道和布置空间取值");
        addSuggested(project, "总高", 1800, "mm", "任务书未明确时按结构高度和检修空间取值");
        addSuggested(project, "设计载荷", 1200, "kg", "按中小型机械设备方案阶段承载估算");
        addSuggested(project, "安全系数", 1.8, "", "按本科毕业设计方案阶段强度校核取值");
        addSuggested(project, "材料", "Q235B", "", "按焊接结构常用材料取值");
        addSuggested(project, "连接方式", "焊接与螺栓连接", "", "兼顾制造、装配和检修维护");
        addSuggested(project, "制造方式", "板材下料、折弯、焊接和标准件装配", "", "按普通机械结构制造流程确定");
        for (ParameterSpec param : rule.parameters()) addSuggested(project, param.name(), param.value(), param.unit(), param.basis());
    }

    private void applyStructures(DesignProject project, RuleSet rule) {
        List<DesignProject.Component> components = new ArrayList<>(project.getComponents());
        int targetParts = targetParts(project);
        for (ComponentSpec spec : rule.components()) {
            if (components.stream().noneMatch(item -> spec.name().equals(item.getName()))) {
                components.add(toComponent(components.size() + 1, spec, project, components.size()));
            }
        }
        for (ComponentSpec spec : genericDetails()) {
            if (components.size() >= targetParts) break;
            if (components.stream().noneMatch(item -> spec.name().equals(item.getName()))) {
                components.add(toComponent(components.size() + 1, spec, project, components.size()));
            }
        }
        ensureMandatoryCategories(project, components);
        resequence(components);
        project.setComponents(components);
        project.setBom(components.stream().map(p -> new DesignProject.BomItem(
                p.getSequence(), p.getName(), p.getMaterial(), p.getQuantity(), p.getFunction())).toList());
    }

    private void ensureMandatoryCategories(DesignProject project, List<DesignProject.Component> components) {
        addIfMissingRole(project, components, "CONNECT",
                spec("CONNECT", "法兰连接件", "连接外部设备或可拆卸部件", "Q235B", 2, "FLANGE", false));
        addIfMissingRole(project, components, "STRUCTURE",
                spec("STRUCTURE", "加强筋", "提高薄板和支撑结构刚度", "Q235B", 6, "RIB", false));
        addIfMissingRole(project, components, "MAINTENANCE",
                spec("MAINTENANCE", "检修门", "提供检查和维护空间", "Q235B", 1, "DOOR", false));
        addIfMissingRole(project, components, "MOUNT",
                spec("MOUNT", "地脚螺栓", "固定设备与基础", "标准件", 8, "ANCHOR_BOLT", false));
        if (components.stream().map(DesignProject.Component::getGeometry)
                .noneMatch(g -> List.of("WELD", "FILLET", "SEAL_GROOVE").contains(g))) {
            components.add(toComponent(components.size() + 1,
                    spec("STRUCTURE", "焊缝标识", "标识关键焊接位置", "焊缝", 1, "WELD", false),
                    project, components.size()));
        }
    }

    private void addIfMissingRole(DesignProject project, List<DesignProject.Component> components,
                                  String role, ComponentSpec fallback) {
        if (components.stream().noneMatch(item -> role.equals(item.getRole()))) {
            components.add(toComponent(components.size() + 1, fallback, project, components.size()));
        }
    }

    private void applyTechnicalRequirements(DesignProject project, RuleSet rule) {
        addUnique(project.getVerificationItems(), "主要结构强度校核");
        addUnique(project.getVerificationItems(), "支撑结构稳定性校核");
        addUnique(project.getVerificationItems(), "接口连接与安装尺寸校核");
        addUnique(project.getTechnicalRequirements(), "图纸尺寸、论文参数、计算书结果和SolidWorks模型必须使用同一套设计数据。");
        addUnique(project.getTechnicalRequirements(), rule.equipmentName() + "的BOM零件必须能在总装图、零件图和论文结构图中找到对应编号。");
        addUnique(project.getTechnicalRequirements(), "焊接件完成后清理焊渣和毛刺，关键安装面校正后加工。");
        addUnique(project.getTechnicalRequirements(), "检修门、观察窗、安装孔、法兰、加强筋和支撑结构应在三视图中表达清楚。");
        addUnique(project.getTechnicalRequirements(), "图纸必须包含主视图、左视图、右视图、俯视图、仰视图中的至少三张视图，复杂设备补充剖视图、局部详图和装配图。");
        addUnique(project.getTechnicalRequirements(), "禁止以底板、立柱、横梁或单纯长方体圆柱体作为最终结构，缺少连接、加强、检修、安装和工艺结构时判定失败。");
    }

    private void addUniversalDetails(DesignProject project) {
        List<DesignProject.Component> components = new ArrayList<>(project.getComponents());
        for (ComponentSpec spec : genericDetails()) {
            if (components.stream().noneMatch(item -> spec.name().equals(item.getName()))) {
                components.add(toComponent(components.size() + 1, spec, project, components.size()));
            }
            if (scoreOf(components, project.allParameters(), project.getTechnicalRequirements()) >= targetScore(project)) break;
        }
        resequence(components);
        project.setComponents(components);
        project.setBom(components.stream().map(p -> new DesignProject.BomItem(
                p.getSequence(), p.getName(), p.getMaterial(), p.getQuantity(), p.getFunction())).toList());
    }

    private void score(DesignProject project) {
        int features = featureCount(project.getComponents(), project.allParameters(), project.getTechnicalRequirements());
        project.setPartCount(project.getComponents().stream().mapToInt(item -> Math.max(1, item.getQuantity())).sum());
        project.setFeatureCount(features);
        project.setDetailScore(scoreOf(project.getComponents(), project.allParameters(), project.getTechnicalRequirements()));
        project.getEnhancementNotes().removeIf(item -> item.startsWith("设计深化评分"));
        project.getEnhancementNotes().add("设计深化评分：partCount=" + project.getPartCount()
                + "，featureCount=" + project.getFeatureCount() + "，detailScore=" + project.getDetailScore()
                + "，设计深度=" + depthName(project));
    }

    private boolean standardSatisfied(DesignProject project) {
        return project.getDetailScore() >= targetScore(project)
                && project.getComponents().size() >= 15
                && project.getFeatureCount() >= 30
                && project.getDetailFeatures().size() >= 20
                && hasRole(project, "CONNECT")
                && hasRole(project, "STRUCTURE")
                && hasRole(project, "MAINTENANCE")
                && hasRole(project, "MOUNT")
                && hasAnyGeometry(project, "WELD", "FILLET", "SEAL_GROOVE")
                && project.getDrawingViews().size() >= 5;
    }

    private void validateStandard(DesignProject project) {
        List<String> reasons = new ArrayList<>();
        if (project.getDetailScore() < targetScore(project)) reasons.add("detailScore低于" + targetScore(project));
        if (project.getComponents().size() < 15) reasons.add("零件数量少于15");
        if (project.getFeatureCount() < 30) reasons.add("建模特征少于30");
        if (project.getDetailFeatures().size() < 20) reasons.add("工程细节少于20");
        if (!hasRole(project, "CONNECT")) reasons.add("缺少连接结构");
        if (!hasRole(project, "STRUCTURE")) reasons.add("缺少加强结构");
        if (!hasRole(project, "MAINTENANCE")) reasons.add("缺少检修结构");
        if (!hasRole(project, "MOUNT")) reasons.add("缺少安装结构");
        if (!hasAnyGeometry(project, "WELD", "FILLET", "SEAL_GROOVE")) reasons.add("缺少工艺结构");
        if (project.getDrawingViews().stream().filter(v -> v.contains("视图") || v.contains("图")).count() < 5) reasons.add("图纸视图不足");
        if (isPrimitiveOnly(project)) reasons.add("主体结构仍接近底板、立柱、横梁或简单几何堆叠");
        if (!reasons.isEmpty()) {
            throw new IllegalStateException("机械设计深化未达到DropAI Mechanical Design Standard v1.0，禁止生成CAD：" + String.join("；", reasons));
        }
    }

    private boolean hasRole(DesignProject project, String role) {
        return project.getComponents().stream().anyMatch(c -> role.equals(c.getRole()));
    }

    private boolean hasAnyGeometry(DesignProject project, String... geometries) {
        return project.getComponents().stream().map(DesignProject.Component::getGeometry)
                .anyMatch(g -> Stream.of(geometries).anyMatch(item -> item.equals(g)));
    }

    private boolean isPrimitiveOnly(DesignProject project) {
        long geometryKinds = project.getComponents().stream().map(DesignProject.Component::getGeometry).distinct().count();
        boolean hasOnlyBasicRoles = project.getComponents().stream().allMatch(c -> List.of("BODY", "SUPPORT", "BASE").contains(c.getRole()));
        return geometryKinds <= 3 && hasOnlyBasicRoles;
    }

    private int scoreOf(List<DesignProject.Component> components, List<DesignProject.Parameter> parameters, List<String> requirements) {
        int score = Math.min(35, components.size() * 2);
        score += Math.min(30, featureCount(components, parameters, requirements));
        score += Math.min(20, parameters.size() * 2);
        score += Math.min(15, requirements.size() * 3);
        return Math.min(100, score);
    }

    private int featureCount(List<DesignProject.Component> components, List<DesignProject.Parameter> parameters, List<String> requirements) {
        int count = (int) components.stream().map(DesignProject.Component::getGeometry).filter(v -> !blank(v)).distinct().count();
        count += (int) components.stream().map(DesignProject.Component::getRole).filter(v -> !blank(v)).distinct().count();
        count += Math.min(10, parameters.size());
        count += Math.min(6, requirements.size());
        return count;
    }

    private int targetScore(DesignProject project) {
        return switch (safe(project.getDesignDepth()).toLowerCase(Locale.ROOT)) {
            case "engineering", "工程版" -> 88;
            case "normal", "普通版" -> 45;
            default -> 80;
        };
    }

    private int targetParts(DesignProject project) {
        return switch (safe(project.getDesignDepth()).toLowerCase(Locale.ROOT)) {
            case "engineering", "工程版" -> 30;
            case "normal", "普通版" -> 15;
            default -> 18;
        };
    }

    private void applyEngineeringLists(DesignProject project, RuleSet rule) {
        project.setEquipmentType(rule.category());
        if (blank(project.getApplicationScenario())) {
            project.setApplicationScenario(rule.equipmentName().contains("沉降") ? "工业通风除尘" : rule.category());
        }
        for (ComponentSpec spec : rule.components()) addUnique(project.getDetailFeatures(), spec.name());
        for (ComponentSpec spec : genericDetails()) addUnique(project.getDetailFeatures(), spec.name());
        for (String item : List.of("Q235B钢板", "45钢轴类件", "标准法兰", "8.8级螺栓", "焊缝", "有机玻璃观察窗", "密封垫", "轴承座材料")) {
            addUnique(project.getMaterials(), item);
        }
        for (String item : List.of("法兰", "地脚螺栓", "排灰阀", "检修门铰链", "密封垫", "螺栓连接件")) {
            addUnique(project.getStandardParts(), item);
        }
        for (String item : List.of("三维装配图", "主视图", "左视图", "右视图", "俯视图", "仰视图", "A-A剖视图", "局部详图", "爆炸图", "装配图", "灰斗详图", "支座布置图", "管口法兰详图", "设备明细表", "材料汇总表")) {
            addUnique(project.getDrawingViews(), item);
        }
        for (String item : List.of("总长", "总宽", "总高", "进出口管径", "检修门尺寸", "灰斗角度", "支腿跨距", "法兰螺栓孔", "板厚", "焊缝高度")) {
            addUnique(project.getAnnotationList(), item);
        }
        for (String name : List.of("处理风量", "设计风速", "有效容积", "停留时间", "入口管径", "出口管径", "法兰规格", "壳体板厚", "支撑方式", "安装方式", "检修方式")) {
            if (project.allParameters().stream().noneMatch(p -> name.equals(p.getName()))) addUnique(project.getMissingParameters(), name);
        }
    }

    private String depthName(DesignProject project) {
        return switch (safe(project.getDesignDepth()).toLowerCase(Locale.ROOT)) {
            case "engineering", "工程版" -> "工程版";
            case "normal", "普通版" -> "普通版";
            default -> "毕业设计版";
        };
    }

    private DesignProject.Component toComponent(int sequence, ComponentSpec spec, DesignProject project, int index) {
        double totalL = number(project, 4200, "总长");
        double totalW = number(project, 1600, "总宽");
        double totalH = number(project, 1800, "总高");
        double col = index % 5;
        double row = index / 5.0;
        DesignProject.Component component = new DesignProject.Component(sequence, spec.role(), spec.name(), spec.function(),
                spec.material(), spec.quantity(), totalL * (0.08 + col * 0.15), totalW * (0.12 + (index % 3) * 0.18),
                totalH * (0.08 + Math.min(0.65, row * 0.12)), totalL * widthFactor(spec.geometry()),
                totalW * depthFactor(spec.geometry()), totalH * heightFactor(spec.geometry()), spec.keyPart());
        component.setGeometry(spec.geometry());
        return component;
    }

    private double widthFactor(String geometry) {
        if ("CHAMBER".equals(geometry)) return .55;
        if ("BELT".equals(geometry) || "TRUSS".equals(geometry)) return .65;
        if ("ARM_XZ".equals(geometry)) return .20;
        return .14;
    }

    private double depthFactor(String geometry) {
        if ("CHAMBER".equals(geometry) || "TRUSS".equals(geometry)) return .58;
        if ("BELT".equals(geometry)) return .48;
        if ("FRAME".equals(geometry)) return .55;
        return .22;
    }

    private double heightFactor(String geometry) {
        if ("CHAMBER".equals(geometry)) return .42;
        if ("HOPPER".equals(geometry)) return .26;
        if ("TRUSS".equals(geometry) || "FRAME".equals(geometry)) return .22;
        if ("ARM_XZ".equals(geometry)) return .14;
        return .16;
    }

    private double number(DesignProject project, double fallback, String... names) {
        for (String name : names) {
            double value = project.number(name, Double.NaN);
            if (!Double.isNaN(value)) return value;
        }
        return fallback;
    }

    private void addSuggested(DesignProject project, String name, Object value, String unit, String basis) {
        if (project.allParameters().stream().noneMatch(item -> name.equals(item.getName()))) {
            project.getSuggestedParameters().add(new DesignProject.Parameter(name, value, unit, null, basis));
        }
    }

    private void resequence(List<DesignProject.Component> components) {
        for (int i = 0; i < components.size(); i++) components.get(i).setSequence(i + 1);
    }

    private static void addUnique(List<String> list, String value) {
        if (!blank(value) && list.stream().noneMatch(value::equals)) list.add(value);
    }

    private static String joinNames(List<ComponentSpec> components, int limit) {
        return components.stream().limit(limit).map(ComponentSpec::name).reduce("", (a, b) -> a.isBlank() ? b : a + "、" + b);
    }

    private static RuleSet genericRule() {
        return new RuleSet(List.of(), "机械设备", "机械结构设计", "通用机械设备",
                List.of("完成指定机械工艺过程", "设备支撑安装", "检修维护", "接口连接"),
                List.of(
                        spec("BODY", "主体结构", "形成主要工作空间", "Q235B", 1, "CHAMBER", true),
                        spec("SUPPORT", "支撑结构", "承载设备并传递载荷", "Q235B", 1, "FRAME", true),
                        spec("INTERFACE", "入口组件", "连接上游设备", "Q235B", 1, "DUCT_X", true),
                        spec("INTERFACE", "出口组件", "连接下游设备", "Q235B", 1, "DUCT_X", true),
                        spec("MAINTENANCE", "检修组件", "提供检查和维护空间", "Q235B", 1, "DOOR", true)
                ),
                List.of(param("接口尺寸", "600×500", "mm", "按常规设备连接空间建议")));
    }

    private static List<ComponentSpec> genericDetails() {
        return List.of(
                spec("MOUNT", "安装孔", "用于设备与基础连接", "标准特征", 8, "HOLE", false),
                spec("STRUCTURE", "加强筋", "提高薄板和支撑结构刚度", "Q235B", 6, "RIB", false),
                spec("CONNECT", "螺栓连接件", "连接可拆卸部件", "8.8级螺栓", 12, "BOLT", false),
                spec("CONNECT", "焊接位置", "连接主体板件和支撑构件", "焊缝", 8, "WELD", false),
                spec("MOUNT", "定位销", "保证装配定位精度", "标准件", 2, "PIN", false),
                spec("SAFETY", "防护罩", "隔离运动或高温部位", "Q235B", 1, "COVER", false),
                spec("MOUNT", "地脚板", "扩大支撑接触面积", "Q235B", 4, "BASE_PLATE", false),
                spec("STRUCTURE", "倒角圆角", "降低应力集中并改善制造质量", "结构特征", 1, "FILLET", false),
                spec("CONNECT", "法兰", "连接外部管路或相邻设备", "Q235B", 2, "FLANGE", false),
                spec("CONNECT", "螺栓孔", "提供标准件连接位置", "标准特征", 12, "HOLE", false),
                spec("CONNECT", "键槽", "传递轴类零件扭矩", "标准特征", 1, "KEYWAY", false),
                spec("MAINTENANCE", "观察窗", "观察内部运行状态", "有机玻璃", 1, "WINDOW", false),
                spec("MAINTENANCE", "检修孔", "提供内部检修入口", "Q235B", 1, "MANHOLE", false),
                spec("STRUCTURE", "加强板", "提高局部连接区域刚度", "Q235B", 4, "GUSSET", false),
                spec("STRUCTURE", "支撑肋", "提高支撑和大跨板件稳定性", "Q235B", 4, "RIB", false),
                spec("MOUNT", "底座", "形成设备安装基准", "Q235B", 1, "BASE_PLATE", false),
                spec("MOUNT", "支腿", "将整机载荷传递至基础", "Q235B", 4, "FRAME", false),
                spec("MOUNT", "地脚螺栓", "固定设备与基础", "标准件", 8, "ANCHOR_BOLT", false),
                spec("STRUCTURE", "密封结构", "保证接口或箱体密封", "密封垫", 1, "SEAL_GROOVE", false)
        );
    }

    private static ComponentSpec spec(String role, String name, String function, String material, int quantity, String geometry, boolean keyPart) {
        return new ComponentSpec(role, name, function, material, quantity, geometry, keyPart);
    }

    private static ParameterSpec param(String name, Object value, String unit, String basis) {
        return new ParameterSpec(name, value, unit, basis);
    }

    private static boolean blank(String value) { return value == null || value.isBlank(); }
    private static String safe(String value) { return value == null ? "" : value; }
    private static boolean containsAny(String value, String... words) {
        if (value == null) return false;
        for (String word : words) if (value.contains(word)) return true;
        return false;
    }

    private record RuleSet(List<String> keywords, String equipmentName, String designType, String category,
                           List<String> functions, List<ComponentSpec> components, List<ParameterSpec> parameters) {}
    private record ComponentSpec(String role, String name, String function, String material, int quantity, String geometry, boolean keyPart) {}
    private record ParameterSpec(String name, Object value, String unit, String basis) {}
}
