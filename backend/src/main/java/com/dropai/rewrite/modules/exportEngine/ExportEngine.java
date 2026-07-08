package com.dropai.rewrite.modules.exportEngine;

import com.dropai.rewrite.modules.drawingEngine.DrawingArtifact;
import com.dropai.rewrite.modules.model.DesignProject;
import com.dropai.rewrite.modules.modelQualityGate.ModelQualityGate;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@Service
public class ExportEngine {
    private final ObjectMapper objectMapper;
    private final ModelQualityGate modelQualityGate;
    public ExportEngine() { this(new ObjectMapper(), new ModelQualityGate()); }
    public ExportEngine(ObjectMapper objectMapper) { this(objectMapper, new ModelQualityGate()); }
    public ExportEngine(ObjectMapper objectMapper, ModelQualityGate modelQualityGate) {
        this.objectMapper = objectMapper;
        this.modelQualityGate = modelQualityGate;
    }

    public List<DrawingArtifact> appendManifests(DesignProject project, List<DrawingArtifact> artifacts) {
        List<DrawingArtifact> result = new ArrayList<>(artifacts);
        try {
            result.add(new DrawingArtifact("design_parameters.json", objectMapper.writerWithDefaultPrettyPrinter().writeValueAsBytes(project), "application/json"));
        } catch (Exception e) { throw new IllegalStateException("生成参数JSON失败", e); }
        result.add(new DrawingArtifact("preview.pdf", simplePdf(project.getProjectTitle()), "application/pdf"));
        return result;
    }

    public byte[] model3d(DesignProject project) {
        try {
            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsBytes(java.util.Map.of(
                    "projectTitle", project.getProjectTitle(),
                    "equipmentName", project.getEquipmentName(),
                    "designType", project.getDesignType(),
                    "structureTree", project.getStructureTree(),
                    "components", project.getComponents(),
                    "assemblyTree", project.getAssemblyTree(),
                    "assemblyConstraints", project.getAssemblyConstraints(),
                    "modelQuality", modelQualityGate.evaluate(project)
            ));
        } catch (Exception e) {
            throw new IllegalStateException("生成3D模型数据失败", e);
        }
    }

    public byte[] mechanicalDesignPlan(DesignProject project) {
        try {
            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsBytes(project.getMechanicalDesignPlan());
        } catch (Exception e) {
            throw new IllegalStateException("生成机械设计方案JSON失败", e);
        }
    }

    public byte[] zip(List<DrawingArtifact> artifacts) {
        try (ByteArrayOutputStream output = new ByteArrayOutputStream(); ZipOutputStream zip = new ZipOutputStream(output, StandardCharsets.UTF_8)) {
            for (DrawingArtifact artifact : artifacts) {
                zip.putNextEntry(new ZipEntry(artifact.fileName())); zip.write(artifact.content()); zip.closeEntry();
            }
            zip.finish(); return output.toByteArray();
        } catch (Exception e) { throw new IllegalStateException("生成成果包ZIP失败：" + e.getMessage(), e); }
    }

    private byte[] simplePdf(String title) {
        String safe = title.replaceAll("[^\\x20-\\x7E]", " ").replace("\\", "\\\\").replace("(", "\\(").replace(")", "\\)");
        String stream = "BT /F1 18 Tf 72 760 Td (Graduation Design Package Preview) Tj 0 -32 Td /F1 11 Tf (" + safe + ") Tj 0 -24 Td (See DOCX, DXF, SVG and BAS files in the package.) Tj ET";
        List<String> objects = List.of(
                "<</Type/Catalog/Pages 2 0 R>>",
                "<</Type/Pages/Kids[3 0 R]/Count 1>>",
                "<</Type/Page/Parent 2 0 R/MediaBox[0 0 595 842]/Resources<</Font<</F1 4 0 R>>>>/Contents 5 0 R>>",
                "<</Type/Font/Subtype/Type1/BaseFont/Helvetica>>",
                "<</Length " + stream.length() + ">>stream\n" + stream + "\nendstream");
        StringBuilder pdf = new StringBuilder("%PDF-1.4\n");
        List<Integer> offsets = new ArrayList<>();
        for (int i = 0; i < objects.size(); i++) {
            offsets.add(pdf.length());
            pdf.append(i + 1).append(" 0 obj\n").append(objects.get(i)).append("\nendobj\n");
        }
        int xref = pdf.length();
        pdf.append("xref\n0 6\n0000000000 65535 f \n");
        offsets.forEach(offset -> pdf.append(String.format("%010d 00000 n \n", offset)));
        pdf.append("trailer<</Root 1 0 R/Size 6>>\nstartxref\n").append(xref).append("\n%%EOF");
        return pdf.toString().getBytes(StandardCharsets.US_ASCII);
    }
}
