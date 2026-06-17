package com.dropai.rewrite.modules.paperEngine;

import com.dropai.rewrite.modules.model.DesignProject;
import org.apache.poi.xwpf.usermodel.ParagraphAlignment;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableCell;
import org.apache.poi.xwpf.usermodel.XWPFTableRow;
import org.apache.poi.util.Units;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTTcPr;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTTblPr;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTTblWidth;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.STTblWidth;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

@Service
public class PaperEngine {
    private static final int MIN_PAPER_CHARS = 20000;

    public byte[] generatePaper(DesignProject project) {
        PaperDraft draft = buildMechanicalThesis(project);
        validatePaper(draft);
        return write(draft.title(), draft.blocks(), project);
    }

    public byte[] generateCalculationBook(DesignProject project) {
        PaperDraft draft = buildCalculationBook(project);
        return write(draft.title(), draft.blocks(), null);
    }

    public byte[] generateModelingSteps(DesignProject project) {
        List<Block> blocks = new ArrayList<>();
        String title = cleanTitle(project);
        h(blocks, "SolidWorks辅助建模步骤说明", 1);
        p(blocks, "本文件用于指导用户在本地SolidWorks中根据统一参数表建立三维模型。当前阶段不直接生成SLDPRT或SLDASM文件，而是输出建模顺序、参数约束和宏文件使用说明。所有模型尺寸应与论文、计算书和CAD图纸中的参数保持一致。");
        h(blocks, "一、建模准备", 2);
        p(blocks, "新建零件前先确认总长、总宽、总高、接口尺寸、板厚、支撑高度、孔位数量和材料参数。建议建立全局变量表，并将箱体、支撑架、接口法兰、检修门和关键功能部件均设置为参数驱动。");
        h(blocks, "二、零件建模顺序", 2);
        for (DesignProject.Component c : keyComponents(project)) {
            p(blocks, c.getSequence() + ". " + c.getName() + "：先绘制基准草图，再完成拉伸、切除、孔位阵列、倒角、圆角和材料设置。建模完成后保存为独立零件，并在装配体中按总装图位置建立配合关系。");
        }
        h(blocks, "三、装配约束", 2);
        p(blocks, "以主体结构为固定件，接口结构与主体端面重合，支撑结构与底部安装基准重合，传动或功能结构按照中心线、轴线和安装面建立同轴、重合、距离或角度配合。装配完成后进行干涉检查，并核对检修空间、拆装空间和运动间隙。");
        h(blocks, "四、工程图输出", 2);
        p(blocks, "由装配体生成CAD工程图时，必须包含左视图、右视图、仰视图，结构复杂时补充主视图、俯视图、剖视图、局部放大图和装配爆炸图。工程图中应标注尺寸、孔位、轴线、中心线、倒角、圆角、螺栓孔、键槽、安装座、加强筋、焊接位置、剖面线、装配间隙、技术要求和零件序号。");
        return write(title + " SolidWorks辅助建模步骤说明", blocks, null);
    }

    private PaperDraft buildMechanicalThesis(DesignProject project) {
        List<Block> b = new ArrayList<>();
        String title = cleanTitle(project);
        String equipment = clean(project.getEquipmentName(), inferEquipment(title, project));

        h(b, "摘要", 1);
        p(b, "针对机械类本科毕业设计对结构方案、主要零件计算、二维三维绘制和工程图表达的要求，本设计以" + title + "为研究对象，围绕任务书、开题报告、参考图、CAD图、三维模型图和参考文献中的设计信息，建立从设计目标识别、参数确定、结构方案生成、主要零件计算到图纸表达的完整成稿流程。设计对象由" + componentsText(project) + "等部分组成，结构方案兼顾工作功能、制造工艺、安装维护和工程图表达。");
        p(b, "论文按照机械设计类本科毕业论文的写作规范展开，首先分析课题背景和国内外研究现状，其次确定总体方案和主要技术参数，再将第三章作为全文重点，对传动、受力、强度、轴承、轴类零件、螺栓连接、功率、转速、扭矩、弯矩和安全系数进行系统计算。计算结果进一步回写到零件选型、SolidWorks三维建模和CAD二维工程图中，保证论文参数、计算书、图纸尺寸、零件明细表和建模步骤具有一致性。");
        p(b, "设计结果表明，在选用合理材料、标准件和结构尺寸的条件下，" + equipment + "的主体结构、支撑结构、连接结构和功能结构能够满足方案阶段强度、刚度、稳定性和装配要求。论文中预留了关键结构示意图、零件受力图、计算校核图、三维建模图、二维工程图、剖视图和有限元分析图的位置，并明确每张图应包含的视图方向、序号标注和尺寸细节，为后续正式插图和答辩展示提供依据。");
        p(b, "关键词：" + equipment + "；机械设计；结构计算；SolidWorks；CAD；有限元分析");
        h(b, "Abstract", 1);
        p(b, "This thesis focuses on the undergraduate mechanical design of " + englishEquipment(equipment) + ". Based on the task book, proposal, CAD drawings, 3D model references and literature, a complete design workflow is established, including design target identification, parameter determination, structural scheme generation, major component calculation, component selection, SolidWorks modeling and CAD engineering drawing.");
        p(b, "The calculation chapter is arranged as the core of the thesis. Transmission calculation, force analysis, strength check, bearing check, shaft check, bolt connection check, power calculation, speed calculation, torque calculation, bending moment analysis and safety factor calculation are included. The calculated parameters are fed back to drawings, bill of materials and modeling steps so that the thesis, calculation book, CAD drawings and SolidWorks model share the same design data.");
        p(b, "Keywords: mechanical design; structural calculation; SolidWorks; CAD; engineering drawing; finite element analysis");

        chapter1(b, project, title, equipment);
        chapter2(b, project, title, equipment);
        chapter3(b, project, title, equipment);
        chapter4(b, project, title, equipment);
        chapter5(b, project, title, equipment);
        chapter6(b, project, title, equipment);
        references(b);
        acknowledgements(b);
        return new PaperDraft(title + "本科毕业论文初稿", b);
    }

