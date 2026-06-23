package com.dropai.rewrite.modules.requirementCompleter;

import com.dropai.rewrite.modules.model.DesignProject;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Locale;

@Service
public class RequirementCompleter {
    private static final String SUGGESTION_MARK = "方案级建议，待用户确认。";
    private static final String UI_NOTICE = "任务书部分参数未明确，系统已生成方案级建议值，可在下一步修改确认。";

    public DesignProject complete(DesignProject project) {
        if (project == null) project = new DesignProject();
        if (blank(project.getProjectTitle())) {
            throw new IllegalStateException("缺少题目：请上传任务书或手动填写毕业设计题目");
        }
        Profile profile = inferProfile(project);
        if (profile == null) {
            throw new IllegalStateException("无法判断设备类型：请在题目或任务书中补充设备名称、用途或主要机构");
        }

        if (blank(project.getEquipmentName())) project.setEquipmentName(profile.equipmentName());
        if (blank(project.getDesignType())) project.setDesignType(profile.designType());
        if (blank(project.getProjectCategory())) project.setProjectCategory("机械类毕业设计");
        if (blank(project.getEquipmentType())) project.setEquipmentType(profile.type());
        if (blank(project.getApplicationScenario())) project.setApplicationScenario(profile.scenario());

        for (String item : profile.functions()) addUnique(project.getMainFunctions(), item);
        for (String item : profile.structures()) addUnique(project.getMainStructures(), item);
        for (String item : profile.drawings()) addUnique(project.getDrawingViews(), item);
        for (String item : profile.technicalRequirements()) addUnique(project.getTechnicalRequirements(), item);
        for (SuggestedParameter item : profile.parameters()) {
            addSuggested(project, item.name(), item.value(), item.unit(), profile.reason());
        }

        addUnique(project.getVerificationItems(), UI_NOTICE);
        addUnique(project.getEnhancementNotes(), "RequirementCompleter已按设备类型、工程常识和毕业设计要求补全缺失项，所有补全内容均标记为系统建议。");
        if (blank(project.getWorkingPrinciple())) project.setWorkingPrinciple(profile.workingPrinciple());
        return project;
    }

    private Profile inferProfile(DesignProject project) {
        String text = normalize(project.getProjectTitle() + " " + project.getEquipmentName() + " "
                + project.getDesignType() + " " + String.join(" ", project.getMainFunctions()) + " "
                + String.join(" ", project.getMainStructures()));
        if (hasAny(text, "重力沉降", "沉降室", "除尘")) return settlingChamber();
        if (hasAny(text, "输送机", "输送带", "滚筒", "conveyor")) return conveyor();
        if (hasAny(text, "机械手", "机械臂", "夹爪", "关节")) return manipulator();
        if (hasAny(text, "爬壁", "履带", "油罐", "吸附", "清扫机器人")) return crawlerRobot(project);
        String titleName = titleAsEquipmentName(project.getProjectTitle());
        if (blank(titleName) || titleName.length() < 3) return null;
        return titledMechanicalProject(titleName);
    }

    private Profile settlingChamber() {
        String reason = "任务书未明确，系统按重力沉降室工程设计经验补全，需用户确认。";
        return new Profile(
                "settling_chamber",
                "工业通风除尘用重力沉降室",
                "环保设备结构设计",
                "工业通风除尘",
                "卧式矩形箱体水平流动结构，含尘气流经进气扩散、导流减速、重力沉降和灰斗收集后由出气口排出。",
                List.of("含尘气流沉降", "颗粒物收集", "进出口连接", "设备检修维护"),
                List.of("进气管", "扩散段", "导流板", "沉降室箱体", "灰斗", "卸灰口", "出气管", "支撑框架", "检修门", "观察孔", "加强筋"),
                List.of(
                        p("总长", 4200, "mm"), p("总宽", 1600, "mm"), p("总高", 1800, "mm"),
                        p("箱体板厚", 4, "mm"), p("灰斗角度", 55, "°"), p("进出口尺寸", "600×500", "mm"),
                        p("设计风量", "3000~6000", "m³/h"), p("气流速度", "0.5~1.0", "m/s"), p("停留时间", "3~5", "s")),
                List.of("总体结构图", "壳体结构图", "进出口接口图", "排灰斗结构图", "检修门结构图", "支撑架结构图"),
                List.of("箱体采用焊接结构，焊缝连续并满足气密性要求。", "灰斗角度按防积灰要求设置，卸灰口预留密封连接。", "检修门、观察孔和加强筋位置需在详细设计阶段复核。"),
                reason);
    }

    private Profile conveyor() {
        String reason = "任务书未明确，系统按带式输送机毕业设计常用方案补全，需用户确认。";
        return new Profile(
                "conveyor",
                "带式输送机",
                "输送设备设计",
                "物料连续输送",
                "物料由输送带承载，主动滚筒经电机、减速器和联轴器驱动，托辊和机架提供支撑与张紧。",
                List.of("连续输送", "滚筒驱动", "张紧调偏", "机架支撑"),
                List.of("输送带", "主动滚筒", "从动滚筒", "托辊", "张紧装置", "机架", "驱动电机", "减速器", "联轴器", "轴承座"),
                List.of(p("输送长度", 5000, "mm"), p("带宽", 500, "mm"), p("输送速度", 1.0, "m/s"), p("滚筒直径", 240, "mm"), p("电机功率", 2.2, "kW")),
                List.of("总体结构图", "输送带机构图", "滚筒机构图", "机架结构图", "驱动装置图"),
                List.of("滚筒轴线平行度和张紧行程需满足输送带调偏要求。", "机架焊接后进行校正，安装孔按装配基准统一定位。"),
                reason);
    }

