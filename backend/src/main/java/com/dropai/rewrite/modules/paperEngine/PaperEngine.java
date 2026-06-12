package com.dropai.rewrite.modules.paperEngine;

import com.dropai.rewrite.modules.model.DesignProject;
import org.apache.poi.xwpf.usermodel.ParagraphAlignment;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.util.List;

@Service
public class PaperEngine {
    public byte[] generatePaper(DesignProject project) { return document(project, false); }
    public byte[] generateCalculationBook(DesignProject project) { return document(project, true); }
    public byte[] generateModelingSteps(DesignProject project) {
        try (XWPFDocument doc = new XWPFDocument(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            heading(doc, project.getProjectTitle() + " SolidWorks辅助建模步骤", 20, true);
            paragraph(doc, "建模顺序：壳体 → 底座 → 进出口组件 → 支撑与连接件 → 总装配。");
            paragraph(doc, "1. 运行 sw_macro_shell.bas 新建壳体基础实体，依据参数表补充检修口、折弯或焊接结构。");
            paragraph(doc, "2. 运行 sw_macro_base.bas 新建底座基础实体，补充支撑、安装孔与连接结构。");
            paragraph(doc, "3. 运行 sw_macro_inlet.bas 新建进出口基础实体，依据零件图完善接口尺寸。");
            paragraph(doc, "4. 分别保存零件，按 assembly.dxf 的部件编号和总体尺寸建立装配体。");
            paragraph(doc, "5. 完成材料设置、干涉检查、关键尺寸复核，并与设计计算书和CAD图纸核对。");
            doc.write(output); return output.toByteArray();
        } catch (Exception e) { throw new IllegalStateException("生成建模步骤DOCX失败：" + e.getMessage(), e); }
    }

    private byte[] document(DesignProject project, boolean calculationOnly) {
        try (XWPFDocument doc = new XWPFDocument(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            heading(doc, project.getProjectTitle() + (calculationOnly ? " 设计计算书" : " 毕业设计说明书初稿"), 20, true);
            if (calculationOnly) {
                heading(doc, "第3章 主要结构设计与计算", 16, false);
                writeCalculations(doc, project);
            } else {
                paragraph(doc, "摘要：" + project.getEquipmentName() + "设计围绕任务要求、结构方案、参数计算、工程图与三维建模展开。本文建立统一参数表，使计算结果、CAD图纸和建模步骤保持一致。");
                paragraph(doc, "关键词：机械设计；参数化设计；工程图；SolidWorks");
                paragraph(doc, "Abstract: This undergraduate design develops a parameter-driven mechanical design package including calculation, drawings and modeling guidance.");
                paragraph(doc, "Keywords: mechanical design; parametric design; engineering drawing; SolidWorks");
                chapter(doc, "第1章 绪论", List.of("1.1 设计背景", "1.2 国内外研究现状", "1.3 设计目标", "1.4 主要研究内容"));
                chapter(doc, "第2章 总体方案设计", List.of("2.1 设计要求分析", "2.2 总体结构方案", "2.3 工作原理", "2.4 主要技术参数"));
                paragraph(doc, "表2-1 主要设计参数表");
                project.allParameters().forEach(p -> paragraph(doc, p.getName() + "：" + p.getValue() + " " + p.getUnit() + "；依据：" + source(p)));
                paragraph(doc, "图2-1 总体结构方案图");
                heading(doc, "第3章 主要结构设计与计算", 16, false);
                writeCalculations(doc, project);
                chapter(doc, "第4章 主要零部件选型与对比", List.of("4.1 材料选型", "4.2 关键部件选型", "4.3 方案对比分析"));
                chapter(doc, "第5章 CAD绘图与SolidWorks建模", List.of("5.1 CAD总装图绘制", "5.2 主要零件图绘制", "5.3 SolidWorks建模过程", "5.4 工程图说明"));
                paragraph(doc, "图5-1 总装工程图；图5-2 壳体零件图；图5-3 底座零件图");
                chapter(doc, "第6章 结论与展望", List.of("6.1 结论", "6.2 展望"));
                heading(doc, "参考文献", 16, false); paragraph(doc, "参考文献应根据用户上传资料及学校模板补充，不自动虚构文献条目。");
                heading(doc, "致谢", 16, false); paragraph(doc, "感谢指导教师在课题分析、结构设计与论文撰写过程中给予的指导。");
            }
            doc.write(output);
            return output.toByteArray();
        } catch (Exception e) { throw new IllegalStateException("生成DOCX失败：" + e.getMessage(), e); }
    }

    private void writeCalculations(XWPFDocument doc, DesignProject project) {
        String[] sections = {"3.1 关键参数确定", "3.2 主体结构计算", "3.3 受力分析", "3.4 强度校核", "3.5 稳定性校核"};
        for (int i = 0; i < sections.length; i++) {
            heading(doc, sections[i], 14, false);
            for (DesignProject.Calculation c : project.getCalculations()) {
                paragraph(doc, c.getName() + "：公式 " + c.getFormula() + "；代入 " + c.getSubstitution() + "；结果 " + c.getResult() + " " + c.getUnit() + "。结论：" + c.getConclusion() + "。");
            }
        }
    }
    private void chapter(XWPFDocument doc, String title, List<String> sections) {
        heading(doc, title, 16, false);
        sections.forEach(section -> { heading(doc, section, 14, false); paragraph(doc, "本节依据设计任务、统一参数表及工程校核要求展开，具体内容将在资料确认后进一步深化。"); });
    }
    private void heading(XWPFDocument doc, String text, int size, boolean center) {
        XWPFParagraph p = doc.createParagraph(); if (center) p.setAlignment(ParagraphAlignment.CENTER);
        XWPFRun r = p.createRun(); r.setBold(true); r.setFontSize(size); r.setText(text);
    }
    private void paragraph(XWPFDocument doc, String text) {
        XWPFParagraph p = doc.createParagraph(); p.setIndentationFirstLine(480); p.setSpacingAfter(100); p.createRun().setText(text);
    }
    private String source(DesignProject.Parameter p) { return p.getSource() != null ? p.getSource() : p.getBasis() != null ? p.getBasis() : "待确认"; }
}