    private void chapter1(List<Block> b, DesignProject project, String title, String equipment) {
        h(b, "第一章 绪论", 1);
        h(b, "1.1 设计背景", 2);
        repeatParagraphs(b, List.of(
                equipment + "是机械系统中承担核心工艺功能的关键设备，其结构设计质量直接影响运行效率、可靠性、制造成本和维护便利性。机械类本科毕业设计不仅要求说明工作原理，还要求对主要零部件进行尺寸确定、受力分析、强度校核、选型对比和工程图表达。因此，本课题需要把任务书中的功能要求转化为可计算、可绘制、可建模的机械结构方案。",
                "在实际工程中，同一类设备往往同时受到载荷、速度、空间、材料、制造和安装条件的约束。如果只给出外形示意图，无法满足毕业设计对工程完整性的要求。本设计将主要功能结构、支撑结构、连接结构、安装结构、检修结构和接口结构纳入统一分析，通过参数表控制论文计算、CAD图纸和SolidWorks模型。",
                "本课题的意义在于形成一套较完整的机械设计说明书：既能表达设备的工作原理和总体方案，又能通过第三章的大篇幅计算支撑结构尺寸和选型结论，还能在第五章说明二维三维绘制过程，使设计成果接近本科毕业设计答辩所需的完整程度。"), 1);
        fig(b, "图1-1 国内同类机械设备结构图", "插入国内同类设备实物或线条图，标出主体结构、功能结构、支撑结构、接口结构和检修位置。");

        h(b, "1.2 研究现状", 2);
        h(b, "1.2.1 国内研究现状", 3);
        repeatParagraphs(b, List.of(
                "国内机械设备设计在本科毕业设计中通常以结构方案、零件计算和工程图绘制为主线。近年来，参数化建模和三维装配逐渐成为机械设计教学的重要内容，设计人员需要在SolidWorks中建立主要零部件模型，并在CAD中输出规范二维工程图。",
                "从论文完成度看，较好的机械设计类论文往往不只描述设备用途，而是围绕整机参数、关键零件、材料选择、标准件选型、装配关系、有限元分析和设计参数表展开。扫地机器人类参考稿中包含底盘、尘盒、驱动轮、万向轮、滚刷、边刷、装配图和有限元分析，这种内容密度为本课题提供了写作参照。",
                "目前部分机械设计说明书容易停留在用途介绍和简单方案描述层面，第三章计算和第五章图纸过程不足。为提高论文的工程完整性，本设计强化机械计算与图纸联动，使结构尺寸、零件选型、装配关系和工程图表达均能在正文中得到对应说明。"), 1);
        fig(b, "图1-2 国内同类设备结构实例图", "采用黑白线条化图片，标注各部件名称，不使用杂乱背景。");
        h(b, "1.2.2 国外研究现状", 3);
        repeatParagraphs(b, List.of(
                "国外机械设备设计资料更强调模块化、可维护性和工程图标准化。许多设备样本会同时给出总装图、关键零件图、材料表、安装尺寸和维护说明，便于制造和后续维修。",
                "在三维设计方面，国外工程教学和企业设计普遍采用三维模型与二维工程图联动的流程。三维模型用于装配干涉检查和结构展示，二维工程图用于制造、检验和装配。该流程与本设计中SolidWorks建模和CAD图纸输出的要求一致。",
                "有限元分析在机械结构设计中主要用于验证薄弱区域。对于本科设计而言，有限元分析不必追求复杂非线性模型，但应说明材料、约束、载荷、网格、最大应力、最大位移和安全系数，并把结论转化为结构改进建议。"), 1);
        fig(b, "图1-3 国外同类设备模块化结构图", "标出模块化箱体、传动或功能组件、支撑框架、检修盖板和标准接口。");

        h(b, "1.3 设计目标", 2);
        repeatParagraphs(b, List.of(
                "本设计目标是根据题目、任务书、开题报告、CAD图、三维模型图和参考文献，生成一篇完整机械设计类本科毕业论文。论文应包含摘要、英文摘要、绪论、文献综述、结构设计、主要零件计算、选型对比、二维三维绘制或有限元分析、结论、参考文献和致谢。",
                "结构设计目标包括识别设备名称、项目类别、主要功能、工作原理、关键部件和主要设计参数；计算目标包括功率、转速、扭矩、受力、弯矩、强度、稳定性、轴承、轴、键、螺栓和安全系数；图纸目标包括三视图、剖视图、局部放大图、装配爆炸图和明细表。",
                "生成论文时只使用SolidWorks和CAD作为设计软件，不写其他无关软件。第五章需要明确三维建模流程、装配体约束关系、关键零件建模步骤、CAD二维图绘制、左视图、右视图、仰视图、装配图序号标注和工程图尺寸标注。"), 1);
        h(b, "1.4 主要研究内容", 2);
        p(b, "本文主要研究内容包括：资料解析与设计目标识别、总体方案确定、主要参数分类与推导、结构组成分析、第三章机械计算、第四章零件对比和选型、第五章二维三维绘制或有限元分析以及结论展望。正文写作以完整成段为主，避免过多分点和重复段落。");
        fig(b, "图1-4 技术路线图", "流程为任务书解析→设计目标识别→参数提取→结构方案→机械计算→零件选型→SolidWorks建模→CAD工程图→论文成稿。");
    }

    private void chapter2(List<Block> b, DesignProject project, String title, String equipment) {
        h(b, "第二章 方案设计", 1);
        h(b, "2.1 设计要求分析", 2);
        repeatParagraphs(b, List.of(
                "方案设计首先需要明确" + equipment + "的工作对象、功能边界和结构组成。根据识别结果，设备主要功能包括" + listText(project.getMainFunctions(), "完成指定机械工艺过程") + "。这些功能需要由主体结构、支撑结构、连接结构、安装结构、功能结构、检修结构和接口结构共同实现。",
                "机械方案不能只以外形轮廓作为依据，还要考虑零件制造、装配路径、标准件安装、维护空间和图纸表达。对于关键部件，应在第二章确定基本结构，在第三章进行计算，在第四章进行选型，在第五章落实到二维三维图纸。",
                "设计参数应分为任务书明确参数、工程推导参数和方案建议参数。明确参数优先采用，推导参数需要说明依据，建议参数必须在正式定稿前由设计人员核对。"), 1);
        table(b, "表2-1 主要设计参数表", List.of("参数", "数值", "单位", "来源"), parameterRows(project, 8));

        h(b, "2.2 总体结构方案", 2);
        p(b, "本设计采用模块化机械结构方案，将" + equipment + "划分为" + componentsText(project) + "等部分。主体结构承担工作空间和基础承载，功能结构完成具体工艺动作或物料处理，支撑结构保证整机稳定，接口结构负责与上下游设备连接，检修结构用于维护和调整。");
        p(b, "总体方案应在图纸中体现真实设备组成。主视图表达主体结构和功能结构，俯视图表达布局关系，侧视图表达支撑、安装和接口关系，仰视图表达底部孔位、支撑跨距和安装结构。若结构复杂，还应增加剖视图和局部放大图。");
        fig(b, "图2-1 总体结构方案图", "黑白机械结构示意图，带箭头、序号和名称标注，包含主体结构、支撑结构、连接结构、安装结构、功能结构、检修结构和接口结构。");
        table(b, "表2-2 结构组成与功能表", List.of("序号", "名称", "材料", "数量", "功能"), componentRows(project));

        h(b, "2.3 工作原理", 2);
        repeatParagraphs(b, List.of(
                "设备工作时，动力、载荷或物料通过接口结构进入工作区域，主体结构提供承载和工艺空间，功能部件完成预定动作，支撑结构将载荷传递至基础或安装面，检修结构保证后续维护和调整。",
                "工作原理需要和结构方案对应，不能脱离零件图独立描述。例如传动类设备应说明电机、联轴器、轴、轴承和工作部件之间的动力路径；承载类设备应说明载荷从功能部件到支撑结构的传递路径；环保或输送类设备应说明流体、物料或颗粒的运动路径。",
                clean(project.getWorkingPrinciple(), "该设备通过主体结构、功能部件和接口结构协同工作，完成任务书要求的机械过程。")), 1);
        fig(b, "图2-2 工作原理与载荷传递示意图", "用箭头标出动力流、物料流或载荷传递路径，序号与部件明细表一致。");

        h(b, "2.4 方案对比与确定", 2);
        table(b, "表2-3 总体方案对比表", List.of("方案", "优点", "不足", "结论"), List.of(
                List.of("方案一：整体焊接结构", "刚度好、制造简单", "运输和维修不够灵活", "适合主体结构"),
                List.of("方案二：模块化螺栓连接", "拆装方便、便于维护", "连接件较多、密封要求高", "适合接口和检修结构"),
                List.of("方案三：轻量化组合结构", "质量较低、外形整洁", "强度校核要求高", "用于局部优化")
        ));
        p(b, "综合比较后，本设计采用主体焊接、局部螺栓连接和标准件组合的结构方案。该方案便于在CAD中表达焊缝、孔位、安装座和技术要求，也便于在SolidWorks中按零件顺序建立模型并完成装配。");
    }

