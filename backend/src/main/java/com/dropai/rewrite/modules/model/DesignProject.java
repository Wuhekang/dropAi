package com.dropai.rewrite.modules.model;

import java.util.ArrayList;
import java.util.List;

public class DesignProject {
    private String projectId = "";
    private String projectTitle = "";
    private String equipmentName = "";
    private String designType = "";
    private String projectCategory = "";
    private String workingPrinciple = "";
    private String designDepth = "graduation";
    private int partCount;
    private int featureCount;
    private int detailScore;
    private List<String> enhancementNotes = new ArrayList<>();
    private String equipmentType = "";
    private String applicationScenario = "";
    private List<String> missingParameters = new ArrayList<>();
    private List<String> detailFeatures = new ArrayList<>();
    private List<String> materials = new ArrayList<>();
    private List<String> standardParts = new ArrayList<>();
    private List<String> drawingViews = new ArrayList<>();
    private List<String> annotationList = new ArrayList<>();
    private List<String> mainFunctions = new ArrayList<>();
    private List<String> mainStructures = new ArrayList<>();
    private List<Component> components = new ArrayList<>();
    private List<BomItem> bom = new ArrayList<>();
    private List<DimensionChain> dimensionChains = new ArrayList<>();
    private List<String> technicalRequirements = new ArrayList<>();
    private List<Parameter> explicitParameters = new ArrayList<>();
    private List<Parameter> derivedParameters = new ArrayList<>();
    private List<Parameter> suggestedParameters = new ArrayList<>();
    private List<String> verificationItems = new ArrayList<>();
    private List<Calculation> calculations = new ArrayList<>();
    private ProjectAnalysis projectAnalysis = new ProjectAnalysis();
    private StructureNode structureTree = new StructureNode("整机", "root", "system", 1.0);
    private List<DesignPart> resolvedParts = new ArrayList<>();
    private AssemblyNode assemblyTree = new AssemblyNode("整机", "root");
    private List<AssemblyConstraint> assemblyConstraints = new ArrayList<>();
    private DrawingPlan drawingPlan = new DrawingPlan();

