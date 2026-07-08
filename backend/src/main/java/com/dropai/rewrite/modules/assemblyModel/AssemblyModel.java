package com.dropai.rewrite.modules.assemblyModel;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class AssemblyModel {
    private String projectName = "";
    private Map<String, String> coordinateSystem = new LinkedHashMap<>();
    private List<AssemblyComponent> components = new ArrayList<>();
    private List<AssemblyConstraint> constraints = new ArrayList<>();
    private List<String> validationMessages = new ArrayList<>();

    public String getProjectName() { return projectName; }
    public void setProjectName(String projectName) { this.projectName = projectName == null ? "" : projectName; }
    public Map<String, String> getCoordinateSystem() { return coordinateSystem; }
    public void setCoordinateSystem(Map<String, String> coordinateSystem) {
        this.coordinateSystem = coordinateSystem == null ? new LinkedHashMap<>() : new LinkedHashMap<>(coordinateSystem);
    }
    public List<AssemblyComponent> getComponents() { return components; }
    public void setComponents(List<AssemblyComponent> components) {
        this.components = components == null ? new ArrayList<>() : new ArrayList<>(components);
    }
    public List<AssemblyConstraint> getConstraints() { return constraints; }
    public void setConstraints(List<AssemblyConstraint> constraints) {
        this.constraints = constraints == null ? new ArrayList<>() : new ArrayList<>(constraints);
    }
    public List<String> getValidationMessages() { return validationMessages; }
    public void setValidationMessages(List<String> validationMessages) {
        this.validationMessages = validationMessages == null ? new ArrayList<>() : new ArrayList<>(validationMessages);
    }

    public static class AssemblyComponent {
        private String id = "";
        private String name = "";
        private String type = "";
        private String source = "";
        private Pose position = new Pose();
        private Pose rotation = new Pose();
        private Size size = new Size();
        private Map<String, Object> parameters = new LinkedHashMap<>();

        public String getId() { return id; }
        public void setId(String id) { this.id = id == null ? "" : id; }
        public String getName() { return name; }
        public void setName(String name) { this.name = name == null ? "" : name; }
        public String getType() { return type; }
        public void setType(String type) { this.type = type == null ? "" : type; }
        public String getSource() { return source; }
        public void setSource(String source) { this.source = source == null ? "" : source; }
        public Pose getPosition() { return position; }
        public void setPosition(Pose position) { this.position = position == null ? new Pose() : position; }
        public Pose getRotation() { return rotation; }
        public void setRotation(Pose rotation) { this.rotation = rotation == null ? new Pose() : rotation; }
        public Size getSize() { return size; }
        public void setSize(Size size) { this.size = size == null ? new Size() : size; }
        public Map<String, Object> getParameters() { return parameters; }
        public void setParameters(Map<String, Object> parameters) {
            this.parameters = parameters == null ? new LinkedHashMap<>() : new LinkedHashMap<>(parameters);
        }
    }

    public static class AssemblyConstraint {
        private String type = "";
        private String componentA = "";
        private String componentB = "";
        private String relation = "";

        public String getType() { return type; }
        public void setType(String type) { this.type = type == null ? "" : type; }
        public String getComponentA() { return componentA; }
        public void setComponentA(String componentA) { this.componentA = componentA == null ? "" : componentA; }
        public String getComponentB() { return componentB; }
        public void setComponentB(String componentB) { this.componentB = componentB == null ? "" : componentB; }
        public String getRelation() { return relation; }
        public void setRelation(String relation) { this.relation = relation == null ? "" : relation; }
    }

    public static class Pose {
        private double x;
        private double y;
        private double z;

        public Pose() {}
        public Pose(double x, double y, double z) { this.x = x; this.y = y; this.z = z; }
        public double getX() { return x; }
        public void setX(double x) { this.x = x; }
        public double getY() { return y; }
        public void setY(double y) { this.y = y; }
        public double getZ() { return z; }
        public void setZ(double z) { this.z = z; }
    }

    public static class Size {
        private double x;
        private double y;
        private double z;

        public Size() {}
        public Size(double x, double y, double z) { this.x = x; this.y = y; this.z = z; }
        public double getX() { return x; }
        public void setX(double x) { this.x = x; }
        public double getY() { return y; }
        public void setY(double y) { this.y = y; }
        public double getZ() { return z; }
        public void setZ(double z) { this.z = z; }
    }
}
