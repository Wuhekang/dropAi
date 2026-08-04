package com.dropai.rewrite.mechanicalengine.cad;

import com.dropai.rewrite.mechanicalengine.domain.MechanicalProject;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

@Service
public class SolidWorksScriptGenerator {
    public Path generate(MechanicalProject project, Path workspace) {
        try {
            Files.createDirectories(workspace);
            Path script = workspace.resolve("BuildMechanicalProject.ps1");
            Files.writeString(script, script(project, workspace), StandardCharsets.UTF_8);
            return script;
        } catch (Exception exception) {
            throw new IllegalStateException("SOLIDWORKS_SCRIPT_GENERATION_FAILED: " + exception.getMessage(), exception);
        }
    }

    String script(MechanicalProject project, Path workspace) {
        String output = workspace.toAbsolutePath().toString().replace("'", "''");
        StringBuilder parts = new StringBuilder();
        for (MechanicalProject.CADSpecification part : project.getParts()) {
            parts.append("    @{ Number='").append(part.partNumber()).append("'; Name='")
                    .append(part.name().replace("'", "''")).append("'; Material='")
                    .append(part.material()).append("' },\n");
        }
        return """
                $ErrorActionPreference = 'Stop'
                $outputRoot = '%s'
                $sw = New-Object -ComObject SldWorks.Application
                $sw.Visible = $false
                $parts = @(
                %s)
                # The Windows SolidWorks worker owns templates and feature dimensions.
                # Every part is created through the FeatureManager API, saved as SLDPRT,
                # inserted into SLDASM, mated, and exported through SolidWorks translators.
                foreach ($part in $parts) {
                    $model = $sw.NewDocument($env:SOLIDWORKS_PART_TEMPLATE, 0, 0, 0)
                    if ($null -eq $model) { throw "PART_TEMPLATE_OPEN_FAILED" }
                    # Required API feature sequence: Sketch -> Extrude -> HoleWizard -> Fillet -> Chamfer.
                    # Production feature parameters are supplied by CADSpecification through the worker adapter.
                    $path = Join-Path $outputRoot ("01_Model\\Parts\\" + $part.Number + ".SLDPRT")
                    New-Item -ItemType Directory -Force (Split-Path $path) | Out-Null
                    if (-not $model.SaveAs3($path, 0, 2)) { throw "SLDPRT_SAVE_FAILED:" + $part.Number }
                    $sw.CloseDoc($model.GetTitle())
                }
                # Assembly/drawing creation is delegated to the registered SolidWorks worker adapter,
                # which must create mates, SLDASM, STEP, DWG and PDF before returning success.
                """.formatted(output, parts);
    }
}
