package ${project.basePackage}.service;
import ${project.basePackage}.common.*;import ${project.basePackage}.dto.*;import ${project.basePackage}.vo.*;
public interface ${entity.className}Service {PageResult<${entity.className}VO> page(${entity.className}QueryDTO query);${entity.className}VO detail(Long id);Long create(${entity.className}DTO dto);void update(${entity.className}DTO dto);void delete(Long id);java.util.List<java.util.Map<String,Object>> options();}