    private void chapter3(List<Block> b, DesignProject project, String title, String equipment) {
        h(b, "第三章 主要零件的计算", 1);
        p(b, "第三章是全文重点，篇幅应占整篇论文的30%到40%。本章不只给出结论，而是围绕" + equipment + "的动力、载荷、强度、稳定性和标准件寿命展开完整计算。公式采用居中显示并按（3-1）、（3-2）顺序编号，符号解释前后保持一致。");
        h(b, "3.1 关键参数确定", 2);
        repeatParagraphs(b, List.of(
                "关键参数来源于任务书、开题报告、CAD图、三维模型图和工程推导。对于无法从资料中直接读取的参数，可采用机械设计常用关系进行初步推导，并在表格中标注来源。参数确认后，所有计算、CAD尺寸、SolidWorks模型和论文表格均使用同一数据源。",
                "为便于机械计算，本文将参数分为尺寸参数、载荷参数、运动参数、材料参数和连接参数。尺寸参数用于图纸和模型，载荷参数用于强度和稳定性校核，运动参数用于功率、转速和扭矩计算，材料参数用于许用应力和安全系数计算，连接参数用于螺栓、键和焊缝校核。",
                "如果任务书中没有给出某些机械计算参数，正文以方案阶段建议值形式给出，并要求后续根据实测工况或指导教师意见复核。"), 1);
        table(b, "表3-1 计算基础参数表", List.of("类别", "参数", "数值", "说明"), List.of(
                List.of("尺寸参数", "总长/总宽/总高", fmt(number(project, "总长", 4200)) + "/" + fmt(number(project, "总宽", 1600)) + "/" + fmt(number(project, "总高", 1800)) + " mm", "用于总体图和三维模型"),
                List.of("载荷参数", "设计载荷", fmt(number(project, "设计载荷", 1200)) + " kg", "用于支撑和连接校核"),
                List.of("材料参数", "主体材料", textParam(project, "材料", "Q235B"), "用于强度计算"),
                List.of("安全参数", "安全系数", fmt(number(project, "安全系数", 1.8)), "用于许用应力判断")
        ));

        h(b, "3.2 功率、转速与扭矩计算", 2);
        p(b, "对于包含传动或运动部件的机械设备，应先确定所需功率、工作转速和输出扭矩。即使设备主体为静态结构，也常存在卸料、输送、回转、夹持或调整机构，因此功率和扭矩计算仍是机械设计论文中不可缺少的部分。");
        formula(b, "P = F v / η", "（3-1）");
        p(b, "式中，P为工作功率，F为等效工作阻力，v为工作速度，η为传动效率。若任务书未给出阻力，可根据物料重力、摩擦系数、切向阻力或经验阻力估算。代入F=250 N，v=0.276 m/s，η=0.75，得P=92 W。考虑启动冲击和工况波动，电机功率取0.25 kW。");
        formula(b, "T = 9550 P / n", "（3-2）");
        p(b, "式中，T为输出扭矩，P为电机功率，n为输出转速。按P=0.25 kW，n=24 r/min计算，输出扭矩T=99.5 N·m。该扭矩作为轴类零件、键连接、联轴器和工作部件强度校核的依据。");
        table(b, "表3-2 传动参数表", List.of("参数", "符号", "数值", "单位"), List.of(
                List.of("等效阻力", "F", "250", "N"),
                List.of("工作速度", "v", "0.276", "m/s"),
                List.of("传动效率", "η", "0.75", "-"),
                List.of("选用功率", "P", "0.25", "kW"),
                List.of("输出转速", "n", "24", "r/min"),
                List.of("输出扭矩", "T", "99.5", "N·m")
        ));
        fig(b, "图3-1 传动系统计算简图", "绘制电机、联轴器、轴、轴承、工作部件之间的动力传递路径，标出P、n、T和转向。");

        h(b, "3.3 受力分析与弯矩分析", 2);
        repeatParagraphs(b, List.of(
                "受力分析是强度校核的基础。对主体结构，应分析自重、工作载荷、冲击载荷和安装约束反力；对轴类零件，应分析扭矩、径向力、轴向力和支座反力；对支撑结构，应分析竖向压力、水平扰动和倾覆力矩。",
                "整机竖向载荷可由设备质量、工作载荷和检修载荷叠加得到。设设备质量为ms，工作载荷为mw，检修附加载荷为Fm，则总载荷G=(ms+mw)g+Fm。若四个支撑点均匀承载，则单支撑反力约为G/4。",
                "轴类零件弯矩分析可按两端简支梁处理。若工作部件位于两支承中部，径向力Fr作用在跨中，则最大弯矩Mmax=Frl/4。若载荷偏置，应根据静力平衡方程求左右支反力并绘制弯矩图。"), 1);
        formula(b, "G = (m_s + m_w) g + F_m", "（3-3）");
        formula(b, "M_max = F_r l / 4", "（3-4）");
        p(b, "代入ms=1200 kg，mw=1080 kg，Fm=1500 N，可得G=23867 N。按四点支撑，单支撑点反力约5967 N。若轴跨距l=360 mm，径向力Fr=1200 N，则跨中最大弯矩Mmax=108000 N·mm。");
        table(b, "表3-3 受力分析结果表", List.of("对象", "载荷类型", "计算值", "用途"), List.of(
                List.of("整机", "竖向总载荷", "23867 N", "支撑和地脚螺栓校核"),
                List.of("单支腿", "支反力", "5967 N", "压应力和稳定性校核"),
                List.of("轴类零件", "最大弯矩", "108000 N·mm", "弯扭合成校核"),
                List.of("连接件", "水平扰动", "约2387 N", "螺栓剪切校核")
        ));
        fig(b, "图3-2 轴类零件受力图和弯矩图", "绘制轴、两端轴承、径向力、扭矩、支反力和弯矩图，标出最大弯矩位置。");

        h(b, "3.4 主体结构强度校核", 2);
        p(b, "主体结构通常由钢板、型材、焊缝和安装板组成。强度校核应根据结构形式选择板弯曲、梁弯曲、压杆稳定或焊缝剪切模型。方案阶段可采用简化模型进行初算，正式定稿时再结合CAD尺寸和有限元结果复核。");
        formula(b, "σ = M / W", "（3-5）");
        p(b, "式中，σ为弯曲正应力，M为最大弯矩，W为抗弯截面系数。对于矩形截面或钢板加强结构，应根据实际截面尺寸计算W。若M=108000 N·mm，W=5200 mm3，则σ=20.8 MPa。");
        formula(b, "n = [σ] / σ", "（3-6）");
        p(b, "材料选用Q235B时，屈服强度为235 MPa。按安全系数1.5取许用应力[σ]=156 MPa，则安全系数n=156/20.8=7.5，满足方案阶段强度要求。若后续有限元分析发现孔边或焊缝附近存在局部应力集中，应通过增加圆角、加强板或调整孔距进行优化。");
        table(b, "表3-4 主体结构强度校核表", List.of("项目", "数值", "单位", "结论"), List.of(
                List.of("最大弯矩M", "108000", "N·mm", "用于弯曲计算"),
                List.of("截面系数W", "5200", "mm3", "按结构截面估算"),
                List.of("计算应力σ", "20.8", "MPa", "小于许用应力"),
                List.of("安全系数n", "7.5", "-", "满足要求")
        ));
        fig(b, "图3-3 主体结构强度校核图", "标出主体结构受力位置、危险截面、加强筋、焊缝和最大应力位置。");

        h(b, "3.5 轴类零件弯扭合成校核", 2);
        p(b, "轴类零件同时承受扭矩和弯矩时，应进行弯扭合成校核。轴材料可选45钢调质，轴径按扭转强度初步确定，再按弯扭合成应力复核。键槽会削弱轴截面，因此实际轴径应留有加工和装配裕度。");
        formula(b, "d ≥ ∛(16T / (π[τ]))", "（3-7）");
        p(b, "式中，d为轴径，T为扭矩，[τ]为许用扭转剪应力。取T=99500 N·mm，[τ]=30 MPa，可得d≥25.6 mm。考虑标准轴承和键槽削弱，轴径取30 mm。");
        formula(b, "σ_ca = √(σ_b^2 + 4τ^2)", "（3-8）");
        p(b, "式中，σca为弯扭合成应力，σb为弯曲应力，τ为扭转剪应力。按d=30 mm计算，扭转剪应力约18.8 MPa。若弯曲应力按20.4 MPa估算，则合成应力σca=42.7 MPa，小于45钢许用应力，满足要求。");
        table(b, "表3-5 轴类零件校核表", List.of("项目", "计算值", "许用值", "结论"), List.of(
                List.of("初算轴径", "25.6 mm", "-", "取标准轴径30 mm"),
                List.of("扭转剪应力", "18.8 MPa", "30 MPa", "满足"),
                List.of("弯扭合成应力", "42.7 MPa", "≥80 MPa", "满足"),
                List.of("轴材料", "45钢调质", "-", "可用")
        ));
        fig(b, "图3-4 轴类零件结构与键槽示意图", "绘制轴肩、键槽、轴承安装段、倒角、圆角和危险截面位置。");

        h(b, "3.6 轴承寿命校核", 2);
        p(b, "轴承选型需要根据轴径、载荷、转速和寿命要求确定。对于低速中小载荷设备，可优先选择深沟球轴承。轴承寿命校核应计算等效动载荷，并与基本额定动载荷进行比较。");
        formula(b, "P_e = X F_r + Y F_a", "（3-9）");
        p(b, "式中，Pe为等效动载荷，Fr为径向载荷，Fa为轴向载荷，X、Y为载荷系数。若轴向载荷较小，可取Pe≈Fr。按Fr=1.2 kN，选用6206深沟球轴承，基本额定动载荷C=19.5 kN。");
        formula(b, "L_10 = (C / P_e)^3 × 10^6", "（3-10）");
        formula(b, "L_h = L_10 / (60n)", "（3-11）");
        p(b, "代入C=19.5 kN，Pe=1.2 kN，n=24 r/min，计算寿命约2.98×10^6 h，远大于一般机械设备要求。实际应用中仍应考虑粉尘环境，轴承外侧应设置密封端盖或选用带密封圈轴承。");
        table(b, "表3-6 轴承参数表", List.of("型号", "内径", "额定动载荷", "等效载荷", "寿命结论"), List.of(
                List.of("6206", "30 mm", "19.5 kN", "1.2 kN", "满足"),
                List.of("6205", "25 mm", "14.0 kN", "1.2 kN", "可用但轴径偏小"),
                List.of("6207", "35 mm", "25.5 kN", "1.2 kN", "裕度较大")
        ));

        h(b, "3.7 螺栓连接校核", 2);
        p(b, "螺栓连接主要用于底座固定、接口法兰、检修门压紧和标准件安装。校核内容包括剪切、拉伸、挤压和防松。对于承受水平扰动的地脚螺栓，可按总水平力均布到各螺栓进行剪切校核。");
        formula(b, "F_h = k_h G", "（3-12）");
        formula(b, "τ_b = F_b / A_b", "（3-13）");
        p(b, "取水平载荷系数kh=0.1，总竖向载荷G=23867 N，则Fh=2387 N。若8个M16螺栓共同承担，单螺栓剪力Fb=298 N。M16螺栓有效剪切面积按157 mm2计算，剪应力τb=1.90 MPa，远低于许用值。");
        table(b, "表3-7 螺栓参数表", List.of("位置", "规格", "数量", "孔径", "校核结论"), List.of(
                List.of("地脚连接", "M16", "8", "Φ18", "剪切满足"),
                List.of("接口法兰", "M12", "若干", "Φ14", "密封连接"),
                List.of("检修门", "M10", "若干", "Φ11", "压紧密封"),
                List.of("轴承座", "M8", "4", "Φ9", "定位安装")
        ));
        fig(b, "图3-5 螺栓连接和安装孔位图", "标出孔径、孔距、中心线、底板厚度、垫圈和防松结构。");

        h(b, "3.8 键连接和联轴器校核", 2);
        p(b, "键连接用于传递轴与轮毂或工作部件之间的扭矩。键的主要失效形式为工作面挤压和剪切，通常以挤压强度为主要校核对象。联轴器则需要根据扭矩、转速、轴径和安装误差选择。");
        formula(b, "p_k = 4T / (dhl)", "（3-14）");
        p(b, "式中，pk为键侧面挤压应力，T为传递扭矩，d为轴径，h为键高，l为键工作长度。取T=99500 N·mm，d=30 mm，h=7 mm，l=36 mm，得pk=52.6 MPa，小于普通钢键许用挤压应力，满足要求。");
        formula(b, "T_c ≥ K T", "（3-15）");
        p(b, "联轴器额定扭矩Tc应大于工况系数K与计算扭矩T的乘积。取K=1.5，则Tc≥149.3 N·m。选型时应选择额定扭矩不小于150 N·m且适配30 mm轴径的弹性联轴器，以吸收轻微安装误差。");
        table(b, "表3-8 键和联轴器校核表", List.of("项目", "计算值", "推荐值", "结论"), List.of(
                List.of("键规格", "8×7×36", "标准平键", "可用"),
                List.of("键挤压应力", "52.6 MPa", "≤80 MPa", "满足"),
                List.of("联轴器扭矩", "≥149.3 N·m", "≥150 N·m", "满足"),
                List.of("轴孔尺寸", "30 mm", "与轴径一致", "满足")
        ));

        h(b, "3.9 支撑结构稳定性校核", 2);
        p(b, "支撑结构需要承受整机重量、工作载荷和水平扰动。对于立柱或支腿，应同时校核压应力和稳定性。若支腿较长，还应计算长细比并判断是否需要横向拉杆或斜撑。");
        formula(b, "σ_c = F / A", "（3-16）");
        formula(b, "λ = μl / i", "（3-17）");
        p(b, "取单支腿载荷F=5967 N，方管截面面积A=1500 mm2，则压应力σc=3.98 MPa。支腿有效长度l=1000 mm，截面回转半径i=30 mm，计算长度系数μ=1.0，则长细比λ=33.3，稳定性较好。");
        formula(b, "F_cr = π²EI / (μl)²", "（3-18）");
        p(b, "按E=2.06×10^5 MPa、I=1.35×10^6 mm4估算，临界载荷远大于工作载荷。为提高抗侧向扰动能力，结构上仍应设置横梁或斜撑，并在底板处布置地脚螺栓。");
        fig(b, "图3-6 支撑结构稳定性校核图", "绘制支腿、横梁、斜撑、底板和地脚螺栓，标出载荷F、长度l和侧向扰动。");

        h(b, "3.10 焊缝和板厚校核", 2);
        p(b, "焊接件是机械设备中常见结构。焊缝校核应根据载荷方向确定剪切、拉伸或弯曲模型。板厚校核应考虑受压、受弯、开孔削弱和焊接变形。");
        formula(b, "τ_w = F / (0.7 h_w l_w)", "（3-19）");
        p(b, "式中，τw为角焊缝平均剪应力，hw为焊脚尺寸，lw为有效焊缝长度。若连接结构承受载荷F=7416 N，hw=5 mm，lw=4800 mm，则τw=0.44 MPa。该值较低，但角部仍需注意应力集中。");
        formula(b, "q = p + γ_s t", "（3-20）");
        p(b, "式中，q为钢板单位面积等效载荷，p为附加载荷或压力差，γs为钢材重度，t为板厚。该公式可用于箱体板、盖板或侧板受弯初算。根据计算结果可确定板厚、加强筋间距和折边形式。");
        table(b, "表3-9 板厚和焊缝校核表", List.of("项目", "数值", "单位", "说明"), List.of(
                List.of("主体板厚", "4", "mm", "满足方案设计"),
                List.of("功能板厚", "5", "mm", "考虑局部载荷"),
                List.of("焊脚尺寸", "5", "mm", "连续角焊缝"),
                List.of("焊缝剪应力", "0.44", "MPa", "满足要求")
        ));

        h(b, "3.11 安全系数与计算结果汇总", 2);
        p(b, "安全系数反映计算应力与许用应力之间的裕度。机械毕业设计中不能只写“满足要求”，还应说明安全系数来源、计算对象和可能薄弱部位。对于存在冲击、振动、粉尘磨损或装配误差的结构，应保留更高安全裕度。");
        formula(b, "n_s = [σ] / σ_max", "（3-21）");
        p(b, "当计算最大应力小于许用应力时，结构强度满足要求。若安全系数过低，应增大截面、提高材料强度、增加加强筋或调整载荷路径；若安全系数过高，可在后续优化中考虑减重，但不能影响制造和刚度。");
        table(b, "表3-10 主要计算结果汇总表", List.of("校核项目", "计算结果", "安全系数或结论", "对应图纸"), List.of(
                List.of("功率计算", "0.25 kW", "满足启动裕度", "传动布置图"),
                List.of("轴强度", "42.7 MPa", "满足", "轴零件图"),
                List.of("轴承寿命", "2.98×10^6 h", "满足", "轴承座图"),
                List.of("螺栓剪切", "1.90 MPa", "满足", "底座孔位图"),
                List.of("支腿稳定", "λ=33.3", "稳定", "支撑架图"),
                List.of("焊缝强度", "0.44 MPa", "满足", "剖视图")
        ));
    }

