package com.dropai.rewrite;

import com.dropai.rewrite.mechanicalengine.cad.CadDslService;
import com.dropai.rewrite.mechanicalengine.cad.FreeCadJobGenerator;
import com.dropai.rewrite.mechanicalengine.domain.MechanicalProject;
import com.dropai.rewrite.mechanicalengine.service.MechanicalChiefEngineer;
import com.dropai.rewrite.mechanicalengine.service.MechanicalPackageBuilder;
import com.dropai.rewrite.mechanicalengine.validation.MechanicalArtifactValidator;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import java.util.zip.ZipInputStream;

import static org.junit.jupiter.api.Assertions.*;

class MechanicalEngineRewriteTests {
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void clampDemoProducesIntentDrivenCadDslAndAssemblyConstraints() throws Exception {
        MechanicalProject project = new MechanicalChiefEngineer().design("设计一个可调自动夹具");
        assertEquals("参数化丝杆自动夹具", project.getProductName());
        assertEquals(5, project.getParts().size());
        assertTrue(project.getParts().stream().allMatch(p -> !p.purpose().isBlank() && !p.manufacturing().isBlank()));
        assertTrue(project.getParts().stream().flatMap(p -> p.features().stream()).anyMatch(f -> "hole_pattern".equals(f.type())));
        assertTrue(project.getParts().stream().flatMap(p -> p.features().stream()).anyMatch(f -> "revolve".equals(f.type())));
        assertEquals(project.getAssembly().getComponents().size(), project.getAssembly().getConstraints().size());
        assertTrue(project.getParameters().stream().allMatch(p -> !p.reason().isBlank()));
        Path root=Files.createTempDirectory("cad-dsl-");
        Path spec=new CadDslService(mapper).write(project,root);
        assertEquals(5,mapper.readTree(spec.toFile()).path("parts").size());
        assertTrue(Files.readString(new FreeCadJobGenerator().generate(root)).contains("OpenCascade"));
    }

    @Test
    void validatorRejectsFakeOrEmptyArtifacts() throws Exception {
        Path root=Files.createTempDirectory("cad-invalid-");
        MechanicalProject project=new MechanicalChiefEngineer().design("自动夹具");
        Files.createDirectories(root.resolve("01_Model/Parts"));
        Files.createDirectories(root.resolve("02_STEP"));
        Files.writeString(root.resolve("01_Model/Assembly.FCStd"),"fake");
        for(var part:project.getParts()) Files.writeString(root.resolve("01_Model/Parts/"+part.partNumber()+".brep"),"box");
        Files.writeString(root.resolve("02_STEP/Assembly.STEP"),"fake step");
        var report=new MechanicalArtifactValidator(mapper).validate(project,root);
        assertFalse(report.passed());
        assertTrue(report.errors().stream().anyMatch(e->e.contains("not an OpenCascade BRep")));
        assertTrue(report.errors().stream().anyMatch(e->e.contains("assembly STEP is incomplete")));
    }

    @Test
    void packageContainsOnlyFiveUserFacingEngineeringFolders() throws Exception {
        Path root=Files.createTempDirectory("cad-package-");
        Set<String> expected=new HashSet<>();
        for(String directory:new String[]{"01_Model","02_STEP","03_Drawing","04_Document","05_Analysis"}){
            Files.createDirectories(root.resolve(directory)); Files.writeString(root.resolve(directory+"/artifact.bin"),directory);
            expected.add(directory+"/artifact.bin");
        }
        Files.writeString(root.resolve("cad-model-spec.json"),"internal");
        byte[] zip=new MechanicalPackageBuilder().build(root); Set<String> names=new HashSet<>();
        try(ZipInputStream input=new ZipInputStream(new ByteArrayInputStream(zip))){for(var entry=input.getNextEntry();entry!=null;entry=input.getNextEntry())names.add(entry.getName());}
        assertEquals(expected,names);
    }
}
