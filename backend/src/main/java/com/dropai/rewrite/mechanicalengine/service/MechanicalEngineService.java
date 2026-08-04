package com.dropai.rewrite.mechanicalengine.service;

import com.dropai.rewrite.auth.AuthContext;
import com.dropai.rewrite.entity.DocumentJobRecord;
import com.dropai.rewrite.mapper.DocumentJobMapper;
import com.dropai.rewrite.mechanicalengine.cad.CadDslService;
import com.dropai.rewrite.mechanicalengine.cad.FreeCadExecutor;
import com.dropai.rewrite.mechanicalengine.cad.FreeCadJobGenerator;
import com.dropai.rewrite.mechanicalengine.domain.MechanicalProject;
import com.dropai.rewrite.mechanicalengine.validation.MechanicalArtifactValidator;
import org.springframework.stereotype.Service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.UUID;

@Service
public class MechanicalEngineService {
    private final MechanicalChiefEngineer chiefEngineer;
    private final CadDslService cadDslService;
    private final FreeCadJobGenerator jobGenerator;
    private final FreeCadExecutor executor;
    private final EngineeringArtifactService artifactService;
    private final MechanicalArtifactValidator validator;
    private final MechanicalPackageBuilder packageBuilder;
    private final DocumentJobMapper mapper;

    public MechanicalEngineService(MechanicalChiefEngineer chiefEngineer, CadDslService cadDslService,
                                   FreeCadJobGenerator jobGenerator, FreeCadExecutor executor,
                                   EngineeringArtifactService artifactService, MechanicalArtifactValidator validator,
                                   MechanicalPackageBuilder packageBuilder, DocumentJobMapper mapper) {
        this.chiefEngineer = chiefEngineer; this.cadDslService = cadDslService; this.jobGenerator = jobGenerator;
        this.executor = executor; this.artifactService = artifactService; this.validator = validator;
        this.packageBuilder = packageBuilder; this.mapper = mapper;
    }

    public MechanicalProject execute(String requirementText) {
        MechanicalProject project = chiefEngineer.design(requirementText);
        pass(project,"REQUIREMENT_UNDERSTANDING","Product, usage, and functions extracted without copying task-book dimensions.");
        pass(project,"CONCEPT_DESIGN","Three concepts compared; the highest-scoring feasible architecture was selected.");
        pass(project,"PARAMETER_GENERATION","Dimensions, load, material, and safety factor generated with reasons.");
        pass(project,"MECHANICAL_ARCHITECTURE","Functional modules and meaningful parts defined.");
        pass(project,"CAD_DSL","Part intents, BRep features, materials, processes, and constraints encoded in CAD DSL.");
        Path workspace;
        try { workspace=Files.createTempDirectory("dropai-cad-"+project.getProjectId()); }
        catch(Exception e){ return fail(project,"WORKSPACE_CREATE_FAILED",e.getMessage()); }

        Path spec=cadDslService.write(project,workspace);
        Path script=jobGenerator.generate(workspace);
        running(project,"BREP_GENERATION","Generating parameterized OpenCascade BRep solids through FreeCADCmd.");
        FreeCadExecutor.ExecutionResult result=executor.execute(script,spec,workspace);
        if(!result.success()) return fail(project,result.errorCode(),result.message());
        pass(project,"BREP_GENERATION","OpenCascade BRep parts and assembly generated.");
        pass(project,"ASSEMBLY","Placements and mechanical constraints applied to the assembly.");
        pass(project,"STEP_EXPORT","Assembly and per-part STEP files exported from BRep solids.");

        artifactService.generate(project,workspace);
        pass(project,"DRAWING_GENERATION","Assembly and part drawings generated in SVG, DXF, and PDF.");
        pass(project,"ENGINEERING_ANALYSIS","Phase-1 rule analysis and stress cloud generated.");
        pass(project,"DOCUMENTATION","Design report generated and bound to project data.");
        MechanicalArtifactValidator.ValidationReport validation=validator.validate(project,workspace);
        if(!validation.passed()) return fail(project,"ARTIFACT_VALIDATION_FAILED",String.join("; ",validation.errors()));
        pass(project,"VALIDATION","BRep volume, feature diversity, STEP, assembly, drawing, analysis, and document checks passed.");

        try {
            persistFile(project,workspace.resolve("02_STEP/Assembly.stl"),"Assembly.stl","MODEL","model/stl");
            persistFile(project,workspace.resolve("03_Drawing/Assembly.svg"),"Assembly.svg","DRAWING","image/svg+xml");
            persistFile(project,workspace.resolve("05_Analysis/stress-cloud.svg"),"stress-cloud.svg","ANALYSIS","image/svg+xml");
            persistFile(project,workspace.resolve("04_Document/Design_Report.pdf"),"Design_Report.pdf","DOCUMENT","application/pdf");
            byte[] zip=packageBuilder.build(workspace);
            project.getArtifacts().add(persist(project,"Mechanical_Project.zip","PACKAGE","application/zip",zip));
        } catch(Exception e) { return fail(project,"ARTIFACT_PERSIST_FAILED",e.getMessage()); }
        pass(project,"PACKAGE","Validated mechanical project package completed.");
        project.setStatus("COMPLETED"); project.setCurrentStage("COMPLETED");
        return project;
    }

    private void persistFile(MechanicalProject project,Path path,String name,String category,String mediaType)throws Exception{
        project.getArtifacts().add(persist(project,name,category,mediaType,Files.readAllBytes(path)));
    }
    private MechanicalProject.Artifact persist(MechanicalProject project,String name,String category,String mediaType,byte[] content){
        String jobId=project.getProjectId()+"_"+UUID.randomUUID().toString().substring(0,8);
        DocumentJobRecord r=new DocumentJobRecord();
        r.setJobId(jobId); r.setUserId(AuthContext.requireUserId()); r.setFileName(name); r.setSourceFeature("MECHANICAL_CAD_ENGINE");
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