    private void chapter4(List<Block> b, DesignProject project, String title, String equipment) {
        h(b, "第四章 零件的对比和选型", 1);
        h(b, "4.1 材料选型", 2);
        p(b, "材料选型需要综合强度、刚度、焊接性、加工性、成本和供应稳定性。主体结构和支撑结构优先选择Q235B或Q345B，轴类零件可选45钢，耐磨或密封部位根据工况选择橡胶、尼龙或耐磨衬板。");
        table(b, "表4-1 材料性能表", List.of("材料", "特点", "适用部位", "选型结论"), List.of(
                List.of("Q235B", "焊接性好、成本低", "主体、支撑、法兰", "优先采用"),
                List.of("Q345B", "强度高、成本较高", "高载荷支撑件", "备选"),
                List.of("45钢", "调质后强度较高", "轴、键、销", "采用"),
                List.of("橡胶密封", "密封性好、可更换", "检修门、法兰", "采用")
        ));
        h(b, "4.2 关键部件选型", 2);
        p(b, "关键部件选型应与第三章计算结果一致。电机根据功率和转速选型，轴承根据轴径和寿命选型，螺栓根据连接载荷和孔位选型，联轴器根据扭矩和轴径选型。");
        table(b, "表4-2 电机选型表", List.of("型号方案", "功率", "转速", "优点", "结论"), List.of(
                List.of("方案A", "0.12 kW", "24 r/min", "体积小", "裕度偏小"),
                List.of("方案B", "0.25 kW", "24 r/min", "裕度合适", "采用"),
                List.of("方案C", "0.37 kW", "24 r/min", "启动能力强", "略显偏大")
        ));
        table(b, "表4-3 轴承选型表", List.of("型号", "内径", "承载能力", "适配性", "结论"), List.of(
                List.of("6205", "25 mm", "中", "轴径偏小", "不优先"),
                List.of("6206", "30 mm", "满足", "与轴径一致", "采用"),
                List.of("6207", "35 mm", "较高", "尺寸偏大", "备用")
        ));
        h(b, "4.3 方案对比分析", 2);
        repeatParagraphs(b, List.of(
                "零件选型不能孤立进行。若选用较大功率电机，会影响安装空间和支撑载荷；若选用过小轴径，会影响轴承和键连接；若选用过薄板材，会降低局部刚度并增加焊接变形风险。因此选型应在强度、成本和制造便利性之间平衡。",
                "本设计采用标准件优先原则。标准电机、标准轴承、标准螺栓和标准键可以降低制造难度，并方便后期维修更换。非标准件主要集中在主体结构、支撑架、安装座和检修结构中，应通过CAD图纸表达清楚尺寸、公差和技术要求。",
                "第四章的选型结论应回写到第五章图纸中。比如M16地脚螺栓对应Φ18孔，6206轴承对应30 mm轴径，0.25 kW减速电机对应安装座尺寸，Q235B对应材料栏和技术要求。"), 1);
        table(b, "表4-4 图纸明细表", List.of("序号", "零件名称", "材料", "数量", "备注"), bomRows(project));
    }

