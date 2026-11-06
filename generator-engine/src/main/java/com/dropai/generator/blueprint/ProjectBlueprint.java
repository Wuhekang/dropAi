package com.dropai.generator.blueprint;

import java.math.BigDecimal;
import java.util.List;

public record ProjectBlueprint(
        String schemaVersion, ProjectSpec project, TemplateSpec template,
        List<EntitySpec> entities, List<RelationSpec> relations, List<ModuleSpec> modules,
        List<RoleSpec> roles, List<PermissionSpec> permissions, List<BusinessFlowSpec> businessFlows,
        CapabilitySpec capabilities, GenerationSpec generation) {
    public record ProjectSpec(String name, String title, String description, String basePackage, String databaseName) {}
    public record TemplateSpec(String id, String version) {}
    public record EntitySpec(String name, String tableName, String title, String description, String displayField,
                             DisplayConfig displayConfig, List<FieldSpec> fields, List<IndexSpec> indexes) {}
    public record DisplayConfig(String primaryField, String template) {}
    public record FieldSpec(String name, String columnName, FieldType type, String title, boolean required,
                            boolean unique, Integer length, Integer precision, Integer scale, Object defaultValue,
                            List<String> enumValues, boolean searchable, boolean listVisible, boolean formVisible) {}
    public record IndexSpec(String name, boolean unique, List<String> fields) {}
    public record RelationSpec(String name, RelationType type, String sourceEntity, String targetEntity,
                               String foreignKeyEntity, String foreignKeyField, String ownerEntity,
                               String joinTable, boolean required, String onDelete) {}
    public record ModuleSpec(String code, String name, String entity, String routePath, String apiPath,
                             int order, List<String> actions) {}
    public record RoleSpec(String code, String name, boolean builtIn, List<String> moduleCodes,
                           List<String> permissionCodes) {}
    public record PermissionSpec(String moduleCode, PermissionAction action, String description) {}
    public record BusinessFlowSpec(String code, String name, List<String> moduleCodes, List<BusinessFlowStepSpec> steps) {}
    public record BusinessFlowStepSpec(int order, String name, String actorRoleCode, String moduleCode, PermissionAction action) {}
    public record CapabilitySpec(boolean fileUpload, boolean imageUpload, boolean importEnabled,
                                 boolean exportEnabled, boolean approvalEnabled) {}
    public record GenerationSpec(boolean backend, boolean frontend, boolean sql, boolean runBackendCompile,
                                 boolean runFrontendBuild, boolean validateSql, boolean packageZip, String namingStrategy) {}
    public enum FieldType { STRING, TEXT, INTEGER, DECIMAL, BOOLEAN, DATE, DATETIME, ENUM, IMAGE, FILE }
    public enum RelationType { ONE_TO_ONE, ONE_TO_MANY, MANY_TO_ONE, MANY_TO_MANY }
    public enum PermissionAction { VIEW, CREATE, UPDATE, DELETE, EXPORT, IMPORT, APPROVE }
}
