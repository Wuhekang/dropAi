package ${project.basePackage}.entity;

public class ${entity.className} {
<#list entity.fields as field>
    private ${field.javaType} ${field.name};
</#list>
<#list entity.fields as field>
    public ${field.javaType} get${field.name?cap_first}() { return ${field.name}; }
    public void set${field.name?cap_first}(${field.javaType} ${field.name}) { this.${field.name} = ${field.name}; }
</#list>
}
