# Dokiai Computer Generator 后端生成引擎设计（修订版）

## 1. 核心原则

- AI 只生成 `ProjectBlueprint`，不生成 Java、Vue、SQL，不决定目录和技术栈。
- Blueprint 是唯一事实源，归一化并冻结后不可修改。
- 首期只支持 `springboot-vue-template`，输出固定为 `backend/frontend/sql`。
- `RelationExpansionService` 将 Blueprint 一次展开为 `GenerationModel`；所有 Generator 只消费 GenerationModel。
- Backend、Frontend、SQL 分支并行，分支内部再按文件任务并行。
- Merge Engine 是唯一可写最终目录的组件；生成分支写隔离 staging 目录。

```text
需求 -> AI Blueprint -> 归一化 -> 可生成性检查 -> GenerationModel
                                                    |
                     +------------------------------+-------------------------+
                     |                              |                         |
               Backend 文件任务              Frontend 文件任务          SQL 文件任务
                     +------------------------------+-------------------------+
                                                    |
                                      Manifest Merge -> Build Check -> ZIP
```

## 2. 后端模块

```text
com.dropai.rewrite.computergenerator
├── api                  # Controller、DTO/VO、SSE
├── application          # 用例服务、GenerationOrchestrator
├── analysis             # AI Blueprint JSON 生成与有限修复
├── blueprint            # domain、normalize、validate、snapshot
├── model                # RelationExpansionService、GenerationModel
├── generator
│   ├── spi
│   ├── backend
│   ├── frontend
│   └── sql
├── template             # 注册、版本、只读缓存、安全渲染
├── task                 # 状态机、领取、心跳、进度事件
├── merge                # manifest 合并与冲突检查
├── buildcheck           # Maven、Vite、MySQL、契约检查
├── packaging            # ZIP、hash、原子发布
└── storage              # 隔离工作区和路径安全
```

## 3. Blueprint 与 GenerationModel

```java
public record ProjectBlueprint(
    String schemaVersion,
    ProjectSpec project,
    TemplateSpec template,
    List<EntitySpec> entities,
    List<RelationSpec> relations,
    List<ModuleSpec> modules,
    List<RoleSpec> roles,
    List<PermissionSpec> permissions,
    List<BusinessFlowSpec> businessFlows,
    CapabilitySpec capabilities,
    GenerationSpec generation) {}

public record BusinessFlowSpec(
    String code,
    String name,
    List<String> moduleCodes,
    List<BusinessFlowStepSpec> steps) {}

public record BusinessFlowStepSpec(
    int order,
    String name,
    String actorRoleCode,
    String moduleCode,
    String action) {}

public enum FieldType {
    STRING, TEXT, INTEGER, DECIMAL, BOOLEAN,
    DATE, DATETIME, ENUM, IMAGE, FILE
}

public enum RelationType {
    ONE_TO_ONE, ONE_TO_MANY, MANY_TO_ONE, MANY_TO_MANY
}

public record GenerationModel(
    ProjectBlueprint source,
    List<BackendEntityModel> backendEntities,
    List<FrontendModuleModel> frontendModules,
    DatabaseModel database,
    RbacModel rbac,
    ApiContractModel apiContract,
    List<FileGenerationPlan> filePlans) {}

public record FileGenerationPlan(
    String sourceKey,
    GeneratorType generatorType,
    String templateName,
    String outputPath,
    String modelHash,
    Set<String> dependsOn) {}
```

`filePlans` 是冻结 GenerationModel 的组成部分，不由 Generator 在运行期临时决定。调度、进度、Manifest 和增量生成使用同一份计划；路由、菜单和 `database.sql` 聚合任务通过 `dependsOn` 引用片段任务。

归一化固定完成：

