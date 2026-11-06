package ${project.basePackage}.dto;

import jakarta.validation.constraints.*;
public class ${entity.className}DTO {
    private Long id;
<#list entity.fields as field><#if field.name != "id" && field.name != "createdTime" && field.name != "updatedTime" && field.name != "deleted">
<#if field.required>    @NotNull(message = "${field.title}不能为空")
</#if>    private ${field.javaType} ${field.name};
</#if></#list>
    public Long getId(){return id;} public void setId(Long id){this.id=id;}
<#list entity.fields as field><#if field.name != "id" && field.name != "createdTime" && field.name != "updatedTime" && field.name != "deleted">
    public ${field.javaType} get${field.name?cap_first}(){return ${field.name};} public void set${field.name?cap_first}(${field.javaType} value){this.${field.name}=value;}
</#if></#list>
}
