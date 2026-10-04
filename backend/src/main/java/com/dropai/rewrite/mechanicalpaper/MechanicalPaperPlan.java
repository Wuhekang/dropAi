package com.dropai.rewrite.mechanicalpaper;

import java.util.List;

/** Fixed placement contract; titles and geometry are planned for the actual topic. */
public final class MechanicalPaperPlan {
    private MechanicalPaperPlan() {}
    public record Section(String id, int chapter, String title, int words) {}
    public record Figure(String id, String section, String kind, String caption) {}
    public static final List<String> CHAPTERS = List.of("绪论", "总体方案与结构设计", "主要参数与设计计算", "主要零件设计", "装配表达与结构分析", "总结与展望");
    public static final List<Section> SECTIONS = List.of(
        new Section("1.1",1,"设计背景与应用需求",580), new Section("1.2",1,"设计目标与研究内容",500),
        new Section("2.1",2,"工况参数与总体方案",650), new Section("2.2",2,"结构组成与工作原理",650),
        new Section("3.1",3,"载荷与关键参数计算",850), new Section("3.2",3,"强度、刚度与精度校核",850),
        new Section("4.1",4,"承载与连接零件设计",650), new Section("4.2",4,"功能与定位零件设计",650),
        new Section("5.1",5,"三维总装与装配关系",420), new Section("5.2",5,"二维总装与尺寸关联",420),
        new Section("5.3",5,"网格划分与载荷约束",450), new Section("5.4",5,"关键承载件应力与位移分析",450),
        new Section("5.5",5,"关键功能件应力与位移分析",450), new Section("6.1",6,"设计总结与改进方向",430));
    public static final List<Figure> FIGURES = List.of(
        new Figure("1-1","1.1","photo","应用实物图"),
        new Figure("2-1","2.2","structure","总体结构图"), new Figure("2-2","2.2","assembly","总体装配结构"),
        new Figure("3-1","3.1","force","载荷与结构计算图"),
        new Figure("4-1","4.1","part","主要承载件零件图"), new Figure("4-2","4.1","part","连接件零件图"),
        new Figure("4-3","4.2","part","关键功能件零件图"), new Figure("4-4","4.2","part","定位或传动件零件图"),
        new Figure("5-1","5.1","assembly","三维总装效果"), new Figure("5-2","5.2","drawing","二维总装结构"),
        new Figure("5-3","5.3","mesh","关键承载件网格划分"),
        new Figure("5-4","5.4","stress","关键承载件应力分布"), new Figure("5-5","5.4","displacement","关键承载件位移分布"),
        new Figure("5-6","5.5","stress","关键功能件应力分布"), new Figure("5-7","5.5","displacement","关键功能件位移分布"));
    public static int wordCount(String text) { return text == null ? 0 : (int) text.codePoints().filter(Character::isLetterOrDigit).count(); }
}
