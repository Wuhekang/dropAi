package com.dropai.rewrite.modules.stepExportEngine;

import com.dropai.rewrite.modules.model.DesignProject;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class StepExportEngine {
    private final ObjectMapper objectMapper;

    public StepExportEngine(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public byte[] exportStepPlan(DesignProject project) {
        StringBuilder step = new StringBuilder();
        step.append("ISO-10303-21;\n");
        step.append("HEADER;\n");
        step.append("FILE_DESCRIPTION(('DropAI feature-based assembly export plan; BREP kernel pending'),'2;1');\n");
        step.append("FILE_NAME('").append(safe(project.getEquipmentName())).append("_assembly.step','")
                .append(java.time.LocalDate.now()).append("',('DropAI'),('DropAI'),'DropAI','DropAI','');\n");
        step.append("FILE_SCHEMA(('AP214_AUTOMOTIVE_DESIGN'));\n");
        step.append("ENDSEC;\n");
        step.append("DATA;\n");
        step.append("/* This file intentionally records the assembly feature plan only.\n");
        step.append("   It is not a fake mesh-to-STEP conversion. A BREP/OCC kernel is required\n");
        step.append("   before exporting import-ready STEP solids for SolidWorks/Creo/UG. */\n");
        int id = 1;
        for (DesignProject.Component component : project.getComponents()) {
            step.append("#").append(id++).append("=PRODUCT('")
                    .append(safe(component.getPartId())).append("','")
                    .append(safe(component.getName())).append("','")
                    .append(safe(component.getMaterial())).append("',());\n");
            step.append("/* geometry=").append(safe(component.getGeometry()))
                    .append("; featureTree=").append(String.join(",", component.getFeatureTree()))
                    .append("; mountTo=").append(safe(component.getMountTo())).append(" */\n");
        }
        step.append("ENDSEC;\nEND-ISO-10303-21;\n");
        return step.toString().getBytes(StandardCharsets.UTF_8);
    }

    public byte[] exportGltfPlan(DesignProject project) {
        return json(project, "GLTF_EXPORT_PLAN", "feature-based Three.js model data; real glTF mesh export requires client-side or server-side mesh serialization");
    }

    public byte[] exportStlPlan(DesignProject project) {
        return json(project, "STL_EXPORT_PLAN", "feature-based tessellation plan; real STL export requires tessellating every feature tree to triangles");
    }

    private byte[] json(DesignProject project, String type, String status) {
        try {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("type", type);
            payload.put("status", status);
            payload.put("projectTitle", project.getProjectTitle());
            payload.put("equipmentName", project.getEquipmentName());
            payload.put("components", project.getComponents());
            payload.put("assemblyTree", project.getAssemblyTree());
            payload.put("assemblyConstraints", project.getAssemblyConstraints());
            payload.put("bom", project.getBom());
            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsBytes(payload);
        } catch (Exception e) {
            throw new IllegalStateException(type + "生成失败", e);
        }
    }

    private String safe(String value) {
        if (value == null) return "";
        return value.replace("'", "_").replace("\n", " ").replace("\r", " ");
    }
}