    public String getProjectId() { return projectId; }
    public void setProjectId(String projectId) { this.projectId = projectId; }
    public String getProjectTitle() { return projectTitle; }
    public void setProjectTitle(String projectTitle) { this.projectTitle = projectTitle; }
    public String getEquipmentName() { return equipmentName; }
    public void setEquipmentName(String equipmentName) { this.equipmentName = equipmentName; }
    public String getDesignType() { return designType; }
    public void setDesignType(String designType) { this.designType = designType; }
    public String getProjectCategory() { return projectCategory; }
    public void setProjectCategory(String projectCategory) { this.projectCategory = projectCategory; }
    public String getWorkingPrinciple() { return workingPrinciple; }
    public void setWorkingPrinciple(String workingPrinciple) { this.workingPrinciple = workingPrinciple; }
    public String getDesignDepth() { return designDepth; }
    public void setDesignDepth(String designDepth) { this.designDepth = designDepth; }
    public int getPartCount() { return partCount; }
    public void setPartCount(int partCount) { this.partCount = partCount; }
    public int getFeatureCount() { return featureCount; }
    public void setFeatureCount(int featureCount) { this.featureCount = featureCount; }
    public int getDetailScore() { return detailScore; }
    public void setDetailScore(int detailScore) { this.detailScore = detailScore; }
    public List<String> getEnhancementNotes() { return enhancementNotes; }
    public void setEnhancementNotes(List<String> value) { enhancementNotes = safe(value); }
    public String getEquipmentType() { return equipmentType; }
    public void setEquipmentType(String equipmentType) { this.equipmentType = equipmentType; }
    public String getApplicationScenario() { return applicationScenario; }
    public void setApplicationScenario(String applicationScenario) { this.applicationScenario = applicationScenario; }
    public List<String> getMissingParameters() { return missingParameters; }
    public void setMissingParameters(List<String> value) { missingParameters = safe(value); }
    public List<String> getDetailFeatures() { return detailFeatures; }
    public void setDetailFeatures(List<String> value) { detailFeatures = safe(value); }
    public List<String> getMaterials() { return materials; }
    public void setMaterials(List<String> value) { materials = safe(value); }
    public List<String> getStandardParts() { return standardParts; }
    public void setStandardParts(List<String> value) { standardParts = safe(value); }
    public List<String> getDrawingViews() { return drawingViews; }
    public void setDrawingViews(List<String> value) { drawingViews = safe(value); }
    public List<String> getAnnotationList() { return annotationList; }
    public void setAnnotationList(List<String> value) { annotationList = safe(value); }
    public List<String> getMainFunctions() { return mainFunctions; }
    public void setMainFunctions(List<String> value) { mainFunctions = safe(value); }
    public List<String> getMainStructures() { return mainStructures; }
    public void setMainStructures(List<String> value) { mainStructures = safe(value); }
    public List<Component> getComponents() { return components; }
    public void setComponents(List<Component> value) { components = safe(value); }
    public List<BomItem> getBom() { return bom; }
    public void setBom(List<BomItem> value) { bom = safe(value); }
    public List<DimensionChain> getDimensionChains() { return dimensionChains; }
    public void setDimensionChains(List<DimensionChain> value) { dimensionChains = safe(value); }
    public List<String> getTechnicalRequirements() { return technicalRequirements; }
    public void setTechnicalRequirements(List<String> value) { technicalRequirements = safe(value); }
    public List<Parameter> getExplicitParameters() { return explicitParameters; }
    public void setExplicitParameters(List<Parameter> value) { explicitParameters = safe(value); }
    public List<Parameter> getDerivedParameters() { return derivedParameters; }
    public void setDerivedParameters(List<Parameter> value) { derivedParameters = safe(value); }
    public List<Parameter> getSuggestedParameters() { return suggestedParameters; }
    public void setSuggestedParameters(List<Parameter> value) { suggestedParameters = safe(value); }
    public List<String> getVerificationItems() { return verificationItems; }
    public void setVerificationItems(List<String> value) { verificationItems = safe(value); }
    public List<Calculation> getCalculations() { return calculations; }
    public void setCalculations(List<Calculation> value) { calculations = safe(value); }
    public ProjectAnalysis getProjectAnalysis() { return projectAnalysis; }
    public void setProjectAnalysis(ProjectAnalysis value) { projectAnalysis = value == null ? new ProjectAnalysis() : value; }
    public StructureNode getStructureTree() { return structureTree; }
    public void setStructureTree(StructureNode value) { structureTree = value == null ? new StructureNode("整机", "root", "system", 1.0) : value; }
    public List<DesignPart> getResolvedParts() { return resolvedParts; }
    public void setResolvedParts(List<DesignPart> value) { resolvedParts = safe(value); }
    public AssemblyNode getAssemblyTree() { return assemblyTree; }
    public void setAssemblyTree(AssemblyNode value) { assemblyTree = value == null ? new AssemblyNode("整机", "root") : value; }
    public List<AssemblyConstraint> getAssemblyConstraints() { return assemblyConstraints; }
    public void setAssemblyConstraints(List<AssemblyConstraint> value) { assemblyConstraints = safe(value); }
    public DrawingPlan getDrawingPlan() { return drawingPlan; }
    public void setDrawingPlan(DrawingPlan value) { drawingPlan = value == null ? new DrawingPlan() : value; }

    public List<Parameter> allParameters() {
        List<Parameter> result = new ArrayList<>();
        result.addAll(explicitParameters);
        result.addAll(derivedParameters);
        result.addAll(suggestedParameters);
        return result;
    }

    public double number(String name, double fallback) {
        return allParameters().stream().filter(item -> name.equals(item.getName()))
                .map(Parameter::getValue).filter(Number.class::isInstance).map(Number.class::cast)
                .mapToDouble(Number::doubleValue).findFirst().orElse(fallback);
    }

