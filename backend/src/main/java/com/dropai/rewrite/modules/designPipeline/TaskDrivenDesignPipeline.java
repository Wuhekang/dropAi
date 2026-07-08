package com.dropai.rewrite.modules.designPipeline;

import com.dropai.rewrite.modules.assemblyBuilder.AssemblyBuilder;
import com.dropai.rewrite.modules.assemblyPlannerAgent.AssemblyPlannerAgent;
import com.dropai.rewrite.modules.bomGenerator.BOMGenerator;
import com.dropai.rewrite.modules.cadFeatureGenerator.CADFeatureGenerator;
import com.dropai.rewrite.modules.calculationEngine.CalculationEngine;
import com.dropai.rewrite.modules.drawingPlanBuilder.DrawingPlanBuilder;
import com.dropai.rewrite.modules.drawingPlannerAgent.DrawingPlannerAgent;
import com.dropai.rewrite.modules.designReferenceAgent.DesignReferenceAgent;
import com.dropai.rewrite.modules.mechanicalDesignAgent.MechanicalDesignAgent;
import com.dropai.rewrite.modules.mechanicalDesignPlanner.MechanicalDesignPlanner;
import com.dropai.rewrite.modules.model.DesignProject;
import com.dropai.rewrite.modules.nonStandardPartGenerator.NonStandardPartGenerator;
import com.dropai.rewrite.modules.partGeneratorAgent.PartGeneratorAgent;
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
    private final DesignReferenceAgent designReferenceAgent;
    private final MechanicalDesignPlanner mechanicalDesignPlanner;
    private final StructureTreeBuilder structureTreeBuilder;
    private final MechanicalDesignAgent mechanicalDesignAgent = new MechanicalDesignAgent();
    private final PartGeneratorAgent partGeneratorAgent = new PartGeneratorAgent();
    private final CADFeatureGenerator cadFeatureGenerator = new CADFeatureGenerator();
    private final AssemblyPlannerAgent assemblyPlannerAgent = new AssemblyPlannerAgent();
    private final DrawingPlannerAgent drawingPlannerAgent = new DrawingPlannerAgent();
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
        this(sessionReset, parameterEngine, projectAnalyzer, new RequirementCompleter(), new DesignReferenceAgent(),
                new MechanicalDesignPlanner(), structureTreeBuilder, standardPartSelector, nonStandardPartGenerator,
                assemblyBuilder, bomGenerator, calculationEngine,
                drawingPlanBuilder);
    }

    @Autowired
    public TaskDrivenDesignPipeline(ProjectSessionReset sessionReset, ParameterEngine parameterEngine,
                                    ProjectAnalyzer projectAnalyzer, RequirementCompleter requirementCompleter,
                                    DesignReferenceAgent designReferenceAgent,
                                    MechanicalDesignPlanner mechanicalDesignPlanner,
                                    StructureTreeBuilder structureTreeBuilder,
                                    StandardPartSelector standardPartSelector, NonStandardPartGenerator nonStandardPartGenerator,
                                    AssemblyBuilder assemblyBuilder, BOMGenerator bomGenerator,
                                    CalculationEngine calculationEngine, DrawingPlanBuilder drawingPlanBuilder) {
        this.sessionReset = sessionReset;
        this.parameterEngine = parameterEngine;
        this.projectAnalyzer = projectAnalyzer;
        this.requirementCompleter = requirementCompleter;
        this.designReferenceAgent = designReferenceAgent;
        this.mechanicalDesignPlanner = mechanicalDesignPlanner;
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
        normalizeDepth(project);
        project = parameterEngine.normalize(project);
        project = projectAnalyzer.analyze(project);
        if (isEngineering(project)) {
            validateEngineeringInput(project);
        } else {
            project = designReferenceAgent.complete(project);
            project = requirementCompleter.complete(project);
        }
        project = projectAnalyzer.analyze(project);
        project = mechanicalDesignPlanner.plan(project);
        project = structureTreeBuilder.build(project);
        project = mechanicalDesignAgent.design(project);
        project = partGeneratorAgent.generate(project, partResolver);
        project = cadFeatureGenerator.generate(project);
        project = assemblyBuilder.build(project);
        project = assemblyPlannerAgent.plan(project);
        project = bomGenerator.generate(project);
        project = calculationEngine.calculate(project);
        project = bomGenerator.generate(project);
        project = drawingPlannerAgent.plan(project);
        project = drawingPlanBuilder.build(project);
        score(project);
        project.getEnhancementNotes().removeIf(item -> item != null && item.contains("任务书驱动结构树流水线"));
        project.getEnhancementNotes().add("任务书驱动结构树流水线：StructureTree + StandardPartSelector + NonStandardPartGenerator + AssemblyTree 已生成当前项目专属结构。");
        return project;
    }

    private void normalizeDepth(DesignProject project) {
        if (project.getDesignDepth() == null || project.getDesignDepth().isBlank() || "normal".equals(project.getDesignDepth())) {
            project.setDesignDepth("graduation");
        }
    }

    private boolean isEngineering(DesignProject project) {
        return "engineering".equalsIgnoreCase(project.getDesignDepth());
    }

    private void validateEngineeringInput(DesignProject project) {
        if (blank(project.getEquipmentName())) throw new IllegalStateException("工程版需要补充完整设备名称后生成。");
        long structures = project.getMainStructures().stream().filter(item -> !blank(item)).count();
        if (structures < 3) throw new IllegalStateException("工程版需要补充完整结构组成后生成。");
        if (project.allParameters().size() < 3) throw new IllegalStateException("工程版需要补充关键设计参数后生成。");
        if (project.getDrawingViews().isEmpty()) throw new IllegalStateException("工程版需要补充完整图纸规划后生成。");
    }

    private boolean blank(String value) {
        return value == null || value.isBlank();
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