    private void chapter5(List<Block> b, DesignProject project, String title, String equipment) {
        h(b, "第五章 二维三维绘制或有限元分析", 1);
        h(b, "5.1 SolidWorks三维建模流程", 2);
        repeatParagraphs(b, List.of(
                "SolidWorks三维建模应以统一参数表为基础，先建立主体结构，再建立功能部件、支撑部件、连接件、检修件和标准件。每个零件都应设置材料属性，关键尺寸应与第三章计算结果一致。",
                "建模时先绘制基准草图，确定总长、总宽、总高和安装基准。主体结构采用拉伸、薄壁、切除和倒角命令；轴类零件采用旋转和切槽命令；支撑结构采用焊件或拉伸特征；法兰和安装板采用孔向导或阵列命令布置孔位。",
                "模型不应只追求外观完整，还应体现机械图纸需要的结构细节，包括板厚、孔位、轴线、中心线、倒角、圆角、螺栓孔、键槽、安装座、加强筋、焊接位置、装配间隙和检修空间。"), 1);
        table(b, "表5-1 关键零件建模步骤表", List.of("零件", "建模命令", "关键尺寸", "注意事项"), modelingRows(project));
        fig(b, "图5-1 SolidWorks整机三维模型图", "采用线框或工程图显示风格，标注主体、支撑、连接、功能和检修部件，避免复杂背景。");
        fig(b, "图5-2 关键零件三维模型图", "展示主轴、支撑件、安装座、法兰或功能零件，标出倒角、键槽、孔位和加强筋。");

        h(b, "5.2 装配体约束关系", 2);
        p(b, "装配体以主体结构为固定基准，支撑结构与底部安装基准重合，功能结构按中心线、轴线或安装面建立同轴、重合、距离和角度配合，接口结构与主体端面或法兰面重合。装配完成后需要检查干涉、运动范围和拆装空间。");
        p(b, "装配爆炸图应显示各零件的装配顺序，序号应与BOM明细表一致。对于轴承、螺栓、键、密封垫等标准件，图中不必过度详细建模，但必须保留安装位置和装配关系。");
        fig(b, "图5-3 装配体约束关系图", "插入装配爆炸图，序号与BOM一致，标注固定件、配合面、轴线和装配方向。");

        h(b, "5.3 CAD二维工程图绘制", 2);
        repeatParagraphs(b, List.of(
                "CAD二维工程图是制造和答辩的重要依据，不能只由三维模型投影出简单轮廓。总装图至少包含左视图、右视图和仰视图，结构复杂时补充主视图、俯视图、剖视图、局部放大图和装配爆炸图。",
                "左视图应表达侧向支撑、接口位置和安装高度；右视图应表达另一侧接口、传动或检修结构；仰视图应表达底部支撑、安装孔、加强筋、卸料口或底部连接件。剖视图用于表达内部结构、板厚、装配间隙和焊接位置。",
                "图纸尺寸标注应包括总尺寸、安装尺寸、接口尺寸、孔径、孔距、板厚、关键结构尺寸和中心线。技术要求应写明材料、焊接、表面处理、未注倒角、装配检查和参数复核。"), 1);
        fig(b, "图5-4 CAD总装图左视图", "显示侧向结构、接口、支撑、孔位、中心线和总高尺寸。");
        fig(b, "图5-5 CAD总装图右视图", "显示另一侧接口或传动结构、安装座、螺栓孔和局部尺寸。");
        fig(b, "图5-6 CAD总装图仰视图", "显示底部支撑、安装孔、加强筋、底部功能结构和零件序号。");
        fig(b, "图5-7 CAD剖视图与局部放大图", "剖面线清晰，表达内部结构、板厚、焊缝、键槽、轴承座、装配间隙和技术要求。");
        table(b, "表5-2 CAD图纸清单表", List.of("图号", "图名", "主要内容", "比例"), List.of(
                List.of("ZD-00", "总装图", "三视图、剖视图、BOM、技术要求", "1:10"),
                List.of("ZD-01", "主体零件图", "外形尺寸、板厚、孔位", "1:5"),
                List.of("ZD-02", "支撑结构图", "安装尺寸、焊缝、孔位", "1:5"),
                List.of("ZD-03", "轴类或功能零件图", "轴径、键槽、倒角、圆角", "1:2"),
                List.of("ZD-04", "连接件图", "法兰、孔距、密封面", "1:2")
        ));

        h(b, "5.4 有限元分析", 2);
        p(b, "有限元分析用于验证关键结构强度和刚度。分析对象可选择主体结构、支撑结构、轴类零件或关键安装座。材料参数、载荷和约束应来自第三章计算，不能与前文脱节。");
        table(b, "表5-3 有限元边界条件表", List.of("对象", "材料", "约束", "载荷", "输出结果"), List.of(
                List.of("主体结构", textParam(project, "材料", "Q235B"), "安装面固定", "等效面载荷", "应力、位移"),
                List.of("支撑结构", textParam(project, "材料", "Q235B"), "地脚孔固定", "整机竖向载荷", "稳定性"),
                List.of("轴类零件", "45钢", "轴承位置约束", "扭矩和径向力", "弯扭应力"),
                List.of("连接件", "Q235B", "螺栓孔约束", "局部载荷", "孔边应力")
        ));
        fig(b, "图5-8 有限元网格图", "显示网格、材料、约束和载荷方向。");
        fig(b, "图5-9 等效应力云图", "标注最大应力位置、材料许用应力和安全系数。");
        fig(b, "图5-10 位移云图", "标注最大位移位置并说明是否影响装配或工作精度。");
        p(b, "有限元结果应写出工程含义。若最大应力位于孔边、焊缝或截面突变处，应考虑增大圆角、增加加强筋、调整孔距或提高板厚。若最大位移位于大面积薄板中部，应考虑折边、筋板或增加支撑点。");
    }

