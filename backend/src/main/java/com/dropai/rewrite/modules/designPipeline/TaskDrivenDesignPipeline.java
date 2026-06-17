package com.dropai.rewrite.modules.designPipeline;

import com.dropai.rewrite.modules.assemblyBuilder.AssemblyBuilder;
import com.dropai.rewrite.modules.bomGenerator.BOMGenerator;
import com.dropai.rewrite.modules.calculationEngine.CalculationEngine;
import com.dropai.rewrite.modules.model.DesignProject;
import com.dropai.rewrite.modules.parameterEngine.ParameterEngine;
import com.dropai.rewrite.modules.projectAnalyzer.ProjectAnalyzer;
import com.dropai.rewrite.modules.projectSessionReset.ProjectSessionReset;
import com.dropai.rewrite.modules.standardPartLibrary.StandardPartLibrary;
import com.dropai.rewrite.modules.structureTreeBuilder.StructureTreeBuilder;
import com.dropai.rewrite.modules.unknownPartResolver.UnknownPartResolver;
import org.springframework.stereotype.Service;

@Service
public class TaskDrivenDesignPipeline {
    private final ProjectSessionReset sessionReset;
    private final ParameterEngine parameterEngine;
    private final ProjectAnalyzer projectAnalyzer;
    private final StructureTreeBuilder structureTreeBuilder;
    private final StandardPartLibrary standardPartLibrary;
    private final UnknownPartResolver unknownPartResolver;
    private final AssemblyBuilder assemblyBuilder;
    private final BOMGenerator bomGenerator;
    private final CalculationEngine calculationEngine;

    public TaskDrivenDesignPipeline(ProjectSessionReset sessionReset, ParameterEngine parameterEngine,
                                    ProjectAnalyzer projectAnalyzer, StructureTreeBuilder structureTreeBuilder,
                                    StandardPartLibrary standardPartLibrary, UnknownPartResolver unknownPartResolver,
                                    AssemblyBuilder assemblyBuilder, BOMGenerator bomGenerator,
                                    CalculationEngine calculationEngine) {
        this.sessionReset = sessionReset;
        this.parameterEngine = parameterEngine;
        this.projectAnalyzer = projectAnalyzer;
        this.structureTreeBuilder = structureTreeBuilder;
        this.standardPartLibrary = standardPartLibrary;
        this.unknownPartResolver = unknownPartResolver;
        this.assemblyBuilder = assemblyBuilder;
        this.bomGenerator = bomGenerator;
        this.calculationEngine = calculationEngine;
    }

    public DesignProject analyzeNewTask(DesignProject project) {
        return run(sessionReset.resetForNewTask(project));
    }

    public DesignProject generateCurrentTask(DesignProject project) {
        return run(sessionReset.ensureCurrentSession(project));
    }

    private DesignProject run(DesignProject project) {
        project = parameterEngine.normalize(project);
        ensureMinimumParameters(project);
        project = projectAnalyzer.analyze(project);
        project = structureTreeBuilder.build(project);
        project = standardPartLibrary.resolve(project);
        project = unknownPartResolver.resolve(project);
        project = assemblyBuilder.build(project);
        project = bomGenerator.generate(project);
        project = calculationEngine.calculate(project);
        project = bomGenerator.generate(project);
        score(project);
        project.getEnhancementNotes().removeIf(item -> item != null && item.contains("任务书驱动结构树流水线"));
        project.getEnhancementNotes().add("任务书驱动结构树流水线：StructureTree + StandardPartLibrary + UnknownPartResolver + AssemblyTree 已生成当前项目专属结构。");
        return project;
    }

    private void ensureMinimumParameters(DesignProject project) {
        addSuggested(project, "总长", project.number("整机长度", 4200), "mm", "任务书未给出总长时按毕业设计方案阶段估算");
        addSuggested(project, "总宽", project.number("整机宽度", 1600), "mm", "任务书未给出总宽时按总体布置估算");
        addSuggested(project, "总高", project.number("整机高度", 1800), "mm", "任务书未给出总高时按装配高度估算");
        addSuggested(project, "设计载荷", 1200, "kg", "任务书未给出载荷时按中小型机械结构方案载荷估算");
        addSuggested(project, "安全系数", 1.8, "", "按本科机械设计方案阶段强度校核取值");
        addSuggested(project, "材料", "Q235B/6061铝合金", "", "按机械结构常用材料初选");
    }

    private void addSuggested(DesignProject project, String name, Object value, String unit, String basis) {
        if (project.allParameters().stream().noneMatch(item -> name.equals(item.getName()))) {
            project.getSuggestedParameters().add(new DesignProject.Parameter(name, value, unit, null, basis));
        }
    }

    private void score(DesignProject project) {
        int partCount = project.getComponents().stream().mapToInt(c -> Math.max(1, c.getQuantity())).sum();
        int featureCount = project.getResolvedParts().stream().mapToInt(p -> Math.max(1, p.getGeometryFeatures().size())).sum();
        int detailScore = Math.min(100, 20 + Math.min(30, project.getComponents().size() * 2)
                + Math.min(25, featureCount / 2) + Math.min(15, project.getCalculations().size() * 2)
                + Math.min(10, project.getDimensionChains().size()));
        project.setPartCount(partCount);
        project.setFeatureCount(featureCount);
        project.setDetailScore(detailScore);
    }
}
