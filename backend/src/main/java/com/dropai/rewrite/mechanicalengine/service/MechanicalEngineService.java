package com.dropai.rewrite.mechanicalengine.service;

import com.dropai.rewrite.auth.AuthContext;
import com.dropai.rewrite.entity.DocumentJobRecord;
import com.dropai.rewrite.mapper.DocumentJobMapper;
import com.dropai.rewrite.mechanicalengine.cad.CadDslService;
import com.dropai.rewrite.mechanicalengine.cad.FreeCadExecutor;
import com.dropai.rewrite.mechanicalengine.cadcore.PartDesignJobGenerator;
import com.dropai.rewrite.mechanicalengine.domain.MechanicalProject;
import com.dropai.rewrite.mechanicalengine.validation.MechanicalArtifactValidator;
import org.springframework.stereotype.Service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.UUID;
import java.util.function.Consumer;
import com.dropai.rewrite.mechanicalengine.domain.MechanicalProgress;

@Service
public class MechanicalEngineService {
    private final MechanicalChiefEngineer chiefEngineer;
    private final CadDslService cadDslService;
    private final PartDesignJobGenerator jobGenerator;
    private final FreeCadExecutor executor;
    private final EngineeringArtifactService artifactService;
    private final MechanicalArtifactValidator validator;
    private final MechanicalPackageBuilder packageBuilder;
    private final MechanicalAnalysisEngine analysisEngine;
    private final DocumentJobMapper mapper;

    public MechanicalEngineService(MechanicalChiefEngineer chiefEngineer, CadDslService cadDslService,
                                   PartDesignJobGenerator jobGenerator, FreeCadExecutor executor,
                                   EngineeringArtifactService artifactService, MechanicalArtifactValidator validator,
                                   MechanicalPackageBuilder packageBuilder, MechanicalAnalysisEngine analysisEngine, DocumentJobMapper mapper) {
        this.chiefEngineer = chiefEngineer; this.cadDslService = cadDslService; this.jobGenerator = jobGenerator;
        this.executor = executor; this.artifactService = artifactService; this.validator = validator;
        this.packageBuilder = packageBuilder; this.mapper = mapper;
        this.analysisEngine = analysisEngine;
    }

    public MechanicalProject execute(String requirementText) {
        return execute(requirementText, AuthContext.requireUserId());
    }

    public MechanicalProject execute(String requirementText, Long userId) {
        return execute(requirementText, userId, progress -> {});
    }

