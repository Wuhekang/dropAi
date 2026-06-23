package com.dropai.rewrite.modules.designPipeline;

import com.dropai.rewrite.modules.assemblyBuilder.AssemblyBuilder;
import com.dropai.rewrite.modules.bomGenerator.BOMGenerator;
import com.dropai.rewrite.modules.calculationEngine.CalculationEngine;
import com.dropai.rewrite.modules.drawingPlanBuilder.DrawingPlanBuilder;
import com.dropai.rewrite.modules.model.DesignProject;
import com.dropai.rewrite.modules.nonStandardPartGenerator.NonStandardPartGenerator;
import com.dropai.rewrite.modules.partResolver.PartResolver;
import com.dropai.rewrite.modules.parameterEngine.ParameterEngine;
import com.dropai.rewrite.modules.projectAnalyzer.ProjectAnalyzer;
import com.dropai.rewrite.modules.projectSessionReset.ProjectSessionReset;
import com.dropai.rewrite.modules.requirementCompleter.RequirementCompleter;
import com.dropai.rewrite.modules.standardPartSelector.StandardPartSelector;
import com.dropai.rewrite.modules.structureTreeBuilder.StructureTreeBuilder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class TaskDrivenDesignPipeline {
    private final ProjectSessionReset sessionReset;
    private final ParameterEngine parameterEngine;
    private final ProjectAnalyzer projectAnalyzer;
    private final RequirementCompleter requirementCompleter;
    private final StructureTreeBuilder structureTreeBuilder;
    private final PartResolver partResolver;
    private final AssemblyBuilder assemblyBuilder;
    private final BOMGenerator bomGenerator;
    private final CalculationEngine calculationEngine;
    private final DrawingPlanBuilder drawingPlanBuilder;

    public TaskDrivenDesignPipeline(ProjectSessionReset sessionReset, ParameterEngine parameterEngine,
                                    ProjectAnalyzer projectAnalyzer, StructureTreeBuilder structureTreeBuilder,
                                    StandardPartSelector standardPartSelector, NonStandardPartGenerator nonStandardPartGenerator,
                                    AssemblyBuilder assemblyBuilder, BOMGenerator bomGenerator,
                                    CalculationEngine calculationEngine, DrawingPlanBuilder drawingPlanBuilder) {
        this(sessionReset, parameterEngine, projectAnalyzer, new RequirementCompleter(), structureTreeBuilder,
                standardPartSelector, nonStandardPartGenerator, assemblyBuilder, bomGenerator, calculationEngine,
                drawingPlanBuilder);
    }

    @Autowired
    public TaskDrivenDesignPipeline(ProjectSessionReset sessionReset, ParameterEngine parameterEngine,
                                    ProjectAnalyzer projectAnalyzer, RequirementCompleter requirementCompleter,
                                    StructureTreeBuilder structureTreeBuilder,
                                    StandardPartSelector standardPartSelector, NonStandardPartGenerator nonStandardPartGenerator,
                                    AssemblyBuilder assemblyBuilder, BOMGenerator bomGenerator,
                                    CalculationEngine calculationEngine, DrawingPlanBuilder drawingPlanBuilder) {
        this.sessionReset = sessionReset;
        this.parameterEngine = parameterEngine;
        this.projectAnalyzer = projectAnalyzer;
        this.requirementCompleter = requirementCompleter;
        this.structureTreeBuilder = structureTreeBuilder;
        this.partResolver = new PartResolver(standardPartSelector, nonStandardPartGenerator);
        this.assemblyBuilder = assemblyBuilder;
        this.bomGenerator = bomGenerator;
        this.calculationEngine = calculationEngine;
        this.drawingPlanBuilder = drawingPlanBuilder;
    }

    public DesignProject analyzeNewTask(DesignProject project) {
        return run(sessionReset.resetForNewTask(project));
    }

    public DesignProject generateCurrentTask(DesignProject project) {
        return run(sessionReset.ensureCurrentSession(project));
    }

    private DesignProject run(DesignProject project) {
        project = parameterEngine.normalize(project);
        project = projectAnalyzer.analyze(project);
        project = requirementCompleter.complete(project);
        project = projectAnalyzer.analyze(project);
        project = structureTreeBuilder.build(project);
        project = partResolver.resolve(project);
        project = assemblyBuilder.build(project);
        project = bomGenerator.generate(project);
        project = calculationEngine.calculate(project);
        project = bomGenerator.generate(project);
        project = drawingPlanBuilder.build(project);
        score(project);
        project.getEnhancementNotes().removeIf(item -> item != null && item.contains("任务书驱动结构树流水线"));
        project.getEnhancementNotes().add("任务书驱动结构树流水线：StructureTree + StandardPartSelector + NonStandardPartGenerator + AssemblyTree 已生成当前项目专属结构。");
        return project;
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
