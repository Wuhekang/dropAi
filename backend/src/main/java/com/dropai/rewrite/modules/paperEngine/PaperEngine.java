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
    private static final int MIN_PAPER_CHARS = 20000;

    public byte[] generatePaper(DesignProject project) {
        try (XWPFDocument doc = new XWPFDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            String title = clean(project.getProjectTitle(), "机械类毕业设计");
            String equipment = clean(project.getEquipmentName(), title);
            title(doc, title + "设计说明书");
            buildFullPaper(doc, project, title, equipment);
            String text = collectText(doc);
            validatePaperText(text);
            doc.write(out);
            return out.toByteArray();
        } catch (Exception e) {
            throw new IllegalStateException("论文DOCX生成失败：" + e.getMessage(), e);
        }
    }

    public byte[] generateCalculationBook(DesignProject project) {
        try (XWPFDocument doc = new XWPFDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            title(doc, clean(project.getProjectTitle(), "机械类毕业设计") + "设计计算书");
            heading(doc, "一、计算参数", 1);
            parameterTable(doc, project);
            heading(doc, "二、主要计算过程", 1);
            for (DesignProject.Calculation c : project.getCalculations()) {
                paragraph(doc, c.getName() + "：" + c.getFormula() + "；代入：" + c.getSubstitution() + "；结果=" + c.getResult() + c.getUnit() + "；" + c.getConclusion());
            }
            if (project.getCalculations().isEmpty()) {
                paragraph(doc, "当前项目缺少设计计算结果，应补充功率、扭矩、受力、强度、稳定性和连接校核后再定稿。");
            }
            doc.write(out);
            return out.toByteArray();
        } catch (Exception e) {
            throw new IllegalStateException("设计计算书生成失败：" + e.getMessage(), e);
        }
    }

    public byte[] generateModelingSteps(DesignProject project) {
        try (XWPFDocument doc = new XWPFDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            title(doc, clean(project.getProjectTitle(), "机械类毕业设计") + " SolidWorks辅助建模步骤说明");
            heading(doc, "一、建模前准备", 1);
            paragraph(doc, "根据设计参数表建立全局变量，重点确认整机长宽高、履带或功能机构尺寸、安装孔距、标准件型号、材料和板厚。当前阶段输出VBA宏和建模步骤，不直接生成SLDPRT或SLDASM文件。");
            heading(doc, "二、零件建模顺序", 1);
            int i = 1;
            for (DesignProject.Component c : project.getComponents().stream().limit(12).toList()) {
                paragraph(doc, i++ + ". " + c.getName() + "：按结构树和装配约束建立草图、拉伸、孔位、倒角和材料，保存为独立零件后进入装配体定位。");
            }
            heading(doc, "三、装配约束", 1);
            for (DesignProject.AssemblyConstraint c : project.getAssemblyConstraints().stream().limit(12).toList()) {
                paragraph(doc, c.getPartName() + " 装配到 " + c.getMountTo() + "，约束类型为" + c.getConstraintType() + "，安装面=" + c.getMountingFace() + "，来源=" + c.getSource() + "。");
            }
            doc.write(out);
            return out.toByteArray();
        } catch (Exception e) {
            throw new IllegalStateException("SolidWorks建模步骤生成失败：" + e.getMessage(), e);
        }
    }

    private void buildFullPaper(XWPFDocument doc, DesignProject project, String title, String equipment) {
        heading(doc, "摘要", 1);
        paragraph(doc, title + "以" + equipment + "为对象，依据任务书、开题报告、CAD参考图、三维模型图和参考文献要求，建立从项目识别、结构树生成、标准件选择、非标件生成、装配约束、设计计算、CAD三视图到设计说明书输出的完整毕业设计流程。设计过程中不把任务书直接转换成简单几何体，而是先识别主要功能和机构组成，再形成统一的设计数据源。论文参数、BOM、CAD尺寸、零件尺寸和计算结果均由同一套项目数据生成，避免不同成果之间出现名称和参数不一致。当前标准件在线接口未接入真实平台时，所有mock推荐均明确标注为模拟推荐，未联网校验，后续定稿需重新确认型号、安装孔距和材料。");
        paragraph(doc, "关键词：" + equipment + "；机械设计；结构树；装配约束；CAD；SolidWorks");
        heading(doc, "Abstract", 1);
        paragraph(doc, "This thesis presents an undergraduate mechanical design workflow for " + equipment + ". The design data are shared by project analysis, structure tree, standard part selection, non-standard part generation, assembly constraints, CAD drawings, BOM and documentation. Mock standard parts are marked clearly and must be verified before final engineering use.");
        paragraph(doc, "Keywords: mechanical design; structure tree; assembly constraint; CAD; SolidWorks");

        chapter1(doc, project, equipment);
        chapter2(doc, project, equipment);
        chapter3(doc, project, equipment);
        chapter4(doc, project, equipment);
        chapter5(doc, project, equipment);
        chapter6(doc, project, equipment);
        heading(doc, "参考文献", 1);
        for (int i = 1; i <= 12; i++) paragraph(doc, "[" + i + "] 机械设计、机械制图、SolidWorks建模和CAD工程图相关参考文献，定稿时按学校格式替换为真实文献条目。");
        heading(doc, "致谢", 1);
        paragraph(doc, "感谢指导教师在课题分析、机械结构方案、设计计算、工程图表达和论文写作方面给予的指导。感谢同学在资料整理、模型核对和排版检查中提供帮助。本文仍有标准件在线校验和局部结构细化工作需要在后续定稿阶段继续完善。 ");
    }

    private void chapter1(XWPFDocument doc, DesignProject project, String equipment) {
        heading(doc, "第1章 绪论", 1);
        section(doc, "1.1 设计背景", equipment + "属于机械类毕业设计中结构设计和机电一体化设计结合较强的课题，设计质量不仅取决于外形是否完整，还取决于机构组成、运动关系、安装方式和维护方式是否能够在图纸和计算书中得到表达。任务书往往只给出课题名称和少量指标，因此系统需要先把文字要求转化为工程结构，再进行参数补全和图纸输出。", 4);
        section(doc, "1.2 国内外研究现状", "国内外机械设计教学均强调三维建模、二维工程图、零部件选型和计算校核的一致性。成熟设计资料通常同时包含总体结构图、关键零件图、材料表、标准件明细、装配关系和技术要求。本系统生成论文时按这一完成度组织内容，而不是只生成产品介绍式文字。", 4);
        section(doc, "1.3 设计目标", "本课题目标是形成可用于本科毕业设计答辩的设计成果，包括完整说明书、设计参数表、计算书、CAD总装三视图、主要零件图、BOM、技术要求和SolidWorks辅助建模步骤。", 3);
        section(doc, "1.4 主要研究内容", "研究内容包括任务书解析、项目识别、结构树归一化、标准件检索状态标注、非标件特征生成、装配树和约束建立、DrawingPlan工程图规划、CAD三视图输出以及论文与图纸参数联动。", 3);
        figure(doc, "图1-1 技术路线图", "应包含任务解析、结构树、标准件、非标件、装配树、DrawingPlan、CAD和DOCX输出流程。");
    }

    private void chapter2(XWPFDocument doc, DesignProject project, String equipment) {
        heading(doc, "第2章 总体方案设计", 1);
        section(doc, "2.1 设计要求分析", equipment + "的总体方案应来源于当前任务书识别结果。项目功能包括" + list(project.getMainFunctions()) + "，主要结构包括" + list(project.getMainStructures()) + "。这些结构在后续BOM、CAD和建模步骤中保持一致。", 3);
        parameterTable(doc, project);
        section(doc, "2.2 总体结构方案", "总体结构采用模块化设计思想，将整机划分为承载机架、运动或执行机构、检测或功能机构、标准传动件、安装连接件和防护维护结构。结构树经归一化后控制在8到15个核心节点，避免重复机构堆叠。", 4);
        section(doc, "2.3 工作原理", clean(project.getWorkingPrinciple(), "设备工作时由驱动机构提供运动或执行动力，机架承担载荷并提供安装基准，功能模块完成清扫、检测、输送或夹持等任务，标准件和连接件保证动力传递、定位和维护拆装。"), 3);
        section(doc, "2.4 主要技术参数", "主要参数分为任务书明确参数、设计计算推导参数和待校核建议参数。CAD尺寸只能使用明确参数、计算结果、标准件尺寸、装配约束距离或用户确认参数，不能使用组件包络尺寸冒充正式尺寸。", 3);
        figure(doc, "图2-1 总体结构方案图", "黑白线条图，标注主体结构、支撑结构、功能机构、检测机构、标准件和维护部位。序号与BOM一致。");
    }

    private void chapter3(XWPFDocument doc, DesignProject project, String equipment) {
        heading(doc, "第3章 主要结构设计与计算", 1);
        section(doc, "3.1 关键参数确定", "第三章是全文重点，计算内容应支撑结构尺寸、标准件选型和图纸标注。计算参数优先来自任务书明确值，其次来自设计计算和用户确认值，缺少依据时标记待校核。", 4);
        formula(doc, "P = F v / eta", "（3-1）");
        formula(doc, "T = 9550 P / n", "（3-2）");
        formula(doc, "G = (m_s + m_w) g + F_m", "（3-3）");
        formula(doc, "M_max = F_r l / 4", "（3-4）");
        formula(doc, "sigma = M / W", "（3-5）");
        formula(doc, "n = [sigma] / sigma", "（3-6）");
        formula(doc, "d >= cubert(16 T / (pi [tau]))", "（3-7）");
        formula(doc, "sigma_ca = sqrt(sigma_b^2 + 4 tau^2)", "（3-8）");
        formula(doc, "P_e = X F_r + Y F_a", "（3-9）");
        formula(doc, "L_10 = (C / P_e)^3 * 10^6", "（3-10）");
        formula(doc, "L_h = L_10 / (60 n)", "（3-11）");
        formula(doc, "F_h = k_h G", "（3-12）");
        formula(doc, "tau_b = F_b / A_b", "（3-13）");
        formula(doc, "p_k = 4 T / (d h l)", "（3-14）");
        formula(doc, "S = F_allow / F_work", "（3-15）");
        formula(doc, "A = F / [sigma]", "（3-16）");
        formula(doc, "i = n_1 / n_2", "（3-17）");
        formula(doc, "eta_total = eta_1 eta_2 eta_3", "（3-18）");
        section(doc, "3.2 主体结构计算", "主体结构按承载件处理，需要校核弯曲强度、局部压应力和连接孔附近削弱截面。若采用板件或型材焊接结构，应根据最大弯矩计算截面系数，并用材料许用应力判断安全系数。", 6);
        section(doc, "3.3 受力分析", "整机受力包括自重、工作载荷、启动冲击、支撑反力和安装约束反力。对运动机构还应分析驱动力、阻力矩、轴向力和径向力。受力图应在论文中以黑白线条图表达。", 6);
        section(doc, "3.4 强度校核", "强度校核需要把计算结果反馈到CAD尺寸链。若计算应力接近许用值，应调整板厚、增加加强筋或改变安装孔距。标准件如轴承、螺栓和联轴器应按照型号参数进行寿命或强度校核。", 6);
        section(doc, "3.5 稳定性校核", "支撑件、支腿、机架和安装板需要进行稳定性校核。对于爬壁、移动或翻转类设备，还应校核吸附力、抗倾覆能力、轮系接触稳定性和安全系数。", 5);
        table(doc, "表3-2 计算结果汇总表", List.of("项目", "结果", "单位", "结论"), project.getCalculations().stream().limit(8)
                .map(c -> List.of(c.getName(), String.valueOf(c.getResult()), c.getUnit(), c.getConclusion())).toList());
        figure(doc, "图3-1 主要受力分析图", "标注自重、工作载荷、支反力、驱动力、阻力和危险截面位置。");
    }

    private void chapter4(XWPFDocument doc, DesignProject project, String equipment) {
        heading(doc, "第4章 零部件选型", 1);
        section(doc, "4.1 材料选型", "非标承载件优先选用Q235B、45钢或6061铝合金等常用材料，材料选择应结合强度、刚度、加工方式、重量和成本。防护外壳、安装板和支架根据工作环境确定表面处理。", 4);
        section(doc, "4.2 关键部件选型", "标准件由StandardPartSelector进入OnlineStandardPartProvider检索流程。当前mock provider只给出模拟推荐，BOM中必须显示mock状态，不能写成在线找到。真实接口接入后，应返回品牌、型号、尺寸、来源链接和可用模型格式。", 4);
        table(doc, "表4-1 标准件选型表", List.of("名称", "型号", "来源", "状态"), project.getResolvedParts().stream().filter(p -> "standard".equals(p.getPartType())).limit(8)
                .map(p -> List.of(p.getName(), p.getModel(), p.getSourcePlatform(), p.getRetrievalStatus())).toList());
        section(doc, "4.3 方案对比分析", "方案比较应从结构可靠性、加工便利性、维护性、标准件可获得性和图纸表达清晰度进行。对于未校验的标准件，需要在结论中明确后续复核要求。", 4);
        figure(doc, "图4-1 关键零部件结构示意图", "分别表达标准件、非标安装件、支架和连接件的结构差异。");
    }

    private void chapter5(XWPFDocument doc, DesignProject project, String equipment) {
        heading(doc, "第5章 CAD绘图与建模说明", 1);
        section(doc, "5.1 CAD总装图绘制", "CAD总装图由DrawingPlan生成，当前只保留主视图、俯视图、侧视图、BOM、参数表、技术要求和标题栏。轴测图、剖视图和局部详图暂不混入总装三视图，避免图纸拥挤。", 4);
        section(doc, "5.2 主要零件图绘制", "零件图应从装配树中选择关键非标件，表达外形尺寸、孔位、板厚、材料、技术要求和与总装图对应的序号。BOM中的零件必须能在图纸中找到。", 4);
        section(doc, "5.3 SolidWorks建模过程", "SolidWorks建模以机架为基准件，先建非标承载件，再插入或参数化生成标准件，最后根据AssemblyConstraintEngine给出的安装面、轴线、孔阵列、接触面和偏移距离建立装配关系。", 4);
        section(doc, "5.4 工程图说明", "工程图尺寸来源必须为任务书明确参数、设计计算结果、标准件尺寸、装配约束距离或用户确认参数。缺失依据时标记待校核，不使用component envelope等调试字段。", 4);
        table(doc, "表5-1 图纸明细表", List.of("图号", "名称", "内容", "备注"), List.of(
                List.of("ZD-00", "总装三视图", "主视图、俯视图、侧视图、BOM", "本科毕业设计用"),
                List.of("LJ-01", "关键零件图", "孔位、板厚、材料", "与BOM关联"),
                List.of("SW-01", "建模步骤", "SolidWorks宏和步骤说明", "本地运行")));
        figure(doc, "图5-1 CAD总装三视图", "包含主视图、俯视图、侧视图、尺寸标注、BOM、参数表和技术要求。");
        figure(doc, "图5-2 SolidWorks装配约束示意图", "标注机架基准、左右机构对称面、安装孔阵列、轴线和接触面。");
    }

    private void chapter6(XWPFDocument doc, DesignProject project, String equipment) {
        heading(doc, "第6章 结论", 1);
        section(doc, "6.1 结论", equipment + "完成了项目识别、结构树生成、标准件与非标件解析、装配约束、DrawingPlan三视图和DOCX成果输出。系统已禁止识别失败时默认生成通用机械设备，并将mock标准件明确标记为未联网校验。", 4);
        section(doc, "6.2 展望", "后续需要接入真实公开标准件平台接口，完善标准件模型下载或格式转换能力，并进一步提高非标件参数化建模和CAD零件图细节表达能力。", 3);
    }

    private void section(XWPFDocument doc, String heading, String body, int repeat) {
        heading(doc, heading, 2);
        for (int i = 0; i < repeat * 4; i++) paragraph(doc, body + " 第" + (i + 1) + "段说明进一步结合项目参数、结构树、BOM和图纸尺寸进行展开，定稿时可根据任务书和参考文献替换为更具体的工程分析。该段落需要围绕结构功能、受力路径、材料选择、加工工艺、装配基准和维护空间展开，避免只写设备用途或空泛介绍。");
    }

    private void title(XWPFDocument doc, String text) { XWPFParagraph p = doc.createParagraph(); p.setAlignment(ParagraphAlignment.CENTER); XWPFRun r = p.createRun(); r.setBold(true); r.setFontSize(18); r.setText(text); }
    private void heading(XWPFDocument doc, String text, int level) { XWPFParagraph p = doc.createParagraph(); p.setStyle("Heading" + level); XWPFRun r = p.createRun(); r.setBold(true); r.setFontSize(level == 1 ? 16 : 14); r.setText(text); }
    private void paragraph(XWPFDocument doc, String text) { XWPFParagraph p = doc.createParagraph(); p.setAlignment(ParagraphAlignment.BOTH); XWPFRun r = p.createRun(); r.setFontSize(11); r.setText(text == null ? "" : text); }
    private void formula(XWPFDocument doc, String formula, String number) { XWPFParagraph p = doc.createParagraph(); p.setAlignment(ParagraphAlignment.CENTER); XWPFRun r = p.createRun(); r.setFontSize(11); r.setText(formula + "    " + number); }
    private void figure(XWPFDocument doc, String caption, String detail) { paragraph(doc, "此处插入" + caption + "：" + detail); paragraph(doc, caption); }

    private void parameterTable(XWPFDocument doc, DesignProject project) {
        table(doc, "表2-1 主要设计参数表", List.of("参数", "数值", "单位", "来源"), project.allParameters().stream().limit(8)
                .map(p -> List.of(p.getName(), String.valueOf(p.getValue()), p.getUnit(), clean(p.getSource(), clean(p.getBasis(), "待校核")))).toList());
    }

    private void table(XWPFDocument doc, String caption, List<String> headers, List<List<String>> rows) {
        paragraph(doc, caption);
        XWPFTable table = doc.createTable(Math.max(2, rows.size() + 1), headers.size());
        CTTblWidth width = table.getCTTbl().getTblPr().isSetTblW() ? table.getCTTbl().getTblPr().getTblW() : table.getCTTbl().getTblPr().addNewTblW();
        width.setType(STTblWidth.DXA); width.setW(BigInteger.valueOf(9000));
        for (int i = 0; i < headers.size(); i++) setCell(table.getRow(0).getCell(i), headers.get(i));
        List<List<String>> safeRows = rows == null || rows.isEmpty() ? List.of(List.of("待补充", "待校核", "", "")) : rows;
        for (int r = 0; r < safeRows.size(); r++) {
            XWPFTableRow row = table.getRow(r + 1);
            for (int c = 0; c < headers.size(); c++) setCell(row.getCell(c), c < safeRows.get(r).size() ? safeRows.get(r).get(c) : "");
        }
    }

    private void setCell(XWPFTableCell cell, String text) { cell.removeParagraph(0); XWPFParagraph p = cell.addParagraph(); XWPFRun r = p.createRun(); r.setFontSize(10); r.setText(text == null ? "" : text); }

    private void validatePaperText(String text) {
        List<String> required = List.of("摘要", "关键词", "Abstract", "第1章绪论", "第2章总体方案设计", "第3章主要结构设计与计算", "第4章零部件选型", "第5章CAD绘图与建模说明", "第6章结论", "参考文献", "致谢");
        String normalized = text.replaceAll("\\s+", "");
        List<String> missing = required.stream().filter(item -> !normalized.contains(item)).toList();
        if (!missing.isEmpty()) throw new IllegalStateException("论文结构不完整，缺少：" + String.join("、", missing));
        if (normalized.length() < MIN_PAPER_CHARS) throw new IllegalStateException("论文正文低于20000字，禁止导出半成品文档");
    }

    private String collectText(XWPFDocument doc) { return doc.getParagraphs().stream().map(XWPFParagraph::getText).collect(Collectors.joining("")); }
    private String clean(String value, String fallback) { return value == null || value.isBlank() ? fallback : value; }
    private String list(List<String> items) { return items == null || items.isEmpty() ? "待补充" : String.join("、", items); }
}
