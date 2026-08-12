package com.dropai.generator.model;

import com.dropai.generator.blueprint.ProjectBlueprint;
import java.util.List;
import java.util.Map;
import java.util.Set;

public record GenerationModel(ProjectBlueprint source, List<EntityModel> entities, List<RelationModel> relations,
                              List<PermissionModel> permissions, List<FileGenerationPlan> filePlans, String blueprintHash) {
    public EntityModel entity() { return entities.get(0); }
    public record EntityModel(String className, String variableName, String tableName, String title,
                              String apiPath, String displayField, String displayTemplate,
                              boolean referenced, List<FieldModel> fields, RelationModel parentRelation) {}
    public record RelationModel(String name, String sourceEntity, String targetEntity, String sourceTable,
                                String targetTable, String foreignKeyName, String foreignKeyColumn,
                                String targetDisplayField, String targetDisplayColumn, String displayProperty,
                                boolean required, String onDelete) {}
    public record FieldModel(String name, String columnName, String javaType, String tsType, String sqlType,
                             String title, boolean required, boolean searchable, boolean listVisible,
                             boolean formVisible, boolean relationField) {}
    public record PermissionModel(String moduleCode, String action, String code, String description) {}
    public record FileGenerationPlan(String sourceKey, GeneratorType generatorType, String templateName,
                                     String outputPath, String modelHash, Set<String> dependsOn,
                                     Map<String, Object> templateModel) {}
    public enum GeneratorType { BACKEND, FRONTEND, SQL, ROOT }
}