    private Profile manipulator() {
        String reason = "任务书未明确，系统按机械手机构毕业设计常用方案补全，需用户确认。";
        return new Profile(
                "manipulator",
                "机械手",
                "机电一体化设计",
                "自动抓取与搬运",
                "底座提供安装基准，关节驱动大臂和小臂运动，末端夹爪完成抓取，限位与连接件保证运动范围和装配可靠性。",
                List.of("回转定位", "关节摆动", "末端夹持", "限位保护"),
                List.of("底座", "回转关节", "大臂", "小臂", "夹爪", "关节伺服驱动", "连杆", "限位块", "安装孔阵列"),
                List.of(p("臂展", 650, "mm"), p("额定负载", 3, "kg"), p("底座直径", 220, "mm"), p("关节转角", "0~180", "°"), p("夹爪开口", 80, "mm")),
                List.of("总体结构图", "底座结构图", "大臂结构图", "小臂结构图", "夹爪结构图", "关节驱动结构图"),
                List.of("关节轴承、连接螺栓和限位结构需按负载工况复核。", "夹爪开口和驱动行程需根据目标工件尺寸确认。"),
                reason);
    }

    private Profile crawlerRobot(DesignProject project) {
        String reason = "任务书未明确，系统按爬壁机器人结构设计经验补全，需用户确认。";
        String name = blank(project.getEquipmentName()) ? titleAsEquipmentName(project.getProjectTitle()) : project.getEquipmentName();
        if (blank(name)) name = "爬壁机器人";
        return new Profile(
                "crawler_robot",
                name,
                "机器人结构设计 / 机电一体化设计",
                "壁面检测与清扫",
                "整机以机架为承载主体，履带机构完成爬行，吸附组件保证附着，清扫刷盘和检测架完成作业模块布置。",
                List.of("壁面爬行", "吸附稳定", "表面清扫", "检测模块安装", "模块化维护"),
                List.of("机架", "履带机构", "驱动轮", "从动轮", "支重轮", "履带板", "轮轴", "安装板", "吸盘组件", "圆盘刷", "刷盘", "清扫电机", "清扫刷盘", "检测架", "控制箱", "驱动轴", "电机", "减速器", "联轴器", "键槽", "外壳"),
                List.of(p("总长", 800, "mm"), p("总宽", 600, "mm"), p("总高", 300, "mm"), p("吸附力", 200, "N"), p("履带宽度", 80, "mm"), p("清扫盘直径", 180, "mm")),
                List.of("总体结构图", "履带机构图", "清扫机构图", "机架结构图", "驱动机构图"),
                List.of("履带、驱动轮和吸附组件需满足壁面附着安全系数。", "清扫机构和检测架应预留维护拆装空间。"),
                reason);
    }

    private Profile titledMechanicalProject(String equipmentName) {
        String reason = "任务书未明确，系统按题目设备名称和机械毕业设计通用工程要求补全，需用户确认。";
        return new Profile(
                "title_inferred_mechanical",
                equipmentName,
                "机械结构设计",
                "按题目推断的机械结构方案",
                equipmentName + "按承载、连接、传动或功能执行机构进行方案级结构组织，后续需由用户确认具体工况。",
                List.of("功能执行", "结构承载", "装配连接", "检修维护"),
                List.of(equipmentName + "主体", "支撑结构", "功能执行机构", "驱动或传动机构", "连接结构", "安装孔", "检修结构", "防护结构"),
                List.of(p("总长", 1200, "mm"), p("总宽", 800, "mm"), p("总高", 900, "mm"), p("安全系数", 1.8, ""), p("材料", "Q235B", "")),
                List.of("总体结构图", "主体结构图", "驱动装置图", "支撑结构图"),
                List.of("补全结构为题目推断的方案级初值，生成后需按任务书和指导教师意见确认。"),
                reason);
    }

    private void addSuggested(DesignProject project, String name, Object value, String unit, String reason) {
        if (project.allParameters().stream().noneMatch(item -> name.equals(item.getName()))) {
            project.getSuggestedParameters().add(new DesignProject.Parameter(name, value, unit, "系统建议", reason + SUGGESTION_MARK));
            addUnique(project.getMissingParameters(), name);
        }
    }

    private void addUnique(List<String> list, String value) {
        if (!blank(value) && list.stream().noneMatch(item -> item != null && item.equals(value))) list.add(value);
    }

    private SuggestedParameter p(String name, Object value, String unit) {
        return new SuggestedParameter(name, value, unit);
    }

    private String titleAsEquipmentName(String title) {
        if (blank(title)) return "";
        return title.replace("毕业设计", "").replace("课程设计", "").replace("结构设计", "")
                .replace("设计", "").replace("研究", "").replace("及", "").trim();
    }

    private boolean hasAny(String text, String... words) {
        for (String word : words) if (text.contains(normalize(word))) return true;
        return false;
    }

    private String normalize(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT).replaceAll("\\s+", "");
    }

    private boolean blank(String value) {
        return value == null || value.isBlank();
    }

    private record SuggestedParameter(String name, Object value, String unit) {}

    private record Profile(String type, String equipmentName, String designType, String scenario,
                           String workingPrinciple, List<String> functions, List<String> structures,
                           List<SuggestedParameter> parameters, List<String> drawings,
                           List<String> technicalRequirements, String reason) {}
}