    public MechanicalProject execute(String requirementText, Long userId, Consumer<MechanicalProgress> progress) {
        progress.accept(new MechanicalProgress(5, "REQUIREMENT_ANALYSIS", "Analyzing product requirements"));
        MechanicalProject project = chiefEngineer.design(requirementText);
        progress.accept(new MechanicalProgress(18, "DESIGNING", "Mechanical architecture and part plan completed"));
        project.setAnalysisReport(analysisEngine.analyze(project.getDesignSpec()));
        project.getAnalysis().setMaximumStressMpa(project.getAnalysisReport().estimatedStressMpa());
        project.getAnalysis().setDisplacementMm(project.getAnalysisReport().estimatedDisplacementMm());
        project.getAnalysis().setSafetyFactor(project.getAnalysisReport().safetyFactor());
        project.getAnalysis().setConclusion(project.getAnalysisReport().conclusion());
        pass(project,"PRODUCT_DEFINITION","Product type, purpose, environment, performance goals, and operating conditions identified.");
        pass(project,"FUNCTIONAL_DECOMPOSITION","Function tree created before physical architecture selection.");
        pass(project,"MECHANICAL_ARCHITECTURE","Modules, interfaces, installation methods, load path, and motion path defined.");
        pass(project,"PART_PLANNING","Every part has a function, material, manufacturing process, and CAD feature intent.");
        pass(project,"ENGINEERING_PARAMETERS","Dimensions, load, material, and safety factor generated with engineering reasons.");
        pass(project,"ASSEMBLY_INTENT","Fixed, coincident, slider, and concentric relationships defined without design-stage coordinates.");
        pass(project,"FEATURE_SPEC","Constrained sketches and ordered PartDesign features encoded in FeatureBasedCADSpec.");
        Path workspace;
        try { workspace=Files.createTempDirectory("dropai-cad-"+project.getProjectId()); }
        catch(Exception e){ return fail(project,"WORKSPACE_CREATE_FAILED",e.getMessage()); }

        Path spec=cadDslService.write(project,workspace);
        Path script=jobGenerator.generate(workspace);
        running(project,"FEATURE_EXECUTION","Executing Sketcher and PartDesign feature histories through FreeCADCmd.");
        progress.accept(new MechanicalProgress(30, "CAD_GENERATING", "FeatureBasedCadSpec generated"));
        FreeCadExecutor.ExecutionResult result=executor.execute(script,spec,workspace,
                event -> progress.accept(new MechanicalProgress(event.progress(), event.stage(), event.message())));
        if(!result.success()) return fail(project,result.errorCode(),result.message());
        pass(project,"FEATURE_EXECUTION","FreeCAD PartDesign bodies and editable feature histories generated.");
        pass(project,"ASSEMBLY","Assembly constraint objects created and solved into component placements.");
        pass(project,"STEP_EXPORT","Assembly and per-part STEP files exported from BRep solids.");

        progress.accept(new MechanicalProgress(91, "DRAWING_GENERATING", "Generating drawing PDF and analysis artifacts"));
        artifactService.generate(project,workspace);
        pass(project,"DRAWING_GENERATION","Assembly and part drawings generated in SVG, DXF, and PDF.");
        pass(project,"ENGINEERING_ANALYSIS","Phase-1 rule analysis and stress cloud generated.");
        MechanicalArtifactValidator.ValidationReport validation=validator.validate(project,workspace);
        progress.accept(new MechanicalProgress(96, "VALIDATING", "Validating CAD reality and required artifacts"));
        if(!validation.passed()) return fail(project,"ARTIFACT_VALIDATION_FAILED",String.join("; ",validation.errors()));
        pass(project,"VALIDATION","PartDesign bodies, sketches, feature histories, solved constraints, STEP, and drawings passed reality checks.");

        try {
            persistFile(project,userId,workspace.resolve("01_Model/Assembly.FCStd"),"Assembly.FCStd","MODEL","application/octet-stream");
            persistFile(project,userId,workspace.resolve("02_STEP/Assembly.STEP"),"Assembly.STEP","STEP","application/step");
            persistFile(project,userId,workspace.resolve("02_STEP/Assembly.stl"),"Assembly.stl","MODEL","model/stl");
            persistFile(project,userId,workspace.resolve("03_Drawing/Assembly.svg"),"Assembly.svg","DRAWING","image/svg+xml");
            persistFile(project,userId,workspace.resolve("03_Drawing/Assembly.dxf"),"Assembly.dxf","DRAWING","image/vnd.dxf");
            persistFile(project,userId,workspace.resolve("03_Drawing/Assembly_Drawing.pdf"),"Assembly_Drawing.pdf","DRAWING","application/pdf");
            persistFile(project,userId,workspace.resolve("05_Analysis/stress-cloud.svg"),"stress-cloud.svg","ANALYSIS","image/svg+xml");
            byte[] zip=packageBuilder.build(workspace);
            project.getArtifacts().add(persist(userId,project,"Mechanical_Result.zip","PACKAGE","application/zip",zip));
        } catch(Exception e) { return fail(project,"ARTIFACT_PERSIST_FAILED",e.getMessage()); }
        pass(project,"PACKAGE","Validated mechanical project package completed.");
        progress.accept(new MechanicalProgress(99, "PACKAGING", "Mechanical result package completed"));
        project.setStatus("COMPLETED"); project.setCurrentStage("COMPLETED");
        return project;
    }

    private void persistFile(MechanicalProject project,Long userId,Path path,String name,String category,String mediaType)throws Exception{
        project.getArtifacts().add(persist(userId,project,name,category,mediaType,Files.readAllBytes(path)));
    }
    private MechanicalProject.Artifact persist(Long userId,MechanicalProject project,String name,String category,String mediaType,byte[] content){
        String jobId=project.getProjectId()+"_"+UUID.randomUUID().toString().substring(0,8);
        DocumentJobRecord r=new DocumentJobRecord();
        r.setJobId(jobId); r.setUserId(userId); r.setFileName(name); r.setSourceFeature("MECHANICAL_CAD_ENGINE");
        r.setMode(category.toLowerCase()); r.setModeName(category); r.setPlatform("OPENCASCADE"); r.setPlatformName("FreeCAD/OpenCascade");
        r.setStatus("SUCCESS"); r.setTotalParagraphs(1); r.setProcessedParagraphs(1); r.setRewrittenParagraphs(1);
        r.setMessage("Validated mechanical engineering artifact"); r.setParagraphsJson("[]"); r.setOutputFile(content);
        r.setCreatedAt(LocalDateTime.now()); r.setUpdatedAt(LocalDateTime.now()); mapper.insert(r);
        return new MechanicalProject.Artifact(name,category,mediaType,content.length,"/api/documents/"+jobId+"/download",true);
    }
    private void pass(MechanicalProject p,String stage,String message){p.setCurrentStage(stage);p.getStages().add(new MechanicalProject.StageState(stage,"PASSED",message));}
    private void running(MechanicalProject p,String stage,String message){p.setStatus("RUNNING");p.setCurrentStage(stage);p.getStages().add(new MechanicalProject.StageState(stage,"RUNNING",message));}
    private MechanicalProject fail(MechanicalProject p,String code,String message){p.setStatus("DESIGN_FAILED");p.setFailureCode(code);p.setFailureMessage(message==null?"":message);p.getStages().add(new MechanicalProject.StageState(p.getCurrentStage(),"FAILED",p.getFailureMessage()));return p;}
}
