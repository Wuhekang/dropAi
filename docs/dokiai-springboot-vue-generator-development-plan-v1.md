# Dokiai SpringBoot Vue Generator 开发任务拆解 v1

## 1. 交付目标

第一条工程里程碑不是接入 AI，而是让固定的 `library-crud.blueprint.json` 确定性生成：

```text
library-system/
├── backend/   # Maven 编译成功并可启动
├── frontend/  # npm ci && npm run build 成功
├── sql/       # MySQL 8 执行成功
├── README.md
└── manifest.json
```

完成标准是端到端自动测试通过，不以“模板文件已经写完”代替可运行验收。

## 2. 阶段与依赖

```text
P0 工程骨架
  -> P1 Blueprint 核心
  -> P2 GenerationModel 与 FilePlan
  -> P3 黄金模板与渲染
  -> P4 Merge/Build/Package
  -> P5 三个 Golden E2E
  -> P6 AI Analyzer
  -> P7 生成前预览与页面重构
```

P0–P5 不依赖 AI，也不依赖生产 UI。开发期间直接读取 `docs/blueprints/*.json`。

## 3. P0：工程骨架

- [ ] 建立 `generator-engine` Maven 模块，Java 17。
- [ ] 引入 Jackson、JSON Schema Validator、FreeMarker、JUnit 5。
- [ ] 建立 `com.dropai.generator` 分层包。
- [ ] 添加格式化、单元测试和构建脚本。
- [ ] 建立 `work/generator/{taskId}` 隔离目录策略。
- [ ] 建立模板 `springboot-vue-template/1.0.0/template.yaml`。

验收：空模块 `mvn test` 通过；模板包能按 id/version/hash 加载。

## 4. P1：Blueprint 核心

### Domain

- [ ] `ProjectBlueprint`、Project、Template、Entity、Field、Relation。
- [ ] Module、Role、PermissionAction、Capability、Generation。
- [ ] `DisplayConfig`、`BusinessFlowSpec`、FlowStep。
- [ ] FieldType、RelationType、OnDeleteAction 等枚举。
- [ ] `project-blueprint-v1.schema.json`。

### Normalizer

- [ ] 补充 id、createdTime、updatedTime、deleted。
- [ ] Java/MySQL/route 命名标准化。
- [ ] 展开外键 owner 和 join table 名称。
- [ ] 从 `moduleCode + PermissionAction` 派生 permission code。
- [ ] 补充 ADMIN 全权限。
- [ ] 编译并验证 display template。
- [ ] 规范化排序、JSON 序列化和 SHA-256。

### Generatability Validator

- [ ] 结构、引用、ER、契约、安全、模板六类规则。
- [ ] 默认 profile：模块目标 3–12、Entity ≤20、Relation ≤30。
- [ ] GOLDEN_TEST profile：允许 1–2 模块，其他规则不放宽。
- [ ] 错误包含稳定 code、JSON path 和可读 message。

验收：三个 fixture 可解析；错误 fixture 能精确定位悬空 Module、错误外键、危险路径和超限。

## 5. P2：GenerationModel 与文件计划

- [ ] `RelationExpansionService`。
- [ ] BackendEntityModel、FrontendModuleModel、DatabaseModel、RbacModel。
- [ ] `ApiContractModel` 成为跨端路径唯一来源。
- [ ] `FileGenerationPlan` 包含 sourceKey/type/template/output/modelHash/dependsOn。
- [ ] FilePlan 在 GenerationModel 冻结前一次生成。
- [ ] 路由、菜单、权限和 database.sql 聚合 DAG。
- [ ] 全局目标路径唯一性和 ownership 校验。

验收：实验室—设备在三端得到同一 laboratoryId/laboratory_id；学生—课程得到稳定 join table、courseIds 和多选组件模型。

## 6. P3：Template Engine 与黄金模板

### Template Engine

- [ ] `TemplateProvider`、`TemplateRegistry`、`ResolvedTemplate`。
- [ ] 按 `id:version:sha256` 的只读缓存。
- [ ] FreeMarker 安全配置，禁止任意类访问和脚本执行。
- [ ] UTF-8、换行符、稳定排序与确定性渲染。
- [ ] 有界文件线程池：8/16/100 初始配置。
- [ ] 临时文件 + hash + 原子改名。
- [ ] `ProtectedRegionMerger` 和冲突检测。

### Spring Boot 固定模板

- [ ] Maven Wrapper、pom、application.yml。
- [ ] Result、PageResult、异常处理。
- [ ] MyBatis Plus 配置、审计字段填充、逻辑删除。
- [ ] Spring Security、JWT、用户/角色/权限基础实现。
- [ ] 健康检查、CORS 和安全默认值。

### Backend 动态模板

- [ ] Entity、DTO、QueryDTO、VO。
- [ ] Mapper、关系查询 XML/注解策略。
- [ ] Service、ServiceImpl、事务和关联表同步。
- [ ] Controller 与固定 CRUD/permission 契约。

