package com.dropai.rewrite.modules.stepExportEngine;

import com.dropai.rewrite.modules.cadFeatureGenerator.CADFeature;
import com.dropai.rewrite.modules.drawingEngine.DrawingArtifact;
import com.dropai.rewrite.modules.model.DesignProject;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
public class StepExportEngine {
    public List<DrawingArtifact> export(DesignProject project) {
        List<DrawingArtifact> result = new ArrayList<>();
        result.add(new DrawingArtifact("assembly.step", step(project, null), "model/step"));
        List<DesignProject.DesignPart> parts = project.getResolvedParts().stream().limit(5).toList();
        for (int i = 0; i < parts.size(); i++) {
            result.add(new DrawingArtifact("part_%02d.step".formatted(i + 1), step(project, parts.get(i)), "model/step"));
        }
        return result;
    }

    private byte[] step(DesignProject project, DesignProject.DesignPart part) {
        String name = part == null ? safe(project.getEquipmentName(), "assembly") : safe(part.getName(), "part");
        StringBuilder builder = new StringBuilder();
        builder.append("ISO-10303-21;\n");
        builder.append("HEADER;\n");
        builder.append("FILE_DESCRIPTION(('DropAI parametric mechanical design output'),'2;1');\n");
        builder.append("FILE_NAME('").append(ascii(name)).append("','").append(LocalDateTime.now()).append("',('DropAI'),('DropAI'),'DropAI StepExportEngine','DropAI','');\n");
        builder.append("FILE_SCHEMA(('CONFIG_CONTROL_DESIGN'));\n");
        builder.append("ENDSEC;\n");
        builder.append("DATA;\n");
        builder.append("#1=APPLICATION_CONTEXT('mechanical design');\n");
        builder.append("#2=PRODUCT('").append(ascii(name)).append("','").append(ascii(name)).append("','generated from CADFeatureGenerator',(#1));\n");
        builder.append("#3=PRODUCT_DEFINITION_FORMATION_WITH_SPECIFIED_SOURCE('1','DropAI generated model',#2,.MADE.);\n");
        builder.append("#4=PRODUCT_DEFINITION('design','CAD feature based model',#3,#1);\n");
        if (part == null) {
            appendAssemblyMetadata(builder, project);
        } else {
            appendPartMetadata(builder, part);
        }
        builder.append("ENDSEC;\n");
        builder.append("END-ISO-10303-21;\n");
        return builder.toString().getBytes(StandardCharsets.UTF_8);
    }

    private void appendAssemblyMetadata(StringBuilder builder, DesignProject project) {
        int id = 10;
        builder.append("#").append(id++).append("=DESCRIPTIVE_REPRESENTATION_ITEM('mechanismType','")
                .append(ascii(project.getMechanicalDesignPlan().getMechanismType())).append("');\n");
        for (DesignProject.DesignPart part : project.getResolvedParts()) {
            builder.append("#").append(id++).append("=DESCRIPTIVE_REPRESENTATION_ITEM('component','")
                    .append(ascii(part.getName())).append("|").append(ascii(part.getPartType())).append("|")
                    .append(ascii(part.getCategory())).append("');\n");
        }
        for (DesignProject.AssemblyConstraint constraint : project.getAssemblyConstraints()) {
            builder.append("#").append(id++).append("=DESCRIPTIVE_REPRESENTATION_ITEM('constraint','")
                    .append(ascii(constraint.getPartName())).append("->").append(ascii(constraint.getMountTo())).append("|")
                    .append(ascii(constraint.getConstraintType())).append("');\n");
        }
    }

    private void appendPartMetadata(StringBuilder builder, DesignProject.DesignPart part) {
        int id = 10;
        builder.append("#").append(id++).append("=DESCRIPTIVE_REPRESENTATION_ITEM('partType','").append(ascii(part.getPartType())).append("');\n");
        builder.append("#").append(id++).append("=DESCRIPTIVE_REPRESENTATION_ITEM('material','").append(ascii(part.getMaterial())).append("');\n");
        builder.append("#").append(id++).append("=DESCRIPTIVE_REPRESENTATION_ITEM('modelingMethod','").append(ascii(part.getModelingMethod())).append("');\n");
        for (CADFeature feature : part.getCadFeatures()) {
            builder.append("#").append(id++).append("=DESCRIPTIVE_REPRESENTATION_ITEM('cadFeature','")
                    .append(ascii(feature.getType())).append(":").append(ascii(String.valueOf(feature.getParameters()))).append("');\n");
        }
    }

    private String ascii(String value) {
        return safe(value, "").replace("\\", "/").replace("'", "").replaceAll("[^\\x20-\\x7E]", "_");
    }

    private String safe(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }
}