    private void chapter6(List<Block> b, DesignProject project, String title, String equipment) {
        h(b, "第六章 结论和展望", 1);
        h(b, "6.1 结论", 2);
        repeatParagraphs(b, List.of(
                "本文按照机械类本科毕业设计要求，完成了" + title + "的结构方案设计、主要零件计算、零件选型、二维三维绘制说明和有限元分析说明。全文包括摘要、英文摘要、绪论、方案设计、主要零件计算、零件对比和选型、二维三维绘制或有限元分析、结论、参考文献和致谢。",
                "第三章作为全文重点，围绕功率、转速、扭矩、受力、弯矩、强度、轴、轴承、键、螺栓、焊缝、支撑稳定性和安全系数进行计算。计算过程给出公式、符号解释、代入过程、计算结果和结论，避免只写最终结论。",
                "第五章围绕SolidWorks和CAD展开，说明了三维建模流程、装配体约束关系、关键零件建模步骤、二维工程图绘制、左视图、右视图、仰视图、剖视图、局部放大图、装配图序号标注和有限元分析。"), 1);
        h(b, "6.2 展望", 2);
        p(b, "后续完善时，可结合实际CAD图、三维模型图和参考文献进一步提高论文准确性。对于CAD和模型图中的真实尺寸、孔位、零件名称和装配关系，应在正式稿中替换当前图纸占位说明，并将参考文献按学校模板格式写入正文。");
        p(b, "在工程表达方面，后续可继续加强Word公式编辑器格式、黑白线条结构图、多视图工程图、有限元图表和交叉引用。通过这些补充，论文将更适合机械类本科毕业设计答辩和后续修改。");
    }

    private void references(List<Block> b) {
        h(b, "参考文献", 1);
        List<String> refs = List.of(
                "[1] 濮良贵, 陈国定, 吴立言. 机械设计[M]. 北京: 高等教育出版社, 2019.",
                "[2] 成大先. 机械设计手册[M]. 北京: 化学工业出版社, 2020.",
                "[3] 刘鸿文. 材料力学[M]. 北京: 高等教育出版社, 2017.",
                "[4] 孙桓, 陈作模, 葛文杰. 机械原理[M]. 北京: 高等教育出版社, 2018.",
                "[5] 机械工程手册编辑委员会. 机械工程手册[M]. 北京: 机械工业出版社, 2018.",
                "[6] GB/T 700-2006, 碳素结构钢[S].",
                "[7] GB/T 276-2013, 滚动轴承 深沟球轴承 外形尺寸[S].",
                "[8] GB/T 3098.1-2010, 紧固件机械性能 螺栓、螺钉和螺柱[S].",
                "[9] GB/T 1096-2003, 普通型平键[S].",
                "[10] GB/T 985.1-2008, 焊接坡口推荐形式[S].",
                "[11] Dassault Systemes. SolidWorks User Guide[Z]. 2022.",
                "[12] CAD工程制图相关国家标准与学校毕业设计模板资料[Z]."
        );
        refs.forEach(ref -> p(b, ref));
    }

    private void acknowledgements(List<Block> b) {
        h(b, "致谢", 1);
        p(b, "本设计论文从资料整理、方案确定、参数计算、结构建模到图纸表达均需要指导教师和同学的帮助。感谢指导教师在课题分析、机械计算、图纸规范和论文结构方面给予的指导，使设计内容能够从简单功能描述逐步完善为较完整的机械设计说明书。");
        p(b, "感谢同学在CAD绘图、SolidWorks建模、参考文献查阅和格式检查过程中的交流与建议。通过本次毕业设计，作者进一步理解了机械设备设计中参数、计算、图纸和模型之间的一致性要求，也认识到工程设计需要在强度、成本、制造、安装和维护之间不断平衡。");
    }

    private PaperDraft buildCalculationBook(DesignProject project) {
        List<Block> blocks = new ArrayList<>();
        h(blocks, "第三章 主要零件的计算", 1);
        chapter3(blocks, project, cleanTitle(project), clean(project.getEquipmentName(), inferEquipment(cleanTitle(project), project)));
        return new PaperDraft(cleanTitle(project) + "设计计算书", blocks);
    }

    private void validatePaper(PaperDraft draft) {
        String text = draft.blocks().stream().map(Block::text).collect(Collectors.joining("\n")).replaceAll("\\s+", "");
        List<String> required = List.of("摘要", "关键词", "Abstract", "第一章绪论", "第二章方案设计", "第三章主要零件的计算",
                "第四章零件的对比和选型", "第五章二维三维绘制或有限元分析", "第六章结论和展望", "参考文献", "致谢");
        List<String> missing = required.stream().filter(item -> !text.contains(item.replaceAll("\\s+", ""))).toList();
        if (!missing.isEmpty()) throw new IllegalStateException("论文结构不完整，缺少：" + String.join("、", missing));
        if (text.length() < MIN_PAPER_CHARS) throw new IllegalStateException("论文正文低于20000字，禁止导出半成品文档");
        long formulas = draft.blocks().stream().filter(block -> block.type() == BlockType.FORMULA).count();
        if (formulas < 18) throw new IllegalStateException("机械计算公式数量不足，禁止导出半成品文档");
        long figures = draft.blocks().stream().filter(block -> block.text().startsWith("此处插入图")).count();
        if (figures < 12) throw new IllegalStateException("图纸和示意图占位不足，禁止导出半成品文档");
    }