1. 补充 `id/createdTime/updatedTime/deleted`。
2. 统一 Java camelCase 与 MySQL snake_case。
3. 一对多/多对一在 many 侧生成外键；一对一生成唯一外键；多对多生成 join table。
4. 自动创建 `ADMIN` 并授予全部权限。
5. Module 的路由、API path 和 permission code 由规则派生；Blueprint 只声明 `PermissionAction` 枚举，不能自定义 code。
6. 规范化 JSON 后计算 SHA-256 并冻结快照。

Entity 的显示规则使用 `displayConfig`：

```java
public record DisplayConfig(String primaryField, String template) {}
```

`template` 只允许引用本实体的 STRING/ENUM/INTEGER 字段，例如 `{assetCode}-{name}`。Normalizer 解析占位符并拒绝未知字段；Frontend 关联选择器和 Backend `displayName` 投影使用同一编译结果。

`businessFlows` 仅描述论文、流程预览和后续图表所需的业务流程，不参与 CRUD 文件数量推导。Flow 中的 role/module/action 引用必须存在，但 Validator 不判断流程是否符合行业常识。

## 4. Blueprint 可生成性检查器

组件命名为 `BlueprintGeneratabilityValidator`。目标只是保证生成器能产生可启动且关系一致的项目，不评价业务设计质量。

只检查六类规则：

1. 结构：project、template、entities、modules、roles 存在，枚举与 JSON 类型合法。
2. 引用：Module、Role、Permission、Relation 的引用对象存在。
3. ER：外键 owner/字段可确定，多对多 join table 可确定。
4. 生成契约：Controller、前端 API/page 和统一 API path 可派生且一致。
5. 安全：拒绝路径穿越、绝对路径、非法包名、非法 SQL 标识符。
6. 模板：首期只能使用已注册的 `springboot-vue-template`。

不检查 AI 业务合理性、行业知识、字段设计优劣和模块数量是否符合真实行业。

容量限制属于生成预算而非行业判断：生产 Analyzer 的目标业务模块为 3–12 个；Entity 最多 20 个、Relation 最多 30 条是硬上限。Analyzer 应先合并同类模块；仍超过硬上限时 Validator 返回 `BLUEPRINT_LIMIT_EXCEEDED`，不得截断数组后继续生成。单实体和双实体黄金 fixture 是 Generator/关系专项测试输入，可在 `GOLDEN_TEST` 校验 profile 下跳过“至少 3 个模块”的产品下限，但不能跳过结构、引用、关系、安全和最大容量检查。

## 5. Generator SPI 与文件级并行

```java
public interface Generator {
    GeneratorType type();
    List<FileGenerationTask> plan(GenerationContext context);
}

public record FileGenerationTask(
    GeneratorType owner,
    String sourceKey,
    String templateName,
    String relativePath,
    String modelHash,
    Map<String, Object> model) {}

public interface TemplateProvider {
    String templateId();
    Set<String> supportedVersions();
    ResolvedTemplate resolve(String version);
}
```

所有文件任务提交前完成路径去重和 ownership 检查。任务只写一个临时文件，完成后校验 hash 并原子改名，不读取其他任务的未完成文件。路由、菜单和 database.sql 先并行生成片段，再执行单独聚合任务。

文件线程池建议起始值：`corePoolSize=8`、`maxPoolSize=16`、`queueCapacity=100`；使用 CallerRunsPolicy 或显式背压，禁止无限队列和每文件新建线程。

## 6. 任务状态机和进度

```text
WAITING -> ANALYZING -> GENERATING -> MERGING -> BUILDING -> SUCCESS
                  \          \             \          \-> FAILED
```

三个分支的详细状态存 `cg_generation_branch`，任务表只保存聚合 stage，避免最后一个分支覆盖其他分支。进度固定按权重计算：分析 15%，生成 55%（Backend 45%、Frontend 35%、SQL 20%），合并 8%，构建 16%，打包 6%。

API 创建任务后立即返回 taskId。后台 worker 通过乐观锁或 `FOR UPDATE SKIP LOCKED` 领取，定时更新心跳。Blueprint 冻结后分支可幂等重试，重试不得重新调用 AI 改需求。进度使用持久化事件 + SSE，轮询作为降级。