    private static <T> List<T> safe(List<T> value) { return value == null ? new ArrayList<>() : new ArrayList<>(value); }

    public static class Parameter {
        private String name;
        private Object value;
        private String unit = "";
        private String source;
        private String basis;

        public Parameter() {}
        public Parameter(String name, Object value, String unit, String source, String basis) {
            this.name = name; this.value = value; this.unit = unit; this.source = source; this.basis = basis;
        }
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public Object getValue() { return value; }
        public void setValue(Object value) { this.value = value; }
        public String getUnit() { return unit; }
        public void setUnit(String unit) { this.unit = unit; }
        public String getSource() { return source; }
        public void setSource(String source) { this.source = source; }
        public String getBasis() { return basis; }
        public void setBasis(String basis) { this.basis = basis; }
    }

    public static class Calculation {
        private String name;
        private String formula;
        private String substitution;
        private double result;
        private String unit;
        private String conclusion;

        public Calculation() {}
        public Calculation(String name, String formula, String substitution, double result, String unit, String conclusion) {
            this.name = name; this.formula = formula; this.substitution = substitution;
            this.result = result; this.unit = unit; this.conclusion = conclusion;
        }
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public String getFormula() { return formula; }
        public void setFormula(String formula) { this.formula = formula; }
        public String getSubstitution() { return substitution; }
        public void setSubstitution(String substitution) { this.substitution = substitution; }
        public double getResult() { return result; }
        public void setResult(double result) { this.result = result; }
        public String getUnit() { return unit; }
        public void setUnit(String unit) { this.unit = unit; }
        public String getConclusion() { return conclusion; }
        public void setConclusion(String conclusion) { this.conclusion = conclusion; }
    }

    public static class Component {
        private int sequence;
        private String role;
        private String name;
        private String function;
        private String material;
        private String geometry = "BOX";
        private int quantity;
        private double x;
        private double y;
        private double z;
        private double length;
        private double width;
        private double height;
        private boolean keyPart;
        private String partId = "";
        private String parentAssembly = "";
        private String mountTo = "";
        private String constraintType = "";
        private List<String> mateReferences = new ArrayList<>();
        private Rotation rotation = new Rotation();

        public Component() {}
        public Component(int sequence, String role, String name, String function, String material, int quantity,
                         double x, double y, double z, double length, double width, double height, boolean keyPart) {
            this.sequence = sequence; this.role = role; this.name = name; this.function = function; this.material = material;
            this.quantity = quantity; this.x = x; this.y = y; this.z = z; this.length = length; this.width = width; this.height = height; this.keyPart = keyPart;
        }
        public int getSequence() { return sequence; } public void setSequence(int v) { sequence = v; }
        public String getRole() { return role; } public void setRole(String v) { role = v; }
        public String getName() { return name; } public void setName(String v) { name = v; }
        public String getFunction() { return function; } public void setFunction(String v) { function = v; }
        public String getMaterial() { return material; } public void setMaterial(String v) { material = v; }
        public String getGeometry() { return geometry; } public void setGeometry(String v) { geometry = v; }
        public int getQuantity() { return quantity; } public void setQuantity(int v) { quantity = v; }
        public double getX() { return x; } public void setX(double v) { x = v; }
        public double getY() { return y; } public void setY(double v) { y = v; }
        public double getZ() { return z; } public void setZ(double v) { z = v; }
        public double getLength() { return length; } public void setLength(double v) { length = v; }
        public double getWidth() { return width; } public void setWidth(double v) { width = v; }
        public double getHeight() { return height; } public void setHeight(double v) { height = v; }
        public boolean isKeyPart() { return keyPart; } public void setKeyPart(boolean v) { keyPart = v; }
        public String getPartId() { return partId; } public void setPartId(String v) { partId = v; }
        public String getParentAssembly() { return parentAssembly; } public void setParentAssembly(String v) { parentAssembly = v; }
        public String getMountTo() { return mountTo; } public void setMountTo(String v) { mountTo = v; }
        public String getConstraintType() { return constraintType; } public void setConstraintType(String v) { constraintType = v; }
        public List<String> getMateReferences() { return mateReferences; } public void setMateReferences(List<String> v) { mateReferences = safe(v); }
        public Rotation getRotation() { return rotation; } public void setRotation(Rotation v) { rotation = v == null ? new Rotation() : v; }
    }

