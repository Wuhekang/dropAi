package com.dropai.rewrite;

import com.dropai.rewrite.mechanicalengine.cad.CadDslService;
import com.dropai.rewrite.mechanicalengine.cad.FreeCadExecutor;
import com.dropai.rewrite.mechanicalengine.cadcore.FeatureInterpreter;
import com.dropai.rewrite.mechanicalengine.cadcore.PartDesignJobGenerator;
import com.dropai.rewrite.mechanicalengine.service.MechanicalChiefEngineer;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Assumptions;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

class FreeCadNativeRuntimeTests {
    @Test
    void simplifiedFivePartClampProducesNativeArtifacts() throws Exception {
        String freeCad = System.getenv("FREECAD_CMD");
        boolean required = Boolean.getBoolean("freecad.required");
        if (freeCad == null || freeCad.isBlank()) {
            assertFalse(required, "FREECAD_CMD is required for native acceptance testing");
            Assumptions.abort("FREECAD_CMD is not configured; native test skipped outside acceptance mode");
        }
        Path workspace = Path.of("target", "freecad-native-clamp-v1-" + System.currentTimeMillis()).toAbsolutePath();
        Files.createDirectories(workspace);
        var project = new MechanicalChiefEngineer().design("automatic clamp");
        assertEquals(5, project.getParts().size());
        var cadDsl = new CadDslService(new ObjectMapper(), new FeatureInterpreter());
        Path spec = cadDsl.write(project, workspace);
        Path script = new PartDesignJobGenerator().generate(workspace);
        long started = System.nanoTime();
        var result = new FreeCadExecutor().execute(script, spec, workspace, event ->
                System.out.printf("NATIVE_PROGRESS %d %s %s%n", event.progress(), event.stage(), event.message()));
        double seconds = (System.nanoTime() - started) / 1_000_000_000.0;
        System.out.printf("NATIVE_WORKSPACE=%s%nNATIVE_RUNTIME_SECONDS=%.3f%n", workspace, seconds);
        assertTrue(result.success(), () -> result.errorCode() + ": " + result.message());
        for (String file : new String[]{
                "01_Model/Assembly.FCStd", "02_STEP/Assembly.STEP", "02_STEP/Assembly.stl",
                "02_STEP/cad-reality-report.json", "03_Drawing/Assembly.svg",
                "03_Drawing/Assembly.dxf", "freecad-runtime-report.json"}) {
            Path artifact = workspace.resolve(file);
            assertTrue(Files.isRegularFile(artifact), file);
            assertTrue(Files.size(artifact) > 0, file);
        }
        for (int i = 1; i <= 5; i++) {
            String number = "P%03d".formatted(i);
            assertTrue(Files.size(workspace.resolve("01_Model/Parts/" + number + ".brep")) > 0);
            assertTrue(Files.size(workspace.resolve("02_STEP/" + number + ".step")) > 0);
            assertTrue(Files.size(workspace.resolve("02_STEP/" + number + ".stl")) > 0);
        }
        Path verifier = workspace.resolve("verify_native_outputs.py");
        Files.writeString(verifier, """
                import FreeCAD as App, Part, json, os
                root=os.environ['DROP_AI_VERIFY_ROOT']
                model=App.openDocument(os.path.join(root,'01_Model','Assembly.FCStd'))
                bodies=[o for o in model.Objects if o.TypeId=='PartDesign::Body' and o.Tip is not None]
                step_doc=App.newDocument('STEP_Verification')
                Part.insert(os.path.join(root,'02_STEP','Assembly.STEP'),step_doc.Name)
                step_doc.recompute()
                shapes=[o.Shape for o in step_doc.Objects if hasattr(o,'Shape') and not o.Shape.isNull()]
                result={'bodies':len(bodies),'stepSolids':sum(len(s.Solids) for s in shapes),'stepVolume':sum(s.Volume for s in shapes)}
                print('DROP_AI_VERIFY|'+json.dumps(result),flush=True)
                """, StandardCharsets.UTF_8);
        ProcessBuilder verifyBuilder = new ProcessBuilder(freeCad, verifier.toString()).redirectErrorStream(true);
        verifyBuilder.environment().put("DROP_AI_VERIFY_ROOT", workspace.toString());
        Process verifyProcess = verifyBuilder.start();
        assertTrue(verifyProcess.waitFor(60, TimeUnit.SECONDS), "FreeCAD output verification timed out");
        String verifyOutput = new String(verifyProcess.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        assertEquals(0, verifyProcess.exitValue(), verifyOutput);
        String marker = verifyOutput.lines().filter(line -> line.startsWith("DROP_AI_VERIFY|")).findFirst()
                .orElseThrow(() -> new AssertionError("Native verification marker missing: " + verifyOutput));
        var verified = new ObjectMapper().readTree(marker.substring("DROP_AI_VERIFY|".length()));
        assertEquals(5, verified.path("bodies").asInt(), marker);
        assertEquals(5, verified.path("stepSolids").asInt(), marker);
        assertTrue(verified.path("stepVolume").asDouble() > 0, marker);
        assertTrue(seconds < 120, "Simplified clamp V1 exceeded two minutes");
    }
}
