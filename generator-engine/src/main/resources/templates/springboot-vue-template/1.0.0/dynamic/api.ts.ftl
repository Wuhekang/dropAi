import request from '../utils/request';import type{${entity.className}Form,${entity.className}VO,PageResult}from'../types/${entity.variableName}';
export const page${entity.className}=(params:Record<string,unknown>)=>request.get<{data:PageResult<${entity.className}VO>}>('/${entity.apiPath}/page',{params});
export const options${entity.className}=()=>request.get<{data:Array<{id:number,label:string}>}>('/${entity.apiPath}/options');
export const get${entity.className}=(id:number)=>request.get<{data:${entity.className}VO}>(`/${entity.apiPath}/${r'${id}'}`);
export const create${entity.className}=(data:${entity.className}Form)=>request.post('/${entity.apiPath}',data);export const update${entity.className}=(data:${entity.className}Form)=>request.put('/${entity.apiPath}',data);export const delete${entity.className}=(id:number)=>request.delete(`/${entity.apiPath}/${r'${id}'}`);
