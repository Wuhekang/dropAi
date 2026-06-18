package com.dropai.rewrite.modules.paperEngine;

import com.dropai.rewrite.modules.model.DesignProject;
import org.apache.poi.xwpf.usermodel.ParagraphAlignment;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableCell;
import org.apache.poi.xwpf.usermodel.XWPFTableRow;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTTblWidth;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.STTblWidth;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class PaperEngine {
    private static final String DOCX_ERROR = "论文DOCX生成失败：";
    private final ContentSanitizer sanitizer;
    private final DocQualityChecker qualityChecker;

    public PaperEngine() {
        this(new ContentSanitizer());
    }

    public PaperEngine(ContentSanitizer sanitizer) {
        this.sanitizer = sanitizer;
        this.qualityChecker = new DocQualityChecker(sanitizer);
    }

    public byte[] generatePaper(DesignProject project) {
        try (XWPFDocument doc = new XWPFDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            DesignProject safeProject = project == null ? new DesignProject() : project;
            String title = safeText(safeProject.getProjectTitle(), "机械类本科毕业设计");
            String equipment = safeText(safeProject.getEquipmentName(), title.replace("结构设计", "").replace("设计", ""));

            title(doc, title + "设计说明书");
            buildFullPaper(doc, safeProject, title, equipment);
            String text = collectText(doc);
            DocQualityChecker.QualityReport report = qualityChecker.checkPaper(text);
            if (!report.passed()) {
                throw new IllegalStateException(String.join("；", report.errors()));
            }
            doc.write(out);
            return out.toByteArray();
        } catch (Exception e) {
            throw new IllegalStateException(DOCX_ERROR + e.getMessage(), e);
        }
    }

    public byte[] generateCalculationBook(DesignProject project) {
        try (XWPFDocument doc = new XWPFDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            DesignProject safeProject = project == null ? new DesignProject() : project;
            title(doc, safeText(safeProject.getProjectTitle(), "机械类本科毕业设计") + "设计计算书");
            heading(doc, "一、计算参数", 1);
            parameterTable(doc, safeProject);
            heading(doc, "二、主要计算过程", 1);
            List<DesignProject.Calculation> calculations = safeProject.getCalculations();
            if (calculations.isEmpty()) {
                paragraph(doc, "当前项目尚未形成完整计算结果，计算书应补充功率、转矩、受力、强度、稳定性和连接校核后再作为定稿使用。");
            } else {
                for (DesignProject.Calculation c : calculations) {
                    paragraph(doc, safeText(c.getName(), "设计计算") + "：" + safeText(c.getFormula(), "公式待校核") +
                            "；代入：" + safeText(c.getSubstitution(), "参数待校核") +
                            "；结果：" + c.getResult() + safeText(c.getUnit(), "") +
                            "；结论：" + safeText(c.getConclusion(), "满足初步设计要求"));
                }
            }
            doc.write(out);
            return out.toByteArray();
        } catch (Exception e) {
            throw new IllegalStateException("设计计算书生成失败：" + e.getMessage(), e);
        }
    }

    public byte[] generateModelingSteps(DesignProject project) {
        try (XWPFDocument doc = new XWPFDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            DesignProject safeProject = project == null ? new DesignProject() : project;
            title(doc, safeText(safeProject.getProjectTitle(), "机械类本科毕业设计") + " SolidWorks建模步骤说明");
            heading(doc, "一、建模前准备", 1);
            paragraph(doc, "根据设计参数表建立全局变量，重点确认整机长宽高、关键机构尺寸、安装孔距、标准件型号、材料和板厚。本阶段输出VBA宏和建模步骤说明，不直接生成SLDPRT或SLDASM文件。");
            heading(doc, "二、零件建模顺序", 1);
            int i = 1;
            for (DesignProject.Component c : safeProject.getComponents().stream().limit(12).toList()) {
                paragraph(doc, i++ + ". " + safeText(c.getName(), "零件") + "：按结构树和装配约束建立草图、拉伸、孔位、倒角和材料，保存为独立零件后进入装配体定位。");
            }
            heading(doc, "三、装配约束", 1);
            for (DesignProject.AssemblyConstraint c : safeProject.getAssemblyConstraints().stream().limit(12).toList()) {
                paragraph(doc, safeText(c.getPartName(), "零件") + "装配到" + safeText(c.getMountTo(), "基准件") +
                        "，约束类型为" + safeText(c.getConstraintType(), "fixed") +
                        "，安装面为" + safeText(c.getMountingFace(), "待校核安装面") + "。");
            }
            doc.write(out);
            return out.toByteArray();
        } catch (Exception e) {
            throw new IllegalStateException("SolidWorks建模步骤生成失败：" + e.getMessage(), e);
        }
    }

    private void buildFullPaper(XWPFDocument doc, DesignProject project, String title, String equipment) {
        heading(doc, "摘要", 1);
        paragraph(doc, title + "以" + equipment + "为设计对象，围绕任务书提出的功能、结构、参数和成果要求，建立从项目识别、结构树生成、标准件选择、非标件设计、装配约束、计算校核、CAD三视图到设计说明书输出的完整流程。设计过程中先把文字任务转化为工程结构，再把结构转化为统一数据源，使论文参数、计算结果、BOM、CAD尺寸和建模步骤能够保持一致。");
        paragraph(doc, "系统生成成果时不把任务书直接变成简单几何体，而是先识别设备组成、工作原理和关键机构，再进行参数补全和工程表达。标准件若处于模拟推荐状态，在BOM和说明书中明确标注未联网校验；非标件则根据结构功能生成材料、板厚、安装孔、加工方式和连接方式。");
        paragraph(doc, "关键词：" + equipment + "；机械结构设计；设计计算；CAD工程图；SolidWorks建模");
        heading(doc, "Abstract", 1);
        paragraph(doc, "This thesis develops an undergraduate mechanical design scheme for " + equipment + ". The workflow links project analysis, structure tree generation, standard part selection, non-standard part design, assembly constraints, design calculation, CAD drawing and documentation. The shared design data reduce inconsistency among parameters, BOM, drawings and modeling steps.");
        paragraph(doc, "Keywords: mechanical design; assembly constraint; CAD drawing; SolidWorks; design calculation");

        chapter1(doc, project, equipment);
        chapter2(doc, project, equipment);
        chapter3(doc, project, equipment);
        chapter4(doc, project, equipment);
        chapter5(doc, project, equipment);
        chapter6(doc, project, equipment);
        references(doc);
        acknowledgements(doc);
    }

    private void chapter1(XWPFDocument doc, DesignProject project, String equipment) {
        heading(doc, "第1章 绪论", 1);
        section(doc, "1.1 设计背景", List.of(
                equipment + "属于机械类本科毕业设计中典型的结构设计课题，设计成果需要同时体现设备功能、机构组成、关键尺寸、材料选择和工程图表达。对于任务书文字较少的课题，设计工作的关键不是直接绘制外形，而是把题目中隐含的工作对象、运动关系、安装方式和维护要求转化为可计算、可出图、可建模的工程数据。",
                "本科毕业设计说明书应具有较完整的工程逻辑。题目识别后，需要说明设备服务场景、功能边界和主要矛盾，再说明结构方案为什么这样布置。只有在结构树、参数表和计算项目明确之后，CAD图纸和SolidWorks建模才具备依据。",
                "本课题采用任务书驱动的生成方式，先提取设备名称、项目类型、主要功能和技术指标，再进行结构树归并、标准件判断、非标件特征生成和装配树构建。该流程能够降低不同课题生成结果相似的问题，也能避免上一个项目结构污染新项目。"
        ));
        section(doc, "1.2 国内外研究现状", List.of(
                "机械设计领域已经形成了以参数化设计、模块化装配和标准件库调用为基础的工程方法。成熟设计资料通常同时包含总体结构图、关键零件图、材料表、标准件明细、计算校核和技术要求，而不是只展示设备外观。毕业设计写作也要求把计算过程、选型依据和图纸表达放在同一设计链路中。",
                "CAD和SolidWorks在机械类毕业设计中承担不同任务。CAD主要用于二维工程图表达，强调视图、尺寸、明细表和技术要求；SolidWorks主要用于三维结构理解、装配关系检查和建模步骤说明。二者应由同一套参数驱动，避免图纸尺寸与说明书文字不一致。",
                "现有自动生成系统常见问题是把任务书直接转为几何图形，导致模型只有方块、圆柱和简单轮廓，缺少标准件、安装孔、连接结构和计算依据。本设计把结构树和装配约束作为中间层，用工程语义约束后续图纸和论文内容。"
        ));
        section(doc, "1.3 设计目标", List.of(
                "本设计目标是形成一套符合本科毕业设计要求的初稿成果，包括完整论文正文、主要参数表、设计计算书、CAD总装三视图、主要零件图、BOM明细表和SolidWorks辅助建模步骤。论文内容应围绕结构设计与计算展开，避免写成产品介绍。",
                "论文第三章作为计算重点，需要覆盖功率、转速、扭矩、受力、弯矩、强度、安全系数、轴承或连接件校核等内容。对于尚未由任务书给出的参数，系统应标记来源和待校核状态，不把估算包络尺寸冒充正式设计尺寸。",
                "CAD成果的目标是表达清晰的本科毕业设计总装三视图。主视图、俯视图和侧视图应分别表达设备外形、布局关系、支撑或安装关系，BOM中的序号必须能在图纸中找到对应结构。"
        ));
        section(doc, "1.4 主要研究内容", List.of(
                "研究内容包括任务书解析、项目识别、结构树生成、标准件与非标件区分、装配约束生成、参数表建立、计算项目生成、工程图纸规划和说明书写作。所有成果共享同一项目数据源，保证名称、尺寸、材料和数量一致。",
                "结构树用于描述整机的功能分解，标准件选择用于确定电机、减速器、轴承、导轨、螺栓等通用零部件，非标件生成用于确定机架、外壳、安装板、检测支架和功能模块支座等自制件。装配树再把这些零件按基准件、安装面、接触面、轴线和偏置距离组织起来。",
                "图纸部分以DrawingPlan为输入，不直接使用三维模型截图投影。尺寸来源限定为任务书明确参数、设计计算结果、标准件尺寸、装配约束距离和用户确认参数。论文部分通过内容清洗和质量检查去除生成提示词、占位符、乱码和重复段落。"
        ));
        figure(doc, "图1-1 任务书驱动的机械设计生成流程图", "图中应包含任务解析、结构树、标准件选择、非标件生成、装配树、计算、CAD和DOCX输出之间的数据关系。");
    }

    private void chapter2(XWPFDocument doc, DesignProject project, String equipment) {
        heading(doc, "第2章 总体方案设计", 1);
        section(doc, "2.1 设计要求分析", List.of(
                equipment + "的设计要求来自任务书、开题报告、参考图和用户确认参数。当前识别的主要功能包括：" + join(project.getMainFunctions(), "待补充主要功能") + "。这些功能决定了整机必须具备承载、运动或执行、检测或工作模块安装、连接固定和维护拆装等结构。",
                "当前识别的主要结构包括：" + join(project.getMainStructures(), "待补充主要结构") + "。结构清单用于生成BOM和CAD标注，不允许把与任务无关的结构带入新项目。若识别结果缺少题目、设备名称或结构信息，应要求用户补充，而不是生成默认通用设备。",
                "设计要求还包括输出成果的一致性。参数表中的总尺寸、工作能力、材料和板厚应在计算书中被引用，在CAD图纸中被标注，在SolidWorks建模步骤中作为全局变量使用。"
        ));
        parameterTable(doc, project);
        section(doc, "2.2 总体结构方案", List.of(
                "总体结构采用模块化设计思想，将整机划分为基准承载模块、功能执行模块、传动或驱动模块、标准连接模块、检测或辅助模块以及防护维护模块。基准承载模块提供安装平面和装配坐标系，其他模块围绕基准件布置。",
                "结构方案不以设备名称硬套模板，而以任务书中的功能词、结构词和技术指标为依据。对明确出现的机构设为必选节点，对由工程规则补全的机构设为建议节点，并保留来源和置信度。结构树经过归一化后控制在若干核心节点，避免重复机构堆叠。",
                "方案设计时优先使用标准件满足传动、支撑、导向和连接需求；标准件无法确定或不适合时，再生成非标安装件。这样既能提高设计可信度，也能让BOM、CAD和建模步骤具有清晰来源。"
        ));
        section(doc, "2.3 工作原理", List.of(
                safeText(project.getWorkingPrinciple(), equipment + "工作时由驱动或执行机构提供运动和功能输出，机架或主体结构承担载荷并提供安装基准，检测、清扫、输送、吸附或夹持等功能模块根据任务要求完成相应工作。标准件负责动力传递、转动支承、导向定位和紧固连接，非标件负责结构承载、安装调节和防护维护。"),
                "各模块之间通过装配约束形成确定关系。基准件固定在整机坐标系中，左右或前后对称机构通过对称面约束布置，轮轴类零件通过同轴约束定位，安装板和支架通过接触面、孔阵列和偏置距离确定位置。",
                "该工作原理在后续CAD图纸中以主视图、俯视图和侧视图表达。主视图强调外形与关键机构，俯视图强调布局与安装位置，侧视图强调高度、支撑和功能模块相对位置。"
        ));
        section(doc, "2.4 主要技术参数", List.of(
                "主要技术参数按来源分为任务书明确参数、设计推导参数和建议参数。明确参数直接进入正式尺寸链；推导参数需要给出计算依据；建议参数用于初步设计，并在图纸或说明书中标注为待校核。",
                "参数表不仅服务论文，也服务CAD和SolidWorks建模。总长、总宽、总高、关键接口尺寸、安装孔距、支撑高度、轮径、板厚和材料等数据必须在不同成果中保持一致。",
                "当任务书没有给出足够指标时，系统可以根据本科毕业设计常用工程规则补全基础参数，但补全值必须保留依据，后续由用户或指导教师确认。"
        ));
        figure(doc, "图2-1 总体结构方案图", "图中应以黑白线条标注主体结构、功能机构、传动或驱动部件、检测部件、支撑结构和维护结构，序号与BOM一致。");
        figure(doc, "图2-2 工作原理示意图", "图中应使用箭头表示动力、运动、物料、气流或检测信号的传递路径。");
    }

    private void chapter3(XWPFDocument doc, DesignProject project, String equipment) {
        heading(doc, "第3章 结构设计与计算", 1);
        section(doc, "3.1 关键参数确定", List.of(
                "结构设计与计算是全文重点。本章计算对象包括整机载荷、驱动功率、运动速度、输出转矩、支撑反力、关键截面弯矩、材料强度、连接件承载能力和安全系数。计算结果直接影响CAD尺寸、标准件型号和非标件板厚。",
                "计算参数优先来自任务书明确值，其次来自用户确认参数、设计推导值和标准件参数。缺少来源的参数不能作为正式设计尺寸，只能用于初步估算并标记待校核。",
                "关键参数确定时，应把总尺寸、工作载荷、运行速度、摩擦阻力、传动效率、材料许用应力和安全系数列入表格，便于后续公式代入。"
        ));
        table(doc, "表3-1 主要计算参数表", List.of("参数", "数值", "单位", "来源"), project.allParameters().stream().limit(10)
                .map(p -> List.of(safeText(p.getName(), "参数"), String.valueOf(p.getValue()), safeText(p.getUnit(), ""), safeText(firstNonBlank(p.getSource(), p.getBasis()), "待校核"))).toList());
        formula(doc, "P = Fv / η", "（3-1）");
        paragraph(doc, "式中：P为驱动功率，F为工作阻力或驱动力，v为运行速度，η为传动总效率。该式用于判断电机功率是否满足整机工作要求。");
        formula(doc, "T = 9550P / n", "（3-2）");
        paragraph(doc, "式中：T为输出转矩，P为功率，n为转速。该式用于电机、减速器、联轴器和轴类零件的初步选型。");
        formula(doc, "Mmax = Fr l / 4", "（3-3）");
        paragraph(doc, "式中：Mmax为最大弯矩，Fr为径向载荷，l为支承间距。对简化为两端支承的轴或梁，可用该式进行初步弯曲强度校核。");
        formula(doc, "σ = M / W", "（3-4）");
        paragraph(doc, "式中：σ为弯曲应力，M为弯矩，W为截面系数。计算应力小于材料许用应力时，结构强度满足初步设计要求。");
        formula(doc, "S = [σ] / σ", "（3-5）");
        paragraph(doc, "式中：S为安全系数，[σ]为许用应力，σ为计算应力。毕业设计中应结合冲击、疲劳和制造误差取合理安全系数。");
        formula(doc, "d ≥ ∛(16T / π[τ])", "（3-6）");
        paragraph(doc, "式中：d为轴径，T为传递转矩，[τ]为许用切应力。该式用于传动轴、刷盘轴或轮轴的初步直径确定。");
        formula(doc, "Pe = XFr + YFa", "（3-7）");
        paragraph(doc, "式中：Pe为轴承当量动载荷，Fr为径向载荷，Fa为轴向载荷，X和Y为载荷系数。该式用于轴承寿命计算。");
        formula(doc, "Lh = (C / Pe)^3 × 10^6 / (60n)", "（3-8）");
        paragraph(doc, "式中：Lh为轴承基本额定寿命，C为基本额定动载荷，n为转速。若寿命低于设计要求，应提高轴承型号或降低载荷。");
        formula(doc, "τb = Fb / Ab", "（3-9）");
        paragraph(doc, "式中：τb为螺栓或销轴剪应力，Fb为连接件承受剪力，Ab为受剪面积。连接件校核需要考虑数量、布置和预紧要求。");
        formula(doc, "p = F / A", "（3-10）");
        paragraph(doc, "式中：p为接触压强，F为法向载荷，A为有效接触面积。该式用于安装面、支承面、磁吸附块或滚轮接触区域的校核。");

        section(doc, "3.2 主体结构计算", engineeringParagraphs(equipment, "主体结构", List.of(
                "主体结构承担整机重量、工作载荷和外部扰动，是确定整机刚度和安装稳定性的基础。计算时可把机架或箱体简化为梁板组合结构，先确定支承位置，再计算关键截面的弯矩和剪力。",
                "若主体结构采用板焊或型材连接，应根据最大弯矩确定截面模量，根据局部安装载荷校核孔边强度。板厚、加强筋高度和横梁间距应由受力结果反推，而不是只由外观比例确定。",
                "主体结构的CAD尺寸链应包含总长、总宽、总高、安装基准面、关键孔距和板厚。计算中得到的危险截面应在图纸或说明书插图中标出。"
        )));
        section(doc, "3.3 受力分析", engineeringParagraphs(equipment, "受力系统", List.of(
                "整机受力包括自重、工作阻力、惯性力、支撑反力、连接反力和外部冲击。运动设备还需要考虑启动和制动阶段的附加载荷，固定设备需要考虑基础连接和振动影响。",
                "受力分析应从装配树出发，把每个功能模块的载荷传递到基准件。轮系、轴承、导轨、支架和安装板之间的载荷路径需要清楚说明，便于后续强度校核和零件图尺寸确定。",
                "论文中应配置黑白受力分析图，图中用箭头标出重力、驱动力、阻力、支反力和危险截面。该图可作为第三章计算和第五章工程图说明之间的桥梁。"
        )));
        section(doc, "3.4 强度校核", engineeringParagraphs(equipment, "强度校核", List.of(
                "强度校核应覆盖关键承载件、传动轴、安装板、支架、螺栓连接和焊缝位置。对非标件应说明材料许用应力，对标准件应说明型号参数来源和校核边界。",
                "当计算应力接近许用应力时，可通过增加板厚、加设加强筋、增大圆角、改变孔距或提高标准件规格来优化。优化后的尺寸必须同步进入CAD图纸和BOM说明。",
                "对于螺栓连接，除剪切和拉伸强度外，还应检查孔边距、安装空间和维护拆装方向。对于轴类零件，应同时考虑弯扭组合应力和键槽削弱影响。"
        )));
        section(doc, "3.5 稳定性校核", engineeringParagraphs(equipment, "稳定性校核", List.of(
                "稳定性校核用于判断设备在运行、安装或外部扰动下是否发生倾覆、滑移、局部失稳或接触不可靠。支撑点布置、重心位置、吸附力或基础连接力是主要影响因素。",
                "对移动或爬行类设备，需要校核驱动力、附着力、接触稳定性和抗倾覆能力；对固定式设备，需要校核支腿、底座、地脚螺栓和焊接支承结构。",
                "稳定性计算结论应反馈到支撑尺寸、安装孔距、轮距、支腿间距或吸附模块布置。若安全系数不足，应调整结构布置而不是只提高材料强度。"
        )));
        section(doc, "3.6 连接件与标准件校核", engineeringParagraphs(equipment, "连接件", List.of(
                "标准件选型不能只列型号，还要说明其承载能力、安装尺寸和与非标件的配合关系。电机、减速器、轴承、导轨、联轴器和螺栓应按功能分组校核。",
                "螺栓孔阵列应与安装板厚度和受力方向匹配。轴承座、导轨滑块和电机法兰应保证定位基准明确，避免装配时产生过约束或无法维护。",
                "若当前标准件数据来自模拟推荐，应在论文和BOM中保留未联网校验说明，后续定稿时根据真实样本或公开平台参数复核。"
        )));
        table(doc, "表3-2 设计计算结果汇总表", List.of("计算项目", "结果", "单位", "结论"), calculationRows(project));
        figure(doc, "图3-1 整机受力分析图", "应包含自重、工作阻力、支撑反力、驱动力、危险截面和连接件受力方向。");
        figure(doc, "图3-2 关键零件强度校核示意图", "应标出截面、孔位、轴线、弯矩方向和校核尺寸。");
    }

    private void chapter4(XWPFDocument doc, DesignProject project, String equipment) {
        heading(doc, "第4章 标准件选型", 1);
        section(doc, "4.1 材料选型", List.of(
                "材料选型需要综合强度、刚度、加工方式、重量、成本和使用环境。机架、安装板和支架等非标承载件可优先选用Q235B、45钢或6061铝合金；防护外壳可根据重量和防护要求选择薄钢板、铝板或工程塑料。",
                "材料确定后，应同步确定板厚、焊接方式、表面处理和加工要求。对于焊接结构，应在技术要求中说明焊缝连续、无明显夹渣和气孔；对于安装基准面，应说明加工后去毛刺并保证平面度。",
                "材料表不仅用于论文，也应进入零件图和BOM。若材料为建议值，应在说明书中保留待校核状态，避免把初步推荐当作最终工艺要求。"
        ));
        table(doc, "表4-1 材料性能与应用表", List.of("材料", "典型用途", "特点", "备注"), List.of(
                List.of("Q235B", "机架、支座、焊接板件", "焊接性好、成本低", "适合一般承载结构"),
                List.of("45钢", "轴、连接销、轮轴", "强度较高、可热处理", "用于受力轴类零件"),
                List.of("6061铝合金", "轻量化安装板、外壳", "质量轻、加工性好", "适合减重需求"),
                List.of("橡胶/聚氨酯", "滚轮包胶、缓冲垫", "耐磨、减振", "需按接触工况选择")
        ));
        section(doc, "4.2 电机与减速器选型", List.of(
                "电机选型以功率、转速、输出转矩、安装空间和控制方式为依据。若计算得到的驱动功率为P，选型功率应考虑效率损失和冲击系数，通常取不低于计算功率的1.2倍。",
                "减速器选型应匹配电机额定转速和执行机构所需转速。减速比由输入转速与输出转速确定，输出转矩应大于工作转矩，并留有安全裕量。",
                "电机和减速器属于标准件，应通过OnlineStandardPartProvider进入检索流程。当前未接入真实在线接口时，只能作为模拟推荐，不能在论文中表述为真实在线找到。"
        ));
        section(doc, "4.3 轴承、导轨与连接件选型", List.of(
                "轴承选型以轴径、径向载荷、轴向载荷和寿命要求为依据。导轨选型以承载能力、行程、安装孔距和滑块尺寸为依据。螺栓选型应考虑受力方向、孔边距、拆装空间和防松措施。",
                "标准件应在BOM中显示类别、型号、来源状态和数量。若只有尺寸参数，可先生成参数化近似模型；若没有真实模型或参数，只能生成简化占位，并在日志中标记。",
                "CAD中标准件需要采用类别化工程表达。例如轴承用圆环和中心线表示，电机用圆柱壳体、法兰和输出轴表示，螺栓用标准符号表示，导轨用导轨截面和安装孔阵列表达。"
        ));
        table(doc, "表4-2 标准件选型表", List.of("名称", "类别", "型号", "来源状态"), standardPartRows(project));
        section(doc, "4.4 非标件设计", List.of(
                "非标件主要包括机架、外壳、安装板、检测支架、磁吸附座、清扫刷安装座和快拆结构等。非标件设计应给出材料、板厚、加工方式、安装孔、连接方式和几何特征。",
                "非标件不应退化为普通方块。机架应体现侧板、横梁、加强筋、安装孔和连接板；检测支架应体现导轨、滑块、调节槽和传感器安装孔；清扫刷组件应体现刷盘、刷毛阵列、中心轴孔和电机座。",
                "非标件的零件图应优先从装配树中选择关键结构生成，至少包含主视图、俯视图或侧视图、材料、尺寸、技术要求和标题栏。"
        ));
        figure(doc, "图4-1 标准件与非标件关系图", "应表现标准件、非标安装件、装配约束和BOM序号之间的对应关系。");
    }

    private void chapter5(XWPFDocument doc, DesignProject project, String equipment) {
        heading(doc, "第5章 CAD与建模说明", 1);
        section(doc, "5.1 CAD总装图绘制", List.of(
                "CAD总装图采用主视图、俯视图和侧视图表达整机结构。主视图用于展示整机外形、关键机构、机架、外壳和功能模块；俯视图用于展示左右或前后布局、安装位置和模块相对关系；侧视图用于展示高度、支撑、轮系或安装关系。",
                "图纸不直接由三维截图投影生成，而由DrawingPlan规划视图内容、尺寸链、标签和BOM。每个视图只显示完成表达所需的关键结构，避免把所有零件堆进图框造成拥挤。",
                "尺寸标注只使用正式来源，包括任务书明确参数、设计计算结果、标准件尺寸、装配约束距离和用户确认参数。来源不足的尺寸在说明书中标记为待校核，不作为正式尺寸。"
        ));
        section(doc, "5.2 主要零件图绘制", List.of(
                "主要零件图从装配树中自动拆分关键非标件和主要机构。优先选择承载机架、功能安装座、传动支撑件、检测支架、清扫组件或吸附模块等对整机性能影响较大的结构。",
                "每张零件图应包含主视图、俯视图或侧视图、孔位、板厚、材料、技术要求和标题栏。对于板件，应标注折弯边、加强筋、孔距和基准面；对于轴类或轮类零件，应标注轴孔、键槽、倒角和中心线。",
                "零件图与总装图通过序号和BOM关联。BOM中出现的零件必须能在图纸中找到，图纸中标注的序号也必须能在BOM中找到。"
        ));
        section(doc, "5.3 SolidWorks三维建模过程", List.of(
                "SolidWorks建模以机架或主体结构为基准件。首先建立基准坐标系和主要全局变量，再按结构树依次建立非标件草图、拉伸、孔阵列、倒角和材料属性。",
                "标准件若有真实模型或缓存模型，优先导入；若只有尺寸参数，则用参数化标准件几何生成近似模型；若缺少参数，才使用简化占位模型并在记录中标记。这样能够避免3D展示全部退化为方块和圆柱。",
                "装配阶段根据AssemblyConstraintEngine输出的基准面、轴线、接触面、孔阵列、对称面和偏置距离建立配合关系。左右对称部件应使用对称约束，轮轴类零件应使用同轴约束，安装板类零件应使用接触或固定约束。"
        ));
        section(doc, "5.4 工程图说明", List.of(
                "工程图应包含图框、标题栏、图号、比例、三视图、尺寸链、部件编号、BOM、参数表和技术要求。当前阶段轴测图、剖视图和局部详图不混入总装三视图，避免图纸拥挤。",
                "技术要求应说明未注尺寸公差、未注倒角、表面处理、焊接质量、安装孔去毛刺、标准件复核和试装要求。对模拟标准件推荐，应在技术要求或BOM备注中说明未联网校验。",
                "图纸清晰度优先于复杂度。毕业设计总装图应让评阅教师第一眼识别设备类型、主要机构和装配关系，而不是只看到矩形框、圆形和内部调试字段。"
        ));
        table(doc, "表5-1 图纸明细表", List.of("图号", "名称", "主要内容", "备注"), List.of(
                List.of("ZD-00", "总装三视图", "主视图、俯视图、侧视图、BOM、参数表", "用于总体表达"),
                List.of("LJ-01", "主要机构图", "关键机构、尺寸和技术要求", "从装配树拆分"),
                List.of("LJ-02", "主要零件图", "材料、孔位、板厚和基准", "与BOM关联"),
                List.of("SW-01", "建模步骤说明", "SolidWorks建模顺序和装配约束", "本地运行宏")
        ));
        figure(doc, "图5-1 CAD总装三视图", "应包含主视图、俯视图、侧视图、尺寸标注、部件编号、BOM和技术要求。");
        figure(doc, "图5-2 SolidWorks装配关系示意图", "应标注基准件、对称面、轴线、安装孔阵列、接触面和关键偏置距离。");
    }

    private void chapter6(XWPFDocument doc, DesignProject project, String equipment) {
        heading(doc, "第6章 总结", 1);
        section(doc, "6.1 设计总结", List.of(
                equipment + "完成了从任务书解析到结构树、标准件、非标件、装配树、设计计算、CAD图纸和说明书的统一生成。该流程把设备识别和工程深化放在绘图之前，能够减少不同任务书生成结果高度相似的问题。",
                "论文生成器采用正文清洗和质量检查机制，禁止把生成指令、段落提示、模板说明和占位文字写入最终DOCX。若检测到提示词残留、章节缺失、乱码或重复率过高，系统会判定文档失败并禁止导出。",
                "当前成果可作为本科毕业设计初稿使用，后续仍需结合真实任务书、指导教师意见、标准件样本和现场工况对参数、图纸和计算结果进行复核。"
        ));
        section(doc, "6.2 展望", List.of(
                "后续应接入真实公开标准件平台，完善电机、减速器、轴承、导轨、联轴器和螺栓等标准件的在线检索、模型下载和格式转换能力。",
                "在CAD图纸方面，可进一步增加剖视图、局部详图、尺寸公差、形位公差、粗糙度和焊接符号，使图纸从清晰三视图逐步提升到更完整的工程图纸。",
                "在论文方面，可结合用户上传的参考文献、三维模型图和CAD图自动生成更贴合具体课题的图注、表注和计算过程，进一步提高定稿效率。"
        ));
    }

    private List<String> engineeringParagraphs(String equipment, String topic, List<String> base) {
        List<String> result = new ArrayList<>(base);
        result.add(equipment + "的" + topic + "应与结构树、BOM和装配约束保持一致。计算对象确定后，需要把载荷路径、材料性能、几何尺寸和安全系数写清楚，使后续图纸尺寸具有依据。");
        result.add(topic + "的结果应回写到参数表和CAD尺寸链中。若计算结果改变板厚、孔距、轮径、支撑间距或安装高度，图纸和SolidWorks建模步骤也应同步更新。");
        result.add("在本科毕业设计语境下，" + topic + "不仅给出结论，还应说明公式来源、符号含义、代入过程和校核判断。这样才能体现机械设计说明书的工程完整性。");
        result.add("若任务书未给出足够参数，" + topic + "可采用初步设计值进行方案论证，但应在表格中标注来源为推导或待校核，避免与任务书明确参数混淆。");
        return result;
    }

    private void references(XWPFDocument doc) {
        heading(doc, "参考文献", 1);
        List<String> refs = List.of(
                "[1] 濮良贵, 纪名刚. 机械设计[M]. 北京: 高等教育出版社.",
                "[2] 成大先. 机械设计手册[M]. 北京: 化学工业出版社.",
                "[3] 王先逵. 机械制造工艺学[M]. 北京: 机械工业出版社.",
                "[4] GB/T 1804. 一般公差 未注公差的线性和角度尺寸的公差[S].",
                "[5] GB/T 1184. 形状和位置公差 未注公差值[S].",
                "[6] GB/T 276. 滚动轴承 深沟球轴承 外形尺寸[S].",
                "[7] GB/T 5782. 六角头螺栓[S].",
                "[8] GB/T 4458. 机械制图 尺寸注法[S].",
                "[9] GB/T 14689. 技术制图 图纸幅面和格式[S].",
                "[10] SolidWorks三维机械设计教程[M]. 北京: 机械工业出版社.",
                "[11] AutoCAD机械制图应用教程[M]. 北京: 清华大学出版社.",
                "[12] 机械工程材料及成形工艺基础[M]. 北京: 高等教育出版社."
        );
        refs.forEach(ref -> paragraph(doc, ref));
    }

    private void acknowledgements(XWPFDocument doc) {
        heading(doc, "致谢", 1);
        paragraph(doc, "在本课题完成过程中，指导教师在题目分析、结构方案、设计计算、工程图表达和论文写作方面给予了持续指导，使设计工作能够从任务书逐步落实到参数表、图纸和说明书。");
        paragraph(doc, "感谢同学在资料整理、模型核对和排版检查中提供帮助。毕业设计训练不仅提升了机械结构分析和CAD表达能力，也加深了对标准件选型、非标件设计和装配约束的理解。");
        paragraph(doc, "由于时间和资料条件限制，文中部分标准件型号和局部尺寸仍需在后续定稿阶段结合真实样本、公开标准件平台和指导教师意见进一步复核。");
    }

    private void section(XWPFDocument doc, String heading, List<String> paragraphs) {
        heading(doc, heading, 2);
        for (String paragraph : paragraphs) {
            paragraph(doc, paragraph);
        }
    }

    private void title(XWPFDocument doc, String text) {
        XWPFParagraph p = doc.createParagraph();
        p.setAlignment(ParagraphAlignment.CENTER);
        XWPFRun r = p.createRun();
        r.setBold(true);
        r.setFontSize(18);
        r.setText(sanitizer.sanitize(text));
    }

    private void heading(XWPFDocument doc, String text, int level) {
        XWPFParagraph p = doc.createParagraph();
        p.setStyle("Heading" + level);
        XWPFRun r = p.createRun();
        r.setBold(true);
        r.setFontSize(level == 1 ? 16 : 14);
        r.setText(sanitizer.sanitize(text));
    }

    private void paragraph(XWPFDocument doc, String text) {
        String clean = sanitizer.sanitize(text);
        if (clean.isBlank()) {
            return;
        }
        XWPFParagraph p = doc.createParagraph();
        p.setAlignment(ParagraphAlignment.BOTH);
        XWPFRun r = p.createRun();
        r.setFontSize(11);
        r.setText(clean);
    }

    private void formula(XWPFDocument doc, String formula, String number) {
        XWPFParagraph p = doc.createParagraph();
        p.setAlignment(ParagraphAlignment.CENTER);
        XWPFRun r = p.createRun();
        r.setFontSize(11);
        r.setText(formula + "    " + number);
    }

    private void figure(XWPFDocument doc, String caption, String detail) {
        paragraph(doc, caption);
        paragraph(doc, "图示要求：" + detail);
    }

    private void parameterTable(XWPFDocument doc, DesignProject project) {
        table(doc, "表2-1 主要设计参数表", List.of("参数", "数值", "单位", "来源"), project.allParameters().stream().limit(12)
                .map(p -> List.of(safeText(p.getName(), "参数"), String.valueOf(p.getValue()), safeText(p.getUnit(), ""), safeText(firstNonBlank(p.getSource(), p.getBasis()), "待校核"))).toList());
    }

    private void table(XWPFDocument doc, String caption, List<String> headers, List<List<String>> rows) {
        paragraph(doc, caption);
        List<List<String>> safeRows = rows == null || rows.isEmpty() ? List.of(List.of("待补全", "待校核", "", "")) : rows;
        XWPFTable table = doc.createTable(Math.max(2, safeRows.size() + 1), headers.size());
        CTTblWidth width = table.getCTTbl().getTblPr().isSetTblW() ? table.getCTTbl().getTblPr().getTblW() : table.getCTTbl().getTblPr().addNewTblW();
        width.setType(STTblWidth.DXA);
        width.setW(BigInteger.valueOf(9000));
        for (int i = 0; i < headers.size(); i++) {
            setCell(table.getRow(0).getCell(i), headers.get(i));
        }
        for (int r = 0; r < safeRows.size(); r++) {
            XWPFTableRow row = table.getRow(r + 1);
            for (int c = 0; c < headers.size(); c++) {
                setCell(row.getCell(c), c < safeRows.get(r).size() ? safeRows.get(r).get(c) : "");
            }
        }
    }

    private void setCell(XWPFTableCell cell, String text) {
        cell.removeParagraph(0);
        XWPFParagraph p = cell.addParagraph();
        XWPFRun r = p.createRun();
        r.setFontSize(10);
        r.setText(sanitizer.sanitize(text));
    }

    private List<List<String>> calculationRows(DesignProject project) {
        if (project.getCalculations().isEmpty()) {
            return List.of(
                    List.of("驱动功率计算", "待校核", "kW", "根据载荷和速度确定"),
                    List.of("输出转矩计算", "待校核", "N·m", "用于电机和减速器选型"),
                    List.of("强度校核", "待校核", "MPa", "用于确定板厚和截面"),
                    List.of("连接件校核", "待校核", "MPa", "用于螺栓和孔位设计")
            );
        }
        return project.getCalculations().stream().limit(10)
                .map(c -> List.of(safeText(c.getName(), "计算项目"), String.valueOf(c.getResult()), safeText(c.getUnit(), ""), safeText(c.getConclusion(), "满足初步设计要求")))
                .toList();
    }

    private List<List<String>> standardPartRows(DesignProject project) {
        List<List<String>> rows = project.getResolvedParts().stream()
                .filter(p -> "standard".equals(p.getPartType()))
                .limit(10)
                .map(p -> List.of(
                        safeText(p.getName(), "标准件"),
                        safeText(p.getCategory(), "通用件"),
                        safeText(p.getModel(), "待校核型号"),
                        safeText(p.getRetrievalStatus(), "待校核")
                ))
                .collect(Collectors.toList());
        if (rows.isEmpty()) {
            rows.add(List.of("电机", "motor", "待校核型号", "待检索"));
            rows.add(List.of("轴承", "bearing", "待校核型号", "待检索"));
            rows.add(List.of("螺栓", "bolt", "待校核型号", "待检索"));
        }
        return rows;
    }

    private String collectText(XWPFDocument doc) {
        String paragraphText = doc.getParagraphs().stream().map(XWPFParagraph::getText).collect(Collectors.joining("\n"));
        String tableText = doc.getTables().stream()
                .flatMap(table -> table.getRows().stream())
                .flatMap(row -> row.getTableCells().stream())
                .map(XWPFTableCell::getText)
                .collect(Collectors.joining("\n"));
        return paragraphText + "\n" + tableText;
    }

    private String safeText(String value, String fallback) {
        if (value == null || value.isBlank() || sanitizer.containsMojibake(value)) {
            return fallback;
        }
        return value;
    }

    private String firstNonBlank(String first, String second) {
        if (first != null && !first.isBlank() && !sanitizer.containsMojibake(first)) {
            return first;
        }
        if (second != null && !second.isBlank() && !sanitizer.containsMojibake(second)) {
            return second;
        }
        return "";
    }

    private String join(List<String> items, String fallback) {
        if (items == null || items.isEmpty()) {
            return fallback;
        }
        List<String> clean = items.stream()
                .map(item -> safeText(item, ""))
                .filter(item -> !item.isBlank())
                .distinct()
                .limit(12)
                .toList();
        return clean.isEmpty() ? fallback : String.join("、", clean);
    }
}