### Vue 固定模板

- [ ] Vite/TypeScript/Element Plus/Pinia/Router。
- [ ] Layout、Login、Dashboard、404。
- [ ] request、token、route guard、权限工具。
- [ ] Table、Pagination、File/Image Upload 公共组件。

### Frontend 动态模板

- [ ] api.ts、types.ts。
- [ ] list/form/detail。
- [ ] 单选、多选关系组件。
- [ ] route/menu/permission entry 与聚合模板。

### SQL 模板

- [ ] database/system/business table。
- [ ] index、foreign key、join table。
- [ ] ADMIN、普通角色、权限和菜单 seed。
- [ ] BCrypt 管理员密码和 README 首登提示。

验收：同一输入连续生成两次，全部 GENERATED 文件 hash 一致。

## 7. P4：Merge、Build 与 Package

- [ ] `GenerationOrchestrator` 执行 FilePlan DAG。
- [ ] Backend/Frontend/SQL 分支和分支内文件级并行。
- [ ] Manifest Merge，拒绝路径冲突。
- [ ] manifest 记录 sourceKey/modelHash/lastGeneratedHash/userModified。
- [ ] Maven Wrapper 编译检查。
- [ ] npm ci + Vite build 检查。
- [ ] MySQL 8 临时实例执行检查。
- [ ] API path、ER 三端一致性检查。
- [ ] README、ZIP、SHA-256 和原子发布。
- [ ] 超时、取消、失败清理和诊断日志。

验收：任一分支失败不发布 ZIP；错误可定位到 task/sourceKey/outputPath。

## 8. P5：三个 Golden E2E

- [ ] Book：Entity/CRUD/Page/权限/前端页面/表 SQL。
- [ ] Laboratory–Device：外键、VO display、单选组件、RESTRICT。
- [ ] Student–Course：join table、courseIds、事务同步、多选组件。
- [ ] 每个案例 Maven、npm、MySQL、启动健康检查全部通过。
- [ ] 记录总耗时，普通项目目标小于 2 分钟。
- [ ] 将生成项目 manifest 作为可审查测试产物保存。

P5 完成才允许开始真实 AI 集成。

## 9. P6：AI Blueprint Analyzer

- [ ] 部署 `project-blueprint-v1.schema.json` 结构化输出。
- [ ] Prompt version 管理。
- [ ] 模型调用、超时、响应 hash 和审计。
- [ ] JSON/Schema/引用/ER 错误最多一次修复。
- [ ] 安全和模板篡改直接拒绝。
- [ ] 大需求压缩到容量范围。
- [ ] AI 回归集：图书、实验室、选课及 20 个常见毕设题目。

验收：AI 响应无法把源码、未知字段或其他模板带入冻结 Blueprint。

## 10. P7：Generation Preview 与前端

### Backend API

- [ ] analyze：创建 Blueprint draft/version。
- [ ] preview：返回项目、模块、实体、ER、角色、流程、预计文件数。
- [ ] edit：保存新 Blueprint 版本并重新校验。
- [ ] confirm：冻结指定 Blueprint version。
- [ ] generate：只能引用已确认版本。
- [ ] status/events/download/delete。

### Frontend

- [ ] 需求输入与 AI 分析。
- [ ] Blueprint 信息预览。
- [ ] 模块、实体/表、ER、角色、businessFlows 展示。
- [ ] 必要字段和关系编辑，不提供源码编辑器。
- [ ] 确认生成。
- [ ] SSE 进度、失败诊断、历史与下载。

验收：用户编辑后生成新版本；任务始终绑定确认时的 hash，不能使用后续被修改的 Blueprint。

## 11. 建议首个迭代（可直接排期）

### Iteration 1：Book 垂直切片

- [ ] P0 工程骨架。
- [ ] Book 所需 Blueprint 子集与 JSON 解析。
- [ ] Normalizer 系统字段和 permission code。
- [ ] Book GenerationModel/FilePlans。
- [ ] 最小 Spring Boot/Vue/SQL 固定模板。
- [ ] Entity/DTO/VO/Mapper/Service/Controller。
- [ ] api/types/list/form/detail。
- [ ] database.sql、manifest、README、ZIP。
- [ ] Maven/npm/MySQL 自动验收。

此迭代暂不实现关系、AI、在线编辑和复杂增量，但接口与模型不得使用只能支持 Book 的临时结构。

## 12. Definition of Done

一个任务只有同时满足以下条件才能勾选完成：

- 有实现代码及至少一个自动测试。
- 不依赖 AI 才能测试确定性逻辑。
- 错误包含稳定 code，不仅打印异常。
- 路径、命令和模板输入经过安全处理。
- 文档、Schema、fixture 与 Java 模型一致。
- 生成产物通过真实编译/build/SQL 执行，而不只是字符串快照。