    public static class BomItem {
        private int sequence;
        private String name;
        private String material;
        private int quantity;
        private String remark;
        public BomItem() {}
        public BomItem(int sequence, String name, String material, int quantity, String remark) {
            this.sequence = sequence; this.name = name; this.material = material; this.quantity = quantity; this.remark = remark;
        }
        public int getSequence() { return sequence; } public void setSequence(int v) { sequence = v; }
        public String getName() { return name; } public void setName(String v) { name = v; }
        public String getMaterial() { return material; } public void setMaterial(String v) { material = v; }
        public int getQuantity() { return quantity; } public void setQuantity(int v) { quantity = v; }
        public String getRemark() { return remark; } public void setRemark(String v) { remark = v; }
    }

    public static class DimensionChain {
        private String name;
        private double value;
        private String unit;
        private String relatedComponent;
        private String source = "";
        public DimensionChain() {}
        public DimensionChain(String name, double value, String unit, String relatedComponent) {
            this.name = name; this.value = value; this.unit = unit; this.relatedComponent = relatedComponent;
        }
        public DimensionChain(String name, double value, String unit, String relatedComponent, String source) {
            this.name = name; this.value = value; this.unit = unit; this.relatedComponent = relatedComponent; this.source = source;
        }
        public String getName() { return name; } public void setName(String v) { name = v; }
        public double getValue() { return value; } public void setValue(double v) { value = v; }
        public String getUnit() { return unit; } public void setUnit(String v) { unit = v; }
        public String getRelatedComponent() { return relatedComponent; } public void setRelatedComponent(String v) { relatedComponent = v; }
        public String getSource() { return source; } public void setSource(String v) { source = v; }
    }

    public static class ProjectAnalysis {
        private String title = "";
        private String equipmentName = "";
        private String projectType = "";
        private List<String> functions = new ArrayList<>();
        private List<String> requirements = new ArrayList<>();
        private List<String> deliverables = new ArrayList<>();
        public String getTitle() { return title; } public void setTitle(String v) { title = v; }
        public String getEquipmentName() { return equipmentName; } public void setEquipmentName(String v) { equipmentName = v; }
        public String getProjectType() { return projectType; } public void setProjectType(String v) { projectType = v; }
        public List<String> getFunctions() { return functions; } public void setFunctions(List<String> v) { functions = safe(v); }
        public List<String> getRequirements() { return requirements; } public void setRequirements(List<String> v) { requirements = safe(v); }
        public List<String> getDeliverables() { return deliverables; } public void setDeliverables(List<String> v) { deliverables = safe(v); }
    }

    public static class StructureNode {
        private String name = "";
        private String type = "";
        private String source = "";
        private double confidence;
        private boolean required = true;
        private List<StructureNode> children = new ArrayList<>();
        public StructureNode() {}
        public StructureNode(String name, String type, String source, double confidence) {
            this.name = name; this.type = type; this.source = source; this.confidence = confidence;
        }
        public String getName() { return name; } public void setName(String v) { name = v; }
        public String getType() { return type; } public void setType(String v) { type = v; }
        public String getSource() { return source; } public void setSource(String v) { source = v; }
        public double getConfidence() { return confidence; } public void setConfidence(double v) { confidence = v; }
        public boolean isRequired() { return required; } public void setRequired(boolean v) { required = v; }
        public List<StructureNode> getChildren() { return children; } public void setChildren(List<StructureNode> v) { children = safe(v); }
    }

