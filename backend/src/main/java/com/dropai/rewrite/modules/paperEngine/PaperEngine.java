package com.dropai.rewrite.modules.paperEngine;

import com.dropai.rewrite.modules.model.DesignProject;
import org.apache.poi.xwpf.usermodel.ParagraphAlignment;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class PaperEngine {
    private static final int MIN_PAPER_CHARS = 8000;
    private static final List<String> REQUIRED_HEADINGS = List.of(
            "摘要", "关键词", "Abstract", "Keywords",
            "第1章 绪论", "第2章 总体方案设计", "第3章 主要结构设计与计算",
            "第4章 主要零部件选型与对比", "第5章 CAD绘图与SolidWorks建模",
            "第6章 结论与展望", "参考文献", "致谢");

    public byte[] generatePaper(DesignProject project) {
        List<Block> blocks = buildPaper(project);
        validatePaper(blocks);
        return write(project.getProjectTitle() + " 设计说明书", blocks);
    }

    public byte[] generateCalculationBook(DesignProject project) {
        List<Block> blocks = new ArrayList<>();
        blocks.add(new Block("第3章 主要结构设计与计算", true));
        addCalculationSections(blocks, project);
        return write(project.getProjectTitle() + " 设计计算书", blocks);
    }

    public byte[] generateModelingSteps(DesignProject project) {
        List<Block> blocks = new ArrayList<>();
        blocks.add(new Block("SolidWorks辅助建模步骤", true));
        blocks.add(new Block("建模顺序：壳体、底座、进出口组件、支撑与连接件、总装配。", false));
        blocks.add(new Block("依次运行成果包中的零件宏，生成基础实体后按照零件图补充孔、支撑、检修口和连接结构。", false));
        blocks.add(new Block("分别保存零件并按总装图建立装配体，完成材料设置、干涉检查和关键尺寸复核。", false));
        return write(project.getProjectTitle() + " SolidWorks辅助建模步骤", blocks);
    }

    private List<Block> buildPaper(DesignProject project) {
        List<Block> b = new ArrayList<>();
        b.add(new Block("摘要", true));
        b.add(new Block(summary(project), false));
        b.add(new Block("关键词：机械设计；参数化设计；工程计算；CAD工程图；SolidWorks", false));
        b.add(new Block("Abstract", true));
        b.add(new Block("This undergraduate design establishes a parameter-driven workflow for mechanical scheme analysis, engineering calculation, CAD drawing and SolidWorks-assisted modeling. The calculation book, drawings and modeling instructions share the same confirmed design parameters.", false));
        b.add(new Block("Keywords: mechanical design; parametric design; engineering calculation; CAD; SolidWorks", false));

        addChapter(b, project, "第1章 绪论", List.of("1.1 设计背景", "1.2 国内外研究现状", "1.3 设计目标", "1.4 主要研究内容"));
        addChapter(b, project, "第2章 总体方案设计", List.of("2.1 设计要求分析", "2.2 总体结构方案", "2.3 工作原理", "2.4 主要技术参数"));
        b.add(new Block("表2-1 结构组成与功能分配", true));
        project.getComponents().forEach(component -> b.add(new Block(
                component.getSequence() + " " + component.getName() + "；功能：" + component.getFunction()
                        + "；材料：" + component.getMaterial() + "；数量：" + component.getQuantity(), false)));
        b.add(new Block("表2-1 主要设计参数表", true));
        project.allParameters().forEach(p -> b.add(new Block(p.getName() + "：" + p.getValue() + " " + p.getUnit() + "；依据：" + source(p), false)));
        b.add(new Block("图2-1 总体结构方案图", false));

        b.add(new Block("第3章 主要结构设计与计算", true));
        addCalculationSections(b, project);
        addChapter(b, project, "第4章 主要零部件选型与对比", List.of("4.1 材料选型", "4.2 关键部件选型", "4.3 方案对比分析"));
        b.add(new Block("表4-1 总装图零部件明细表（BOM）", true));
        project.getBom().forEach(item -> b.add(new Block(
                item.getSequence() + " " + item.getName() + "；材料：" + item.getMaterial()
                        + "；数量：" + item.getQuantity() + "；备注：" + item.getRemark(), false)));
        addChapter(b, project, "第5章 CAD绘图与SolidWorks建模", List.of("5.1 CAD总装图绘制", "5.2 主要零件图绘制", "5.3 SolidWorks建模过程", "5.4 工程图说明"));
        b.add(new Block("图5-1 总装工程图；图5-2 壳体零件图；图5-3 底座零件图", false));
        addChapter(b, project, "第6章 结论与展望", List.of("6.1 结论", "6.2 展望"));
        b.add(new Block("参考文献", true));
        b.add(new Block("[1] 机械设计相关教材与设计手册，具体版本按学校模板和用户上传文献核对后填写。", false));
        b.add(new Block("[2] 与课题设备类型相关的论文和技术资料，必须由设计人员核验来源后列入正式稿。", false));
        b.add(new Block("致谢", true));
        b.add(new Block("感谢指导教师在课题分析、参数确定、结构设计、工程校核和论文撰写过程中给予的指导。感谢参与资料整理、方案讨论和图纸复核的老师与同学。", false));
        return b;
    }

    private void addChapter(List<Block> blocks, DesignProject project, String chapter, List<String> sections) {
        blocks.add(new Block(chapter, true));
        for (String section : sections) {
            blocks.add(new Block(section, true));
            blocks.add(new Block(sectionParagraph(project, section, 1), false));
            blocks.add(new Block(sectionParagraph(project, section, 2), false));
        }
    }

    private void addCalculationSections(List<Block> blocks, DesignProject project) {
        for (String section : List.of("3.1 关键参数确定", "3.2 主体结构计算", "3.3 受力分析", "3.4 强度校核", "3.5 稳定性校核")) {
            blocks.add(new Block(section, true));
            blocks.add(new Block(sectionParagraph(project, section, 1), false));
            for (DesignProject.Calculation c : project.getCalculations()) {
                blocks.add(new Block(c.getName() + "：公式 " + c.getFormula() + "；代入 " + c.getSubstitution()
                        + "；计算结果 " + c.getResult() + " " + c.getUnit() + "。校核结论：" + c.getConclusion() + "。", false));
            }
            blocks.add(new Block(sectionParagraph(project, section, 2), false));
        }
    }

    private String sectionParagraph(DesignProject project, String section, int variant) {
        String parameters = project.allParameters().stream().limit(8)
                .map(p -> p.getName() + "=" + p.getValue() + p.getUnit()).collect(Collectors.joining("，"));
        String checks = String.join("、", project.getVerificationItems());
        String structure = project.getComponents().stream().limit(8)
                .map(DesignProject.Component::getName).collect(Collectors.joining("、"));
        String first = "本节围绕“" + section + "”展开。设计对象为" + project.getEquipmentName()
                + "，统一参数表中的主要输入为" + parameters + "。方案阶段首先区分任务资料明确给出的参数、依据工程关系推导的参数和需要设计人员确认的建议参数，"
                + "再将同一组数值用于结构计算、总装图、零件图和三维建模说明，避免论文描述与图纸尺寸不一致。结构方案由" + structure + "组成。";
        String second = "在具体设计过程中，需要结合制造、装配、运输、使用和维护条件，对结构尺寸、材料、连接方式和安全裕量进行综合判断。"
                + "当前成果用于本科毕业设计初稿和方案校核，重点复核项目包括" + checks + "。所有未经任务书或可靠资料确认的内容均应在正式定稿前完成工程复核，"
                + "并将复核结论同步回写到参数表、计算书和CAD图纸。";
        String extension = " 为保证说明书具备可追溯性，相关选择均需说明输入条件、计算依据、结果含义及适用范围；当输入参数发生修改时，应重新执行计算并检查受影响的图纸尺寸、技术要求和建模步骤。";
        return (variant == 1 ? first : second) + extension + extension;
    }

    private String summary(DesignProject project) {
        return "本设计以" + project.getProjectTitle() + "为课题，围绕任务资料解析、设计目标识别、参数分类、工程计算、结构方案、CAD工程图及SolidWorks辅助建模开展工作。"
                + "设计过程采用统一参数模型管理明确参数、推导参数和建议参数，计算结果直接用于图纸尺寸与建模步骤，从而保证论文、计算书和工程图之间的一致性。"
                + "在方案阶段完成主体结构受力、强度和稳定性初步校核，并形成总装图、主要零件图和可执行的建模宏。成果仍需结合学校模板、真实参考文献及指导教师意见进一步复核完善。";
    }

    private void validatePaper(List<Block> blocks) {
        String text = blocks.stream().map(Block::text).collect(Collectors.joining("\n"));
        List<String> missing = REQUIRED_HEADINGS.stream().filter(heading -> !text.contains(heading)).toList();
        if (!missing.isEmpty()) throw new IllegalStateException("论文结构不完整，缺少：" + String.join("、", missing));
        long chapters = blocks.stream().filter(Block::heading).filter(block -> block.text().matches("第[1-6]章.*")).count();
        if (chapters < 6) throw new IllegalStateException("论文未生成完整六章，禁止导出");
        if (text.replaceAll("\\s+", "").length() < MIN_PAPER_CHARS) {
            throw new IllegalStateException("论文正文不足8000字，禁止导出半成品文档");
        }
    }

    private byte[] write(String title, List<Block> blocks) {
        try (XWPFDocument doc = new XWPFDocument(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            heading(doc, title, 20, true);
            for (Block block : blocks) {
                if (block.heading()) heading(doc, block.text(), block.text().matches("第[1-6]章.*") ? 16 : 14, false);
                else paragraph(doc, block.text());
            }
            doc.write(output);
            if (output.size() == 0) throw new IllegalStateException("DOCX文件为空");
            return output.toByteArray();
        } catch (Exception e) { throw new IllegalStateException("生成DOCX失败：" + e.getMessage(), e); }
    }

    private void heading(XWPFDocument doc, String text, int size, boolean center) {
        XWPFParagraph p = doc.createParagraph(); if (center) p.setAlignment(ParagraphAlignment.CENTER);
        XWPFRun r = p.createRun(); r.setBold(true); r.setFontSize(size); r.setText(text);
    }
    private void paragraph(XWPFDocument doc, String text) {
        XWPFParagraph p = doc.createParagraph(); p.setIndentationFirstLine(480); p.setSpacingAfter(100); p.createRun().setText(text);
    }
    private String source(DesignProject.Parameter p) { return p.getSource() != null ? p.getSource() : p.getBasis() != null ? p.getBasis() : "待确认"; }
    private record Block(String text, boolean heading) {}
}