生成前增加确认阶段：`ANALYZED -> PREVIEW_READY -> CONFIRMED -> WAITING`。预览只展示项目、模块、实体/表、ER 关系、角色、业务流程和预计文件数量，不展示源码。用户确认后锁定 Blueprint 版本；若用户编辑 Blueprint，则生成新版本并重新执行 Normalizer、Validator 和预览，旧任务不得偷换快照。

## 7. 核心数据库

- `cg_project`：user_id、name、title、status（ACTIVE/DELETED）、active_blueprint_version。单用户一个有效项目由事务和用户级锁保证，不对 `(user_id,status)` 建唯一约束。
- `cg_blueprint`：project_id、version、schema_version、template_id/version、content_json、content_sha256、status、validation_errors。
- `cg_generation_task`：blueprint_id、status、stage、progress、message、workspace、artifact、error、heartbeat、乐观锁 version。
- `cg_generation_branch`：task_id、BACKEND/FRONTEND/SQL、status、progress、manifest、错误和耗时。
- `cg_task_event`：sequence、event_type、stage、progress、payload，用于 SSE 断点续传。
- `cg_artifact`：ZIP/BUILD_LOG/MANIFEST、路径、大小和 SHA-256。

Blueprint 使用版本化 JSON 快照，不把 Entity/Field 全部拆成平台数据库行；这更适合不可变生成输入、schema 演进、审计和复现。

## 8. Manifest、Merge 与增量

每次生成写入：

```json
{
  "manifestVersion": "1",
  "project": "student-system",
  "blueprintHash": "...",
  "template": {"id":"springboot-vue-template","version":"1.0.0","hash":"..."},
  "engineVersion": "1.0.0",
  "files": [{
    "path": "backend/src/main/java/com/example/entity/Student.java",
    "sha256": "...",
    "sourceKey": "backend:entity:student",
    "modelHash": "...",
    "ownership": "GENERATED",
    "lastGeneratedHash": "...",
    "userModified": false
  }]
}
```

Merge Engine 拒绝同路径多 ownership，不采用 last-write-wins。未来增量生成以 `sourceKey + modelHash` 判断变化。生成前比较当前磁盘 hash 与 `lastGeneratedHash`，不同则运行时判定 `userModified=true`。未变化且未修改的文件复用；已消失且未修改的生成文件删除；用户改过的文件产生冲突而不静默覆盖。该标记是扫描结果，不信任客户端传值。

## 9. Build Check

检查保持轻量，只保证能跑通和关系一致：

1. `backend/frontend/sql` 目录存在。
2. Backend 执行 Maven Wrapper 编译。
3. Frontend 执行 `npm ci && npm run build`。
4. SQL 在 MySQL 8 临时实例执行。
5. SQL 外键/中间表、Java 字段/DTO/VO、Vue 关系控件一致。
6. Controller path 和 frontend api path 与 GenerationModel 契约一致。

进程使用 `ProcessBuilder` 参数数组、隔离目录、超时和输出上限，不能拼 Shell 字符串。

## 10. 模板和扩展

模板固定部分直接复制，动态部分渲染。缓存 key 为 `templateId:version:contentSha256`，缓存对象只读。新栈通过 `GeneratorPlugin` 注册 TemplateProvider、三类 Generator 和 BuildCheck；未来可加入 django-vue、node-vue、spring-react，而 Blueprint 保持不变。

## 11. 迁移与验收

从现有大服务迁移顺序：Blueprint/Validator -> GenerationModel -> 三类 Generator -> 文件级并行与 Manifest -> Build/ZIP。原 API 保留为兼容 Facade。

验收要求：同一 Blueprint/模板/引擎重复生成 manifest hash 一致；三个分支及文件任务时间有重叠；任何分支失败不发布 ZIP；四种关系跨 SQL/Java/Vue 一致；生成工程 Maven、Vite、MySQL 检查全部通过；普通项目端到端目标在 2 分钟内完成。