    public static class DesignPart {
        private String partType = "non_standard";
        private String category = "";
        private String name = "";
        private String model = "";
        private String brand = "";
        private String source = "";
        private String sourcePlatform = "";
        private String sourceUrl = "";
        private String reason = "";
        private List<String> availableFormats = new ArrayList<>();
        private List<String> availableModelFormats = new ArrayList<>();
        private java.util.Map<String, Object> technicalParams = new java.util.LinkedHashMap<>();
        private String modelDownloadUrl = "";
        private String cachedModelPath = "";
        private String retrievalStatus = "";
        private double confidence;
        private java.util.Map<String, Object> dimensions = new java.util.LinkedHashMap<>();
        private String generatedBy = "";
        private List<String> geometryFeatures = new ArrayList<>();
        private String material = "";
        private String process = "";
        private int quantity = 1;
        private String parentStructure = "";
        public String getPartType() { return partType; } public void setPartType(String v) { partType = v; }
        public String getCategory() { return category; } public void setCategory(String v) { category = v; }
        public String getName() { return name; } public void setName(String v) { name = v; }
        public String getModel() { return model; } public void setModel(String v) { model = v; }
        public String getBrand() { return brand; } public void setBrand(String v) { brand = v; }
        public String getSource() { return source; } public void setSource(String v) { source = v; }
        public String getSourcePlatform() { return sourcePlatform; } public void setSourcePlatform(String v) { sourcePlatform = v; }
        public String getSourceUrl() { return sourceUrl; } public void setSourceUrl(String v) { sourceUrl = v; }
        public String getReason() { return reason; } public void setReason(String v) { reason = v; }
        public List<String> getAvailableFormats() { return availableFormats; } public void setAvailableFormats(List<String> v) { availableFormats = safe(v); }
        public List<String> getAvailableModelFormats() { return availableModelFormats; } public void setAvailableModelFormats(List<String> v) { availableModelFormats = safe(v); }
        public java.util.Map<String, Object> getTechnicalParams() { return technicalParams; } public void setTechnicalParams(java.util.Map<String, Object> v) { technicalParams = v == null ? new java.util.LinkedHashMap<>() : new java.util.LinkedHashMap<>(v); }
        public String getModelDownloadUrl() { return modelDownloadUrl; } public void setModelDownloadUrl(String v) { modelDownloadUrl = v; }
        public String getCachedModelPath() { return cachedModelPath; } public void setCachedModelPath(String v) { cachedModelPath = v; }
        public String getRetrievalStatus() { return retrievalStatus; } public void setRetrievalStatus(String v) { retrievalStatus = v; }
        public double getConfidence() { return confidence; } public void setConfidence(double v) { confidence = v; }
        public java.util.Map<String, Object> getDimensions() { return dimensions; } public void setDimensions(java.util.Map<String, Object> v) { dimensions = v == null ? new java.util.LinkedHashMap<>() : new java.util.LinkedHashMap<>(v); }
        public String getGeneratedBy() { return generatedBy; } public void setGeneratedBy(String v) { generatedBy = v; }
        public List<String> getGeometryFeatures() { return geometryFeatures; } public void setGeometryFeatures(List<String> v) { geometryFeatures = safe(v); }
        public String getMaterial() { return material; } public void setMaterial(String v) { material = v; }
        public String getProcess() { return process; } public void setProcess(String v) { process = v; }
        public int getQuantity() { return quantity; } public void setQuantity(int v) { quantity = v; }
        public String getParentStructure() { return parentStructure; } public void setParentStructure(String v) { parentStructure = v; }
    }

