export interface ${entity.className}VO {<#list entity.fields as field>${field.name}<#if !field.required>?</#if>: ${field.tsType};</#list><#if entity.parentRelation??>${entity.parentRelation.displayProperty}?:string;</#if>}
export type ${entity.className}Form = Partial<${entity.className}VO>
export interface PageResult<T>{records:T[];total:number;pageNum:number;pageSize:number}
