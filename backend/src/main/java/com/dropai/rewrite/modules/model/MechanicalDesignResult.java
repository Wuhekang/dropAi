package com.dropai.rewrite.modules.model;

import com.dropai.rewrite.modules.cadFeatureGenerator.CADFeature;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class MechanicalDesignResult {
    private Map<String, Object> product = new LinkedHashMap<>();
    private List<String> requirements = new ArrayList<>();
    private Map<String, Object> architecture = new LinkedHashMap<>();
    private DesignProject.StructureNode structureTree = new DesignProject.StructureNode();
    private List<DesignProject.DesignPart> parts = new ArrayList<>();
    private DesignProject.AssemblyNode assembly = new DesignProject.AssemblyNode();
    private List<CADFeature> cadFeatures = new ArrayList<>();
    private DesignProject.DrawingPlan drawings = new DesignProject.DrawingPlan();
    private List<DesignProject.BomItem> bom = new ArrayList<>();
    private List<String> materials = new ArrayList<>();
    private List<DesignProject.Calculation> calculations = new ArrayList<>();
    private List<String> manufacturing = new ArrayList<>();
    private Map<String, Object> documentation = new LinkedHashMap<>();

    public static MechanicalDesignResult fromProject(DesignProject project) {
        MechanicalDesignResult result = new MechanicalDesignResult();
        if (project == null) {
            return result;
        }
        result.product.put("projectId", project.getProjectId());
        result.product.put("projectTitle", project.getProjectTitle());
        result.product.put("equipmentName", project.getEquipmentName());
        result.product.put("designType", project.getDesignType());
        result.product.put("detailScore", project.getDetailScore());
        result.requirements.addAll(project.getVerificationItems());
        result.architecture.put("workingPrinciple", project.getWorkingPrinciple());
        result.architecture.put("mainFunctions", project.getMainFunctions());
        result.architecture.put("mainStructures", project.getMainStructures());
        result.structureTree = project.getStructureTree();
        result.parts.addAll(project.getResolvedParts());
        result.assembly = project.getAssemblyTree();
        project.getResolvedParts().forEach(part -> result.cadFeatures.addAll(part.getCadFeatures()));
        result.drawings = project.getDrawingPlan();
        result.bom.addAll(project.getBom());
        result.materials.addAll(project.getMaterials());
        result.calculations.addAll(project.getCalculations());
        result.manufacturing.addAll(project.getTechnicalRequirements());
        result.documentation.put("enhancementNotes", project.getEnhancementNotes());
        return result;
    }

    public Map<String, Object> getProduct() { return product; }
    public void setProduct(Map<String, Object> product) { this.product = map(product); }
    public List<String> getRequirements() { return requirements; }
    public void setRequirements(List<String> requirements) { this.requirements = list(requirements); }
    public Map<String, Object> getArchitecture() { return architecture; }
    public void setArchitecture(Map<String, Object> architecture) { this.architecture = map(architecture); }
    public DesignProject.StructureNode getStructureTree() { return structureTree; }
    public void setStructureTree(DesignProject.StructureNode structureTree) { this.structureTree = structureTree == null ? new DesignProject.StructureNode() : structureTree; }
    public List<DesignProject.DesignPart> getParts() { return parts; }
    public void setParts(List<DesignProject.DesignPart> parts) { this.parts = list(parts); }
    public DesignProject.AssemblyNode getAssembly() { return assembly; }
    public void setAssembly(DesignProject.AssemblyNode assembly) { this.assembly = assembly == null ? new DesignProject.AssemblyNode() : assembly; }
    public List<CADFeature> getCadFeatures() { return cadFeatures; }
    public void setCadFeatures(List<CADFeature> cadFeatures) { this.cadFeatures = list(cadFeatures); }
    public DesignProject.DrawingPlan getDrawings() { return drawings; }
    public void setDrawings(DesignProject.DrawingPlan drawings) { this.drawings = drawings == null ? new DesignProject.DrawingPlan() : drawings; }
    public List<DesignProject.BomItem> getBom() { return bom; }
    public void setBom(List<DesignProject.BomItem> bom) { this.bom = list(bom); }
    public List<String> getMaterials() { return materials; }
    public void setMaterials(List<String> materials) { this.materials = list(materials); }
    public List<DesignProject.Calculation> getCalculations() { return calculations; }
    public void setCalculations(List<DesignProject.Calculation> calculations) { this.calculations = list(calculations); }
    public List<String> getManufacturing() { return manufacturing; }
    public void setManufacturing(List<String> manufacturing) { this.manufacturing = list(manufacturing); }
    public Map<String, Object> getDocumentation() { return documentation; }
    public void setDocumentation(Map<String, Object> documentation) { this.documentation = map(documentation); }

    private static <T> List<T> list(List<T> value) {
        return value == null ? new ArrayList<>() : new ArrayList<>(value);
    }

    private static Map<String, Object> map(Map<String, Object> value) {
        return value == null ? new LinkedHashMap<>() : new LinkedHashMap<>(value);
    }
}