    public static class AssemblyNode {
        private String name = "";
        private String relation = "";
        private String assemblyName = "";
        private String basePart = "";
        private java.util.Map<String, String> coordinateSystem = new java.util.LinkedHashMap<>();
        private List<AssemblyNode> children = new ArrayList<>();
        public AssemblyNode() {}
        public AssemblyNode(String name, String relation) { this.name = name; this.relation = relation; }
        public String getName() { return name; } public void setName(String v) { name = v; }
        public String getRelation() { return relation; } public void setRelation(String v) { relation = v; }
        public String getAssemblyName() { return assemblyName; } public void setAssemblyName(String v) { assemblyName = v; }
        public String getBasePart() { return basePart; } public void setBasePart(String v) { basePart = v; }
        public java.util.Map<String, String> getCoordinateSystem() { return coordinateSystem; } public void setCoordinateSystem(java.util.Map<String, String> v) { coordinateSystem = v == null ? new java.util.LinkedHashMap<>() : new java.util.LinkedHashMap<>(v); }
        public List<AssemblyNode> getChildren() { return children; } public void setChildren(List<AssemblyNode> v) { children = safe(v); }
    }

    public static class Rotation {
        private double x; private double y; private double z;
        public double getX() { return x; } public void setX(double v) { x = v; }
        public double getY() { return y; } public void setY(double v) { y = v; }
        public double getZ() { return z; } public void setZ(double v) { z = v; }
    }

    public static class AssemblyConstraint {
        private String partId = "";
        private String partName = "";
        private String parentAssembly = "";
        private String mountTo = "";
        private String constraintType = "fixed";
        private String axisId = "";
        private String mountingFace = "";
        private String holePattern = "";
        private String contactFace = "";
        private String symmetryPlane = "";
        private double offsetDistance;
        private String source = "";
        private List<String> mateReferences = new ArrayList<>();
        public String getPartId() { return partId; } public void setPartId(String v) { partId = v; }
        public String getPartName() { return partName; } public void setPartName(String v) { partName = v; }
        public String getParentAssembly() { return parentAssembly; } public void setParentAssembly(String v) { parentAssembly = v; }
        public String getMountTo() { return mountTo; } public void setMountTo(String v) { mountTo = v; }
        public String getConstraintType() { return constraintType; } public void setConstraintType(String v) { constraintType = v; }
        public String getAxisId() { return axisId; } public void setAxisId(String v) { axisId = v; }
        public String getMountingFace() { return mountingFace; } public void setMountingFace(String v) { mountingFace = v; }
        public String getHolePattern() { return holePattern; } public void setHolePattern(String v) { holePattern = v; }
        public String getContactFace() { return contactFace; } public void setContactFace(String v) { contactFace = v; }
        public String getSymmetryPlane() { return symmetryPlane; } public void setSymmetryPlane(String v) { symmetryPlane = v; }
        public double getOffsetDistance() { return offsetDistance; } public void setOffsetDistance(double v) { offsetDistance = v; }
        public String getSource() { return source; } public void setSource(String v) { source = v; }
        public List<String> getMateReferences() { return mateReferences; } public void setMateReferences(List<String> v) { mateReferences = safe(v); }
    }