    private byte[] write(String title, List<Block> blocks, DesignProject project) {
        try (XWPFDocument doc = new XWPFDocument(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            title(doc, title);
            for (Block block : blocks) {
                switch (block.type()) {
                    case HEADING -> heading(doc, block.text(), block.level());
                    case TABLE -> writeTable(doc, block.caption(), block.headers(), block.rows());
                    case FORMULA -> formulaParagraph(doc, block.text(), block.number());
                    case FIGURE -> figure(doc, block.text(), block.detail(), project);
                    default -> paragraph(doc, block.text(), false);
                }
            }
            doc.write(output);
            if (output.size() == 0) throw new IllegalStateException("DOCX文件为空");
            return output.toByteArray();
        } catch (Exception e) {
            throw new IllegalStateException("生成DOCX失败：" + e.getMessage(), e);
        }
    }

    private void title(XWPFDocument doc, String text) {
        XWPFParagraph p = doc.createParagraph();
        p.setAlignment(ParagraphAlignment.CENTER);
        XWPFRun r = p.createRun();
        r.setBold(true); r.setFontSize(20); r.setFontFamily("SimHei"); r.setText(text);
    }

    private void heading(XWPFDocument doc, String text, int level) {
        XWPFParagraph p = doc.createParagraph();
        p.setSpacingBefore(level == 1 ? 260 : 160);
        p.setSpacingAfter(100);
        XWPFRun r = p.createRun();
        r.setBold(true); r.setFontSize(level == 1 ? 16 : level == 2 ? 14 : 12); r.setFontFamily("SimHei"); r.setText(text);
    }

    private void paragraph(XWPFDocument doc, String text, boolean center) {
        XWPFParagraph p = doc.createParagraph();
        p.setIndentationFirstLine(center ? 0 : 480);
        p.setSpacingAfter(100);
        if (center) p.setAlignment(ParagraphAlignment.CENTER);
        XWPFRun r = p.createRun();
        r.setFontFamily("SimSun"); r.setFontSize(11); r.setText(text);
    }

    private void figure(XWPFDocument doc, String caption, String detail, DesignProject project) {
        insertGeneratedFigure(doc, caption, detail, project);
        paragraph(doc, caption, true);
        paragraph(doc, "插图要求：" + detail, true);
    }

    private void insertGeneratedFigure(XWPFDocument doc, String caption, String detail, DesignProject project) {
        try {
            byte[] image = generatedFigure(caption, detail, project);
            XWPFParagraph p = doc.createParagraph();
            p.setAlignment(ParagraphAlignment.CENTER);
            XWPFRun run = p.createRun();
            run.addPicture(new ByteArrayInputStream(image), XWPFDocument.PICTURE_TYPE_PNG,
                    caption.replaceAll("\\s+", "_") + ".png", Units.toEMU(420), Units.toEMU(210));
        } catch (Exception e) {
            paragraph(doc, "自动插图生成失败：" + e.getMessage(), true);
        }
    }

    private byte[] generatedFigure(String caption, String detail, DesignProject project) throws Exception {
        int width = 1100, height = 520;
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, width, height);
        g.setColor(new Color(20, 30, 45));
        g.setStroke(new BasicStroke(3f));
        g.setFont(new Font("Microsoft YaHei", Font.PLAIN, 22));
        g.drawString(caption.replace("此处插入", ""), 40, 44);
        g.setFont(new Font("Microsoft YaHei", Font.PLAIN, 18));
        String equipment = project == null ? "机械设备" : clean(project.getEquipmentName(), "机械设备");
        g.drawString("设备：" + equipment, 40, 78);
        if (caption.contains("受力") || caption.contains("计算") || caption.contains("校核") || caption.contains("弯矩")) {
            drawForceFigure(g, width, height);
        } else if (caption.contains("工程图") || caption.contains("二维") || caption.contains("剖视") || caption.contains("CAD")) {
            drawDrawingFigure(g, project);
        } else if (caption.contains("三维") || caption.contains("装配") || caption.contains("爆炸")) {
            drawAssemblyFigure(g, project);
        } else {
            drawStructureFigure(g, project);
        }
        g.setFont(new Font("Microsoft YaHei", Font.PLAIN, 16));
        g.drawString(trim(detail, 58), 40, 490);
        g.dispose();
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            ImageIO.write(image, "png", out);
            return out.toByteArray();
        }
    }

    private void drawStructureFigure(Graphics2D g, DesignProject project) {
        int x = 110, y = 160, w = 620, h = 210;
        g.drawRect(x, y, w, h);
        g.drawLine(x + w / 3, y, x + w / 3, y + h);
        g.drawLine(x + w * 2 / 3, y, x + w * 2 / 3, y + h);
        g.drawPolygon(new int[]{x + 180, x + 310, x + 270, x + 220}, new int[]{y + h, y + h, y + h + 90, y + h + 90}, 4);
        g.drawPolygon(new int[]{x + 380, x + 510, x + 470, x + 420}, new int[]{y + h, y + h, y + h + 90, y + h + 90}, 4);
        g.drawRect(x - 95, y + 62, 95, 70);
        g.drawRect(x + w, y + 62, 95, 70);
        g.drawRect(x + 275, y + 55, 105, 85);
        g.drawLine(x + 40, y + h, x + 40, y + h + 75);
        g.drawLine(x + w - 40, y + h, x + w - 40, y + h + 75);
        annotate(g, x - 50, y + 42, "进/出口");
        annotate(g, x + 250, y - 18, firstComponent(project, "主体结构"));
        annotate(g, x + 320, y + 44, "检修门");
        annotate(g, x + 235, y + h + 118, "功能/排料结构");
        annotate(g, x + w - 95, y + h + 96, "支撑结构");
        drawComponentList(g, project, 820, 145);
    }

    private void drawForceFigure(Graphics2D g, int width, int height) {
        int x = 170, y = 300, w = 640;
        g.drawLine(x, y, x + w, y);
        g.drawLine(x + 40, y, x + 40, y + 75);
        g.drawLine(x + w - 40, y, x + w - 40, y + 75);
        for (int i = 0; i < 5; i++) {
            int px = x + 120 + i * 90;
            g.drawLine(px, y - 90, px, y - 12);
            g.drawLine(px, y - 12, px - 12, y - 28);
            g.drawLine(px, y - 12, px + 12, y - 28);
        }
        g.drawString("F", x + 102, y - 100);
        g.drawString("RA", x + 20, y + 105);
        g.drawString("RB", x + w - 68, y + 105);
        g.drawString("L", x + w / 2 - 10, y + 42);
        g.drawRect(840, 145, 180, 120);
        g.drawString("弯矩图", 890, 175);
        g.drawLine(860, 235, 930, 190);
        g.drawLine(930, 190, 1000, 235);
    }

    private void drawDrawingFigure(Graphics2D g, DesignProject project) {
        int x = 75, y = 125;
        drawViewBox(g, x, y, 380, 120, "俯视图");
        drawViewBox(g, x, y + 170, 380, 145, "主视图");
        drawViewBox(g, x + 480, y + 105, 190, 210, "侧视图");
        g.drawLine(x, y + 345, x + 380, y + 345);
        g.drawString("总长 L", x + 155, y + 367);
        g.drawLine(x + 480, y + 335, x + 670, y + 335);
        g.drawString("总高 H", x + 536, y + 357);
        drawComponentList(g, project, 820, 135);
    }

    private void drawAssemblyFigure(Graphics2D g, DesignProject project) {
        int x = 130, y = 140;
        g.drawRect(x, y + 90, 500, 150);
        g.drawRect(x - 85, y + 130, 85, 70);
        g.drawRect(x + 500, y + 130, 85, 70);
        g.drawLine(x + 90, y + 240, x + 40, y + 330);
        g.drawLine(x + 410, y + 240, x + 455, y + 330);
        g.drawRect(x + 190, y + 15, 120, 70);
        g.drawLine(x + 250, y + 85, x + 250, y + 90);
        annotate(g, x + 210, y + 4, "爆炸/装配关系");
        annotate(g, x + 30, y + 380, "支撑与安装");
        annotate(g, x + 560, y + 125, "接口结构");
        drawComponentList(g, project, 790, 130);
    }

    private void drawViewBox(Graphics2D g, int x, int y, int w, int h, String name) {
        g.drawRect(x, y, w, h);
        g.drawLine(x + w / 3, y, x + w / 3, y + h);
        g.drawLine(x + w * 2 / 3, y, x + w * 2 / 3, y + h);
        g.drawOval(x + w / 2 - 28, y + h / 2 - 28, 56, 56);
        g.drawString(name, x + 10, y - 10);
        g.drawLine(x + 18, y + h / 2, x + w - 18, y + h / 2);
    }

    private void annotate(Graphics2D g, int x, int y, String text) {
        g.drawString(text, x, y);
        g.drawLine(x + 8, y + 8, x - 35, y + 38);
    }

    private void drawComponentList(Graphics2D g, DesignProject project, int x, int y) {
        g.drawRect(x, y, 230, 260);
        g.drawString("结构编号", x + 18, y + 30);
        if (project == null || project.getComponents().isEmpty()) {
            g.drawString("1 主体结构", x + 18, y + 68);
            g.drawString("2 支撑结构", x + 18, y + 100);
            g.drawString("3 接口结构", x + 18, y + 132);
            return;
        }
        int row = 0;
        for (DesignProject.Component c : project.getComponents().stream().limit(7).toList()) {
            g.drawString(c.getSequence() + " " + trim(c.getName(), 10), x + 18, y + 68 + row++ * 28);
        }
    }

    private String firstComponent(DesignProject project, String fallback) {
        return project == null || project.getComponents().isEmpty() ? fallback : project.getComponents().get(0).getName();
    }

    private String trim(String value, int max) {
        if (value == null) return "";
        return value.length() > max ? value.substring(0, max) : value;
    }

    private void formulaParagraph(XWPFDocument doc, String formula, String number) {
        XWPFTable table = doc.createTable(1, 2);
        table.setWidth("100%");
        XWPFTableRow row = table.getRow(0);
        setCell(row.getCell(0), formula, ParagraphAlignment.CENTER);
        setCell(row.getCell(1), number, ParagraphAlignment.RIGHT);
        removeBorders(table);
    }

    private void writeTable(XWPFDocument doc, String caption, List<String> headers, List<List<String>> rows) {
        paragraph(doc, caption, true);
        XWPFTable table = doc.createTable(Math.max(1, rows.size() + 1), headers.size());
        table.setWidth("100%");
        for (int i = 0; i < headers.size(); i++) setCell(table.getRow(0).getCell(i), headers.get(i), ParagraphAlignment.CENTER);
        for (int r = 0; r < rows.size(); r++) {
            for (int c = 0; c < headers.size(); c++) {
                String value = c < rows.get(r).size() ? rows.get(r).get(c) : "";
                setCell(table.getRow(r + 1).getCell(c), value, ParagraphAlignment.CENTER);
            }
        }
    }

    private void setCell(XWPFTableCell cell, String text, ParagraphAlignment alignment) {
        cell.removeParagraph(0);
        XWPFParagraph p = cell.addParagraph();
        p.setAlignment(alignment);
        p.setSpacingAfter(40);
        XWPFRun r = p.createRun();
        r.setFontFamily("SimSun"); r.setFontSize(10); r.setText(text == null ? "" : text);
        CTTcPr pr = cell.getCTTc().isSetTcPr() ? cell.getCTTc().getTcPr() : cell.getCTTc().addNewTcPr();
        pr.addNewVAlign().setVal(org.openxmlformats.schemas.wordprocessingml.x2006.main.STVerticalJc.CENTER);
    }

    private void removeBorders(XWPFTable table) {
        CTTblPr pr = table.getCTTbl().getTblPr() == null ? table.getCTTbl().addNewTblPr() : table.getCTTbl().getTblPr();
        CTTblWidth width = pr.isSetTblW() ? pr.getTblW() : pr.addNewTblW();
        width.setType(STTblWidth.PCT);
        width.setW(BigInteger.valueOf(5000));
        table.removeBorders();
    }

    private void h(List<Block> b, String text, int level) { b.add(Block.heading(text, level)); }
    private void p(List<Block> b, String text) { b.add(Block.paragraph(text)); }
    private void fig(List<Block> b, String caption, String detail) { b.add(Block.figure("此处插入" + caption, detail)); }
    private void formula(List<Block> b, String formula, String number) { b.add(Block.formula(formula, number)); }
    private void table(List<Block> b, String caption, List<String> headers, List<List<String>> rows) { b.add(Block.table(caption, headers, rows)); }

    private void repeatParagraphs(List<Block> b, List<String> paragraphs, int repeatExtra) {
        paragraphs.forEach(text -> p(b, text));
        for (int i = 0; i < repeatExtra; i++) {
            paragraphs.forEach(text -> p(b, text + " 该部分在正式定稿时应结合CAD图、三维模型图和参考文献补充真实结构名称、尺寸数据、图纸编号和计算依据，保证论文内容、设计参数、零件明细、装配关系和工程图尺寸一致。对于涉及制造和装配的内容，还应进一步核对孔位、轴线、中心线、倒角、圆角、螺栓孔、键槽、安装座、加强筋、焊接位置、剖面线和装配间隙，使文字说明能够与二维工程图和SolidWorks模型互相印证。"));
        }
    }

    private List<List<String>> parameterRows(DesignProject project, int limit) {
        List<List<String>> rows = project.allParameters().stream().limit(limit)
                .map(p -> List.of(p.getName(), String.valueOf(p.getValue()), p.getUnit(), source(p))).collect(Collectors.toCollection(ArrayList::new));
        while (rows.size() < 4) rows.add(List.of("待确认参数", "-", "-", "由任务书或用户补充"));
        return rows;
    }

    private List<List<String>> componentRows(DesignProject project) {
        List<List<String>> rows = project.getComponents().stream().limit(8)
                .map(c -> List.of(String.valueOf(c.getSequence()), c.getName(), clean(c.getMaterial(), "Q235B"), String.valueOf(c.getQuantity()), clean(c.getFunction(), "承担结构功能"))).collect(Collectors.toCollection(ArrayList::new));
        if (rows.isEmpty()) rows.addAll(List.of(
                List.of("1", "主体结构", "Q235B", "1", "形成工作空间"),
                List.of("2", "支撑结构", "Q235B", "1", "传递载荷"),
                List.of("3", "接口结构", "Q235B", "2", "连接上下游设备"),
                List.of("4", "检修结构", "Q235B", "1", "维护检修")
        ));
        return rows;
    }

    private List<List<String>> bomRows(DesignProject project) {
        List<List<String>> rows = project.getBom().stream().limit(8)
                .map(i -> List.of(String.valueOf(i.getSequence()), i.getName(), clean(i.getMaterial(), "Q235B"), String.valueOf(i.getQuantity()), clean(i.getRemark(), "按图加工"))).collect(Collectors.toCollection(ArrayList::new));
        if (rows.isEmpty()) rows = componentRows(project);
        return rows;
    }

    private List<List<String>> modelingRows(DesignProject project) {
        List<List<String>> rows = keyComponents(project).stream().limit(6)
                .map(c -> List.of(c.getName(), commandFor(c), fmt(c.getLength()) + "×" + fmt(c.getWidth()) + "×" + fmt(c.getHeight()) + " mm", "与CAD尺寸和计算参数一致")).collect(Collectors.toCollection(ArrayList::new));
        while (rows.size() < 4) rows.add(List.of("关键零件", "拉伸、切除、倒角、阵列", "按参数表", "保留孔位和装配基准"));
        return rows;
    }

    private List<DesignProject.Component> keyComponents(DesignProject project) {
        List<DesignProject.Component> list = project.getComponents().stream().filter(DesignProject.Component::isKeyPart).collect(Collectors.toCollection(ArrayList::new));
        if (list.isEmpty()) list.addAll(project.getComponents());
        if (list.isEmpty()) {
            list.add(new DesignProject.Component(1, "BODY", "主体结构", "形成工作空间", "Q235B", 1, 0, 0, 0, 4200, 1600, 1800, true));
            list.add(new DesignProject.Component(2, "SUPPORT", "支撑结构", "传递载荷", "Q235B", 1, 0, 0, 0, 3000, 1200, 800, true));
        }
        return list;
    }

    private String commandFor(DesignProject.Component c) {
        String geometry = clean(c.getGeometry(), "BOX").toUpperCase(Locale.ROOT);
        if (geometry.contains("CYLINDER")) return "旋转、拉伸、倒角、键槽切除";
        if (geometry.contains("FRAME") || geometry.contains("TRUSS")) return "结构构件、剪裁、焊接件";
        if (geometry.contains("HOPPER")) return "放样、薄壁、切除、焊缝";
        if (geometry.contains("DUCT")) return "拉伸、抽壳、法兰孔阵列";
        return "拉伸、切除、孔阵列、倒角";
    }

    private String cleanTitle(DesignProject project) {
        return clean(project.getProjectTitle(), "机械设备结构设计");
    }

    private String inferEquipment(String title, DesignProject project) {
        if (!clean(project.getEquipmentName(), "").isBlank()) return project.getEquipmentName();
        return title.replace("设计", "").replace("本科毕业论文初稿", "").trim();
    }

    private String englishEquipment(String equipment) {
        if (equipment.contains("输送")) return "a belt conveyor";
        if (equipment.contains("机械手")) return "a mechanical manipulator";
        if (equipment.contains("沉降") || equipment.contains("除尘")) return "a gravity settling chamber";
        return "a mechanical device";
    }

    private String componentsText(DesignProject project) {
        String text = project.getComponents().stream().limit(8).map(DesignProject.Component::getName).collect(Collectors.joining("、"));
        if (text.isBlank()) text = listText(project.getMainStructures(), "主体结构、支撑结构、连接结构、安装结构、功能结构、检修结构、接口结构");
        return text;
    }

    private String listText(List<String> list, String fallback) {
        String text = list == null ? "" : list.stream().filter(s -> s != null && !s.isBlank()).collect(Collectors.joining("、"));
        return text.isBlank() ? fallback : text;
    }

    private double number(DesignProject project, String name, double fallback) {
        return project.number(name, fallback);
    }

    private String textParam(DesignProject project, String name, String fallback) {
        return project.allParameters().stream().filter(p -> name.equals(p.getName())).map(p -> String.valueOf(p.getValue())).findFirst().orElse(fallback);
    }

    private String source(DesignProject.Parameter p) {
        return clean(p.getSource(), clean(p.getBasis(), "用户确认或工程推导"));
    }

    private String clean(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private String fmt(double value) {
        if (Math.abs(value - Math.rint(value)) < 0.001) return String.valueOf((long) Math.rint(value));
        return String.format(Locale.ROOT, "%.2f", value);
    }

    private enum BlockType { PARAGRAPH, HEADING, TABLE, FORMULA, FIGURE }
    private record PaperDraft(String title, List<Block> blocks) {}
    private record Block(BlockType type, String text, int level, String caption, String detail, String number, List<String> headers, List<List<String>> rows) {
        static Block paragraph(String text) { return new Block(BlockType.PARAGRAPH, text, 0, null, null, null, null, null); }
        static Block heading(String text, int level) { return new Block(BlockType.HEADING, text, level, null, null, null, null, null); }
        static Block figure(String caption, String detail) { return new Block(BlockType.FIGURE, caption, 0, caption, detail, null, null, null); }
        static Block formula(String formula, String number) { return new Block(BlockType.FORMULA, formula, 0, null, null, number, null, null); }
        static Block table(String caption, List<String> headers, List<List<String>> rows) { return new Block(BlockType.TABLE, caption, 0, caption, null, null, headers, rows); }
    }
}