    public static class DrawingPlan {
        private String inputSource = "";
        private int qualityScore;
        private List<String> qualityNotes = new ArrayList<>();
        private DrawingViewPlan mainView = new DrawingViewPlan("主视图");
        private DrawingViewPlan topView = new DrawingViewPlan("俯视图");
        private DrawingViewPlan sideView = new DrawingViewPlan("侧视图");
        private List<DrawingViewPlan> sectionViews = new ArrayList<>();
        private List<DrawingViewPlan> detailViews = new ArrayList<>();
        private DrawingViewPlan isometricView = new DrawingViewPlan("网页三维展示");
        private List<BomItem> bomTable = new ArrayList<>();
        private List<String> technicalRequirements = new ArrayList<>();
        private java.util.Map<String, String> titleBlock = new java.util.LinkedHashMap<>();
        private List<Parameter> parameterTable = new ArrayList<>();
        public String getInputSource() { return inputSource; } public void setInputSource(String v) { inputSource = v; }
        public int getQualityScore() { return qualityScore; } public void setQualityScore(int v) { qualityScore = v; }
        public List<String> getQualityNotes() { return qualityNotes; } public void setQualityNotes(List<String> v) { qualityNotes = safe(v); }
        public DrawingViewPlan getMainView() { return mainView; } public void setMainView(DrawingViewPlan v) { mainView = v == null ? new DrawingViewPlan("主视图") : v; }
        public DrawingViewPlan getTopView() { return topView; } public void setTopView(DrawingViewPlan v) { topView = v == null ? new DrawingViewPlan("俯视图") : v; }
        public DrawingViewPlan getSideView() { return sideView; } public void setSideView(DrawingViewPlan v) { sideView = v == null ? new DrawingViewPlan("侧视图") : v; }
        public List<DrawingViewPlan> getSectionViews() { return sectionViews; } public void setSectionViews(List<DrawingViewPlan> v) { sectionViews = safe(v); }
        public List<DrawingViewPlan> getDetailViews() { return detailViews; } public void setDetailViews(List<DrawingViewPlan> v) { detailViews = safe(v); }
        public DrawingViewPlan getIsometricView() { return isometricView; } public void setIsometricView(DrawingViewPlan v) { isometricView = v == null ? new DrawingViewPlan("网页三维展示") : v; }
        public List<BomItem> getBomTable() { return bomTable; } public void setBomTable(List<BomItem> v) { bomTable = safe(v); }
        public List<String> getTechnicalRequirements() { return technicalRequirements; } public void setTechnicalRequirements(List<String> v) { technicalRequirements = safe(v); }
        public java.util.Map<String, String> getTitleBlock() { return titleBlock; } public void setTitleBlock(java.util.Map<String, String> v) { titleBlock = v == null ? new java.util.LinkedHashMap<>() : new java.util.LinkedHashMap<>(v); }
        public List<Parameter> getParameterTable() { return parameterTable; } public void setParameterTable(List<Parameter> v) { parameterTable = safe(v); }
    }

    public static class DrawingViewPlan {
        private String name = "";
        private String purpose = "";
        private String levelOfDetail = "engineering_simplified";
        private java.util.Map<String, Double> viewport = new java.util.LinkedHashMap<>();
        private List<String> visibleParts = new ArrayList<>();
        private List<String> hiddenParts = new ArrayList<>();
        private List<DimensionChain> dimensions = new ArrayList<>();
        private List<String> labels = new ArrayList<>();
        private List<String> centerLines = new ArrayList<>();
        private List<String> sectionMarkers = new ArrayList<>();
        public DrawingViewPlan() {}
        public DrawingViewPlan(String name) { this.name = name; }
        public String getName() { return name; } public void setName(String v) { name = v; }
        public String getPurpose() { return purpose; } public void setPurpose(String v) { purpose = v; }
        public String getLevelOfDetail() { return levelOfDetail; } public void setLevelOfDetail(String v) { levelOfDetail = v; }
        public java.util.Map<String, Double> getViewport() { return viewport; } public void setViewport(java.util.Map<String, Double> v) { viewport = v == null ? new java.util.LinkedHashMap<>() : new java.util.LinkedHashMap<>(v); }
        public List<String> getVisibleParts() { return visibleParts; } public void setVisibleParts(List<String> v) { visibleParts = safe(v); }
        public List<String> getHiddenParts() { return hiddenParts; } public void setHiddenParts(List<String> v) { hiddenParts = safe(v); }
        public List<DimensionChain> getDimensions() { return dimensions; } public void setDimensions(List<DimensionChain> v) { dimensions = safe(v); }
        public List<String> getLabels() { return labels; } public void setLabels(List<String> v) { labels = safe(v); }
        public List<String> getCenterLines() { return centerLines; } public void setCenterLines(List<String> v) { centerLines = safe(v); }
        public List<String> getSectionMarkers() { return sectionMarkers; } public void setSectionMarkers(List<String> v) { sectionMarkers = safe(v); }
    }
}
